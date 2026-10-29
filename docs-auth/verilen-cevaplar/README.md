# VERİLEN CEVAPLAR

Bir ekip arkadaşı Auth'tan bir şey istediyse, sorduysa ya da Auth hakkında bir sorun bildirdiyse cevabım burada. Her branch'in kendi klasörü var. Kendi branch'inin klasörüne gir, `README.md` önce durumu özetler, sonra talepler sırayla gelir.

**Sahip:** Kerem (auth-service) · **Son güncelleme:** 2026-10-26

---

## NASIL OKUNUR

- Her branch klasörünün adı branch adıyla aynı (`feature/` öneki olmadan). Örnek: `feature/hardware-service` → `hardware-service/`
- Klasörde önce `README.md` var: durum tablosu ve açık işler
- Her talep için ayrı dosya var: `talep-01-...md`, `talep-02-...md` gibi
- Her talep dosyası aynı sırayla yazılı: **NEYDİ**, **NE SORULDU**, **BENİM CEVABIM**, **NEDEN**, **SENDEN NE BEKLİYORUM**, **DURUM**
- Yapay zekâ asistanına vereceksen klasörün yolunu ver ve "bu klasördeki cevapları oku, bana yapılacakları söyle" de. Metinler kısa cümlelerle ve başlıklarla yazıldı

## DURUM ETİKETLERİ

| Etiket | Anlamı |
|---|---|
| KAPANDI | Cevap verildi, kimsenin yapacağı iş kalmadı |
| CEVAPLANDI | Cevap verildi, karşı tarafta yapılacak iş var |
| KABUL EDİLDİ / YAPILACAK | Auth'ın işi, sıraya alındı |
| KARAR VERİLDİ, SIRADA | Yön belli, şu an geliştirmeye kapalı |
| AÇIK | Hâlâ yapılmamış, kimin yapacağı yazılı |

## TÜM BRANCH'LER

| Klasör | Sahip | Durum özeti | Açık iş var mı |
|---|---|---|---|
| [hardware-service](hardware-service/README.md) | Alper | 4 talep cevaplandı, 04 kapandı | Evet: talep 02 (cihaz kimliği, sıraya alındı), 03 (ortak deneme). Talep 04 kapandı |
| [social-service](social-service/README.md) | Umut | Uyumlu, 2 sıkılaştırma açık | Evet: Social'da yapılacak |
| [community-service](community-service/README.md) | Nisa | Gerçek JWT bekliyor | Evet: Community'de yapılacak |
| [game-service](game-service/README.md) | Hamza | Eski talepler kapandı, yeni talep 02 cevaplandı | Evet: HttpClient düzeltmesi Hamza'da |
| [library-service](library-service/README.md) | Hamza | İskelet + silme ucu var | Hayır |
| [review-service](review-service/README.md) | Hamza | İskelet + silme ucu var | Hayır |
| [messaging-service](messaging-service/README.md) | Umut | İskelet | Hayır |
| [lfp-service](lfp-service/README.md) | Umut | İskelet | Hayır |
| [content-service](content-service/README.md) | Nisa | İskelet | Hayır |
| [event-service](event-service/README.md) | Nisa | İskelet | Hayır |
| [performance-service](performance-service/README.md) | Alper | İskelet, cihaz kimliği ile ilgili | Bekliyor (talep 02) |
| [recommendation-service](recommendation-service/README.md) | Alper | İskelet | Hayır |
| [user-service](user-service/README.md) | Kerem | İskelet | Hayır |
| [mail-service](mail-service/README.md) | Kerem | İskelet | Hayır |
| [admin-service](admin-service/README.md) | Ortak | İskelet, Auth tarafı hazır | Admin yazılınca bağlanır |
| [notification-service](notification-service/README.md) | Ortak | İskelet | Hayır |
| [auth-service](auth-service/README.md) | Kerem | Bu servis | Bkz. ana `docs-auth/README.md` |
| [chore-architecture](chore-architecture/README.md) | Alper | Altyapı dalı | Hayır |
| [chore-security](chore-security/README.md) | Kerem | Güvenlik altyapısı | Hayır |

## YENİ TALEP YAZARKEN

Talebini kendi branch klasörüne `talep-NN-kisa-konu.md` olarak yazabilirsin. Şunları yaz: ne istiyorsun, neden, şu an elinde ne var, hangi HTTP kodu ya da hata davranışı bekliyorsun. Ben cevabı aynı klasöre aynı şablonla yazarım.
