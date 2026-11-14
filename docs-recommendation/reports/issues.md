# TASK 1 sorun ve sınırlama kaydı

2026-11-14 · TASK 1 kod/test kapsamını bloke eden açık sorun yoktur.

| Kayıt | Durum | Kanıt / çözüm |
| --- | --- | --- |
| Windows araç ortamında Docker Compose eklentisi bulunamıyor | RESOLVED (yerel komut uyarlaması) | İzole runner'ın `runCommand` enjeksiyonuyla aynı Compose argümanları kurulu docker-compose.exe'ye verildi; gerçek ENV ve ortak runner değişmedi |
| Mockito inline agent Türkçe Windows yolunda yüklenemiyor | RESOLVED | Yalnız interface kullanan unit testlerde ajan gerektirmeyen küçük sözleşme test uygulamaları kullanıldı; production mock yok |
| Launcher'a annotation eklemek merkezi coverage'da ölçülemeyen semantik değişiklik | RESOLVED | Scheduling analitik bileşenine taşındı; launcher orijinal kaldı; policy/manifest değişmedi |
| Eski docx ve mimari metinleri güncel kodla çelişiyor | DOCUMENTED | PostgreSQL/Java25/Boot4.1.1 ve güncel Auth kodu esas; `integration/contracts.md` |
| Bağımsız `LTZ_PDA_Alper*.md` repoda yok | DOCUMENTED | Master v2 §4 isim/açıklamaları aynen kullanıldı; ayrı kaynakla karşılaştırma iddiası yok |
| Gerçek Recommendation Auth/downstream tüketicisi ve kullanıcı HTTP akışı yok | NOT IMPLEMENTED | TASK 1 yalnız servis içi temel; 401/403 smoke testi JWT/RBAC/IDOR kabulü değildir |
| REC-23 feedback/gösterim/tıklama olayları yok | NOT IMPLEMENTED (ürün akışları) | Kaynak işlem olmadan sayaç icat edilmedi; TASK 1 motor kullanım/sonuç gözlemleri ve tasarım sınırı testli |
| Python çalışma modeli | KARAR BEKLİYOR | Mevcut mimari §10 korunur; TASK 1 sürümlü port Python çalıştırıldığı iddiası taşımaz |

Sonar ve legacy pre-push final gate bu checkpoint'te çalıştırılmadı; TASK 5'e ayrılan
kapıların geçtiği veya projenin merge-ready olduğu iddia edilmez.
