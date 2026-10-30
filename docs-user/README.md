# User Service çalışma kayıtları

Sorumlu: Kerem. Servis: `user-service` (`user_db`, migration aralığı V71–V80, Gateway `/api/v1/user/**`).
Görev kimlikleri: `LTZ-KER-USER-01..04` (USER-01..13). Tek API sözleşmesi [docs/api.md](../docs/api.md) `## USER` başlığıdır.

## Durum

| Faz | Görevler | Durum |
| --- | --- | --- |
| LTZ-KER-USER-01 — Oyuncu profili + medya | USER-01..04 | **1a tamamlandı (Auth bağları, profil, silme, olay girişi)**; 1b (avatar/kapak medyası) bekliyor |
| LTZ-KER-USER-02 — Açık profil, gizlilik, arama, özet | USER-05, 06, 11, 12, 13 | Bekliyor |
| LTZ-KER-USER-03 — Steam bağlantısı | USER-07, 08 | Bekliyor (Auth tarafı talebine bağlı) |
| LTZ-KER-USER-04 — Onboarding ve tercihler | USER-09, 10 | Bekliyor |

Önceki kararlar ve fazlama: [kararlar](decisions/kararlar.md). Gün gün kayıt: [progress/](progress/).

## Dizin

- [decisions/kararlar.md](decisions/kararlar.md) — proje sahibi kararları ve ertelenen/kapsam dışı maddeler
- [progress/LTZ-KER-USER-01.md](progress/LTZ-KER-USER-01.md) — faz 1 ilerleme ve test kanıtı
- [integration/auth/auth-service-talepleri/](integration/auth/auth-service-talepleri/) — Auth'a yazılı talepler; Auth cevapları
  `docs-auth/verilen-cevaplar/user-service/` altına gelir

## Servis sınırı (kısa)

Auth kimlik kaynağıdır (parola, token, kullanıcı adı, hesap durumu). User profil sahibidir ve diğer servislere
**okuma sözleşmeleriyle** veri sağlar; hiçbir servisi senkron çağırmaz (döngü yok), dış veri yalnız olayla gelir.
