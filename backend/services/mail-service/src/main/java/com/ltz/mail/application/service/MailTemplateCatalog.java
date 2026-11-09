package com.ltz.mail.application.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

/**
 * Şablon kataloğu: her {@link MailType} x {@link MailLocale} için {@code mail/templates/<TÜR>.<dil>.mail} dosyasını açılışta
 * okur ve doğrular. Eksik ya da hatalı dosya uygulamanın açılmasını engeller (fail-fast); çalışma anında şablon aranmaz.
 * Şablon eklemek/değiştirmek için yalnız ilgili dosya düzenlenir; veritabanı ve migration gerekmez.
 */
@Component
public class MailTemplateCatalog {

    private static final String DIRECTORY = "mail/templates/";

    private final Map<MailType, Map<MailLocale, MailTemplateDefinition>> templates = new EnumMap<>(MailType.class);

    public MailTemplateCatalog() {
        for (MailType type : MailType.values()) {
            if (type.isDynamic()) {
                continue;
            }
            Map<MailLocale, MailTemplateDefinition> byLocale = new EnumMap<>(MailLocale.class);
            for (MailLocale locale : MailLocale.values()) {
                String name = type.name() + "." + locale.code() + ".mail";
                byLocale.put(locale, MailTemplateParser.parse(name, read(name)));
            }
            templates.put(type, byLocale);
        }
    }

    MailTemplateDefinition get(MailType type, MailLocale locale) {
        return templates.get(type).get(locale);
    }

    private static String read(String name) {
        ClassPathResource resource = new ClassPathResource(DIRECTORY + name);
        if (!resource.exists()) {
            throw new IllegalStateException("Mail template is missing: " + DIRECTORY + name);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Mail template could not be read: " + name, exception);
        }
    }
}
