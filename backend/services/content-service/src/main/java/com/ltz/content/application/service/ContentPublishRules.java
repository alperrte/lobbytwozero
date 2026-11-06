package com.ltz.content.application.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Yayın için gereken asgari içerik kuralları (MVP kararı, LTZ-NIS-CONT-01): başlık en az 5, gövde en az 30 karakter; baştaki
 * ve sondaki boşluklar sayılmaz. Bu kurallar yalnız yayın sırasında uygulanır ve taslak kaydetmeyi engellemez: taslakta yalnız
 * başlık zorunludur, özet ve gövde boş olabilir (bkz. {@code ContentTranslation}). Yayınlama ucu sonraki fazdadır (CONT-09);
 * bu sınıf o fazın kullanacağı kuralları ve eşikleri tek yerde tutar. Özet için asgari uzunluk yoktur.
 */
public final class ContentPublishRules {

	public static final int MIN_TITLE_LENGTH = 5;
	public static final int MIN_BODY_LENGTH = 30;

	public enum Issue {
		TITLE_TOO_SHORT,
		BODY_TOO_SHORT
	}

	private ContentPublishRules() {
	}

	/** Yayını engelleyen eksiklerin listesi; boşsa içerik yayınlanabilir. */
	public static List<Issue> check(String title, String body) {
		List<Issue> issues = new ArrayList<>();
		if (lengthOf(title) < MIN_TITLE_LENGTH) {
			issues.add(Issue.TITLE_TOO_SHORT);
		}
		if (lengthOf(body) < MIN_BODY_LENGTH) {
			issues.add(Issue.BODY_TOO_SHORT);
		}
		return issues;
	}

	private static int lengthOf(String text) {
		return text == null ? 0 : text.strip().length();
	}
}
