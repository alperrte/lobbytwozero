# game-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Hamza.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Windows'ta `core.autocrlf=true` ise SQL migration dosyaları CRLF kalır ve 8 migration testi kırmızı çıkar (kod hatası değil, Linux CI'da yok). `.gitattributes` (`*.sql text eol=lf`) bunu çözer; eski checkout'ta migration klasörünü silip `git checkout -- <klasör>` ile LF'e çevir.
