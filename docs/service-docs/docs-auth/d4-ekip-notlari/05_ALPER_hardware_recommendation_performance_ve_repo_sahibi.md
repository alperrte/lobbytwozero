# Alper — Hardware, Recommendation, Performance + Repo Sahibi Kararları

> Tarih: 2026-10-24 · Kimden: Kerem (Auth) · Önce [01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md)'yi
> oku.

## 1. Senin servislerin

| Servis | `service-audience` | Silinecek tipik veri (öneri) |
| --- | --- | --- |
| **hardware** | `hardware-service` | Kullanıcının donanım profilleri ve parçaları |
| **recommendation** | `recommendation-service` | Kişisel öneri geçmişi, tercih/geri bildirim sinyalleri, önbellekteki kişisel sonuçlar |
| **performance** | `performance-service` | Kullanıcının gönderdiği performans ölçümleri (anonim istatistik olarak tutulacaksa hesap REF'i kalmaz) |

Üçünde de uç yok. İskelet olsa da uç açılıp `204` dönmeli; yoksa hiçbir hesap `DELETED` olamaz.

## 2. Gelecek Auth PR'ı (senin onayın gerekiyor)

`feature/auth-service` dalında main'e henüz girmemiş commit'ler var. Main'le birleştirme provası **çakışmasız**.

| Ne | Ortak alana etkisi |
| --- | --- |
| Auth + Gateway Sonar temizliği (`ltz-auth-gateway`: 0 bulgu, 0 hotspot, tüm notlar A, gate PASSED) | **Gateway'e dokunuyor** (davranış değişikliği yok: kullanılmayan catch değişkeni `_`, test düzenlemeleri) |
| D4 servis token'ı + gerçek silme adaptörü, 16 silme hedefi | Yalnız `auth-service`. Compose/ENV değişmedi (`*_SERVICE_URL` zaten vardı) |
| Belgeler | `docs/api.md` (yalnız AUTH bölümü), `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md`, `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md`, `docs-auth/**` |
| `.gitignore` | `docs-auth/` artık izleniyor (auth belgeleri ekibe açık) |

Testler: auth 290, gateway 18, hepsi yeşil. `pre-push full dev` bu commit'ler için henüz koşulmadı; PR'dan önce koşulup sonucu PR'a
yazılacak.

## 3. Senden beklenen kararlar

| # | Konu | Neden | Önerim |
| --- | --- | --- | --- |
| 1 | **notification ve admin silme ucunun sahibi** | İkisi ortak servis ve silme hedefi; sahip yoksa uç yazılmaz, hesap silme hiç tamamlanmaz | Bir sahip ata; admin için denetim kaydının anonimleştirme kuralını Auth'la birlikte yazarız |
| 2 | **İskelet servislerde %80 kapsam kuralı** | `pre-push backend` modu modül başına ≥%80 istiyor; 15 iskelet servis %33,33'te FAIL. `full` modu ağırlıklı toplamla geçiyor | Kural senin (2576f73). Seçenek: iskeletler için minimal test, ya da kodu olmayan modülü kapıdan muaf tutmak. Auth tarafı değiştirmedi |
| 3 | **Prod'da iç ağ şifrelemesi (B18)** | Auth → servis silme çağrıları iç ağda düz `http`; token kısa ömürlü ve tek hesaba bağlı ama ağı dinleyen görür | Prod'da TLS/mTLS veya en az ağ politikası ile yalnız Auth'un `/internal/**`'a erişebilmesi. Dev'de gerek yok |
| 4 | **Sonar için ortak proje anahtarı** | Auth, ana `ltz-backend` projesini ezmemek için `ltz-auth-gateway` anahtarıyla tarandı | Ekipçe tek anahtar mı, servis başına mı, karar ver |
