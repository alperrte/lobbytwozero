package com.ltz.auth.application.port;

import java.util.Optional;

/**
 * Giriş isteğini yapan tarayıcının rastgele cihaz çerezi (ham değer). İstek dışında (iç çağrı, zamanlanmış iş) boştur;
 * bu durumda "yeni cihaz" denetimi yapılmaz.
 */
public interface LoginDeviceContext {

    Optional<String> deviceToken();
}
