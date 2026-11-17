package com.ltz.library.application.service;

import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import com.ltz.library.domain.exception.LibraryPrivacyUnavailableException;
import com.ltz.library.infrastructure.client.AuthServiceTokenClient;
import com.ltz.library.infrastructure.client.UserServiceLibraryVisibility;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LibraryAccessServiceTest {
    @Test void unconfiguredProductionAdapterNeverGrantsForeignProfileOrRecommendationReads() {
        var adapter = new UserServiceLibraryVisibility(RestClient.builder(), mock(AuthServiceTokenClient.class), "", "/internal/v1/privacy/decisions");
        var service = new LibraryAccessService(adapter);
        UUID owner = UUID.randomUUID();
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(owner, owner, LibraryAccessPurpose.RECOMMENDATION));
        UUID stranger = UUID.randomUUID();
        assertThrows(LibraryPrivacyUnavailableException.class, () -> service.require(owner, stranger, LibraryAccessPurpose.PROFILE));
        assertThrows(LibraryPrivacyUnavailableException.class, () -> service.require(owner, owner, LibraryAccessPurpose.RECOMMENDATION));
        service.require(owner, owner, LibraryAccessPurpose.PROFILE);
    }
}
