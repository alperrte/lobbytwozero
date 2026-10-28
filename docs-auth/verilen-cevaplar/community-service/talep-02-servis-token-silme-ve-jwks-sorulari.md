# TALEP 02 — SERVİS TOKEN'I, SİLME, DURUM OLAYI VE JWKS SORULARI

**Kimden:** Nisa (community-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-28 (güncelleme: aynı gün, tablo geldikten sonra)
**Durum:** CEVAPLANDI — Auth tarafı kodlandı, main'e PR ile girer. Community'de iş var (token'ı alıp kullanmak)

---

## NEYDİ

Nisa 6 soru göndermiş, sonra "kim → hangi servise → hangi kapsamla → neden" tablosunu yollamış (4 satır + 1 ileride).

## ÖNCE ŞUNU BİLİN

Aşağıdaki yeni şeyler `feature/auth-service` dalında hazır ve test edildi ama **main'e ve dev'e henüz girmedi**. PR açılıp birleşince devreye girer. Tarih vermiyorum çünkü Alper onayı gerekiyor

## TABLO GELDİ, ŞÖYLE YAPILDI

**Tablodaki 1 ve 2. satırlar (admin-service ve social-service → community-service) ve 3 ve 4. satırlar (community-service → admin-service ve notification-service): hepsi kodlandı.** Her biri ayrı bir tanım, listede olmayan çağrı çalışmaz. Kimseye varsayılan imtiyaz yok

- **İstemci kimliği servis adıyla aynı** oldu: `admin-service`, `social-service`, `community-service`. Böylece token'daki `sub` senin beklediğin gibi `svc:admin-service`, `svc:social-service`, `svc:community-service` çıkıyor. Eskiden `admin` idi, değiştirdim, henüz kimse kullanmıyordu
- **Token nasıl alınır:** `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` gövde `{ "clientId": "community-service", "clientSecret": "...", "audience": "admin-service" }`. Dönen token `aud=admin-service`, `scope=events:ingest`, 60 sn. Her hedef için ayrı token alırsın
- **Gizli anahtar:** her istemcinin kendi gizli anahtarı var, ortam değişkeninden gelir (`AUTH_COMMUNITY_SERVICE_CLIENT_SECRET`, `AUTH_SOCIAL_SERVICE_CLIENT_SECRET`, `AUTH_ADMIN_SERVICE_CLIENT_SECRET`). Boşsa o istemci kapalı. Koda ve repoya yazılmaz. Yerelde `node ops/generate-auth-dev-keys.mjs` üretir
- **İzinsiz hedef:** örneğin social'ın notification için token istemesi `403 SERVICE_AUDIENCE_FORBIDDEN`, yanlış gizli anahtar `401 INVALID_CLIENT`. Auth'a (`auth-service`) bile token isteyemez, o yalnız admin-service'te
- **Bu token Auth'ta geçmez:** hedefe bağlı token Auth'un iç uçlarında reddedilir (401)
- **Alıcı tarafın işi:** `aud` kendi adın mı, `typ=svc+jwt`, `scope=events:ingest`, olay türü ↔ `sub` eşleşmesi. Dediğin gibi `ModerationActionTaken` yalnız `svc:admin-service` ile kabul edilmeli. Sende bu zaten var
- **5. satır** (Auth → community, `AccountStatusChanged`): olay Auth tarafında hazır (2026-10-29, outbox); hedef listesi varsayılan boş, Community alıcı ucunu yazınca eklenir

## ALTI SORUNUN CEVABI

**1. Servis token'ı başka kapsamlara ve çağıranlara açılacak mı, `events:ingest` uygun mu:** Evet, yukarıdaki tabloyla. `events:ingest` adı uygun

**2. Community admin ve notification'a giderken token'ı nasıl alır:** Auth'un token ucundan, kendi istemci kimliğiyle (yukarıda anlatıldı)

**3. Anonimleştirme, sabit silinmiş hesap kimliği, `[removed]`, rapor saklama:** Karar bizde: **silinen kullanıcının kaydını tutmuyoruz.** Auth'ta tüm servisler silmeyi onaylayınca hesap kaydı, güvenlik olayları, silme görevleri ve talebi tamamen siliniyor. Community'de de aynı çizgiyi öneririm: kullanıcı silinince içerik raporlarını da silmek ya da kimlik bağını tamamen koparıp anonim bırakmak. Sabit kimlik ve `[removed]` Auth sözleşmesiyle çelişmiyor, ama kullanıcı silme tamamlandıktan sonra sana bağlı bir kimlik kalmaması gerekiyor

**4. Dev'de birlikte silip CONFIRMED görebilir miyiz:** Görebiliriz. Silme 14 gün geri alma süresiyle başlıyor, dev'de bunu `AUTH_DELETION_GRACE_PERIOD=PT0S` ile sıfırlayacağız (sadece dev, canlıda 14 gün). Şartlar: yeni Auth dev'e çıkmış olmalı, senin community container'ın güncel imajla çalışmalı. Dev env'ini ekip ekleyecek. Tüm 16 hedef onaylamadan Auth kaydı silinmez, o yüzden göreceğin şey "community görevi CONFIRMED"

**5. `AccountStatusChanged` ne zaman, askıdaki hesabın içeriği gizlensin mi:** Olay Auth tarafında hazır (2026-10-29, transactional outbox), ama yalnız yönetici durum değişikliğinde çıkar (silme isteği ve iptal olay üretmez) ve hedef listesi varsayılan boştur; Community alıcı ucunu yazınca `community` hedef olarak eklenir. Olay gelene kadar durum değişince eski token'lar en fazla 90 saniyede ölüyor (60 sn token + 30 sn saat farkı). İçeriği gizleme Community'nin ürün kararı

**6. JWKS önbellek ve eski anahtar süresi:** Anahtar değişirken eski açık anahtarı JWKS'te bir süre daha yayınlayabiliyoruz (`AUTH_JWT_ADDITIONAL_PUBLIC_KEYS`). Süre en az 90 saniye olmalı, ben pratikte 5 dakikadan fazla tutacağım. Sen kendi önbelleğini en fazla 5 dakika yap ve tanımadığın `kid` görürsen bir kez yeniden çek. Game servisi böyle yapıyor

## SENDEN NE BEKLİYORUM

- Gerçek JWT doğrulayıcıyı bağla (talep 01) ve hesap silme ucunu yaz
- Kendi `POST /internal/events` ucunda `aud`, `typ`, `scope` ve olay türü ↔ `sub` kontrolünü tut
- Main'e girince `node ops/generate-auth-dev-keys.mjs` çalıştır, kendi gizli anahtarını al
- Dev testi için community imajını güncel tut
