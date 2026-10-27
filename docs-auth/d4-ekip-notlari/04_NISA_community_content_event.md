# Nisa — Community, Content, Event: Hesap Silme İç Ucu

> Tarih: 2026-10-24 · Kimden: Kerem (Auth) · Önce [01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md)'yi
> oku; bu dosya yalnız sana özel kısım.

## Özet

Üç servisin üçü de silme hedefi ve üçünde de uç henüz yok. Auth her hesap silmede üçünü çağırıyor; biri onaylamazsa hesap
`DELETED` olmaz. İskelet aşamasındaysan bile ucu şimdi aç ve `204` dön; tablo ekledikçe silme mantığını büyüt (her yeni tabloda
"bu hesap silinince ne olur?" sorusu PR'ın parçası olsun).

| Servis | `service-audience` | Silinecek tipik veri (öneri) |
| --- | --- | --- |
| **community** | `community-service` | Üyelikler, roller, gönderi/yorumlar (silinir veya içerik kaldırılıp yazar anonim), tepkiler, takipler |
| **content** | `content-service` | Kullanıcının yüklediği içerik (UGC) ve dosyaları, taslaklar, beğeniler; dosya deposundaki nesneler de silinir |
| **event** | `event-service` | Katılım kayıtları, oluşturduğu etkinlikler (iptal + anonim düzenleyici veya silme, karar), hatırlatmalar |

## Dikkat

- **Content / dosyalar:** DB satırını silip depodaki dosyayı bırakmak kişisel veriyi bırakmaktır. Dosya silme 5 sn'ye sığmıyorsa
  rehber §3'teki gibi parça parça sil, bitmediyse `503` dön; `202` dönme.
- **Başkalarının verisi:** Başka bir kullanıcının gönderisi altındaki yorum, silinen hesabın verisidir (silinir/anonimleşir); ama
  başkasının gönderisi silinmez.
- **Anonim saklama** (ör. topluluk geçmişinde "silinmiş kullanıcı" yazısı) seçersen içerik metni ve yazar REF'i kalmamalı; seçimi
  servis belgene yaz ve bana haber ver.
- Social'ın `AccountErasureService`'i iyi bir örnek: tek işlemde ilişki satırları + outbox olayları siliniyor.
