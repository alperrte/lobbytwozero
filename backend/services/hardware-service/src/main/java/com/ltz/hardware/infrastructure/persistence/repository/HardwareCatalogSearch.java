package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.HardwareBrand;
import com.ltz.hardware.domain.entity.HardwareProduct;
import com.ltz.hardware.domain.enums.CatalogStatus;
import jakarta.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Criteria values are bound parameters; no SQL identifiers come from the request. */
public final class HardwareCatalogSearch {
    private HardwareCatalogSearch() { }

    public record NumericRange(String attribute, Integer min, Integer max) { }
    public record TextValue(String attribute, String value) { }

    public static Specification<HardwareProduct> matching(String model, CatalogStatus status,
            com.ltz.hardware.domain.enums.ComponentCategory category, UUID brandId, String brand,
            List<NumericRange> ranges, List<TextValue> texts) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            contains(cb, root.get("normalizedModel"), model, 160, predicates);
            manufacturer(root, query, cb, brandId, brand, predicates);
            Path<Object> specs = root.get("specifications");
            ranges.forEach(range -> range(cb, specs.get(range.attribute()), range, predicates));
            texts.forEach(text -> exact(cb, specs.get(text.attribute()), text.value(), predicates));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void manufacturer(Root<HardwareProduct> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            UUID brandId, String name, List<Predicate> predicates) {
        if (brandId == null && (name == null || name.isBlank())) return;
        Join<HardwareProduct, HardwareBrand> joined = root.join("brands");
        query.distinct(true);
        predicates.add(cb.equal(joined.get("status"), CatalogStatus.ACTIVE));
        if (brandId != null) predicates.add(cb.equal(joined.get("id"), brandId));
        contains(cb, joined.get("normalizedName"), name, 100, predicates);
    }

    private static void contains(CriteriaBuilder cb, Expression<String> path, String text, int limit,
            List<Predicate> predicates) {
        if (text == null || text.isBlank()) return;
        String escaped = CatalogText.normalize(text, limit).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        predicates.add(cb.like(path, "%" + escaped + "%", '!'));
    }

    private static void range(CriteriaBuilder cb, Expression<Integer> path, NumericRange range,
            List<Predicate> predicates) {
        if (range.min() != null) predicates.add(cb.ge(path, range.min()));
        if (range.max() != null) predicates.add(cb.le(path, range.max()));
    }

    private static void exact(CriteriaBuilder cb, Expression<String> path, String text, List<Predicate> predicates) {
        if (text != null) predicates.add(cb.equal(cb.lower(path), text.strip().toLowerCase(Locale.ROOT)));
    }
}
