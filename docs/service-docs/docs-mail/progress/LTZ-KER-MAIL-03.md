# LTZ-KER-MAIL-03 — Etkinlik ve Bildirim E-postaları

Sorumlu: Kerem. Görevler: MAIL-06, 07. Durum: **ERTELENDİ** (2026-10-31). Event, notification ve admin servisleri iskelet; Mail'e çağrı yok, Auth'ta çağıran→mail çifti tanımsız.
İlke: Mail'e yalnız Auth istek atar. İstek doğunca bu faz açılır; şablon altyapısı (MAIL-02) buna hazır tutulur (`type`↔`sub` izin tablosu genişletilebilir). Aşağıdaki "karar" referansları eski numaralamadır; güncel liste `decisions/kararlar.md` D14.

## Kapsam
- **MAIL-06:** `EVENT_INVITE`, `EVENT_REMINDER`, `EVENT_CANCELLED` şablonları (TR/EN). Yalnız kullanıcının **izin verdiği** gönderim; izin kararı Mail'de değil (öneri: notification_preferences EMAIL kanalı). Hatırlatma zamanlaması event-service'in (EventReminderDue); Mail zamanlamaz.
- **MAIL-07:** `NOTIFICATION` genel şablonu: notification-service tercihe bakıp çağırır; Mail yalnız render + teslim. İçerik gizlilik tercihine uyar (mesaj önizlemesi yok).
- Abonelikten çıkış: bildirim/etkinlik e-postalarında tercih ekranına bağlantı (frontend adresi allowlist), pazarlama niteliği yok.

## Ön koşullar
1. K2 (alıcı e-postası kaynağı) ve K6 (kim çağırır) kararı; Auth'ta `event/notification → mail` ve gerekirse `mail → auth` token çiftleri.
2. MAIL-01 ve MAIL-02 fazları bitmiş olmalı.
3. Event/notification olay sözleşmesi `docs/api.md`'de kendi servis başlıklarında tanımlanmış olmalı.

## İş kırılımı
1. V83 seed şablonlar; `type` ↔ `sub` izin matrisi genişler (event → `EVENT_*`, notification → `NOTIFICATION`).
2. Oran sınırı: kullanıcı başına saatlik e-posta üst sınırı (spam/e-posta bombası koruması; alıcıyı çağıranın kötüye kullanması).
3. Testler: izin matrisi (yanlış `sub` 403), oran sınırı, TR/EN render, escape, idempotent yeniden çağrı, sızıntı yok.
4. `docs/api.md` güncellemesi + Nisa/ortak sahibe sözleşme bildirimi.
