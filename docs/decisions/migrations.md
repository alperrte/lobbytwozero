
# LTZ — Flyway Migration Numaralandırma Planı

## ALPER
- V1–V10       → HARDWARE-SERVICE
- V11–V20      → RECOMMENDATION-SERVICE
- V21–V30      → PERFORMANCE-SERVICE

## HAMZA
- V31–V40      → GAME-SERVICE
- V171–V190    → GAME-SERVICE (ek aralık; 2026-10-21 kullanıcı onayı)
- V41–V50      → LIBRARY-SERVICE
- V51–V60      → REVIEW-SERVICE

## KEREM
- V61–V70      → AUTH-SERVICE
- V71–V80      → USER-SERVICE
- V81–V90      → MAIL-SERVICE

## NISA
- V91–V100     → COMMUNITY-SERVICE
- V101–V110    → CONTENT-SERVICE
- V111–V120    → EVENT-SERVICE

## UMUT
- V121–V130    → SOCIAL-SERVICE
- V131–V140    → MESSAGING-SERVICE
- V141–V150    → LFP-SERVICE

## ORTAK SERVİSLER
- V151–V160    → NOTIFICATION-SERVICE
- V161–V170    → ADMIN-SERVICE

Bu iki ortak servisin migration sorumlusu,
geliştirmeye başlanmadan önce belirlenmelidir.

---

## Migration Kuralları

Game Service'in V31–V40 aralığı Faz 1–4 ile tamamen kullanılmıştır.
İlerleyen şema ihtiyaçları için V171–V190 yalnız Game Service'e ayrılmıştır;
diğer servisler bu numaraları kullanmamalıdır. Mevcut ve rezerve V1–V170
tahsisleri değişmez. Bu kayıt ortak branch'e aktarılarak ekiple paylaşılmalıdır.
Tahsis, Faz 5'i başlatmaz ve mevcut migration'ların yeniden numaralandırılmasına izin vermez.

1. Her servis yalnızca kendisine ayrılan
   migration numaralarını kullanır.

2. Migration dosya adlandırması:
   V{NUMARA}__{aciklama}.sql

3. Örnekler:
   V1__create_hardware_tables.sql
   V31__create_game_tables.sql
   V61__create_auth_tables.sql
   V121__create_social_tables.sql
   V151__create_notification_tables.sql

4. Her servisin migration dosyaları:
   backend/services/{service-name}/src/main/resources/db/migration/

5. Uygulanmış migration dosyaları değiştirilmez.
   Şema değişiklikleri yeni migration ile yapılır.

6. Migration numaraları, servisin kendi aralığı
   içerisinde artan sırayla kullanılır.

7. İlk 10 migration yeterli olmazsa yeni bir
   numara aralığı ekipçe kararlaştırılır.

8. Aynı servis üzerinde birden fazla geliştirici
   çalışıyorsa migration numaraları koordine edilir.

9. Servisler birbirlerinin veritabanlarına
   doğrudan müdahale etmez.

10. Migration dosyaları Git'e gönderilmeden önce
    mevcut migration geçmişi kontrol edilir.
