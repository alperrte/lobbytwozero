# LTZ-KER-USER-02 — Açık Profil, Alan Bazlı Gizlilik ve Oyuncu Arama

Sorumlu: Kerem. Görevler: USER-05, USER-06, USER-11, USER-12, USER-13. Tarih: 2026-10-30.
Durum: **User tarafı tamamlandı (yerel doğrulama).** Çağıran servisler ve Auth kapsamı PENDING (T3, T6, T7, T8).

## Yapılanlar

| Görev | Kayıt |
| --- | --- |
| USER-06 | `GET/PUT /api/v1/user/me/privacy`: dört alan (`friendsList`, `library`, `hardware`, `performance`) + misafir erişimi; varsayılanlar (K4); iyimser kilit |
| USER-05 | `GET /api/v1/user/profiles/{userId}` ve `/by-username/{username}`: misafire açık (K5), temel kart her zaman, ayrıntı ve bölümler kurala göre; engel/yasaklı/olmayan aynı `404` |
| USER-11 | `GET /api/v1/user/search?q=`: kullanıcı adı veya görünen ad öneki, yalnız `ACTIVE`, engelliler hariç, en çok 20, Türkçe `ı/I/İ/i` katlaması; misafir `401` |
| Toplu kart | `POST /api/v1/user/profiles/batch`: en çok 50 kimlik, ad + avatar, gizlenenler sessizce atlanır |
| USER-12 | Açık profilde `summary`: arkadaş, kütüphane, inceleme sayıları; `AVAILABLE/HIDDEN/UNAVAILABLE`, sayı uydurulmaz |
| USER-13 | `POST /internal/v1/privacy/decisions` (`privacy:read`, çağıran: social/hardware/library); fail-closed. Olay girişi: `FriendshipChanged`, `UserBlocked/Unblocked`, `LibrarySummaryChanged`, `ReviewSummaryChanged` |
| Tek kural | `PrivacyRules`: tüm uçlar aynı görünürlük kuralını kullanır |
| Migration | `V72__create_privacy_and_social_projection.sql`: `privacy_settings`, `social_friendships`, `social_blocks`, `profile_counters`, arama sütunu ve indeksleri |
| Hesap silme | Yeni tablolar kapsandı (gizlilik FK ile, yansıma iki yönde, sayaçlar) |
| Yeni ENV | **Yok** (`ltz.user.social.projection-enabled=false` dağıtım ayarı) |

## Doğrulama (yerel, 2026-10-30)

| Kontrol | Sonuç |
| --- | --- |
| `mvn -pl services/user-service verify` (JDK 25, Testcontainers `postgres:17-alpine`), art arda 2 koşu | **BUILD SUCCESS, 201 test PASS** (iki koşuda da) |
| Yeni testler | PrivacyRulesTest 21, PublicProfileControllerTest 12, PlayerSearchTest 9, PrivacyDecisionTest 8, ProjectionEventTest 8, PrivacyControllerTest 7, SocialProjectionTrustedTest 3; AccountErasureTest yeni tabloları kapsıyor |
| Satır kapsamı (JaCoCo) | **%95,68** |
| Checkstyle / PMD | Temiz |
| `lint-ratchet check` | **Yeşil** |
| `pre-push`, Sonar, Docker imajı | Çalıştırılmadı |
| Gerçek Auth / Social / Library / Review / Hardware ile uçtan uca | **NOT VERIFIED** (çağıranlar ve Auth kapsamı yok) |

## Testlerin bulduğu ve düzeltilen hatalar

1. **Türkçe büyük/küçük harf:** `ÇAĞRI`/`İ` ile yapılan arama eşleşmiyordu (yerelden bağımsız küçük harf `I`→`i`, `İ`→`i̇`).
   Arama anahtarına Türkçe i katlaması eklendi (`ı`, `i̇` → `i`); V72 başlangıç doldurması da aynı kurala çekildi. Testler makinenin
   Türkçe yerelinde ortaya çıktı.
2. **PMD karmaşıklık:** yeni olay türleriyle `EventIngestService.ingest` sınırı aştı; dağıtım ayrı metoda alındı.
3. **Yeni gizlilik satırı:** varsayılan nesnenin sürümü 0 olduğu için Spring Data onu "var olan kayıt" sanıyordu; kayıt için
   sürümü boş ayrı fabrika (`PrivacySettings.newRow`) eklendi.

## Açık işler

- Talepler: [USER-AUTH-REQ-04](../integration/auth/auth-service-talepleri/auth-requests-04.md) (T3),
  [USER-SVC-REQ-01](../integration/services/requests-01-social-library-review-hardware.md) (T6, T7, T8).
- Social başlangıç aktarımı bitmeden `ltz.user.social.projection-enabled=true` yapılmamalı.
- Arama için kullanıcı başına oran sınırı yok (izlenecek).
