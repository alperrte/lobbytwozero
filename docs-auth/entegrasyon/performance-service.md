# PERFORMANCE-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Alper · **Kod durumu:** İskelet · **Güncelleme:** 2026-10-29

## ÖZET

Henüz kod yok. Performance'ın Auth ile iki bağı olacak: kullanıcı token'ı ve hesap silme. Üçüncüsü, **aktif cihaz envanteri**: cihaz kimliği Auth'ta hazır (2026-10-29), cihazın aktif olup olmadığını söyleyen API Performance'ın işi ve onun uç adı ile yanıt biçimi henüz yazılmadı.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. Performance silme hedefleri listesinde (16 hedeften biri)
- Servis token mekanizması (gerekirse): `README.md` bölüm 3

## SENİN YAPACAKLARIN

- [ ] JWT doğrulayıcı (JWKS) ve `DELETE /internal/v1/accounts/{accountId}` ucu. Silme ucu yazılmadıkça Auth'ta Performance görevi `PENDING` kalır ve hesap silme tamamlanmaz
- [ ] PERF-01: aktif cihaz API'si. Önerilen sorumluluk bölümü: **Auth cihazın kimliğini ve hangi hesaba bağlı olduğunu söyler, Performance cihazın aktif ve çalışıyor olduğunu söyler.** Ayrıntı: `cihaz-kimligi-tasarimi.md`
- [ ] Hardware ile aktif cihaz politikası: Hardware bu kararı Performance'a soruyor, uç adı ve yanıt biçimi yazılı değil

## AUTH'TA BEKLEYEN

- Cihaz kimliği (B yolu): **Auth'ta uygulandı (2026-10-29)**, ayrıntı `hardware-service.md` ve `docs/api.md` "Masaüstü cihaz kimliği"
- Auth'ta Performance için servis istemcisi yok. İhtiyaç çıkarsa kendi klasörüne talep yaz (`README.md` bölüm 3)
- Auth cihazı iptal edince Performance'a olay gidecek mi: önerilen ikinci aşama, şimdilik gerekmiyor

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/performance-service/`, `docs-auth/entegrasyon/cihaz-kimligi-tasarimi.md`
