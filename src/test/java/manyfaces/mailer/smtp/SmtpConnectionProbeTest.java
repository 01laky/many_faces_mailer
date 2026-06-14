package manyfaces.mailer.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Edge-case coverage for the SMTP probe's JavaMail session properties (previously untested): host/port and
 * timeouts are mapped through, auth is toggled by whether a user is present, and STARTTLS is propagated.
 */
class SmtpConnectionProbeTest {

	private static SmtpTransportSettings settings(String user, boolean startTls) {
		return new SmtpTransportSettings(
				"smtp.example.com", 587, startTls, user, "pw", "from@example.com", "From");
	}

	@Test
	void maps_host_port_and_timeouts() {
		Properties p = SmtpConnectionProbe.sessionProperties(settings("", false));
		assertThat(p.getProperty("mail.smtp.host")).isEqualTo("smtp.example.com");
		assertThat(p.getProperty("mail.smtp.port")).isEqualTo("587");
		assertThat(p.getProperty("mail.smtp.connectiontimeout")).isEqualTo("10000");
		assertThat(p.getProperty("mail.smtp.timeout")).isEqualTo("30000");
		assertThat(p.getProperty("mail.smtp.writetimeout")).isEqualTo("30000");
	}

	@Test
	void auth_disabled_when_user_blank() {
		Properties p = SmtpConnectionProbe.sessionProperties(settings("", false));
		assertThat(p.getProperty("mail.smtp.auth")).isEqualTo("false");
	}

	@Test
	void auth_enabled_when_user_present() {
		Properties p = SmtpConnectionProbe.sessionProperties(settings("svc@example.com", false));
		assertThat(p.getProperty("mail.smtp.auth")).isEqualTo("true");
	}

	@Test
	void starttls_flag_is_propagated() {
		assertThat(SmtpConnectionProbe.sessionProperties(settings("", true))
						.getProperty("mail.smtp.starttls.enable"))
				.isEqualTo("true");
		assertThat(SmtpConnectionProbe.sessionProperties(settings("", false))
						.getProperty("mail.smtp.starttls.enable"))
				.isEqualTo("false");
	}
}
