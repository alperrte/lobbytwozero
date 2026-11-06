# HW-USER-REQ-01 — Yorum görünürlüğü ve Social yansıması

Tarih: 2026-11-06. Gönderen: Alper / Hardware. Sorumlu: Kerem / User.
Durum: REQ-02 DONE — tüketici + izole gerçek User kabulü VERIFIED;
REQ-01 operasyonel OPEN (Kerem/Umut).

## Güncel cevap ve Hardware karşılığı

PR #67 (`0199494`) ile Kerem'in REVIEW_AUTHOR sözleşmesi yerel koda geldi.
[Kerem'in cevabı](../../../docs-user/integration/services/hardware-service-talepleri/hardware-cevap-01.md),
[Hardware uygulaması ve güncel doğrulama](../user-review-integration.md).
Profil paylaşımı için yeni User geliştirmesi yoktur. Ürün yorumları HARDWARE
profil ayarından bağımsızdır; REVIEW_AUTHOR kararı SQL sayfalama/toplam/puan
hesabından önce uygulanır. User/Social yansımasının güvenilir ilan edilmesi
Kerem/Umut'ta bekler; gerçek ayar bu görevde değiştirilmez.

Aşağıdaki REQ-02 soruları ilk talebin tarihçesidir; güncel cevap yukarıdadır.

## Tamamlanan Hardware tüketicisi

`UserServiceHardwareVisibilityPolicy`, mevcut `POST /internal/v1/privacy/decisions`
ucuna `{viewerId, field:"HARDWARE", ownerIds:[ownerId]}` gönderir.
Kimlik Hardware'da doğrulanan kullanıcıdan gelir. Auth client-credentials ile
`clientId=hardware-service`, `audience=user-service`, `privacy:read` kullanılır;
bu hedef için `accountId` gönderilmez. Yanıttaki owner/allowed/friends/setting
alanları doğrulanır; her belirsizlikte paylaşım reddedilir.

Profil paylaşımı için User'da yeni endpoint veya kod değişikliği gerekmiyor.
Gerçek ENV ve User kodu bu görevde değiştirilmedi.

## REQ-01 — Paylaşımın ortak ortamda açılabilmesi

User'ın `ltz.user.social.projection-enabled=false` varsayılanında sahibi olmayan
izleyici için `allowed=false` dönmesi doğru ve Hardware'da aynen korunur.
Bu ayarı Hardware açmaz. Kerem/Umut, arkadaşlık ve çift yönlü engelleme başlangıç
aktarımı ile güncel olayların tamamlandığını doğrulamalı; bundan sonra User sahibi
yansımanın güvenilir olduğunu ilan etmelidir. Gerçek hesaplarla deney yapılmaz.

Kabul: sentetik owner/viewer ile PUBLIC izin, PRIVATE ret, FRIENDS yalnız arkadaş,
iki yöndeki engelde ret ve güvenilir olmayan yansımada ret. Hardware yerel profil,
kategori ve alan kurallarını global karardan sonra ayrıca uygular.

## REQ-02 — HardwareReviewAudiencePolicy için UGC kararı

Hardware yorum arayüzü şu an `Decision(allowOthers, blockedAuthors)` ister.
User mevcut API'si tüm engel listesini dökmez; yalnız verilen sahipler için
HARDWARE profil bölümü görünürlüğünü değerlendirir. Donanım profilini PRIVATE
yapmanın ürün yorumunu da gizleyip gizlemediği tanımlanmamıştır; bu iş kuralı
varsayılmadı. Yorumlar için mevcut kapalı policy korunur: izleyici yalnız kendi
görünür yorumlarını listeler; başkasının yorum detayına erişemez.

İstenen mutabakat:

- Ürün yorumlarının profil HARDWARE ayarına bağlı olup olmadığını belirtin.
- Hesap durumu ve çift yönlü engellemeyi içeren, güvenilir yansıma yokken reddeden
  UGC kararının sözleşmesini belirleyin. İlk tercih mevcut batch karar yaklaşımında
  yorum yazarlarına özgü açık bir kaynak/alan tanımıdır; yeni uç zorunlu değildir.
- Donanım yorum listesinin sayfalama, toplam ve puan özetinin yalnız izinli
  yorumlardan oluşması korunmalıdır. Bir sayfayı aldıktan sonra elemek doğru
  toplam veya dolu sayfa garantisi vermez. Sınırlı ve eksiksiz bir sorgu kararı
  veya alternatif listeleme yaklaşımı birlikte kararlaştırılmalıdır.
- Kısmi/eksik sahip cevabı, kesinti, 401/403/429/5xx ve timeout erişim açmamalıdır.

Bu talep moderasyon veya bildirim teslimini kapsamaz. Auth/User/Gateway/Social
kodu bu görevde değiştirilmez; sözleşme kararı Kerem'le ayrıca tamamlanacaktır.
