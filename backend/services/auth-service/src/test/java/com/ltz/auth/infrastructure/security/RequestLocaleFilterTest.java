package com.ltz.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLocaleFilterTest {

    @ParameterizedTest
    @CsvSource({
            "tr, tr",
            "en, en",
            "EN-gb, en",
            "tr-TR, tr",
            "'tr-TR,tr;q=0.9,en;q=0.8', tr",
            "'en;q=0.8', en",
            "' en ', en"
    })
    void taninanDilBirincilEtiketeIndirilir(String header, String expected) {
        assertThat(RequestLocaleFilter.parse(header)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "*", "fr-FR,tr;q=0.9", "tr_TR", "trx", "<script>", "tr\r\nX-Injected: 1"})
    void taninmayanDegerYokSayilir(String header) {
        assertThat(RequestLocaleFilter.parse(header)).isNull();
    }

    @Test
    void baslikYokBosYadaCokUzunsaYokSayilir() {
        assertThat(RequestLocaleFilter.parse(null)).isNull();
        assertThat(RequestLocaleFilter.parse("  ")).isNull();
        assertThat(RequestLocaleFilter.parse("tr" + ",en".repeat(RequestLocaleFilter.MAX_HEADER_LENGTH))).isNull();
    }

    @Test
    void dilIstekBoyuncaGorunurIstekBitinceSilinir() throws Exception {
        RequestLocaleHolder holder = new RequestLocaleHolder();
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");

        new RequestLocaleFilter(holder).doFilter(request, new MockHttpServletResponse(),
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override
                    protected void service(jakarta.servlet.http.HttpServletRequest req,
                                           jakarta.servlet.http.HttpServletResponse res) {
                        seen.set(holder.locale().orElse(null));
                    }
                }));

        assertThat(seen.get()).isEqualTo("en");
        assertThat(holder.locale()).isEmpty();
    }
}
