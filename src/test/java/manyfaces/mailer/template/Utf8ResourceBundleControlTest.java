package manyfaces.mailer.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class Utf8ResourceBundleControlTest {

	@Test
	void loadsSlovakBundleWithUtf8Characters() {
		ResourceBundle bundle =
				ResourceBundle.getBundle(
						"i18n.messages",
						Locale.forLanguageTag("sk"),
						Thread.currentThread().getContextClassLoader(),
						new Utf8ResourceBundleControl());
		assertThat(bundle.getString("subject.identity_email_confirm")).isEqualTo("Potvrďte svoj e-mail");
	}
}
