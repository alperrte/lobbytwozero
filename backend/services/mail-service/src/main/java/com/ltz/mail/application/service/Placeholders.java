package com.ltz.mail.application.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code {{ad}}} yer tutucu çözücüsü. Bilinmeyen/eksik değişken hata verir (şablon kusuru sessizce boş e-posta
 * üretmesin). HTML bölümünde değerler kaçışlanır: kod/bağlantı gibi dış değerler biçimlendirmeyi bozamaz.
 */
final class Placeholders {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z]+)}}");

    private Placeholders() {
    }

    /**
     * @param escapeHtml {@code true} ise değerler HTML-kaçışlanır
     * @throws IllegalArgumentException şablondaki değişkenin değeri yoksa
     */
    static String apply(String template, Map<String, String> values, boolean escapeHtml) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Template variable has no value: " + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(escapeHtml ? escape(value) : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }
}
