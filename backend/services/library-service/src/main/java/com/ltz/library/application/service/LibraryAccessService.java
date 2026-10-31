package com.ltz.library.application.service;

import com.ltz.library.application.port.LibraryVisibilityPort;
import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import com.ltz.library.domain.exception.LibraryPrivacyUnavailableException;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class LibraryAccessService {
    private final LibraryVisibilityPort visibility;

    public LibraryAccessService(LibraryVisibilityPort visibility) { this.visibility = visibility; }

    public void require(UUID owner, UUID viewer, LibraryAccessPurpose purpose) {
        if (purpose == LibraryAccessPurpose.PROFILE && owner.equals(viewer)) return;
        LibraryAccessDecision decision;
        try { decision = visibility.decide(owner, viewer, purpose); }
        catch (RuntimeException _) { throw new LibraryPrivacyUnavailableException(); }
        if (decision == LibraryAccessDecision.DENY) throw new AccessDeniedException("Koleksiyon görünür değil.");
        if (decision != LibraryAccessDecision.ALLOW) throw new LibraryPrivacyUnavailableException();
    }
}
