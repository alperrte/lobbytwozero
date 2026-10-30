package com.ltz.library.infrastructure.client;

import com.ltz.library.application.port.LibraryVisibilityPort;
import com.ltz.library.domain.enums.LibraryAccessDecision;
import com.ltz.library.domain.enums.LibraryAccessPurpose;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** User karar API'si henüz yok: üretimde izin üretilmez, tercih kopyalanmaz. */
@Component
public class UnavailableLibraryVisibility implements LibraryVisibilityPort {
    @Override
    public LibraryAccessDecision decide(UUID owner, UUID viewer, LibraryAccessPurpose purpose) {
        return LibraryAccessDecision.UNAVAILABLE;
    }
}
