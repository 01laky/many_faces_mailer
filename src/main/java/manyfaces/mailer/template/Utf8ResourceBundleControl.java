package manyfaces.mailer.template;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Loads {@code .properties} bundles from the classpath using UTF-8 instead of ISO-8859-1 (Java legacy default).
 *
 * <p>Without this control, accented characters in Slovak subject lines would require {@code \\uXXXX} escapes;
 * product copy stays human-editable in plain UTF-8 files under {@code src/main/resources/i18n/}.
 */
public final class Utf8ResourceBundleControl extends ResourceBundle.Control {

    @Override
    public ResourceBundle newBundle(
            String baseName,
            Locale locale,
            String format,
            ClassLoader loader,
            boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, "properties");
        try (var stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
