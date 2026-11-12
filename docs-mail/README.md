# Mail Service çalışma kayıtları

Sorumlu: Kerem. Servis: `mail-service` (`mail_db`, migration aralığı V81–V90, Gateway `/api/v1/mail/**`).
Görev kimlikleri: `LTZ-KER-MAIL-01..03` (MAIL-01..16). Tek API sözleşmesi [docs/api.md](../docs/api.md) `## MAIL` başlığıdır.

**Yeni oturumda önce [MEMORY.md](MEMORY.md) oku.**

## Durum

| Faz | Görevler | Durum |
| --- | --- | --- |
| LTZ-KER-MAIL-01 — SMTP, kalıcı kuyruk, güvenilir teslim | MAIL-10..16 | **KOD + TEST TAMAM** (58 test PASS, satır coverage %89,52; 2026-11-05; V83 ile 4 yeni güvenlik bildirimi türü); resmi kapı (`pre-push mail-service`) 2026-10-31'de PASS ([plan](progress/LTZ-KER-MAIL-01.md), [MEMORY §10](MEMORY.md)) |
| Rehberler | [entegrasyon-rehberi.md](entegrasyon-rehberi.md) (servisler için, Notification'dan farklı), [AJAN-OZETI.md](AJAN-OZETI.md), [sablon-rehberi.md](sablon-rehberi.md) | **HAZIR** |
| LTZ-KER-MAIL-02 — Hesap e-postaları ve TR/EN şablonlar | MAIL-01..05, 08, 09 | **KOD + TEST TAMAM** (15 tür × TR/EN, koyu marka tasarımı, `WELCOME` dahil; [LTZ-KER-MAIL-04](progress/LTZ-KER-MAIL-04.md)); `SUSPICIOUS_LOGIN` şablonu yok ([plan](progress/LTZ-KER-MAIL-02.md)) |
| LTZ-KER-MAIL-03 — Etkinlik ve bildirim e-postaları | MAIL-06, 07 | **ERTELENDİ**: çağıran servis yok ([plan](progress/LTZ-KER-MAIL-03.md)) |

## Dizin

- [MEMORY.md](MEMORY.md) — bağlam özeti, kurallar, boşluklar (clear sonrası başlangıç noktası)
- [decisions/kararlar.md](decisions/kararlar.md) — açık sorular ve önerilen kararlar
- [integration/README.md](integration/README.md) — kimden ne bekleniyor, önerilen sözleşmeler, gönderilecek mesajlar
- [progress/](progress/) — faz planları ve ilerleme

## Servis sınırı (kısa)

**Mail'e yalnız Auth istek atar; Mail Auth'un portlarına uyar** ([kararlar](decisions/kararlar.md)). Auth kod/token üretir, Mail yalnız teslim eder. Mail uygulama içi bildirim üretmez (notification) ve tercih tutmaz.
Mail kimseyi senkron çağırmaz (varsayılan); gelen istek kuyruğa yazılıp `202` dönülür, SMTP ana akışı bloke etmez.
