package com.ltz.auth.application.port;

/**
 * Auth → Mail: hesap sahibine güvenlik ve durum bildirimi. Bildirim <b>en iyi çabayla</b> teslim edilir: iş değişikliği
 * (parola, e-posta, durum) bundan bağımsız tamamlanır; teslim edilemezse yalnız bildirim türü loglanır, alıcı adresi
 * yazılmaz. Gerçek teslim Mail Service'in sorumluluğudur (MAIL-03, MAIL-04).
 */
public interface SecurityNotificationMailPort {

    /** @throws RuntimeException teslim kabul edilmediyse; çağıran türü loglar ve devam eder */
    void sendSecurityNotification(SecurityNotificationMail mail);
}
