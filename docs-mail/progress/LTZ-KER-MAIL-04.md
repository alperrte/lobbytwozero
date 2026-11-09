# LTZ-KER-MAIL-04 — E-posta Arayüzleri ve Şablonların Tamamlanması

Sorumlu: Kerem. Görevler: MAIL-08 (HTML şablonlar), MAIL-09 (TR/EN), MAIL-05 (hoş geldin). Durum: **KOD + TEST TAMAM** (2026-11-09); Auth tarafındaki `WELCOME` tetiği ve gerçek istemci denemesi açık.

## Gerçekleşen durum (2026-11-09)

- **Tasarım:** Sitenin diline göre koyu marka teması (`docs/ltz-palette-and-tokens.md`: `#0B0B0F` zemin, `#141419` kart, `#E11D2E` / `#DB1B60` / `#FF2E88` vurgu). Kabuk `mail/layout.html`: üstte yatay LTZ wordmark şeridi (navbar karşılığı), altta site footer'ının karşılığı (marka, tagline, mailin nedeni, yasal metin notu, telif) ve en altta kırmızı → pembe → kırmızı imza çizgisi. Taban koyu, renkler satır içi (inline); `prefers-color-scheme: light` isteyen istemcilerde açık palete geçer. Mailpit HTML denetimi: özelliklerin ~%89'u desteklenen, ~%10'u kısmi, ~%2'si desteksiz (kısmi olanlar çoğunlukla `<style>`/`@media`; bu yüzden taban inline).
- **Bileşenler:** kategori etiketi, başlık, kod kutusu, düğme, yedek bağlantı, tarih kutusu, uyarı kutusu, özellik satırı. Sınıf adları `layout.html` ile şablon gövdeleri arasında ortaktır.
- **Şablonlar:** 15 tür × TR/EN = 30 satır. V84/V85 (yayınlandı): 14 türün sürüm 2'si ve `WELCOME`; V86 şablon tablosunu kaldırdı, içerik artık dosyada. `ltz-logo.png` artık yatay wordmark (CID adı `ltz-logo` aynı).
- **Ses:** TR metinler "siz" yerine sitenin "sen" diline çevrildi (konu satırları dahil, örn. "LTZ hesabın yasaklandı"). EN konu ve düz metin değişmedi.
- **Altbilgi:** `TemplateRenderer` artık dil başına marka, tagline, mailin nedeni, yasal metin notu (Gizlilik Politikası, KVKK Aydınlatma Metni, Kullanım Koşulları) ve yıllık telif satırı üretir. Yasal metinlere **tıklanabilir bağlantı yok** (canlı alan adı ve site adresi ayarı henüz yok).
- **`WELCOME`:** `MailType.WELCOME`, `SecurityNotificationType.WELCOME`; `security-notifications` ucu `type=WELCOME` kabul eder (yalnız `occurredAt`). `SUSPICIOUS_LOGIN` hâlâ `400`.
- **Şablonlar dosyadır (2026-11-09):** `src/main/resources/mail/templates/<TÜR>.<dil>.mail` (30 dosya), bileşenler `MailBlocks`, katalog `MailTemplateCatalog` (açılışta doğrular), ayrıştırıcı `MailTemplateParser`; düz metin bloklardan otomatik üretilir. `mail_templates` tablosu V86 ile kaldırıldı (V84/V85 geçmişte kalır). Şablon eklemek/değiştirmek için migration gerekmez; bkz. [sablon-rehberi.md](../sablon-rehberi.md).
- **Doğrulama:** `mvn -pl services/mail-service test` 71 test PASS, Checkstyle/PMD temiz; görünüm gerçek renderer çıktısıyla ekran görüntüsünde önceki tasarımla birebir doğrulandı.

## Açık işler

- **Auth tetiği:** e-posta doğrulaması tamamlanınca `type=WELCOME` gönderimi (Auth tarafı ayrı iş; sözleşme `docs/api.md` → MAIL → security-notifications).
- **Yasal bağlantılar:** canlı alan adı belli olunca Mail Service'e site adresi ayarı (`docker-compose.yml` + `.env.example`, ortak dosya, onay ister) ve altbilgi bağlantıları.
- **Dil/tema tercihi:** User Service `user_preferences.locale` var; Auth Mail'e `locale` göndermiyor, mailler iki dilli gidiyor. Tema (koyu/açık) tercihi taşınmıyor; taban koyu, açık tema istemci tercihine bırakıldı (karar 2026-11-09).
- **Gerçek istemci denemesi:** Gmail ve Outlook'ta görünüm elle doğrulanmadı (yalnız Mailpit ve Edge).
- **Henüz şablonu olmayanlar:** şüpheli giriş / yeni cihaz, silme süreci bildirimleri (planlandı, iptal, tamamlandı), etkinlik ve bildirim e-postaları (MAIL-06/07, ertelendi; çağıran servis ve izin tablosu gerekir).
- **Sonraki migration:** V87 (aralık V81–V90).
