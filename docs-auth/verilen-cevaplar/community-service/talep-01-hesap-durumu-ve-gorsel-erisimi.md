# TALEP 01 — GERÇEK JWT, HESAP DURUMU VE GÖRSEL ERİŞİMİ

**Kaynak:** `docs-community/LTZ-NIS-COM-01-Rapor.md` "Bilinen sınırlar" madde 1 ve 5 · **Kimden:** Nisa (community-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26
**Durum:** CEVAPLANDI — Community tarafında yapılacak iş var, Auth'ta ek geliştirme yok

---

## NEYDİ

Community'nin JWT doğrulayıcısı şu an geçici ve her token'ı reddediyor. Yani giriş yapmış kullanıcı gönderi yazamıyor. Nisa raporunda iki şeyi Auth sözleşmesine bağlamış.

## NE SORULDU

- Auth'un anahtarı, `sub` değeri ve rol bilgisi nasıl gelecek
- Banlanan ya da askıya alınan hesap gönderi atmasın diye ayrı bir kontrol gerekiyor mu
- Tarayıcının `<img>` etiketi Authorization başlığı gönderemiyor. Özel görseller nasıl gösterilecek

## BENİM CEVABIM

**1. Sözleşme hazır ve yazılı.** Anahtarlar JWKS ucundan alınır: `AUTH_SERVICE_URL` artı `/api/v1/auth/.well-known/jwks.json`. Yeni ayar eklemene gerek yok. Token'ın `sub` değeri hesap kimliği ve UUID. Roller `roles` içinde liste halinde ve öneksiz geliyor: PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN. Hesap durumu token'da YOK.

**2. Banlı ya da askıdaki hesap için ek kontrol yapma.** Auth bu hesapların oturumlarını anında kapatıyor ve eski token'ları 90 saniye içinde ölüyor. Senin servisin yeni bir şey yapmıyor. Gönderi atma yetkisini sadece token'ın geçerli olmasına bağla.

**3. Görseller için çerez yolu hazır.** Tarayıcı giriş yapınca Auth HttpOnly bir çerez bırakıyor. Görsel isteği aynı siteden Gateway'e gidince Gateway çerezi Bearer başlığına çeviriyor. Senin servisin yine Authorization başlığıyla doğruluyor. Yani ayrı bir imzalı bağlantı modeline gerek yok. Misafire açık görsellerde zaten kimlik gerekmiyor, senin kuralın doğru. Sadece kendi gizli gönderisinin görselinde çerez devreye giriyor. Çerez adı ve akış ayrıntısı `docs/EKIP_AUTH_GATEWAY_ENTEGRASYON_NOTLARI.md` içinde.

## DECODER'I GERÇEKLEŞTİRİRKEN İKİ ŞEYE DİKKAT

- `.jwt(Customizer.withDefaults())` token türünü, issuer'ı ve audience'ı denetlemez. Rehberdeki doğrulayıcıları ekle: tür `at+jwt`, issuer `ltz-auth`, audience içinde `ltz-api`, yalnız RS256
- Spring'in varsayılan yetki dönüştürücüsü `roles` değil `scope` okur. Rolleri `ROLE_` önekiyle `roles` claim'inden eşle. Rehber bölüm 3.4'te örnek kod var

## SENDEN NE BEKLİYORUM

- Gerçek doğrulayıcıyı rehbere göre bağla
- Kendi dalın Auth'un main'e girmesinden önceki bir tabana dayanıyor. Main'i dalına al
- Hesap silme ucu da yazılacak (hedef listesinde community var). Rehber: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`
