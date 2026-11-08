# LTZ donanım görselleri

`illustrations/` altındaki sekiz SVG bu proje için özgün vektör çizimleridir:
cpu, gpu, motherboard, ram, storage-nvme (M.2 biçimi), storage-sata (2.5 inç SSD),
storage-hdd ve storage (türü bilinmeyen kayıt için genel çizim).
Üretici fotoğrafı, logo, gerçek ürün modeli veya üçüncü taraf ikon kopyası içermez.
640×360 viewBox, transparan zemin, aynı metal paleti ve LTZ kırmızısı kullanılır.
Çizimler gerçek ürünün ölçüsü, fan/modül sayısı veya soğutucu tasarımına dair kanıt değildir.

Bu görevde üçüncü taraf marka logosu indirilmedi. İzni doğrulanmayan markalar
uygulamanın normal yazı tipiyle, sade tipografik badge olarak sunulur; marka
wordmark'ları yeniden çizilmez. Kaynak ve kullanım incelemesi:
[görsel sistem raporu](../../../../docs-hardware/reports/HARDWARE_PRODUCT_VISUAL_SYSTEM.md).

Gelecekte incelenmiş gerçek ürün görselleri `products/` altına yerel dosya olarak
eklenebilir. `HardwareProductVisual.approvedImage` yalnız bu dizindeki kontrollü
PNG/JPEG/WebP/AVIF adlarını kabul eder; şu anda hiçbir çağrı gerçek görsel vermiyor.
Dosya yolu denetimi kullanım hakkı doğrulaması yerine geçmez; hak incelemesi varlık
eklenmeden önce yapılır. Bu README üçüncü taraf logolarına lisans sağlamaz.
