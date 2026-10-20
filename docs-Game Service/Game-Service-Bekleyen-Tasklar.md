# Game Service — Bekleyen Tasklar (2026-10-21)

Bu not, Game Service görevlerinde **neden bazı işlerin yapılmadığını** ve **neyi beklediklerini** özetler.

## Kısa özet

| Task | Durum | Neden | Ne bekliyor |
| --- | --- | --- | --- |
| GAME-04 (Filtreleme) | Kısmen yapıldı | LTZ puanı filtresi için puan verisi yok | Review Service toplu puan API'si |
| GAME-05 (Sıralama) | Kısmen yapıldı | LTZ puanına göre sıralama için puan verisi yok | Review Service toplu puan API'si |
| GAME-04 indirim filtresi | Yapılmadı | Fiyat/indirim verisi kaynağı yok | GAME-24 (kaynak ve lisans kararı) |
| GAME-17 (Detay zenginleştirme) | Altyapı hazır, gerçek bağlantı yok | Hangi sağlayıcıdan veri çekileceği seçilmedi | Sağlayıcı kararı ve onay |
| Faz 4 ve sonrası | Başlanmadı | Onay verilmedi | Onay |

## GAME-04 ve GAME-05: ne yapıldı, ne yapılmadı

**Yapılanlar (çalışıyor ve test edildi):**
- Filtreler: tür, platform, dil, çıkış tarihi aralığı, erken erişim, editör (popüler) seçimi. Hepsi birlikte kullanılabilir.
- Sıralama: ada, çıkış tarihine ve popülerliğe göre, artan/azalan. Her sıralama `gameId` ile bağlandığı için sayfalar arasında tekrar veya atlama olmaz.
- Verisi eksik olan kayıtlar (ör. tarihi olmayan oyun) sıralamada hep sonda kalır.

**Yapılmayanlar:**
- **LTZ puanı filtresi** (`minRating`) ve **LTZ puanına göre sıralama** (`sort=RATING`).

**Neden yapılmadı:**
- LTZ puanı Review Service'e aittir. Game Service başka servisin veritabanını okuyamaz.
- Review Service'in `docs/api.md` içinde henüz toplu puan sözleşmesi (REV-12) yok. Elimizde bağlanacak bir API olmadığı için tahmini puan, sahte API ya da "puan yok = 0" gibi uydurma bir çözüm yazılmadı.

**Şu an ne oluyor:**
- `minRating` veya `sort=RATING` ile istek atılırsa servis **400** döner ve `RATING_INTEGRATION_PENDING` kodunu verir. Sessizce yok sayılmaz, yanlış sonuç da üretmez. Bu davranış testlerle doğrulanmıştır.
- Yani gerçek puan altyapısı yok. Hazır olan: parametrelerin API'de tanımlı olması ve "henüz desteklenmiyor" cevabı. Review Service API'si gelince bu bölümün içi doldurulacak.

**Tamamlanması için gerekenler:**
1. Review Service'in çoklu `gameId` için ortalama puan ve puan sayısı veren toplu API'sini yayınlaması (REV-12), ve bunun `docs/api.md` içine yazılması.
2. Game Service'te bu API'yi çağıran istemci eklenmesi. Oyun başına ayrı istek (N+1) atılmayacak, sayfa başına tek toplu çağrı yapılacak.
3. Zaman aşımı ve Review Service erişilemezse davranış (kısmi başarısızlık) kararı.
4. Puanı olmayan oyunların filtre ve sıralamadaki yerinin belgelenmesi.

## Diğer bekleyenler

- **İndirim filtresi (GAME-04 içinde):** İzinli bir fiyat/indirim veri kaynağı bulunmadığı için GAME-24'e bağlıdır. Kaynak yoksa görev ertelenir; fiyat bilgisi uydurulmaz.
- **GAME-17 gerçek sağlayıcı entegrasyonu:** Altyapı (kilitleme, TTL, retry, hata durumunda eski veriyi koruma) hazır. Ancak detay çekilecek sağlayıcı (Steam dışında IGDB, RAWG vb.) henüz seçilmedi. Mevcut Steam bağlantısı yalnızca katalog listesi verir, detay vermez. Araştırma: `docs-Game Service/LTZ-HAM-GAME-02-Saglayici-Arastirmasi.md`.
- **Faz 4 (yetkili yönetim):** Onay verilmediği için başlanmadı. Editör listesini değiştiren komut şu an yalnızca uygulama içinden çağrılıyor; dışarıya açık, yetkili bir HTTP girişi Faz 4'te gelecek.

## Bu yüzden Faz 3 "8/8 tamamlandı" sayılmaz

Tamamlananlar: GAME-01, 02, 03, 06, 20, 25. Kısmi: GAME-04, 05.
