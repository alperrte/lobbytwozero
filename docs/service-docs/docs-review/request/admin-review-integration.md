# ADMIN–REVIEW entegrasyon talebi

> **Güncelleme — 12 Kasım 2026:** Yeniden doğrulandı — Admin Service'te hâlâ yalnız `AdminServiceApplication` vardır, Auth'ta Review↔Admin çifti **tanımsızdır**; bu belgedeki hiçbir öneri uygulanmış değildir. Yeni: Review frontend'i tamamlandı ve raporlama/moderasyon için **hazır tutulan arayüz sınırları ile bu servisten beklenenler §14'te** toplandı. Review'ın yeni okuma uçları (`reviews/mine`, `likedByActor`) bu talebi değiştirmez.

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
- Review'da rapor **yerel olarak kaydedilir** (`review_reports`, `delivered_at NULL` = Admin'e iletilmedi) ve kullanıcı API'si vardır ([docs/api.md → REVIEW](../../docs/api.md)); Admin'e teslim işçisi/outbox, moderasyon durumları (`HIDDEN/ARCHIVED`) ve moderasyon karar alıcısı **yoktur**.

## 6. Eksik gereksinimler

- Kimliği doğrulanmış Review üreticisinden rapor alma ve kalıcı kabul/tekilleştirme sözleşmesi.
- Rapor ile vaka kimliklerinin eşlenmesi; aynı incelemeye ait birden fazla kişinin raporunun nasıl gruplanacağı.
- Vakanın durum otoritesi, atanmış moderatör/izin kapsamı, karar gerekçesi ve değiştirilemez audit.
- Review'a durum/karar aktarımı, Review uygulama onayı ve başarısız uygulamanın Admin durumunda doğru gösterilmesi.
- Silinmiş inceleme/hesap, eski sürüm ve tekrar/sıra bozuk teslim davranışları.

**REV-13 — raporlamaya bağlı gereksinimler (sorumlu servis belirtilerek; Review yerel işini Admin'e görev olarak atamaz):**

- **[Review]** Güvenilir iletim (KALAN): rapor kaydı Review'da kalıcıdır ve bekleyen raporlar `delivered_at IS NULL` ile bulunur; **teslim işçisi yoktur.** Admin alıcısı belli olunca olay kimliği rapor kimliğinden deterministik üretilmeli ve aynı kimlikle yeniden teslim edilmelidir. Admin kesintisi raporu kaybetmez; "Admin'e iletildi" iddiası teslim ACK'inden önce kurulmaz.
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

REV-07 ve REV-10 **ENTEGRASYON BEKLİYOR**. Yerel bir rapor kaydı veya taslak zarf bu kabul kriterlerinin yerine geçmez.

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


## 14. Review'ın bugünkü durumu ve Admin'den/ortak karardan beklenenler (12 Kasım 2026)

### 14.1 Review'da bugün olan ve olmayanlar (yalnız Admin'i ilgilendiren)

- **Rapor kaydı (Review yerelinde):** Kullanıcı API'si [docs/api.md → REVIEW](../../docs/api.md)'dedir. Politikalar: mükerrer rapor 409 (`REPORT_ALREADY_EXISTS`), kendi incelemesi 403 (`REPORT_OWN_REVIEW`), yalnız görünür incelemeye rapor, raportör kimliği hiçbir yanıtta dönmez, hesap silme raporları temizler. Rapor durumu yalnız `RECEIVED`'dir (Review'ın yerel kaydı); Admin vaka durumu **yoktur**.
- **Teslim yok:** Raporlar `delivered_at NULL` ile bekler; Admin'e iletim işçisi, olay zarfı üretimi ve Admin'den durum/karar alma uygulanmadı. Frontend, kullanıcıya "raporun kaydedildi; moderasyon ekibine iletim henüz etkin değil" der; "iletildi/karara bağlandı" iddiası kurmaz.
- **Moderasyon yok:** `HIDDEN/ARCHIVED` durumları, karar alıcı ve uygulama ACK'i uygulanmadı. Görünür olmayan her inceleme tek okuma, güncelleme, beğeni, öne çıkanlar, `reviews/mine` ve istatistik `activeCount` yollarında fail-closed dışlanır (şemada gizli durum olmadığından davranış testi yoktur).

### 14.2 Admin sözlüğüyle uyumlanacak geçici değerler (Review'da uygulandı; Admin onayıyla güncellenir)

- **Gerekçe listesi (kapalı):** `SPAM, HARASSMENT, HATE_SPEECH, PERSONAL_INFO, SPOILER, OFF_TOPIC, OTHER`. Admin'in nihai sözlüğü farklıysa Review'daki enum, `review_reports.reason` CHECK kısıtı ve frontend çevirileri birlikte güncellenir (yeni migration gerekir).
- **Açıklama sınırı:** isteğe bağlı `detail`, en çok 500 karakter düz metin.
- **Rapor kotası:** hesap başına saatte 10 deneme (mükerrer/geçersiz denemeler dahil); `ltz.review.abuse.report-create.*` ile yapılandırılır. Nihai sayı Admin ile birlikte onaylanmalıdır.

### 14.3 Moderasyon sonucunun kullanıcıya yansıması (karar bekleyen noktalar)

- **Gizlenen/arşivlenen inceleme:** Bugün herkese 404. Yazarın kendi gizli incelemesini **görmesi/düzenlemesi/silmesi** kararı (REV-15 §13) verilirse Review'ın yazara özel bir durum alanı (durum + gerekçe kodu) döndürmesi gerekir; mevcut `ReviewResponse`'ta yoktur. Karar çıkana kadar yazar da 404 görür ve "İncelemelerim" (yalnız `VISIBLE`) gizlenen incelemeyi sessizce düşürür — yazar için kafa karıştırıcıdır; Admin/Notification bildirimiyle ([Notification talebi](notification-review-integration.md)) birlikte ele alınmalıdır.
- **Puan:** Karar gereği gizli incelemenin puanı ortalamada kalır; puanı geçersiz kılma ayrı bir Admin eylemi olarak tanımlanırsa Review'ın uygulayacağı uç ve REV-14 yeniden hesabı ayrıca kararlaştırılmalıdır.
- **Beğeniler:** Gizlenen incelemenin beğenilerinin korunması/silinmesi kararı açık.
- **Raporlanmış inceleme silinirse/gizlenirse:** Review bugün raporu saklar (silinmiş inceleme raporlarını da); saklama süresi ve Admin vakasına etkisi **KARAR BEKLİYOR**.

### 14.4 Admin'den somut beklenenler (özet)

1. Sorumlunun atanması; olay/senkron protokol, ACK ve vaka/rapor ayrımı kararı (§13).
2. Rapor alıcısı (`ContentReported` benzeri) ve Auth çifti (Review→Admin); alıcı hazır olunca Review'da **teslim işçisi** eklenir (bekleyen raporlar hazırdır).
3. Gerekçe sözlüğü ve alan sınırlarının onayı (§14.2).
4. Kullanıcıya gösterilebilir **sınırlı rapor durumu** kümesi ("alındı/inceleniyor/sonuçlandı"); Admin vaka sonucunu (karar, gerekçe, moderatör) kullanıcıya açmaz.
5. Moderasyon eylemleri ve yazara görünürlük politikası (§14.3) — Review'ın yanıt şemasını etkilediği için önce kararlaştırılmalı.
6. Karar aktarımında Review'ın uygulama ACK'i ve `actionId`/sürüm kuralları (§7–§10).

## 15. Model değişikliği önerisi (Auth cevabı, 12 Kasım 2026) — KABUL EDİLDİ (12 Kasım 2026, Hamza)

Auth'un bildirdiği Admin modeli: Admin paneli (login + dashboard) hazırdır; **her servis kendi admin uçlarını kendisi açar, panel çağırır.** Auth önerisi ve sonuçları ([Auth belgesi](auth-review-integration.md) §2):

- Review, rapor listeleme/okuma ve moderasyon (gizle/arşivle/yeniden görünür yap) uçlarını **kendisi** sunar; uçlar kullanıcı `at+jwt`'sini alır ve `MODERATOR`/`ADMIN` rolünü Review'da doğrular.
- **Düşenler:** `admin-service → review-service` ve `review-service → admin-service` servis çiftleri, moderasyon komut kapsamı, insan aktör taşıması, Admin'e rapor push'u (§6–§9'daki olay zarfı, ACK, inbox/outbox gereksinimleri). Bu belgenin §7–§10'daki "Admin alıcısı" tasarımı bu modelde **geçersiz** olur.
- **Kalanlar:** Admin servisi rapor **push** ile almak isterse en dar çift (`events:ingest`) ve §6–§10 tasarımı yeniden devreye girer. Admin panelinin Review uçlarını çağırması panel/Gateway tarafıdır (Admin sahibi).

**Review'a etkisi (UYGULANDI, 12 Kasım 2026 — ayrıntı [docs/api.md → REV-15 Moderatör uçları](../../docs/api.md)); aşağıdaki liste tarihsel plandır:**
1. Rol tabanlı yetki: `/api/v1/review/admin/**` (yol önerisi) yalnız `MODERATOR`/`ADMIN`; mevcut "bu rollere ek yetki yok" kararı bu uçlar için değişir, kullanıcı uçları aynı kalır. Silinmiş hesap 403 kuralı korunur.
2. Rapor yönetimi: mevcut `review_reports` (V58) üzerine rapor listesi (inceleme bazında gruplama, sayfalı) ve rapor kapatma/durum (`RECEIVED` → `REVIEWED/DISMISSED` benzeri; adlar **KARAR BEKLİYOR**) — `delivered_at` artık gerekmez/yeniden tanımlanır.
3. Moderasyon: `HIDDEN/ARCHIVED` durumları (yeni migration; `ck_reviews_status`/`ck_reviews_content` güncellemesi), karar kaydı (aktör referansı = token `sub`, gerekçe, zaman — Review'da audit), geri alma; `VISIBLE` beyaz liste zaten gizleneni listelerden dışlar. Kullanıcı kararı (31 Ekim): gizli incelemenin puanı ortalamada kalır.
4. Frontend: Review sayfalarına bağlı değil; panel kendi ekranlarını Admin sahibi yapar. Yazara kendi gizli incelemesinin durumu gösterilecekse `ReviewResponse` genişler (§14.3 kararı).
5. Bildirim: moderasyonu Review uyguladığı için doğal üretici Review olur ([Notification belgesi](notification-review-integration.md)).

**Açık kararlar:** (1) modelin kabulü; (2) `MODERATOR` 2FA zorunluluğu (Auth + ürün); (3) rapor durumu adları ve kapatma kuralları; (4) yazarın gizli inceleme görünürlüğü ve itiraz; (5) moderasyon audit'inin saklama süresi; (6) panelin Review uçlarını hangi yolla çağıracağı (Gateway `/api/v1/review/admin/**` yeterli mi).

### 15.1 Uygulama durumu ve Admin paneli sahibinden beklenenler

**Review tarafı uygulandı** (V60, `/api/v1/review/admin/**`, 229 test PASS; canlı doğrulama yok). Rapor durumları `RECEIVED/REVIEWED/DISMISSED`, eylemler `HIDE/ARCHIVE/RESTORE` olarak kesinleşti ([kararlar](../REVIEW_SERVICE_DECISIONS.md)). Bu belgenin §6–§10, §13 ve REV-15 sorumluluk matrisindeki Admin push/alıcı/ACK/aktör taşıma maddeleri **geçersizdir**.

**Admin paneli sahibinden (açık):**
1. Panelin Review uçlarını Gateway `/api/v1/review/admin/**` yoluyla ve kullanıcı oturumuyla (çerez→Bearer, CSRF) çağırması; rapor listesi, inceleme ayrıntısı, gizle/arşivle/geri al ve rapor kapatma ekranları (gerekçe alanı ve onay zorunlu, sürüm çatışmasında "güncel hali yükle").
2. Moderatör görünümünde yazar ve raportör kimliği **yoktur** (bilinçli). Yazara işlem (askıya alma vb.) gerekiyorsa User/Admin tarafında ayrı bir sözleşme gerekir.
3. `MODERATOR` hesaplar için 2FA kararı (Auth) — uçlar canlıya açılmadan önce.
4. Yazara bildirim gerekiyorsa Notification alıcısı ([Notification belgesi](notification-review-integration.md) §15).
