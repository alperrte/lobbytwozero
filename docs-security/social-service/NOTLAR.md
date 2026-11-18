# social-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Umut.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- Henüz kayıtlı bir not yok.

## User yansıması geçmiş aktarımı (REC-13, 2026-11-18)

- **Amaç:** user-service'in `social_blocks` / `social_friendships` yansıması yalnız olay iletimi başladıktan sonraki
  değişiklikleri bilir. Önceden var olan engel ve arkadaşlıklar aktarılmadan User `ltz.user.social.projection-enabled=true`
  yapılmamalıdır (eski engeller bilinmeden aday döner).
- **Nasıl çalışır:** `UserProjectionBackfill`, açılışta bir kez (`ltz.social.projection-backfill.enabled=true`, ortam değişkeni
  `SOCIAL_PROJECTION_BACKFILL_ENABLED`) tüm mevcut arkadaşlık ve engelleri user-service `POST /internal/events` ucuna
  doğrudan gönderir. Outbox'a yazılmaz; community yeniden engel olayı almaz. Varsayılan KAPALI.
- **Güvenli tekrar:** olay zamanı ilişkinin gerçek `created_at` değeri, kimliği ilişkiden türetilir. User yansıması yalnız
  daha yeni zamanlı olayı uyguladığı için sonradan gelen unblock/yeniden engel ezilmez; ikinci çalıştırma değişiklik yapmaz.
- **Hata:** teslim hatasında (User 503 vb.) aktarım yarıda kesilir, loga `stopped after friendships=N, blocks=M` yazılır
  ve uygulama açılmaya devam eder; bayrak açık kalırken yeniden başlatmak kaldığı yerden değil baştan güvenle çalışır.
- **Kullanım sırası:** (1) Social'da bayrağı aç ve yeniden başlat, loga `finished` satırını doğrula; (2) bayrağı kapat;
  (3) User'da `projection-enabled=true` yap. Gerçek ortamda henüz çalıştırılmadı.
- **Olay teslimi (S3):** engel/arkadaşlık olayları outbox ile en az bir kez iletilir. Relay varsayılan KAPALIDIR
  (`SOCIAL_OUTBOX_RELAY_ENABLED`); açık olmadan olaylar teslim edilmez. Hata halinde 30 sn'den başlayıp en çok 1 saate
  çıkan üstel bekleyişle 10 denemeye kadar tekrar edilir; sonra `FAILED` olur ve **el ile müdahale** ister (otomatik
  yeniden kuyruklama yok). `FAILED` olay User yansımasında engeli eksik bırakır; izleme gereklidir.
