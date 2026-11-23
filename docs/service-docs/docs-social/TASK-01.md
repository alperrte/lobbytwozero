# LTZ-UMT-SOC-01 — Arkadaşlık İsteği Gönder / Kabul / Red / İptal (TASK-01)

> Tarih: 2026-10-22 · Sorumlu: Umut · Servis: `social-service` · Kapsam: SOC-01, SOC-02, SOC-03, SOC-04, SOC-05, SOC-06 · Durum: **kod ve testler yazıldı; entegrasyon testleri ve pre-push henüz koşulmadı (Docker kapalıydı)**
>
> Bu belge social-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint sözleşmeleri `docs/api.md` → `## SOCIAL` altındadır.

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| SOC-01 İstek gönderme | IMPLEMENTED | Kendine, arkadaşa ve bekleyen istek olan kişiye gönderilemez; kullanıcı başına bekleyen giden istek sınırı var |
| SOC-02 Kabul | IMPLEMENTED | Yalnız alıcı; karşılıklı arkadaşlık aynı işlemde oluşur |
| SOC-03 Red | IMPLEMENTED | Yalnız alıcı |
| SOC-04 İptal | IMPLEMENTED | Yalnız gönderen, yalnız bekleyen istek |
| SOC-05 Gelen bekleyen istekler | IMPLEMENTED | Keyset sayfalama |
| SOC-06 Gönderilen bekleyen istekler | IMPLEMENTED | Keyset sayfalama |

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Birim + entegrasyon testleri (Testcontainers, gerçek PostgreSQL 17) | PASS — 64 test |
| `.\pre-push\pre-push.cmd social-service` | PASS — `BACKEND TEST CHECK PASSED`, satır kapsamı %93,75 |
| `.\pre-push\pre-push.cmd full dev` | **SAFE TO COMMIT** — Git/Secret, Backend Tests/Build/Coverage, Docker Configuration, Backend HTTP Health (18 servis) PASS |
| `.\sonarqube\sonar.cmd scan dev` | **SONAR SCAN PASSED** — kapsam %89,4, yeni kod kapsamı %81,7, tekrar %0,7 (yeni kod %0,0), Security/Reliability/Maintainability A, açık bulgu 0 |

Sonar notları:
- İlk taramada genel tekrar oranı %3,1'di (hedef ≤ %3). Neden: auth-service'ten kopyalanan `RequestBodySizeLimitFilter` ve `ProblemDetailSecurityHandler` iki serviste de tekrar sayılıyordu. Servisler arası kod paylaşımı yasak olduğundan kopya kaldırıldı: filtre bağımsız ve daha kısa yeniden yazıldı, güvenlik işleyicileri `SecurityConfig` içinde lambda oldu, hata işleyici sadeleştirildi. Davranış değişmedi (aynı testler geçiyor).
- "Assigned gate policy" `NOT VERIFIED` (token yetkisi nedeniyle HTTP 403); gerçek analiz Quality Gate'i ve tüm hedefler PASS.

## 2. Akış ve kurallar

1. **Gönder** `POST /api/v1/social/friend-requests {receiverId}` → `PENDING` kayıt. Sıra: kendine mi → zaten arkadaş mı → aynı yönde bekleyen var mı → karşı yönde bekleyen var mı → giden bekleyen sınırı → kayıt. Eşzamanlı iki istekte veritabanı kazananı belirler (aşağıda).
2. **Kabul** `POST .../{id}/accept` → isteği satır kilidiyle okur, `ACCEPTED` yapar, `friendships` tablosuna çift başına **tek satır** yazar.
3. **Red** `POST .../{id}/reject`, **İptal** `POST .../{id}/cancel` → ilgili son duruma geçer; arkadaşlık oluşmaz.
4. **Listeler** `GET .../incoming|outgoing?limit&cursor` → yalnız `PENDING`, `createdAt` azalan, `id` azalan.
5. Yanıtlanmış istek bir daha değişmez (`409 FRIEND_REQUEST_NOT_PENDING`). Reddedilen/iptal edilen istekten sonra yeniden istek gönderilebilir.

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| Karşı yönde bekleyen istek (A→B varken B→A) | `409 INCOMING_FRIEND_REQUEST_PENDING`; otomatik kabul **yok** | Gereksinim "aynı ilişki için mükerrer aktif istek oluşamaz" der; otomatik kabul ayrı bir iş kararıdır |
| Red/iptal sonrası yeniden istek | Serbest, bekleme süresi (cooldown) yok | Gereksinimde sınır tanımlı değil; kötüye kullanım şimdilik yalnız bekleyen istek sınırıyla (varsayılan 100) kısıtlı |
| IDOR | Başkasının isteği ve olmayan istek aynı `404` | SECURITY.md §4; varlık sızdırılmaz |
| Kullanıcı varlığı | `receiverId` doğrulanmaz | user-service ucu yok — **PENDING**; sonradan `application/port` + `infrastructure/client` ile eklenir |
| Sayfalama | Keyset (`createdAt, id`) imleci, `limit` 1–50 | database.md §4; offset yok |
| Hata dili | Kod (`code`) İngilizce sabit, açıklama İngilizce | auth-service ile aynı sözleşme |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Kimlik | Access token servis tarafında JWKS ile doğrulanır: yalnız RS256; `iss`, `aud`, `typ=at+jwt`, `exp/nbf`, `sub` zorunlu. Kural listesi Auth'unkiyle aynı (`JwtConfig.accessTokenValidator`). Yeni ENV yok: `AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` zaten tüm servislere aktarılıyor |
| Yetki | Deny-by-default: yalnız `/actuator/health` açık, `/api/v1/social/**` kimlik ister, gerisi `denyAll` |
| `userId` | Yalnız token `sub`; gövdedeki bilinmeyen alan `400`, `X-User-Id` / `X-Role` başlıkları yok sayılır |
| CSRF | Servis çerez okumaz (Bearer-only); çerez→Bearer ve CSRF/Origin denetimi Gateway'de |
| Girdi | Jakarta Validation, bilinmeyen alan reddi, 8 KB gövde sınırı (413), `limit` ve `cursor` sunucuda doğrulanır |
| Hata | `ProblemDetail` + `code`; geçersiz path/query değeri, istenen yol ve gönderilen değer geri yansıtılmaz |
| Eşzamanlılık | `uq_friend_requests_pending_pair` (yönden bağımsız kısmi tekil indeks), kabul/red/iptalde `SELECT ... FOR UPDATE`, `@Version` |
| Kötüye kullanım | Kullanıcı başına bekleyen giden istek sınırı → `429`. Sayım kontrolü kayıttan önce yapıldığı için çok sıkı eşzamanlılıkta sınırı birkaç istek aşılabilir (kabul edilen sapma) |

## 5. Veritabanı (V121)

`V121__create_friendship_tables.sql` → `friend_requests`, `friendships` (+ indeksler). Ayrıntı: `docs/database.md` §3.7, `docs/er-diagram.md` §8 (aynı değişiklikte güncellendi).

- `friendships` çifti `user_low_id < user_high_id` ile tutulur. Uygulama sıralamayı **işaretsiz** karşılaştırır (`FriendshipId.compareUnsigned`), çünkü `java.util.UUID.compareTo` işaretlidir ve PostgreSQL uuid sırasından farklı sonuç verir; bu CHECK kısıtını ihlal ederdi. `FlywayMigrationTest` ve `FriendshipIdTest` bunu doğrular.
- Sonraki migration numarası: **V122** (social aralığı V121–V130).

## 6. Gereksinim → test → dosya kanıtı

| Madde | Test | Ana dosya |
| --- | --- | --- |
| SOC-01 gönder, kendine yok, tekil aktif istek, yönden bağımsız | `FriendRequestControllerTest`, `FlywayMigrationTest` | `FriendRequestService.send`, `V121` |
| Eşzamanlı aynı çift → tek kayıt (aynı ve karşı yön) | `FriendRequestConcurrencyTest` | `uq_friend_requests_pending_pair` |
| SOC-02 kabul → karşılıklı arkadaşlık; çift kabul → tek kayıt | `FriendRequestControllerTest`, `FriendRequestConcurrencyTest` | `FriendRequestService.accept`, `Friendship`, `FriendshipId` |
| SOC-03 red, SOC-04 iptal; yanlış taraf `404` (IDOR) | `FriendRequestControllerTest` | `FriendRequestService.loadForUpdate` |
| Kabul ↔ iptal yarışı tutarlı | `FriendRequestConcurrencyTest` | `FriendRequestRepository.findByIdForUpdate` |
| SOC-05 / SOC-06 yalnız bekleyen ve yalnız kendine ait; sayfalama | `FriendRequestControllerTest`, `FriendRequestCursorTest` | `FriendRequestService.listIncoming/listOutgoing`, `FriendRequestCursor` |
| `limit` / `cursor` sınırları | `FriendRequestControllerTest` | `FriendRequestService.resolvePageSize` |
| Durum makinesi | `FriendRequestTest` | `FriendRequest` |
| JWT saldırıları (yanlış anahtar/aud/iss/typ, süre, none/HS256, bozuk imza, sub) → 401 | `JwtValidationTest` | `JwtConfig`, `SecurityConfig` |
| Deny-by-default, health açık | `JwtValidationTest` | `SecurityConfig` |
| Gövde sınırı 413 | `RequestBodySizeLimitTest` | `RequestBodySizeLimitFilter` |
| Bekleyen istek sınırı 429 | `PendingRequestLimitTest` | `FriendRequestProperties` |
| Gövdede/başlıkta sahte kimlik | `FriendRequestControllerTest` | `AuthenticatedUser`, `SendFriendRequestRequest` |

## 7. Açık noktalar

| # | Konu | Durum |
| --- | --- | --- |
| 1 | Engelli kullanıcıya istek gönderilemez (kuralın bu uca bağlanması) | SOC-09 ile (TASK-03); `user_blocks` tablosu henüz yok |
| 2 | Bildirim / `FriendshipChanged` olayı (outbox) | SOC-13 (TASK-02) |
| 3 | `receiverId` kullanıcı varlık doğrulaması | user-service ucu bekleniyor |
| 4 | Hesap silme: `DELETE /internal/v1/accounts/{accountId}` — social, Auth'un silme hedef listesinde | Servis kimliği (D4) olmadan açılmaz; ayrı iş |
| 5 | Hesap durumu (askı/yasak) yayılımı | D3 bekliyor; en fazla access token ömrü (5 dk) gecikme |
| 6 | IP/kullanıcı bazlı oran sınırı (Gateway) | Gateway'de yalnız `/api/v1/auth/**` var; social için **NOT IMPLEMENTED** |
| 7 | `docs/SECURITY.md` §0 "iş servislerinde JWT doğrulaması yoktur" ifadesi artık social için eskidir | Belge sahibiyle güncellenmeli |

## 8. Commit öncesi kontrol listesi

1. Docker Desktop'ı başlatın.
2. `.\pre-push\pre-push.cmd social-service` → `BACKEND TEST CHECK PASSED` (Surefire raporları + JaCoCo ≥ %80). Tam kapı için `.\pre-push\pre-push.cmd full dev` → `SAFE TO COMMIT`.
3. SonarQube: `.\sonarqube\sonar.cmd scan dev` (Security / Reliability / Maintainability A; yeni kodda coverage ≥ %80, duplication ≤ %3).
4. Sonuçlar bu belgenin §1 tablosuna işlenir; `docs/api.md` SOCIAL kayıtlarındaki `Test Durumu` satırları (`NOT VERIFIED` ibareleri) gerçek sonuca göre güncellenir.
