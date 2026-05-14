package manyfaces.mailer.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional shared-secret gate for internal gRPC: mirrors {@code x-push-worker-token} on many_faces_push.
 *
 * <p>Health checks and reflection (when registered) must stay callable without the header so orchestrators
 * and grpcurl can probe liveness. All business RPCs require the token when {@code expectedToken} is non-empty.
 */
public final class MailerAuthInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(MailerAuthInterceptor.class);

    /** Metadata key must match {@code many_faces_backend} {@code MailerWorkerGrpcClient} byte-for-byte. */
    public static final Metadata.Key<String> MAILER_WORKER_TOKEN_KEY =
            Metadata.Key.of("x-mailer-worker-token", Metadata.ASCII_STRING_MARSHALLER);

    private final String expectedToken;

    public MailerAuthInterceptor(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String fullMethod = call.getMethodDescriptor().getFullMethodName();
        if (shouldBypassAuth(fullMethod)) {
            return Contexts.interceptCall(Context.current(), call, headers, next);
        }
        if (expectedToken.isEmpty()) {
            LOG.debug("MailerAuthInterceptor: MAILER_WORKER_EXPECTED_TOKEN unset; gRPC is open on this bind address.");
            return Contexts.interceptCall(Context.current(), call, headers, next);
        }
        String presented = headers.get(MAILER_WORKER_TOKEN_KEY);
        if (presented == null || !expectedToken.equals(presented)) {
            call.close(Status.UNAUTHENTICATED.withDescription("missing or invalid x-mailer-worker-token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current(), call, headers, next);
    }

    /**
     * Standard {@link io.grpc.health.v1.HealthGrpc} and reflection RPCs are excluded from token checks so
     * Kubernetes/docker HEALTHCHECK and dev grpcurl stay ergonomic.
     */
    private static boolean shouldBypassAuth(String fullMethod) {
        return fullMethod.startsWith("grpc.health.v1.Health/")
                || fullMethod.startsWith("grpc.reflection.v1alpha.ServerReflection/")
                || fullMethod.startsWith("grpc.reflection.v1.ServerReflection/");
    }
}
