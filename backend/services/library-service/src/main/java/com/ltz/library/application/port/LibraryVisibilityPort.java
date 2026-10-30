package com.ltz.library.application.port;

import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import java.util.UUID;

/** Karar User'a aittir; viewer yalnız doğrulanmış kullanıcı/delegasyon kimliğidir. */
public interface LibraryVisibilityPort {
    LibraryAccessDecision decide(UUID owner, UUID viewer, LibraryAccessPurpose purpose);
}
