package com.ltz.mail.application.service;

import java.util.List;

/**
 * Bir e-posta şablonunun (tür + dil) bildirimsel tanımı; {@code mail/templates/<TÜR>.<dil>.mail} dosyasından okunur.
 *
 * @param subject  konu ({@code {{değişken}}} içerebilir)
 * @param kind     kategori rengi: accent, warn, danger, success
 * @param label    başlık üstündeki küçük kategori etiketi
 * @param title    ana başlık
 * @param noteKind uyarı kutusunun renk türü
 * @param blocks   içerik blokları, dosyadaki sırayla
 */
record MailTemplateDefinition(String subject, String kind, String label, String title, String noteKind,
                              List<Block> blocks) {

    MailTemplateDefinition {
        blocks = List.copyOf(blocks);
    }

    /** İçerik blok türleri; {@code key} dosyadaki anahtardır. */
    enum BlockType {
        TEXT("text"), CODE("code"), BUTTON("button"), LINK_FALLBACK("link-fallback"), DATE("date"), NOTE("note"),
        FEATURE("feature");

        private final String key;

        BlockType(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        static BlockType fromKey(String key) {
            for (BlockType type : values()) {
                if (type.key.equals(key)) {
                    return type;
                }
            }
            return null;
        }
    }

    /** @param value etiket/metin; {@link BlockType#FEATURE} için "başlık | açıklama" */
    record Block(BlockType type, String value) {
    }
}
