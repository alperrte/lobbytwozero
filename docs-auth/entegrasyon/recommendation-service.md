# RECOMMENDATION-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Alper · **Kod durumu:** İskelet (yalnız `.gitkeep`) · **Güncelleme:** 2026-10-29

## ÖZET

Recommendation, Auth'tan özel bir şey beklemiyor. Görev belgesinde de Auth'a bağımlılık yazılı değil. Yazılınca iki şey gerekecek: kullanıcı token'ı doğrulama (kullanıcı verisine dokunuyorsa) ve hesap silme ucu.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı JWT ve roller: `README.md` bölüm 1
- Silme ucu sözleşmesi: `README.md` bölüm 2. Recommendation silme hedefleri listesinde

## SENİN YAPACAKLARIN

- [ ] Karar: Recommendation kullanıcı token'ı doğrulayacak mı? Kişiselleştirilmiş öneri kullanıcıya özelse evet (`sub` ile sahiplik)
- [ ] `DELETE /internal/v1/accounts/{accountId}` ucu: kişisel tablo yoksa `204` ile no-op yeter. Silme ucu yazılmadıkça hesap silme global olarak tamamlanmaz
- [ ] Donanım ve performans verisini Hardware'in owner batch ucundan alacaksan kullanıcı token'ını ileten akışı belirle (`docs/api.md` Hardware bölümü, `DEFERRED_CONTRACT`)

## AUTH'TA BEKLEYEN

- Yok. Auth'ta Recommendation için istemci kaydı yok, gerek de görünmüyor

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/recommendation-service/`
