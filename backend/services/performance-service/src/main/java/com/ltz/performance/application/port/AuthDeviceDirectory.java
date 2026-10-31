package com.ltz.performance.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Auth Service'in kanonik cihaz kaydına salt-okunur erişim. Cihaz kimliği, sahiplik ve iptal Auth'tadır;
 * Performance kendi cihaz kimliği sistemi kurmaz.
 */
public interface AuthDeviceDirectory {
    record AuthDevice(UUID deviceId, String label) { }

    /**
     * Çağıranın kendi Bearer token'ı ile Auth'tan kullanılabilir (iptal edilmemiş, süresi dolmamış) cihazı arar.
     *
     * @throws com.ltz.performance.domain.exception.AuthServiceUnavailableException Auth yanıt vermezse (kapalı başarısızlık)
     */
    Optional<AuthDevice> findUsableDevice(String authorization, UUID deviceId);
}
