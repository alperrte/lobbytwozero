# LTZ-HAM-LIB-05 — Son bilinen oyun özetiyle koleksiyon geçmişini koruma

* LIB-17 için V42 game_snapshots migration'ı ve public metadata deposu eklendi; V41 korundu.
* Başarılı Game batch kartları snapshot'a alınır; unavailable, timeout ve bozuk yanıt son kullanılabilir kaydı ezmez.
* Metadata kaynağı LIVE/LAST_KNOWN/NONE ve gözlem zamanı yanıta eklendi; eski metadata açıkça işaretlenir.
* Eksik title için snapshot fallback'i, snapshot yoksa null alanlar ve metadataPartial davranışı uygulandı.
* Snapshot adları global TITLE sıralamasına katılır; hiç bilinmeyen adlar iki yönde de sonda kalır.
* Batch upsert, UUID sıralı yazma ve gözlem zamanı karşılaştırmasıyla eski/eşzamanlı yanıtların snapshot'ı geri alması önlendi.
* Katalog hatasında koleksiyon id/bayrak/tarih/version bilgileri korunur; snapshot mevcut Game doğrulamasını atlatmaz.
* İşaret geri alma ve açık silme katalogdan bağımsızdır; D4 silme ortak public metadata'yı veya diğer owner verisini kaldırmaz.
* V41→V42 yükseltmesi, katalog durum geçişleri, fallback sıralaması ve 16 eşzamanlı snapshot yenilemesi test edildi.
* Maven clean verify: 100 PASS, failure/error/skipped 0; LINE %99,09; Checkstyle/PMD 0 ihlal.
* ltz-dev Library imajı yenilendi; V42 normal Flyway akışıyla uygulandı. Sağlık, güvenlik ve gerçek public Game batch kontrolleri 34/34 PASS.
* Game arşiv/metadata yanıtları otomatik testlerde simüledir; gerçek Game arşivleme E2E NOT VERIFIED.
* docs/api.md LIBRARY sözleşmesi ve Faz 5 sonuç raporu güncellendi; diğer servisler ve frontend değiştirilmedi.
* Faz 5: 1/1; genel: 15/18. LIB-10/11/14 gerçek servis entegrasyonlarını bekliyor.
