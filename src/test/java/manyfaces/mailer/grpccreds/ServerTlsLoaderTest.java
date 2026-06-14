package manyfaces.mailer.grpccreds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Edge-case coverage for the gRPC server TLS loader (previously untested): blank paths mean cleartext, a
 * half-configured pair fails fast, and unreadable/invalid PEM material is rejected (Netty raises an
 * IllegalArgumentException while reading the cert before the loader's IOException path is reached).
 */
class ServerTlsLoaderTest {

	@Test
	void both_paths_blank_means_cleartext() {
		assertThat(ServerTlsLoader.loadServerSslContext("", "", "")).isEmpty();
	}

	@Test
	void cert_without_key_is_rejected() {
		assertThatThrownBy(() -> ServerTlsLoader.loadServerSslContext("cert.pem", "", ""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("both certificate and key");
	}

	@Test
	void key_without_cert_is_rejected() {
		assertThatThrownBy(() -> ServerTlsLoader.loadServerSslContext("", "key.pem", ""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void missing_or_invalid_pem_files_are_rejected() {
		assertThatThrownBy(
						() -> ServerTlsLoader.loadServerSslContext("/no/such/cert.pem", "/no/such/key.pem", ""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("valid certificates");
	}
}
