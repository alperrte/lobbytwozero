# ADMIN–REVIEW entegrasyon talebi

## 1. Talep eden servis

`review-service` — Hamza. Bu belge bir ihtiyaç/sözleşme taslağıdır; hedef servisin mimarisini belirleyen emir değildir.

## 2. Hedef servis

`admin-service` — ortak servis; sorumlu ekip/kişi henüz atanmalı. Bu çalışmada Admin koduna müdahale edilmedi.

## 3. İlgili task

REV-07 ve REV-10. Kararın içeriğe uygulanması, görünürlük/puan etkisi gibi davranışlar ileride REV-15 kapsamıyla birlikte netleşir; bu belge Faz 4 kodlaması değildir.

## 4. Talebin amacı

Review'de gelecekte oluşturulacak uygunsuz inceleme raporlarının Admin'e güvenli teslimi, durumlarının Admin tarafından yönetimi ve yetkili kararların kaynağa güvenilir aktarımı. Review kendi inceleme/rapor verisinin, Admin moderasyon vakasının sahibi kalmalı.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Admin Java kaynakları](../../backend/services/admin-service/src/main/java/com/ltz/admin): yalnız `AdminServiceApplication` launcher var; rapor alma, vaka, karar ve geri bildirim controller/service'i bulunmuyor.
- [API kataloğunun ADMIN bölümü](../../docs/api.md): `_Kayıt yok._`
- [Mimari olay kataloğu](../../docs/architecture.md) `ContentReported` ve `ModerationActionTaken` akışlarını **ÖNERİ** olarak listeliyor. Community'nin önerilmiş `POST /internal/events` teslim modeli hazır Admin alıcısı kanıtı değildir.
- Auth yapılandırmasında Review→Admin ve Admin→Review çiftleri yok; [Auth talebi](auth-review-integration.md) ile birlikte ele alınmalı.
- Review'da şu an metin görünürlüğü VISIBLE/DELETED; rapor tablosu, rapor endpoint'i, outbox, relay ve moderasyon endpoint'i bu çalışma kapsamında oluşturulmadı.

## 6. Eksik gereksinimler

- Kimliği doğrulanmış Review üreticisinden rapor alma ve kalıcı kabul/tekilleştirme sözleşmesi.
- Rapor ile vaka kimliklerinin eşlenmesi; aynı incelemeye ait birden fazla kişinin raporunun nasıl gruplanacağı.
- Vakanın durum otoritesi, atanmış moderatör/izin kapsamı, karar gerekçesi ve değiştirilemez audit.
- Review'a durum/karar aktarımı, Review uygulama onayı ve başarısız uygulamanın Admin durumunda doğru gösterilmesi.
- Silinmiş inceleme/hesap, eski sürüm ve tekrar/sıra bozuk teslim davranışları.

## 7. Önerilen sözleşme — kesinleşmedi

Alıcı sahibinin tercihine bağlı öneri: `POST /internal/events` ile `ContentReported` v1. Bu endpoint bugün Admin'de **yoktur**. Örnek zarf alanları: `eventId`, `eventType`, `eventVersion`, `occurredAt` UTC, `producer=review-service`, `aggregateId` (reportId önerisi), `payload`.

Önerilen payload: `reportId`, `targetType` (GAME_REVIEW adı yalnız öneri), `targetId` (reviewId), `gameId`, `targetAuthorId`, `reporterId`, `reason`, gerektiğinde sınırlı `detail`, `reportedAt`. Gerekçe kategorileri/metin limitleri ve hangi alanların gerçekten zorunlu olduğu karşılıklı karardır. Tam inceleme gövdesi, e-posta, token veya geniş kullanıcı profili taşınmamalı. Moderatörün içeriği görmesi gerekiyorsa mevcut olmayan bir Review yönetim API'si varmış gibi davranılmadan ayrı sözleşme tasarlanmalı.

Önerilen kalıcı ACK: 200 ve `eventId/reportId`, vaka kimliği ve duplicate bilgisi; yalnız inbox/vaka kaydı commit edildikten sonra. Asenkron 202 seçilecekse kalıcı kabul anlamı ve takip yöntemi kesinleştirilmeli. Geçersiz şema 400, kimlik yok/geçersiz 401, yetki/producer uyuşmazlığı 403, çelişkili aynı kimlik 409, kapasite 429/503, geçici hata 5xx önerilir.

Durum izleme için Admin→Review durum olayı (ör. `ReviewReportStatusChanged`, **öneri**) tercih edilebilir. Durum adları kesin değildir; OPEN/IN_REVIEW/RESOLVED/REJECTED gibi durumların anlamını Admin sahibi belirler. Review yerel teslim durumu ile Admin vaka durumunu karıştırmamalı.

Karar aktarımında örnek bilgiler: `actionId`, `caseId`, `reviewId`, eylem, gerekçe, güvenilir aktör referansı, karar zamanı/sırası, beklenen hedef sürümü. Gizle/kaldır/geri al eylemleri ve uygulama ACK/olayı (ör. `ReviewModerationApplied`) ayrı anlaşılmalı; bunlar bugün mevcut endpoint/olay değildir. Admin'de karar verildi olması Review'da uygulandı anlamına gelmez.

## 8. Kimlik doğrulama ve yetkilendirme

İki yönde de Auth imzalı, doğru hedef/kapsam/producer'a bağlı servis kimliği gerekir. `at+jwt`, başka servis token'ı ve salt iç ağ konumu yeterli değildir. Admin insan moderatörün güncel yetkisini doğrulamalı; Review yetkili Admin kaynağını, hedefi, actionId/sürümünü ve onaylı aktör/audit modelini ayrıca kontrol etmeli. D4 `account:erase` moderasyon yetkisi değildir. İç uçlar Gateway'den sunulmamalı.

## 9. Hata ve güvenilirlik davranışı

Sabit eventId/actionId ile at-least-once teslim ve idempotent alıcı; aynı ID'nin farklı payload'ı reddedilmeli. Tekrar teslim ikinci vaka/eylem/bildirim üretmemeli. Timeout sonucu belirsizse alıcı tekilliğine dayanarak sınırlı retry; 401/403/şema hatasında sıkı retry döngüsü olmamalı. Bounded timeout, backoff, maksimum deneme ve manuel iyileştirme süreci belirlenmeli.

Gelecekte üretici outbox'ı iş transaction'ıyla, alıcı inbox/vaka kaydı kendi transaction'ıyla atomik olmalı. Şu an Review'da outbox/relay geliştirilmesi onaylı değildir. Geçici kesintide raporu teslim edilmiş veya kararı uygulanmış gösterme kabul edilmez. Silinen içerik/hesap için retention, gizlilik ve eski olayın veri geri oluşturmasını engelleme kuralları belirlenmeli.

Kullanıcı kendi incelemesini raporlayamaz (Review backend politikası). Review'da aynı reporter/review tekilliği, Admin'de event/report/action tekilliği ayrı gereksinimlerdir; kötüye kullanım limitleri REV-13 ile uyumlu tasarlanmalı, bu belgeyle oran sınırı kodlanmaz.

## 10. Kabul kriterleri

- Gerçek Auth kimliğiyle Review raporu Admin'e kalıcı ve yalnız bir kez kabul edilir; tekrar/sıra bozukluğu sonuçları bozmaz.
- Başka producer, kullanıcı token'ı, yanlış audience/scope, yetkisiz insan ve başka hedefe taşınmış action reddedilir.
- Admin durum değişikliği Review'da doğru rapora yansır; eski durum/karar yenisini ezmez.
- Karar uygulanamadığında Admin uygulandı göstermez; yeniden denemede ikinci mutasyon/audit/bildirim oluşmaz.
- DB/outbox/inbox transaction hataları, timeout, kapasite, silinmiş içerik ve hesap testleri yapılır; başka servis DB'sine doğrudan erişilmez.

## 11. Bağımlılık ve etki

REV-07 ve REV-10 **ENTEGRASYON BEKLİYOR**. Yerel bir rapor kaydı veya taslak zarf bu kabul kriterlerinin yerine geçmez. REV-06 tamamlanmasını engellemez; REV-09 da bu çalışmada geliştirilmez.

## 12. Durum

**TALEP / ÖNERİ — Henüz uygulanmadı. SÖZLEŞME BEKLENİYOR.** Kullanıcı kararı olmadan Review'da rapor/outbox/relay/karar alıcısı yazılmayacak.

## 13. Karar bekleyen konular

Admin sorumlusu; olay/senkron protokol ve ACK; vaka/rapor ayrımı; hedef/gerekçe/durum sözlüğü; aktör doğrulama; sıra/sürüm modeli; içerik okuma izni; saklama/anonimleştirme; yeniden teslim ve uzlaştırma; moderasyon sonrası Notification üreticisi. Nihai tasarım hedef servis sahibiyle birlikte onaylanır.
