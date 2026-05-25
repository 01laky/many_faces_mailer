package manyfaces.mailer.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import manyfaces.mailer.config.MailerConfig;
import org.junit.jupiter.api.Test;

/**
 * Verifies Pebble + UTF-8 bundles resolve subjects and bodies for supported locales without hitting SMTP.
 */
class TemplateRenderServiceTest {

	@Test
	void renders_confirm_template_in_english() {
		MailerConfig cfg = MailerConfig.testingMinimal(Locale.ENGLISH);
		var svc = new TemplateRenderService(cfg);
		var out =
				svc.render(
						TemplateCatalog.IDENTITY_EMAIL_CONFIRM,
						"en",
						Map.of("user_name", "Ada", "action_link", "https://example.test/confirm?token=abc"));
		assertThat(out.subject()).isEqualTo("Confirm your email");
		assertThat(out.htmlBody()).contains("Ada");
		assertThat(out.htmlBody()).contains("https://example.test/confirm?token=abc");
		assertThat(out.textBody()).contains("Ada");
	}

	@Test
	void renders_reset_template_in_slovak() {
		MailerConfig cfg = MailerConfig.testingMinimal(new Locale("sk"));
		var svc = new TemplateRenderService(cfg);
		var out =
				svc.render(
						TemplateCatalog.IDENTITY_PASSWORD_RESET,
						"sk",
						Map.of("user_name", "Bruno", "action_link", "https://example.test/reset?x=1"));
		assertThat(out.subject()).isEqualTo("Obnovenie hesla");
		assertThat(out.htmlBody()).contains("Bruno");
	}
}
