# GAME-17 — Oyun detaylarını zenginleştirme: ekip özeti

**Tarih:** 21 Ekim 2026 · **Sorumlu:** Hamza · **Servis:** game-service

## Ne yapmak istiyoruz?

Steam'den aldığımız hafif katalog, oyunları kimlik ve ad bilgileriyle indeksliyor; kapsamlı oyun detaylarını sağlamıyor. GAME-17'nin amacı, eksik detayları izinli bir sağlayıcıdan alıp yerel veritabanında saklamak. Böylece kullanıcı, dış servis yavaşken veya erişilemiyorken de mevcut oyun bilgilerini görebilecek.

**Steam katalog indeksimiz korunacak.** Alternatif kaynak yalnız detayları tamamlayacak; LTZ'nin kanonik `gameId` yapısı değişmeyecek. Oyunlar yalnız ad benzerliğiyle birleştirilmeyecek.

## Şu ana kadar ne hazırlandı?

- Sağlayıcıdan bağımsız detay sözleşmesi ve iç uygulama yenileme servisi.
- Açıklama, çıkış tarihi, geliştirici/yayıncı ve güvenilir oyun türü bilgisini işleyebilen altyapı.
- Aynı oyun için eşzamanlı gereksiz sağlayıcı çağrılarını önleyen DB kilitleri.
- 24 saatlik önbellek süresi, güncellik bilgisi ve eski veriyi `STALE` olarak sunma.
- Hatalarda mevcut veriyi koruma; sınırlı yeniden deneme, artan bekleme ve oran sınırı yönetimi.
- V36 veritabanı migration'ı ve otomatik testler. Faz 2 doğrulamasında 83 Game Service testi ve SonarQube kalite kapısı geçti.

**Gerçek detay sağlayıcısı henüz bağlanmadı.** Mevcut Steam adaptörü detay desteği varmış gibi davranmıyor. Public detay GET isteği yalnız yerel veriyi okuyor; kullanıcı isteğinden otomatik yenilemeye uzanan akış henüz tamamlanmadı. Testlerdeki başarı, canlı sağlayıcı entegrasyonu anlamına gelmiyor.

## Hangi sağlayıcıyı öneriyoruz?

- **Steam:** Katalog kaynağı olarak devam. İncelenen resmî API'lerde ihtiyacımızı karşılayan kapsamlı ve kullanım koşulları doğrulanmış bir detay kaynağı bulunamadı. Belgelenmemiş mağaza uç noktalarına veya scraping'e dayanılmadı. [Resmî katalog API'si](https://partner.steamgames.com/doc/webapi/IStoreService).
- **IGDB — birinci aday:** Açıklama, tür, platform, şirket ve medya gibi alanları; Steam dış kimliğiyle eşleme ve yerel saklama desteği nedeniyle öne çıkıyor. Her alanın her oyunda dolu olması garanti değil. Ticari kullanım/ortaklık koşulları ve görünür atıf gereksinimi netleştirilmeli. Henüz kesin seçim veya entegrasyon yok. [Resmî API ve koşullar](https://api-docs.igdb.com/).
- **RAWG — ikinci aday:** Zengin metadata sunuyor; ancak ücretsiz ticari kullanım açıklamaları sayfaları arasında çelişiyor. Saklama ve kullanım izinleri netleşmeden ana kaynak olarak önerilmiyor. [Planlar](https://rawg.io/apidocs) · [API koşulları](https://rawg.io/tos_api).
- **Diğerleri:** FreeToGame yalnız ücretsiz oyun kapsamıyla sınırlı; Giant Bomb'ın mevcut API erişimi belirsiz. Genel Steam kataloğu için öncelikli aday değiller.

## Ekipçe hangi kararı vermeliyiz?

Öneri: **Steam indeksini koruyup IGDB'yi yalnız detay zenginleştirme için değerlendirmek.** Önce projenin ticari kullanım durumu, lisans/atıf koşulları ve Steam AppID eşleme yöntemi netleştirilmeli; ardından onayla küçük bir oyun örnekleminde pilot yapılmalı.

**GAME-17 açık:** Altyapı hazır; gerçek entegrasyon, canlı eşleme ve alan doğrulaması için ortak karar ve onay bekleniyor. Uygun ve sürdürülebilir kaynak kesinleşmezse görev “veri kaynağı kararı bekleniyor” durumunda kalacak. Faz 3 başlatılmadı.
