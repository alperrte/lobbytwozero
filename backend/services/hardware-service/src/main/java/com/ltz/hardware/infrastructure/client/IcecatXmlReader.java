package com.ltz.hardware.infrastructure.client;

import com.ltz.hardware.application.port.IcecatCatalogPort.Batch;
import com.ltz.hardware.application.port.IcecatCatalogPort.Entry;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Streaming parser. DTDs and entity expansion are forbidden, including on reference files. */
public final class IcecatXmlReader {
    private IcecatXmlReader() { }
    private static XMLStreamReader open(InputStream input) throws XMLStreamException {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        return factory.createXMLStreamReader(input);
    }

    public static Map<String, ComponentCategory> taxonomy(InputStream input) throws XMLStreamException {
        var reader = open(input);
        var result = new HashMap<String, ComponentCategory>();
        String id = null;
        int depth = 0;
        try {
            while (reader.hasNext()) {
                int event = next(reader);
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                    id = taxonomyElement(reader, id, depth, result);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("Category".equals(reader.getLocalName())) id = null;
                    depth--;
                }
            }
        } finally { reader.close(); }
        if (!result.values().containsAll(java.util.List.of(ComponentCategory.CPU, ComponentCategory.MOTHERBOARD,
                ComponentCategory.RAM, ComponentCategory.STORAGE))) throw new XMLStreamException("Incomplete category mapping");
        return Map.copyOf(result);
    }

    private static int next(XMLStreamReader reader) throws XMLStreamException {
        int event = reader.next();
        if (event == XMLStreamConstants.DTD) throw new XMLStreamException("DTD not allowed");
        return event;
    }

    private static String taxonomyElement(XMLStreamReader reader, String id, int depth,
            Map<String, ComponentCategory> result) {
        if ("Category".equals(reader.getLocalName())) id = reader.getAttributeValue(null, "ID");
        if (id != null && "Name".equals(reader.getLocalName())
                && "1".equals(reader.getAttributeValue(null, "langid")) && depth <= 4) {
            var category = category(reader.getAttributeValue(null, "Value"));
            if (category != null) result.put(id, category);
        }
        return id;
    }

    private static ComponentCategory category(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "processors" -> ComponentCategory.CPU;
            case "motherboards" -> ComponentCategory.MOTHERBOARD;
            case "memory modules", "memory" -> ComponentCategory.RAM;
            case "internal solid state drives", "solid state drives", "internal ssds", "external ssds",
                    "internal hard drives", "internal hard disk drives", "external hard drives" -> ComponentCategory.STORAGE;
            default -> null;
        };
    }

    public static Batch index(InputStream input, String previous, long offset, int limit,
            Map<String, ComponentCategory> categories) throws XMLStreamException {
        var reader = open(input);
        var entries = new ArrayList<Entry>();
        String generated = null;
        long row = 0;
        long start = offset;
        try {
            while (reader.hasNext()) {
                int event = next(reader);
                if (event == XMLStreamConstants.START_ELEMENT && "files.index".equals(reader.getLocalName())) {
                    generated = generation(reader);
                    if (!generated.equals(previous)) start = 0;
                }
                if (event == XMLStreamConstants.START_ELEMENT && "file".equals(reader.getLocalName()) && ++row > start) {
                    appendEntry(reader, categories, row, entries);
                    if (pageFull(entries.size(), limit, row - start)) return new Batch(generated, row, false, entries);
                }
            }
            if (generated == null) throw new XMLStreamException("Missing index generation");
            return new Batch(generated, row, true, entries);
        } finally { reader.close(); }
    }

    private static void appendEntry(XMLStreamReader reader, Map<String, ComponentCategory> categories,
            long row, java.util.List<Entry> entries) throws XMLStreamException {
        var entry = indexEntry(reader, categories, row);
        if (entry != null) entries.add(entry);
    }

    private static String generation(XMLStreamReader reader) throws XMLStreamException {
        String value = reader.getAttributeValue(null, "Generated");
        if (value == null || !value.matches("\\d{14}")) throw new XMLStreamException("Invalid index generation");
        return value;
    }

    private static Entry indexEntry(XMLStreamReader reader, Map<String, ComponentCategory> categories, long row)
            throws XMLStreamException {
        var category = categories.get(reader.getAttributeValue(null, "Catid"));
        String quality = reader.getAttributeValue(null, "Quality");
        boolean removed = "REMOVED".equals(quality);
        if (category == null || "Yes".equalsIgnoreCase(reader.getAttributeValue(null, "Limited"))
                || !(removed || "ICECAT".equals(quality) || "SUPPLIER".equals(quality))) return null;
        String id = reader.getAttributeValue(null, "Product_ID");
        if (id == null || !id.matches("\\d{1,20}")) throw new XMLStreamException("Invalid source identity");
        return new Entry(id, category, timestamp(reader.getAttributeValue(null, "Updated")), removed, row);
    }

    private static boolean pageFull(int size, int limit, long scanned) {
        return size >= limit || scanned >= 100000;
    }

    public static Instant timestamp(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toInstant(ZoneOffset.UTC);
    }
}
