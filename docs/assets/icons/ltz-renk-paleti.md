# LTZ HARDWARE SERVICE — FRONTEND TASARIM STANDARTLARI

## 1. GENEL TASARIM YÖNÜ

LTZ Hardware Service, oyunculara ve bilgisayar donanımı meraklılarına yönelik premium bir donanım platformudur.

Tüm frontend sayfaları aynı tasarım dilini kullanmalıdır.

Tasarım referansları:
- ASUS ROG (Republic of Gamers)
- MSI MPG Gaming
- Premium Gaming Hardware Dashboard
- Modern Dark Gaming Interface

**Ana tasarım karakteri:** Karanlık, güçlü, modern, teknolojik, premium ve profesyonel.

Sayfalar birbirinden bağımsız geliştirilse bile aynı ürünün parçaları gibi görünmelidir.

## 2. ZORUNLU RENK PALETİ

Aşağıdaki renk kodlarını değiştirme.

### Arka plan ve yüzeyler

- `#0B0B0F` — Ana arka plan. Sayfanın temel arka plan rengi.
- `#141419` — Birincil yüzey. Kartlar, sidebar, navbar, paneller ve modal arka planları.
- `#1E1F26` — İkincil yüzey. Input alanları, hover yüzeyleri, iç içe kartlar ve seçili alanlar.

### Marka ve vurgu renkleri

- `#E11D2E` — Birincil vurgu. Ana CTA butonları, aktif navigasyon, önemli aksiyonlar ve marka vurguları.
- `#DB1B60` — İkincil vurgu. Alternatif aksiyonlar, seçili öğeler ve destekleyici görsel detaylar.
- `#FF2E88` — Parlak pembe. RGB donanım atmosferini destekleyen küçük vurgular, odak halkaları ve dekoratif ışık efektleri.

### Metin renkleri

- `#F1F1F1` — Birincil metin. Başlıklar, önemli bilgiler, içerik metinleri ve kart başlıkları.
- `#A0A7B4` — İkincil metin. Açıklamalar, placeholder, yardımcı bilgiler ve metadata.

### Durum renkleri

- `#22C55E` — Başarı. Başarılı işlemler, uyumlu donanımlar, mevcut stok ve olumlu durumlar.
- `#F59E0B` — Uyarı. Sınırlı stok, dikkat gerektiren işlemler ve uyarı bildirimleri.

Hata ve tehlike durumlarında `#E11D2E` kullanılabilir.

## 3. RENK KULLANIM KURALLARI

1. Sayfanın baskın rengi `#0B0B0F` olmalıdır.
2. Kartlarda ve panellerde `#141419` kullanılmalıdır.
3. İç içe geçmiş yüzeyler ve etkileşim alanlarında `#1E1F26` tercih edilmelidir.
4. Birincil aksiyonlarda `#E11D2E` kullanılmalıdır.
5. Pembe ve kırmızı tonları yalnızca vurgu amacıyla kullanılmalı, geniş alanlara gelişigüzel yayılmamalıdır.
6. Açık renkli büyük yüzeylerden kaçınılmalıdır.
7. Mavi, mor, turkuaz veya palet dışında rastgele vurgu renkleri eklenmemelidir.
8. Gradient kullanılacaksa yalnızca tanımlı marka renklerinin uyumlu geçişleri tercih edilmelidir.
9. Border, hover ve shadow renklerinde mevcut renklerin transparan varyasyonları kullanılabilir.
10. WCAG 2.2 AA metin kontrastı korunmalıdır. Kırmızı veya magenta butonlar üzerinde gerektiğinde `#FFFFFF` kullanılabilir. Parlak pembe üzerinde beyaz küçük metin kullanılmamalıdır.

## 4. COMPONENT TASARIM STANDARTLARI

### Butonlar

- Primary Button: Kırmızı (`#E11D2E`) arka plan.
- Secondary Button: Koyu yüzey, ince magenta/kırmızı border.
- Ghost Button: Transparan arka plan, hover durumunda `#1E1F26`.
- Disabled Button: Kontrastı azaltılmış ve etkileşimi kapatılmış görünüm.
- Hover efektleri yumuşak ve kontrollü olmalıdır.

### Kartlar

- Arka plan: `#141419`
- İçerik hiyerarşisi net olmalıdır.
- İnce ve düşük kontrastlı border kullanılmalıdır.
- Hover durumunda hafif yüzey değişimi veya kırmızı vurgu uygulanabilir.
- Radius, padding ve boşluklar sayfalar arasında tutarlı olmalıdır.

### Input ve formlar

- Koyu input arka planı.
- Açık renkli okunabilir metinler.
- İkincil renkte placeholder.
- Focus durumunda görünür pembe/kırmızı focus ring.
- Hata durumlarında açık hata açıklaması.

### Navbar ve Sidebar

- Arka plan koyu olmalıdır.
- Aktif navigasyon öğelerinde kırmızı vurgu kullanılmalıdır.
- Hover ve active durumları birbirinden ayırt edilmelidir.
- Menü ikonları, yazılar ve boşluklar aynı görsel standardı takip etmelidir.

### Tablolar, etiketler ve bildirimler

- Tablolarda koyu yüzeyler ve ince satır ayırıcıları kullanılmalıdır.
- Başarı, uyarı ve hata durumları tanımlı semantik renklerle gösterilmelidir.
- Durumlar yalnızca renkle ifade edilmemeli; metin veya ikonla desteklenmelidir.

## 5. TİPOGRAFİ VE GÖRSEL HİYERARŞİ

- Modern, okunabilir ve teknolojik karakterli sans-serif font kullanılmalıdır.
- Ana başlıklar güçlü ve dikkat çekici olmalıdır.
- Gövde metinleri rahat okunabilir olmalıdır.
- Başlık, açıklama, yardımcı bilgi ve etiket arasında belirgin hiyerarşi bulunmalıdır.
- Tamamı büyük harf yazılan metinler yalnızca kısa başlıklarda ve etiketlerde tercih edilmelidir.
- Harf aralıkları ve font ağırlıkları tutarlı olmalıdır.

## 6. ANİMASYON VE ETKİLEŞİM

- Hover, focus ve aktif durumlarda yumuşak geçişler kullanılmalıdır.
- Kırmızı/pembe glow efektleri yalnızca önemli etkileşimlerde hafif şekilde kullanılabilir.
- Sürekli yanıp sönen, dikkat dağıtan veya aşırı RGB animasyonlarından kaçınılmalıdır.
- Kullanıcının `prefers-reduced-motion` tercihi desteklenmelidir.
- Arayüz performansı görsel efektlerden daha önceliklidir.

## 7. RESPONSIVE VE ERİŞİLEBİLİRLİK

- Desktop, tablet ve mobil görünüm desteklenmelidir.
- Yatay taşma olmamalıdır.
- Mobil cihazlarda içerik hiyerarşisi korunmalıdır.
- Klavye navigasyonu ve görünür focus state sağlanmalıdır.
- Semantik HTML kullanılmalıdır.
- WCAG 2.2 AA hedeflenmelidir.
- Etkileşimli bileşenlerin erişilebilir isimleri bulunmalıdır.

## 8. TEKNİK UYGULAMA KURALI

Next.js, React, TypeScript ve Tailwind CSS kullanılan projelerde:

- Ortak renkler global design token olarak tanımlanmalıdır.
- Sayfa bileşenlerinde rastgele hex değerleri kullanılmamalıdır.
- Mevcut tema değişkenleri varsa yeniden oluşturmak yerine kullanılmalıdır.
- Shared UI bileşenleri varsa bunlardan yararlanılmalıdır.
- Her sayfa kendi renk sistemini veya bağımsız tasarım dilini oluşturmamalıdır.
- Mevcut mimari, klasör yapısı ve proje standartları korunmalıdır.

Önerilen token isimleri:

- `--color-background`
- `--color-surface`
- `--color-surface-elevated`
- `--color-primary`
- `--color-secondary`
- `--color-accent`
- `--color-text-primary`
- `--color-text-secondary`
- `--color-success`
- `--color-warning`

## 9. KESİNLİKLE KAÇINILACAKLAR

- Beyaz veya açık renkli sayfa arka planları.
- Her sayfada farklı renk paleti.
- Aşırı neon, glow veya gradient efektleri.
- Gereksiz glassmorphism kullanımı.
- Okunabilirliği azaltan düşük kontrast.
- Tutarsız buton, input ve kart tasarımları.
- Gelişigüzel farklı border-radius ve spacing değerleri.
- Oyun platformu kimliğinden uzak, sıradan kurumsal dashboard görünümü.

## 10. SON TALİMAT

Geliştirdiğin sayfanın işlevsel gereksinimlerini eksiksiz karşıla. Ancak görsel tasarımı bu dokümanda tanımlanan LTZ Hardware Service tasarım sistemiyle oluştur.

**Hedef:** Bütün geliştiricilerin hazırladığı frontend ekranlarının tek bir profesyonel tasarım ekibinin elinden çıkmış gibi tutarlı görünmesi.

Renk paleti sabit kalmalı; sayfa yerleşimi, içerik düzeni ve bileşen kompozisyonu geliştirilmekte olan sayfanın ihtiyaçlarına göre şekillenmelidir.
