package com.ltz.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ltz.mail.application.service.MailTemplateDefinition.BlockType;

class MailTemplateParserTest {

    private static final String HEADER = """
            subject: Konu {{code}}
            kind: accent
            label: ETİKET
            title: Başlık
            """;

    @Test
    void parsesHeadersAndKeepsBlockOrder() {
        MailTemplateDefinition template = MailTemplateParser.parse("A.tr.mail", HEADER + """
                # yorum satırı yok sayılır

                text: Merhaba {{validityMinutes}} dakika
                code: KOD
                date: Tarih:
                note: Dikkat
                feature: BAŞLIK | açıklama
                """);

        assertThat(template.subject()).isEqualTo("Konu {{code}}");
        assertThat(template.kind()).isEqualTo("accent");
        assertThat(template.noteKind()).isEqualTo("warn");
        assertThat(template.blocks()).extracting(MailTemplateDefinition.Block::type).containsExactly(
                BlockType.TEXT, BlockType.CODE, BlockType.DATE, BlockType.NOTE, BlockType.FEATURE);
        assertThat(template.blocks().get(2).value()).isEqualTo("Tarih:");
    }

    @Test
    void noteKindCanBeOverridden() {
        MailTemplateDefinition template = MailTemplateParser.parse("A.tr.mail", HEADER + "note-kind: danger\ntext: x\n");

        assertThat(template.noteKind()).isEqualTo("danger");
    }

    @Test
    void rejectsBrokenTemplatesWithTheFileNameInTheMessage() {
        assertThatThrownBy(() -> MailTemplateParser.parse("B.en.mail", HEADER + "bogus: x\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("B.en.mail").hasMessageContaining("line 5")
                .hasMessageContaining("unknown key 'bogus'");
        assertThatThrownBy(() -> MailTemplateParser.parse("B.en.mail", HEADER + "text: Merhaba {{secret}}\n"))
                .hasMessageContaining("unknown variable 'secret'");
        assertThatThrownBy(() -> MailTemplateParser.parse("B.en.mail", HEADER + "text:\n"))
                .hasMessageContaining("empty value");
        assertThatThrownBy(() -> MailTemplateParser.parse("B.en.mail", HEADER + "satır iki nokta üst üste içermez\n"))
                .hasMessageContaining("expected 'key: value'");
        assertThatThrownBy(() -> MailTemplateParser.parse("B.en.mail", HEADER + "feature: yalnız başlık\n"))
                .hasMessageContaining("feature needs");
    }

    @Test
    void rejectsMissingRequiredPartsAndBadColors() {
        assertThatThrownBy(() -> MailTemplateParser.parse("C.tr.mail", "kind: accent\nlabel: L\ntitle: T\ntext: x\n"))
                .hasMessageContaining("needs subject");
        assertThatThrownBy(() -> MailTemplateParser.parse("C.tr.mail", HEADER.replace("accent", "pink") + "text: x\n"))
                .hasMessageContaining("kind must be accent, warn, danger or success");
        assertThatThrownBy(() -> MailTemplateParser.parse("C.tr.mail", HEADER)).hasMessageContaining("no content blocks");
    }
}
