package com.ltz.content.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * İçeriğin metin parmak izi (SHA-256, onaltılık, 64 karakter). Büyük/küçük harf ve boşluk farkları yok sayılır, böylece
 * aynı metnin hafifçe değiştirilmiş kopyası da mükerrer sayılır (CONT-18, spam/mükerrer paylaşım).
 */
@Component
public class ContentFingerprint {

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final String SEPARATOR = Character.toString(0x1F);

	public String of(String locale, String title, String summary, String body) {
		String normalized = String.join(SEPARATOR, normalize(locale), normalize(title), normalize(summary),
				normalize(body));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private static String normalize(String text) {
		return text == null ? "" : WHITESPACE.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
	}
}
