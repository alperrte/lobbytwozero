package com.ltz.library.application.service;

import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import com.ltz.library.domain.exception.LibraryPrivacyUnavailableException;
import com.ltz.library.infrastructure.client.UnavailableLibraryVisibility;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryAccessServiceTest {
    @Test void productionAdapterNeverGrantsForeignProfileOrRecommendationReads() {
        var adapter = new UnavailableLibraryVisibility();
        var service = new LibraryAccessService(adapter);
        UUID owner = UUID.randomUUID();
        assertEquals(LibraryAccessDecision.UNAVAILABLE, adapter.decide(owner, owner, LibraryAccessPurpose.RECOMMENDATION));
        assertThrows(LibraryPrivacyUnavailableException.class, () -> service.require(owner, UUID.randomUUID(), LibraryAccessPurpose.PROFILE));
        assertThrows(LibraryPrivacyUnavailableException.class, () -> service.require(owner, owner, LibraryAccessPurpose.RECOMMENDATION));
        service.require(owner, owner, LibraryAccessPurpose.PROFILE);
    }
}
