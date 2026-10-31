# Mail Service çalışma kayıtları

Sorumlu: Kerem. Servis: `mail-service` (`mail_db`, migration aralığı V81–V90, Gateway `/api/v1/mail/**`).
Görev kimlikleri: `LTZ-KER-MAIL-01..03` (MAIL-01..16). Tek API sözleşmesi [docs/api.md](../docs/api.md) `## MAIL` başlığıdır.

**Yeni oturumda önce [MEMORY.md](MEMORY.md) oku.**

## Durum

| Faz | Görevler | Durum |
| --- | --- | --- |
| LTZ-KER-MAIL-01 — SMTP, kalıcı kuyruk, güvenilir teslim | MAIL-10..16 | **KOD + TEST TAMAM** (44 test PASS, satır coverage %81,7); resmi coverage kapısı ortam sorunuyla doğrulanamadı ([plan](progress/LTZ-KER-MAIL-01.md), [MEMORY §10](MEMORY.md)) |
| LTZ-KER-MAIL-02 — Hesap e-postaları ve TR/EN şablonlar | MAIL-01..05, 08, 09 | **KOD + TEST TAMAM** (10 tür × TR/EN); `WELCOME` / `SUSPICIOUS_LOGIN` şablonu yok ([plan](progress/LTZ-KER-MAIL-02.md)) |
| LTZ-KER-MAIL-03 — Etkinlik ve bildirim e-postaları | MAIL-06, 07 | **ERTELENDİ**: çağıran servis yok ([plan](progress/LTZ-KER-MAIL-03.md)) |

## Dizin

- [MEMORY.md](MEMORY.md) — bağlam özeti, kurallar, boşluklar (clear sonrası başlangıç noktası)
- [decisions/kararlar.md](decisions/kararlar.md) — açık sorular ve önerilen kararlar
- [integration/README.md](integration/README.md) — kimden ne bekleniyor, önerilen sözleşmeler, gönderilecek mesajlar
- [progress/](progress/) — faz planları ve ilerleme

## Servis sınırı (kısa)

**Mail'e yalnız Auth istek atar; Mail Auth'un portlarına uyar** ([kararlar](decisions/kararlar.md)). Auth kod/token üretir, Mail yalnız teslim eder. Mail uygulama içi bildirim üretmez (notification) ve tercih tutmaz.
Mail kimseyi senkron çağırmaz (varsayılan); gelen istek kuyruğa yazılıp `202` dönülür, SMTP ana akışı bloke etmez.
