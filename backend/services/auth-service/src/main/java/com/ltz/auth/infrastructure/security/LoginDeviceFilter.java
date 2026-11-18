package com.ltz.auth.infrastructure.security;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Giriş uçlarında (parola, 2FA, Google/Steam) tarayıcının cihaz çerezini okur; yoksa ya da biçimi geçersizse rastgele
 * yenisini üretir ve {@link LoginDeviceHolder} ile aynı istekte çalışan oturum açma koduna iletir. Çerezi YAZMAZ:
 * yalnız oturum başarıyla kurulunca {@code SessionCookies.write} yazar, böylece başarısız girişte çerez verilmez.
 */
public class LoginDeviceFilter extends OncePerRequestFilter {

    static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final String cookieName;
    private final LoginDeviceHolder holder;
    private final SecureTokens secureTokens;
    private final String[] pathPrefixes;

    public LoginDeviceFilter(String cookieName, LoginDeviceHolder holder, SecureTokens secureTokens,
                             String... pathPrefixes) {
        this.cookieName = cookieName;
        this.holder = holder;
        this.secureTokens = secureTokens;
        this.pathPrefixes = pathPrefixes.clone();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : pathPrefixes) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = Optional.ofNullable(WebUtils.getCookie(request, cookieName))
                .map(Cookie::getValue).filter(value -> TOKEN.matcher(value).matches())
                .orElseGet(secureTokens::newOpaqueToken);
        holder.set(token);
        try {
            chain.doFilter(request, response);
        } finally {
            holder.clear();
        }
    }
}
