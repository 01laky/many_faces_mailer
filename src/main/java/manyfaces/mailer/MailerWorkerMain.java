package manyfaces.mailer;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.grpc.services.HealthStatusManager;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.grpc.MailerAuthInterceptor;
import manyfaces.mailer.grpc.MailerServiceImpl;
import manyfaces.mailer.grpccreds.ServerTlsLoader;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.template.TemplateRenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JVM entry point: loads env-driven {@link MailerConfig}, binds a Netty gRPC server, registers health + optional
 * reflection, and installs {@link MailerAuthInterceptor} for parity with other Many Faces workers.
 *
 * <p>Shutdown: {@code SIGTERM} from Docker/Kubernetes triggers {@link Runtime#addShutdownHook(Thread)} so in-flight SMTP
 * attempts get a bounded window to finish before the process exits.
 */
public final class MailerWorkerMain {

    private static final Logger LOG = LoggerFactory.getLogger(MailerWorkerMain.class);

    public static void main(String[] args) throws Exception {
        MailerConfig cfg = MailerConfig.loadFromEnv();
        LOG.info(
                "Starting many_faces_mailer gRPC bind={} smtp={}:{} reflection={}",
                cfg.grpcListenAddress(),
                cfg.smtpHost(),
                cfg.smtpPort(),
                cfg.enableReflection());

        HealthStatusManager health = new HealthStatusManager();
        TemplateRenderService templates = new TemplateRenderService(cfg);
        SmtpMailSender smtp = new SmtpMailSender(cfg);
        MailerServiceImpl mailerGrpc = new MailerServiceImpl(cfg, templates, smtp);

        NettyServerBuilder serverBuilder = NettyServerBuilder.forAddress(cfg.grpcListenAddress())
                .maxInboundMessageSize(4 * 1024 * 1024)
                .intercept(new MailerAuthInterceptor(cfg.expectedWorkerToken()));

        Optional<SslContext> sslContext =
                ServerTlsLoader.loadServerSslContext(cfg.grpcTlsCertFile(), cfg.grpcTlsKeyFile(), cfg.grpcMtlsClientCaFile());
        sslContext.ifPresent(serverBuilder::sslContext);
        if (sslContext.isEmpty()) {
            LOG.debug(
                    "gRPC TLS not configured (cleartext listener). Set MAILER_WORKER_GRPC_TLS_* before exposing on shared networks.");
        }

        serverBuilder.addService(mailerGrpc);
        serverBuilder.addService(health.getHealthService());
        if (cfg.enableReflection()) {
            serverBuilder.addService(ProtoReflectionServiceV1.newInstance());
            LOG.warn("gRPC reflection enabled (MAILER_WORKER_GRPC_REFLECTION) — disable outside trusted dev.");
        }

        Server server = serverBuilder.build().start();
        health.setStatus("", io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING);

        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> gracefulShutdown(server), "mailer-grpc-shutdown"));

        LOG.info("many_faces_mailer listening {}", cfg.grpcListenAddress());
        server.awaitTermination();
    }

    private static void gracefulShutdown(Server server) {
        LOG.info("Shutdown signal received; stopping gRPC (5s grace)…");
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warn("Forcing shutdown after timeout");
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
