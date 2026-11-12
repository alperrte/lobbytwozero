# game-catalog-dump

Game oyun kataloğu veritabanı dökümü (`ltz-game-catalog.dump`, yaklaşık 340 MB) buraya konur.
Dosya Git'e girmez (`*.dump` ignore edilir); ekipten elden alınır ve dosya adı değiştirilmeden bu klasöre kopyalanır.

`node ops/dev.mjs --with=game-service` (veya `--all`) dökümü Game servisi başlamadan önce otomatik içe aktarır.
Elle: `node ops/game-catalog-import.mjs verify` ve `node ops/game-catalog-import.mjs import`.
Daha önce kurduysan ve yeni dump aldıysan: eski dosyayı silip yenisini koymak yetmez, `game_db` doluysa kurulum atlanır. Adımlar: `ops/game-catalog.md` → "Daha önce kurduysan".
Fiyatlar: dump'taki fiyatlar en geç 48 saat sonra "güncelliği doldu" olur ve yeni dump bunu çözmez. Güncel fiyat için kendi `.env.dev` dosyana `ITAD_API_KEY` yaz (anahtarı Game servisinin sahibinden al). Adımlar: `ops/game-catalog.md` → "Fiyatlar güncel kalsın".
Ayrıntı: `ops/ops.md`.
