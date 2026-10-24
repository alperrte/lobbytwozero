# HW-AUTH-REQ-04 — DEBUG/TRACE altında OAuth HTTP form loglarının sınırı

Tarih: 2026-10-24. Gönderen: Alper / hardware-service. Hedef: Kerem / auth-service.
Öncelik: HIGH (Auth DEBUG/TRACE dağıtım ayarı açılmadan önce). Durum: OPEN / HARDENING REVIEW.

**Gerekçe/kanıt:** İlk tüm-backend pre-push koşusunda GoogleLoginTest ve SteamLoginTest'in
iki log gizliliği assertion'ı başarısız oldu. Spring DefaultRestClient DEBUG form yazımında
yalnız test için üretilmiş OAuth code/kimlik değerleri görüldü. Belgeye hiçbir değer kopyalanmadı.
Host araç ortamının genel DEBUG bayrağı kaldırılınca aynı kaynaklarla 22/22 test geçti.

**Mevcut/eksik sözleşme:** Auth loglarında sır/kişisel veri bulunmaması gerekir.
Normal INFO testi geçer; global framework DEBUG/TRACE için zorunlu logger sınırı
bu çalışmada Auth kaynakları değiştirilerek uygulanmadı. Yeni bir HTTP API eksikliği değildir.

**Beklenen güvenlik:** Kerem, HTTP client request/response form ve body loglarını
global DEBUG/TRACE altında da kapatan logger/transport politikasını belirlesin;
OAuth callback, code/state, credential ve PII için regresyonlar açık logger seviyelerinde çalışsın.
Gerçek loglarda bu durum yaşandı iddiası yoktur; saptama izole sentetik test koşusundadır.

**RBAC/HTTP/başarı-hata:** Auth endpoint/rol/yetki sözleşmesi değiştirilmez;
mevcut başarı/hata HTTP kodları aynıdır. Güvenli logger ayarı kimlik doğrulamayı veya
yetki hatasını başarılıya çevirmemeli; hassas HTTP gövdesi loglanmadan teşhis yapılabilmeli.

**Sahiplik/idempotency:** Auth logger/HTTP client politikası Kerem'indir; Hardware
Auth kaynağını değiştirmez. Tekrarlı isteklerde de sır sızmamalı; iş idempotency davranışı değişmez.

**Uyumluluk:** Kalite runner'ı yalnız genel host DEBUG bayrağını Maven test JVM'inden ayırır.
Açık Spring test logger/property ayarı, gerçek ENV ve çalışan servis log seviyesi değiştirilmez.
Bu izolasyon üretim DEBUG/TRACE güvenliği için bir düzeltme veya kabul kanıtı değildir.

**Kabul:** DEBUG/TRACE açıkken Google/Steam negatif/pozitif login testleri sır/PII loglamadan geçer;
INFO davranışı ve mevcut testler korunur. Kerem risk/politika kararını ve üretim ayarını kaydeder.

**Referans:** logs/hardware-auth-pre-push-full.log (ilk FAIL),
logs/hardware-auth-debug-isolation-diagnostic.log (aynı iki sınıf 22 PASS);
auth-integration-quality.md. Talep dışarı araçla gönderilmedi.
