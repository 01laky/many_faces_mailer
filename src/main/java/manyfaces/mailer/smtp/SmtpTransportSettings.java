package manyfaces.mailer.smtp;

import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.v1.SmtpTransportConfig;

/** Per-send SMTP endpoint + From identity (wire override or env snapshot). */
public record SmtpTransportSettings(
		String host,
		int port,
		boolean startTls,
		String user,
		String password,
		String fromEmail,
		String fromDisplayName) {

	public SmtpTransportSettings {
		host = host == null ? "" : host.trim();
		user = user == null ? "" : user.trim();
		password = password == null ? "" : password;
		fromEmail = fromEmail == null ? "" : fromEmail.trim();
		fromDisplayName = fromDisplayName == null ? "" : fromDisplayName.trim();
	}

	public static SmtpTransportSettings fromConfig(MailerConfig cfg) {
		return new SmtpTransportSettings(
				cfg.smtpHost(),
				cfg.smtpPort(),
				cfg.smtpStartTls(),
				cfg.smtpUser(),
				cfg.smtpPassword(),
				cfg.fromEmail(),
				cfg.fromDisplayName());
	}

	public static SmtpTransportSettings fromProto(SmtpTransportConfig proto) {
		return new SmtpTransportSettings(
				proto.getHost(),
				proto.getPort(),
				proto.getStartTls(),
				proto.getUser(),
				proto.getPassword(),
				proto.getFromEmail(),
				proto.getFromDisplayName());
	}

	/** Validates wire override blocks before SMTP I/O. */
	public void validateForSend() {
		if (host.isEmpty()) {
			throw new IllegalArgumentException("SMTP host is required when smtp block is present.");
		}
		if (port <= 0 || port > 65535) {
			throw new IllegalArgumentException("SMTP port out of range.");
		}
		if (fromEmail.isEmpty()) {
			throw new IllegalArgumentException("from_email is required when smtp block is present.");
		}
	}
}
