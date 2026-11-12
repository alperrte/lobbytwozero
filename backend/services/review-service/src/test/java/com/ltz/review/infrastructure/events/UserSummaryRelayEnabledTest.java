package com.ltz.review.infrastructure.events;

import com.ltz.review.application.port.GameCatalogPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Secret yapılandırıldığında (yalnız test özelliği) yayıcı kurulur; zamanlayıcı dış servislere bağlanmaz (adresler kapalı yerel port). */
@SpringBootTest(properties = {
        "ltz.review.user-events.client-secret=test-only-secret",
        "ltz.review.user-events.auth-url=http://127.0.0.1:9",
        "ltz.review.user-events.user-url=http://127.0.0.1:9",
        "ltz.review.user-events.interval=1h"})
class UserSummaryRelayEnabledTest {
    @Autowired ApplicationContext context;
    @MockitoBean GameCatalogPort games;

    @Test void relayAndTokenClientAreCreatedOnlyWhenTheSecretIsConfigured() {
        assertEquals(1, context.getBeanNamesForType(UserSummaryRelay.class).length);
        assertEquals(1, context.getBeanNamesForType(ServiceTokenClient.class).length);
    }
}
