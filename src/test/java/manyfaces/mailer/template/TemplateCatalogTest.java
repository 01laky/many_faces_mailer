package manyfaces.mailer.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplateCatalogTest {

    @Test
    void known_templates_allow_listed() {
        assertThat(TemplateCatalog.isKnownTemplate(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)).isTrue();
        assertThat(TemplateCatalog.isKnownTemplate(TemplateCatalog.IDENTITY_PASSWORD_RESET)).isTrue();
        assertThat(TemplateCatalog.isKnownTemplate("unknown")).isFalse();
    }
}
