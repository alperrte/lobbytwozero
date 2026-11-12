package com.ltz.hardware.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.ltz.hardware.infrastructure.config.IcecatProperties;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IcecatHttpClientTest {
    private static final URI OFFICIAL = URI.create("https://data.icecat.biz/product");

    @Test
    void rejectsUnsafeAuthoritiesBeforeResolvingDns() {
        for (String value : List.of("http://data.icecat.biz/p", "https://example.test/p",
                "https://name@data.icecat.biz/p", "https://data.icecat.biz/p#fragment",
                "https://data.icecat.biz:444/p")) {
            URI uri = URI.create(value);
            assertThatThrownBy(() -> IcecatHttpClient.requireOfficial(uri))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("ICECAT_UNSAFE_URL");
        }
    }

    @Test
    void permitsPublicAddressesAndRejectsPrivateIpv4AndIpv6WithoutNetwork() throws Exception {
        var publicAddress = InetAddress.getByAddress(new byte[] {8, 8, 8, 8});
        var addresses = List.of(new byte[] {0, 0, 0, 0}, new byte[] {127, 0, 0, 1},
                new byte[] {10, 0, 0, 1}, new byte[] {(byte) 169, (byte) 254, 0, 1},
                new byte[] {(byte) 224, 0, 0, 1},
                new byte[] {(byte) 0xfc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        for (byte[] bytes : addresses) {
            var address = InetAddress.getByAddress(bytes);
            try (var dns = mockStatic(InetAddress.class)) {
                dns.when(() -> InetAddress.getAllByName("data.icecat.biz"))
                        .thenReturn(new InetAddress[] {publicAddress, address});
                assertThatThrownBy(() -> IcecatHttpClient.requireOfficial(OFFICIAL))
                        .hasMessage("ICECAT_UNSAFE_ADDRESS");
            }
        }
        try (var dns = mockStatic(InetAddress.class)) {
            dns.when(() -> InetAddress.getAllByName("data.icecat.biz"))
                    .thenReturn(new InetAddress[] {publicAddress});
            IcecatHttpClient.requireOfficial(OFFICIAL);
            IcecatHttpClient.requireOfficial(URI.create("https://data.icecat.biz:443/p"));
            dns.when(() -> InetAddress.getAllByName("data.icecat.biz"))
                    .thenThrow(new UnknownHostException("synthetic-dns-failure"));
            assertThatThrownBy(() -> IcecatHttpClient.requireOfficial(OFFICIAL))
                    .hasMessage("ICECAT_DNS_UNAVAILABLE").hasCauseInstanceOf(UnknownHostException.class);
        }
    }

    @Test
    void boundedStreamsCountSingleAndBulkReads() throws Exception {
        try (var input = IcecatHttpClient.bounded(new ByteArrayInputStream(new byte[] {1, 2, 3}), 2)) {
            assertThat(input.read()).isEqualTo(1);
            assertThat(input.read(new byte[1], 0, 1)).isEqualTo(1);
            assertThatThrownBy(input::read).hasMessage("ICECAT_RESPONSE_LIMIT");
        }
    }

    @Test
    void lifecycleClosesOwnedHttpClient() {
        var client = new IcecatHttpClient(new IcecatProperties());
        try (var http = (HttpClient) ReflectionTestUtils.getField(client, "http")) {
            client.close();
            assertThat(http).isNotNull();
            assertThat(http.isTerminated()).isTrue();
        }
    }
}
