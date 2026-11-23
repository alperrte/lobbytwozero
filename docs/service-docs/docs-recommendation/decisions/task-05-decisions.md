# TASK 5 — uygulama ve final kapı kararı

2026-11-15 · Alper · REC-22 **DONE (kapalı rehberlik kapsamı)**; TASK 5 kapanışı
**PARTIAL / NOT READY**, çünkü native pre-push ve SonarQube final kapıları BLOCKED.

Bağlayıcı çalışma kararı [ADR 0003](../../docs/decisions/0003-recommendation-python-yardimcisi.md)
kullanıcı tarafından açıkça onaylandı. Aynı servis içinde gerçek CPython, sabit script ve
standart kütüphane seçildi; pip/sidecar/LLM/ENV eklenmedi.

GAME seçimi katalog filtresi ve gereksinim inceleme rehberidir, gerçek aday/ranking sorgusu
değildir. LTZ_USAGE kendi history/feedback kullanım adımlarıdır. HARDWARE, owner doğrulanan
mevcut assessment durumlarına göre açıklama üretir; eksik girdiyi belirtir ve FPS/tam sistem
uyumu garantisi vermez. Bu kapsam REC-02/03/05/06/09/13/14/15/16 açıklarını kapatmaz.

Java giriş/çıkış DTO'su, topic-intent allowlist ve response plan doğrulaması Python çıktısını
sınırlar. Kimlik/token/profile snapshot/history Python'a aktarılmaz; sadece genreProvided,
eksik alan kodları ve assessment durumları gider. Ana serviste mevcut JWT/rıza/ACTIVE/kota
kontrolleri korunur. Assistant history veya analitik kaydı oluşturmaz; geri çekilmiş rıza
mevcut history purge semantiğini paylaşır.

İlk Java/Python koşusunda Windows `-c` kaçışı hata verdi; sabit script dosyasıyla giderildi.
Sonraki 80 test geçse de PMD kapısı kırmızıydı; try-with-resources, cause koruması, response
validator/plan ve FPS test fixture sorumlulukları ayrılarak gerçek ihlaller düzeltildi.
Kural kapatma, baseline büyütme veya coverage policy değişikliği yapılmadı.

Docker minimal Python paketinde JSON yoktu; gereken standart kütüphane paketi eklenip
image yeniden doğrulandı. Son image non-root, ağsız/read-only smoke ve HIGH/CRITICAL
Trivy kapısını geçti. Bu, canlı Gateway + kaynak servis E2E kabulü değildir.

Native pre-push/Sonar betikleri gerçek profil dosyasını okur. Oturumluk read guard bunları
okumadan engelledi; uygulama DB'sine düşecek Maven çağrısı başlatılmadı. Ortak kalite araçları
bu kapsamda değiştirilmedi. Ayrı secretsiz CI runner gerçek disposable PostgreSQL ile
80 test ve kapıları geçti; bu sonuç native pre-push/Sonar PASS yerine yazılmadı.
Sonar localhost:5199 erişimi başarısız, terminal analiz token'ı yok; scanner/gate/A'lar UNKNOWN.

[Test kanıtı](../testings/task-05-tests.md), [final kapı tablosu](../testings/pre-merge-quality.md),
[çalışma yönergesi](../integration/assistant-runtime.md). Gerçek Git/PR/deploy işlemi yoktur.
