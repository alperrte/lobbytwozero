# LIB-14 — Öneri rızası ve RECOMMENDATION kararı

Sorumlu: Kerem. Tarih: 2026-11-09. Kaynak: Hamza'nın Library → User talebi; cevap:
[library-cevap-01](../integration/services/library-review-talepleri/library-cevap-01.md).
Durum: **User tarafı tamamlandı ve doğrulandı.** Library adaptörü ve uçtan uca Library denemesi bekliyor.

## Yapılanlar

| Başlık | Kayıt |
| --- | --- |
| Migration | `V76__create_user_consents.sql`: `user_consents` (`recommendation_granted`, `recommendation_changed_at`, `updated_at`, `version`; profil FK `ON DELETE CASCADE`) |
| Uç | `GET/PUT /api/v1/user/me/consents`: rızaların tamamı, `version` ile iyimser kilit (409), eksik/tanımsız alan 400, `no-store` |
| Karar | `POST /internal/v1/privacy/decisions` → `field=RECOMMENDATION`: izleyici = sahip + görünür profil + rıza; sahiplik kısayolu yok; `projection-enabled`'dan bağımsız |
| Karar düzeltmesi | Profili olmayan sahip için sahibi olmayan izleyiciye `allowed=false` (`setting` varsayılan kalır) |
| Yeni ENV | **Yok** |

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| `.\pre-push\pre-push.cmd user-service` | **Geçti**: 242 test, kapsam %95,53, Checkstyle/PMD yeni ihlal yok |
| Yeni testler | `ConsentsTest` 13 (varsayılan, sürüm, 400/401/409, sahip kısayolu yok, geri çekme, görünürlükten bağımsızlık, başka izleyici/misafir, profilsiz/kapalı hesap, toplu, profilsiz sahip `LIBRARY`) |
| Canlı yığın (Docker `ltz-dev`) | user-service yeniden kuruldu, V76 uygulandı; Gateway üzerinden gerçek Auth token'ıyla (çerez ve Bearer) varsayılan, verme, geri çekme, 409, 400, 401 geçti |
| Servis token'lı `RECOMMENDATION` kararı canlıda | **NOT VERIFIED** (Library adaptörü yok; istemci sırrı `.env.dev` içinde, okunmadı). Karar yolu entegrasyon testleriyle kapsanıyor |

## Sıradaki

- Hamza: HTTP adaptörü ve `LibrarySummaryChanged` yayıncısı; sonra ortak uçtan uca deneme.
- Frontend: Hesap ayarları sayfası (gizlilik, tercihler, rıza). Backend uçları hazır.
