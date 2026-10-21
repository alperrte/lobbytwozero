package com.ltz.gateway.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Enumeration;

/** İstek başlıklarını silen veya ekleyen sarmalayıcı; aşağı akıştaki rota filtreleri değişmiş başlıkları görür. */
final class HeaderOverrideRequest extends HttpServletRequestWrapper {

    private final Set<String> removed;
    private final Map<String, String> added = new LinkedHashMap<>();

    HeaderOverrideRequest(HttpServletRequest request, Set<String> removedLowerCaseNames) {
        super(request);
        this.removed = removedLowerCaseNames;
    }

    HeaderOverrideRequest add(String name, String value) {
        added.put(name.toLowerCase(Locale.ROOT), value);
        return this;
    }

    @Override
    public String getHeader(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (added.containsKey(key)) {
            return added.get(key);
        }
        return removed.contains(key) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (added.containsKey(key)) {
            return Collections.enumeration(List.of(added.get(key)));
        }
        return removed.contains(key) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original != null && original.hasMoreElements()) {
            String name = original.nextElement();
            if (!removed.contains(name.toLowerCase(Locale.ROOT))) {
                names.add(name);
            }
        }
        added.keySet().forEach(names::add);
        return Collections.enumeration(names);
    }
}
