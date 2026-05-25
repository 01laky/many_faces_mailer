package manyfaces.mailer.grpc;

import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.smtp.SmtpConnectionProbe;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.smtp.SmtpTransportSettings;
import manyfaces.mailer.template.TemplateCatalog;
import manyfaces.mailer.template.TemplateRenderService;
import manyfaces.mailer.v1.MailerServiceGrpc;
import manyfaces.mailer.v1.SendTemplatedEmailRequest;
import manyfaces.mailer.v1.SendTemplatedEmailResponse;
import manyfaces.mailer.v1.TestSmtpConnectionRequest;
import manyfaces.mailer.v1.TestSmtpConnectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * gRPC entry point for transactional sends: validates the contract, renders templates server-side, then blocks on SMTP.
 *
 * <p>Security posture: this class never logs raw {@code params} maps (they may include signed callback URLs with
 * secrets). Only {@code template_id}, {@code locale}, correlation id, recipient counts, {@code smtp_host}/{@code smtp_port},
 * and {@code duration_ms} are logged at INFO (never raw {@code params} or full callback URLs).
 */
public final class MailerServiceImpl extends MailerServiceGrpc.MailerServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(MailerServiceImpl.class);

    private static final int MAX_RECIPIENTS_TOTAL = 50;
    private static final int MAX_PARAM_KEY_LEN = 128;
    private static final int MAX_PARAM_VALUE_LEN = 8192;
    private static final int MAX_PARAMS_TOTAL_CHARS = 256 * 1024;

    private final MailerConfig mailerConfig;
    private final TemplateRenderService templateRenderService;
    private final SmtpMailSender smtpMailSender;

    public MailerServiceImpl(MailerConfig mailerConfig, TemplateRenderService templateRenderService, SmtpMailSender smtpMailSender) {
        this.mailerConfig = mailerConfig;
        this.templateRenderService = templateRenderService;
        this.smtpMailSender = smtpMailSender;
    }

    @Override
    public void sendTemplatedEmail(SendTemplatedEmailRequest request, StreamObserver<SendTemplatedEmailResponse> responseObserver) {
        String correlationId = Optional.ofNullable(MDC.get(MailerCorrelationInterceptor.MDC_CORRELATION_ID))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        long t0 = System.nanoTime();
        SmtpTransportSettings transport;
        try {
            transport = resolveTransportForSend(request);
        } catch (IllegalArgumentException iae) {
            responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
            return;
        }
        String smtpHost = transport.host();
        int smtpPort = transport.port();
        try {
            validateRecipients(request);
            String templateId = request.getTemplateId().trim();
            if (!TemplateCatalog.isKnownTemplate(templateId)) {
                throw io.grpc.Status.INVALID_ARGUMENT.withDescription("Unknown template_id: " + templateId).asRuntimeException();
            }
            String localeTag = request.getLocale().trim();
            if (localeTag.isEmpty()) {
                localeTag = mailerConfig.defaultLocale().toLanguageTag();
            }
            Map<String, String> params = request.getParamsMap();
            validateParams(params);
            validateRequiredTemplateKeys(templateId, params);

            if (request.hasIdempotencyKey() && !request.getIdempotencyKey().isBlank()) {
                LOG.debug("idempotency_key present for correlation {} (not yet deduplicated)", correlationId);
            }

            TemplateRenderService.RenderedEmail rendered =
                    templateRenderService.render(templateId, localeTag, params);

            List<String> to = new ArrayList<>(request.getToList());
            List<String> cc = new ArrayList<>(request.getCcList());
            List<String> bcc = new ArrayList<>(request.getBccList());

            String replyTo = request.hasReplyTo() ? request.getReplyTo() : null;
            String smtpMessageId =
                    smtpMailSender.send(to, cc, bcc, rendered.subject(), rendered.textBody(), rendered.htmlBody(), replyTo, transport);

            SendTemplatedEmailResponse.Builder rb =
                    SendTemplatedEmailResponse.newBuilder().setCorrelationId(correlationId);
            if (smtpMessageId != null) {
                rb.setSmtpMessageId(smtpMessageId);
            }
            long durationMs = durationMillisSince(t0);
            LOG.info(
                    "SendTemplatedEmail ok correlation={} template_id={} locale={} to_count={} smtp_host={} smtp_port={} duration_ms={}",
                    correlationId,
                    templateId,
                    localeTag,
                    to.size(),
                    smtpHost,
                    smtpPort,
                    durationMs);
            responseObserver.onNext(rb.build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException sre) {
            responseObserver.onError(sre);
        } catch (IllegalArgumentException iae) {
            LOG.warn(
                    "SendTemplatedEmail invalid_argument correlation={} template_id_hint={} smtp_host={} smtp_port={} duration_ms={} detail={}",
                    correlationId,
                    request.getTemplateId(),
                    smtpHost,
                    smtpPort,
                    durationMillisSince(t0),
                    iae.getMessage());
            responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
        } catch (jakarta.mail.AuthenticationFailedException afe) {
            LOG.warn(
                    "SMTP authentication failed correlation={} smtp_host={} smtp_port={} duration_ms={} detail={}",
                    correlationId,
                    smtpHost,
                    smtpPort,
                    durationMillisSince(t0),
                    afe.getMessage());
            responseObserver.onError(
                    io.grpc.Status.FAILED_PRECONDITION.withDescription("SMTP authentication failed").asRuntimeException());
        } catch (jakarta.mail.MessagingException me) {
            LOG.warn(
                    "SMTP transport error correlation={} smtp_host={} smtp_port={} duration_ms={} detail={}",
                    correlationId,
                    smtpHost,
                    smtpPort,
                    durationMillisSince(t0),
                    me.getMessage());
            responseObserver.onError(
                    io.grpc.Status.UNAVAILABLE.withDescription("SMTP transport error: " + me.getMessage())
                            .asRuntimeException());
        } catch (Exception e) {
            LOG.error(
                    "Unexpected send failure correlation={} smtp_host={} smtp_port={} duration_ms={}",
                    correlationId,
                    smtpHost,
                    smtpPort,
                    durationMillisSince(t0),
                    e);
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void testSmtpConnection(
            TestSmtpConnectionRequest request, StreamObserver<TestSmtpConnectionResponse> responseObserver) {
        try {
            if (!request.hasSmtp()) {
                responseObserver.onError(
                        io.grpc.Status.INVALID_ARGUMENT.withDescription("smtp block is required.").asRuntimeException());
                return;
            }
            SmtpTransportSettings transport = SmtpTransportSettings.fromProto(request.getSmtp());
            transport.validateForSend();
            SmtpConnectionProbe.probe(transport);
            responseObserver.onNext(
                    TestSmtpConnectionResponse.newBuilder()
                            .setReachable(true)
                            .setDetail("SMTP connection ok")
                            .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException iae) {
            responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
        } catch (jakarta.mail.AuthenticationFailedException afe) {
            responseObserver.onNext(
                    TestSmtpConnectionResponse.newBuilder()
                            .setReachable(false)
                            .setDetail("SMTP authentication failed")
                            .build());
            responseObserver.onCompleted();
        } catch (jakarta.mail.MessagingException me) {
            responseObserver.onNext(
                    TestSmtpConnectionResponse.newBuilder()
                            .setReachable(false)
                            .setDetail(me.getMessage() == null ? "SMTP transport error" : me.getMessage())
                            .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /** Package-private for unit tests (AMC-J*). */
    static SmtpTransportSettings resolveTransportForSend(SendTemplatedEmailRequest request, MailerConfig mailerConfig) {
        if (request.hasSmtp()) {
            SmtpTransportSettings wire = SmtpTransportSettings.fromProto(request.getSmtp());
            wire.validateForSend();
            return wire;
        }
        return SmtpTransportSettings.fromConfig(mailerConfig);
    }

    private SmtpTransportSettings resolveTransportForSend(SendTemplatedEmailRequest request) {
        return resolveTransportForSend(request, mailerConfig);
    }

    private static long durationMillisSince(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000L;
    }

    private static void validateRecipients(SendTemplatedEmailRequest request) {
        if (request.getToCount() == 0) {
            throw new IllegalArgumentException("At least one 'to' address is required.");
        }
        Set<String> unique = new HashSet<>();
        addRecipients(unique, "to", request.getToList());
        addRecipients(unique, "cc", request.getCcList());
        addRecipients(unique, "bcc", request.getBccList());
        if (unique.size() > MAX_RECIPIENTS_TOTAL) {
            throw new IllegalArgumentException("Recipient count after dedup exceeds limit " + MAX_RECIPIENTS_TOTAL);
        }
    }

    private static void addRecipients(Set<String> unique, String fieldName, List<String> recipients) {
        for (String recipient : recipients) {
            String normalized = recipient.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Blank recipient in '" + fieldName + "' list.");
            }

            unique.add(normalized);
        }
    }

    private static void validateParams(Map<String, String> params) {
        int total = 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k.length() > MAX_PARAM_KEY_LEN) {
                throw new IllegalArgumentException("Param key too long: " + k.substring(0, Math.min(32, k.length())));
            }
            if (v.length() > MAX_PARAM_VALUE_LEN) {
                throw new IllegalArgumentException("Param value too long for key: " + k);
            }
            if (containsIllegalControlChars(v)) {
                throw new IllegalArgumentException("Param value contains illegal control characters for key: " + k);
            }
            total += k.length() + v.length();
        }
        if (total > MAX_PARAMS_TOTAL_CHARS) {
            throw new IllegalArgumentException("Params total serialized size exceeds cap.");
        }
    }

    private static boolean containsIllegalControlChars(String v) {
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\r' || c == '\n' || c == 0) {
                return true;
            }
        }
        return false;
    }

    private static void validateRequiredTemplateKeys(String templateId, Map<String, String> params) {
        for (String required : TemplateCatalog.requiredKeys(templateId)) {
            if (!params.containsKey(required) || params.get(required).isBlank()) {
                throw new IllegalArgumentException("Missing required param: " + required);
            }
        }
    }
}
