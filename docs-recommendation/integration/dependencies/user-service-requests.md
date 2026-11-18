# TASK 4 — User aday ve izinli tercih sözleşmesi talebi

2026-11-15 · REC-13/14 · Sahip: **Kerem** · **User tarafı UYGULANDI (2026-11-18); Recommendation tüketimi ve REC-14 Social tarafı AÇIK**.
Aşağıdaki "Mevcut kanıt" ve "İstenen" bölümleri özgün talebin kaydıdır; uygulama durumu en alttaki "Uygulama" bölümündedir.

## Mevcut kanıt

- `PublicProfileController`: profil detayını tek kimlikle okur; batch ve search yalnız kart döner.
- `ProfileCardsResponse.Card`: userId, username, displayName, avatar. Tür/platform, öneriye
  aday olma izni, eligibility ve durumun Recommendation için yeterli kanıtı yoktur.
- `GET /api/v1/user/search`: en az iki karakterli isim öneki, en çok 20 sonuç; sayfalama yok.
  Bu uç oyun zevkine dayalı izinli aday keşfi değildir. Alfabe/isim taraması yapılmayacak.
- `POST /api/v1/user/profiles/batch`: 1–50 kimlik, olmayan/engelli/devre dışı hesapları atlar;
  temel kartta gizlilik ayarını uygulamaz ve SUSPENDED hesabı gösterebilir. Kart görünürlüğü,
  öneriye aday olma/oyun tercihlerini işleme izni sayılmaz.
- Tek profil detayında üyeye favoriteGenres/platforms görünebilir. Bu durum bütün kullanıcıları
  aday olarak keşfetmeye, gizli library/rating toplamaya veya üçüncü taraf davranış analizi
  yapmaya yetki veren bir sözleşme değildir; N+1 profil okuması eklenmedi.
- Mevcut `/me/consents` recommendation rızası owner'ın kişisel oyun önerisi için favori/bitirilen
  verisini işlemeye yöneliktir. Başkasına oyuncu/arkadaş olarak önerilme izni diye genişletilmedi.

## İstenen en dar kabiliyet

Kerem'in belirleyeceği API ile doğrulanmış izleyiciye göre izinli aday keşfi ve gerekliyse
batch tercih/eligibility değerlendirmesi. Yeni route/scope burada icat edilmez.

- Owner kimliği güvenilir JWT/servis bağlamından; istemcinin userId iddiasından alınmamalı.
- Aday olma ve izinli tercih alanlarının işleme/gösterme amacı açıkça tanımlanmalı.
- Yalnız izin verilen normalize tür/platform sinyalleri ve kanonik kullanıcı kimliği;
  e-posta, ham özel kütüphane, kişisel rating geçmişi, bağlı hesap token'ı dönmemeli.
- Kendisi, silinmiş/devre dışı/yasaklı veya öneriye kapalı hesaplar, erişilemeyen adaylar
  atlanmalı; bulunmama/gizlilik/engel nedeni birbirinden ayırt ettirilmemeli.
- Aday penceresi sınırlı, sayfalama/cursor deterministik olmalı; en çok 50 kayıt/sayfa
  başlangıç talebidir, kesin limit/total/freshness semantiği owner tarafından onaylanmalı.
- İzin geri çekilmesi ve account state değişikliği sonraki üretimde yansıtılmalı; cache varsa
  gizlilik iptali semantiği açık olmalı. Öneri döndürmeden önce Social eligibility de gerekir.
- İsim önekiyle kullanıcı tarama veya serbest candidateIds ile private enumeration yaratılmamalı.

Auth izin modeli değişikliği gerekiyorsa Kerem'in açık sahipliğiyle ayrıca tasarlanmalıdır.
Recommendation mevcut Library/Performance grant'ını User verisine erişim için kullanmaz.

## Beklenen owner kabul kanıtı

İzinli/izinsiz aday, boş tercih, self, iki kullanıcı ayrımı, silinmiş/disabled/suspended hesap
politikası, iki yönlü engel, sınır/cursor/tekrar ve timeout testleri; gerçek DTO + `docs/api.md`
kaydı. Ardından Recommendation batch tüketimi, deterministik sıralama/gerekçe ve privacy
testleri eklenebilir. Şu an bu entegrasyon ve olumlu aday önerisi **NOT IMPLEMENTED**.

## Uygulama (2026-11-18) — User tarafı (User sahibinin yetkisiyle)

Karar: ayrı bir "aday olma" rızası, migration ya da UI **eklenmedi**; mevcut `recommendation_granted` rızası
yeniden kullanıldı (sadelik kararı, Alper). Bu, yukarıdaki "rıza başkasına önerilme izni olarak genişletilmedi"
notunu bilinçli olarak değiştirir: rıza artık hem kişisel öneriyi hem de başkalarına benzer oyuncu adayı olarak
görünmeyi kapsar. **Açık nokta:** rıza ekranındaki açıklama metni (tr/en) henüz güncellenmedi; kullanıcıya bu
kapsamın söylenmesi için tek cümlelik metin değişikliği gerekir (frontend, onay bekliyor).

| İstenen | Karşılık |
| --- | --- |
| Güvenilir bağlam | `GET /internal/v1/accounts/{accountId}/recommendation-candidates`; `svc:recommendation-service`, `candidates:read`, `account_id` = yoldaki izleyici; izleyici yalnız token'dan |
| Yeni grant | Auth: `recommendation-service.targets.user-service=candidates:read` (hesaba bağlı kapsam) |
| Dar alan | yalnız `userId`, `playerTypes`, `favoriteGenres`, `favoritePlatforms` |
| Atlanacaklar | self, rızasız, onboardingsiz, ACTIVE olmayan, iki yönlü engelli; sebep ayırt edilemez (boş/eksiksiz sayfa) |
| Sınırlı pencere/cursor | `limit` 1–50 (varsayılan 20), `user_id` keyset cursor, sunucu tarafı genre/platform süzgeci; isim taraması ve serbest `candidateIds` yok |
| Fail-closed | Social engel yansıması güvenilir değilse (`ltz.user.social.projection-enabled=false`, varsayılan) hiç aday dönmez |
| İzin geri çekme | Her istekte canlı DB okunur; önbellek yok, `no-store` |

Kanıt: User 276/276 (yeni `RecommendationCandidateTest` 10 + `RecommendationCandidateFailClosedTest` 1), Auth
`ServiceClientConfigTest` 12 + `ServiceAudienceTokenTest` 20 (hedefli). `docs/api.md` USER bölümüne kayıt eklendi.
Çalıştırılmadı: Checkstyle/PMD ratchet, merkezi coverage gate, Sonar, canlı çok servisli E2E.

**Hâlâ açık:** (1) Recommendation REC-13 tüketicisi; (2) canlı ortamda `projection-enabled=true` kararı (Social
sahibi) — kapalıyken özellik boş döner; (3) REC-14 için Social friend/block batch API'si (Umut); (4) rıza metni.
Bu yüzden bu dosya `done/` klasörüne **taşınmadı**.
