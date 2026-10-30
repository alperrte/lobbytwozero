# LTZ-KER-USER-01 — Oyuncu Profili Oluştur ve Düzenle

Sorumlu: Kerem. Görevler: USER-01, USER-02, USER-03, USER-04. Başlangıç: 2026-10-30.
Durum: **Faz 1a ve 1b tamamlandı (yerel doğrulama).** Gerçek Auth ve Docker imajı/volume doğrulaması NOT VERIFIED.

## Faz 1a — profil, Auth bağları (commit d7edfc5)

| Alan | Kayıt |
| --- | --- |
| USER-01 | `GET /api/v1/user/me` (profil yoksa oluşturur), Auth `UserRegistered` ile kullanıcı adı kopyası |
| USER-02 | `PATCH /api/v1/user/me/profile`: görünen ad, biyografi, ülke, oyuncu türleri, favori tür/platform; iyimser kilit (`version`) |
| Auth bağı | Kullanıcı JWT'si (JWKS, RS256) + servis JWT'si (`svc+jwt`), yola göre ayrı güvenlik zinciri, `iat` zorunlu |
| Hesap silme | `DELETE /internal/v1/accounts/{id}` (rehberdeki 17 senaryo + 1) |
| Olay girişi | `POST /internal/events`: `UserRegistered`, `AccountStatusChanged`, `AccountEmailChanged`; inbox ile tekilleştirme |
| Migration | `V71__create_user_profile_tables.sql` (`user_profiles`, `user_media`, `inbox_events`) |

## Faz 1b — profil görselleri (USER-03, USER-04)

| Alan | Kayıt |
| --- | --- |
| Yükleme | `PUT /api/v1/user/me/avatar` ve `/cover`: **ham gövde** (`image/jpeg\|png\|gif`), bayt sınırı akışta (avatar 2 MB, kapak 4 MB) |
| Kaldırma | `DELETE /api/v1/user/me/avatar` ve `/cover` (idempotent `204`) |
| Sunum | `GET /api/v1/user/media/{dosya}`: herkese açık, **DB'siz**, `immutable` 1 yıl önbellek, `nosniff`, `sandbox` CSP |
| İşleme | JDK `ImageIO` (yeni bağımlılık yok): magic byte, başlıktan boyut, **alt örnekleyerek açma**, EXIF yönü, yeniden kodlama (meta veri temizliği), 2 boyutlu çıktı, saydamlıkta PNG; GIF çözülmeden doğrulanıp temizlenir |
| Koruma | Kenar ≤ 8000, ≤ 40 MP, ≤ 100 GIF karesi; aynı anda 2 görsel; saatte 10 yükleme/kullanıcı (DB'den) |
| Tutarlılık | Yeni dosya → tek DB transaction'ı (eski `PENDING_DELETE`, yeni `CURRENT`) → commit sonrası eski dosya silinir; zamanlanmış temizleyici (5 dk) yeniden dener |
| Hesap silme | Önce tüm dosyalar (bekleyenler dahil), sonra satırlar; depo hatasında `503 ACCOUNT_ERASURE_PENDING` ve hiçbir satır silinmez |
| Altyapı | `docker-compose.yml`: `user_media` named volume'ü; `Dockerfile`: `/data/user-media` `ltz` kullanıcısına ait, `-Djava.awt.headless=true`; `docs/folder-structure.md`: `infrastructure/storage` |
| Yeni ENV | **Yok** (`ltz.user.media.*` dağıtım sabitleridir) |

## Doğrulama (yerel, 2026-10-30)

| Kontrol | Sonuç |
| --- | --- |
| `mvn -pl services/user-service verify` (JDK 25, Docker Desktop + Testcontainers `postgres:17-alpine`) | **BUILD SUCCESS, 133 test PASS** |
| Test dağılımı | MediaControllerTest 23, InternalEventTest 27, MeControllerTest 21, AccountErasureTest 18, ProfileTextTest 13, GifSanitizerTest 7, ImageIoInspectorTest 7, ExifOrientationTest 5, LocalFileMediaStorageTest 5, MediaCleanupServiceTest 3, MediaCleanupWorkerTest 2, AccountErasureStorageFailureTest 1, contextLoads 1 |
| Satır kapsamı (JaCoCo) | **%94,18** (890/945) |
| Checkstyle / PMD (`verify` içinde) | Temiz |
| `node pre-push/ci/lint-ratchet.mjs check --modules services/user-service` | **Yeşil**: yeni ihlal yok, eski borç 0 |
| `.\pre-push\pre-push.cmd service` | **Çalıştırılmadı** (gerçek Compose/ENV kullanabilir; kullanıcı onayı bekleniyor) |
| Docker imajı build / volume'de yazma / `docker compose config` | **NOT VERIFIED** |
| Gerçek Auth ile uçtan uca | **NOT VERIFIED** (Auth hedefi `user` açılmadı) |
| SonarQube | Çalıştırılmadı |

## Testlerin bulduğu ve düzeltilen hatalar

1. (1a) `PATCH` yanıtındaki `version` bayattı; yazma yanıttan önce flush edilir.
2. (1a) `iat` eksik token kabul ediliyordu (Spring `exp-1 sn` atıyor); imza çözümünde zorunlu kılındı.
3. (1b) PMD döngüsel karmaşıklık kapısı üç yerde aşıldı (`GifSanitizer.run`, `ImageIoInspector.detect`, sınıf toplamı); kuralı
   bastırmak yerine kod parçalandı (`ImageTransforms` ayrıldı, `blocks`/`screenDescriptor` çıkarıldı).

## Açık işler

- Auth talepleri: [REQ-01](../integration/auth/auth-service-talepleri/auth-requests-01.md) (hedef açma),
  [REQ-02](../integration/auth/auth-service-talepleri/auth-requests-02.md) (mevcut hesaplar). REQ-03 kapandı.
- `pre-push` resmî kapısı, Docker imajı/volume doğrulaması ve Sonar kullanıcı onayıyla.
- Faz 2: açık profil, gizlilik, arama, özetler (misafir kuralları dahil).
