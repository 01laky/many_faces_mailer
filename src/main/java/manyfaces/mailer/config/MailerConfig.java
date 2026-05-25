package manyfaces.mailer.config;

import java.net.InetSocketAddress;
import java.util.Locale;

/**
 * Immutable snapshot of all process configuration loaded from the environment (12-factor style).
 *
 * <p>Rationale: the mailer is a standalone sidecar with no Spring {@code @Configuration}; parsing
 * env vars once at startup makes behaviour predictable in containers and keeps secrets out of CLI
 * flags. Defaults align with {@code docker-compose.yml} in this repository (Mailpit on the same
 * compose network, cleartext gRPC on port 50054).
 */
public final class MailerConfig {

	/** TCP bind address for the gRPC server (host + port). */
	private final InetSocketAddress grpcListenAddress;

	/**
     * When non-empty, unary RPCs (except standard gRPC health checks) must send metadata header
     * {@code x-mailer-worker-token} matching this value — parity with {@code PUSH_WORKER_EXPECTED_TOKEN}.
     */
	private final String expectedWorkerToken;

	/** When true, registers gRPC server reflection (grpcurl). Disable on shared networks. */
	private final boolean enableReflection;

	private final String grpcTlsCertFile;
	private final String grpcTlsKeyFile;
	private final String grpcMtlsClientCaFile;

	private final String smtpHost;
	private final int smtpPort;
	private final boolean smtpStartTls;
	private final String smtpUser;
	private final String smtpPassword;

	/** RFC 5322 From address used for envelope and MIME {@code From:}. */
	private final String fromEmail;

	/** Optional display name for {@code From:} header (MIME-encoded when needed). */
	private final String fromDisplayName;

	/** Fallback locale when an unknown {@code locale} string arrives on the wire (BCP 47). */
	private final Locale defaultLocale;

	private MailerConfig(
			InetSocketAddress grpcListenAddress,
			String expectedWorkerToken,
			boolean enableReflection,
			String grpcTlsCertFile,
			String grpcTlsKeyFile,
			String grpcMtlsClientCaFile,
			String smtpHost,
			int smtpPort,
			boolean smtpStartTls,
			String smtpUser,
			String smtpPassword,
			String fromEmail,
			String fromDisplayName,
			Locale defaultLocale) {
		this.grpcListenAddress = grpcListenAddress;
		this.expectedWorkerToken = expectedWorkerToken;
		this.enableReflection = enableReflection;
		this.grpcTlsCertFile = grpcTlsCertFile;
		this.grpcTlsKeyFile = grpcTlsKeyFile;
		this.grpcMtlsClientCaFile = grpcMtlsClientCaFile;
		this.smtpHost = smtpHost;
		this.smtpPort = smtpPort;
		this.smtpStartTls = smtpStartTls;
		this.smtpUser = smtpUser;
		this.smtpPassword = smtpPassword;
		this.fromEmail = fromEmail;
		this.fromDisplayName = fromDisplayName;
		this.defaultLocale = defaultLocale;
	}

	public InetSocketAddress grpcListenAddress() {
		return grpcListenAddress;
	}

	public String expectedWorkerToken() {
		return expectedWorkerToken;
	}

	public boolean enableReflection() {
		return enableReflection;
	}

	public String grpcTlsCertFile() {
		return grpcTlsCertFile;
	}

	public String grpcTlsKeyFile() {
		return grpcTlsKeyFile;
	}

	public String grpcMtlsClientCaFile() {
		return grpcMtlsClientCaFile;
	}

	public String smtpHost() {
		return smtpHost;
	}

	public int smtpPort() {
		return smtpPort;
	}

	public boolean smtpStartTls() {
		return smtpStartTls;
	}

	public String smtpUser() {
		return smtpUser;
	}

	public String smtpPassword() {
		return smtpPassword;
	}

	public String fromEmail() {
		return fromEmail;
	}

	public String fromDisplayName() {
		return fromDisplayName;
	}

	public Locale defaultLocale() {
		return defaultLocale;
	}

	/**
     * Reads environment variables and returns a validated config, or throws {@link IllegalArgumentException}
     * with an actionable message (mis-typed port, TLS cert without key, etc.).
     */
	public static MailerConfig loadFromEnv() {
		String listenRaw = trimOrDefault(System.getenv("MAILER_WORKER_GRPC_LISTEN"), ":50054");
		InetSocketAddress grpcAddr = parseListen(listenRaw);

		String token = trimToEmpty(System.getenv("MAILER_WORKER_EXPECTED_TOKEN"));
		boolean reflection = parseBool(System.getenv("MAILER_WORKER_GRPC_REFLECTION"));

		String tlsCert = trimToEmpty(System.getenv("MAILER_WORKER_GRPC_TLS_CERT_FILE"));
		String tlsKey = trimToEmpty(System.getenv("MAILER_WORKER_GRPC_TLS_KEY_FILE"));
		String mtlsCa = trimToEmpty(System.getenv("MAILER_WORKER_GRPC_MTLS_CLIENT_CA_FILE"));
		validateTlsTriple(tlsCert, tlsKey, mtlsCa);

		String smtpHost = trimOrDefault(System.getenv("MAILER_SMTP_HOST"), "mailpit");
		int smtpPort = parsePort(trimOrDefault(System.getenv("MAILER_SMTP_PORT"), "1025"));
		boolean startTls = parseBool(System.getenv("MAILER_SMTP_STARTTLS"));
		String smtpUser = trimToEmpty(System.getenv("MAILER_SMTP_USER"));
		String smtpPass = trimToEmpty(System.getenv("MAILER_SMTP_PASSWORD"));

		String fromEmail = trimOrDefault(System.getenv("MAILER_FROM_EMAIL"), "no-reply@example.invalid");
		String fromName = trimOrDefault(System.getenv("MAILER_FROM_DISPLAY_NAME"), "Many Faces");

		Locale def = Locale.forLanguageTag(trimOrDefault(System.getenv("MAILER_DEFAULT_LOCALE"), "en"));

		return new MailerConfig(
				grpcAddr,
				token,
				reflection,
				tlsCert,
				tlsKey,
				mtlsCa,
				smtpHost,
				smtpPort,
				startTls,
				smtpUser,
				smtpPass,
				fromEmail,
				fromName,
				def);
	}

	/**
     * Fixed configuration for unit tests (no env reads). Not used by production {@link MailerWorkerMain}.
     */
	public static MailerConfig testingMinimal(Locale defaultLocale) {
		return new MailerConfig(
				new InetSocketAddress("127.0.0.1", 0),
				"",
				false,
				"",
				"",
				"",
				"127.0.0.1",
				1025,
				false,
				"",
				"",
				"no-reply@example.invalid",
				"Many Faces Test",
				defaultLocale);
	}

	/**
     * Exposes listen parsing for unit tests in {@code manyfaces.mailer.config} without widening {@link #loadFromEnv()}.
     */
	static InetSocketAddress parseListenAddressForTest(String raw) {
		return parseListen(raw);
	}

	private static void validateTlsTriple(String cert, String key, String mtlsCa) {
		boolean any = !cert.isEmpty() || !key.isEmpty() || !mtlsCa.isEmpty();
		if (!any) {
			return;
		}
		if (cert.isEmpty() || key.isEmpty()) {
			throw new IllegalArgumentException(
					"TLS requires both MAILER_WORKER_GRPC_TLS_CERT_FILE and MAILER_WORKER_GRPC_TLS_KEY_FILE when either is set.");
		}
	}

	/**
     * Parses {@code :50054} (all interfaces) or {@code 127.0.0.1:50054} forms. Leading {@code :} means bind
     * {@code 0.0.0.0} for container-friendly listening.
     */
	private static InetSocketAddress parseListen(String raw) {
		if (raw.startsWith(":")) {
			int port = parsePort(raw.substring(1));
			return new InetSocketAddress("0.0.0.0", port);
		}
		int colon = raw.lastIndexOf(':');
		if (colon <= 0 || colon == raw.length() - 1) {
			throw new IllegalArgumentException(
					"MAILER_WORKER_GRPC_LISTEN must be :port or host:port, got: " + raw);
		}
		String host = raw.substring(0, colon);
		int port = parsePort(raw.substring(colon + 1));
		return new InetSocketAddress(host, port);
	}

	private static int parsePort(String s) {
		try {
			int p = Integer.parseInt(s.trim());
			if (p <= 0 || p > 65535) {
				throw new IllegalArgumentException("Port out of range: " + p);
			}
			return p;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid port: " + s, e);
		}
	}

	private static boolean parseBool(String raw) {
		if (raw == null) {
			return false;
		}
		String s = raw.trim().toLowerCase(Locale.ROOT);
		return s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on");
	}

	private static String trimOrDefault(String v, String d) {
		if (v == null) {
			return d;
		}
		String t = v.trim();
		return t.isEmpty() ? d : t;
	}

	private static String trimToEmpty(String v) {
		return v == null ? "" : v.trim();
	}
}
