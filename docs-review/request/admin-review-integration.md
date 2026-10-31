# ADMIN–REVIEW entegrasyon talebi

## 1. Talep eden servis

`review-service` — Hamza. Bu belge bir ihtiyaç/sözleşme taslağıdır; hedef servisin mimarisini belirleyen emir değildir.

## 2. Hedef servis

`admin-service` — ortak servis; sorumlu ekip/kişi henüz atanmalı. Bu çalışmada Admin koduna müdahale edilmedi.

## 3. İlgili task

REV-07 ve REV-10. Kararın içeriğe uygulanması, görünürlük/puan etkisi gibi davranışlar ileride REV-15 kapsamıyla birlikte netleşir; bu belge Faz 4 kodlaması değildir. REV-13'ün raporlamaya bağlı kısmı (rapor kotası, mükerrer rapor/vaka engeli, güvenilir iletim) ve REV-15'in entegrasyona bağlı kısmı da bu belgede izlenir; ikisi de Review'da kodlanmadı, tamamlanmış sayılmaz.

## 4. Talebin amacı

Review'de gelecekte oluşturulacak uygunsuz inceleme raporlarının Admin'e güvenli teslimi, durumlarının Admin tarafından yönetimi ve yetkili kararların kaynağa güvenilir aktarımı. Review kendi inceleme/rapor verisinin, Admin moderasyon vakasının sahibi kalmalı.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Admin Java kaynakları](../../backend/services/admin-service/src/main/java/com/ltz/admin): yalnız `AdminServiceApplication` launcher var; rapor alma, vaka, karar ve geri bildirim controller/service'i bulunmuyor.
- [API kataloğunun ADMIN bölümü](../../docs/api.md): `_Kayıt yok._`
- [Mimari olay kataloğu](../../docs/architecture.md) `ContentReported` ve `ModerationActionTaken` akışlarını **ÖNERİ** olarak listeliyor. Community'nin önerilmiş `POST /internal/events` teslim modeli hazır Admin alıcısı kanıtı değildir.
- Auth yapılandırmasında Review→Admin ve Admin→Review çiftleri yok; [Auth talebi](auth-review-integration.md) ile birlikte ele alınmalı.
- Review'da şu an metin görünürlüğü VISIBLE/DELETED; rapor tablosu, rapor endpoint'i, outbox, relay ve moderasyon endpoint'i bu çalışma kapsamında oluşturulmadı.
- 31 Ekim 2026'da yeniden doğrulandı (değişiklik yok): Admin Service'te `AdminServiceApplication` dışında Java sınıfı yoktur; `docs/api.md` ADMIN bölümü hâlâ "Kayıt yok"; Auth'ta Review↔Admin çifti hâlâ tanımsızdır. Bu belgedeki hiçbir öneri uygulanmış değildir.

## 6. Eksik gereksinimler

- Kimliği doğrulanmış Review üreticisinden rapor alma ve kalıcı kabul/tekilleştirme sözleşmesi.
- Rapor ile vaka kimliklerinin eşlenmesi; aynı incelemeye ait birden fazla kişinin raporunun nasıl gruplanacağı.
- Vakanın durum otoritesi, atanmış moderatör/izin kapsamı, karar gerekçesi ve değiştirilemez audit.
- Review'a durum/karar aktarımı, Review uygulama onayı ve başarısız uygulamanın Admin durumunda doğru gösterilmesi.
- Silinmiş inceleme/hesap, eski sürüm ve tekrar/sıra bozuk teslim davranışları.

**REV-13 — raporlamaya bağlı gereksinimler (sorumlu servis belirtilerek; Review yerel işini Admin'e görev olarak atamaz):**

- **[Review]** Kendi incelemesini raporlama yasağı (403, kullanıcı onaylı). Yalnız görünür (`VISIBLE`) incelemeye yeni rapor; silinmiş/olmayan hedefte 404. Gizli/arşivli hedefe yeni rapor ve devam eden vakanın etkilenmesi: **KARAR BEKLİYOR**.
- **[Review]** Rapor tekilliği: aynı raportör–inceleme çifti için PostgreSQL benzersizliği; eşzamanlı ve tekrarlanan istekler tek kayıt üretir. Tekrar isteğin sonucu (aynı kaydı 200 ile dönme mi, 409 mu): **KARAR BEKLİYOR**.
- **[Review]** Rapor oluşturma oran sınırı: mevcut REV-13 kota mekanizması (hesap başına, PostgreSQL ortak sayaç, 429 + `Retry-After`) yeni bir eylem olarak yeniden kullanılır; **sayısal sınır ve tekrar/geçersiz isteklerin sayılıp sayılmayacağı KARAR BEKLİYOR** (değer uydurulmadı; mevcut 5/60 ve 60/60 değerleri raporlamaya uygulanmaz).
- **[Review]** Güvenilir iletim: rapor kaydı ve teslim niyeti aynı yerel transaction'da kalıcılaşmalı; olay kimliği rapor kimliğinden deterministik olmalı ki yeniden teslim aynı kimlikle gitsin. Admin kesintisi raporu kaybetmemeli ve "teslim edildi" göstermemeli.
- **[Admin]** Olay kabulü: aynı `eventId`/`reportId` tekrarı ikinci moderasyon vakası, vaka kaydı veya bildirim üretmez (kalıcı kabul + tekilleştirme); aynı ID'nin farklı içeriği 409. Aynı incelemeye gelen çok sayıda raporun tek vakada gruplanması ve öncelik/yoğunluk kararı Admin sahibine aittir (grup/öncelik modeli **KARAR BEKLİYOR**).
- **[Admin]** Kapasite: 429/503 ile geri basınç; Review'ın yeniden deneme bütçesiyle uyumlu.
- **[Review + Admin]** Timeout/yeniden deneme: belirsiz sonuçta aynı `eventId` ile sınırlı retry; 4xx (şema/kimlik/yetki) kalıcı hata olarak görünür operasyonel duruma düşer, sıkı döngü olmaz. Azami deneme, backoff ve manuel iyileştirme değerleri **KARAR BEKLİYOR**.
- **[Auth]** Bu gereksinimler için yeni bir Auth çifti gerekmez; mevcut Review→Admin önerisi [Auth talebindedir](auth-review-integration.md).

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

- **REV-13 raporlama spam kabulü:** kendi incelemesini raporlama reddedilir; aynı kullanıcı aynı incelemeyi eşzamanlı ve ardışık birden çok kez raporladığında tek rapor kalır ve Admin'de tek vaka oluşur; silinmiş/görünmez hedef raporlanamaz; oran sınırı aşımı 429 + `Retry-After` verir ve sınırın altındaki normal kullanım engellenmez; Admin kesintisinde rapor kaybolmaz ve aynı kimlikle yeniden teslim ikinci vaka üretmez. Test sonuçları gerçek Auth kimliğiyle ve PostgreSQL'de doğrulanmalıdır.
- **REV-15 kabulü:** aşağıdaki sorumluluk matrisindeki her satır için olumlu ve olumsuz test (yetkisiz insan, yanlış producer, eski/sıra bozuk karar, tekrar karar, silinmiş inceleme/hesap).

## 11. Bağımlılık ve etki

REV-07 ve REV-10 **ENTEGRASYON BEKLİYOR**. Yerel bir rapor kaydı veya taslak zarf bu kabul kriterlerinin yerine geçmez. REV-06 tamamlanmasını engellemez; REV-09 da sonradan tamamlandı ve bu talepten etkilenmez.

## 12. Durum

**TALEP / ÖNERİ — Henüz uygulanmadı. SÖZLEŞME BEKLENİYOR.** Kullanıcı kararı olmadan Review'da rapor/outbox/relay/karar alıcısı yazılmayacak.

## 13. Karar bekleyen konular

Admin sorumlusu; olay/senkron protokol ve ACK; vaka/rapor ayrımı; hedef/gerekçe/durum sözlüğü; aktör doğrulama; sıra/sürüm modeli; içerik okuma izni; saklama/anonimleştirme; yeniden teslim ve uzlaştırma; moderasyon sonrası Notification üreticisi. Nihai tasarım hedef servis sahibiyle birlikte onaylanır.

**REV-15 için eklenen karar maddeleri (Faz 4 değerlendirmesi, 31 Ekim 2026):** Aşağıdakiler hedef sözleşme önerisi değil, Admin ile Review'ın birlikte kararlaştırması gereken sorulardır; Review'da `HIDDEN/ARCHIVED` durumu, moderasyon alanı veya karar alıcısı **yoktur** ve kodlanmadı. Review tarafı ürün politikaları (puana etki vb.) [kararlar belgesinde](../REVIEW_SERVICE_DECISIONS.md) ayrıdır ve Admin'e bırakılmamıştır.

- Eylem sözlüğü: gizle / arşivle / geri al / kaldır ayrımı; hangileri geri alınabilir ve geri alma hangi önceki durumu döndürür.
- Karar zarfı: `actionId` (tekil), hedef `reviewId`, beklenen hedef sürümü, monoton karar sırası veya `decidedAt`; eski kararın yeni durumu ezmemesi ve aynı `actionId` tekrarının idempotent olması alıcı kuralı olarak kabul edilmeli.
- Uygulama ACK'i: Review'ın kararı uyguladığını (ve hangi sürümde) Admin'e nasıl bildirdiği; başarısız/uygulanamayan karar (silinmiş inceleme, silinmiş hesap, eski sürüm) için Admin durumu.
- Rapor kapanışı: kapanışın otoritesi Admin vakasıdır; Review kapanışı yalnız uygulama sonucunu bilir. Kapanan raporun Review'da ne saklayacağı (yalnız sonuç mu, rapor referansı mı).
- Hesap silme: silinen yazar/raportörün karar ve rapor izleri nasıl temizlenir; gecikmiş olay silinen veriyi yeniden oluşturmamalı.
- Yazar yetkileri: gizli/arşivli incelemeyi yazarın görüp görmeyeceği, düzenleyip silip silemeyeceği (bugünkü `delete` VISIBLE olmayan kayıtta da çalışır).
- Etki alanı: gizlenen incelemenin beğenileri, öne çıkan listesi (zaten yalnız VISIBLE) ve istatistik `activeCount` etkisi Review tarafında; sayısal puan/tavsiye etkisi Review ürün kararıdır.

**Kullanıcı kararı (31 Ekim 2026) — Admin sözleşmesini sınırlar:** Gizlenen/arşivlenen incelemeler herkese açık listelerde ve öne çıkanlarda gösterilmez; bunların bağımsız sayısal puanı, ayrıca geçersiz kılınmadığı sürece LTZ ortalamasına dahil kalır ve metin durumu puanı otomatik silmez. Dolayısıyla Admin karar zarfı, metin görünürlüğü eylemlerinin puanı etkilemediğini varsaymalıdır; puanı geçersiz kılma ayrı bir eylem olarak Admin ile ayrıca kararlaştırılırsa tanımlanır. Beğenilerin saklanması, yazarın müdahalesi ve yukarıdaki diğer maddeler hâlâ açıktır. Bu belge kesin sözleşme değil, öneri/talep olarak kalır.

### REV-15 entegrasyon sorumluluk matrisi

Onaylanmış politikalar (kullanıcı kararı, Review ürün sahibi): gizlenen/arşivlenen incelemeler herkese açık listelerde ve öne çıkanlarda gösterilmez; metinsel inceleme ile sayısal puan bağımsızdır; gizlenen/arşivlenen incelemenin puanı, ayrıca geçersiz kılınmadıkça ortalamaya, dağılıma ve puan veren kullanıcı sayısına dahil kalır; **puanın geçersiz kılınması metin görünürlüğünden ayrı bir moderasyon kararı olmalıdır** (bu eylem henüz tanımlı değildir, **KARAR BEKLİYOR**).

| Gereksinim (REV-15) | Sorumlu | Talepte yeri | Durum |
| --- | --- | --- | --- |
| Admin kararının Review'a güvenli iletimi | Admin (gönderen), Auth (servis kimliği), Review (alıcı doğrulama) | §7–§8 | Sözleşme bekliyor |
| Kararı verenin yetkili insan moderatör olduğunun doğrulanması | Admin (insan yetkisi ve audit aktörü), Auth (servis kimliği ile insan rolünün ayrımı), Review (producer ve aktör referansını doğrular; body'deki `actorId`'ye güvenmez) | §8, [Auth talebi](auth-review-integration.md) §6 ve §8 | Güven modeli **KARAR BEKLİYOR** |
| Gizle / arşivle / yeniden görünür yap | Admin (karar), Review (uygulama) | §7 ve REV-15 maddeleri (§13) | Eylem adları ve geçiş kuralları **KARAR BEKLİYOR** |
| Kararın doğru incelemeye uygulanması | Review (`reviewId`, hedef sürüm eşleşmesi) | §7, §10 | Sözleşme bekliyor |
| Aynı kararın tekrarında ikinci işlem olmaması | Admin (sabit `actionId`), Review (idempotent alıcı) | §9 | Sözleşme bekliyor |
| Eski/sırası bozulmuş kararın güncel durumu ezmemesi | Admin (monoton sıra/sürüm üretir), Review (kontrol eder) | §9–§10 | Sıra/sürüm modeli **KARAR BEKLİYOR** |
| Uygulama sonucunun Admin'e bildirilmesi | Review (ACK/olay üretir), Admin (kabul eder, vaka durumunu günceller) | §7 | Sözleşme bekliyor |
| Raporların doğru vakayla ilişkilendirilip kapatılması | Admin (vaka/rapor kapanışı otoritesi); Review yalnız uygulama sonucunu bilir | §6, §7 | Kapanış durum adları ve geçişleri **KARAR BEKLİYOR** |
| Başarılı uygulamadan sonra inceleme sahibine bildirim | Üretici **KARAR BEKLİYOR** (Admin ACK sonrası mı, Review uygulaması sonrası mı); Notification (alıcı) | [Notification talebi](notification-review-integration.md) §6 | Tek üretici seçilmeli |
| Silinmiş inceleme/hesabın eski olayla yeniden oluşmaması | Review (silinmişe uygulamayı reddeder, veri geri oluşturmaz), Admin (saklama/anonimleştirme), Notification (silinmiş hesap) | §9 | Sözleşme bekliyor |
| Denetlenebilirlik ve güvenlik | Admin (değiştirilemez audit: aktör, gerekçe, zaman), Review (uygulanan `actionId`/sürüm kaydı), Auth (kimlik) | §8–§9 | Sözleşme bekliyor |

**Henüz kesinleşmemiş (KARAR BEKLİYOR):** gizlenen incelemenin mevcut beğenilerinin korunması veya kaldırılması; yazarın gizli/arşivli içeriği düzenleyebilmesi veya silebilmesi; gizleme–arşivleme–yeniden görünür geçiş kuralları; moderasyon bildirimini Admin'in mi Review'ın mı üreteceği; rapor kapanış durumlarının isimleri ve geçişleri; puan geçersiz kılma eyleminin varlığı ve kapsamı. Bunlar bu belgede kesinleşmiş gibi yazılmamıştır; Review'da bu davranışlar kodlanmadı.
