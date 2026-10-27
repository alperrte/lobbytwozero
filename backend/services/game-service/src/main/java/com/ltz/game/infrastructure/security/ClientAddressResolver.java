package com.ltz.game.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * İstek sınırı için istemci anahtarı. Doğrudan bağlantı adresi yalnız güvenilir bir proxy'ye (yapılandırılmış CIDR'ler)
 * aitse {@code X-Forwarded-For} dikkate alınır; zincir sağdan sola yürünür ve güvenilir olmayan ilk adres istemci sayılır
 * (en soldaki, istemcinin kendi yazdığı değerlere güvenilmez). Başlıktaki değerler yalnız sayısal IP adresi olabilir;
 * alan adı içeren değer asla çözümlenmez (DNS sorgusu yapılmaz) ve başlığın tamamı yok sayılır.
 * IPv6 adresleri /64 önekiyle gruplanır: tek bir kullanıcı yüzlerce adres üretip sınırı atlatamaz.
 */
final class ClientAddressResolver {
    private static final int MAX_FORWARDED_ENTRIES = 20;
    private final List<IpAddressMatcher> trusted;

    ClientAddressResolver(Set<String> trustedProxies) {
        this.trusted = trustedProxies.stream().map(ClientAddressResolver::matcher).toList();
    }

    String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (peer == null || !isLiteral(peer)) return "unknown";
        if (trusted.isEmpty() || !isTrusted(peer)) return normalize(peer);
        List<String> chain = forwardedChain(request);
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (!isTrusted(chain.get(i))) return normalize(chain.get(i));
        }
        return normalize(peer);
    }

    /** Başlık satırlarının tamamı; biçimi bozuksa veya çok uzunsa boş liste (başlık tamamen yok sayılır). */
    private static List<String> forwardedChain(HttpServletRequest request) {
        List<String> entries = new java.util.ArrayList<>();
        for (String header : Collections.list(request.getHeaders("X-Forwarded-For"))) {
            for (String part : header.split(",", -1)) {
                String value = part.strip();
                if (!isLiteral(value) || entries.size() >= MAX_FORWARDED_ENTRIES) return List.of();
                entries.add(value);
            }
        }
        return entries;
    }

    private boolean isTrusted(String address) {
        return trusted.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private static IpAddressMatcher matcher(String cidr) {
        int slash = cidr.indexOf('/');
        String address = slash < 0 ? cidr : cidr.substring(0, slash);
        if (!isLiteral(address)) throw new IllegalStateException("Geçersiz güvenilir proxy yapılandırması.");
        return new IpAddressMatcher(cidr);
    }

    /** Yalnız sayısal IPv4/IPv6 değişmezi; yan etkisiz ve DNS'e hiç gitmez. */
    static boolean isLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > 45) return false;
        return value.indexOf(':') >= 0 ? isIpv6Literal(value) : isIpv4Literal(value);
    }

    private static boolean isIpv6Literal(String value) {
        long colons = value.chars().filter(c -> c == ':').count();
        if (colons < 2 || !value.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0 && c < 128)) return false;
        try {
            // ':' içeren ve yalnız onaltılık/nokta karakterli metin IPv6 değişmezi olarak ayrıştırılır; DNS'e gitmez.
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException _) {
            return false;
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        return parts.length == 4 && java.util.Arrays.stream(parts).allMatch(ClientAddressResolver::isOctet);
    }

    private static boolean isOctet(String part) {
        return !part.isEmpty() && part.length() <= 3 && part.chars().allMatch(c -> c >= '0' && c <= '9') && Integer.parseInt(part) <= 255;
    }

    /** IPv4 olduğu gibi; IPv6 ve IPv4-eşlemeli adresler çözülerek /64 (IPv6) veya IPv4 olarak anahtarlanır. */
    static String normalize(String address) {
        try {
            byte[] bytes = InetAddress.getByName(address).getAddress();
            if (bytes.length == 4) return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
            StringBuilder prefix = new StringBuilder("v6:");
            for (int i = 0; i < 8; i++) prefix.append(String.format("%02x", bytes[i] & 0xff));
            return prefix.toString();
        } catch (UnknownHostException _) {
            return "unknown";
        }
    }
}
