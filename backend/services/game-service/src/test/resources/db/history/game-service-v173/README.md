# Tarihsel Game Service migration fixture'ları

Bu 13 SQL dosyası, yeniden yapılandırma öncesindeki V31–V40 ve V171–V173 zincirinin
byte düzeyinde kopyalarıdır. SHA256SUMS arşiv bütünlüğünü doğrular.
Yerel .gitattributes SQL dosyalarındaki otomatik EOL dönüşümünü kapatarak bu
bütünlüğü farklı işletim sistemi checkout'larında korur.

Yalnız testler açıkça bu lokasyonu seçer. Normal uygulama lokasyonu
`classpath:db/migration` bu klasörü içermez; test kaynakları uygulama JAR'ına girmez.
Buradaki tarihsel ALTER ifadeleri yeni başlangıç migration zincirinin parçası değildir.

LocalizationMigrationTest gerçek eski V40 -> V173, PhaseSixMigrationTest gerçek eski
V171 -> V173 yükseltmesini sınamaya devam eder. MigrationArchitectureTest bu zinciri
yeni üretim kaynaklarıyla karşılaştırır ve eski geçmiş üzerine yeni zincirin
uygulanmasının güvenli biçimde reddedildiğini doğrular. Fixture'lar değiştirilmez.
