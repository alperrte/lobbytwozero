# HW-04 yorum ve moderasyon sözleşmesi — APPROVED

Tarih 2026-10-21; sorumlu Alper. Master §0.4/§3.7 ve SECURITY.md §5/§12/§19.
Kullanıcı 2026-10-21 tarihinde modeli ve güvenli dış sözleşme ertelemesini ayrı ayrı
onayladı. Bu karar gerçek dış entegrasyonun uygulandığı anlamına gelmez.

## Mevcut durum ve kapsam

Kullanıcı HW-03 PDA kapanışını ve HW-04 başlangıcını bildirdi; HEAD 104524a,
çalışma ağacı başlangıçta temiz. HW-12 ardından HW-13 uygulanacak.
Hardware V1–V7 mevcut; sonraki boş sürüm V8, aralık V1–V10 korunacak.
Admin/Notification API kayıtları yok; admin-service yalnız application iskeleti.
Başka servisin kodu/DB'si, gateway, ENV, Compose veya bağımlılık değişmeyecek.

## 1. HW-12 yorum iş kuralı ve erişim önerisi

- Kimlik yalnız mevcut typed actor; anonim yorum/okuma yok. Gerçek Auth ertelenmiş
  ve production fail-closed kalır; CSRF/no-store/16 KiB korunur.
- Yorum aktif genel katalog ürününe yazılır. Body zorunlu 1..5000 karakter düz
  metin; boş, HTML açı ayraçları, zararlı kontrol karakterleri ve normalizasyonla
  bunlara dönüşen metin reddedilir. Satır sonu/tab desteklenir; URL fetch/HTML yoktur.
- Rating opsiyonel tam sayı 1..5. Her kullanıcı/ürün için tek silinmemiş yorum;
  HIDDEN da tekilliğe dahildir. Aynı yazarın farklı ürünlere aynı normalize body'yi
  kopyalaması DB hash tekilliğiyle engellenir. DELETED kayıt yeni yorumu engellemez.
- VISIBLE/HIDDEN/DELETED; owner yalnız kendi VISIBLE yorumunu günceller.
  HIDDEN yorum owner güncellemesiyle yeniden yayımlanamaz. Owner kendi yorumunu
  soft-delete eder; deleted_at tutulur, body/rating/hash temizlenir. DELETED geri açılmaz.
- Arşivli ürüne yeni yorum/güncelleme yok; genel okumalar ürünü ve yorumları gizler.
  Owner geçmiş yorumunu okuyabilir ve silebilir.
- Owner mine/detail kendi HIDDEN yorumunu görür. Başkasına yalnız VISIBLE ve aktif
  ürüne ait yorum sunulur. Genel DTO author UUID/kullanıcı adı/e-posta/IP veya rapor
  sayısını içermez; yalnız own flag ile aktöre ait olup olmadığı belirtilir.
- Başkalarının UGC'si için ayrı, güvenilir ReviewAudiencePolicy karar portu:
  global okuma izni + iki yönlü engel seti. Set en fazla 1000 UUID; geçersiz/null/hata
  veya bilinmeyen karar başkalarını deny eder. Üretim adapter'ı default deny;
  owner kendi verisini görmeye devam eder. Gerçek User/Social URL/JSON uydurulmaz.
  İzin/engel filtresi DB sayfalama, toplam ve rating özetinden önce uygulanır.
- Sayfa 0..10000, size 1..100, createdAt/id sabit sıralı. Yalnız görünür yorumlar
  sayılır; yalnız bilinen rating'ler ortalamaya girer, hiç rating yoksa average null.
- Normal yazmalar ve raporlar aktör başına 20/dakika, doğrudan socket peer başına
  120/dakika sınırlı; DB fixed-minute bucket/transaction ile çok instance'ta korunur.
  X-Forwarded-For'a güvenilmez. Ham IP saklanmaz/loglanmaz; iki dakikadan eski hash
  bucket'ları dakikalık job ile temizlenir (yaklaşık 2–3 dakika). Proxy arkasındaki gerçek istemci IP zinciri ayrıca NOT_VERIFIED;
  socket peer limiti gerçek istemci-IP doğrulaması olarak sunulmaz.

Önerilen uçlar (tam prefix /api/v1/hardware):
GET/POST /catalog/products/{productId}/reviews;
GET /reviews/mine; GET/PUT/DELETE /reviews/{reviewId}.
Mutasyonlar reviewVersion ister (create hariç); DB unique + parent/resource kilidi
ve @Version stale/concurrency durumlarını korur.

## 2. HW-13 raporlama ve kaynak servis moderasyonu

- POST /reviews/{reviewId}/reports: kullanıcı okuyabildiği, kendisine ait olmayan
  VISIBLE yorumu raporlar. Reason SPAM/HARASSMENT/HATE_SPEECH/SEXUAL_CONTENT/
  PERSONAL_DATA/OTHER, opsiyonel 1..500 karakter düz metin açıklama.
- Aynı reporter/review tekilliği DB'dedir; tekrar 409, ikinci event oluşmaz.
  GET /review-reports/{reportId} yalnız reporter'a; başka rapor kimliği güvenli 404.
  DTO teslim edilmemiş olayı açıkça NOT_DELIVERED/PENDING olarak gösterir.
- Kaynak servis hedef detayı ve HIDE/RESTORE komutları yalnız typed actor + yeni
  hardware:reviews:moderate uygulama iznine açık. Bu bir gerçek Auth rolü/claim'i
  değildir; JWT/rol eşlemesi DEFERRED_AUTH. Katalog yazma izni moderasyon sağlamaz.
- GET /moderation/reviews/{reviewId}: hedef ve sınırlı/sayfalı rapor ayrıntısı.
  POST /moderation/reviews/{reviewId}/actions: HIDE veya RESTORE + reviewVersion +
  gerekçe (1..500). DELETED geri getirilemez. Aktör başına 60/dakika, socket peer
  120/dakika moderasyon limiti. Normal kullanıcı owner olsa bile bu uçlara giremez.
- Merkezi admin case/kuyruk/rol/hesap yönetimi burada kurulmaz. Kaynak HIDE/RESTORE
  işlemi gerekçeli append-only audit ve aynı transaction'da sahibine yönelik
  moderasyon notice event'i üretir. UGC metni veya ham IP audit/log/event'e kopyalanmaz.
- ContentReported v1 için yerel outbox zarfı: eventId/type/version/occurredAt/
  producer/aggregateId; payload report/review/product kimlikleri ve reason code.
  Moderasyon notice payload'ı owner/review kimliği ve yeni status ile sınırlıdır.
  Yerel şema önerisi gerçek consumer sözleşmesi olarak gösterilmez.
- Bildirim/rapor olayı iş mutasyonuyla atomiktir. Relay/REST URL/broker ve servis
  token modeli uydurulmaz. Consumer hazır olmadığı için kayıtlar PENDING kalır;
  SENT/teslim alındı/bildirim gönderildi denmez. Yinelenen rapor outbox üretmez.
- Yerel outbox yükü olay başına 4 KiB ve PENDING/FAILED toplamı 10.000 kayıtla
  sınırlıdır. Global transaction advisory lock kapasite yarışını korur; doluysa
  503 MODERATION_DELIVERY_UNAVAILABLE ile iş değişikliği/audit/quota geri alınır.

## 3. Gerekli güvenli ertelemeler

Yeni Auth dışı erteleme için master §3.7 açık onay ister. Öneri:
gerçek Admin kabul/moderasyon case E2E, Notification teslim E2E ve User/Social UGC
izni/engel sözleşmesi DEFERRED_CONTRACT / NOT_VERIFIED. Yerel iş kuralları, kaynak
komutları, audit/outbox ve test-only policy ile tamamlanır; production non-owner
UGC okuması default deny kalır. Bu bağlantılar sonraki açık talimatla yapılır.
Yerel kriterler ve iki kalite kapısı geçtiğinde bu açık erteleme ile READY olabilir.

Alternatif: Dış API/güven sözleşmeleri gelene kadar ilgili alt görevleri bloke tutmak.
Test mock'unu gerçek E2E saymak veya korumaları açmak seçenek değildir.

## 4. Teslim kapıları

Önce HW-12, test/API/checkpoint; sonra HW-13, test/API/checkpoint.
Final .\pre-push\pre-push.cmd full dev ve .\sonarqube\sonar.cmd scan dev
tüm backend için yeniden çalıştırılır; önceki ana task sonuçları devralınmaz.
İki kapı PASS ve Sonar A/A/A olmadan READY/PDA/feature mesajı verilmez.
HW-05 başlamaz; Git staging/commit/push/PDA değişikliği yapılmaz.
