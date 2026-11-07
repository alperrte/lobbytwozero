# 0002 — Harici oyun kimliği düzeltme protokolü

Durum: TASLAK — gerçek kimlik taşıma/ayırma ek kapsamının teknik sözleşmesi; asıl GAME-26 kabul kapanışı geçerlidir.

Tarih: 5 Kasım 2026  
Hazırlayan alan: Hamza / Game, Library, Review.

## 7 Kasım 2026 durum açıklaması

5 Kasım 2026 tarihli `6578e3e` kaydı GAME-26'yı, gerçek dış kimlik mutasyonlarının
bilerek kapalı bırakıldığı kabul kapsamıyla kapatmıştır. Bu kapanış geçerlidir.
Bu taslak protokol, o kabulün yapılmamış zorunlu işi olarak değerlendirilmez.

7 Kasım'da kullanıcı yalnız referanssız oyunlar için gerçek taşıma/ayırma ek yeteneğini
istedi ve şimdilik servis sahiplerine sözleşme hazırlanmasını talep etti. İş kuralı
onaylı; teknik uygulama/ortak kabul beklemededir. Güncel teslim belgesi:
`docs-Game Service/GAME-26-Servis-Sahipleri-Entegrasyon-Sozlesmesi.md`.
Mevcut `game-references:read` okuma izni ile yeni koordinasyon yetkileri farklıdır.

## Problem ve mevcut davranış

Bir Steam AppID'sinin başka bir kanonik oyuna taşınması veya yeni oyuna ayrılması,
eski gameId'ye bağlı koleksiyon, puan ve diğer içeriklerin anlamını değiştirebilir.
Game'in kendi transaction kilidi diğer servislerdeki yeni yazmaları durdurmaz.

Bu nedenle mevcut HTTP yönetim girişleri gerçek mutasyonda
`409 FOREIGN_REFERENCE_SAFETY_UNVERIFIED` verir. Reassign yalnız beklenen kaynak
ile hedefin aynı olduğu doğrulanmış no-op isteğinde 200 döner. GAME-26'nın manuel
kilit, arşivleme ve audit kabulü tamamlanmış olsa da bu ilave yetenek açık değildir.

## Önerilen karar

Kullanıcının 7 Kasım iş kuralı: ilk güvenli sürüm yalnız **hiçbir tüketicide referansı olmayan** kaynaklarda
taşıma/ayırmayı açacak. Referanslı kayıtların yeniden atanması ayrı bir ürün/veri
taşıma kararı olsun. Otomatik veri taşıma, fiziksel silme veya ada göre birleştirme
yapılmasın.

1. Game, kaynak/hedef UUID ve dış referans için kalıcı operasyon kimliği oluşturur;
   ilgili katalog kaydını yeni referans edinimine kapatır.
2. Tüketici envanteri eksiksiz olmalıdır. 7 Kasım kaynak taramasındaki katılımcılar:
   Library, Review, Community, Content ve Performance. Game kendi yerel ilişkilerini
   kontrol eder; Auth yalnız yetkilendirme sağlar. Diğer servisler kalıcı gameId
   ilişkisi eklediğinde envanter genişletilir; sahipler listeyi doğrulamalıdır.
   Eksik servis başarılı/boş kabul edilmez. Game geliştirici içeriği, başarım ve
   feedback referansları da kontrol edilir.
3. Her tüketici operasyon kimliğiyle idempotent prepare isteğini işler. Yeni gameId
   yazmalarıyla aynı transaction kilidini alarak dondurma durumunu saklar ve mevcut
   referans sayısını döndürür. Sadece anlık COUNT uçları yeterli değildir.
4. Herhangi bir referans, timeout, 401/403 veya eksik katılımcıda işlem kapalı kalır.
   Hazır olan katılımcılara telafi/serbest bırakma uygulanır; başarısız telafi kalıcı
   olarak tekrar denenir. Bir süre doldu diye kendiliğinden yazma açılmaz.
5. Tüm katılımcılar hazırsa Game aynı katalog kilidi altında expected mapping'i
   tekrar denetler, eşlemeyi ve başarı audit'ini atomik kaydeder. Commit edilen
   operasyon tekrar çağrıldığında aynı sonucu verir.
6. Sonlandırma/geri alma mesajları idempotent olmalı; süreç çökmesi, ağ bölünmesi ve
   yanıtı kaybolmuş commit için kalıcı operasyon durumu sorgulanabilmeli.

## Güvenlik ve veri sınırları

- Auth'taki `game-references:read` yalnız sayım okumak içindir. Prepare/commit/release
  mutasyonları için otomatik olarak yetki vermez. Gerekli yeni dar kapsamlar Kerem
  tarafından kararlaştırılır ve Auth'a uygulanır.
- Tüm iç uçlarda servis JWT audience, subject ve scope doğrulanır. Dış yönetim ucu
  ADMIN + operatör gerekçesi ister. Kişisel referanslar API yanıtına eklenmez.
- Her servis kendi migration aralığını kullanır; geçmiş migration değiştirilmez.
- Nisa/Alper/Umut/Kerem servisleri Hamza'nın bu çalışmasında değiştirilmez.

## Açılmadan önce gereken kabul testleri

Eşzamanlı yeni favori/puan/içerik oluşturma, prepare sonrası uygulama çökmesi,
katılımcı kesintisi, kiralama kaybı, kaybolan commit yanıtı, yinelenen prepare/commit,
başarısız rollback, mapping conflict ve yetkisiz servis token'ı sınanmalıdır.
Sadece mutlu yol veya sıfır referans döndüren sahte sunucu yeterli değildir.

## Şimdiki sonuç ve karar alanları

HTTP koruması sürer; bu taslak gerçek koordinasyonun uygulandığı anlamına gelmez.
Alınacak kararlar: tam tüketici listesi ve sahipleri, referansın anlamı, yeni Auth
kapsamları, kalıcı operasyon/telafi protokolü ve teslim sırası. Bu kararlar ve uygulama tamamlanmadan
korumayı kaldırmak yeni kimlik mutasyonu kapsamını tamamlamak sayılmaz; asıl
GAME-26'nın 5 Kasım kabul kapanışı bu taslak nedeniyle yeniden açılmaz.

İlgili kaynaklar: `docs/api.md` → GAME / Yönetici: dış kimliği yeniden eşleme ve
ayırma; `docs-Game Service/Game-Service-Bekleyen-Tasklar.md`; AGENTS.md §1/§8.
