package com.ltz.mail.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ltz.mail.application.service.MailTemplateDefinition.Block;
import com.ltz.mail.application.service.MailTemplateDefinition.BlockType;

/**
 * Şablon dosyası ayrıştırıcısı. Biçim satır tabanlıdır: {@code anahtar: değer}; boş satırlar ve {@code #} ile başlayan
 * satırlar yok sayılır. Başlık anahtarları {@code subject, kind, label, title} (zorunlu) ve {@code note-kind} (isteğe bağlı,
 * varsayılan warn); içerik anahtarları {@code text, code, button, link-fallback, date, note, feature} dosyadaki sırayla
 * blok olur. Değerlerde yalnız {@link #ALLOWED_VARIABLES} kullanılabilir. Hatalı dosya uygulamayı açtırmaz (fail-fast).
 */
final class MailTemplateParser {

    /** Mail Service'in çözebildiği değişkenler ({@link MailPayload} alanlarından üretilir). */
    static final Set<String> ALLOWED_VARIABLES = Set.of("code", "link", "validityMinutes", "occurredAt");

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private static final String DEFAULT_NOTE_KIND = "warn";

    private MailTemplateParser() {
    }

    /** @throws IllegalStateException biçim, anahtar, renk türü ya da değişken hatalıysa (mesaj dosya adını taşır) */
    static MailTemplateDefinition parse(String name, String source) {
        String subject = null;
        String kind = null;
        String label = null;
        String title = null;
        String noteKind = DEFAULT_NOTE_KIND;
        List<Block> blocks = new ArrayList<>();
        String[] lines = source.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw failure(name, index, "expected 'key: value'");
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            if (value.isEmpty()) {
                throw failure(name, index, "empty value for '" + key + "'");
            }
            requireKnownVariables(name, index, value);
            switch (key) {
                case "subject" -> subject = value;
                case "kind" -> kind = value;
                case "label" -> label = value;
                case "title" -> title = value;
                case "note-kind" -> noteKind = value;
                default -> blocks.add(block(name, index, key, value));
            }
        }
        requireKind(name, "kind", kind);
        requireKind(name, "note-kind", noteKind);
        if (subject == null || label == null || title == null) {
            throw new IllegalStateException("Mail template " + name + " needs subject, kind, label and title");
        }
        if (blocks.isEmpty()) {
            throw new IllegalStateException("Mail template " + name + " has no content blocks");
        }
        return new MailTemplateDefinition(subject, kind, label, title, noteKind, blocks);
    }

    private static Block block(String name, int index, String key, String value) {
        BlockType type = BlockType.fromKey(key);
        if (type == null) {
            throw failure(name, index, "unknown key '" + key + "'");
        }
        if (type == BlockType.FEATURE && value.split(" \\| ", -1).length != 2) {
            throw failure(name, index, "feature needs 'title | description'");
        }
        return new Block(type, value);
    }

    private static void requireKind(String name, String field, String kind) {
        if (kind == null || !MailBlocks.isKind(kind)) {
            throw new IllegalStateException("Mail template " + name + ": " + field + " must be accent, warn, danger or success");
        }
    }

    private static void requireKnownVariables(String name, int index, String value) {
        Matcher matcher = VARIABLE.matcher(value);
        while (matcher.find()) {
            if (!ALLOWED_VARIABLES.contains(matcher.group(1))) {
                throw failure(name, index, "unknown variable '" + matcher.group(1) + "'");
            }
        }
    }

    private static IllegalStateException failure(String name, int index, String message) {
        return new IllegalStateException("Mail template " + name + " line " + (index + 1) + ": " + message);
    }
}
