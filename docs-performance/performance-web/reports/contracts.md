# Performance Web — doğrulanmış sözleşme matrisi

| İhtiyaç | Kaynak | Gerçek durum | Kimlik/sahiplik | Strateji |
| --- | --- | --- | --- | --- |
| Oturum | Auth `/api/v1/auth/me`, `/session/refresh`, `/csrf` | Mevcut frontend `SessionProvider`, `apiRequest` | HttpOnly cookie, Gateway CookieBearerFilter, CSRF/Origin | Var olan akış; yeni Auth talebi yok |
| Cihazlar | Performance `GET /devices` | Controller + DeviceLinkService | JWT aktör; Auth cihaz listesiyle doğrulama; hata 503 | Kendi referansları; boş/hata ayrı |
| Donanım | Hardware `GET /profiles`, `/profiles/{id}` | Gerçek API ve frontend consumer | JWT, owner/visibility | Mevcut DTO, çoklu profil, sayfalama |
| Oturum/rapor | Performance `GET /sessions`, `/{id}/report` | Gerçek controller/DTO | Owner, başkası için 404 | Sınırlı sayfa, kullanıcı seçimiyle detay |
| Ölçüm | Performance `GET /sessions/{id}/series` | RAW/BUCKET, maxPoints 10..1000 | Owner | Tek metrik; 100 nokta, zaman penceresi; tablo |
| Gizlilik/silme | Performance `/sync-consent`, `DELETE /sessions/{id}` | Gerçek API | Owner, JWT, Gateway CSRF | Açık onay, kesinleşmiş sonuç, yeniden okuma |
| Oyun/öneri | Game `/games/{gameId}`; Recommendation | Game mevcut, Recommendation iş API'si yok | Recommendation sözleşmesi yok | PWEB-08/09 BLOCKED; sonuç uydurulmaz |

Kanıt: `docs/api.md` AUTH/HARDWARE/PERFORMANCE bölümleri; Performance controller/response sınıfları; `backend/services/api-gateway/src/main/java/com/ltz/gateway/security/CookieBearerFilter.java`.
Frontend → hook → feature service → ortak `apiRequest` → Gateway; servis portuna tarayıcı erişimi yok.

## TASK 2 consumer durumu

Panel: `/tr/performans/panel`, `/en/performance/dashboard`.
Hardware gerçek profile/detail/privacy uçları yeni public feature export'ları `HardwareSnapshot`/`HardwareProfileReference` üzerinden tüketilir; paralel DTO/client yazılmadı. Oturum listesi 20, grafik 100 noktayla sınırlıdır. Rapor/grafik yalnız seçilen oturum için alınır. Profile/oturum seçimi veya hesap değişiminde önceki sonuçlar gizlenir.
Auth consumer'da yeni kabiliyet gerekmiyor; yeni Auth request yok. API sözleşmesi/endpoint/backend değişikliği yok. Test fixture'ları canlı servis kabulü değildir; canlı tarayıcı entegrasyonu NOT VERIFIED.
