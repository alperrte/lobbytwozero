package com.ltz.content.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentFingerprintTest {

	private final ContentFingerprint fingerprint = new ContentFingerprint();

	@Test
	void producesA64CharacterHexDigest() {
		assertThat(fingerprint.of("tr", "Başlık", "Özet", "Gövde")).matches("[0-9a-f]{64}");
	}

	@Test
	void preservesStoredFingerprintCompatibility() {
		assertThat(fingerprint.of("tr", "Title", "Summary", "Body"))
				.isEqualTo("228e2e4f12d43c32b356fbdf5993fc366c3f067872e577570dc1c18250a8a812");
	}

	@Test
	void caseAndWhitespaceDifferencesAreIgnored() {
		String original = fingerprint.of("tr", "Yeni Guncelleme Cikti", "Kisa ozet", "Govde metni burada");

		assertThat(fingerprint.of("tr", "  yeni   guncelleme cikti ", "KISA OZET", "Govde\n\nmetni  burada"))
				.isEqualTo(original);
	}

	@Test
	void differentTextOrLocaleChangesTheDigest() {
		String original = fingerprint.of("tr", "Başlık", "Özet", "Gövde");

		assertThat(fingerprint.of("tr", "Başlık", "Özet", "Gövde 2")).isNotEqualTo(original);
		assertThat(fingerprint.of("en", "Başlık", "Özet", "Gövde")).isNotEqualTo(original);
	}

	@Test
	void fieldBoundariesAreNotAmbiguous() {
		// "ab" + "c" ile "a" + "bc" aynı birleşik metni vermesin.
		assertThat(fingerprint.of("tr", "ab", "c", null)).isNotEqualTo(fingerprint.of("tr", "a", "bc", null));
	}

	@Test
	void missingFieldsAreTreatedAsEmpty() {
		assertThat(fingerprint.of("tr", "Başlık", null, null)).isEqualTo(fingerprint.of("tr", "Başlık", "", " "));
	}
}
