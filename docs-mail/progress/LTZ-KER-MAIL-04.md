# LTZ-KER-MAIL-04 — E-posta Arayüzleri ve Şablonların Tamamlanması

Sorumlu: Kerem. Görevler: MAIL-08 (HTML şablonlar), MAIL-09 (TR/EN), MAIL-05 (hoş geldin). Durum: **KOD + TEST TAMAM** (2026-11-09); Auth tarafındaki `WELCOME` tetiği ve gerçek istemci denemesi açık.

## Gerçekleşen durum (2026-11-09)

- **Tasarım:** Sitenin diline göre koyu marka teması (`docs/ltz-palette-and-tokens.md`: `#0B0B0F` zemin, `#141419` kart, `#E11D2E` / `#DB1B60` / `#FF2E88` vurgu). Kabuk `mail/layout.html`: üstte yatay LTZ wordmark şeridi (navbar karşılığı), altta site footer'ının karşılığı (marka, tagline, mailin nedeni, yasal metin notu, telif) ve en altta kırmızı → pembe → kırmızı imza çizgisi. Taban koyu, renkler satır içi (inline); `prefers-color-scheme: light` isteyen istemcilerde açık palete geçer. Mailpit HTML denetimi: özelliklerin ~%89'u desteklenen, ~%10'u kısmi, ~%2'si desteksiz (kısmi olanlar çoğunlukla `<style>`/`@media`; bu yüzden taban inline).
- **Bileşenler:** kategori etiketi, başlık, kod kutusu, düğme, yedek bağlantı, tarih kutusu, uyarı kutusu, özellik satırı. Sınıf adları `layout.html` ile şablon gövdeleri arasında ortaktır.
- **Şablonlar:** 15 tür × TR/EN = 30 satır. V84/V85 (yayınlandı): 14 türün sürüm 2'si ve `WELCOME`; V86 şablon tablosunu kaldırdı, içerik artık dosyada. `ltz-logo.png` artık yatay wordmark (CID adı `ltz-logo` aynı).
- **Ses:** TR metinler "siz" yerine sitenin "sen" diline çevrildi (konu satırları dahil, örn. "LTZ hesabın yasaklandı"). EN konu ve düz metin değişmedi.
- **Altbilgi:** `TemplateRenderer` artık dil başına marka, tagline, mailin nedeni, yasal metin notu (Gizlilik Politikası, KVKK Aydınlatma Metni, Kullanım Koşulları) ve yıllık telif satırı üretir. Yasal metinlere **tıklanabilir bağlantı yok** (canlı alan adı ve site adresi ayarı henüz yok).
- **`WELCOME`:** `MailType.WELCOME`, `SecurityNotificationType.WELCOME`; `security-notifications` ucu `type=WELCOME` kabul eder (yalnız `occurredAt`). `SUSPICIOUS_LOGIN` ve `ACCOUNT_DELETION_CANCELLED` V88 ile eklendi (2026-11-18; yalnız `occurredAt`, TR/EN şablon, V88 gerçek PostgreSQL testli); Auth tetikleyicileri bekliyor.
- **Şablonlar dosyadır (2026-11-09):** `src/main/resources/mail/templates/<TÜR>.<dil>.mail` (30 dosya), bileşenler `MailBlocks`, katalog `MailTemplateCatalog` (açılışta doğrular), ayrıştırıcı `MailTemplateParser`; düz metin bloklardan otomatik üretilir. `mail_templates` tablosu V86 ile kaldırıldı (V84/V85 geçmişte kalır). Şablon eklemek/değiştirmek için migration gerekmez; bkz. [sablon-rehberi.md](../sablon-rehberi.md).
- **Doğrulama:** `mvn -pl services/mail-service test` 71 test PASS, Checkstyle/PMD temiz; görünüm gerçek renderer çıktısıyla ekran görüntüsünde önceki tasarımla birebir doğrulandı.

## Platform kapısı (2026-11-09)

Auth dışındaki servislerin kullanıcıya bilgilendirme maili attırması: `POST /internal/v1/mail/notices` (kapsam `mail:notify`). Rehber: [entegrasyon-rehberi.md](../entegrasyon-rehberi.md), ajan özeti: [AJAN-OZETI.md](../AJAN-OZETI.md), sözleşme: `docs/api.md` → MAIL.

- **V87:** `NOTICE` türü, `caller` sütunu, bekleyen kayıtta adres yerine yalnız `accountId`, hız sınırı indeksi.
- **Alıcı adresi saklanmaz:** servis yalnız `accountId` verir; işleyici gönderim anında Auth'tan çözer (`AuthRecipientResolver`: token + önbellek + `GET /internal/v1/accounts/{id}/contact`), bellekte kullanır. Adres yok / doğrulanmamış / hesap `ACTIVE` değil / hesap yok → kalıcı atla; Auth kapalı, `5xx`, `401`, `403` → kuyrukta bekler, yeniden denenir.
- **Güvenlik:** çağıran izin tablosu (`PlatformProperties`, varsayılan boş = kapalı); JWT `sub` Auth ya da kayıtlı servis, kapsam uç başına; Auth platform ucuna, platform servisi Auth'un uçlarına giremez; içerik düz metin ve kaçışlanır, kontrol/yön değiştirme karakteri yasak, düğme adresi https + izinli host; çağıran+hesap ve çağıran başına saatlik hız sınırı (`429`); idempotency; hesap silme bu kayıtları da siler; Auth anahtarı yoksa uç `503` (fail-closed); prod'da platform servisi varsa Auth anahtarı zorunlu (`MailStartupGuard`).
- **Doğrulama:** Mail Service 111 test PASS, Checkstyle/PMD temiz (`NoticeControllerTest`, `NoticeDispatchTest`, `AuthRecipientResolverTest` sahte Auth sunucusuyla, `PlatformPropertiesTest`, `PayloadCodecTest`). Gerçek Auth ile uçtan uca **NOT VERIFIED**: ilk çağıran servis Auth'ta `mail:notify` hedefi alınca denenecek.

## Açık işler

- **Auth tetiği (KAPANDI, kodda doğrulandı 2026-11-16):** `EmailVerificationService` e-posta doğrulanınca `WELCOME` gönderir; Google girişi de aynı türü kullanır. Uçtan uca (Mailpit) deneme NOT VERIFIED.
- **Yasal bağlantılar:** canlı alan adı belli olunca Mail Service'e site adresi ayarı (`docker-compose.yml` + `.env.example`, ortak dosya, onay ister) ve altbilgi bağlantıları. **Karar (2026-11-18, Kerem):** henüz DEV'deyiz, canlı alan adı gelene kadar bekler.
- **SMTP sağlayıcısı:** DEV/test için Gmail SMTP kullanılır (karar 2026-11-18; uygulama şifresi, kurulum satırları `.env.example`'da). Kişisel Gmail'in günlük gönderim sınırı düşüktür (yaklaşık 500, doğrulanmadı): canlı için ayrı sağlayıcı kararı gerekir. Gerçek Gmail gönderimi henüz denenmedi (NOT VERIFIED).
- **Dil/tema tercihi:** User Service `user_preferences.locale` ve `GET /internal/v1/users/{userId}/locale` main'de; Auth (`HttpUserLocaleClient`, `HttpMailServiceAdapter`) `locale` değerini Mail'e ekler (kodda ve DEV'de uçtan uca doğrulandı 2026-11-16; yalnız `AUTH_MAIL_ADAPTER=mail-service` ile, `dev-smtp`'de bu zincir kapalı). Dil bilinmiyorsa mail iki dilli gider. Tema (koyu/açık) tercihi taşınmıyor; taban koyu, açık tema istemci tercihine bırakıldı (karar 2026-11-09).
- **Gerçek istemci denemesi:** Gmail ve Outlook'ta görünüm elle doğrulanmadı (yalnız Mailpit ve Edge).
- **Şablonu eklenenler (V88, 2026-11-18):** `SUSPICIOUS_LOGIN` (yeni cihaz) ve `ACCOUNT_DELETION_CANCELLED`; Auth tarafı bekliyor. Silme talebi onayı zaten var (`ACCOUNT_DELETION_CONFIRMATION`). **"Silme tamamlandı" maili yapılamaz:** Auth bekleme süresi bitince e-postayı hemen anonimleştirir (`AccountDeletionCoordinator.claimDue`), Mail ise alıcı adresini gönderim anında Auth'tan çözer; adres o zamana kadar yoktur. İstenirse adresi saklamayan mevcut tasarımı değiştiren ayrı bir karar gerekir. Etkinlik ve bildirim e-postaları (MAIL-06/07) için genel platform ucu hazır (aşağıda); özel şablonlar çağıran çıkınca `.mail` dosyası olarak eklenir.
- **Sonraki migration:** V89 (aralık V81–V90).
