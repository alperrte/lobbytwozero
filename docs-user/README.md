# User Service çalışma kayıtları

Sorumlu: Kerem. Servis: `user-service` (`user_db`, migration aralığı V71–V80, Gateway `/api/v1/user/**`).
Görev kimlikleri: `LTZ-KER-USER-01..04` (USER-01..13). Tek API sözleşmesi [docs/api.md](../docs/api.md) `## USER` başlığıdır.

## Durum

| Faz | Görevler | Durum |
| --- | --- | --- |
| LTZ-KER-USER-01 — Oyuncu profili + medya | USER-01..04 | **Tamamlandı (yerel doğrulama):** 1a profil, Auth bağları, silme, olay girişi; 1b avatar/kapak medyası. Docker imajı ve gerçek Auth doğrulaması NOT VERIFIED |
| LTZ-KER-USER-02 — Açık profil, gizlilik, arama, özet | USER-05, 06, 11, 12, 13 | **User tarafı tamamlandı (yerel doğrulama).** Çağıran servisler ve Auth kapsamı PENDING (T3, T6, T7, T8) |
| LTZ-KER-USER-03 — Steam bağlantısı | USER-07, 08 | Bekliyor (Auth tarafı talebine bağlı) |
| LTZ-KER-USER-04 — Onboarding ve tercihler | USER-09, 10 | **Kod tamamlandı, testler sona ertelendi** ([ilerleme](progress/LTZ-KER-USER-04.md)) |

Önceki kararlar ve fazlama: [kararlar](decisions/kararlar.md). Gün gün kayıt: [progress/](progress/).

## Dizin

- [decisions/kararlar.md](decisions/kararlar.md) — proje sahibi kararları ve ertelenen/kapsam dışı maddeler
- [progress/LTZ-KER-USER-01.md](progress/LTZ-KER-USER-01.md) — faz 1 ilerleme ve test kanıtı
- [progress/LTZ-KER-USER-02.md](progress/LTZ-KER-USER-02.md) — faz 2 ilerleme ve test kanıtı
- [progress/LTZ-KER-USER-04.md](progress/LTZ-KER-USER-04.md) — faz 4 ilerleme ve sonda yazılacak test listesi
- [integration/services/](integration/services/) — Social, Library/Review, Hardware'den beklenenler
- [integration/auth/auth-service-talepleri/](integration/auth/auth-service-talepleri/) — Auth'a yazılı talepler; Auth cevapları
  `docs-auth/verilen-cevaplar/user-service/` altında (REQ-01..04 cevaplandı, 2026-10-30; Steam talebi bekliyor)

## Servis sınırı (kısa)

Auth kimlik kaynağıdır (parola, token, kullanıcı adı, hesap durumu). User profil sahibidir ve diğer servislere
**okuma sözleşmeleriyle** veri sağlar; hiçbir servisi senkron çağırmaz (döngü yok), dış veri yalnız olayla gelir.
