# GAME-11 — Community Service Entegrasyon Yanıtı

Tarih: 7 Kasım 2026  
Sorumlu: Nisa (Community Service)  
Durum: **Community sağlayıcı uygulaması yerel testlerde hazır; GAME-11 ortak entegrasyonu bekliyor.** Bu belge Hamza ile alınmış ortak karar veya canlı ortam kabulü değildir.

## Sahiplik, kapsam ve öncelik

Nisa tarafının önerisi: `GUIDES`, `EASTER_EGGS` ve `EXPERIENCES` oyuna bağlı kullanıcı içeriği olarak **Community Service**'te tutulur. Oyun kimliği ve başarım tanımları Game Service'te, haber ve duyurular Content Service'te kalır. Önerilen okuma yolu **frontend → API Gateway → Community Service**; çağıranın varsa kendi oturumu aktarılır. Game'in bu içerikleri servis token'ıyla toplayıp public cache'e koyması önerilmez. Mevcut Game modül yanıtındaki `INTEGRATION_PENDING` davranışı Hamza'nın kodunda devam eder.

Bu seçim [Nisa görevlerindeki COM-14/COM-21 ve Content kapsamına](../../servis-gereksinimleri/LTZ_Servis_Icerikleri_Nisa.md), [Hamza'nın GAME-11 tanımına](../../servis-gereksinimleri/LTZ_Servis_Icerikleri_Hamza.md) ve masaüstündeki `GAME-11-Nisa-Karar-ve-Entegrasyon.md` karar önerisine dayanır. Eski genel gereksinim belgesinin rehberi Content altında anmasıyla güncel görev dosyası arasındaki ayrım Nisa ve Hamza'nın ortak onayında açıkça kapatılmalıdır. Yeni servis veya Game tarafında UGC tablosu açılmadı.

GAME-11, Hamza'nın görev listesinde **MVP sonrası** yer alır. Bu işin kapsamı Community sağlayıcı API'sidir; ürün önceliği ve hedef tarih: **Bekliyor**. Nisa'nın içerik sahipliği için nihai onayı: **Bekliyor**. Hamza'nın okuma yolu ve Game DTO kararı için onayı: **Bekliyor**. V181–V190 aralığının Community'ye tahsisi ve türlü içerik için kullanıcı başına saatte 10 oluşturma sınırı kullanıcı tarafından 7 Kasım 2026'da bu çalışma sırasında açıkça onaylandı. Tahsis [merkezî migration planına](../../decisions/migrations.md) kaydedildi. V181 dosyası mevcut/uygulanmış migration'ları değiştirmez; canlı/dev DB'ye uygulanmadı.

## Gerçek API sözleşmesi

Kod: [GameContentController](../../../backend/services/community-service/src/main/java/com/ltz/community/api/controller/GameContentController.java), [GameContentService](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/GameContentService.java). Katalog: [docs/api.md — COMMUNITY, Oyuna Bağlı İçerik Listesi/Detayı](../../api.md). Gateway'de `/api/v1/community/**` rotası [tanımlı](../../../backend/services/api-gateway/src/main/resources/application.properties); çalışan Gateway üzerinden uçtan uca erişim **doğrulanmadı**.

| İşlem | Yol | Kimlik |
| --- | --- | --- |
| Liste | `GET /api/v1/community/games/{gameId}/content?type=GUIDES&page=0&size=20` | Misafir veya doğrulanmış kullanıcı access token'ı |
| Detay | `GET /api/v1/community/games/{gameId}/content/{contentId}` | Misafir veya doğrulanmış kullanıcı access token'ı |
| Oluşturma | `POST /api/v1/community/posts` | Doğrulanmış kullanıcı; yazar JWT `sub` değeridir |
| Düzenleme/silme | `PATCH /api/v1/community/posts/{postId}`, `DELETE /api/v1/community/posts/{postId}` | Yalnız sahibi; mevcut moderasyon kuralları geçerli |

Liste `gameId` UUID'siyle filtrelenir. `type` isteğe bağlıdır; yalnız `GUIDES`, `EASTER_EGGS`, `EXPERIENCES` kabul edilir. Verilmezse üç türün tamamı listelenir; `category=GUIDE` olan eski gönderi otomatik `GUIDES` sayılmaz. `page` sıfır tabanlı, varsayılan 0 ve en çok 10000; `size` varsayılan 20, 1–50. Sıra `publishedAt` (`createdAt`) azalan, eşit zamanda içerik UUID'si azalandır. `totalElements`, **aynı oyun, tür ve çağırana göre görünürlük filtresi** uygulandıktan sonraki, sayfadan bağımsız sayıdır. Bilinmeyen `gameId` ya da sonuçsuz sayfa `200` ve boş `items` verir; okuma sırasında Game Service'e çağrı yapılmaz. `gameId` oluşturma sırasında mevcut [COM-14 katalog doğrulaması](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/PostService.java) ile kontrol edilir.

Örnek oluşturma gövdesi (anonim örnek UUID, gerçek kişisel veri içermez):

```json
{"body":"Başlangıç için ilk ipucu.","gameId":"0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66","contentType":"GUIDES","title":"Başlangıç rehberi"}
```

Örnek liste yanıtı:

```json
{
  "gameId": "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66",
  "type": "GUIDES",
  "items": [{
    "id": "0198a3c2-9999-7d44-9a55-3f1c2b4d5e66",
    "gameId": "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66",
    "type": "GUIDES",
    "title": "Başlangıç rehberi",
    "excerpt": "Başlangıç için ilk ipucu.",
    "authorId": "0198a3c2-7b1e-7d44-9a55-3f1c2b4d5e66",
    "publishedAt": "2026-11-05T10:00:00Z",
    "updatedAt": "2026-11-05T10:00:00Z",
    "detailPath": "/api/v1/community/games/0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66/content/0198a3c2-9999-7d44-9a55-3f1c2b4d5e66"
  }],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

Detay `id`, `gameId`, `type`, `title`, `body`, `authorId`, `publishedAt`, `updatedAt`, `likeCount`, `commentCount`, `likedByMe` ve `images[]` döner. İçerik kimliği aynı zamanda gönderi kimliğidir; beğeni, yorum, rapor ve görsel işlemleri mevcut gönderi uçlarından yürür. Tam örnek ve hata tablosu [API kataloğunda](../../api.md) bulunur.

## Görünürlük, güvenlik ve hatalar

Liste, detay ve `totalElements` yalnız `VISIBLE` türlü içeriği kapsar. `HIDDEN`, `DELETED`, gizli forum konusu ve çağıranla yazar arasında iki yönlü engelleme bulunan kayıtlar dışarıda kalır; detayda bunların tümü `404` olur. Sahip kendi gizli gönderisini bu yeni detay ucundan göremez; mevcut kullanıcı gönderi geçmişi ayrı davranır. Community'de taslak durumu yoktur, oluşturma doğrudan yayımlar. Engelleme Community'nin yerel `user_blocks` yansısına dayanır; Social'den olay aktarımının güncelliği ortak entegrasyonda doğrulanmalıdır. Kanıt: [sorgu filtresi](../../../backend/services/community-service/src/main/java/com/ltz/community/infrastructure/persistence/repository/PostSpecifications.java), [detay erişimi](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/PostAccess.java), [engelleme politikası](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/BlockPolicy.java).

Gövde, başlık ve özet **düz metindir**; HTML/Markdown çalıştırılmaz, frontend metin olarak göstermelidir. Özet boşlukları birleştirir ve en çok 200 Unicode kod noktası alır. Spoiler alanı mevcut model ve gereksinimlerde tanımlı değil: ihtiyaç ve davranış için ürün kararı **Bekliyor**. Dosya yalnız mevcut gönderi görsel uçlarıyla, JPEG/PNG ve sunucu doğrulamasıyla eklenir; dış bağlantılar için ayrı alan veya otomatik ayrıştırma yoktur. Moderasyon [mevcut rapor uçları](../../../backend/services/community-service/src/main/java/com/ltz/community/api/controller/ReportController.java) ile başlar; Admin kararları Community'nin mevcut olay akışından uygulanır. Yönetim ve gönderi değişikliği için [Community güvenliği](../../../backend/services/community-service/src/main/java/com/ltz/community/infrastructure/security/SecurityConfig.java) ve sahiplik kontrolü kullanılır.

Gateway çerezi varsa kullanıcı Bearer token'ına dönüştürür; Community token'ı JWKS, issuer ve audience ile kendi sınırında doğrular. Anonim GET mümkündür; hatalı/süresi dolmuş/servis token'ı `401` alır. Okuma yanıtı kullanıcıya göre değişebildiğinden paylaşımlı cache kullanılmamalıdır. **Yeni ENV değişkeni gerekmiyor.** Mevcut `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` Auth/JWKS yapılandırması; `GAME_SERVICE_URL` yalnız oluşturmadaki oyun doğrulaması; mevcut Community→Auth servis istemcisi için `AUTH_COMMUNITY_SERVICE_CLIENT_SECRET` mevcut konfigürasyondur. Değer ve dağıtım sahipleri sırasıyla Auth/ortam ekibi, Game/ortam ekibi ve Auth/Community ortam ekibidir; bu görevde ENV dosyası değiştirilmedi. Kanıt: [Community application.properties](../../../backend/services/community-service/src/main/resources/application.properties), [Gateway çerez filtresi](../../../backend/services/api-gateway/src/main/java/com/ltz/gateway/security/CookieBearerFilter.java).

- `400`: geçersiz UUID, tür veya sayfalama; yazmada biçimsiz gövde.
- `401`: korumalı yazmada kimlik yok veya herhangi bir uçta geçersiz/yanlış türde token.
- `403`: başkasının gönderisini yönetme; public okuma için üretilmez.
- `404`: görünmeyen/yok/yanlış oyuna ait detay ve geçersiz kaynak; liste bilinmeyen oyun için boş döner.
- `422`: `contentType`, başlık, `gameId` ve forum birlikteliği kuralları ihlal edildiğinde veya oyun katalogda yayımlanmıyorsa.
- `429` + `Retry-After: 3600`: kullanıcı başına son bir saatte 10 türlü içerik oluşturma sınırı; **yalnız oluşturma** için, okuma oran sınırı yok. Eşzamanlı yazmalar [veritabanı işlemi ve kullanıcı kilidiyle](../../../backend/services/community-service/src/main/java/com/ltz/community/infrastructure/persistence/GameContentQuotaAdapter.java) sıraya alınır.
- `503`: oluşturma sırasında Game doğrulaması veya yerel DB geçici erişilemez; kesinti boş listeye çevrilmez. Gateway'in kendi 502/503/504 yanıtı ayrı katmandadır.

## Ekteki yedi sözleşme maddesi

| Madde | Durum | Kanıt / kalan karar |
| --- | --- | --- |
| 1. `gameId`, tür, `page`, `size`; `size ≤ 50` | **Uygulandı** | [Controller ve istek DTO'su](../../../backend/services/community-service/src/main/java/com/ltz/community/api/dto/request/GameContentListRequest.java), [API kataloğu](../../api.md). |
| 2. Kararlı sıra ve `totalElements` anlamı | **Uygulandı** | [GameContentService](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/GameContentService.java) aynı filtreli JPA sayfasını ve sayıyı kullanır; [PostSpecifications](../../../backend/services/community-service/src/main/java/com/ltz/community/infrastructure/persistence/repository/PostSpecifications.java) tarih/id DESC uygular. |
| 3. Özet alanları, gerçek detay yolu, spoiler | **Uygulandı** (alanlar); spoiler kararı **Bekliyor** | [Özet DTO](../../../backend/services/community-service/src/main/java/com/ltz/community/api/dto/response/GameContentSummaryResponse.java); spoiler gereksinimi bulunmadı, alan eklenmedi. |
| 4. Yayın/gizleme/silme/engel/gizli forum tutarlılığı | **Uygulandı** | Liste filtresi, detay erişimi ve [PostgreSQL entegrasyon testi](../../../backend/services/community-service/src/test/java/com/ltz/community/application/service/GameContentServiceIntegrationTest.java); Social olaylarının gerçek akışı **Bekliyor**. |
| 5. Yazma yetkisi, sahiplik, moderasyon ve rapor | **Mevcut davranışla karşılandı** | [PostService](../../../backend/services/community-service/src/main/java/com/ltz/community/application/service/PostService.java), [SecurityConfig](../../../backend/services/community-service/src/main/java/com/ltz/community/infrastructure/security/SecurityConfig.java), [ReportController](../../../backend/services/community-service/src/main/java/com/ltz/community/api/controller/ReportController.java). Admin ile canlı akış doğrulaması **Bekliyor**. |
| 6. Boş sonuç ile kesinti ayrımı; 401/403/404/429/503 | **Uygulandı** (ilgili uçlarda) | [Hata eşlemesi](../../../backend/services/community-service/src/main/java/com/ltz/community/api/exception/GlobalExceptionHandler.java), [controller testleri](../../../backend/services/community-service/src/test/java/com/ltz/community/api/controller/GameContentControllerTest.java), [güvenlik testleri](../../../backend/services/community-service/src/test/java/com/ltz/community/infrastructure/security/InternalEndpointSecurityTest.java), [eşzamanlı kota testi](../../../backend/services/community-service/src/test/java/com/ltz/community/infrastructure/persistence/GameContentQuotaIntegrationTest.java). Okuma public olduğundan 403/429 üretmez; Gateway gerçek kesinti davranışı **Bekliyor**. |
| 7. Düz metin, dosya ve dış bağlantı | **Mevcut davranışla karşılandı** | [Post doğrulaması](../../../backend/services/community-service/src/main/java/com/ltz/community/domain/entity/Post.java), [görsel ucu](../../../backend/services/community-service/src/main/java/com/ltz/community/api/controller/PostImageController.java), [SECURITY.md](../../SECURITY.md). Zengin metin/dış URL alanı eklenmedi. |

## Doğrulama ve açık işler

7 Kasım 2026 yerel doğrulaması: `mvn -B -f backend/pom.xml -pl services/community-service verify` **BUILD SUCCESS**, 495 test; toplam kapsam %99,07, değişen Java satırları %99,41; Checkstyle 0. PMD 6 mevcut dondurulmuş ihlal bildirdi. `node pre-push/ci/lint-ratchet.mjs check --modules services/community-service` **yeşil**, yeni ihlal 0. Ayrıca `pre-push/pre-push.cmd community-service dev` **BACKEND TEST CHECK PASSED** verdi: Community Maven Verify, test, kapsam ve yeni ihlal denetimi geçti. Bu çıktı yalnız Community kapsamına aittir. SonarQube sunucusu ayakta olsa da depodaki `sonar.cmd scan dev` tüm backend servislerini test eder; kullanıcı yalnız Community testlerini istediği için Sonar analizi çalıştırılmadı. Gerçek Gateway/Auth/Game/Social/Admin servisleriyle E2E ve canlı migration uygulanması **doğrulanmadı**.

| Bekliyor | Sorumlu |
| --- | --- |
| Game modül DTO'sunun `List<Achievement>` dışındaki türlere uygun sürümlemesi, eski istemci için deprecation, durum/hata eşlemesi; gerekli görülürse kimlik aktarımı ve sınırlı çağrılı RestClient adaptörü. Mevcut `INTEGRATION_PENDING` korunsun. | Hamza / Game Service |
| Oyun detayının frontend'den Gateway üzerinden Community liste/detay yoluna, kullanıcının oturumuyla bağlanması; 401/404/429/503 ve spoiler kararı için ekran davranışı. | Frontend sahibi ve Nisa/Hamza |
| Gateway rotası kodda var; çalışan Gateway üzerinden yol, çerez aktarımı, cache ve kesinti davranışının doğrulanması. Okuma oran sınırı gerekiyorsa ürün/Gateway kararı. | Gateway sahibi |
| Social engelleme olaylarının Community yerel yansımasına zamanında geçmesi; Admin rapor ve gizleme kararlarının gerçek akışta görünmesi. | Umut / Social, ortak Admin sahibi, Nisa |
| İçerik sahipliği ve rehberin Content sınırı; spoiler ihtiyacı; kapsam önceliği ve hedef tarih; Nisa/Hamza ortak onayı. | Nisa ve Hamza |

Ortak kabulde gerçek servislerle şu durumlar sınanmalı: görünür içerik ve boş sonuç; eşit tarihli sayfaların çakışmaması; engellenen yazar ve `totalElements`; moderasyonla gizleme ve rapor akışı; yetkisiz/hatalı token; farklı oyun kimliği; Game doğrulama ve DB kesintisi; Gateway çerez aktarımı. **Community sağlayıcı uygulamasının yerel test başarısı GAME-11'in tamamlandığı anlamına gelmez.** Sağlayıcı API gerçek ortamda erişilebilir ve bu ortak senaryolar başarılı olduğunda genel görev tamamlanabilir.
