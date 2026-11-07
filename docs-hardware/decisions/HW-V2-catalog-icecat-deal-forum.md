# HW V2 — dört kategori, Icecat ve bağımsız fırsat forumu

> 2026-11-07: Katalog veri aktarımı için Open Icecat kararı kullanıcı tarafından iptal edildi.
> Güncel kaynak [PC Part Dataset](pc-part-dataset-import.md); bu belgenin Icecat ve
> V10 forum rezervasyonu ifadeleri tarihçedir. Yeni görev yalnız yerel veri aktarımıdır.

Tarih: 7 Kasım 2026. Kaynak: Alper'in açık uygulama talimatı ve `LTZ-Hardware-Kapsam-Guncellemesi-V2-Uygulama.md`.

Bu karar HW02-catalog-campaign-proposal.md belgesinin yeni kullanıcı forumuyla çelişen iş kapsamının yerine geçer. Eski belgenin güvenlik, audit, yetkili katalog yönetimi ve veri uydurmama kuralları sürer. Eski yönetici `/campaigns` API'si geriye uyumluluk için korunur ve yeni forum tarafından kullanılmaz.

- Keşif kataloğu CPU/MOTHERBOARD/RAM/STORAGE ile sınırlıdır. Profil enum'u ve eski API tüketicileri on iki kategoriyi kullanmaya devam eder.
- Open Icecat resmi JSON + XML indeksleri kullanılır. Kaynak kimliği, SKU, kalite, senkronizasyon ve yerel medya takibi ayrı tutulur. Eksik değer tahmin edilmez; doğrulanmış manuel kayıt körlemesine güncellenmez. Çelişkiler karantinaya alınır.
- Entegrasyon varsayılan kapalıdır. Gerçek hesap/izin, sunucu tarafı secret yapılandırması ve medya hakları doğrulanmadan canlı aktarım başlatılmaz. ENV/Compose/production storage değişikliği bu kararın otomatik sonucu değildir.
- PSU rehberi yalnız SPL / Sir ProvidenceLaw ve katkıda bulunanlara atıf yapan ZTT dış bağlantısıdır; içerik kopyalanmaz.
- Fırsat forumu Hardware içinde ayrı bounded context'tir; başka ekibin community/content/admin servisi değiştirilmez. Gönderi bir donanım ürünü gerektirmez. Başlık/açıklama/kontrollü kategori zorunludur; fiyat ve kaynak isteğe bağlı, doğrulanmamış kullanıcı beyanıdır.
- UGC plain text, principal'dan sahiplik, CSRF, rate limit, optimistic locking, raporlama ve audit korunur. Kullanıcı URL'si sunucudan fetch edilmez. Mevcut User görünürlük/engel politikası kullanılabilir olduğunda aynı güvenli sözleşme tüketilir; yeni servis sözleşmesi uydurulmaz.
- V9 kaynak/medya/import şeması, V10 forum şeması için ayrılır. V1–V8 değiştirilmez. Gerçek DB'ye migration uygulaması test sonucu gibi sunulmaz.

Resmi kaynaklar: [JSON API](https://iceclog.com/manual-for-icecat-json-product-requests/), [XML indeksleri](https://iceclog.com/manual-icecat-product-xmls-batch-processing/), [kaynak taksonomisi](https://iceclog.com/manual-for-reference-files/), [fair use](https://iceclog.com/open-icecat-fair-use-policy/). Canlı ürün sayıları ve test sonuçları teslim raporunda ayrıca belirtilir.
