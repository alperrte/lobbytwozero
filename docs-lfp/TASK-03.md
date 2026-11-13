# LTZ-UMT-LFP-03 — İlan Sahibine Mesaj ve Sosyal Gizlilik Entegrasyonu (TASK-03)

> Tarih: 2026-11-13 · Sorumlu: Umut · Servis: `lfp-service` · Dal: `feature/lfp-service` · Kapsam: LFP-16, LFP-17, LFP-18 · Durum: **kod, testler ve belgeler yazıldı; testler henüz çalıştırılmadı** (kullanıcı kararı: servisin tüm görevleri bitti, merge öncesi pre-push ile birlikte koşulacak)
>
> Bu belge lfp-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint sözleşmeleri `docs/api.md` → `## LFP` altındadır. Önceki görevler: [TASK-01](TASK-01.md), [TASK-02](TASK-02.md).

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| LFP-16 İlan sahibiyle iletişim | IMPLEMENTED | `POST /api/v1/lfp/listings/{id}/contact` izni + alıcıyı döner; sohbet messaging-service'te açılır, LFP mesaj altyapısı kurmaz |
| LFP-17 Gizlilik ve engelleme | IMPLEMENTED | Engel (iki yönde) ilanı listede gizler, detay/iletişim 404; Social'a ulaşılamazsa fail-closed 503 |
| LFP-18 Servis sınırları ve entegrasyon | IMPLEMENTED | İlan verisi yalnız `lfp_db`'de; REF kimlikler; sahiplik (TASK-02); oyun game-service'te doğrulanır (TASK-01); Auth hesap silme iç ucu; Social engel sorgusu; user/notification için bkz. §3 |

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Derleme (`test-compile`, JDK 25) | PASS — Checkstyle derleme sırasında temiz |
| Birim + entegrasyon testleri (toplam 141 test; bu görevde 41 yeni) | **ÇALIŞTIRILMADI** — merge öncesi |
| JaCoCo, PMD, `lint-ratchet`, `pre-push full dev`, SonarQube | **ÇALIŞTIRILMADI** — merge öncesi |
| Gerçek Auth + Social + Messaging ile uçtan uca | NOT VERIFIED |

## 2. Uçlar ve akış

| Uç | Ne yapar |
| --- | --- |
| `POST /api/v1/lfp/listings/{id}/contact` | İletişim izni; `recipientId` = ilan sahibi |
| `DELETE /internal/v1/accounts/{accountId}` | Auth → LFP hesap verisi silme (iç uç) |
| `GET /api/v1/lfp/listings`, `GET /api/v1/lfp/listings/{id}` | (TASK-02) artık engel kuralını uygular |

1. **Engel sorgusu:** `BlockGuard` → `SocialBlockCheckClient` → Auth'tan `aud=social-service`, `scope=blocks:check` servis token'ı (`AuthServiceTokenClient`, 60 sn, en çok 45 sn yeniden kullanım) → `POST {SOCIAL_SERVICE_URL}/internal/v1/blocks/check`. Social yanıtı eksik/fazla/bozuksa güvenilmez sayılır; her hata `503 BLOCK_CHECK_UNAVAILABLE`. İsteyenin kendi ilanı için sorulmaz, boş partide çağrı yapılmaz.
2. **Liste:** her parti (en çok `limit` ilan) kısa bir salt okunur transaction'da okunur, roller yüklenir; Social çağrısı transaction **dışında** yapılır (havuzda 3 bağlantı var, uzak çağrı süresince tutulmaz). Engelli sahiplerin ilanları atlanır; sayfa en çok 3 partide doldurulur, imleç son **incelenen** ilandır (tekrar/atlama yok).
3. **Detay:** ilan yok ya da sahibiyle engel var → aynı `404`; sahip kendi ilanını her zaman görür.
4. **İletişim:** ilan var mı (404) → kendi ilanı mı (400) → engel (404) → açık mı (409) → `{listingId, recipientId}`. İstemci ardından `POST /api/v1/messaging/conversations/direct {"userId": recipientId}`; Messaging engeli ayrıca denetler.
5. **Hesap silme:** güvenlik zinciri `/internal/**` yollarında yalnız servis token'ı doğrulayıcısını kullanır (`typ=svc+jwt`, `aud=lfp-service`, `sub=svc:…`, `jti`, ömür ≤ 60 sn); `DELETE` yalnız `SCOPE_account:erase` ile; controller `sub=svc:auth-service` ve `account_id = yol` denetler → tek SQL ile hesabın tüm ilanları (roller FK cascade).

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| İletişim modeli | LFP izin + alıcı döner; sohbeti istemci messaging'de açar | LFP-16 "ikinci mesajlaşma altyapısı oluşturulmaması". LFP'nin kullanıcı adına messaging'i çağırması kullanıcı token'ını servisler arasında taşımayı (yeni güven modeli, SECURITY.md §19 onayı) gerektirirdi |
| Engelli ilan | Listede gizli; detay ve iletişim `404` (ayırt edilmez) | Engelin varlığı sızdırılmasın; Messaging'in `403`'ünden farkı: burada ilanın kendisi de gizli |
| Engel yönü | Herhangi bir yönde engel iki tarafı da etkiler | Social'ın sorgusu yönü vermez (sızdırmaz); SECURITY.md §4 "engellenen kullanıcı LFP iletişimi başlatamaz" |
| Social kapalıysa | Liste/detay/iletişim `503`; yazma uçları ve "kendi ilanlarım" çalışır | Fail-closed (SECURITY.md §0); engel kontrolü olmayan uçlar etkilenmesin |
| Kısa sayfa | Engelliler atlanınca sayfa `limit`'ten kısa olabilir (en çok 3 parti) | Sınırsız okuma döngüsü yerine sabit maliyet; `nextCursor` devamı gösterir |
| Hesap silme | Hesabın tüm ilanları kalıcı silinir (soft delete yok) | `AccountDeleted` = kişisel veri kalmadı; ilan başka kullanıcıya ait veri taşımaz, anonimleştirilecek satır yok |
| user-service | Çağrı yok; istemci sahibin adını/avatarını `USER — Toplu profil kartı` ile alır | LFP kişisel veri saklamaz; ad önbelleği olay aboneliği gerektirirdi |
| notification-service | Bildirim yok | notification'da alıcı uç/olay teslimi tanımsız (Messaging'deki durumla aynı) |
| game-service | Kimliksiz kart ucu (TASK-01) | Herkese açık; Auth'tan yeni çift gerekmedi |
| `AccountStatusChanged` | Tüketilmiyor | Olay teslimi (broker/iç olay ucu) kararı bekliyor; askıya alınan kullanıcının açık ilanları süresi dolana kadar görünür (en çok 7 gün) |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Token ayrımı | Kullanıcı uçları yalnız `at+jwt`, iç uçlar yalnız `svc+jwt` (yol bazlı doğrulayıcı seçimi); biri diğerinin yerine geçemez (testli) |
| Hesap silme | `scope=account:erase` (yetkilendirme kuralı) + `sub=svc:auth-service` tam eşleşme + `account_id = yol`; ihlalde hiçbir şey silinmez; idempotent |
| Servis token'ı | Ömür ≤ 60 sn; `iat` çıkarılarak ömür kuralı aşılamaz (bitiş zamanı ayrıca denetlenir); `jti` zorunlu; RS256 dışı reddedilir |
| Engel | Fail-closed; yanıt doğrulaması (her sorulan kimlik için tam bir sonuç); engelin yönü/varlığı yanıtta yok |
| Gizli anahtar | Yalnız `AUTH_LFP_SERVICE_CLIENT_SECRET` ortam değişkeni; `ServiceClientProperties.toString` anahtarı yazmaz; token/anahtar/yanıt gövdesi loglanmaz |
| Kaynak | Social çağrısı transaction dışında; sayfa başına en çok 3 parti ve 3 Social çağrısı |
| Log | Hesap silmede yalnız hesap kimliği + silinen ilan sayısı |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| LFP-16 | `ListingPrivacyContactTest` (iletişim bölümü, 5) | `ListingQueryService.contact`, `ContactResponse` |
| LFP-17 | `ListingPrivacyContactTest` (liste/detay bölümü, 8), `SocialBlockCheckClientTest` (5), `AuthServiceTokenClientTest` (6) | `BlockGuard`, `SocialBlockCheckClient`, `AuthServiceTokenClient`, `ListingQueryService.search/get` |
| LFP-18 hesap silme | `AccountErasureTest` (16) | `AccountErasureController`, `AccountErasureService`, `ListingRepository.deleteAllOwnedBy`, `SecurityConfig`, `JwtConfig.serviceTokenValidator` |
| LFP-18 sınırlar | `ServiceBoundaryTest` (4), `JwtValidationTest` (iç yol), TASK-01/02 sahiplik ve oyun testleri | — |

## 6. Değişen dosyalar

- `backend/services/lfp-service/`: `application.properties` (`ltz.lfp.auth.service-audience`, `ltz.lfp.clients.*` Auth/Social, `ltz.lfp.blocks.adapter`), `SecurityConfig` (iç uçlar, servis token'ı, kapsam), `JwtConfig` (servis token'ı doğrulayıcısı), `AuthProperties`, `TokenClaims`, `ServiceClientProperties`, `LfpApplicationConfig`, `InternalApiPaths`, `AccountErasureController`, `AccountErasureService`, `ListingRepository.deleteAllOwnedBy`, `BlockGuard`, `ContactView`, `ContactResponse`, `ListingQueryService` (engel, parti doldurma, transaction dışı Social), `ListingController` (iletişim ucu), `LfpApiPaths`, `LfpErrorCode` (`CANNOT_CONTACT_SELF`, `BLOCK_CHECK_UNAVAILABLE`), portlar (`BlockCheckPort`, `ServiceTokenPort` ve istisnaları), istemciler (`SocialBlockCheckClient`, `AuthServiceTokenClient`; messaging ile aynı sözleşme).
- Testler: `ListingPrivacyContactTest`, `AccountErasureTest`, `SocialBlockCheckClientTest`, `AuthServiceTokenClientTest`, `ScriptedBlockCheck`, `IntegrationTestSupport` (servis token'ı, engel adaptörü), `GameServiceClientTest` ve `JwtValidationTest` güncellendi.
- `docker-compose.yml`: lfp-service ortamına **mevcut** `AUTH_LFP_SERVICE_CLIENT_SECRET` aktarıldı (yeni anahtar değil; `.env.example`, Auth ve `ops/dev.mjs` zaten tanımlıyor).
- `docs/api.md` (LFP ortak kurallar: engel; liste/detay güncellendi; iletişim ve hesap silme kayıtları; SOCIAL engel sorgusu tüketici notu), `docs-auth/entegrasyon/lfp-service.md`, `docs-security/lfp-service/NOTLAR.md`.

## 7. Açık noktalar

1. **Testler çalıştırılmadı.** Servisin tüm görevleri bitti: merge öncesi `.\pre-push\pre-push.cmd lfp-service` → `full dev` → SonarQube. Testcontainers Docker ister.
2. **Uçtan uca doğrulama:** DEV'de gerçek Auth + Social ile ilan listesi/iletişim ve Auth'un hesap silmede `lfp` görevini `CONFIRMED` görmesi NOT VERIFIED. `AUTH_LFP_SERVICE_CLIENT_SECRET` `.env.dev`'de dolu olmalı (`node ops/dev.mjs` üretir); lfp-service imajı yeniden derlenmeli.
3. **`docs-auth/entegrasyon/README.md`** tablosunda lfp-service satırı hâlâ "İskelet" yazıyor; dosya Auth sahibinindir (Kerem), güncellenmesi önerilir.
4. **`AccountStatusChanged` / bildirim** olay teslimi kararıyla birlikte (bkz. §3).
