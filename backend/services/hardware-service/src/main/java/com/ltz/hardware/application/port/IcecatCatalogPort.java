package com.ltz.hardware.application.port;

import com.ltz.hardware.domain.entity.HardwareCatalogContent;
import com.ltz.hardware.domain.enums.ComponentCategory;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface IcecatCatalogPort {
    record Entry(String id, ComponentCategory category, Instant updatedAt, boolean removed, long row) { }
    record Batch(String generated, long rowOffset, boolean complete, List<Entry> entries) { }
    Map<String, ComponentCategory> taxonomy();
    Batch index(boolean bootstrap, String generated, long rowOffset, Map<String, ComponentCategory> taxonomy);
    HardwareCatalogContent product(Entry entry, Map<String, ComponentCategory> taxonomy);
    byte[] image(HardwareCatalogContent.Asset asset);
}
