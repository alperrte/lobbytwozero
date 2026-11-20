# TASK 2 — kişisel tür önerisi, geçmiş ve feedback sözleşmesi

2026-11-14 · Alper · LTZ-ALP-REC-02 · **PARTIAL**.

## Kaynak sınırı ve dürüst sonuç

User'ın `GET /api/v1/user/me` yanıtındaki kendi favoriteGenres alanı kullanılır.
Kullanıcı token'ı yalnız aynı istekte User/Game'e iletilir; kalıcı kayıt veya log'a yazılmaz.
Game discovery'nin genre/platform filtre üyeliği eşleşme kanıtıdır; kartta var olmayan tür
metadatası türetilmez. Tür tercihi en çok 10; her türün TITLE/ASC ilk 50 kaydı sorgulanır.
Bu, tüm katalogda en iyi 50 öneri veya eksiksiz keşif iddiası değildir.

Her adayın GENRE_AFFINITY sinyali `eşleşen seçili tür sayısı * 10000 / seçili tür sayısı`dır.
İstek genre içeriyorsa yalnız kullanıcının o tür tercihi seçilir; tercihte olmayan filtre boş döner.
Task 1 rules-v1 ağırlığı %20 olduğundan yalnız bu sinyalle skor en çok 2000'dir; eksik sinyallerin
ağırlığı yeniden ölçeklenmez. Rank skora göre, eşitlikte kanonik UUID metnine göre sabittir.
Neden kodu, aynı katkı skoru ve gerçekten eşleşen türler owner'a döner.

Library `recommendation-signals` gerçek account-bound token ile en çok 10 sayfa/500 kayıt
okunur; total/schema değişimi, eksik sayfa veya tekrar ID fail-closed 503'tür. Favorite veya
completed kayıtlar tekrar öneriden dışlanır. Bu dışlama **REC-02/05 analizinin yerine geçmez**.
Game batch kartlarında tür metadata'sı yoktur; kişisel Review rating batch API'si de yoktur.
REC-02/03/05 BLOCKED ve [owner talepleri](../integration/dependencies/) açıktır. N+1, çapraz DB,
mock production adapter veya genel rating'i kişisel rating gibi kullanma yoktur.

MAIN_GAME olmayan ve adult=false olduğu doğrulanmayan adaylar elenir. Bilinmeyen metadata
uyum/yaş garantisi olarak yorumlanmaz. Cold start boş döner. Body'de bilinmeyen alanlar, örneğin
userId/systemCompatible, 400'dür. Sistem uygunluğu filtresi henüz yoktur; REC-16 bu nedenle PARTIAL.
Tüm yanıtlar PARTIAL/EMPTY ve açık limitations taşır; REC-01/04/17'nin uygulanmış dar kapsamı
boş/eksik veriyle de çalışır.

## Güvenlik ve gerçek Auth kullanımı

Mevcut Auth modeli benimsenir; yeni auth/cookie/CSRF/CORS veya delegasyon modeli tanımlanmaz.
Recommendation, sabit Auth origin'inden bounded JWKS alır; RS256, kid, typ, iss, aud, zorunlu
iat/nbf/exp/sub ve UUID doğrular. at+jwt ömrü en çok 60 saniye, saat toleransı 30 saniyedir.
Kullanıcı ve D4 decoder'ları ayrı bean'dir; varsayılan in-memory kullanıcı/Basic/form login yoktur.
Kanonik beş rolden biri kullanıcı uçları için gerekir. Cookie'yi servis işlemez; Gateway mevcut
CSRF/Origin kontrolünden sonra Bearer'a çevirir. Bearer dışındaki mutasyonlarda CSRF korunur.
İç uçta kullanıcı token'ı, kullanıcı ucunda servis token'ı kabul edilmez.

Kişisel her işlemde User recommendation rızası yeniden okunur; Auth'un mevcut account-bound
Library grant endpoint'i çağrılarak hesabın ACTIVE şartı da denetlenir. Bu, kullanıcı oturum
introspection'ı değildir; logout/session revocation yerel token expiry+tolerans penceresiyle
sınırlıdır. Auth veya User erişilemezse veri açılmaz. Oluşturma kaydından hemen önce kontrol
tekrarlanır. Doğrulanmış rıza reddinde geçmiş silinir; erişim hatası rıza reddi diye varsayılmaz.

Mevcut `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET` anahtarının Recommendation ortamına
aktarımı ve `.env.example` yorum değişikliği kullanıcı tarafından açıkça onaylandı. Yeni anahtar
üretilmedi, gerçek ENV okunmadı/değişmedi; secret boşsa grant alınamaz ve 503 döner.
Grant token'ı her çağrıda alınır, saklanmaz/loglanmaz. Üç hedef origin ENV'leri zaten ortaktır.

Doğrudan generation fanout en çok 26 HTTP çağrısıdır (10 Library sayfası + 10 tür sorgusu dahil).
Kaynaklar istek bazlıdır; TTL snapshot/arka plan kullanıcı token'ı yoktur. HTTP read 5 sn/connect
3 sn (mevcut ENV), redirect/retry yok, JSON yanıt en çok 256 KiB; JWKS 64 KiB. Uçtan uca latency
ve yük testi yapılmadı; p95 veya tam canlı E2E başarı iddiası yoktur.

## V12 ve transaction sınırı

Yalnız recommendation_db'de run/item/feedback; başka servis UUID'leri REF'tir.
JdbcTemplate geçmişi ve JPA V11 gözlemi aynı Spring transaction'ına katılır. Başarısız kota
kontrolünde gözlem ve run birlikte rollback olur; gerçek PostgreSQL testi vardır.
Account advisory transaction lock farklı instance'larda da generation/feedback/D4 yazmasını
seri hale getirir. Geçmiş okuması repeatable-read ve owner koşulludur.

Run başına en çok 50 item; hesap başına 500 run; 30 günlük saklama/okuma penceresi.
History cursor `(created_at,id)` sırasını kullanır; başka hesap cursor'ı 404'tür. Yeni run
eklenmesi devam sayfasını kaydırmaz. Run sonuçları değişmez; canlı katalog içeriği veya oyun
başlığı kopyalanmaz. Geçmiş yalnız kaydedilmiş ID/skor/sıra/tür gerekçeleri ve filtrelerdir.
Expired temizliği dakikada 1000 run, FK cascade ile item/feedback'tir; fiziksel silme backlog'da
gecikebilir. Hesap silmede istisna yoktur: Auth D4 scope/subject/account binding ile kişisel
üç tablo ve rate-limit kaydı temizlenir. İptal/deletion zamanlaması mevcut Auth koordinasyonuna aittir;
Auth worker'ın gerçek çok servisli silme E2E'si bu taskta çalıştırılmadı.

Feedback tek item state'idir: LIKE/NOT_INTERESTED, UNIQUE item_id, version. İlk yazma 0→1;
farklı değere geçişte güncel version gerekir, aksi 409. Aynı değerin tekrarı aynı version'ı döner.
NOT_INTERESTED sonraki generation'da o hesabın oyunu dışlar; LIKE'a uydurma ranking etkisi eklenmez.
Current feedback state güvenle gözlemlenebilir; gösterim/tıklama veya memnuniyet olayı değildir.

Instance başına hesap başına generation 10/dk, diğer kişisel işlemler ortak 120/dk. En çok
10000 aktif pencere; kapasite doluysa 503. Dağıtık global rate-limit iddiası yoktur. Rıza nedeniyle
veri temizlenmesi oran sayacını sıfırlamaz; güvenilir D4 temizliği rate kaydını kaldırır.

## Test sınırı

Auth issuer/JWKS/D4 üretim kaynakları testte geçici dizine derlenip gerçekten çalıştırılır;
başka servis kaynakları değiştirilmez. User/Game/Library HTTP kaynakları dar contract fixture'dır.
Bu testler gerçek servislerin canlı DB'si veya tam Gateway/tarayıcı/çok servis E2E'si değildir.
Pre-push/Sonar final gate master protokolünde TASK 5'e aittir.
