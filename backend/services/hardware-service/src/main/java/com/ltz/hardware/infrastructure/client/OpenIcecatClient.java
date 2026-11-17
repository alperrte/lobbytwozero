package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.IcecatCatalogPort;
import com.ltz.hardware.domain.entity.HardwareCatalogContent;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.infrastructure.config.IcecatProperties;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLStreamException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenIcecatClient implements IcecatCatalogPort {
    private final IcecatHttpClient http;
    private final IcecatProperties properties;
    private final IcecatProductParser parser;
    private final ObjectMapper mapper;
    public OpenIcecatClient(IcecatHttpClient http, IcecatProperties properties, IcecatProductParser parser, ObjectMapper mapper) {
        this.http = http; this.properties = properties; this.parser = parser; this.mapper = mapper;
    }
    private void ready() {
        if (!properties.ready()) throw new IllegalStateException("ICECAT_DISABLED_OR_UNVERIFIED");
    }
    @Override public Map<String, ComponentCategory> taxonomy() {
        ready();
        return http.get(URI.create("https://data.icecat.biz/export/freexml/refs/CategoriesList.xml.gz"),
                16_000_000, true, input -> {
                    try (var gzip = new GZIPInputStream(input);
                            var bounded = IcecatHttpClient.bounded(gzip, 64_000_000)) {
                        return IcecatXmlReader.taxonomy(bounded);
                    } catch (IOException | XMLStreamException exception) {
                        throw new IllegalStateException("ICECAT_TAXONOMY_INVALID", exception);
                    }
                });
    }
    @Override public Batch index(boolean bootstrap, String generated, long offset, Map<String, ComponentCategory> taxonomy) {
        ready();
        var uri = URI.create("https://data.icecat.biz/export/freexml/EN/"
                + (bootstrap ? "files.index" : "daily.index") + ".xml.gz");
        return http.get(uri, 512_000_000, true, input -> {
            try (var gzip = new GZIPInputStream(input);
                    var bounded = IcecatHttpClient.bounded(gzip, 2_000_000_000)) {
                return IcecatXmlReader.index(bounded, generated, offset, properties.getBatchSize(), taxonomy);
            } catch (IOException | XMLStreamException exception) {
                throw new IllegalStateException("ICECAT_INDEX_INVALID", exception);
            }
        });
    }
    @Override public HardwareCatalogContent product(Entry entry, Map<String, ComponentCategory> taxonomy) {
        ready();
        if (!entry.id().matches("\\d{1,20}")) throw new IllegalArgumentException("ICECAT_ID_INVALID");
        return http.get(URI.create("https://live.icecat.biz/api?lang=EN&icecat_id=" + entry.id()
                + "&content=generalinfo,image,gallery,featuregroups"), 4_000_000, true,
                input -> parser.parse(mapper.readTree(input), entry, taxonomy));
    }
    @Override public byte[] image(HardwareCatalogContent.Asset asset) {
        ready();
        if (!properties.isMediaRightsVerified()) throw new IllegalStateException("ICECAT_MEDIA_RIGHTS_REQUIRED");
        return http.get(asset.download(), 5_000_000, false, input -> {
            try { return input.readNBytes(5_000_001); }
            catch (IOException exception) { throw new IllegalStateException("ICECAT_MEDIA_UNAVAILABLE", exception); }
        });
    }
}
