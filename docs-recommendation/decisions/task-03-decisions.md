# TASK 3 — donanım değerlendirmesi ve kanıt sınırları

2026-11-15 · Alper · LTZ-ALP-REC-03 · PARTIAL.

## Kaynak ve yetki

Tek seçilmiş oyun + tek owner profil için `POST /api/v1/recommendation/hardware/assessments`.
Hardware owner batch ucu kullanıcı Bearer'ıyla çağrılır; profileId/version ve tam ilk sayfa
doğrulanır. En çok 100 bileşen; hasNext veya tutarsız total varsa PROFILE_WINDOW_EXCEEDED/
INVALID_SOURCE_RESPONSE ile 503. Eksik satırlar yokmuş gibi kabul edilmez. Birden fazla profil
aynı kullanıcının ayrı istekleriyle seçilebilir; farklı makinelerin bileşenleri karıştırılmaz.
Marka/productId zorunlu değildir; manuel ve marka belirtilmemiş model/spec alanları geçerlidir.

Game'in gerçek tek oyun metadata endpoint'i ve platforma göre minimum/recommended alanları
okunur. Adult=false ve delisted=false doğrulanmayan oyun GAME_UNAVAILABLE 404'tür.
Performance mevcut account-bound reference:read grant'ıyla ve aynı game/profile kimliğiyle
çağrılır; kimlik/kapsam/şema/sonuç eşleşmesi doğrulanır. Doğrudan fanout 6 çağrıdır:
User consent + Auth ACTIVE Library grant + Hardware batch + Game metadata + Auth Performance
grant + Performance reference. Timeout/redirect/body sınırları Task 2'deki ortak HTTP transport'tadır.

Kanonik roller, JWT/JWKS, no-store/private, CSRF/Gateway sınırı ve 16 KiB body limiti korunur.
Generation/assessment hesap başına aynı 10/dk penceresini paylaşır. Var olan uygulama rıza
denetimi yeniden kullanılır; rıza reddinde Task 2 kişisel geçmişi de temizlenir, rate sayacı
sıfırlanmaz. Yeni auth/ENV/bağımlılık/altyapı yoktur; mevcut caller/scope izni kullanılır.

## Minimum ve recommended karşılaştırması

İki seviye bağımsızdır; Check status: MEETS, BELOW, UNKNOWN. computeStatus yalnız CPU/GPU/RAM
alt kümesidir; overallStatus ayrıca VRAM, depolama ve OS/driver belirsizliklerini içerir.
Bir gerekli alanda kesin BELOW varsa seviye BELOW; aksi halde tüm alanlar kanıtlanmadıkça UNKNOWN.

- CPU/GPU: yalnız tek bileşen, quantity=1 ve tek model gereksiniminde NFKC/case/whitespace
  normalizasyonuyla **tam model eşleşmesi** MEETS'tir. Daha büyük model numarası üstün sayılmaz;
  alternatifli/serbest metin veya farklı model UNKNOWN'dır. Benchmark/CPU/GPU puanı icat edilmez.
- RAM: tüm RAM bileşenlerinin capacity×quantity toplamı gerekir; bir modül bilinmiyorsa toplam
  UNKNOWN'dır. Kaynak nominal GB/MB birim konvansiyonunu netleştirmediği için decimal/binary
  byte sınırlarıyla karşılaştırılır: capacity lower=GB×10^9, upper=GB×2^30; requirement
  lower=MB×10^6, upper=MB×2^20. Alt kapasite üst gereksinimi karşılıyorsa MEETS; üst kapasite
  alt gereksinimden küçükse BELOW; aradaki belirsizlikte UNKNOWN. BigInteger taşmayı önler.
  Bu politika belirtilen iki byte konvansiyonuyla sınırlıdır; birim sözleşmesi talebi açıktır.
- Depolama: toplam kapasite **boş alan değildir**. Toplam üst sınırı bile gereken alandan
  küçükse BELOW kanıtıdır; büyük toplamdan kullanılabilir boş alan veya kurulum garantisi çıkarılmaz.
- VRAM: yalnız tek GPU'nun bildirilen kapasitesi gösterilebilir; çoklu GPU VRAM'i toplanmaz.
  Game'de yapılandırılmış VRAM gereksinimi olmadığından kontrol UNKNOWN'dır.
- OS/driver: owner teknik profil sözleşmesi bu bilgiyi kanıtlamaz; UNKNOWN. Dolayısıyla mevcut
  sözleşmeyle bütün bilgisayar için olumlu MEETS_MINIMUM/MEETS_RECOMMENDED sonucu üretilmez.

Yanıttaki suitability yalnız kanıtlı minimum açığında BELOW_MINIMUM; diğer hallerde UNKNOWN'dır.
Compute MEETS olması “oyun kesin çalışır” anlamına gelmez. Katalog genelinde hardware oyun
listesi/system filter yoktur: Game metadata batch sözleşmesi eksiktir; N+1 uygulanmadı.

## Grafik ve yükseltme önerisi

Minimum CPU/GPU/RAM karşılaştırması MEETS, bilinen minimum açık veya component mismatch yoksa
LOW yalnız **varsa düşük ayardan başlayıp oyun içinde ölçme** yönlendirmesidir; CONDITIONAL ve
FPS taahhüdü olmadan döner. Diğer hallerde INSUFFICIENT_DATA ve preset=null. Üreticinin
recommended sistemini HIGH/MEDIUM preset veya belirli FPS garantisi sayma kuralı yoktur.

Yükseltme önerisi yalnız kanıtlanmış RAM veya toplam depolama açığında kaynak gereksinimi
gösterir. Minimum/recommended gerekçeleri ayrı taşınır. Farklı CPU/GPU modelinden keyfi
darboğaz, satın alma listesi, fiyat, vendor veya yükseltme uyum garantisi çıkarılmaz.

## Parça uyumluluğu

Söz dizimi desteklenen AMn/LGAn socket ve DDRn memoryType alanları karşılaştırılır.
Farklı beyan edilmiş socket/DDR değeri INCOMPATIBLE gerekçesidir; eşitlik yalnız DECLARED_MATCH.
Chipset/BIOS, RAM slot/form factor, GPU slot/boyut/güç verisi kanıtlanmadığından bütün profil
UNKNOWN kalır (bir açık mismatch varsa INCOMPATIBLE). Eksik/çoklu CPU veya motherboard
birini rastgele seçmez. Bu, bildirilen teknik alanlara dayalı değerlendirmedir, fiziksel sertifika değildir.

## FPS ve saklama sınırı

Performance reliable bayrağı örnek sayısı eşiğidir; resolution/preset/provenance/profileVersion
bağı yoktur. Bu aggregate'in min/max'ı tahmin aralığına dönüştürülmez. İstek aynı resolution/preset
taşısa dahi ölçümlerin o bağlamda yapıldığı kanıtlanamaz. 0/az/çok/outlier örnekte tahmin null,
confidence NONE, INSUFFICIENT_DATA. Count/session/lastObservedAt ve producer eşiği tanı bilgisidir.
Gerçek pozitif FPS tahmini branch'i varmış gibi test veya tamamlanma iddiası yoktur.
[Performance sözleşme ihtiyacı](../integration/dependencies/performance-service-requests.md).

Analiz stateless'tir; run/history/ham hardware/FPS snapshot yazılmaz. Profil sürümü ve değerlendirme
zamanı yanıtı sınırlar; sonradan değişen profile güncel sonuç garantisi yoktur. Yeni migration gerekmez;
V11/V12 ve Hardware/Performance şemaları değişmedi. Üreticiye eksik veri ekleyen DTO iskeleti
yerine kanıt eksikliği açık bırakıldı. Kaynak testleri fixture contract + gerçek Auth issuer/JWKS;
tam canlı Hardware/Game/Performance/worker/Gateway E2E henüz doğrulanmadı.
