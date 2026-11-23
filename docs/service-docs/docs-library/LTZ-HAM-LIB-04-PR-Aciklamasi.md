# LTZ-HAM-LIB-04 — Profil ve Recommendation için güvenli Library okuma sınırı

* Kullanıcı JWT'siyle hedef koleksiyon ve sayaç okuyan iki profil GET ucu eklendi.
* User servisinin profil listesi/sayacı ve Recommendation sinyalleri için üç iç GET ucu hazırlandı.
* İç okumalarda svc+jwt, caller sub, ayrı kapsamlar ve imzalı hedef/viewer bağlamı denetlenir; D4 yetkisi okuma için kullanılamaz.
* PROFILE ve RECOMMENDATION amaçlarını ayıran görünürlük portu ve erişim servisi eklendi.
* Üretim adaptörü görünürlük API'si hazır olana kadar erişimi kapatır; ret/ulaşılamama durumunda kayıt, sayaç veya Game verisi okunmaz.
* Owner kendi profilini okuyabilir; Recommendation amacı owner eşitliğiyle izin denetimini atlamaz.
* Recommendation DTO'su yalnız gameId/favorite/completed içerir; OR filtresi, kararlı sayfalama ve aynı snapshot toplamı uygulanır.
* Yeni başarılı yanıtlar no-store döner; tercihler ve izin kararları Library'de saklanmaz/cache'lenmez.
* Gerçek PostgreSQL/RSA/JWKS ile gizlilik, token ayrımı, veri azaltma, izin iptali ve 32 eşzamanlı ekle/işaretle/sil senaryoları test edildi.
* Maven clean verify: 90 PASS; failure/error/skipped 0; LINE %99,01; Checkstyle/PMD 0 ihlal.
* ltz-dev Library imajı yenilendi; yeni yolların kimlik sınırları, iç yolların Gateway'e kapalı olması ve mevcut sağlık kontrolleri 32/32 PASS.
* İzinli dış erişimler yalnız test görünürlük portuyla sınandı; gerçek User/Auth/Recommendation entegrasyonu PENDING.
* Eksik endpoint, scope, delegasyon ve tüketici sorumlulukları ayrı bağımlılık raporuna kaydedildi; docs/api.md LIBRARY bölümü güncellendi.
* Yeni migration, ENV veya bağımlılık yoktur; diğer servisler, frontend ve ortak yapılandırmalar değiştirilmedi.
* LIB-10/11/14 açık; LIB-18 otomatik test teslimi tamamlandı. Faz 4: 1/4, genel: 14/18.
