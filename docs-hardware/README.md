# Hardware Service çalışma kayıtları

Sorumlu: Alper. Aktif kapsam: **LTZ-ALP-HW-01 — Bilgisayar Profili Oluştur, Düzenle ve Sil**.

Tek durum otoritesi [kök master](../LTZ_HARDWARE_SERVICE.md), API sözleşmesi
[docs/api.md](../docs/api.md) dosyasıdır. PDA kapatma ve Git yazma işlemleri kullanıcıya aittir.

- [Aktif görev günlüğü](progress/LTZ-ALP-HW-01.md)
- [Açık kararlar](decisions/open-decisions.md)
- [Ertelenmiş gerçek Auth sözleşmesi](decisions/auth-integration.md)
- [Kalite kanıtları](testing/LTZ-ALP-HW-01-quality.md)
- [LTZ-ALP-HW-01 teslim raporu](reports/LTZ-ALP-HW-01-delivery.md)

Sonraki ana görevler: 02 katalog/arama/kampanya; 03 gizlilik/karşılaştırma/entegrasyon;
04 yorum/moderasyon; 05 izinli masaüstü sözleşmesi. Kullanıcı onayı olmadan başlanmaz.

2026-10-21: aktif görevde **7/7**, **READY_FOR_PDA — DEFERRED_AUTH**.
Hardware 58 test, %96,69 satır kapsamı; onaylı runner regresyonları 44 PASS.
Pre-push PASS; bütün backend 76 test; Sonar Security/Reliability/Maintainability A,
Quality Gate PASS, overall coverage %84,3, duplication %0. Gerçek Auth doğrulanmadı;
production-ready değildir. Ana task kutusu yalnız kullanıcı PDA kapanışını doğrulayınca işaretlenir.
