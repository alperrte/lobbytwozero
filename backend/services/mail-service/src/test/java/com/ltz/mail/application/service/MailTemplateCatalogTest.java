package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.ltz.mail.application.service.MailTemplateDefinition.Block;
import com.ltz.mail.domain.enums.MailLocale;
import com.ltz.mail.domain.enums.MailType;

/** Şablon dosyaları ile bu türlerin taşıdığı yük (kod, bağlantı, süre, zaman) uyumlu olmalı; aksi hâlde çalışma anında TEMPLATE_ERROR olur. */
class MailTemplateCatalogTest {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private static final List<MailType> LINK_TYPES = List.of(MailType.PASSWORD_RESET_LINK, MailType.ACCOUNT_DELETION_CONFIRMATION);

    private final MailTemplateCatalog catalog = new MailTemplateCatalog();

    private static Set<String> allowedVariables(MailType type) {
        if (type == MailType.EMAIL_VERIFICATION) {
            return Set.of("code", "validityMinutes");
        }
        if (LINK_TYPES.contains(type)) {
            return Set.of("link", "validityMinutes");
        }
        return Set.of("occurredAt");
    }

    /** İçeriği istekten gelen türlerin (NOTICE) şablon dosyası yoktur. */
    private static List<MailType> fileBackedTypes() {
        return java.util.Arrays.stream(MailType.values()).filter(type -> !type.isDynamic()).toList();
    }

    @Test
    void everyTypeHasATemplateInEveryLanguage() {
        for (MailType type : fileBackedTypes()) {
            for (MailLocale locale : MailLocale.values()) {
                assertThat(catalog.get(type, locale)).as(type + "." + locale).isNotNull();
            }
        }
    }

    @Test
    void templatesOnlyUseVariablesTheirTypeActuallyCarries() {
        for (MailType type : fileBackedTypes()) {
            for (MailLocale locale : MailLocale.values()) {
                MailTemplateDefinition template = catalog.get(type, locale);
                StringBuilder all = new StringBuilder(template.subject());
                template.blocks().forEach(block -> all.append(' ').append(block.value()));
                Matcher matcher = VARIABLE.matcher(all);
                while (matcher.find()) {
                    assertThat(allowedVariables(type)).as(type + "." + locale + " uses " + matcher.group(1))
                            .contains(matcher.group(1));
                }
            }
        }
    }

    @Test
    void blockKindsMatchWhatTheTypeCarries() {
        for (MailType type : fileBackedTypes()) {
            for (MailLocale locale : MailLocale.values()) {
                List<MailTemplateDefinition.BlockType> blocks = catalog.get(type, locale).blocks().stream()
                        .map(Block::type).toList();
                assertThat(blocks.contains(MailTemplateDefinition.BlockType.CODE)).as(type + " code")
                        .isEqualTo(type == MailType.EMAIL_VERIFICATION);
                assertThat(blocks.contains(MailTemplateDefinition.BlockType.BUTTON)).as(type + " button")
                        .isEqualTo(LINK_TYPES.contains(type));
                boolean needsDate = !LINK_TYPES.contains(type) && type != MailType.EMAIL_VERIFICATION
                        && type != MailType.WELCOME;
                assertThat(blocks.contains(MailTemplateDefinition.BlockType.DATE)).as(type + " date").isEqualTo(needsDate);
            }
        }
    }
}
