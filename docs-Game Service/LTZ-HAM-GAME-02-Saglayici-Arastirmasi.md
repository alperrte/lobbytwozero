# Faz 2 — Detay sağlayıcısı araştırması ve karar önerisi

Tarih: 2026-10-21 · Sorumlu: Hamza · Durum: Entegrasyon onayı bekleniyor

## Sonuç

**Önerilen aday IGDB'dir; kesin entegrasyon veya ticari kullanım onayı verilmiş değildir.** Steam hafif katalog indeksi korunacak, alternatif kaynak yalnız detay zenginleştirmede kullanılacaktır. Aşağıdaki karşılaştırma belge incelemesidir; kimlik bilgili canlı IGDB/RAWG isteği, hesap açma, sağlayıcıya mesaj gönderme veya abonelik satın alma yapılmadı.

## Steam — Öncelikli inceleme

`IStoreService/GetAppList` katalog devamı ve değişiklik watermark'ı sunar. Açıklama diline göre filtre, açıklama metninin döndüğü anlamına gelmez. Belgelenmiş bu sözleşmede kapsamlı mağaza detayı yoktur. [Resmî IStoreService](https://partner.steamgames.com/doc/webapi/IStoreService).

`ISteamApps` yayıncı işlemleri ilgili yayıncının uygulamalarıyla sınırlıdır. Eski GetAppList yerine IStoreService önerilir; yayıncı listesi genel katalog detay kaynağı değildir. [Resmî ISteamApps](https://partner.steamgames.com/doc/webapi/ISteamApps?l=english). `ISteamUserStats` istatistik/başarım odaklıdır; aranan açıklama, şirket, platform ve medya paketinin karşılığı değildir. [Resmî istatistik API'si](https://partner.steamgames.com/doc/webapi/ISteamUserStats).

İncelenen resmî referansta tüm mağaza metadatasını kapsayan, erişim ve yeniden saklama koşulları doğrulanmış genel bir detay API'si bulunamadı. Bu, Steam'de hiçbir başka yöntem olmadığı iddiası değildir. `store.steampowered.com/api/appdetails` için gerekli resmî sözleşme ve yeniden kullanım izni doğrulanamadığından kalıcı bağımlılık önerilmedi. Scraping yapılmadı.

AppID katalogda doğrudan kimliktir. Web API ücretsizdir; günlük sınır 100.000 çağrıdır. LTZ'nin daha ihtiyatlı bütçesi bunun altında kalır. Kullanım şartları anahtarı gizli tutmayı, uygulama üzerinden son kullanıcıya sunmayı ve Valve ortaklığı izlenimi vermemeyi gerektirir; burada tüm mağaza metin/görselleri için sınırsız ticari lisans veya kalıcı saklama hakkı çıkarılmadı. Kaynak görünürlüğü korunmalı, Steam marka kuralları ayrıca uygulanmalıdır. [Steam Web API koşulları](https://steamcommunity.com/dev/apiterms).

**Karar:** Katalog için devam; kapsamlı GAME-17 detayı için yeterlilik doğrulanmadı. Yeni canlı istek gerekmedi.

## IGDB — Birinci aday

- Alanlar: özet/hikâye, çıkış tarihi, tür, platform, şirket ilişkileri, kapak, ekran görüntüsü, video ve dil desteği. Her oyunda doluluk veya PC sistem gereksinimi garantisi yoktur.
- Steam eşlemesi: `external_games.uid` ile kaynak kimliği; güncel `external_game_source` kullanılır. Eski `category=1` sabiti yeni tasarıma temel alınmamalıdır.
- Erişim: Twitch uygulama kimlik bilgileri ve backend OAuth; 4 istek/sn, en fazla 8 açık istek.
- Lisans/ticari kullanım: belgeler ücretsiz kullanım belirtir; ticari ürünler için ortaklık süreci tanımlar. Bunu koşulsuz ticari onay saymıyoruz.
- Saklama: FAQ yerel saklama/önbelleği destekler, ortaklık bitiminde verinin tutulabileceğini söyler. Görünür ve sabit IGDB atfı beklenir.
- Eksik doğrulama: LTZ'nin ticari statüsü, geçerli ortaklık şartları ve görsel hakları kesinleştirilmeli. Genel Twitch sözleşmesinin içeriği bu taramada okunabilir dönmedi; IGDB FAQ tek başına sınırsız hak kanıtı sayılmadı.

[IGDB resmî API, şema ve FAQ](https://api-docs.igdb.com/) · [Twitch sözleşme adresi](https://legal.twitch.com/en/legal/developer-agreement/).

**Değerlendirme:** Belge seviyesinde en güçlü teknik aday. Kullanıcı onayı, erişim kurulumu ve koşul teyidi sonrasında küçük bir AppID örneklemiyle eşleme doğrulanmalıdır. Şimdilik entegrasyon yoktur.

## RAWG — İkinci aday, lisans açıklığı yetersiz

Açıklama, tür, çıkış tarihi, şirket, medya, mağaza bağlantısı ve sistem gereksinimi alanları sunulur. Steam mağaza URL'sindeki AppID eşleme kanıtı olabilir; ad benzerliği yeterli değildir. Doğrudan AppID ile evrensel ters sorgu garantisi doğrulanmadı. [API tanıtımı](https://rawg.io/apidocs), [resmî şema](https://api.rawg.io/docs/).

Plan sayfası Free için ticari olmayan kullanım ve aylık 20.000; Business için ticari kullanım ve aylık 50.000; Enterprise için aylık 1.000.000 isteğe kadar sınır gösteriyor. Verinin kullanıldığı sayfalarda geri bağlantı bekleniyor. Aynı sayfanın koşul bölümünde ücretsiz ticari kullanım ifadesi bulunması, plan metniyle çelişiyor. [RAWG planları](https://rawg.io/apidocs).

API koşulları da ücretsiz ticari kullanım için istek/kullanıcı/sayfa sınırları tanımlıyor; yeniden dağıtımı kısıtlıyor. Yerel önbelleğin süresi, kalıcı yeniden saklama ve sözleşme bitimindeki veri kullanımı bu incelemede açıkça doğrulanmadı. Ücretli planın her görselin haklarını otomatik çözdüğü varsayılmamalı. [RAWG API koşulları](https://rawg.io/tos_api).

**Karar:** Ticari kullanım ve kalıcı saklama için yazılı netlik olmadan önerilen ana kaynak değil. Fiyat, erişilebilirlik ve doluluk canlı doğrulanmadı.

## Diğer adaylar

**FreeToGame:** Ücretsiz oyun/MMO kapsamındadır; açıklama, tür, şirket ve çıkış tarihi alanları vardır. Kişisel/ticari kullanım ücretsiz, etkin bağlantıyla atıf zorunlu; saniyede 10 isteği aşmama beklentisi var. AppID'ye güvenilir doğrudan eşleme ve kalıcı saklama/sona erme şartları doğrulanmadı. Bu yüzden tüm Steam kataloğuna çözüm değil; yalnız dar kapsamlı yardımcı adaydır. [Resmî belgeler](https://www.freetogame.com/api-doc).

**Giant Bomb:** Resmî sayfa API erişim mekanizmalarının şu anda mevcut olmadığını ve değişeceğini bildiriyor. Eski metadata şeması, Steam eşleme, ticari lisans, kota, saklama ve atıf bilgileri güncel çalışan sözleşme olarak doğrulanamadı. Sürdürülebilir entegrasyon adayı seçilmedi. [Resmî API durumu](https://giantbomb.com/api).

## Önerilen LTZ entegrasyon sınırı — Henüz uygulanmadı

1. Steam katalog adaptörü ve mevcut `(STEAM, AppID) → gameId` zinciri korunur. IGDB kataloğu Steam indeksinin yerine geçirilmez.
2. Detay kaynağı ayrı adaptörle, mevcut sağlayıcı portuna uyumlu biçimde bağlanır; Steam katalog yetenekleri aynen kalır. Katalog ve detay sağlayıcıları birbirinin yerine seçilmez.
3. Steam AppID'den bulunan IGDB eşlemesi tam dış kimlik kanıtıyla doğrulanır. Birden çok aday/edition/remaster varsa otomatik birleştirme yapılmaz. Kanonik `gameId` değişmez.
4. Kaynak kimliği, eşleme kanıtı, atıf ve alınan alanların kaynak zamanı için ayrı izlenebilirlik tasarlanır. Başka kaynağın alanı sessizce ezilmez. Bu ek tasarım ve migration, sağlayıcı onayı sonrasında yapılır.
5. Şimdiki altyapı açıklama, tarih, geliştirici/yayıncı ve güvenilir tür sinyalini destekler. Medya, dil ve diğer alanların adaptör eşlemeleri onay sonrası genişletilir; bugün çalışıyor sayılmaz.
6. Başlangıç pilotu az sayıda doğrulanmış AppID, sınırlı alan kümesi ve ölçülen hata/kota sonuçlarıyla yapılır. Sağlayıcı kesintisinde yerel okuma çalışmaya devam eder.
7. Kimlik bilgileri backend'de tutulur. Yeni ENV önerisi, aktarım ve güvenli token yaşam döngüsü kullanıcı onayından sonra hazırlanır. Bu araştırmada ENV değiştirilmedi.

## Beklenen karar

IGDB'yi yalnız detay kaynağı olarak, yukarıdaki sınırlarla ilerlemek üzere öneriyoruz. **Kullanıcı entegrasyon onayı bekleniyor.** Ticari kullanım gerekiyorsa ortaklık/koşul teyidi de gereklidir; bu rapor sağlayıcı adına izin vermez. Kaynak veya izin koşulları sonuçlanmazsa GAME-17 gerçek adaptörü **veri kaynağı kararı bekleniyor** durumunda kalır; hazır altyapı ve test sağlayıcısı üretim entegrasyonu sayılmaz.
