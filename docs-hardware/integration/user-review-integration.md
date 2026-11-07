# HW-12 / HW-USER-REQ-02 — REVIEW_AUTHOR tüketicisi

Tarih: 2026-11-06. Sorumlu: Alper. Kaynak cevap:
[Kerem'in hardware-cevap-01 kaydı](../../docs-user/integration/services/hardware-service-talepleri/hardware-cevap-01.md).
PR #67 merge commit'i `0199494` yerel geçmişte ve REVIEW_AUTHOR kaynak kodu mevcut.

## İş kuralı ve uygulama

Ürün yorumu, kullanıcının donanım profilinin HARDWARE gizlilik ayarından bağımsızdır.
User'a `POST /internal/v1/privacy/decisions` ile doğrulanmış viewer,
`field=REVIEW_AUTHOR` ve yorum yazarı kimlikleri gönderilir. Auth'un mevcut
hardware-service → user-service / privacy:read client-credentials sözleşmesi kullanılır.

- Listeleme aynı REPEATABLE_READ snapshot'ında, sayfa almadan önce ürünün VISIBLE
  yorumlarındaki distinct yazarları çıkarır. Gizli/silinmiş yorum yazarları sorulmaz.
- `UserServiceReviewAudiencePolicy` adayları 100'lük gruplarla sorar. Her grupta tam
  owner kümesi, tekillik, UUID biçimi ve boolean alanlar doğrulanır. Eksik/yabancı/
  mükerrer karar veya bir grubun hatası önceki gruplardaki izinleri de geçersiz kılar.
- `allowed=false` yazarlar blockedAuthors kümesine alınır. SQL filtresi sayfa,
  totalElements/totalPages ve puan özetinden önce uygulanır; sayfa sonradan elenmez.
- Detay ve mevcut raporlama erişimi yalnız hedef yorumun yazarı için aynı kararı
  alır. Owner detay/mine davranışı ve yerel moderasyon teslimi değişmez.
- Bozuk yanıt, eksik credential, 401/403/429/5xx veya timeout dış izni kapatır;
  kendi yorumları mevcut kuralla korunur. Yeni endpoint, migration veya ENV yoktur.

## Sınırlı karar ve sözleşme ayrıntısı

Mevcut `HardwareReviewAudiencePolicy.Decision.blockedAuthors` üst sınırı 1000'dir.
Tüm kararları sınırlı ve eksiksiz temsil etmek için bu tüketicide toplam aday
kümesi de 1000 ile sınırlandırılır. Aday sorgusu en çok 1001 distinct UUID alır;
1000 aşılırsa kısmi global izin
verilmez, yalnız owner yorumları kalır. En çok 10 User batch çağrısı yapılır.
Bu sınır yorum ekleme kotası değildir; tam karar temsil edilemediğinde güvenli ret
davranışıdır. Daha büyük kataloglarda ayrı, sorgulanabilir karar modeli gerekecektir.

Kerem'in cevap metni setting için “her zaman PUBLIC” diyor; mevcut User kodu pasif
hesap için **PRIVATE + allowed=false** döndürüyor. Tüketici bunu ret olarak kabul
eder; PUBLIC dışındaki hiçbir izin cevabını açmaz. User kodu ve User API bölümü
bu görevde değiştirilmedi; bu fark kaynak önceliğiyle açıkça kaydedildi.

## Üretim sınırı

REQ-02 sözleşmesi karşılanmıştır. REQ-01 operasyonel olarak açıktır: User'ın
`projection-enabled=false` ayarı değişmez. Umut'un başlangıç aktarımı doğrulanıp
Kerem yansımayı güvenilir ilan edene kadar başkasının yorumları görünmez.

`node ops/dev.mjs e2e hardware --with-user` gerçek Auth/Hardware/User/Gateway
JAR'ları, ayrı geçici DB'ler ve sentetik hesaplar kullanır. Yalnız bu testte User
önce false, sonra yeni fixture sürecinde true ile başlatılır. PRIVATE Hardware
ayarından bağımsız yorum görünürlüğü, iki yönlü engelleme, sayfa/toplam/ortalama ve
önbelleksiz unblock kontrol edilir. Social **servisi** başlatılmaz; Auth'tan gerçek
Social client token'ı ile sentetik olaylar doğrudan User'a verilir. Bu test Social
başlangıç aktarımını veya ortak DEV'i doğrulamaz. Auth D4 yalnız Hardware'a
yönlendirilir; 16 hedef korunur ve diğer 15 ACK beklemede kalır.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| Başlangıç, değişiklik öncesi Hardware | 474 PASS; merkezi coverage/ratchet ve bağımlılık taraması PASS |
| Son Hardware JDK25 `clean verify`, 2026-11-06 07:59:49 +03 | **503 PASS**, failure/error/skipped 0; **29 yeni senaryo** |
| HardwareServiceClientsTest | **98 PASS**, önceki 69'a REVIEW_AUTHOR için 29 eklendi |
| Merkezi coverage | **PASS**, 1752/1770 = **%98,98**; değişen executable satırlar 157/157 = **%100** |
| Checkstyle/PMD ratchet | **PASS**, mevcut 213 borç aynı; yeni ihlal 0 |
| Taze runtime JAR bağımlılık taraması | **PASS**, HIGH/CRITICAL 0 |
| `node ops/dev.mjs e2e hardware --with-user` | **25/25 PASS**; gerçek 4 JAR, 3 PostgreSQL 17 ve Mailpit; 5 süreç (User iki başlangıç) / 4 container temizlendi |
| `node --test ops/hardware-auth-e2e.test.mjs` | **9/9 PASS** |
| `node --check ops/dev.mjs e2e hardware`, `git diff --check` | **PASS** |

İlk gerçek User fixture koşusu, projection=false ret kabulünü geçti; yeniden
başlatma adımında Windows'ta sinyalle kapanan sürecin exitCode'u null kaldığı için
`Timeout: fixture User shutdown` ile durdu. Kaynaklar temizlendi; bu koşu PASS
değildir. Test betiği signalCode'u da dikkate alacak şekilde düzeltildi; tekrar
koşu 25/25 geçti. Uygulama güvenliği veya kalite kuralı kapatılmadı.

Tam modül kontrolü gerçek ENV okumayan CI runner ile yapıldı. Önceki görevdeki
yerel wrapper aynı Compose executable uyarlaması ve geçici ASCII Mockito agent'ını
kullanır; ortak runner, manifest ve baseline değiştirilmedi. User JAR yalnız
`-DskipTests package` ile hazırlandı; bu User tam modül kalite kabulü değildir.

Yerel Git dışı kanıtlar: `logs/hardware-review-baseline.log`,
`logs/hardware-review-final.log`, `logs/hardware-review-clients.log`,
`logs/hardware-review-user-package.log`, `logs/hardware-user-runtime.log`.
Full dev, bütün backend, frontend ve Sonar bu görevde çalıştırılmadı;
`SAFE TO COMMIT`/PDA kapanışı veya gerçek Social başlangıç aktarımı iddiası yoktur.
