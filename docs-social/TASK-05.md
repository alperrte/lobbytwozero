# Social Service — Frontend: Arkadaşlık Ekranları (TASK-05)

> Tarih: 2026-11-07 · Sorumlu: Umut · Servis: `social-service` (frontend) · Kapsam: SOC-01…SOC-11, SOC-14'ün arayüzü · Durum: **1. aşama (tasarım), 2. aşama (gerçek API bağlantısı), 3. aşama (kontrol düzeltmeleri) ve 4. aşama (gerçek yığınla canlı deneme ve düzeltmeleri, 2026-11-08) tamam**
>
> Kaynak: ekip taslağı (Arkadaşlarım, Arkadaşlık İstekleri, Engellenen Kullanıcılar), `docs/web-rules.md`, `docs/ltz-palette-and-tokens.md`, LTZ tasarım standardı. Backend, gateway ve başka servislerin kodunda değişiklik **yapılmadı**. Yeni uç eklenmedi; tüketilen uçlar `docs/api.md`'de zaten "Tüketici: Frontend" olarak kayıtlı.

## 1. Sonuç

| Ekran | Adres (TR / EN) | İçerik |
| --- | --- | --- |
| Arkadaşlar | `/tr/arkadaslar` · `/en/friends` | Kart ızgarası, arama, Tümü/Çevrimiçi/Oyunda süzgeci, ⋯ menüsü (Profili Gör: yakında, Arkadaşlıktan Çıkar, Engelle), "Daha fazla yükle". Arama/süzgeç yalnız yüklenen sayfalarda çalışır; yüklenmemiş arkadaş varken bu açıkça yazılır. Yan panel: gelen istek özeti, davet kodu, kodla ekleme. |
| Gelen İstekler | `/tr/arkadaslar/istekler` · `/en/friends/requests` | Kabul Et / Reddet, gönderilenlerin özeti. Yan panel: Arkadaş Ekle, Davet Kodun, Sosyal gizlilik (yakında). |
| Gönderilenler | `/tr/arkadaslar/gonderilenler` · `/en/friends/sent` | Bekliyor + İsteği İptal Et. |
| Engellenenler | `/tr/arkadaslar/engellenenler` · `/en/friends/blocked` | Onaylı Engeli Kaldır, "Engelleme nasıl çalışır?" paneli. Arama ve önizleme rozeti yoktur (bkz. §7). |

Kenar çubuğundaki **Arkadaşlar** gerçek bağlantıdır. Dört sayfa `(dashboard)` grubundadır, `noindex` ve sitemap dışıdır. Sayfalar yalnız oturum açmış kullanıcıya çizilir; misafir `/giris?next=<sayfa>` adresine yönlenir.

## 2. Veri kaynakları

| Bilgi | Kaynak | Uç |
| --- | --- | --- |
| Arkadaş, gelen/giden istek, engel, davet kodu | **social-service (gerçek)** | `/api/v1/social/**` (14 uç; serviste 16 public uç var, kullanılmayan 2'si §9'da) |
| Ad, kullanıcı adı, avatar | **user-service (gerçek)** | `POST /api/v1/user/profiles/batch` (en çok 50 kimlik/istek) |
| Çevrimiçi/oyunda durumu, oynanan oyun, ortak arkadaş sayısı | **Önizleme verisi** (kaynak yok) | — |
| Sekme sayıları | Listelerden türetilir | Her liste `limit=50` ile okunur; fazlası "50+" |

- **Önizleme verisi** ürün kararıdır. Değerler gerçek kullanıcı kimliğinden deterministik türetilir, yani aynı kişi her yüklemede aynı görünür. Arkadaşlar, Gelen İstekler ve Gönderilenler başlığında "Önizleme verisi" rozeti bulunur (`SOCIAL_PREVIEW_DATA`, `web-rules.md` §1). Engellenenler sayfası önizleme verisi göstermediği için rozet taşımaz. Oyun adları kurgusal, kapaklar geometrik yer tutucudur.
- **Ad yoksa:** user-service silinmiş/yasaklı hesapları ve aranızda engel olanları atlar. Bu yüzden **Engellenenler listesi hiçbir zaman ad almaz**, bu liste için toplu profil isteği de hiç atılmaz ve "Engellenen oyuncu #<kimliğin son 8 karakteri>" gösterir. Diğer listelerde yedek ad "Bilinmeyen oyuncu #…" olur. user-service hata verirse sosyal liste yine açılır, yalnız adlar yedeğe düşer.
- **Avatar:** yalnız user-service medya yolu kabul edilir (`/api/v1/user/media/<32 hex>(-s).(jpg|png|gif)`). Fotoğraf yoksa kimlikten türeyen SVG portre çizilir.

## 3. Mimari

Akış: `app/.../page.tsx → features/social/components → hooks → services → shared/lib/api/client.ts`.

| Dosya | Görev |
| --- | --- |
| `services/social-http.ts` | Gateway istekleri. Yazmalarda çift gönderimli CSRF (`X-XSRF-TOKEN`); çerez yoksa `GET /auth/csrf`. Gateway `403 CSRF_TOKEN_INVALID` dönerse çerez yenilenir ve istek **bir kez** tekrarlanır (istek servise ulaşmamıştır). Başka hiçbir hata otomatik tekrarlanmaz. 15 sn zaman aşımı. |
| `services/social-api.ts` | 15 social ucu. Yol parametreleri `encodeURIComponent` ile kaçırılır; davet kodu normalleştirilir. |
| `services/user-profiles.ts` | Toplu profil kartı: 50'lik parçalar, tekilleştirme, avatar yolu doğrulaması, hata durumunda boş sonuç. |
| `services/social-service.ts` | Hook'ların tek girişi: kayıt + profil + önizleme birleştirme, sayaç türetme. |
| `services/social-preview.ts` | Önizleme verisi (deterministik). |
| `services/social-mock-store.ts`, `social-mock-data.ts` | **Yalnız testlerde** kullanılan sahte social-service (aynı imza ve hata kodları). |
| `components/social-session-gate.tsx` | Oturum doğrulanmadan sosyal istek atılmaz. |
| `hooks/social-revision.ts` | Başarılı her işlem listeleri, rozetleri ve yan panelleri tazeler. |

Sayfalama imleçlidir: taslaktaki "1 2 3 4" yerine "Daha fazla yükle" kullanılır. Davet kodu gerçek biçimdedir (10 karakter, `ABCDE-FGHJK` gruplu gösterim). Taslaktaki "kod girince kişi önizlemesi" adımı yoktur, çünkü social-service'te kodla kişi arama ucu bulunmuyor.

## 4. Ortak (shared) değişiklikler

| Dosya | Değişiklik |
| --- | --- |
| `shared/components/ui/avatar.tsx` | Yeni. Fotoğraf ya da deterministik SVG portre. |
| `shared/components/ui/confirm-dialog.tsx` | Yeni. Yerel `<dialog>` onay penceresi: odak "Vazgeç"te başlar, Esc ile kapanır, işlem sürerken kapatılamaz. |
| `shared/components/ui/button-styles.ts` | Yeni. `primary` / `secondary` / `ghost` düğme sınıfları. |
| `shared/components/ui/icons.tsx` | Eklenen ikonlar: `userPlus`, `userMinus`, `send`, `ban`, `shield`, `copy`, `more`, `clock`, `share`, `refresh`, `message`. |
| `app/globals.css` | Yeni token: `--ltz-scene-ink` (Dark `#0b0b0f`, Light `#17171b`; paletin içinde). |
| `shared/lib/i18n/routing.ts` | Dört rota (TR/EN slug). |
| `shared/config/navigation.ts` | Arkadaşlar öğesi etkin. |
| `messages/{tr,en}.json` | Yeni `Social` namespace'i (yalnız ekleme). |
| `navigation.test.ts`, `app-shell.test.tsx` | "Yakında" sayısı 11 → 10, bağlantı sayısı 2 → 3. |

## 5. Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| `npm run lint` | **PASS** |
| `npm run typecheck` | **PASS** |
| `npm run test` | **PASS**: 3. aşamadan sonra 744 geçti, 1 atlandı (önceden de atlanıyordu). 2. aşamada 741 idi. |
| `npx playwright test e2e/social.spec.ts` | **PASS 9/9**. Sahte Gateway yazmalarda CSRF'i gerçekten denetler. Kapsam: misafir yönlendirmesi, user-service adları, CSRF'li kabul, davet koduyla istek, onaylı engel kaldırma, slug'lar, 375/768/1440 px'de taşma yok. |
| `npm run build` | **PASS** |
| 3. aşama tekrar | 2026-11-08: görsel düzeltmelerden (§7 madde 6–10) sonra lint, typecheck, `npm run test` (744 geçti, 1 atlandı), `e2e/social.spec.ts` (9/9) ve `npm run build` yeniden **PASS**. Tüm E2E paketi yeniden çalıştırılmadı. `e2e/social.spec.ts` 6 çalıştırmanın 4'ünde 9/9 geçti; kırılan 2 çalıştırma ağır işlerin hemen ardındaydı, her seferinde farklı testler zaman aşımına uğradı (sayfa oturum kapısında "Yükleniyor…" kaldı). Tek işçiyle (`--workers=1`) 2/2 tam geçti. Kod hatası değil, dev sunucusunun yük altındaki yavaşlığı olarak değerlendirildi. |
| Tüm E2E paketi (2. aşama) | **14 FAIL / 113 PASS**. Aynı 14 hata değişiklik içermeyen HEAD'de de tekrarlandı (`git archive HEAD` ile ayrı klasörde): `home`, `navbar`, `shell`, `game` senaryoları. **Bu görevle ilgisizdir.** |
| Gerçek yığınla canlı deneme | **PASS** (4. aşama, 2026-11-08): 20 servis `node ops/dev.mjs --all` ile ayakta, üç dev hesabıyla dört sayfa ve tüm yazmalar denendi (§8). |
| 4. aşama tekrar | `npm run lint`, `npm run typecheck`, `npm run build` **PASS**; `npm run test` **748 geçti, 1 atlandı** (4 yeni test); `e2e/social.spec.ts --workers=1` **9/9**. |
| `pre-push full dev` | **SAFE TO COMMIT** (2026-11-08): Git/Secret, Backend Tests/Build/Coverage, yeni kalite ihlali (0), Docker, 18 servis HTTP health hepsi PASS. Frontend bu tabloya dahil değildir (yukarıdaki satırlar). |
| Sonar (`sonar.cmd scan dev`) | Backend Quality Gate **FAIL**: yalnız `new_violations` 86 > 0 (coverage %93,3 ve tekrar %2,28 geçti). Runner yalnız backend'i tarar; bu görev backend'e dokunmadığından bulguların hiçbiri bu değişiklikten gelmez. Dağılım: community 27, social 21, mail 11, auth 9, library 9, hardware 6, user 2, review 1 (§6 #8). |

## 6. Açık noktalar

| # | Konu | Not |
| --- | --- | --- |
| 1 | Gerçek yığınla canlı deneme | **Kapandı** (4. aşama, §8). |
| 2 | Sekme sayıları | Her tazelemede dört liste `limit=50` okunur. Trafik sorun olursa social-service'e `GET /api/v1/social/summary` eklenebilir (`docs/api.md` kaydıyla). |
| 3 | Durum / oyun / ortak arkadaş | Presence kaynağı ve ortak arkadaş hesabı yazılınca `social-preview.ts` kaldırılır, `SOCIAL_PREVIEW_DATA = false` yapılır. |
| 4 | Engellenenlerde ad yok | user-service engel ilişkisinde kartı bilerek gizliyor. Engelleyen kişinin kendi listesinde ad görmesi isteniyorsa user-service'te ayrı bir kural gerekir (sahibi: Kerem). |
| 5 | Mesaj Gönder / Profili Gör / Gizlilik ayarları | İlgili ekranlar yayınlanana kadar pasif ve "Yakında" işaretli. |
| 6 | Önceden kırık 14 E2E testi | Kabuk/navbar/game/home sahiplerine bildirilmeli. |
| 7 | `frontend/AGENTS.md` | `next dev` her açılışta yeniden üretiyor. Bu görevin parçası değil; commit'e alınıp alınmayacağı ekip kararı. |
| 8 | social-service Sonar bulguları (backend, önceki commit'ler) | 21 bulgu: S5778 ×9, S5838 ×5 (testler), S6831 ×2 (`JwtConfig`), S7467 ×2, S9142, S6213 ve **S6809 CRITICAL** (`FriendshipService:59`, aynı sınıftan `@Transactional` çağrısı; transaction'ın uygulanıp uygulanmadığı incelenmeli). Ayrı backend işi. |
| 9 | Davet kodu yokken konsolda 404 | `GET /invite-code` "kod yok" durumunu `404 INVITE_CODE_NOT_FOUND` ile bildirir; arayüz doğru davranır, tarayıcı konsolu kırmızı satır yazar. Kaldırmak için social-service sözleşmesi değişmeli (ör. 200 + boş gövde). |
| 10 | Davet kodu kullanım sayacı | İptal edilen istekler de kullanımdan sayılır (canlıda 3/50). Backend davranışı; ürün kararı gerekirse social-service'te değişir. |

## 7. 3. aşama: kontrol düzeltmeleri (2026-11-08)

Frontend'i "bitti" saymadan önce kod backend sözleşmesiyle tek tek karşılaştırıldı: uç yolları, DTO alanları, `SocialErrorCode` kodlarının tamamı, davet kodu alfabesi, sayfa sınırı (50), toplu profil sınırı (50) ve avatar dosya adı kuralı birebir uyuşuyor. Bulunan sorunlar düzeltildi:

| # | Sorun | Düzeltme | Test |
| --- | --- | --- | --- |
| 1 | "Yeni kod oluştur" iki adımdır (`DELETE` + `POST`). İptal geçip üretim düşerse kart iptal edilmiş kodu göstermeye devam ediyordu. | Hata durumunda davet kodu yeniden okunur; kod yoksa "Davet kodu oluştur" görünür (`invite-code-card.tsx`). | `social-views.test.tsx`: düzeltme olmadan kırıldığı doğrulandı. |
| 2 | Arkadaş araması ve durum süzgeci yalnız yüklenen ilk 12 arkadaşta çalışıyordu, bunu kullanıcıya söylemiyordu. | Yüklenmemiş arkadaş varken arama/süzgeç açıksa "yalnız yüklenen N arkadaşa uygulanıyor" notu çıkar (`friends.partialResults`). Liste gövdesi ve süzgeç ayrı bileşenlere ayrıldı (Sonar bilişsel karmaşıklık ve iç içe ternary uyarıları). | `social-views.test.tsx` |
| 3 | Engellenenlerde arama işlevsizdi: ad hiç gelmediğinden yalnız "#son 8 karakter" ile eşleşiyordu. Bu liste için atılan toplu profil isteği de her zaman boş dönüyordu. | Arama ve "eşleşme yok" durumu kaldırıldı (kullanılmayan 4 çeviri anahtarı silindi); `listBlocks` user-service'e sormaz. | `social-views.test.tsx`, `social-service.test.ts` |
| 4 | Engellenenler sayfasında önizleme verisi olmadığı halde "Önizleme verisi" rozeti vardı. | Rozet kaldırıldı. | `social-views.test.tsx` |
| 5 | Bu raporda social uç sayısı "15" yazıyordu. | 14 olarak düzeltildi (15'incisi user-service toplu profil ucu). | — |
| 6 | Kartta oyun adı "Sky…" diye kesiliyordu. Kök neden: Tailwind 3 sözdizimi `max-@[25rem]:` Tailwind 4'te derlenmiyordu, dar kartta "Mesaj Gönder" yazısı gizlenmiyordu. Izgara da ekran genişliğine (`xl`) bağlıydı, 1280 px'te kart ~275 px'e iniyordu. | `@max-[25rem]:` (dar kartta yalnız ikon) ve liste genişliğine bağlı ızgara (`auto-fill`, kart en az 18rem). | Ekran görüntüsü ölçümü: 375–1920 px arasında kesilen oyun adı yok. |
| 7 | Sekme çubuğu yan panelli masaüstünde sığmıyordu: 1440 px'te "Engellenenler" kenardan kesiliyor, 1024 px'te üç sekme görünmüyordu. | Yatay kaydırma kaldırıldı: 2×2 ızgara, geniş alanda (≥48rem) tek satır. Çok dar alanda ikon gizlenir, etiket gerekirse kısaltılır (erişilebilir ad tam). | 375–1920 px, TR/EN: kesilen sekme yok. 320 px'te üç TR etiketi "…" ile kısalır. |
| 8 | Mobilde başlık alt yazısı "Arkadaş Ekle" düğmesinin yanında kelime kelime kırılıyordu. | Metin bloğu en az 18rem; dar ekranda düğme alt satıra iner ve tam genişlik olur. | Ekran görüntüsü (375 px). |
| 9 | Durum süzgeci mobilde taşıyordu ("Oyunda" yana kaydırılmadan görünmüyordu). Araç çubuğu ekran genişliğine (`sm`) bağlı olduğundan yan panelli dar sütunda arama kutusu da ezilebiliyordu. | Araç çubuğu kapsayıcı genişliğine bağlandı: dar alanda arama üstte, süzgeç altta üç sütun; geniş alanda (≥40rem) yan yana. Kaydırma yok, çok dar alanda süs ikonları gizlenir. | Ölçüm 320–1920 px, TR/EN: taşan düğme yok, arama kutusu en az 262 px. 320 px'te iki TR etiketi "…" ile kısalır. |
| 10 | "Davet koduyla ekle" alanı alt alta dizildiği genişlikte (1024–1279 px) eziliyordu: `flex-1` sütun yönünde yüksekliği sıfırlıyordu. | `flex-1` yerine `grow`. | Ölçüm: 320–1920 px'te alan yüksekliği 44 px. |

## 8. 4. aşama: gerçek yığınla canlı deneme (2026-11-08)

Yığın `node ops/dev.mjs --all --force` ile kuruldu (20 servis, Gmail SMTP). Playwright ile üç ayrı oturum: `user@ltz.com` (A), `creator@ltz.com` (B), `developer@ltz.com` (C).

| Senaryo | Sonuç |
| --- | --- |
| Giriş, dört sayfa, sekme sayaçları (TR ve EN) | Tüm GET'ler 200; EN başlıkları doğru, yatay taşma yok. |
| Kodsuz kullanıcı: "Davet kodu oluştur" | `POST /invite-code` 201, kod gösterildi. |
| Davet koduyla istek (küçük harf/boşlukla da) | 201; aynı kişiye ikinci istek 409 "zaten bekleyen isteğin var"; geçersiz kod 404 doğru mesaj. |
| Gönderilenler → İptal, yeniden gönder | 200 / 201, sayaçlar güncellendi. |
| Gelen İstekler → Kabul / Reddet | 200 / 200; arkadaşlık iki tarafta da göründü, boş durum metni doğru. |
| ⋯ → Engelle (onay penceresi) | 201; arkadaşlık bitti. Engellenen B'nin isteği 403 "istek alamıyor". |
| Engellenenler → Engeli Kaldır | 204; boş durum. Engellenen kişi adsız görünür (§2, §6 #4). |
| Ad / kullanıcı adı | user-service toplu kartından geldi (`@creator`, `@developer`). |

Canlı denemede bulunan ve düzeltilen hatalar:

| # | Sorun | Düzeltme | Test |
| --- | --- | --- | --- |
| 11 | Başarılı istekten sonraki 7 sn içinde hata alınırsa alttaki "istek gönderildi" bandı, formdaki hata mesajıyla birlikte görünüyordu (çelişkili iki mesaj). | `social-announcer.tsx`: `useClearAnnouncement()`; `add-by-code-form.tsx` her gönderimde eski bandı kapatır, başarıda yeniden duyurur. Context değeri bir kez kurulur (Sonar S6481). | `social-views.test.tsx` (+2; düzeltme olmadan kırıldığı doğrulandı). Canlıda tekrar denendi. |
| 12 | Çevrimdışı kişide oyun "Arkadaşıyla oynuyor" / "Bu oyunu oynuyor" yazıyordu. `Presence.game` "şu an ya da en son oynanan" oyundur; var olan `game.lastPlayed` çevirisi kullanılmıyordu. | `GameLine` `lastPlayed` alır; arkadaş kartı ve istek satırı çevrimdışıyken "En son oynadığı" yazar. | `social-offline-game.test.tsx` (+2; düzeltme olmadan kırıldığı doğrulandı). Canlıda tekrar denendi. |

## 9. Bağlanmamış yerler (sonraki işler)

**A. Backend hazır, frontend bağlamadı**

| Ne | Uç | Not |
| --- | --- | --- |
| Kullanıcı adıyla arama + istek gönderme | `GET /api/v1/user/search?q=` (USER-11) + `POST /api/v1/social/friend-requests {receiverId}` (SOC-01) | Şu an arkadaş eklemenin tek yolu davet kodu. |
| Sosyal gizlilik ayarı (arkadaş listesi görünürlüğü) | user-service `/api/v1/user/me/privacy` (`friendsVisibility`) | Arayüzde "Yakında". Frontend'de `/settings` sayfası yok. |
| Başka kullanıcının arkadaş listesi (SOC-12) | `GET /api/v1/social/users/{userId}/friends` | Profil sayfası gerekir. |

**B. Kaynağı olmayan, önizleme verisiyle dolan alanlar** (`social-preview.ts`; kaynak gelince `SOCIAL_PREVIEW_DATA = false`): çevrimiçi/oyunda/çevrimdışı durumu ve son görülme; oynanan oyun, etkinlik türü, oyun kapağı; ortak arkadaş sayısı; bunlara bağlı Çevrimiçi/Oyunda süzgeci ve sayıları.

**C. Başka ekrana bağlı, pasif "Yakında"**: Mesaj Gönder (messaging-service backend'i var, frontend ekranı yok); Profili Gör (user-service profil uçları var, frontend profil sayfası yok).

**D. Backend'de eksik ya da PENDING**: sayım ucu (sekme rozetleri 4×`limit=50` ile türetiliyor); engellenenlerde ad (user-service kuralı, sahibi Kerem); SOC-13 bildirim teslimi (frontend'de bildirim ve kenar çubuğu istek rozeti yok); davet koduyla kişi önizlemesi ucu; sunucu tarafında arkadaş araması.
