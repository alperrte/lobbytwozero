package com.ltz.mail.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import com.ltz.mail.domain.entity.MailTemplate;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;
import com.ltz.mail.infrastructure.persistence.repository.MailTemplateRepository;

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
    private static final Map<MailLocale, String> FOOTERS = Map.of(
            MailLocale.TR, "Bu e-posta LTZ tarafından otomatik gönderilmiştir; lütfen yanıtlamayın.",
            MailLocale.EN, "This is an automated message from LTZ. Please do not reply.");

    private final MailTemplateRepository templates;
    private final String layout;

    public TemplateRenderer(MailTemplateRepository templates) {
        this.templates = templates;
        try {
            this.layout = StreamUtils.copyToString(new ClassPathResource(LAYOUT_RESOURCE).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Mail layout could not be loaded", exception);
        }
    }

    /** @param locale {@code null} ise iki dilli */
    public RenderedMail render(MailType type, MailLocale locale, MailPayload payload) {
        List<MailLocale> locales = locale == null ? List.of(MailLocale.TR, MailLocale.EN) : List.of(locale);
        List<String> subjects = new ArrayList<>();
        List<String> htmlParts = new ArrayList<>();
        List<String> textParts = new ArrayList<>();
        List<String> footers = new ArrayList<>();
        try {
            for (MailLocale current : locales) {
                MailTemplate template = templates
                        .findFirstByCodeAndLocaleAndActiveTrueOrderByTemplateVersionDesc(type.name(), current.code())
                        .orElseThrow(() -> new IllegalArgumentException("Template not found"));
                Map<String, String> variables = variables(current, payload);
                subjects.add(Placeholders.apply(template.getSubject(), variables, false));
                htmlParts.add(Placeholders.apply(template.getBodyHtml(), variables, true));
                textParts.add(Placeholders.apply(template.getBodyText(), variables, false));
                footers.add(FOOTERS.get(current));
            }
        } catch (IllegalArgumentException _) {
            throw MailDeliveryException.permanent(TEMPLATE_ERROR, null);
        }
        String subject = subject(subjects);
        String html = html(locales, subject, htmlParts, footers);
        String text = String.join(TEXT_SEPARATOR, textParts) + "\n\n-- \n" + String.join("\n", footers);
        return new RenderedMail(subject, html, text);
    }

    private String html(List<MailLocale> locales, String subject, List<String> parts, List<String> footers) {
        Map<String, String> values = new HashMap<>();
        values.put("lang", locales.getFirst().code());
        values.put("title", Placeholders.escape(subject));
        values.put("content", String.join("<hr style=\"border:0;border-top:1px solid #e5e7eb;margin:24px 0\">", parts));
        values.put("footer", String.join("<br>", footers));
        return Placeholders.apply(layout, values, false);
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
