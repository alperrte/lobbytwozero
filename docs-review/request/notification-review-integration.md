# NOTIFICATION–REVIEW entegrasyon ihtiyacı

## 1. Talep eden servis

`review-service` — Hamza. İhtiyaç belgesidir; Notification sahibine kesin tasarım/geliştirme emri değildir.

## 2. Hedef servis

`notification-service` — ortak servis; sorumlu kişi/ekip kararı bekleniyor.

## 3. İlgili task

REV-10; moderasyon sonucu içerik davranışının ele alınacağı REV-15 ile de ilişkilidir. REV-07 akışındaki yetkili moderasyonun sonucu için hazırlık belgesidir. REV-06 beğeni API'sine yeni bildirim gereksinimi eklemez.

## 4. Talebin amacı

Uygulanmış bir moderasyon eylemi sonucunda inceleme sahibinin güvenli ve tutarlı bilgilendirilmesi. Bu ihtiyaç [SECURITY.md §5](../../docs/SECURITY.md) içindeki “moderasyon sonucu sahibine bildirilir” kuralından gelir; varsayımsal bir beğeni bildirimi talebi değildir.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Notification kaynaklarında](../../backend/services/notification-service/src/main/java/com/ltz/notification) yalnız launcher var; olay alma, alıcı tercihleri, idempotent bildirim oluşturma/teslim controller/service'i bulunmuyor.
- [API kataloğunun NOTIFICATION bölümü](../../docs/api.md) `_Kayıt yok._` diyor.
- [Mimari katalog](../../docs/architecture.md) `ModerationActionTaken` akışını Admin→kaynak servis/Notification için **ÖNERİ** olarak listeliyor; bu uygulanmış bir tüketici sözleşmesi değildir.
- Auth'ta community→notification örneği var; Review→Notification ve Admin→Notification izni mevcut yapılandırmada bulunmuyor. Üretici kararı olmadan bu izinlerin hepsi istenmemeli.
- Review'da bu çalışmada moderasyon, bildirim üretimi veya outbox/relay uygulanmadı. REV-06 kaynak görevi beğeni bildirimini zorunlu kılmıyor; “beğenildi”, rapor gönderildi veya raporlayana sonuç bildirimi bu taleple kapsam kazanmaz.

## 6. Eksik gereksinimler

- Uygulanmış moderasyon sonucunu kabul eden sürümlü ve kimlikli olay/teslim sözleşmesi.
- Doğru inceleme sahibi alıcının seçimi, tercihler ve silinmiş hesap davranışı.
- Aynı action/event için yinelenmeyen bildirim ve kalıcı kabul/başarısızlık anlamı.
- Tek üretici kararı: Review uygulama transaction'ı sonrası üretim veya Admin'in Review uygulama ACK'i sonrası üretimi. İkisinin aynı bildirim üretmesi engellenmeli.

## 7. Önerilen sözleşme — kesinleşmedi

Alıcı sahibinin onayına bağlı öneri: `POST /internal/events`, olay zarfı `eventId`, `eventType`, `eventVersion`, `producer`, `aggregateId`, `occurredAt`, `payload`. Bu endpoint şu an Notification'da **yoktur**.

Olay adı için katalogdaki `ModerationActionTaken` veya uygulandığını açıkça ifade eden `ReviewModerationApplied` değerlendirilebilir; isim/üretici kesinleşmemiştir. Yalnız başarılı uygulama sonrasında: `actionId`, `reviewId`, gerekiyorsa `gameId`, inceleme sahibi `recipientId`, uygulanmış eylem, `appliedAt`, izinli sınırlı `reasonCode` ve yerelleştirme `messageKey` önerilir.

Reporter kimliği/rapor detayı, kaldırılan inceleme gövdesi, e-posta, token, ham kişisel veri veya serbest moderatör notu bildirim payload'ına taşınmamalı. Hedef bağlantısı uygulama içi tip/kimlikten üretilebilir; üreticiden gelen keyfi URL fetch edilmemeli.

Önerilen kalıcı ACK: 200 ve eventId/dedup bilgisi; yalnız inbox+bildirim kaydı commit edildikten sonra. 202 seçilirse kalıcı kabul ve daha sonraki teslim durumunun anlamı açık olmalı. 400 şema, 401 kimlik, 403 yetki/producer, 409 aynı ID farklı içerik, 429/503 kapasite/geçici sorun davranışları öneridir; son kararı Notification sahibi verir.

## 8. Kimlik doğrulama ve yetkilendirme

Auth JWKS/RS256, `typ=svc+jwt`, issuer, tek `aud=notification-service`, onaylı scope, ham zaman claim'leri ve kısa ömür; token sub ile producer eşleşmesi. Hangi producer'ın hangi olay türünü hangi alıcı için üretebileceği allowlist ile daraltılmalı. Kullanıcı token'ı veya iç ağ konumu yeterli değildir.

Review üreticisi seçilirse koşullu Review→Notification izni [Auth talebinde](auth-review-integration.md) değerlendirilir; Admin seçilirse Admin çiftinin gereksinimi ayrıca kararlaştırılır. Kullanıcı yalnız kendi bildirimini okuyup işaretleyebilmeli; gizlilik/tercih/hak sahipliği Notification sorumluluğunda doğrulanmalı.

## 9. Hata ve güvenilirlik davranışı

Sabit eventId ve actionId/recipientId bazlı doğal tekillik; duplicate olay ek bildirim üretmemeli. Gecikmiş karar eski görünürlük durumunu veya silinmiş hesabı geri oluşturmamalı. Bildirim başarısızlığı uygulanmış moderasyonu geri almamalı; teslim ve içerik durumu ayrı tutulmalı.

Gelecekte onaylanırsa üretici outbox'ı ve alıcı inbox/bildirim transaction'ı atomik, kuyruk ve retry bütçesi sınırlı olmalı. Timeout ve 5xx sınırlı retry; 401/403/kalıcı şema hatası görünür operasyonel hata olmalı. Push/e-posta kanalı veya Mail entegrasyonu bu belgeyle varsayılmıyor. Bu çalışma kapsamında outbox/relay kodu yazılmadı.

## 10. Kabul kriterleri

- Gerçek Auth token'ıyla yalnız izinli üreticinin uygulanmış eylemi doğru alıcıya bir kez bildirim oluşturur.
- Admin karar verilmiş fakat Review uygulayamamışsa “uygulandı” bildirimi oluşmaz.
- Tekrar/sıra bozukluğu, timeout, geçici kesinti ve kalıcı ret senaryoları test edilir.
- Yanlış sub/producer/audience/scope ve kullanıcı token'ı reddedilir; başka kişinin bildirimine erişim engellenir.
- Silinmiş hesap, tercihler ve içerik minimizasyonu test edilir; reporter/rapor detayı yazara sızmaz.

## 11. Bağımlılık ve etki

REV-10'un moderasyon bildirim hattı ve ileride REV-15'in sahibini bilgilendirme kriteri sözleşme bekler. REV-06'nın kabulünü engellemez. REV-07 rapor alıcısı Admin olduğundan bildirim sözleşmesi rapor teslimi yerine geçmez.

## 12. Durum

**DOĞRULANMIŞ BİLDİRİM İHTİYACI / SÖZLEŞME BEKLENİYOR — Henüz uygulanmadı.** Üretici ve olay/ACK tasarımı kesinleşmedi; yeni Review entegrasyonu veya hedef servis değişikliği yapılmadı.

## 13. Karar bekleyen konular

Notification sorumlusu, tek üretici, olay adı/sürümü, alıcı alanları/tercihler, gerekçe minimizasyonu, uygulama ACK'i, teslim durumu, dedup ve retention. Bildirim gereksinimini genişletme kararı ilgili servis sahipleri ve kullanıcı onayıyla verilir; Review karşılıklı onaylı sözleşmeye uyarlanır.
