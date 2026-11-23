# TASK 4 — Sosyal ve etkinlik önerileri için güvenlik sınırı

2026-11-15 · LTZ-ALP-REC-04 · Alper · **BLOCKED — 0/3 DONE**.

Bu, mevcut sözleşmelere dayalı task kararıdır; yeni bir auth/mimari modeli onaylamaz.

## Kaynak ve karar

Master v2 §8.1 kritik privacy garantisi yoksa öneri üretmemeyi ve eksik User/Social/Event
sözleşmelerini kaydetmeyi ister. AGENTS.md §1 başka geliştiricinin kodunu değiştirmeyi yasaklar.

User kart batch'i oyun tercihi ve öneriye aday olma izni taşımaz; isim araması aday discovery
değildir. Tek profil detail okumasını N+1 taramaya dönüştürmek ve private Library/Review
verisini başka kullanıcıya açıklamak kabul edilmez. Mevcut owner recommendation rızası,
başkasına sosyal aday olarak önerilme izni diye genişletilmez.

Social iki yönlü engel kaynağı mevcut olsa da yalnız Messaging/LFP caller'larına izin verir.
Recommendation'a Auth Social grant tanımlı değildir. Başka caller'a ait kimlik/secret veya
token kullanılmaz. Arkadaş/owner engellenenler listesi reverse block kontrolünün yerine geçmez.

Event'te yalnız launcher vardır; çalışan keşif/eligibility API'si yoktur. Öneri üretmek için
etkinlik/kapasite/zaman verisi veya sahte fixture-backed production kaynak oluşturulmaz.

Sonuç: REC-13/14/15 BLOCKED; production controller/DTO/port/ranker/adapter eklenmedi.
Eksik veri için boş başarılı liste, yapay popülerlik veya tahmini skor sunulmadı. Mevcut
security allowlist/denyAll aynı kalır. `/players`, `/friends`, `/events` testte yalnız negatif
probe path'leridir; yeni veya vaat edilmiş API sözleşmesi değildir.

## Güvenle yapılan çalışma

Mevcut RecommendationApiIntegrationTest'e üç sınır testi eklenir. Her probe GET/POST'ta
anonim ve kullanıcı/servis token ayrımını, A/B/ADMIN reddini, sahte kimlik/private/candidate
girdisinin etkisizliğini, no-store/ProblemDetail ve kaynak iş çağrısı/DB yazısı yokluğunu doğrular.
Bu testler self/friend/block/özel profil filtre algoritmasını veya etkinlik eligibility'yi
uygulamaz; o pozitif/negatif feature kabul testleri sözleşmeler gelene kadar NOT IMPLEMENTED.

Hiçbir endpoint oluşturulmadı/değişmedi; docs/api.md yalnız Recommendation altında gerçek
kapalı kapsamı not eder. ENV/bağımlılık/servis güven modeli/DB/ER/migration/üretici kodu değişmez.
Mevcut Java25/Boot4.1.1/PostgreSQL kodu eski docx/iskelet ifadelerinden önceliklidir.

## Yeniden açılma koşulları

1. Kerem: izinli ve bounded User aday/tercih/eligibility sözleşmesi.
2. Umut + Kerem: Recommendation için mevcut arkadaşlık/iki yönlü block/erişim kontrolünün
   izinli batch ve gerçek Auth trust sözleşmesi.
3. Nisa: gerçek Event discovery ve viewer eligibility; state/time/capacity kuralları.

Owner talepleri [User](../integration/dependencies/done/user-service-requests.md),
[Social](../integration/dependencies/done/social-service-requests.md),
[Event](../integration/dependencies/done/event-service-requests.md).
Sonraki uygulamada bounded aday penceresi, deterministik tie-breaker, izinli sinyalle aynı
skor/gerekçe, güncel eligibility ve kaynak arızasında fail-closed testleri gerekir.
Bu maddeler tasarım talebidir; uygulandı olarak işaretlenmez. TASK 5 başlatılmadı.
