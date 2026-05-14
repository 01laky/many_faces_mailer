package manyfaces.mailer.grpccreds;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Builds a Netty {@link SslContext} for the gRPC server when PEM paths are set — same conceptual split as
 * {@code many_faces_push/internal/grpccreds}: server cert+key; optional client CA bundle enables mTLS.
 *
 * <p>When both cert and key paths are empty, gRPC should listen in cleartext (dev only on trusted networks).
 */
public final class ServerTlsLoader {

    private ServerTlsLoader() {}

    /**
     * @param certFile server certificate chain PEM
     * @param keyFile matching private key PEM
     * @param clientCaFile optional CA for verifying client certificates (mTLS)
     * @return empty when both cert and key are blank; otherwise a built {@link SslContext}
     */
    public static Optional<SslContext> loadServerSslContext(String certFile, String keyFile, String clientCaFile) {
        if (certFile.isEmpty() && keyFile.isEmpty()) {
            return Optional.empty();
        }
        if (certFile.isEmpty() || keyFile.isEmpty()) {
            throw new IllegalArgumentException("TLS requires both certificate and key file paths.");
        }
        try {
            File cert = new File(certFile);
            File key = new File(keyFile);
            SslContextBuilder builder = GrpcSslContexts.forServer(cert, key);
            if (!clientCaFile.isEmpty()) {
                builder = builder.trustManager(new File(clientCaFile)).clientAuth(ClientAuth.REQUIRE);
            }
            return Optional.of(builder.build());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load gRPC TLS material", e);
        }
    }
}
