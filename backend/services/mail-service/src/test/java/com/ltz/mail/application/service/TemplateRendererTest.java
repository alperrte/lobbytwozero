package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ltz.mail.IntegrationTestSupport;
import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

class TemplateRendererTest extends IntegrationTestSupport {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-31T12:30:00Z");

    @Autowired
    private TemplateRenderer renderer;

    private static MailPayload payloadFor(MailType type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> new MailPayload("123456", null, 600, null);
            case PASSWORD_RESET_LINK, ACCOUNT_DELETION_CONFIRMATION -> new MailPayload(null, RESET_LINK, 900, null);
            default -> new MailPayload(null, null, null, OCCURRED_AT);
        };
    }

    @Test
    void everyTypeRendersInEveryLanguageModeWithoutUnresolvedPlaceholders() {
        for (MailType type : MailType.values()) {
            for (MailLocale locale : Arrays.asList(null, MailLocale.TR, MailLocale.EN)) {
                RenderedMail mail = renderer.render(type, locale, payloadFor(type));

                assertThat(mail.subject()).isNotBlank().doesNotContain("{{").doesNotContain("\n");
                assertThat(mail.html()).contains("cid:ltz-logo", "<title>").doesNotContain("{{");
                assertThat(mail.text()).isNotBlank().doesNotContain("{{");
            }
        }
    }

    @Test
    void mailsUseTheDarkBrandDesignAndKeepTheLightSchemeAsAnEnhancement() {
        RenderedMail mail = renderer.render(MailType.EMAIL_VERIFICATION, MailLocale.TR, payloadFor(MailType.EMAIL_VERIFICATION));

        assertThat(mail.html()).contains("background:#141419", "#e11d2e", "prefers-color-scheme: light",
                "E-POSTA DOĞRULAMA", "123456");
        assertThat(mail.html()).doesNotContain("#6d28d9");
    }

    @Test
    void welcomeMailGreetsInBothLanguagesWithoutNeedingAnyVariable() {
        MailPayload payload = payloadFor(MailType.WELCOME);

        RenderedMail turkish = renderer.render(MailType.WELCOME, MailLocale.TR, payload);
        RenderedMail english = renderer.render(MailType.WELCOME, MailLocale.EN, payload);
        RenderedMail both = renderer.render(MailType.WELCOME, null, payload);

        assertThat(turkish.subject()).isEqualTo("LTZ'ye hoş geldin");
        assertThat(turkish.html()).contains("HOŞ GELDİN", "KEŞFET", "TOPLULUK", "LTZ PERFORMANCE");
        assertThat(english.subject()).isEqualTo("Welcome to LTZ");
        assertThat(english.html()).contains("WELCOME", "DISCOVER", "COMMUNITY");
        assertThat(both.subject()).isEqualTo("LTZ'ye hoş geldin / Welcome to LTZ");
    }

    @Test
    void footerCarriesBrandReasonAndLegalNoticeInEachLanguage() {
        RenderedMail turkish = renderer.render(MailType.PASSWORD_CHANGED, MailLocale.TR, payloadFor(MailType.PASSWORD_CHANGED));
        RenderedMail english = renderer.render(MailType.PASSWORD_CHANGED, MailLocale.EN, payloadFor(MailType.PASSWORD_CHANGED));
        RenderedMail both = renderer.render(MailType.PASSWORD_CHANGED, null, payloadFor(MailType.PASSWORD_CHANGED));
        String year = String.valueOf(Year.now(ZoneOffset.UTC).getValue());

        assertThat(turkish.html()).contains("LobbyTwoZero", "hesabınla ilgili bir işlem nedeniyle aldın",
                "KVKK Aydınlatma Metni", "© " + year + " LobbyTwoZero. Tüm hakları saklıdır.");
        assertThat(turkish.text()).contains("KVKK Aydınlatma Metni", "lütfen yanıtlama");
        assertThat(english.html()).contains("because of an action on your LTZ account", "KVKK Privacy Notice");
        assertThat(both.html()).contains("Tüm hakları saklıdır.", "All rights reserved.");
    }

    @Test
    void valuesAreHtmlEscapedButTextStaysPlain() {
        RenderedMail mail = renderer.render(MailType.EMAIL_VERIFICATION, MailLocale.EN,
                new MailPayload("<script>x</script>", null, 600, null));

        assertThat(mail.html()).contains("&lt;script&gt;x&lt;/script&gt;").doesNotContain("<script>");
        assertThat(mail.text()).contains("<script>x</script>");
    }

    @Test
    void linksAreEscapedInsideAttributes() {
        RenderedMail mail = renderer.render(MailType.PASSWORD_RESET_LINK, MailLocale.EN,
                new MailPayload(null, "https://app.ltz.test/r?a=1&b=\"2\"", 900, null));

        assertThat(mail.html()).contains("href=\"https://app.ltz.test/r?a=1&amp;b=&quot;2&quot;\"");
    }

    @Test
    void timeAndValidityAreFormattedPerLanguage() {
        RenderedMail turkish = renderer.render(MailType.PASSWORD_CHANGED, MailLocale.TR, payloadFor(MailType.PASSWORD_CHANGED));
        RenderedMail english = renderer.render(MailType.PASSWORD_CHANGED, MailLocale.EN, payloadFor(MailType.PASSWORD_CHANGED));
        RenderedMail code = renderer.render(MailType.EMAIL_VERIFICATION, MailLocale.TR,
                new MailPayload("123456", null, 90, null));

        assertThat(turkish.text()).contains("31.10.2026 12:30 UTC");
        assertThat(english.text()).contains("2026-10-31 12:30 UTC");
        assertThat(code.text()).contains("2 dakika");
    }

    @Test
    void bilingualMailHasBothLanguagesAndLanguageAttributeOfTheFirst() {
        RenderedMail mail = renderer.render(MailType.ACCOUNT_BANNED, null, payloadFor(MailType.ACCOUNT_BANNED));

        assertThat(mail.subject()).isEqualTo("LTZ hesabın yasaklandı / Your LTZ account was banned");
        assertThat(mail.html()).contains("<html lang=\"tr\"", "Hesabın yasaklandı", "Your account was banned");
        assertThat(mail.text()).contains(List.of("Tarih:", "Date:").toArray(new String[0]));
    }

    @Test
    void accountTakeoverTypesRenderDateAndWarningInBothLanguages() {
        for (MailType type : List.of(MailType.TWO_FACTOR_ENABLED, MailType.TWO_FACTOR_DISABLED,
                MailType.RECOVERY_CODE_USED, MailType.EXTERNAL_IDENTITY_LINKED)) {
            RenderedMail turkish = renderer.render(type, MailLocale.TR, payloadFor(type));
            RenderedMail english = renderer.render(type, MailLocale.EN, payloadFor(type));

            assertThat(turkish.text()).contains("Tarih: 31.10.2026 12:30 UTC",
                    "Bu işlemi sen yapmadıysan hemen parolanı sıfırla ve destek ile iletişime geç.");
            assertThat(english.text()).contains("Date: 2026-10-31 12:30 UTC",
                    "reset your password immediately and contact support.");
            assertThat(turkish.html()).contains("31.10.2026 12:30 UTC").doesNotContain("{{");
            assertThat(english.html()).contains("2026-10-31 12:30 UTC").doesNotContain("{{");
        }
    }

    @Test
    void missingVariableIsAPermanentTemplateError() {
        MailPayload withoutDate = new MailPayload(null, null, null, null);

        assertThatThrownBy(() -> renderer.render(MailType.PASSWORD_CHANGED, MailLocale.TR, withoutDate))
                .isInstanceOfSatisfying(MailDeliveryException.class, exception -> {
                    assertThat(exception.isPermanent()).isTrue();
                    assertThat(exception.errorCode()).isEqualTo("TEMPLATE_ERROR");
                });
    }
}
