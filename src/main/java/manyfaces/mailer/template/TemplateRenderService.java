package manyfaces.mailer.template;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import manyfaces.mailer.config.MailerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combines Pebble HTML/text templates with {@link ResourceBundle} subject lines for a given {@code template_id} + locale.
 *
 * <p>Locale resolution: try exact BCP 47 tag first, then language-only, then {@link MailerConfig#defaultLocale()}.
 * Pebble auto-escaping stays enabled for dynamic fields; URLs marked {@code |raw} in templates are documented as
 * server-controlled callback links only (never pass untrusted HTML through {@code |raw}).
 */
public final class TemplateRenderService {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateRenderService.class);
    private static final String BUNDLE_BASE = "i18n.messages";

    private final PebbleEngine pebbleEngine;
    private final MailerConfig mailerConfig;
    private final Utf8ResourceBundleControl utf8Control = new Utf8ResourceBundleControl();

    public TemplateRenderService(MailerConfig mailerConfig) {
        this.mailerConfig = mailerConfig;
        this.pebbleEngine = new PebbleEngine.Builder()
                .loader(new ClasspathLoader())
                .autoEscaping(true)
                .cacheActive(false)
                .build();
    }

    public record RenderedEmail(String subject, String htmlBody, String textBody) {}

    /**
     * Loads {@code templates/{templateId}.html} and {@code .txt}, evaluates with {@code params}, and reads
     * {@code subject.{templateId}} from the resolved bundle.
     */
    public RenderedEmail render(String templateId, String localeTag, Map<String, String> params) {
        Locale requested = Locale.forLanguageTag(localeTag == null ? "" : localeTag.trim());
        if (requested.getLanguage().isEmpty()) {
            requested = mailerConfig.defaultLocale();
        }
        ResourceBundle bundle = resolveBundle(requested);
        String subjectKey = "subject." + templateId;
        String subject;
        try {
            subject = bundle.getString(subjectKey);
        } catch (MissingResourceException e) {
            LOG.warn("Missing bundle key {} for locale {}; falling back to default locale", subjectKey, requested);
            subject = ResourceBundle.getBundle(BUNDLE_BASE, mailerConfig.defaultLocale(), utf8Control).getString(subjectKey);
        }

        Map<String, Object> context = new HashMap<>(params);
        context.put("templateId", templateId);

        String html = evaluateClasspathTemplate(templateId + ".html", context);
        String text = evaluateClasspathTemplate(templateId + ".txt", context);
        return new RenderedEmail(subject, html, text);
    }

    private ResourceBundle resolveBundle(Locale requested) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE, requested, cl, utf8Control);
        } catch (MissingResourceException ex) {
            String lang = requested.getLanguage();
            if (!lang.isEmpty()) {
                try {
                    return ResourceBundle.getBundle(BUNDLE_BASE, new Locale(lang), cl, utf8Control);
                } catch (MissingResourceException ignored) {
                    // fall through
                }
            }
            LOG.info("No bundle for {}; using default {}", requested, mailerConfig.defaultLocale());
            return ResourceBundle.getBundle(BUNDLE_BASE, mailerConfig.defaultLocale(), cl, utf8Control);
        }
    }

    private String evaluateClasspathTemplate(String fileName, Map<String, Object> context) {
        try {
            PebbleTemplate template = pebbleEngine.getTemplate("templates/" + fileName);
            Writer writer = new StringWriter();
            template.evaluate(writer, context);
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Template render failed for templates/" + fileName + ": " + e.getMessage(), e);
        }
    }
}
