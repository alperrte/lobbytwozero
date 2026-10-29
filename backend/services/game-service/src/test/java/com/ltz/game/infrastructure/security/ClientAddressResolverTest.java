package com.ltz.game.infrastructure.security;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** İstemci anahtarı: sahte X-Forwarded-For ile sınır atlatılamaz; alan adı değerleri çözümlenmez. */
class ClientAddressResolverTest {
    static MockHttpServletRequest request(String remote, String... forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        for (String value : forwarded) request.addHeader("X-Forwarded-For", value);
        return request;
    }

    final ClientAddressResolver none = new ClientAddressResolver(Set.of());
    final ClientAddressResolver gateway = new ClientAddressResolver(Set.of("10.0.0.0/8", "172.16.0.0/12"));

    @Test void withoutTrustedProxiesTheHeaderIsAlwaysIgnored() {
        assertEquals("203.0.113.9", none.resolve(request("203.0.113.9", "1.2.3.4")));
        assertEquals("10.0.0.5", none.resolve(request("10.0.0.5", "1.2.3.4, 5.6.7.8")));
    }

    @Test void untrustedPeerCannotInjectAClientAddress() {
        assertEquals("198.51.100.7", gateway.resolve(request("198.51.100.7", "10.0.0.1", "203.0.113.50")));
    }

    @Test void trustedPeerYieldsTheRightmostUntrustedAddressAndIgnoresClientForgedLeftEntries() {
        assertEquals("203.0.113.50", gateway.resolve(request("10.1.2.3", "203.0.113.50")));
        // İstemci başa sahte adres yazdı; güvenilir proxy gerçek istemciyi sona ekledi.
        assertEquals("203.0.113.50", gateway.resolve(request("10.1.2.3", "9.9.9.9, 203.0.113.50")));
        assertEquals("203.0.113.50", gateway.resolve(request("10.1.2.3", "9.9.9.9", "203.0.113.50, 172.20.0.4")));
        // Zincir tamamen güvenilir adreslerden oluşuyorsa doğrudan bağlantı kullanılır.
        assertEquals("10.1.2.3", gateway.resolve(request("10.1.2.3", "10.0.0.9, 172.17.0.2")));
        assertEquals("10.1.2.3", gateway.resolve(request("10.1.2.3")));
    }

    @Test void malformedOrNonNumericEntriesDisableTheWholeHeaderAndNeverTriggerNameResolution() {
        for (String bad : new String[] {"evil.example", "203.0.113.50, evil.example", "999.1.1.1", "1.2.3", "1.2.3.4.5", "", ",", "::::", "fe80::1%eth0",
                "0x7f.0.0.1", "1.2.3.4:80", "localhost", "a".repeat(100), "[::1]", "203.0.113.50 203.0.113.51"})
            assertEquals("10.1.2.3", gateway.resolve(request("10.1.2.3", bad)), bad);
        var tooMany = new StringBuilder();
        for (int i = 0; i < 30; i++) tooMany.append(i == 0 ? "" : ", ").append("203.0.113.").append(i + 1);
        assertEquals("10.1.2.3", gateway.resolve(request("10.1.2.3", tooMany.toString())));
    }

    @Test void ipv6ClientsAreGroupedBy64BitPrefixAndIpv4MappedAddressesCollapse() {
        assertEquals(gateway.resolve(request("10.1.2.3", "2001:db8:1:2::1")), gateway.resolve(request("10.1.2.3", "2001:db8:1:2:ffff:ffff:ffff:ffff")));
        assertNotEquals(gateway.resolve(request("10.1.2.3", "2001:db8:1:2::1")), gateway.resolve(request("10.1.2.3", "2001:db8:1:3::1")));
        assertTrue(gateway.resolve(request("10.1.2.3", "2001:db8:1:2::1")).startsWith("v6:"));
        assertEquals("203.0.113.7", gateway.resolve(request("10.1.2.3", "::ffff:203.0.113.7")));
        assertEquals("203.0.113.7", none.resolve(request("::ffff:203.0.113.7")));
    }

    @Test void misconfiguredTrustedProxiesFailFast() {
        for (String bad : new String[] {"localhost", "evil.example/24", "not-an-ip", "10.0.0/8"})
            assertMisconfigured(Set.of(bad), bad);
    }

    private static void assertMisconfigured(Set<String> proxies, String message) {
        assertThrows(IllegalStateException.class, () -> new ClientAddressResolver(proxies), message);
    }

    @Test void literalDetectionNeverAcceptsNames() {
        assertTrue(ClientAddressResolver.isLiteral("203.0.113.7"));
        assertTrue(ClientAddressResolver.isLiteral("2001:db8::1"));
        assertFalse(ClientAddressResolver.isLiteral("example.com"));
        assertFalse(ClientAddressResolver.isLiteral(null));
        assertFalse(ClientAddressResolver.isLiteral("1.2.3.256"));
        assertFalse(ClientAddressResolver.isLiteral("::::"));
        assertFalse(ClientAddressResolver.isLiteral("1:2:3:4:5:6:7:8:9"));
    }
}
