package com.ltz.auth.infrastructure.security;

import java.io.IOException;
import java.util.Locale;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code Accept-Language} başlığının ilk dil etiketini okur ve yalnız {@code tr} ya da {@code en} ise
 * {@link RequestLocaleHolder} ile aynı istekte çalışan koda iletir (mail dili için yedek). Başlık güvenilmez girdidir:
 * uzunluk sınırlıdır, birincil dil alt etiketi tam eşleşme ile doğrulanır, başka her değer sessizce yok sayılır ve
 * ham değer loglanmaz.
 */
public class RequestLocaleFilter extends OncePerRequestFilter {

    static final int MAX_HEADER_LENGTH = 128;

    private final RequestLocaleHolder holder;

    public RequestLocaleFilter(RequestLocaleHolder holder) {
        this.holder = holder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        holder.set(parse(request.getHeader("Accept-Language")));
        try {
            chain.doFilter(request, response);
        } finally {
            holder.clear();
        }
    }

    /** @return {@code tr} ya da {@code en}; başlık yok, çok uzun ya da tanınmıyorsa {@code null} */
    static String parse(String header) {
        if (header == null || header.isBlank() || header.length() > MAX_HEADER_LENGTH) {
            return null;
        }
        String first = header.split(",", 2)[0].split(";", 2)[0].strip();
        String primary = first.split("-", 2)[0].toLowerCase(Locale.ROOT);
        return "tr".equals(primary) || "en".equals(primary) ? primary : null;
    }
}
