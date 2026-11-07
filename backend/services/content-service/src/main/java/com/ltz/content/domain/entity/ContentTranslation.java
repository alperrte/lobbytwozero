package com.ltz.content.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * İçeriğin bir dildeki metni. Başlık zorunludur; özet ve gövde taslakta boş (null) olabilir. Metin düz metindir
 * (HTML/Markdown yorumlanmaz); buradaki sınırlar ve izinli karakterler taslak içindir, yayın için daha sıkı kurallar
 * {@code ContentPublishRules}'tadır.
 */
@Entity
@Table(name = "content_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTranslation {

	public static final int TITLE_MAX_LENGTH = 150;
	public static final int SUMMARY_MAX_LENGTH = 500;
	public static final int BODY_MAX_LENGTH = 20_000;

	/** Başlık tek satırdır: satır sonu ve sekme dahil tüm kontrol karakterleri reddedilir. */
	public static final String TITLE_ALLOWED_CHARACTERS = "[^\\x00-\\x1F\\x7F]*";

	/** Özet ve gövde için: satır sonu ve sekme dışındaki kontrol karakterleri reddedilir. */
	public static final String TEXT_ALLOWED_CHARACTERS = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*";

	@EmbeddedId
	private ContentTranslationId id;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(length = SUMMARY_MAX_LENGTH)
	private String summary;

	@Column
	private String body;

	public static ContentTranslation of(UUID contentId, String locale, String title, String summary, String body) {
		ContentTranslation translation = new ContentTranslation();
		translation.id = new ContentTranslationId(contentId, locale);
		translation.title = title;
		translation.summary = summary;
		translation.body = body;
		return translation;
	}

	/** Verilen metinleri uygular; {@code summary} ve {@code body} null ise ilgili alan temizlenir. */
	public void rewrite(String newTitle, String newSummary, String newBody) {
		this.title = newTitle;
		this.summary = newSummary;
		this.body = newBody;
	}
}
