# Performance Service — Sorunlar ve riskler

## Faz 1

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-01 | Auth cihaz token'ı yalnız `aud=hardware-service` üretiyor (REQ-01-A) | BLOCKED | Kerem | Gerçek cihaz senkronizasyonu E2E yapılamaz; Hardware token'ı Performance'ta reddedilir, gevşetme yapılmadı |
| I-02 | Auth cihaz iptalini servislere bildirmiyor (REQ-01-B) | REQUESTED / kabul edilen risk | Kerem | İptal sonrası ≤ 60 sn pencere; Performance bağlantısı kullanıcı kaldırana dek ACTIVE görünebilir |
| I-03 | Auth servis token'ı `sub` değerini hep `svc:auth-service` yapıyor, `devices:verify` yok (REQ-01-C) | BLOCKED | Kerem | Hardware→Performance aktif cihaz ucu gerçek token ile çağrılamaz; Hardware kodu değiştirilmedi (Hardware istemcisi önce Hardware sahibine bildirilmeli) |
| I-04 | Auth silme işçisi → Performance D4 ucu ortak ortamda doğrulanmadı | NOT VERIFIED | Alper + Kerem | Uç ve testler hazır |
| I-05 | Standart `pre-push` (`.\pre-push\pre-push.cmd performance-service`) | ÇÖZÜLDÜ — PASS | Alper | BACKEND TEST CHECK PASSED, EXIT 0 (31 test, %94,25) |
| I-06 | SonarQube (`sonar.cmd scan dev`) | Kapı FAIL — tüm repo taraması | Alper | Analiz çalıştı; kapı repo genelinde kırmızı. performance-service'in 9 bulgusu (S2259, S1075, S3358, S107, S1192×2, S5778×4) düzeltildi; üçüncü tarama araç hatasıyla (`Git facts changed`, tarama sürerken ağaç değişti) geçersiz kaldı; düzeltmelerin Sonar'daki etkisi NOT VERIFIED, yeniden tarama Faz 2 sonunda (`phase-01-tests.md` §1). Diğer servislerin bulguları bu görevin kapsamı dışında, dokunulmadı |
| I-08 | Bearer'sız yazma isteğinde CSRF reddi 401'e çevrildi | Karar (KARAR-04) | Alper | Oturum/cookie desteklenmediği için doğru semantik; gözden geçirilebilir |
| I-09 | Test kapsamına iki bağımlılık eklendi | Bilgi | Alper | Hardware'de zaten kullanılıyor; yeni üretim bağımlılığı yok |

## Faz 2

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-10 | Game GAME-15 ve Hardware HW-17 tüketici tarafı gerçek servislerle doğrulanmadı | NOT VERIFIED (`DEFERRED_CONTRACT`) | Alper (Hardware), Hamza (Game) | Testler yerel mock kullanır (`PerformancePeerDoubles`). Bağımlı servis yanıtsızsa kayıt açılmaz (503, kapalı başarısızlık). Her iki servisin kodu değiştirilmedi |
| I-11 | Cihaz kaynaklı oturum (`source=DEVICE`) yok | Karar (KARAR-P3-02) — Faz 3'te kapandı | Alper | Oturumu kullanıcı açar, cihaz yalnız örnek yazar; kaynak ayrımı örnek düzeyinde (`source='DEVICE'`). Oturum `source` CHECK'i `MANUAL` kalır |
| I-12 | Tembel `ABANDONED` geçişi (arka plan zamanlayıcısı yok) | Karar (KARAR-P2-03) | Alper | Terk edilmiş oturum bir sonraki başlatma/okumada kapanır; veri tabanında bir süre `ACTIVE` görünebilir, ancak API tutarlıdır |
| I-13 | `erase_performance_account` en çok 1000 oturum/çağrı siler | Karar (KARAR-P2-10) | Alper | Hesap başına 1000 oturum sınırı bunu tek geçişte yeterli kılar |
| I-14 | Standart pre-push ve SonarQube Faz 2 sonuçları | Pre-push PASS; Sonar kapısı FAIL (performance-service dışı) | Alper | Pre-push 77 test / %96,02 PASS. Sonar: performance-service bulgusu 0, Faz 1 düzeltmeleri doğrulandı. Kapı hardware (Alper), social, user, game, review, library bulgularıyla kırmızı; bu servislere dokunulmadı |
| I-15 | Hardware-service Sonar bulguları (S2259 ×3, S3358, S3776: `HardwareDeviceJwtValidator`, `HardwareSecurityConfiguration`) | AÇIK — kullanıcı kararı bekliyor | Alper | Performance'ın bu fazına ait değil; hardware kodu önce bildirilmeden değiştirilmedi. Düzeltme istenirse ayrı iş olarak yapılabilir |

## Faz 3

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-16 | Gerçek masaüstü sensör/istemci sözleşmesi yok; metrik aralıkları geçici | AÇIK (varsayım) | Alper | Sıcaklık 0<v≤150, FPS 0<v≤1000, yüzde 0..100, RAM ≤1.048.576 MB, VRAM ≤262.144 MB, disk ≤100.000 MB/s, fan ≤30.000 RPM. Gerçek istemci sözleşmesiyle gözden geçirilmeli (KARAR-P3-06) |
| I-17 | `FAN_RPM` hangi fanı ölçtüğü belirsiz (CPU/GPU/kasa) | AÇIK (ekip kararı) | Alper | Tek metrik; çoklu fan için yeni metrik/`sensor` alanı ve yeni migration gerekir |
| I-18 | Disk için yalnız okuma/yazma verimi (MB/s); doluluk/gecikme yok | AÇIK (sözleşme yok) | Alper | PERF-10 "kullanım ve performans" kısmen karşılandı |
| I-19 | Kotalar ayarlanabilir varsayımdır (oturum 100.000, hesap 500.000, gönderim 500, sync gövdesi 64 KiB, cihaz makbuz 1500) | Karar (KARAR-P3-09) | Alper | Gerçek ölçüm yoğunluğu öğrenilince yeniden değerlendirilmeli |
| I-20 | Saklama süresi/otomatik temizlik ve bölümleme (partitioning) yok | AÇIK (risk) | Alper | Örnekler kullanıcı silene/hesap silinene kadar kalır; hesap kotası (500.000) büyümeyi sınırlar. Üretim öncesi saklama politikası gerekli |
| I-21 | Gerçek Auth cihaz token'ıyla örnek yazımı E2E değil | BLOCKED (REQ-01-A) | Alper / Kerem (Auth) | Testler yerel imzalı sözleşme token'ı kullanır |
| I-22 | Replay, oturum sonradan silinse de ilk sonucu döndürür | Karar (KARAR-P3-04) | Alper | Yanıt yalnız sayaçlardır; veri sızmaz, yazım yapılmaz |
| I-23 | Jackson `"60"` → 60.0 sessiz dönüşümü yakalandı ve kapatıldı | ÇÖZÜLDÜ | Alper | `StrictNumberDeserializer`; testle doğrulandı (KARAR-P3-07) |

## Faz 4

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-24 | Recommendation referansı güvenilirlik eşiği (≥ 60 FPS örneği) bir varsayım | AÇIK (varsayım) | Alper | Eşik altında değerler `null`, `reliable=false`. Recommendation ihtiyacı netleşince ayarlanmalı (KARAR-P4-07) |
| I-25 | Hesaplar arası ("tüm oyuncularda bu oyun") toplam yok | AÇIK (gizlilik kararı) | Alper / ekip | Başka kullanıcıların verisini toplamak ayrı gizlilik kararı ve k-anonimlik eşiği ister; yapılmadı. Sözleşme yalnız çağıran hesabın verisini döner (KARAR-P4-06) |
| I-26 | Okuma uçlarında hız sınırı yok | AÇIK (risk) | Alper | Her sorgu `size`/`maxPoints`/kota ile sınırlıdır ama tekrarlı çağrılabilir; üretim öncesi okuma sınırı değerlendirilmeli (KARAR-P4-09) |
| I-27 | Hesap istatistiği tek sorguda hesap kotasına (≤ 500.000 örnek) kadar tarayabilir | AÇIK (risk) | Alper | Kota maliyeti sınırlar; büyük hesaplarda önbellek/özet tablo veya zaman penceresi zorunluluğu düşünülmeli. Saklama/bölümleme I-20 ile birlikte ele alınmalı |
| I-28 | Hardware/Recommendation servis token'ı gerçek Auth ile alınamıyor | BLOCKED (REQ-04-A, REQ-01-C) | Alper / Kerem (Auth) | Testler yerel imzalı sözleşme token'ı kullanır; gerçek entegrasyon DEĞİL. Tüketici servisler (Hardware/Recommendation) bu fazda değiştirilmedi |
| I-29 | İlk kapsam adları (`performance:summary:read`) Auth desenine uymuyordu | ÇÖZÜLDÜ | Alper | `summary:read` / `reference:read` seçildi; gerçek issuer ile token üretimi testte doğrulandı (KARAR-P4-08) |
| I-30 | Grafik serisi eşit genişlikli kova ortalamasıdır; görsel olarak ince tepe noktaları LTTB gibi algoritmalardan farklı görünebilir | Karar (KARAR-P4-05) | Alper | min/max kova bazında verilir, böylece uç değerler kaybolmaz |
