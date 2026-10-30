# library-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Hamza.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- `contextLoads` testi JWKS yer tutucusu için kendi `AUTH_SERVICE_URL` değerini test özelliği olarak verir (bağlantı kurulmaz); `-Pci` dışı koşuda (Sonar dahil) ortam değişkeni gerekmez.
- `LibraryApiTest` tabloyu temizlediği için geliştirici DB'sine (`library_db`/`ltz_dev`) asla bağlanmaz: yerelde (pre-push `full dev` dahil) Testcontainers ile geçici PostgreSQL açar, CI'da (`-Pci`) izole `library-service`/`ltz_test` DB'sini kullanır. Docker çalışıyor olmalıdır.
- `AuthErasureAdapterIntegrationTest` de aynı kuralla çalışır (yerelde Testcontainers, CI'da `-Pci` DB'si); yalnız kendi rastgele kullanıcı kimliklerinin satırlarını ekler/siler, tabloyu toptan temizlemez. Auth kaynaklarını (HttpAccountDataEraser, ServiceTokenIssuer) geçici dizine derler; JDK 25 ve repository checkout gerekir.

## Auth entegrasyonu (kullanıcı JWT ve D4)

- **Kullanıcı JWT:** Library, Auth'un gerçek access token'ını JWKS ile yerel doğrular (`typ=at+jwt`, RS256, `iss`, `aud=ltz-api`, `kid`, `iat/nbf/exp`, `sub` kanonik UUID, `roles` allowlist'i). Canlı Auth ile 30 Ekim 2026'da doğrulandı; ayrıntı: `docs-library/LTZ-HAM-LIB-06-Auth-Entegrasyonu-Sonuc.md`.
- **İptal gecikmesi (Auth politikası, Library'ye özgü açık değil):** Auth introspection/iptal listesi sunmaz; Library oturum veya hesap iptalini token süresi dolana kadar (+ en çok 30 sn saat toleransı) görmez. `docs/api.md` Auth politikası prod için 60 sn der; dev ortamında gözlenen access token ömrü 900 sn'dir. Kritik bir işlem için daha kısa pencere gerekirse Auth ile ayrı bir sözleşme gerekir (Library tek başına çözemez).
- **İç okuma uçları:** Recommendation sinyal ucu artık Auth'un `signals:read` sözleşmesindedir (`sub=svc:recommendation-service`, imzalı `account_id` == yoldaki hesap; eski `library:recommendation:read`/`target_user_id` kalktı). Profil iç uçları (`library:profile:read`, `viewer_id`) eski taslaktır: Auth karar K1 gereği delegasyon token'ı üretmez, User kullanıcının kendi at+jwt'siyle public uçları çağırır; bu iç uçlar kapalı kalır ve `DEPRECATED`'dır. Ayrıntı: `docs-library/LTZ-HAM-LIB-07-Auth-Karar-Uyarlamasi-Sonuc.md`.
- **Library → User gizlilik kararı:** Auth son kararı (PR #37, `origin/main`): `library-service` → `user-service` token'ı `scope="events:ingest privacy:read"`, `typ=svc+jwt`, `sub=svc:library-service`, 60 sn. Gizlilik kararı için yalnız `privacy:read` aranır (`AuthServiceTokenClient.privacyDecisionToken()`); `events:ingest` olay yetkisidir, onun yerine geçmez; eski `access:decide` kabul edilmez ve Auth artık vermez. Library User'a olay göndermez (olay mimarisi yok, kapsam dışı). Görünürlük adaptörü hâlâ fail-closed (`UnavailableLibraryVisibility`): User karar ucu henüz birleşmemiş dalda ve HTTP adaptörü yazılmadı. Sır `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` tanımsızsa istemci kapalıdır.
- **Atlanan test yasağı:** merkezi coverage kapısı atlanan testi reddeder. Auth PR #36/#37 kaynağına bağlı gerçek-Auth testleri bu yüzden test ağacında yoktur; Auth dalda olunca eklenebilir.
- **Hesap silme (D4):** Auth 14 günlük bekleme sonrası çağırır; canlı Auth silme akışı doğrulanmadı (NOT VERIFIED). Sözleşme, Auth'un gerçek HTTP adaptörüyle `AuthErasureAdapterIntegrationTest` içinde sınanır.
