# HW-AUTH-REQ-05 — Ortak DEV D4 kabul kanıtının takibi

Tarih: 2026-10-29. Gönderen: Alper / hardware-service. Hedef: Kerem / auth-service.
Durum: OPEN / ORTAK DEV NOT VERIFIED. Öncelik: ortak dağıtım kabulünden önce.
Önceki kayıt: [REQ-03](../entegrasyon-v1/auth-service-talepleri/auth-requests-03.md).
Bu belge yeni Auth endpoint'i veya Auth üretim kodu değişikliği talep etmez.

## Kapatılan hazırlık

Hardware E2E betiği Auth fixture sürecine `AUTH_DELETION_GRACE_PERIOD=PT0S` ve
`JWT_ACCESS_TTL_SECONDS=60` verir; güncel üç JAR'ı kaynak tazeliğiyle kontrol eder.
İzole kabul sonucu [06 numaralı kalite kaydında](../auth-integration-06.md) tutulur.
Token/anahtar/parola belgeye veya mesaja taşınmaz.

## Beklenen ortak kabul

Yalnız ortak sentetik hesapla ve güncel imajlarla Auth→Hardware gerçek iç DELETE çağrısı,
kalıcı `hardware=CONFIRMED`, yerel kişisel veri temizliği, tekrar 204 ve eski JWT ile
yeniden yazma reddi birlikte kaydedilmelidir. Gerçek kullanıcı hesabı veya veri reseti yetkisi verilmez.

Repoda 16 hedeften beşinin silme ucu vardır; kalan 11 eksik uç ilgili sahiplerin işidir.
Ortak denemede CONFIRMED/PENDING sayısı gerçekten çalışan katılımcılardan ölçülmeli;
eksik ACK atlanmamalı, hedef listesi daraltılmamalı, sahte 204 verilmemelidir.
Yalnız Hardware'ın başlatıldığı izole fixture'da 15 PENDING beklenir.

## Mevcut güvenlik ve hata sözleşmesi

RS256/JWKS/kid, `typ=svc+jwt`, tek `aud=hardware-service`, tam
`sub=svc:auth-service`, `scope=account:erase`, `account_id` yol hesabıyla aynı olmalıdır.
Auth koordinatör, Hardware yalnız kendi DB'sinin katılımcısıdır; iç uç Gateway'e açılmaz.
Yalnız tamamlanan yerel temizlik 204; tekrarlı komut idempotent 204'tür.
401/403/5xx/timeout ACK değildir. Kısmi temizlik 503 + Retry-After ile tekrar edilir;
silinen kişisel veri geri yüklenmez. Yeni HTTP/JSON sözleşmesi önerilmiyor.

Kerem'in talep 01/02/04 cevapları ve Hardware karşılıkları
[05 numaralı karşılaştırmada](../auth-integration-05.md) bulunur.
PERF-01 aktif cihaz/sahiplik bağımlılığı Auth'a devredilmedi; Alper'in ayrı işidir.
Bu kayıt araçla kimseye gönderilmedi.
