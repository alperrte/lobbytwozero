package com.ltz.hardware.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.Test;

class IcecatXmlReaderTest {
    private static final String GENERATION = "20261109000000";

    private static ByteArrayInputStream input(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void taxonomyMapsEnglishCategoriesAndIgnoresOtherLanguagesAndUnknownNames() throws Exception {
        var result = IcecatXmlReader.taxonomy(input("""
                <Categories>
                  <Category ID="1"><Name langid="1" Value="Processors"/></Category>
                  <Category ID="2"><Name langid="1" Value="Motherboards"/></Category>
                  <Category ID="3"><Name langid="1" Value="Memory"/></Category>
                  <Category ID="4"><Name langid="1" Value="Internal SSDs"/></Category>
                  <Category ID="5"><Name langid="2" Value="Processors"/></Category>
                  <Category ID="6"><Name langid="1" Value="Unknown"/><Name langid="1"/></Category>
                </Categories>
                """));
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("1", ComponentCategory.CPU,
                "2", ComponentCategory.MOTHERBOARD, "3", ComponentCategory.RAM, "4", ComponentCategory.STORAGE));
        assertThatThrownBy(() -> IcecatXmlReader.taxonomy(input("<Categories/>")))
                .hasMessage("Incomplete category mapping");
    }

    @Test
    void indexPreservesCursorFilteringAndRemovedEntries() throws Exception {
        String xml = """
                <files.index Generated="20261109000000">
                  <file Product_ID="1" Catid="cpu" Quality="ICECAT"/>
                  <file Product_ID="2" Catid="cpu" Quality="ICECAT" Limited="Yes"/>
                  <file Product_ID="3" Catid="other" Quality="ICECAT"/>
                  <file Product_ID="4" Catid="cpu" Quality="OTHER"/>
                  <file Product_ID="5" Catid="cpu" Quality="REMOVED" Updated="20261108000000"/>
                  <file Product_ID="6" Catid="cpu" Quality="SUPPLIER"/>
                </files.index>
                """;
        var categories = Map.of("cpu", ComponentCategory.CPU);
        var first = IcecatXmlReader.index(input(xml), GENERATION, 0, 1, categories);
        assertThat(first.complete()).isFalse();
        assertThat(first.rowOffset()).isEqualTo(1);
        assertThat(first.entries()).extracting("id").containsExactly("1");
        var rest = IcecatXmlReader.index(input(xml), GENERATION, first.rowOffset(), 10, categories);
        assertThat(rest.complete()).isTrue();
        assertThat(rest.rowOffset()).isEqualTo(6);
        assertThat(rest.entries()).extracting("id").containsExactly("5", "6");
        assertThat(rest.entries().getFirst().removed()).isTrue();
        assertThat(rest.entries().getFirst().updatedAt()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));
        var reset = IcecatXmlReader.index(input(xml), "older-generation", 100, 1, categories);
        assertThat(reset.entries()).extracting("id").containsExactly("1");
    }

    @Test
    void rejectsDtdInvalidGenerationAndInvalidSourceIds() {
        var categories = Map.of("cpu", ComponentCategory.CPU);
        String dtd = "<!DOCTYPE root [<!ENTITY private 'blocked'>]><root/>";
        assertThatThrownBy(() -> IcecatXmlReader.taxonomy(input(dtd))).isInstanceOf(XMLStreamException.class);
        assertThatThrownBy(() -> IcecatXmlReader.index(input(dtd), null, 0, 10, categories))
                .isInstanceOf(XMLStreamException.class);
        for (String xml : new String[] {"<files.index/>", "<files.index Generated='bad'/>", "<root/>",
                "<files.index Generated='20261109000000'><file Catid='cpu' Quality='ICECAT'/></files.index>",
                "<files.index Generated='20261109000000'><file Product_ID='bad' Catid='cpu' Quality='ICECAT'/></files.index>"}) {
            assertThatThrownBy(() -> IcecatXmlReader.index(input(xml), null, 0, 10, categories))
                    .isInstanceOf(XMLStreamException.class);
        }
        assertThat(IcecatXmlReader.timestamp(null)).isNull();
        assertThat(IcecatXmlReader.timestamp(" ")).isNull();
    }
}
