# Messaging Service — Frontend (TASK-09)

> Tarih: 2026-11-23 · Sorumlu: Umut · Servis: `messaging-service` (web) · Dal: `feature/messaging-service` · Kapsam: Mesajlar sayfası; birebir ve grup sohbetleri, gerçek zamanlı mesajlaşma, geçmiş, okundu bilgisi, görsel/GIF, emoji ve tepkiler, yanıt, iletme, silme · **Backend'e dokunulmadı.**
>
> Görev: Mesajlar sayfasının LTZ tasarım standartlarına uygun, responsive ve erişilebilir biçimde tamamlanması. Önceki raporlarda (TASK-02..08 "Frontend yok" maddeleri) bırakılan istemci notları bu görevde uygulandı.

## 1. Sonuç

| İşlev | Gereksinim | Durum | Not |
| --- | --- | --- | --- |
| Sohbet listesi, okunmamış (`99+`), son mesaj önizlemesi, arama | MSG-08, MSG-13 | DONE | Son etkinliğe göre sıralı; arama yüklenmiş sohbetlerde ad/başlıkla süzer (sunucuda sohbet araması yok) |
| Birebir sohbet aç / grup kur | MSG-01, MSG-02 | DONE | "Yeni Sohbet" penceresi (arkadaş seçici); arkadaş kartındaki "Mesaj Gönder" `?to=<userId>` ile birebir sohbeti açar |
| Grup bilgisi: üye ekle/çıkar, rol, ayrıl | MSG-03..05 | DONE | Yalnız yetkili düğmeler gösterilir; yetki kararı sunucuda |
| Metin, emoji | MSG-06, MSG-18 | DONE | Enter gönderir, Shift+Enter satır; 4000 UTF-16 sınırı ve sayaç; emoji seçici istemcide |
| Geçmiş (sayfalı, yukarı kaydırınca) | MSG-07, MSG-10 | DONE | Kaydırma konumu korunur; gün ayraçları; gönderen ve saat |
| Gerçek zamanlı | MSG-09 | DONE | Tek `EventSource`; kopunca yeniden bağlanır, `after` ile eşitler, son sayfayı yeniden okur; bağlantı göstergesi |
| Okundu / kimler okudu | MSG-11, MSG-12 | DONE | Gönderiliyor / gönderildi / okundu / başarısız ayrı; "Mesaj bilgisi" yalnız kendi mesajında okuyanları gösterir |
| Görsel, GIF | MSG-17, MSG-22 | DONE | İstemcide ≤1600 px, ≤1 MB; yükleme ilerlemesi ve iptal; GIF seçici (öne çıkanlar + arama, "Powered by GIPHY") |
| Yanıt, ilet, tepki, menü | MSG-19, MSG-20, MSG-23, MSG-24 | DONE | Hover'da ok, dokunmatikte uzun basma, klavyede Tab + Enter/↓; iletme en çok 5 hedef |
| Benden / herkesten sil | MSG-25, MSG-26 | DONE | Farklı onay metinleri; herkesten sil yalnız `deletableForEveryoneUntil` geçmemişse |
| Sesli mesaj | MSG-21 | PARTIAL | Gelen sesli mesaj oynatılır (`<audio>`); kayıt düğmesi yok (opsiyonel özellik, bkz. §6) |

## 2. Doğrulama (2026-11-23)

| Kontrol | Sonuç |
| --- | --- |
| `npm run typecheck` | PASS |
| `npm run lint` | PASS (0 hata, 0 uyarı) |
| `npm run test` | 195/195 dosya, 1963 test PASS. **Exit 1:** `features/user/components/profile-edit-form.test.tsx` içinde 2 "unhandled error" (`window.matchMedia is not a function`, `shared/components/ui/card-snake.tsx:82`). Origin/main'den gelen CardSnake değişikliği jsdom'da `matchMedia` bulamıyor; messaging koduyla ilgisi yok. |
| Yeni messaging testleri | 8 dosya, 58 test PASS (reducer 10, http 7, stream 4, api 6, hatalar 11, gövde 6, görsel 6, ekran 8) |
| Tarayıcıda görsel kontrol | `next dev` + Playwright, API yanıtları taklit edildi (gerçek yığın kapalıydı). 1440 px ve 375 px, açık ve koyu tema; yatay taşma yok; menü kırpılmadan açılıyor, klavyeyle açılınca odak menüye giriyor |
| Gerçek DEV yığını (Gateway + messaging + auth + social + user) | **NOT VERIFIED** — yığın bu görevde ayağa kaldırılmadı |

Not: Testler bash'ten (`/c/...` yolu) koşunca Vitest iki örnek yükleyip "failed to find the current suite" veriyor. PowerShell'den `C:\...` yoluyla koşulmalı.

## 3. Mimari

`app/[locale]/(dashboard)/messages/page.tsx` → `features/messaging` (component → hook → service → `shared/lib/api/client.ts`).

| Katman | Dosya | Görev |
| --- | --- | --- |
| Servis | `services/messaging-api.ts` | Tüm kullanıcı uçları (`docs/api.md` MESSAGING) |
| | `services/messaging-http.ts` | CSRF çift gönderim, reddedilince bir kez tekrar; yükleme XHR ile (ilerleme + iptal, 401'de oturum yenileme) |
| | `services/messaging-stream.ts` | SSE; kalıcı kapanışta (süresi dolan token) oturumu yeniler, üstel bekleme 1 → 30 sn; 3 başarısızlıktan sonra "bağlantı yok" |
| | `services/image-prepare.ts` | PNG/JPEG/GIF izin listesi, EXIF yönü, 1600 px, PNG büyükse JPEG |
| | `services/messaging-errors.ts` | `ProblemDetail.code` → çeviri anahtarı (sunucu metni gösterilmez) |
| Durum | `hooks/messaging-reducer.ts` | Liste, açık sohbetler ve akış olayları için saf reducer (test edilebilir) |
| | `hooks/use-messaging.tsx` | Sağlayıcı: tek akış, iyimser gönderim, okundu işaretleme (sekme görünürken), yeniden eşitleme |
| | `hooks/use-people.tsx` | user-service toplu profil kartı önbelleği (50/istek, birleştirilmiş); anonim gönderen "Silinmiş hesap" |
| Görünüm | `components/*` | Liste, sohbet, balon + menü, yazma alanı, emoji/GIF seçici, diyaloglar |

Değişen ortak dosyalar: `shared/config/navigation.ts` (Mesajlar öğesi `href: "/messages"`), `shared/lib/i18n/routing.ts` (`/mesajlar`), `shared/components/ui/icons.tsx` (9 ikon: reply, forward, trash, smile, image, gif, arrowLeft, checkDouble, play), `tr.json`/`en.json` (`Messaging` bölümü, `Social.card.messageNamed`), `features/social` (`index.ts` profil kartı ve `socialApi` dışa açıldı; arkadaş kartında "Mesaj Gönder" artık bağlantı).

## 4. Kararlar

| Konu | Karar | Neden |
| --- | --- | --- |
| Yeniden deneme | Kullanıcı eylemiyle, aynı `clientMessageId` | web-rules §7.2; sunucu `200` ile aynı mesajı döner, çift mesaj olmaz |
| Akıştan gelen kendi mesajı | `clientMessageId` ile yerel kopyanın yerine geçer | HTTP yanıtı ile akış olayı hangi sırayla gelirse gelsin tek mesaj |
| Silme/tepki olayı kaçarsa | Yeniden bağlanınca açık sohbetin son sayfası yeniden okunur | Bu olaylar `id` taşımaz, `after` kapsamaz (ADR 0001) |
| Menü konumu | `fixed`; alttaki/üstteki boşluğa göre yön; kayınca mesajı izler | Sohbetin kaydırma alanı menüyü kırpıyordu (tarayıcıda görüldü) |
| Görsel adresi | Yalnız `/api/v1/messaging/conversations/{id}/attachments/{id}` ya da yerel `blob:`; GIF yalnız `https://media*.giphy.com/` | Sunucudan gelse de başka kaynağa istek atılmaz |
| Açık sohbet | Adres çubuğunda `?c=`; mobilde tek panel + geri düğmesi | Paylaşılabilir, geri/ileri çalışır; 375 px'de taşma yok |
| Arkadaş seçici | social-service arkadaş listesi (en çok 200) | Sohbet için arkadaşlık şartı yok; seçici yalnız kolay erişim |

## 5. Güvenlik

- Mesaj gövdesi, GIF başlığı ve alıntı düz metin olarak çizilir (`dangerouslySetInnerHTML` yok).
- Gövdeye gönderen kimliği yazılmaz; kimlik yalnız oturum çerezinden gelir.
- Yol parametreleri `encodeURIComponent` ile kaçırılır; `?to=` ve `?c=` UUID biçimi dışında yok sayılır.
- Hata yanıtından yalnız `code` okunur (`[A-Z0-9_]`); sunucu metni ve gönderilen değer arayüze taşınmaz.
- Dış GIF görselleri `referrerPolicy="no-referrer"` ile yüklenir.

## 6. Açık noktalar

1. **Gerçek yığınla uçtan uca doğrulama yapılmadı.** Özellikle tarayıcı `EventSource` + Gateway çerez → Bearer çevirisi, ek indirme (`<img>`/`<audio>` çerezle) ve 60 sn'lik erişim token'ı dolunca akışın yenilenmesi canlıda denenmeli.
2. **Sesli mesaj kaydı (MSG-21) yok.** Backend hazır; `MediaRecorder` (`audio/webm;codecs=opus`, `audioBitsPerSecond ≈ 32000`, 2:00'de dur, 64 değerlik dalga formu) ve dalga formu çizen oynatıcı eklenmedi. Opsiyonel özellik olduğu için ayrı karar bekliyor. Ayrıca `Permissions-Policy` şu an `microphone=()` ile mikrofonu tüm sitede kapatıyor; kayıt eklenirse güncellenmeli.
3. **Kenar çubuğunda okunmamış rozeti yok.** Toplam okunmamış için sunucu ucu yok; bildirimler notification-service'i bekliyor.
4. **Ortak test kurulumu:** `profile-edit-form.test.tsx`'teki `matchMedia` hatası ortak `test/setup.ts`'e `matchMedia` taklidi eklenerek kapatılabilir. Ortak dosya olduğu için dokunulmadı; layout değişikliğinin sahibine iletilmeli.
5. **E2E (Playwright) testi eklenmedi.** `e2e/layout.spec.ts` ekran listesine `/mesajlar` eklenebilir; giriş gerektirdiği için oturum fikstürü gerekir.
