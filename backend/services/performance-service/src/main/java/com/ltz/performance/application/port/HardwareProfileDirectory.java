package com.ltz.performance.application.port;

import java.util.UUID;
import java.util.OptionalLong;

/** Hardware Service HW-17 sözleşmesi. Hardware DB'sine erişilmez; sahiplik Hardware'in kararıdır. */
public interface HardwareProfileDirectory {
    /**
     * Profil çağıranın ise {@code true}; yok veya başkasının ise {@code false} (Hardware ikisini ayırmaz).
     * Doğrulama yapılamıyorsa fırlatır (kapalı başarısızlık).
     */
    boolean isOwnedProfile(String authorization, UUID profileId);

    OptionalLong ownedProfileVersion(String authorization, UUID profileId);
}
