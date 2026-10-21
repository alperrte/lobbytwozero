package com.ltz.game.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Varsayılan CSRF koruması her istekte açıktır. Yalnız anonim, durumsuz ve salt-okunur toplu kart sorgusu
 * (POST /api/v1/game/games/batch) için CSRF filtresi Spring Security'nin belgelenmiş {@link CsrfFilter#skipRequest}
 * uzantı noktasıyla atlanır: uç çerez/oturum kimlik bilgisi kullanmaz, durum değiştirmez ve kişisel veri döndürmez.
 * Ek savunma: istisna yalnız {@code Content-Type: application/json} isteklerine uygulanır. Bir tarayıcının başka bir
 * siteden çerezle/form olarak gönderebileceği "basit" içerik türleri (form, düz metin) istisnadan yararlanamaz; JSON
 * içerik türü CORS ön kontrolü gerektirir. Başka hiçbir istek bu filtreden etkilenmez.
 */
final class PublicBatchCsrfExemptionFilter extends OncePerRequestFilter {
    private final RequestMatcher publicBatch;

    PublicBatchCsrfExemptionFilter(RequestMatcher publicBatch) { this.publicBatch = publicBatch; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (publicBatch.matches(request) && isJson(request.getContentType())) CsrfFilter.skipRequest(request);
        chain.doFilter(request, response);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        try {
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException _) {
            return false;
        }
    }
}
