# LTZ-KER-USER-01 — Oyuncu Profili Oluştur ve Düzenle

Sorumlu: Kerem. Görevler: USER-01, USER-02, USER-03, USER-04. Başlangıç: 2026-10-30.
Durum: **Faz 1a tamamlandı (yerel doğrulama). Faz 1b (USER-03/04 medya) bekliyor.**

## Faz 1a — yapılanlar

| Alan | Kayıt |
| --- | --- |
| USER-01 | `GET /api/v1/user/me` (profil yoksa oluşturur), Auth `UserRegistered` ile kullanıcı adı kopyası |
| USER-02 | `PATCH /api/v1/user/me/profile`: görünen ad, biyografi, ülke, oyuncu türleri, favori tür/platform; iyimser kilit (`version`) |
| Auth bağı | Kullanıcı JWT'si (JWKS, RS256) + servis JWT'si (`svc+jwt`), yola göre ayrı güvenlik zinciri |
| Hesap silme | `DELETE /internal/v1/accounts/{id}` (rehberdeki 17 senaryo + 1) |
| Olay girişi | `POST /internal/events`: `UserRegistered`, `AccountStatusChanged`, `AccountEmailChanged`; inbox ile tekilleştirme |
| Migration | `V71__create_user_profile_tables.sql` (`user_profiles`, `user_media`, `inbox_events`) |
| Belgeler | `docs/api.md` USER (4 kayıt), `docs/database.md` §3.2, `docs/er-diagram.md` §3, `docs-security/user-service/NOTLAR.md`, `docs-user/` |

## Doğrulama (yerel, 2026-10-30)

| Kontrol | Sonuç |
| --- | --- |
| `mvn -pl services/user-service verify` (JDK 25, Docker Desktop + Testcontainers `postgres:17-alpine`) | **BUILD SUCCESS, 80 test PASS** (AccountErasureTest 18, InternalEventTest 27, MeControllerTest 21, ProfileTextTest 13, contextLoads 1) |
| Satır kapsamı (JaCoCo) | **%95,26** (422/443) |
| Checkstyle / PMD (`verify` içinde) | Temiz |
| `node pre-push/ci/lint-ratchet.mjs check --modules services/user-service` | Yeşil: yeni ihlal yok, eski borç 0 |
| `.\pre-push\pre-push.cmd user-service` | **Çalıştırılmadı** (gerçek Compose/ENV kullanabilir; kullanıcı onayı bekleniyor) |
| Gerçek Auth ile uçtan uca | **NOT VERIFIED** (Auth hedefi `user` açılmadı; test token'ları Auth'un `ServiceTokenIssuer` biçiminde üretildi) |
| SonarQube | Çalıştırılmadı |

## Testlerin bulduğu ve düzeltilen hatalar

1. `PATCH` yanıtındaki `version` bayattı (sürüm commit'te artıyor, yanıt önce okunuyordu); istemci aynı sürümle tekrar
   gönderirse haksız `409` alırdı. Yazma yanıttan önce flush edilir.
2. `iat` eksik token kabul ediliyordu (Spring `exp-1 sn` atıyor). İmza çözümünde zorunlu kılındı.

## Açık işler

- Faz 1b: avatar/kapak yükleme, görsel işleme, `/media/{key}`, temizleyici, hesap silmede dosya temizliği, compose volume'ü ve
  Dockerfile medya klasörü izni, `folder-structure.md`'ye `infrastructure/storage` paketi.
- Auth talepleri: [REQ-01](../integration/auth/auth-service-talepleri/auth-requests-01.md) (hedef açma),
  [REQ-02](../integration/auth/auth-service-talepleri/auth-requests-02.md) (mevcut hesaplar). REQ-03 kapandı.
- `pre-push` resmî kapısı ve Sonar kullanıcı onayıyla.
