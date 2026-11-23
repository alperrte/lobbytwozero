# LFP Service — Frontend (TASK-05)

> Tarih: 2026-11-24 · Sorumlu: Umut · Servis: `lfp-service` (web) · Dal: `feature/lfp-service` · Kapsam: Takım Bul sayfaları; ilan listeleme, arama, filtreleme, oluşturma, güncelleme, kapatma ve ilan sahibine mesaj · **Backend'e dokunulmadı.**
>
> Tasarım kaynağı: "LTZ Gereksinim Dokümanı - Güncel.docx" Şekil 91–93 (11.01 Oyun Arkadaşı İlanları, 11.02 İlan Oluşturma, 11.03 İlanlarım) ve Şekil 57 (gezinme modeli).

## 1. Sonuç

| Ekran / işlev | Gereksinim | Durum | Not |
| --- | --- | --- | --- |
| Aktif ilanlar (11.01): kart ızgarası, "daha fazla" | LFP-10, LFP-15 | DONE | Yalnız açık ilanlar; sayfa başına 12, imleçle devam |
| Arama ve süzgeçler: oyun, platform, seviye, rol, oyun zamanı, mikrofon, metin | LFP-11 | DONE | Süzgeçler adres çubuğunda (`?platform=&mic=&when=&q=`…); metin süzgeçleri 400 ms gecikmeli |
| İlan oluştur (11.02): oyun, platform, rol, oyuncu sayısı, seviye, zaman, süre, mikrofon, açıklama + canlı önizleme | LFP-01..09 | DONE | Oyun seçimi katalogdaki `GamePicker` ile |
| İlan düzenle | LFP-12 | DONE | Oyun ve süre kilitli (sunucu kuralı); `version` gönderilir |
| İlanlarım (11.03): durum sekmeleri, sayaçlar, tablo / mobil kart, kapatma onayı, detay | LFP-13, LFP-14 | DONE | Kapalı/süresi dolmuş ilanda yalnız "Detay" |
| Mesaj Gönder | LFP-16, LFP-17 | DONE | `POST .../contact` → `/mesajlar?to=<sahip>`; engel ve kapalı ilan sunucuda |
| Gezinme | — | DONE | Kenar çubuğu ve footer'da "Takım Bul" artık gerçek bağlantı |

## 2. Rapordaki tasarımdan bilinçli farklar (API'ye göre)

| Tasarımda | Uygulamada | Neden |
| --- | --- | --- |
| İlan başlığı ("Akşam ranked için 2 oyuncu") | Kart başlığı oyun adı, altında açıklama | `Listing`'de başlık alanı yok |
| Mikrofon: Gerekli / Tercih edilir / Fark etmez | Gerekli / Fark etmez | `micRequired` boolean |
| Seviye açılır listesi | Serbest metin (en çok 50) | Seviye oyuna göre değişir, API serbest metin |
| Tarih + saat zorunlu, "İlan Bitişi" tarih/saat | Oyun zamanı isteğe bağlı; ilan süresi 3 sa – 7 gün seçimi | API: `playTimeStart/End` opsiyonel, `durationHours` 1–168 |
| "En Yeni" sıralama, sayfa numaraları | Yalnız en yeni önce, "daha fazla ilan" | API keyset imleçli, tek sıralama |
| "24 aktif ilan" toplamı | "N aktif ilan" / "N+ aktif ilan" (yüklenen) | Sayım ucu yok |

## 3. Doğrulama (2026-11-24)

| Kontrol | Sonuç |
| --- | --- |
| `npm run typecheck`, `npm run lint` | PASS |
| `npm run test` | 199 dosya, 1997 test PASS (footer, gezinme ve app-shell testleri Takım Bul bağlantısına göre güncellendi). **Exit 1:** yalnız `features/user/components/profile-edit-form.test.tsx` içindeki 2 "unhandled error" (`window.matchMedia`, main'den gelen CardSnake; messaging TASK-09'da da raporlandı) |
| Yeni LFP testleri | 4 dosya, 36 test (api 5, hatalar 10, şema 11, ekranlar 10) |
| Tarayıcıda görsel kontrol | `next dev` + Playwright, API yanıtları taklit edildi: 1440 px ve 375 px; üç ekranda yatay taşma yok; kapatma onayı, form doğrulaması ve önizleme çalışıyor. Bulunan iki sorun düzeltildi: kendi ilanında sahip adı (profil kartı kendini döndürmüyor → "Sen") ve dar sütunda platform etiketi kırpılması |
| Gerçek DEV yığını | **NOT VERIFIED** (yığın kaldırılmadı) |

## 4. Mimari

`app/[locale]/(dashboard)/find-team/{page, new, mine, [listingId]/edit}` → `features/lfp`.

| Dosya | Görev |
| --- | --- |
| `services/lfp-api.ts` | LFP uçları; zaman ön ayarı → `playFrom/playTo` (yerel gün, UTC ISO); game-service toplu kart (çerezsiz, ≤50) |
| `services/lfp-http.ts` | CSRF çift gönderim, reddedilince bir kez tekrar; 15 sn üst süre |
| `services/lfp-errors.ts` | `ProblemDetail.code` → çeviri anahtarı; alan hatası → form alanı |
| `schemas/listing-form.ts` | Form durumu ↔ gövde, istemci doğrulaması (sunucu kurallarının aynası), süzgeç ↔ adres |
| `hooks/use-lfp-data.ts` | Arama (sayfalı), ilanlarım (≤200, yerel sayaç), sahip/kapak görselleri, iletişim akışı |
| `components/*` | `ListingsView`, `ListingFiltersPanel`, `ListingCard`, `ListingForm` (oluştur/düzenle), `MyListingsView` |

Ortak dosyalar: `routing.ts` (`/takim-bul`, `/ilan-olustur`, `/ilanlarim`, `/[listingId]/duzenle`), `navigation.ts` (findTeam `href`), `icons.tsx` (pencil, plus, minus, eye, filter), `tr.json`/`en.json` (`Lfp`), `features/social/index.ts` (`SocialHero` dışa açıldı; sosyal sayfalarla aynı başlık kartı).

## 5. Güvenlik

- Açıklama, rol ve seviye düz metin çizilir (`whitespace-pre-line`, HTML yok).
- Sahip kimliği gövdeye yazılmaz; düzenleme sayfası sahip olmayanı ve kapalı ilanı engeller (yetki kararı yine sunucuda, `403/409`).
- Adresten okunan süzgeçler doğrulanır (UUID, platform listesi, uzunluk, kontrol karakteri); geçersiz değer sunucuya gönderilmez.
- Oyun kapakları yalnız güvenli HTTPS adresinden (`GameCover`), toplu kart çerezsiz istenir.

## 6. Açık noktalar

1. **Gerçek yığınla uçtan uca doğrulama yapılmadı** (oluştur → listede görünme → iletişim → mesajlar sayfası).
2. **Oyun zamanı süzgeci ön ayarlıdır** (bugün / yarın / 7 gün). Serbest tarih aralığı istenirse `playFrom/playTo` doğrudan kullanılabilir.
3. **Toplam ilan sayısı ve sıralama seçenekleri** API'de yok; tasarımdaki "24 aktif ilan" ve "En Yeni" menüsü bu yüzden sadeleştirildi.
4. **Messaging TASK-09.md** bu dalda `docs/service-docs/docs-messaging/` altına taşındı (main'de kökte kalmıştı).
