# LTZ-NIS-CONT-03 — Resmî Duyurular ve Haber Moderasyonu (Rapor)

> Tarih: 2026-11-07 · Servis: content-service (Nisa) · Dal: `feature/content-service`
> Kapsam kaynağı: `docs-community/LTZ_PDA_Nisa_Guncellenmis.md` (content-service / CONT-03: CONT-04, CONT-10, CONT-17, CONT-19).
> **Test durumu:** moderasyon akışı düzeltmesinden sonra `mvn test` (content-service) 365 test ile geçti (yalnız bu düzeltme sonrası yerel koşu). `mvn verify`, Checkstyle/PMD kapısı ve SonarQube bu düzeltmeden sonra **yeniden çalıştırılmadı**; sahibi çalıştıracak. Düzeltme öncesi sürümün sonuçları (364 test, Sonar) bu sürümün doğrulaması değildir.

## 0. Düzeltme özeti (moderasyon akışı)

İlk teslimde moderatör için doğrudan yazma ucu (`POST /moderation/contents/{id}/actions`) vardı. Mimari akış (`docs/architecture.md`: "admin karar → `ModerationActionTaken` → kaynak servis uygular") bunu istemez: moderasyon kararı admin-service'te verilir, kaynak servis yalnız uygular. Bu düzeltmede:

- Doğrudan `POST` ucu, `ModerationActionRequest`, moderatör yolundaki servis metodu ve ilgili hata sınıfları (`ContentNotRemovableException`, `ContentNotRestorableException`) **kaldırıldı**. Moderasyon kaynaklı durum değişikliği yalnız `POST /internal/events` (`ModerationActionTaken`) ile uygulanır; servis token'ı kontrolleri ve inbox tekilleştirmesi aynen korundu.
- Olay yüküne **admin-service tarafından doğrulanmış karar rolü** (`moderatorRole`: `MODERATOR` | `ADMIN`) eklendi. Content-service içerik türünü kendi veritabanından okuyarak yetkiyi ayrıca denetler.
- `GET /moderation/contents/{id}/actions` (geçmiş) salt okunur olarak **korundu** (gerekçe §4).

## 1. PDA maddelerinin uygulama durumu

| Madde | Durum | Karşılığı |
| --- | --- | --- |
| **CONT-04** Platform duyuruları | Uygulandı | `ANNOUNCEMENT` oluşturma, yayın, düzenleme, silme, çeviri, görsel, kategori/kaynak/oyun yalnız `ADMIN`; **moderasyon olayıyla kaldırma ve geri alma da yalnız `ADMIN` kararıyla** uygulanır. Kullanıcı haberinden ayrım: `type=ANNOUNCEMENT` ve akışta `type` filtresi |
| **CONT-10** Moderatör kaldırması | Uygulandı (admin-service teslimi NOT VERIFIED) | `POST /internal/events` `ModerationActionTaken` (yalnız admin-service servis token'ı) ile `REMOVE`/`RESTORE`; `GET …/actions` salt okunur geçmiş. Gerekçe, karar veren, rolü, zaman ve olay değişmez kayda yazılır (V106/V107). Moderatör için doğrudan yazma ucu yoktur |
| **CONT-17** İçerik Üreticisi rolü | Uygulandı | `CONTENT_CREATOR` yüksek kota alır (saatte 30 içerik, 50 etkin taslak; normal 10 / 20). Normal kullanıcının yayımlama hakkı ve kendi haberini silme hakkı değişmedi |
| **CONT-19** Servis sınırları | Uygulandı / belgelendi | Haber ve duyuru yalnız `content_db`'dedir; başka servis DB'sine erişim yok (§5). Olay çıkışı (notification/admin) ertelendi |

## 2. Uçlar (`docs/api.md` → CONTENT)

| Method | Path | Yetki |
| --- | --- | --- |
| POST | `/internal/events` | Internal: Auth servis token'ı (`svc+jwt`, `aud=content-service`, `events:ingest`, `sub=svc:admin-service`) |
| GET | `/api/v1/content/moderation/contents/{contentId}/actions` | Moderator / Admin, salt okunur |
| ~~POST~~ | ~~`/api/v1/content/moderation/contents/{contentId}/actions`~~ | **Kaldırıldı** (`405`); `docs/api.md` kaydı silindi |
| (mevcut) PATCH/DELETE, çeviri, kategori, kaynak, oyun, görsel | | Resmî duyuruda yalnız `ADMIN`; yazar normal haberini `DELETE /contents/{id}` ile silebilir (kaldırılmış `REMOVED` içerik hariç) |

`ContentResponse` alanları (`removedAt`, `removalReason`) değişmedi.

## 3. Migration

- **V106** (`contents.removed_*`, `content_moderation_actions`, `inbox_events`) değişmedi (dev veritabanında uygulanmış).
- **V107** `record_moderation_authority.sql`: `content_moderation_actions.actor_role` (`MODERATOR` | `ADMIN`, admin-service'in doğruladığı karar rolü) ve karar yolunu yalnız `ADMIN_SERVICE` olarak kısıtlayan `source` CHECK'i (`NOT VALID`: olası eski satırları taramaz, migration'ı durdurmaz). Aralık V101–V110; **V108–V110 boş**.

## 4. Kararlar

| Konu | Karar |
| --- | --- |
| Kaldırma yolu | **Yalnız admin-service olayı** (düzeltme). Önceki "ikisi birden" kararı mimari akışla çeliştiği için geri alındı |
| Geri alma | Evet (`RESTORE`), aynı olay yoluyla; kaldırma bilgisi temizlenir, geçmiş korunur, öne çıkarma geri gelmez |
| İçerik Üreticisi | Yalnız yüksek kotalar |
| Olaylar | Content'ten dışarı olay (outbox) yok; broker kararı bekliyor |
| Geçmiş okuma | `GET` korundu: salt okunur, yalnız `MODERATOR`/`ADMIN` (JWT `roles`), rol içerik aranmadan denetlenir. Karar veren kimliği ve rolü yazara gösterilmez; yazar yalnız kendi içeriğinde gerekçeyi (`removalReason`) görür. Admin-service'in kendi geçmişi asıl kayıt olsa da, içeriğe bağlı kararı content tarafında görmek moderatör ekranı için gereklidir ve yazma yetkisi vermez |

### 4.1 Olay sözleşmesi ve karar yetkisi

Yük (`payload`): `targetType=CONTENT`, `targetId`, `action` (`REMOVE` | `RESTORE` | `DISMISS`), `moderatorId`, **`moderatorRole`** (`MODERATOR` | `ADMIN`; `REMOVE`/`RESTORE` için zorunlu), `reason` (1-500), `reportId` (opsiyonel). Zarf ve token kuralları `docs/api.md` → "İç Olay Girişi" kaydındadır.

- Servis token'ı çağıranın admin-service olduğunu kanıtlar (`sub=svc:<producer>` zarftaki `producer` ile eşleşmeli, `events:ingest` kapsamı). `moderatorRole` admin-service'in kendi yetkilendirmesinde doğruladığı karar rolüdür; content-service bu bilgiye servis token'ı geçerliyse güvenir.
- Content-service içerik türünü **kendi veritabanından** okuyup yetkiyi ayrıca denetler:

| İçerik türü | `MODERATOR` kararı | `ADMIN` kararı |
| --- | --- | --- |
| `NEWS`, `USER_CONTENT` | `REMOVE`, `RESTORE` uygulanır | `REMOVE`, `RESTORE` uygulanır |
| `ANNOUNCEMENT` | **reddedilir (403)** | `REMOVE`, `RESTORE` uygulanır |

- Yetersiz yetkide olay reddedilir: içerik durumu ve moderasyon geçmişi değişmez, olay inbox'a **yazılmaz** (işlem geri alınır); aynı `eventId` yetkili bir kararla yeniden gönderilebilir. Rol eksik/geçersizse 400.
- Başarılı olay yeniden teslim edilirse `DUPLICATE` döner, ikinci durum değişikliği ya da denetim kaydı oluşmaz. Yeni `eventId` ile gelen ama durumu zaten uygun karar (ör. kaldırılmış içeriğe ikinci `REMOVE`) etkisizdir ve ikinci kayıt yazmaz; ancak içerik varsa yetki yine önce denetlenir.

## 5. Servis sınırları ve entegrasyon (CONT-19)

| Servis | Yön | Sözleşme / durum |
| --- | --- | --- |
| auth-service | Content ← Auth | Kullanıcı (`at+jwt`) ve servis (`svc+jwt`) token'ları JWKS ile bu serviste doğrulanır; kullanıcı rolleri yalnız `roles` claim'inden. **Bekleyen iş:** Auth'ta `admin-service → content-service` çifti için `events:ingest` kapsamı (Kerem) |
| game-service | Content → Game | `POST /api/v1/game/games/batch` ile oyun doğrulama; gerçek servisle uçtan uca NOT VERIFIED |
| admin-service | Admin → Content | `POST /internal/events` (`ModerationActionTaken`). Admin-service sahipsiz ve sözleşmesi yok: teslim NOT VERIFIED (gerekenler §6) |
| notification-service | Content → Notification | **Yok** (olay çıkışı ertelendi): kaldırılan içeriğin yazarı bildirim almaz |
| user-service | — | Çağrı yok; yazar yalnız `authorId` ile döner |
| event-service, community-service | — | Entegrasyon gerekmedi; veriler kendi servislerinde |

Başka servisin DB'sine erişim yoktur.

## 6. Admin-service tarafında gerekenler (content-service kapsamı dışı; sahibi belirlenmeli)

1. Moderasyon kararını kendi kuyruğundan/ekranından verirken **karar veren kullanıcının rolünü doğrulayıp** olay yüküne `moderatorRole` olarak yazmak (`MODERATOR` | `ADMIN`).
2. `ANNOUNCEMENT` hedefli `REMOVE`/`RESTORE` kararını yalnız `ADMIN` rolüyle vermek (content bunu ayrıca zorlar; `MODERATOR` kararına 403 döner).
3. Content'e `ModerationActionTaken`'ı `POST {CONTENT_SERVICE_URL}/internal/events` ile, Auth'tan alınan 60 sn'lik `svc+jwt` token'ıyla (`aud=content-service`, `scope=events:ingest`, `sub=svc:admin-service`) iletmek; yük biçimi §4.1.
4. Sonuçları yorumlamak: `200 PROCESSED`/`DUPLICATE` başarı (içerik yok ya da durum uygunsa etkisiz); `400` (geçersiz yük/rol) ve `403` (yetersiz yetki) **kalıcı red** (yeniden denenmemeli, kayıt `PENDING_APPLY` yerine reddedilmiş olarak izlenmeli); `401`/`5xx` geçici (yeniden denenebilir).
5. Aynı `eventId`'yi yeniden denemelerde sabit tutmak (tekilleştirme anahtarı).
6. Auth tarafında `admin-service → content-service` çiftinin tanımlanması (Kerem).

## 7. Doğrulama senaryoları

| # | Senaryo | Beklenen |
| --- | --- | --- |
| 1 | Moderatör rolüyle `POST /api/v1/content/moderation/contents/{id}/actions` (herhangi bir gövde) | `405`; tokensız `401` |
| 2 | Admin-service token'ıyla `NEWS` için `REMOVE` (`moderatorRole=MODERATOR`) | 200 `PROCESSED`; içerik akıştan/detaydan/görselden çıkar; yazar gerekçeyi görür; geçmişte 1 kayıt (`actor_role=MODERATOR`, `source=ADMIN_SERVICE`) |
| 3 | `ANNOUNCEMENT` için `REMOVE` ve `RESTORE`, `moderatorRole=MODERATOR` | `403`; içerik, geçmiş ve inbox değişmez |
| 4 | Aynı olay `moderatorRole=ADMIN` ile (aynı `eventId`) | 200 `PROCESSED`; içerik kaldırılır; 1 kayıt |
| 5 | Başarılı olayın yeniden teslimi | 200 `DUPLICATE`; ikinci kayıt yok |
| 6 | `moderatorRole` yok / `PLAYER` / küçük harfli | `400`; inbox'a yazılmaz |
| 7 | Kullanıcı token'ı, süresi/audience'ı hatalı ya da kapsamsız servis token'ı, `sub`≠`producer` | `401` / `403`; servise ulaşmaz |
| 8 | `GET …/actions`: moderatör/admin → 200, normal kullanıcı → 403, tokensız → 401 | salt okunur |
| 9 | Yazar yayımlanmış haberini `DELETE /contents/{id}` ile siler; kaldırılmış (`REMOVED`) haberi silmeye çalışır | 204; `409` |
| 10 | `RESTORE` ile geri alınan içerik | akışta yeniden görünür, `featured=false`, geçmişte iki kayıt |

## 8. Testler

| Dosya | Kapsam |
| --- | --- |
| `ContentModerationServiceTest` | Yetki matrisi (duyuru yalnız ADMIN, REMOVE ve RESTORE), eksik rol, yetersiz kararın hiçbir şey yazmaması, gerekçe doğrulaması, kayıt alanları, geri alma, idempotans (kayıt yok), salt okunur geçmiş ve rol denetimi |
| `InboundEventServiceTest` | Üretici denetimi, yük ve `moderatorRole` doğrulaması (eksik/geçersiz rol kaydedilmez), tekilleştirme, DISMISS, yetersiz yetkinin yayılması |
| `ContentModerationControllerTest` | Geçmiş uç sözleşmesi ve hata eşlemesi; **yazma yöntemleri 405**; tokensız 401 |
| `InternalEventControllerTest` | Gerçek RS256 servis token'ı: kapsam, `sub`–`producer`, audience/issuer/ömür/`jti`, kullanıcı token'ı reddi, yetersiz yetkinin 403'ü |
| `ContentSecurityTest`, `JwtConfigTest`, `SecurityContextCurrentUserTest` | Yola göre token türü, servis token'ı doğrulayıcısı, rol okuma; doğrudan POST'un 405'i |
| `ContentModerationIntegrationTest` (gerçek PostgreSQL) | Olayla kaldırma sonrası akış/detay/görsel URL'sinin anında 404'ü, yazarın gerekçeyi görmesi, düzenleme engeli, geri alma ve geçmiş sırası, duyuru yetkisi, **reddedilen kararın iz bırakmaması ve aynı `eventId`'nin yetkili kararla işlenmesi** (işlemsiz test), yeniden teslimde ikinci kayıt olmaması, yazarın normal silmesi |
| `ContentRepositoryIntegrationTest` | V106/V107 kısıtları (`actor_role`, `source` yalnız `ADMIN_SERVICE`, `ck_contents_removal`, inbox PK) |

## 9. Açık işler

- Admin-service sahibi/sözleşmesi ve Auth `events:ingest` çifti: **NOT VERIFIED**.
- Content'ten dışarı olay çıkışı (outbox: `ContentRemoved` → notification): broker kararı sonrası.
- Hesap silme ucu: ayrı görev (`LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md`).
- Canlı (Docker) doğrulama bu düzeltme sonrası yapılmadı (V107 henüz dev veritabanına uygulanmadı).
