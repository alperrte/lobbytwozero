package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ltz.mail.domain.exception.MailErrorCode;
import com.ltz.mail.domain.exception.MailException;
import com.ltz.mail.infrastructure.config.LinkProperties;

/** E-posta bağlantı politikası: açık yönlendirme / kimlik avı yüzeyi (C012). Saf birim testi, Spring gerekmez. */
class LinkPolicyTest {

    private static LinkPolicy policy(String... allowedHosts) {
        return new LinkPolicy(new LinkProperties(List.of(allowedHosts)));
    }

    private static void assertRejected(LinkPolicy policy, String link) {
        assertThatThrownBy(() -> policy.requireAllowed(link)).isInstanceOf(MailException.class);
    }

    @Test
    void izinliHostHttpsIleKabulEdilir() {
        assertThatCode(() -> policy("ltz.example").requireAllowed("https://ltz.example/verify?token=abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void hostKarsilastirmasiBuyukKucukHarfeDuyarsizdir() {
        assertThatCode(() -> policy("LTZ.example").requireAllowed("https://Ltz.EXAMPLE/x")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/verify",
            "https://ltz.example.evil.example/verify",
            "https://evilltz.example/verify",
            "https://ltz.example@evil.example/verify",
            "https://user:pass@ltz.example/verify",
            "http://ltz.example/verify",
            "javascript:alert(1)",
            "ftp://ltz.example/file",
            "//ltz.example/verify",
            "/verify",
            "ltz.example/verify",
            "https://",
            "not a url"
    })
    void izinsizHostSemaKimlikBilgisiVeBicimHataliBaglantilarReddedilir(String link) {
        assertRejected(policy("ltz.example"), link);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void bosBaglantiReddedilir(String link) {
        assertRejected(policy("ltz.example"), link);
    }

    @Test
    void enFazla512KarakterKabulEdilir() {
        String base = "https://ltz.example/?q=";
        String atLimit = base + "a".repeat(LinkPolicy.MAX_LENGTH - base.length());
        assertThatCode(() -> policy("ltz.example").requireAllowed(atLimit)).doesNotThrowAnyException();
        assertRejected(policy("ltz.example"), atLimit + "a");
    }

    @Test
    void izinliListeBossaYalnizYerelAdresKabulEdilir() {
        LinkPolicy local = policy();
        assertThatCode(() -> local.requireAllowed("http://localhost:5001/verify")).doesNotThrowAnyException();
        assertThatCode(() -> local.requireAllowed("http://127.0.0.1:5001/verify")).doesNotThrowAnyException();
        assertThatCode(() -> local.requireAllowed("https://localhost/verify")).doesNotThrowAnyException();
        assertRejected(local, "https://ltz.example/verify");
    }

    @Test
    void izinliListeVarkenYerelAdresKabulEdilmez() {
        assertRejected(policy("ltz.example"), "http://localhost:5001/verify");
    }

    @Test
    void reddedilenBaglantiInvalidLinkKoduIleDoner() {
        assertThatThrownBy(() -> policy("ltz.example").requireAllowed("https://evil.example"))
                .isInstanceOfSatisfying(MailException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(MailErrorCode.INVALID_LINK));
    }
}
