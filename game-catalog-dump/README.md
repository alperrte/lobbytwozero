# game-catalog-dump

Game oyun kataloğu veritabanı dökümü (`ltz-game-catalog.dump`, yaklaşık 180 MB) buraya konur.
Dosya Git'e girmez (`*.dump` ignore edilir); ekipten elden alınır ve dosya adı değiştirilmeden bu klasöre kopyalanır.

`node ops/dev.mjs --with=game-service` (veya `--all`) dökümü Game servisi başlamadan önce otomatik içe aktarır.
Elle: `node ops/game-catalog-import.mjs verify` ve `node ops/game-catalog-import.mjs import`.
Ayrıntı: `ops/ops.md`.
