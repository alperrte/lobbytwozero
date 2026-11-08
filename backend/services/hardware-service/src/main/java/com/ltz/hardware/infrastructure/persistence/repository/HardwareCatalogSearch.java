package com.ltz.hardware.infrastructure.persistence.repository;

import com.ltz.hardware.domain.entity.CatalogText;
import com.ltz.hardware.domain.entity.HardwareBrand;
import com.ltz.hardware.domain.entity.HardwareProduct;
import com.ltz.hardware.domain.entity.HardwareProductSourceView;
import com.ltz.hardware.domain.enums.CatalogStatus;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
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
    /** Exact match on a key inside the source's typed_specs JSON; {@code key} always comes from a server-side allowlist. */
    public record TypedSpec(String key, String text, Integer number) {
        public boolean active() { return (text != null && !text.isBlank()) || number != null; }
    }

    public static Specification<HardwareProduct> matching(String model, CatalogStatus status,
            com.ltz.hardware.domain.enums.ComponentCategory category, UUID brandId, String brand,
            List<NumericRange> ranges, List<TextValue> texts) {
        return matching(model, status, category, brandId, brand, ranges, texts, List.of());
    }

    /** The key must hold a non-blank value that is not the literal {@code false}; {@code required=false} adds no condition. */
    public record TypedPresence(String key, boolean required) { }

    public static Specification<HardwareProduct> matching(String model, CatalogStatus status,
            com.ltz.hardware.domain.enums.ComponentCategory category, UUID brandId, String brand,
            List<NumericRange> ranges, List<TextValue> texts, List<TypedSpec> typed) {
        return matching(model, status, category, brandId, brand, ranges, texts, typed, List.of());
    }

    public static Specification<HardwareProduct> matching(String model, CatalogStatus status,
            com.ltz.hardware.domain.enums.ComponentCategory category, UUID brandId, String brand,
            List<NumericRange> ranges, List<TextValue> texts, List<TypedSpec> typed, List<TypedPresence> present) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), status));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            search(root, query, cb, model, predicates);
            manufacturer(root, query, cb, brandId, brand, predicates);
            Path<Object> specs = root.get("specifications");
            ranges.forEach(range -> range(cb, specs.get(range.attribute()), range, predicates));
            texts.forEach(text -> exact(cb, specs.get(text.attribute()), text.value(), predicates));
            typedSpecs(root, query, cb, typed, present, predicates);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void typedSpecs(Root<HardwareProduct> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<TypedSpec> typed, List<TypedPresence> present, List<Predicate> predicates) {
        List<TypedSpec> active = typed.stream().filter(TypedSpec::active).toList();
        List<TypedPresence> required = present.stream().filter(TypedPresence::required).toList();
        if (active.isEmpty() && required.isEmpty()) return;
        Subquery<UUID> sources = query.subquery(UUID.class);
        Root<HardwareProductSourceView> source = sources.from(HardwareProductSourceView.class);
        List<Predicate> inner = new ArrayList<>();
        inner.add(cb.equal(source.get("quality"), "VERIFIED"));
        for (TypedSpec spec : active) {
            if (spec.number() != null) {
                Expression<String> value = cb.function("jsonb_extract_path_text", String.class,
                        source.get("typedSpecs"), cb.literal(spec.key()), cb.literal("number"));
                inner.add(cb.equal(((org.hibernate.query.criteria.JpaExpression<String>) value).cast(BigDecimal.class),
                        BigDecimal.valueOf(spec.number())));
            } else {
                Expression<String> value = cb.function("jsonb_extract_path_text", String.class,
                        source.get("typedSpecs"), cb.literal(spec.key()), cb.literal("text"));
                inner.add(cb.equal(cb.lower(value), spec.text().strip().toLowerCase(Locale.ROOT)));
            }
        }
        for (TypedPresence spec : required) {
            Expression<String> value = cb.function("jsonb_extract_path_text", String.class,
                    source.get("typedSpecs"), cb.literal(spec.key()), cb.literal("text"));
            inner.add(cb.isNotNull(value));
            inner.add(cb.notEqual(cb.trim(value), ""));
            inner.add(cb.notEqual(cb.lower(cb.trim(value)), "false"));
        }
        sources.select(source.get("productId")).where(inner.toArray(Predicate[]::new));
        predicates.add(root.get("id").in(sources));
    }

    private static final int MAX_SEARCH_TOKENS = 8;
    /** Tokens shorter than this match only at the start of a word; longer ones may match anywhere in a word. */
    private static final int SUBSTRING_FROM = 3;

    /**
     * Free-text search: the text is split on spaces and every token must match the model or one of its
     * brands, so {@code "amd ryzen 98"} finds the Ryzen 9 9800X3D. A short token such as {@code "r"} matches only
     * the start of a word (model words begin after a space or hyphen); a token of three or more
     * characters also matches inside a word ({@code "x3d"}).
     */
    private static void search(Root<HardwareProduct> root, CriteriaQuery<?> query, CriteriaBuilder cb, String text,
            List<Predicate> predicates) {
        if (text == null || text.isBlank()) return;
        String[] tokens = CatalogText.normalize(text, 160).split(" ");
        for (int index = 0; index < tokens.length && index < MAX_SEARCH_TOKENS; index++) {
            String token = tokens[index].replace("!", "!!").replace("%", "!%").replace("_", "!_");
            if (token.isEmpty()) continue;
            Subquery<UUID> brands = query.subquery(UUID.class);
            Root<HardwareProduct> owner = brands.from(HardwareProduct.class);
            Join<HardwareProduct, HardwareBrand> brand = owner.join("brands");
            brands.select(owner.get("id")).where(cb.equal(owner.get("id"), root.get("id")),
                    cb.equal(brand.get("status"), CatalogStatus.ACTIVE),
                    wordMatch(cb, brand.get("normalizedName"), token));
            predicates.add(cb.or(wordMatch(cb, root.get("normalizedModel"), token), cb.exists(brands)));
        }
    }

    /** {@code token} is already LIKE-escaped with {@code '!'}. */
    private static Predicate wordMatch(CriteriaBuilder cb, Expression<String> path, String token) {
        // A token that does not start with a letter or digit (an escaped "!"/"%"/"_" or punctuation) has no word start to anchor to.
        if (token.length() >= SUBSTRING_FROM || !Character.isLetterOrDigit(token.codePointAt(0))) {
            return cb.like(path, "%" + token + "%", '!');
        }
        return cb.or(cb.like(path, token + "%", '!'), cb.like(path, "% " + token + "%", '!'),
                cb.like(path, "%-" + token + "%", '!'));
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
