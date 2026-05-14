package manyfaces.mailer.grpc;

import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.template.TemplateCatalog;
import manyfaces.mailer.template.TemplateRenderService;
import manyfaces.mailer.v1.MailerServiceGrpc;
import manyfaces.mailer.v1.SendTemplatedEmailRequest;
import manyfaces.mailer.v1.SendTemplatedEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC entry point for transactional sends: validates the contract, renders templates server-side, then blocks on SMTP.
 *
 * <p>Security posture: this class never logs raw {@code params} maps (they may include signed callback URLs with
 * secrets). Only {@code template_id}, {@code locale}, correlation id, and recipient counts are logged at INFO.
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
        String correlationId = UUID.randomUUID().toString();
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
                // v1: log only; future dedup may persist keys with TTL to protect against blind backend retries.
                LOG.debug("idempotency_key present for correlation {} (not yet deduplicated)", correlationId);
            }

            TemplateRenderService.RenderedEmail rendered =
                    templateRenderService.render(templateId, localeTag, params);

            List<String> to = new ArrayList<>(request.getToList());
            List<String> cc = new ArrayList<>(request.getCcList());
            List<String> bcc = new ArrayList<>(request.getBccList());

            String replyTo = request.hasReplyTo() ? request.getReplyTo() : null;
            String smtpMessageId =
                    smtpMailSender.send(to, cc, bcc, rendered.subject(), rendered.textBody(), rendered.htmlBody(), replyTo);

            SendTemplatedEmailResponse.Builder rb =
                    SendTemplatedEmailResponse.newBuilder().setCorrelationId(correlationId);
            if (smtpMessageId != null) {
                rb.setSmtpMessageId(smtpMessageId);
            }
            LOG.info(
                    "SendTemplatedEmail ok correlation={} template_id={} locale={} to_count={}",
                    correlationId,
                    templateId,
                    localeTag,
                    to.size());
            responseObserver.onNext(rb.build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException sre) {
            responseObserver.onError(sre);
        } catch (IllegalArgumentException iae) {
            responseObserver.onError(
                    io.grpc.Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
        } catch (jakarta.mail.AuthenticationFailedException afe) {
            LOG.warn("SMTP authentication failed: {}", afe.getMessage());
            responseObserver.onError(
                    io.grpc.Status.FAILED_PRECONDITION.withDescription("SMTP authentication failed").asRuntimeException());
        } catch (jakarta.mail.MessagingException me) {
            LOG.warn("SMTP failure: {}", me.getMessage());
            responseObserver.onError(
                    io.grpc.Status.UNAVAILABLE.withDescription("SMTP transport error: " + me.getMessage())
                            .asRuntimeException());
        } catch (Exception e) {
            LOG.error("Unexpected send failure", e);
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private static void validateRecipients(SendTemplatedEmailRequest request) {
        if (request.getToCount() == 0) {
            throw new IllegalArgumentException("At least one 'to' address is required.");
        }
        Set<String> unique = new HashSet<>();
        for (String t : request.getToList()) {
            unique.add(t.trim().toLowerCase(Locale.ROOT));
        }
        for (String t : request.getCcList()) {
            unique.add(t.trim().toLowerCase(Locale.ROOT));
        }
        for (String t : request.getBccList()) {
            unique.add(t.trim().toLowerCase(Locale.ROOT));
        }
        if (unique.size() > MAX_RECIPIENTS_TOTAL) {
            throw new IllegalArgumentException("Recipient count after dedup exceeds limit " + MAX_RECIPIENTS_TOTAL);
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
