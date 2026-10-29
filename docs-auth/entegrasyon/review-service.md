# REVIEW-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Hamza · **Kod durumu:** Silme ucu ve JWKS ayarı var, iş mantığı iskelet · **Güncelleme:** 2026-10-29

## ÖZET

Review'ın Auth ile bağı: kullanıcı token'ı, roller (moderasyon silme) ve hesap silme. Silme ucu `docs/api.md`'ye göre yazılı. İki açık: inceleme metni silme kararı ve Admin → Review çağrısının sözleşmesi.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT, `sub`, `roles`: `README.md` bölüm 1. Düzenleme ve silme `review.userId == sub`. Moderasyon silme `MODERATOR` veya `ADMIN`
- Silme ucu sözleşmesi: `README.md` bölüm 2

## SENİN YAPACAKLARIN

- [ ] Karar: kullanıcı silinince inceleme **metni silinir mi, yazar bağı mı koparılır** (anonim bırakılır mı), puan ortalaması korunur mu. Auth tarafı "silinen kullanıcının kaydı tutulmaz" çizgisinde: yazar kimliği kalmamalı
- [ ] REV-07 ve REV-15 (rapor ve moderasyon): Admin → Review çağrısı için sözleşme **yok** (Auth'ta bu çift tanımlı değil). İhtiyacı kendi klasörüne `talep-NN` olarak yaz, ben izin listesine eklerim
- [ ] `main`'deki `contextLoads` testi `AUTH_SERVICE_URL` olmadan kırık, `chore/security` (#16) çözüyor

## AUTH'TA BEKLEYEN

- Admin → Review servis kimlikli çağrı: çift tanımsız (karar bekliyor)
- Yazar adı ve görünen ad: JWT'de yok, User Service yazılınca oradan (`UserRegistered` olayı Auth'ta hazır, 2026-10-29; hedef listesi varsayılan boş, User'ın alıcı ucu yazılınca eklenir)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/review-service/`
