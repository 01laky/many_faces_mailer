package manyfaces.mailer.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Propagates correlation from inbound gRPC metadata into {@link MDC} so mailer logs align with API traces.
 *
 * <p>Metadata keys must stay in sync with {@code many_faces_backend} {@code MailerWorkerCorrelationMetadata}
 * (lowercase ASCII). {@link #MDC_CORRELATION_ID} is filled from {@code x-request-id}, else the trace id segment of
 * {@code traceparent}, else a random UUID. Optional {@code traceparent} / {@code tracestate} are copied into MDC when
 * present. MDC is restored when the {@link ServerCall} closes (covers auth failures that never reach the service).
 */
public final class MailerCorrelationInterceptor implements ServerInterceptor {

	public static final String MDC_CORRELATION_ID = "correlation_id";
	public static final String MDC_TRACEPARENT = "traceparent";
	public static final String MDC_TRACESTATE = "tracestate";

	static final Metadata.Key<String> KEY_X_REQUEST_ID =
			Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
	static final Metadata.Key<String> KEY_TRACEPARENT =
			Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
	static final Metadata.Key<String> KEY_TRACESTATE =
			Metadata.Key.of("tracestate", Metadata.ASCII_STRING_MARSHALLER);

	@Override
	public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
			ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
		String prevCorrelation = MDC.get(MDC_CORRELATION_ID);
		String prevTraceParent = MDC.get(MDC_TRACEPARENT);
		String prevTraceState = MDC.get(MDC_TRACESTATE);

		pushFromMetadata(headers);

		ServerCall<ReqT, RespT> tracked =
				new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
					@Override
					public void close(Status status, Metadata trailers) {
						try {
							super.close(status, trailers);
						} finally {
							restoreMdc(prevCorrelation, prevTraceParent, prevTraceState);
						}
					}
				};

		return Contexts.interceptCall(Context.current(), tracked, headers, next);
	}

	private static void pushFromMetadata(Metadata headers) {
		String rid = sanitize(headers.get(KEY_X_REQUEST_ID));
		String tp = sanitize(headers.get(KEY_TRACEPARENT));
		String ts = sanitize(headers.get(KEY_TRACESTATE));

		String correlation = rid;
		if (correlation == null || correlation.isBlank()) {
			correlation = traceIdFromTraceparent(tp);
		}
		if (correlation == null || correlation.isBlank()) {
			correlation = UUID.randomUUID().toString();
		}

		MDC.put(MDC_CORRELATION_ID, correlation);
		if (tp != null && !tp.isBlank()) {
			MDC.put(MDC_TRACEPARENT, tp);
		}
		if (ts != null && !ts.isBlank()) {
			MDC.put(MDC_TRACESTATE, ts);
		}
	}

	private static void restoreMdc(String prevCorrelation, String prevTraceParent, String prevTraceState) {
		putOrRemove(MDC_CORRELATION_ID, prevCorrelation);
		putOrRemove(MDC_TRACEPARENT, prevTraceParent);
		putOrRemove(MDC_TRACESTATE, prevTraceState);
	}

	private static void putOrRemove(String key, String previous) {
		if (previous == null) {
			MDC.remove(key);
		} else {
			MDC.put(key, previous);
		}
	}

	static String sanitize(String raw) {
		if (raw == null) {
			return null;
		}
		String v = raw.trim();
		if (v.isEmpty() || v.length() > 256) {
			return null;
		}
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			if (c == '\r' || c == '\n' || c == 0 || c > 127) {
				return null;
			}
		}
		return v;
	}

	/**
     * W3C traceparent: {@code version-trace_id-span_id-trace_flags}; trace_id is 32 lowercase hex chars (see
     * https://www.w3.org/TR/trace-context/).
     */
	static Optional<String> traceIdFromTraceparentForTests(String traceparent) {
		return Optional.ofNullable(traceIdFromTraceparent(traceparent));
	}

	private static String traceIdFromTraceparent(String tp) {
		if (tp == null || tp.isBlank()) {
			return null;
		}
		String[] parts = tp.split("-");
		if (parts.length < 2) {
			return null;
		}
		String traceId = parts[1].toLowerCase(Locale.ROOT);
		if (traceId.length() != 32) {
			return null;
		}
		for (int i = 0; i < traceId.length(); i++) {
			char c = traceId.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
			if (!hex) {
				return null;
			}
		}
		return traceId;
	}
}
