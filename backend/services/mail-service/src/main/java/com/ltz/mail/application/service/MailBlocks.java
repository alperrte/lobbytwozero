package com.ltz.mail.application.service;

import java.util.List;
import java.util.Map;

/**
 * E-posta içerik bileşenleri (tek yerde): etiket, başlık, paragraf, kod kutusu, düğme, yedek bağlantı, tarih kutusu, uyarı
 * kutusu ve özellik satırları. Şablon dosyaları ({@code mail/templates/*.mail}) ve genel bildirim bu bileşenlerden kurulur;
 * böylece görünüm değişikliği tek dosyada yapılır, şablonlar yalnız içerik taşır. Renkler satır içi (inline) koyu temadır;
 * {@code class} adları {@code mail/layout.html} içindeki açık tema iyileştirmesiyle ortaktır. Bütün metin parametreleri
 * DÜZ METİNDİR ve burada HTML-kaçışlanır (dış değer biçimlendirmeyi bozamaz).
 */
final class MailBlocks {

    private static final String DISPLAY = "'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif";
    private static final String BOX = "background:#1e1f26;border:1px solid #34343c;";
    private static final Map<String, String> EYEBROW_COLORS = Map.of(
            "accent", "#ff2e88", "warn", "#f59e0b", "danger", "#ff5a68", "success", "#22c55e");
    private static final Map<String, String> NOTE_BORDERS = Map.of(
            "accent", "#ff2e88", "warn", "#f59e0b", "danger", "#e11d2e", "success", "#22c55e");

    private MailBlocks() {
    }

    /** Geçerli {@code kind} değerleri: accent, warn, danger, success. */
    static boolean isKind(String kind) {
        return EYEBROW_COLORS.containsKey(kind);
    }

    static String eyebrow(String kind, String label) {
        return "<p class=\"eb-" + kind + "\" style=\"margin:0 0 10px 0;font-family:" + DISPLAY
                + ";font-size:12px;font-weight:700;letter-spacing:2.5px;color:" + EYEBROW_COLORS.get(kind) + "\">"
                + Placeholders.escape(label) + "</p>";
    }

    static String heading(String title) {
        return "<h1 class=\"h1\" style=\"margin:0 0 16px 0;font-family:" + DISPLAY
                + ";font-size:28px;line-height:1.2;font-weight:700;color:#f1f1f1\">" + Placeholders.escape(title) + "</h1>";
    }

    /** Boş satırla ayrılan her bölüm ayrı paragraf olur; tek satır sonu {@code <br>} olur. */
    static String paragraphs(String text) {
        StringBuilder html = new StringBuilder();
        for (String part : text.split("\\R{2,}")) {
            if (!part.isBlank()) {
                html.append("<p style=\"margin:0 0 18px 0\">")
                        .append(Placeholders.escape(part.strip()).replaceAll("\\R", "<br>")).append("</p>");
            }
        }
        return html.toString();
    }

    static String codeBox(String label, String code) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 20px 0\">"
                + "<tr><td class=\"box\" align=\"center\" style=\"padding:20px 16px;" + BOX + "border-radius:12px\">"
                + "<p class=\"muted\" style=\"margin:0 0 8px 0;font-family:" + DISPLAY
                + ";font-size:11px;font-weight:700;letter-spacing:2.5px;color:#a0a7b4\">" + Placeholders.escape(label) + "</p>"
                + "<p class=\"code\" style=\"margin:0;font-size:36px;line-height:1.2;letter-spacing:10px;font-weight:700;"
                + "font-family:Consolas,Menlo,'Courier New',monospace;color:#ff2e88\">" + Placeholders.escape(code)
                + "</p></td></tr></table>";
    }

    static String button(String label, String link) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 18px 0\"><tr>"
                + "<td bgcolor=\"#e11d2e\" style=\"background:#e11d2e;border-radius:10px\"><a href=\""
                + Placeholders.escape(link) + "\" style=\"display:inline-block;padding:14px 32px;font-family:" + DISPLAY
                + ";font-size:15px;font-weight:700;letter-spacing:0.5px;line-height:1.2;color:#ffffff;text-decoration:none;"
                + "border-radius:10px;background:#e11d2e\">" + Placeholders.escape(label) + "</a></td></tr></table>";
    }

    static String linkFallback(String intro, String link) {
        return "<p class=\"muted\" style=\"margin:0 0 22px 0;font-size:13px;line-height:1.5;color:#a0a7b4\">"
                + Placeholders.escape(intro) + "<br><span style=\"word-break:break-all\">" + Placeholders.escape(link)
                + "</span></p>";
    }

    static String info(String label, String value) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 22px 0\">"
                + "<tr><td class=\"box\" style=\"padding:13px 18px;" + BOX + "border-radius:10px;font-size:14px\">"
                + "<span class=\"muted\" style=\"color:#a0a7b4\">" + Placeholders.escape(label)
                + "</span>&nbsp;&nbsp;<strong>" + Placeholders.escape(value) + "</strong></td></tr></table>";
    }

    static String note(String kind, String text) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:4px 0 0 0\">"
                + "<tr><td class=\"note\" style=\"padding:14px 18px;background:#1e1f26;border-left:4px solid "
                + NOTE_BORDERS.get(kind) + ";border-radius:6px;font-size:14px;line-height:1.55\">"
                + Placeholders.escape(text) + "</td></tr></table>";
    }

    /** @param items her öğe {başlık, açıklama} */
    static String features(List<String[]> items) {
        StringBuilder html = new StringBuilder(
                "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 12px 0\">");
        for (String[] item : items) {
            html.append("<tr><td class=\"box\" style=\"padding:14px 18px;").append(BOX)
                    .append("border-radius:10px;font-size:14px;line-height:1.5\"><p class=\"eb-accent\" style=\"margin:0 0 4px 0;")
                    .append("font-family:").append(DISPLAY).append(";font-size:11px;font-weight:700;letter-spacing:2.5px;")
                    .append("color:#ff2e88\">").append(Placeholders.escape(item[0])).append("</p><p style=\"margin:0\">")
                    .append(Placeholders.escape(item[1])).append("</p></td></tr><tr><td height=\"8\" ")
                    .append("style=\"height:8px;line-height:8px;font-size:0\">&nbsp;</td></tr>");
        }
        return html.append("</table>").toString();
    }
}
