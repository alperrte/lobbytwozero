# TASK 1 — motor, algoritma sözleşmesi ve analitik sınırı

Tarih: 2026-11-14 · Sorumlu: Alper · Kapsam: LTZ-ALP-REC-01

## Motor sözleşmesi

`RecommendationAlgorithm` servis içi Java portudur; HTTP isteği veya yetkilendirme kanıtı değildir.
`Input` en çok 500 tekil kanonik hedef ve 1–50 sonuç limiti kabul eder. Aday/sinyal listeleri
değişmez kopyalanır; null ve 0–10000 dışındaki sinyaller reddedilir. Bu task dış servislerden
sinyal toplamaz; ilerideki yetkili use-case, kaynak veriyi doğrulayıp bu porta dönüştürmelidir.

`rules-v1` teknik başlangıç politikası: favori yakınlığı %40, puan yakınlığı %30,
tür yakınlığı %20, tamamlanan oyun yakınlığı %10. Bunlar ürün başarısıyla kalibre edilmiş
ağırlıklar değildir; sürümle sabitlenen, test edilebilir başlangıç kurallarıdır.
Her katkı `signal * weight / 100` tamsayı aşağı yuvarlamasıdır. Eksik sinyal sıfır katkıdır;
kalan ağırlıklar yeniden ölçeklenmez. Sıfır skorlu aday önerilmez. Skor gerekçe katkılarının
toplamıdır; nedenler enum sırasıyla, sonuçlar skor azalan ve eşitlikte UUID metni artan sıralanır.
Veri yoksa sonuç boştur; katalog/oyun/FPS uydurulmaz.

`RecommendationExecutionService`, seçilen algoritma sürümünü girdiden doğrular; sonucu
aday üyeliği, tekillik, limit, sıralı rank ve azalan skor bakımından denetler.
`Result` sunucunun ürettiği runId, algoritma sürümü, UTC/mikrosaniye zamanı ve değişmez
öğeleri taşır. Başka algoritma aynı `version/rank` portunu uygulayıp aynı çıktıyı üretir;
testte ikinci uygulama ile bu sınır doğrulanır. Python çalışma zamanı henüz seçilmedi;
bu task Python çalıştırıldığı iddiasında bulunmaz (mimari §10).

## Gözlemlenen olaylar ve ölçüm anlamı

Sunucu yalnız tamamladığı motor çalışmasını kaydeder: `GENERATED`, `EMPTY`, `UNAVAILABLE`.
Ölçülenler: çalışma sayısı, üretilen öğe sayısı, toplam kural skoru, boş sonuç ve
bağımlılık/algoritma kullanılamama sayısı. Skor toplamı ve sonuç sayısı algoritma çıktısını
incelemeye yarar; doğruluk, kullanıcı memnuniyeti, CTR veya FPS güvenilirliği değildir.
Algoritma kullanılamazsa tipli hata yeniden fırlatılır; sahte fallback yoktur. Analitik
DB yazımı başarısızsa başarı yanıtı üretilmez. Geçersiz algoritma çıktısı başarı metriği oluşturmaz.

Motorun çalışması **gösterim veya tıklama sayılmaz**. TASK 2'deki gerçek, sahibi doğrulanmış
feedback mutasyonu tamamlanmadan feedback olayı ölçülmez. Gelecekteki analitik üreticisi,
gerçek iş transaction'ının sonucuna bağlanmalı ve aynı işlem kimliğiyle tekilleştirilmelidir;
istemcinin bildirdiği keyfi olay sayısı kabul edilmez. Bu taskta genel event ingest API'si yoktur.

## Saklama ve mahremiyet

V11 `recommendation_observations`: yalnız rastgele çalışma kimliği, sürüm, sonuç durumu,
UTC zamanı ve üç sayısal sayaç. Kullanıcı/hedef kimliği, token, email, ham sinyal, oyun listesi,
profil veya FPS örneği saklanmaz/loglanmaz. Kişisel öneri geçmişi tablosu bu taskta açılmaz.
Çalışma kimliği sonuçla ilişkilendirilebilir; tabloya kullanıcı ilişkisi veya public döküm eklenmez.

PK runId ile tekilleştirme PostgreSQL `ON CONFLICT DO NOTHING` ve kayıt içeriği karşılaştırmasıyla
transaction içinde yapılır. Aynı olay tekrar/eşzamanlı gelirse tek sayılır; farklı içerik çakışmadır.
Idempotency süresi saklama penceresidir: 30 günden eski veya 30 saniyeden ilerideki olay reddedilir.
Özet sorgusu en çok 30 gün ve saklama penceresiyle sınırlıdır; expired kayıtlar özette görünmez.
Zamanlayıcı dakikada en çok 1000 eski satır siler; yoğun birikimde fiziksel silme gecikebilir,
servis kapalıyken çalışmaz. Yedek saklama politikası servis dışındadır.

Yeni runtime, bağımlılık, ENV, servis/DB veya güven modeli eklenmedi. Kullanıcı/rol/ownership
gerektiren yeni HTTP yüzeyi açılmadı. Başlangıç Spring Security davranışının 401 ve CSRF 403
testleri, gerçek JWT/RBAC/IDOR kabulü yerine geçmez. İleride HTTP use-case açılırken mevcut
Auth sözleşmesiyle gerçek principal ve ProblemDetail eşlemesi ayrıca uygulanmalıdır.
