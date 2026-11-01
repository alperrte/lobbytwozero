# LTZ-UMT-MSG-01 — Birebir ve Grup Sohbetleri ile Üyelik Yetkileri (TASK-01)

> Tarih: 2026-11-01 · Sorumlu: Umut · Servis: `messaging-service` · Dal: `feature/messaging-service` · Kapsam: MSG-01, MSG-02, MSG-03, MSG-04, MSG-05, MSG-14, MSG-15 · Durum: **kod, testler ve belgeler tamam; gerçek Auth + Social ile uçtan uca doğrulanmadı**
>
> Bu belge messaging-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint sözleşmeleri `docs/api.md` → `## MESSAGING` altındadır.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| MSG-01 Birebir mesajlaşma | IMPLEMENTED (sohbet) | İki kullanıcı için tek birebir sohbet; aç/varsa getir. Mesaj gönderme/okuma MSG-02 görevinde (MSG-06/07) |
| MSG-02 Grup sohbeti oluşturma | IMPLEMENTED | Oluşturan `OWNER`, en fazla 100 üye |
| MSG-03 Grup üyeliği yönetimi | IMPLEMENTED | Üye ekleme (`OWNER`/`ADMIN`), rol değiştirme (`OWNER`), eski üyeyi yeniden ekleme |
| MSG-04 Gruptan ayrılma | IMPLEMENTED | Sahip ayrılınca sahiplik otomatik devredilir |
| MSG-05 Yöneticinin üye çıkarması | IMPLEMENTED | `OWNER` herkesi, `ADMIN` yalnız `MEMBER`'ı çıkarır |
| MSG-14 Sohbet erişim kontrolü | IMPLEMENTED | Yalnız aktif üye; yazma kuralı `ConversationAccessPolicy.requireCanWrite` hazır ve testli, mesaj uçları MSG-02 görevinde bu kuralı kullanacak |
| MSG-15 Engelleme kontrolü | IMPLEMENTED | social-service `POST /internal/v1/blocks/check`; engel öğrenilemezse fail-closed 503 |

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, gerçek PostgreSQL 17) | PASS — 80 test |
| JaCoCo (merkezi progressive politika) | PASS — satır kapsamı %97,40 (değişen satırlar %97,82) |
| Checkstyle / PMD yeni ihlal (`lint-ratchet`) | PASS — 0 yeni ihlal |
| `.\pre-push\pre-push.cmd messaging-service` | **BACKEND TEST CHECK PASSED** (ilk denemede "Stale checkstyle-result.xml" verdi, rapor dosyası aynı çalıştırmada yazılmış olmasına rağmen; aynı kodla ikinci denemede PASS — muhtemelen IDE/OneDrive'ın `target/`'a eşzamanlı erişimi) |
| `.\pre-push\pre-push.cmd full dev` | TASK sonunda raporlanır (bkz. sohbet özeti) |
| SonarQube | Çalıştırılmadı — commit öncesi Umut çalıştırır |
| Gerçek Auth token'ı + gerçek Social ile uçtan uca | NOT VERIFIED |

## 2. Uçlar ve akış

| Uç | Ne yapar |
| --- | --- |
| `POST /api/v1/messaging/conversations/direct` | Birebir sohbeti açar; yoksa 201, varsa 200 aynı sohbet |
| `POST /api/v1/messaging/conversations/groups` | Grup kurar (oluşturan `OWNER`) |
| `GET /api/v1/messaging/conversations/{id}` | Sohbet + aktif üyeler (yalnız üye) |
| `POST /api/v1/messaging/conversations/{id}/members` | Üye ekler (`OWNER`/`ADMIN`) |
| `DELETE /api/v1/messaging/conversations/{id}/members/{userId}` | Üye çıkarır (yetkili yönetici) |
| `PUT /api/v1/messaging/conversations/{id}/members/{userId}/role` | Rolü `ADMIN`/`MEMBER` yapar (`OWNER`) |
| `POST /api/v1/messaging/conversations/{id}/leave` | Gruptan ayrılır |

1. **Birebir sohbet:** kendine mi → engel sorgusu (Social, transaction dışında) → çift için advisory kilit → `direct_key` ile bul ya da oluştur (iki `MEMBER` satırı). Eşzamanlı açılışlar aynı sohbette buluşur (8 iş parçacıklı test).
2. **Grup kurma:** kimlikler tekilleştirilir, oluşturan listede olamaz, sınır denetlenir → oluşturan ↔ tüm ilk üyeler için tek toplu engel sorgusu → tek transaction'da sohbet + üyelikler.
3. **Üye ekleme:** yetki önce okunur (üye olmayan/yetkisiz kişi engel sorgusu tetikleyemez) → engel sorgusu → sohbet satırı `SELECT … FOR UPDATE` ile kilitlenip yetki, mevcut üyelik ve sınır yeniden denetlenir → yeni satır ya da eski üyeliği `MEMBER` olarak canlandırma. Hep-ya-hiç.
4. **Çıkarma / rol / ayrılma:** sohbet satırı kilitlenir, üyelik kilitten **sonra** okunur (eşzamanlı rol değişikliği/çıkarma görülsün), tür denetimi üyelikten sonradır (üye olmayan sohbetin türünü öğrenemez).
5. **Sahip ayrılınca:** ayrılış önce yazılır (tek aktif sahip indeksi satır satır denetlenir), sonra en eski `ADMIN`, yoksa en eski üye `OWNER` olur. Son üye ayrılırsa grup boş kalır.

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| Mesaj uçları | Bu görevde yok (kullanıcı kararı, 2026-11-01) | PDA'da MSG-06/07 ayrı görev (LTZ-UMT-MSG-02); erişim ve engel kuralları bu görevde hazırlandı |
| Üye ekleme yetkisi | Yalnız `OWNER`/`ADMIN` (kullanıcı kararı) | MSG-03 "grup erişim kuralları", MSG-05 yönetici yetkisi |
| Gruptaki engel | Yalnız ekleyen ↔ eklenen çifti; mevcut üyelik etkilenmez (kullanıcı kararı) | Tüm üyelerle denetim engelin varlığını dolaylı sızdırır; WhatsApp davranışına yakın |
| Sahip ayrılması | Otomatik devir: en eski `ADMIN`, yoksa en eski üye (kullanıcı kararı) | Grup sahipsiz kalmasın |
| Arkadaşlık şartı | Yok; yalnız engel denetlenir | LFP-16 ilan sahibiyle (arkadaş olmayan) iletişimi Messaging üzerinden ister; Social'da "arkadaş mı" iç ucu da yok |
| Engelin yönü | Yanıtta belirtilmez, her iki taraf `403 MESSAGING_NOT_ALLOWED` alır | Social'ın sorgusu yönü vermez (sızdırmaz) |
| Engel öğrenilemezse | `503 BLOCK_CHECK_UNAVAILABLE`, kayıt yok | SECURITY.md fail-closed; engel yokmuş gibi devam edilmez |
| Grup sınırı | 100 üye (sahip dahil), ayarlanabilir, üst sınır 100 | Social'ın engel sorgusu tek çağrıda en fazla 100 kimlik kabul eder; N+1 çağrı yok |
| Üyelik satırı | Silinmez; `left_at` / `removed_by` dolar, yeniden eklenince aynı satır | Kimin ne zaman çıkardığı izlenebilir; MSG-07'de "katıldıktan sonraki mesajlar" için `joined_at` hazır |
| IDOR | Var olmayan ve erişilemeyen sohbet aynı `404` | SECURITY.md §4 |
| Kullanıcı varlığı | Doğrulanmaz | user-service ucu yok — **PENDING** (Social'la aynı durum) |
| Rol adları | `OWNER`/`ADMIN`/`MEMBER` | database.md §3.8 |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Kimlik | Access token servis tarafında JWKS ile doğrulanır: yalnız RS256; `iss`, `aud`, `typ=at+jwt`, `exp`/`nbf`/`iat`/`sub` zorunlu (docs-auth/entegrasyon/README.md §1). Servis token'ı kullanıcı uçlarında geçmez |
| Yetki | Deny-by-default: yalnız `/actuator/health` açık, `/api/v1/messaging/**` kimlik ister, gerisi (`/internal/**` dahil) `denyAll`; üyelik ve rol servis katmanında |
| `userId` | Yalnız token `sub`; gövdedeki bilinmeyen alan `400`, `X-User-Id` yok sayılır (testli) |
| Girdi | Jakarta Validation, bilinmeyen alan reddi, 8 KB gövde sınırı (Content-Length ve chunked; 413), başlıkta kontrol karakteri yok |
| Servisler arası | Auth'tan `aud=social-service`, `scope=blocks:check`, 60 sn token (en fazla 45 sn yeniden kullanılır); gizli anahtar yalnız `AUTH_MESSAGING_SERVICE_CLIENT_SECRET`. Social yanıtı eksik/fazla/bozuksa güvenilmez sayılır; 3xx/4xx/5xx fail-closed. Token, gizli anahtar, yanıt gövdesi loglanmaz |
| Eşzamanlılık | Birebirde çift advisory kilidi + `uq_conversations_direct_key`; grupta sohbet satırı kilidi + `uq_conversation_members_active_owner` |
| Veri | `messaging_db` dışına erişim yok; kullanıcılar REF, FK yalnız servis içi (`ServiceBoundaryTest`) |
| Hata | `ProblemDetail` + `code`; `detail` gönderilen değeri yansıtmaz (`instance` Spring varsayılanı olarak yolu taşır) |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| MSG-01 | `DirectConversationTest` (8) | `ConversationService.openDirect`, `Conversation.directKey`, `DirectConversationLock` |
| MSG-02 | `GroupConversationTest` (9) | `ConversationService.createGroup` |
| MSG-03 | `GroupMembershipTest` ekleme/rol bölümleri | `GroupMembershipService.addMembers/changeRole` |
| MSG-04 | `GroupMembershipTest` ayrılma bölümü | `GroupMembershipService.leave` |
| MSG-05 | `GroupMembershipTest` çıkarma bölümü, `ConversationTest.rolYetkiMatrisi` | `GroupMembershipService.removeMember`, `MemberRole.canRemove` |
| MSG-14 | `ConversationAccessTest` (9) | `ConversationAccessPolicy`, `SecurityConfig`, `JwtConfig` |
| MSG-15 | `DirectConversationTest`, `GroupConversationTest`, `GroupMembershipTest`, `SocialBlockCheckClientTest` (5), `AuthServiceTokenClientTest` (6), `SocialAdapterFailClosedTest` (2) | `BlockGuard`, `SocialBlockCheckClient`, `AuthServiceTokenClient` |
| Şema | `FlywayMigrationTest` (6) | `V131__create_conversation_tables.sql` |
| Sınırlar | `ServiceBoundaryTest` (3), `RequestBodySizeLimitTest` (3) | `RequestBodySizeLimitFilter` |

## 6. Değişen dosyalar

- `backend/services/messaging-service/`: `pom.xml` (testcontainers-postgresql, test), `application.properties`, `V131__create_conversation_tables.sql`, `api/` (2 controller, DTO'lar, `GlobalExceptionHandler`), `application/` (servisler, `ConversationAccessPolicy`, `BlockGuard`, portlar, mapper), `domain/` (entity, enum, hata kodları), `infrastructure/` (Auth/Social istemcileri, güvenlik, yapılandırma, repository'ler) ve testler.
- `docker-compose.yml`: messaging-service ortamına **mevcut** `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` aktarıldı (yeni anahtar değil; Auth ile aynı değer, `.env.example`'da zaten var).
- `docs/api.md` (MESSAGING kayıtları; SOCIAL engel sorgusu tüketici notu), `docs/database.md` §3.8, `docs/er-diagram.md` §9, `docs-security/messaging-service/NOTLAR.md`.

## 7. Açık noktalar

1. **Hesap silme iç ucu** (`DELETE /internal/v1/accounts/{accountId}`) yok. Auth silme hedeflerinde `messaging` var ve Messaging artık üyelik verisi tutuyor; ayrı görev olarak yazılmalı (Social'daki `AccountErasureController` kalıbı, `sub=svc:auth-service` + `account_id` denetimi).
2. **Mesaj uçları** (MSG-06/07) ve okundu bilgisi (`last_read_message_id`, MSG-11/13) sonraki görevlerde; yazma kuralı hazır.
3. **Oran sınırı** (sohbet açma/grup kurma spam'i) yok — SECURITY.md §12, ayrı karar.
4. **Kullanıcı varlığı** doğrulanmıyor (user-service ucu yok).
5. **Gerçek Auth + Social uçtan uca** doğrulanmadı; DEV'de `AUTH_MESSAGING_SERVICE_CLIENT_SECRET` tanımlı değilse sohbet açma/grup kurma/ekleme 503 döner (beklenen fail-closed).
6. **Olay yok:** Bu görevde outbox olayı üretilmez (ör. "gruba eklendin" bildirimi); MSG-16 ile birlikte tasarlanacak.
