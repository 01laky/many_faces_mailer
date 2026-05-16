package manyfaces.mailer.template;

import java.util.Set;

/**
 * Authoritative allow-list of {@code template_id} values accepted by {@link manyfaces.mailer.grpc.MailerServiceImpl}.
 * The catalog is duplicated in README for operators; changing IDs is a coordinated release across Java + C# callers.
 */
public final class TemplateCatalog {

    private TemplateCatalog() {}

    public static final String IDENTITY_EMAIL_CONFIRM = "identity_email_confirm";
    public static final String IDENTITY_PASSWORD_RESET = "identity_password_reset";
    public static final String ACCOUNT_REGISTRATION_CODE = "account_registration_code";

    /** Required param keys per template (values still validated for control chars / length in the service layer). */
    public static final Set<String> REQUIRED_CONFIRM = Set.of("action_link", "user_name");

    public static final Set<String> REQUIRED_RESET = Set.of("action_link", "user_name");

    public static final Set<String> REQUIRED_REGISTRATION_CODE =
            Set.of("action_link", "registration_code", "user_name", "expiry_minutes");

    private static final Set<String> ALL =
            Set.of(IDENTITY_EMAIL_CONFIRM, IDENTITY_PASSWORD_RESET, ACCOUNT_REGISTRATION_CODE);

    public static boolean isKnownTemplate(String templateId) {
        return templateId != null && ALL.contains(templateId);
    }

    public static Set<String> requiredKeys(String templateId) {
        return switch (templateId) {
            case IDENTITY_EMAIL_CONFIRM -> REQUIRED_CONFIRM;
            case IDENTITY_PASSWORD_RESET -> REQUIRED_RESET;
            case ACCOUNT_REGISTRATION_CODE -> REQUIRED_REGISTRATION_CODE;
            default -> Set.of();
        };
    }
}
