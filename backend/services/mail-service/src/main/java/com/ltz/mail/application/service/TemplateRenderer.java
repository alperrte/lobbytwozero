package com.ltz.mail.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.service.MailTemplateDefinition.Block;
import com.ltz.mail.application.service.MailTemplateDefinition.BlockType;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

/**
 * Şablon + yük → gönderime hazır e-posta (MAIL-08/09). Dil verilirse tek dil, verilmezse TR ve EN birlikte
 * (konu "TR / EN", gövde iki bölüm) üretilir. HTML bölümünde değişkenler kaçışlanır; konu satırından satır sonları
 * temizlenir (başlık enjeksiyonu). Şablon veya değişken kusuru kalıcı hatadır ({@code TEMPLATE_ERROR}).
 */
@Component
public class TemplateRenderer {

    static final String TEMPLATE_ERROR = "TEMPLATE_ERROR";
    private static final String LAYOUT_RESOURCE = "mail/layout.html";
    private static final String SUBJECT_SEPARATOR = " / ";
    private static final String TEXT_SEPARATOR = "\n\n----------\n\n";
    private static final int MAX_SUBJECT_LENGTH = 200;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final DateTimeFormatter TURKISH_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ENGLISH_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final String BUTTON_FALLBACK_TR = "Düğme çalışmazsa bu adresi tarayıcına yapıştır:";
    private static final String BUTTON_FALLBACK_EN = "If the button does not work, paste this address into your browser:";
    private static final String BRAND = "LobbyTwoZero";
    private static final String FOOTER_DIVIDER =
            "<hr class=\"hr\" style=\"border:0;border-top:1px solid #34343c;margin:16px 0\">";
    private static final String FOOTER_BRAND_STYLE = "margin:0 0 6px 0;font-family:'Chakra Petch','Bahnschrift',"
            + "'Segoe UI',Arial,sans-serif;font-size:13px;font-weight:700;letter-spacing:1px;color:#f1f1f1";
    private static final Map<MailLocale, FooterCopy> FOOTERS = Map.of(
            MailLocale.TR, new FooterCopy(
                    "Oyunları keşfet, oyuncularla bağlantı kur, deneyimlerini paylaş.",
                    "Bu e-postayı LTZ hesabınla ilgili bir işlem nedeniyle aldın. Otomatik gönderilmiştir; lütfen yanıtlama.",
                    "Gizlilik Politikası, KVKK Aydınlatma Metni ve Kullanım Koşulları LTZ sitesinde yer alır.",
                    "© %d LobbyTwoZero. Tüm hakları saklıdır."),
            MailLocale.EN, new FooterCopy(
                    "Discover games, connect with players, share your experiences.",
                    "You received this email because of an action on your LTZ account. It was sent automatically; "
                            + "please do not reply.",
                    "The Privacy Policy, KVKK Privacy Notice and Terms of Use are available on the LTZ website.",
                    "© %d LobbyTwoZero. All rights reserved."));

    /** Altbilgi metinleri (sitenin footer'ının e-posta karşılığı); telif satırındaki {@code %d} yıldır. */
    private record FooterCopy(String tagline, String reason, String legal, String copyright) {
    }

    private final MailTemplateCatalog templates;
    private final String layout;

    public TemplateRenderer(MailTemplateCatalog templates) {
        this.templates = templates;
        try {
            this.layout = StreamUtils.copyToString(new ClassPathResource(LAYOUT_RESOURCE).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Mail layout could not be loaded", exception);
        }
    }

    /** @param locale {@code null} ise iki dilli ({@code NOTICE} tek dillidir: {@code null} ise {@code tr}) */
    public RenderedMail render(MailType type, MailLocale locale, MailPayload payload) {
        if (type.isDynamic()) {
            return renderNotice(locale == null ? MailLocale.TR : locale, payload);
        }
        List<MailLocale> locales = locale == null ? List.of(MailLocale.TR, MailLocale.EN) : List.of(locale);
        List<String> subjects = new ArrayList<>();
        List<String> htmlParts = new ArrayList<>();
        List<String> textParts = new ArrayList<>();
        List<String> footers = new ArrayList<>();
        List<String> footerTexts = new ArrayList<>();
        try {
            for (MailLocale current : locales) {
                MailTemplateDefinition template = templates.get(type, current);
                Map<String, String> variables = variables(current, payload);
                subjects.add(Placeholders.apply(template.subject(), variables, false));
                Body body = body(template, variables);
                htmlParts.add(body.html());
                textParts.add(body.text());
                footers.add(footerHtml(current));
                footerTexts.add(footerText(current));
            }
        } catch (IllegalArgumentException _) {
            throw MailDeliveryException.permanent(TEMPLATE_ERROR, null);
        }
        String subject = subject(subjects);
        String html = html(locales, subject, htmlParts, footers);
        String text = String.join(TEXT_SEPARATOR, textParts) + "\n\n-- \n" + String.join("\n\n", footerTexts);
        return new RenderedMail(subject, html, text);
    }

    /**
     * Genel bilgilendirme: içerik istekten gelir, görünüm {@link MailBlocks} bileşenlerinden kurulur (renk türü, başlık,
     * paragraflar, isteğe bağlı düğme). Tek dillidir; çağıran içeriği kendi yerelleştirir.
     */
    private RenderedMail renderNotice(MailLocale locale, MailPayload payload) {
        NoticeContent notice = payload.notice();
        if (notice == null) {
            throw MailDeliveryException.permanent(TEMPLATE_ERROR, null);
        }
        boolean turkish = locale == MailLocale.TR;
        StringBuilder html = new StringBuilder(MailBlocks.eyebrow(notice.kind().color(), notice.kind().label(turkish)))
                .append(MailBlocks.heading(notice.title()))
                .append(MailBlocks.paragraphs(notice.message()));
        List<String> text = new ArrayList<>(List.of(notice.title(), notice.message()));
        if (notice.actionLink() != null) {
            html.append(MailBlocks.button(notice.actionLabel(), notice.actionLink()))
                    .append(MailBlocks.linkFallback(turkish ? BUTTON_FALLBACK_TR : BUTTON_FALLBACK_EN,
                            notice.actionLink()));
            text.add(notice.actionLabel() + ": " + notice.actionLink());
        }
        String subject = subject(List.of(notice.title()));
        return new RenderedMail(subject,
                html(List.of(locale), subject, List.of(html.toString()), List.of(footerHtml(locale))),
                String.join("\n", text) + "\n\n-- \n" + footerText(locale));
    }

    /** Şablon bloklarını HTML ve düz metne çevirir; eksik değişken {@link IllegalArgumentException} fırlatır. */
    private static Body body(MailTemplateDefinition template, Map<String, String> variables) {
        StringBuilder html = new StringBuilder(MailBlocks.eyebrow(template.kind(), template.label()))
                .append(MailBlocks.heading(template.title()));
        List<String> text = new ArrayList<>(List.of(template.title()));
        List<String[]> features = new ArrayList<>();
        for (Block block : template.blocks()) {
            if (block.type() == BlockType.FEATURE) {
                String[] parts = Placeholders.apply(block.value(), variables, false).split(" \\| ", 2);
                features.add(parts);
                text.add(parts[0] + ": " + parts[1]);
                continue;
            }
            flushFeatures(html, features);
            appendBlock(html, text, template, block, variables);
        }
        flushFeatures(html, features);
        return new Body(html.toString(), String.join("\n", text));
    }

    private static void flushFeatures(StringBuilder html, List<String[]> features) {
        if (!features.isEmpty()) {
            html.append(MailBlocks.features(features));
            features.clear();
        }
    }

    private static void appendBlock(StringBuilder html, List<String> text, MailTemplateDefinition template, Block block,
                                    Map<String, String> variables) {
        String value = Placeholders.apply(block.value(), variables, false);
        switch (block.type()) {
            case TEXT -> {
                html.append(MailBlocks.paragraphs(value));
                text.add(value);
            }
            case CODE -> {
                String code = Placeholders.apply("{{code}}", variables, false);
                html.append(MailBlocks.codeBox(value, code));
                text.add(value + ": " + code);
            }
            case BUTTON -> {
                String link = Placeholders.apply("{{link}}", variables, false);
                html.append(MailBlocks.button(value, link));
                text.add(value + ": " + link);
            }
            case LINK_FALLBACK -> html.append(MailBlocks.linkFallback(value, Placeholders.apply("{{link}}", variables, false)));
            case DATE -> {
                String date = Placeholders.apply("{{occurredAt}}", variables, false);
                html.append(MailBlocks.info(value, date));
                text.add(value + " " + date);
            }
            case NOTE -> {
                html.append(MailBlocks.note(template.noteKind(), value));
                text.add(value);
            }
            default -> throw new IllegalArgumentException("Unsupported block");
        }
    }

    private record Body(String html, String text) {
    }

    private String html(List<MailLocale> locales, String subject, List<String> parts, List<String> footers) {
        Map<String, String> values = new HashMap<>();
        values.put("lang", locales.getFirst().code());
        values.put("title", Placeholders.escape(subject));
        values.put("content", String.join("<hr class=\"hr\" style=\"border:0;border-top:1px solid #34343c;margin:28px 0\">", parts));
        values.put("footer", String.join(FOOTER_DIVIDER, footers));
        return Placeholders.apply(layout, values, false);
    }

    private static String footerHtml(MailLocale locale) {
        FooterCopy copy = FOOTERS.get(locale);
        return "<p class=\"ft-brand\" style=\"" + FOOTER_BRAND_STYLE + "\">" + BRAND + "</p>"
                + "<p style=\"margin:0 0 10px 0\">" + copy.tagline() + "</p>"
                + "<p style=\"margin:0 0 10px 0\">" + copy.reason() + "</p>"
                + "<p style=\"margin:0 0 10px 0\">" + copy.legal() + "</p>"
                + "<p style=\"margin:0\">" + copyright(copy) + "</p>";
    }

    private static String footerText(MailLocale locale) {
        FooterCopy copy = FOOTERS.get(locale);
        return String.join("\n", BRAND, copy.tagline(), copy.reason(), copy.legal(), copyright(copy));
    }

    private static String copyright(FooterCopy copy) {
        return String.format(Locale.ROOT, copy.copyright(), Year.now(ZoneOffset.UTC).getValue());
    }

    private static String subject(List<String> subjects) {
        String joined = String.join(SUBJECT_SEPARATOR, subjects).replaceAll("[\\r\\n]+", " ").strip();
        return joined.length() > MAX_SUBJECT_LENGTH ? joined.substring(0, MAX_SUBJECT_LENGTH) : joined;
    }

    private static Map<String, String> variables(MailLocale locale, MailPayload payload) {
        Map<String, String> values = new HashMap<>();
        if (payload.code() != null) {
            values.put("code", payload.code());
        }
        if (payload.link() != null) {
            values.put("link", payload.link());
        }
        if (payload.validitySeconds() != null) {
            int minutes = Math.max(1, (payload.validitySeconds() + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
            values.put("validityMinutes", String.valueOf(minutes));
        }
        if (payload.occurredAt() != null) {
            DateTimeFormatter formatter = locale == MailLocale.TR ? TURKISH_TIME : ENGLISH_TIME;
            values.put("occurredAt", formatter.format(payload.occurredAt()));
        }
        return values;
    }
}
