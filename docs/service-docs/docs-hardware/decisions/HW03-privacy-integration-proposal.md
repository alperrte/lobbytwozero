# LTZ-ALP-HW-03 gizlilik ve veri erişimi — APPROVED

Tarih: 2026-10-21. Sorumlu: Alper.
Kaynak: master §0.4 / §3 / HW-08–09–17, SECURITY.md §4, §8 ve §19.
Kullanıcı 2026-10-21 tarihinde erişim sınırını ve güvenli ertelemeyi açıkça onayladı.

## 1. Mevcut kanıt ve eksik sözleşmeler

HW-02 kullanıcı tarafından PDA'da kapatıldı; commit 6756210 doğrulandı.
Mevcut profil uçları yalnız owner'a açık. User ve Social bölümlerinde gerçek API
kaydı yok. Profil gizliliğini veya arkadaşlığı bir header, rol veya tahmini endpoint
üzerinden öğrenmek güvenli değildir. Mevcut genel User gizlilik tasarımı öneridir.

## 2. HW-08 için önerilen erişim

- Mevcut profil detail/update/delete ve bileşen mutasyonları owner-only kalır.
  Global katalog/kampanya hakları profil verisini görme hakkı vermez.
- Profil paylaşım düzeyi PRIVATE / FRIENDS / PUBLIC; mevcut ve yeni profillerde
  varsayılan PRIVATE. PUBLIC bu aşamada yalnız doğrulanmış diğer kullanıcıları
  kapsar; anonim iş endpoint'i açılmaz.
- Owner, profil adını/türünü ve 12 bileşen kategorisini ayrı ayrı sınırlar;
  ayrıca model, marka, adet ve teknik özellik alanları için görünürlük belirler.
  Eksik kural PRIVATE; etkili görünürlük her katmanın en kısıtlı sonucudur.
- Owner GET/PUT /api/v1/hardware/profiles/{id}/privacy ile ayarlarını yönetir;
  PUT profileVersion ister, mevcut aggregate kilidi/sürümüyle korunur.
- GET /api/v1/hardware/profiles/{id}/shared yalnız doğrulanmış actor ile çalışır.
  Non-owner için hem yerel izin hem güvenilir dış global tercih ve ilişki kararı
  gerekir. FRIENDS için doğrulanmış arkadaşlık gerekir; engelleme her durumda reddeder.
- Dış karar yok/geçersiz/erişilemez ise non-owner 404 alır. İzin verilmeyen profil ile
  olmayan profil ayırt edilmez. Gizli alan/kategori/kimlik/sayaç DTO'ya taşınmaz;
  sayfa toplamları yalnız izinli bileşenleri sayar. Adım adım IDOR/sızıntı testi yapılır.
- Üretimde dış karar portunun varsayılan adapter'ı deny/unavailable döndürür.
  Gerçek User/Social URL/JSON veya servis kimliği uydurulmaz. Güvenli test adapter'ı
  yalnız test scope'ta izin/arkadaşlık/engel senaryolarını sağlar.
- Auth/JWT DEFERRED_AUTH ve üretimde fail-closed; CSRF/no-store/16 KiB korunur.
  Yeni ENV, bağımlılık, Compose veya başka servis değişikliği önerilmiyor.
  V1–V5 değişmeden hardware aralığında sıradaki V6 privacy migration'ı kullanılabilir.

## 3. HW-09 karşılaştırma sınırı

- Profil karşılaştırma yalnız aktörün iki bilgisayarını kapsar. Yanıt kayıtlı
  kategori/adet, bilinen RAM/depolama kapasitesi ve bilinmeyen değer sayaçlarını
  karşılaştırır; otomatik donanım eşleme/performans puanı üretmez.
- Bileşen karşılaştırması yalnız owner'ın iki bileşeni; katalog model karşılaştırması
  doğrulanmış actor'ın erişebildiği iki aktif model. Teknik kategori aynı olmalıdır.
- Sayısal/metinsel sekiz kayıtlı teknik alan sunulur; veri yoksa null/bilinmiyor.
  Fark gösterimi daha iyi ürün, FPS, benchmark veya uyumluluk garantisi sayılmaz.
- Başka owner kaynağı 404; read-only POST işlemlerinde mevcut CSRF korunur.

## 4. HW-17 batch API sınırı

- Performance/Recommendation için kullanıcı adına doğrulanmış actor ile çağrılacak
  owner-only toplu profil/teknik bileşen DTO sözleşmesi sunulur.
- POST /api/v1/hardware/profiles/batch: en fazla 20 profil sayfa isteği,
  istek başına toplam en fazla 200 bileşen; sayfalama/sayaç/açık devam bilgisi.
  userId, rol veya servis kimliği request'ten alınmaz.
- Her profil aynı actor'a ait olmalı; yetkisiz/olmayan kaynak aynı güvenli 404.
  Diğer kullanıcıların paylaşılmış profilleri bu batch API'ye dahil edilmez.
  Seri numarası, owner UUID, kişisel profil adı veya gizlilik ayarı DTO'ya konmaz.
- Bu yeni bir servis-token güven modeli değildir; mevcut owner/typed actor sınırıdır.
  Kullanıcı bağlamı olmayan servis erişimi veya yeni servis yetkisi açılmaz.
  Tüketici servisleri düzenlenmez; gerçek tüketici çağrıları/E2E ayrıca doğrulanmalıdır.

## 5. Onay gerektiren erteleme

Master §3.7, Auth dışındaki zorunlu entegrasyonun onaysız ertelenmesine izin vermez.
Öneri: User/Social global görünürlük/arkadaşlık/engelleme ve Performance/Recommendation
gerçek tüketici E2E entegrasyonlarını açıkça DEFERRED_CONTRACT / NOT_VERIFIED kaydetmek.
Yalnız yerel kurallar, DTO sözleşmeleri ve fail-closed adapter testlerle tamamlanır.
Bu karar gerçek User/Social erişimi veya canlı paylaşım hazır demek değildir.
Yerel işlevler ve iki kalite kapısı geçtiğinde görev bu açık erteleme kaydıyla
READY_FOR_PDA olabilir; gerçek servis bağlantıları sonraki açık talimatla yapılır.

Alternatif: Gerçek dış sözleşmeler gelene kadar HW-08 ve sonraki alt görevleri bloke
tutmak. Dış API/kimlik/arkadaşlık varsaymak veya global tercih yokken paylaşmak önerilmez.

## 6. Onay durumu

Yeni gizlilik/karşılaştırma/batch sınırı APPROVED. User/Social gerçek sözleşmeleri
ve Performance/Recommendation tüketici E2E akışları DEFERRED_CONTRACT / NOT_VERIFIED.
Kullanıcı ayrıca tüm servisleri kapsayan pre-push full dev ve Sonar scan dev
çalıştırılmasını istedi. Git yazma işlemleri ve sonraki ana task yetkisi verilmedi.
