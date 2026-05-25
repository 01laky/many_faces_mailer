package manyfaces.mailer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** Guards parsing rules for {@code MAILER_WORKER_GRPC_LISTEN} so misconfiguration fails fast at startup. */
class MailerConfigListenParseTest {

	@Test
	void colon_only_form_binds_all_interfaces() {
		InetSocketAddress a = MailerConfig.parseListenAddressForTest(":50054");
		assertThat(a.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
		assertThat(a.getPort()).isEqualTo(50054);
	}

	@Test
	void host_port_form_preserved() {
		InetSocketAddress a = MailerConfig.parseListenAddressForTest("127.0.0.1:6000");
		assertThat(a.getHostString()).isEqualTo("127.0.0.1");
		assertThat(a.getPort()).isEqualTo(6000);
	}

	@Test
	void rejects_invalid_port() {
		assertThatThrownBy(() -> MailerConfig.parseListenAddressForTest(":70000"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_host_without_port() {
		assertThatThrownBy(() -> MailerConfig.parseListenAddressForTest("127.0.0.1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_zero_port() {
		assertThatThrownBy(() -> MailerConfig.parseListenAddressForTest(":0"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejects_trailing_colon_only() {
		assertThatThrownBy(() -> MailerConfig.parseListenAddressForTest("host:"))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
