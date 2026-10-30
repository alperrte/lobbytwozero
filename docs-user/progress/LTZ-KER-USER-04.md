# LTZ-KER-USER-04 — Onboarding ve Kullanıcı Tercihleri

Sorumlu: Kerem. Görevler: USER-09, USER-10. Tarih: 2026-10-30.
Durum: **Kod tamamlandı ve derlendi. TESTLER SONA ERTELENDİ** (proje sahibi kararı: testler tüm fazlar bitince toplu yazılacak).

## Yapılanlar

| Görev | Kayıt |
| --- | --- |
| USER-10 | `GET/PUT /api/v1/user/me/preferences`: dil (`tr`/`en`), tema (`LIGHT/DARK/SYSTEM`, dilden bağımsız), `reducedMotion`, `compactLayout`; varsayılanlar (`tr`, `SYSTEM`, kapalı, kapalı); iyimser kilit |
| USER-09 | `GET /api/v1/user/me/onboarding` (durum, eksik adımlar, profil sürümü) ve `POST .../complete`: en az 1 tür + 1 platform zorunlu; oyuncu türü, dil, tema isteğe bağlı; idempotent |
| Migration | `V73__create_user_preferences.sql` (planda V74'tü; Faz 3 `linked_accounts` V74 olacak ki sıra artarak kalsın) |
| Hesap silme | Ek iş yok: `user_preferences` profil FK'si `ON DELETE CASCADE` ile gider |
| Yeni ENV | **Yok** |

## Tasarım notları

- Onboarding için ayrı tablo yok; seçimler Faz 1 profil alanlarına (`PATCH /me/profile` ile aynı doğrulama) ve tercihlere yazılır,
  durum `user_profiles.onboarding_completed_at` sütunundadır.
- Tamamlama tek transaction'da profil + tercih yazar; başarısızlıkta hiçbiri yazılmaz. İkinci çağrı hiçbir şeyi değiştirmez.
- `locale` yalnız `tr`/`en`; DB'de de CHECK var.
- Tercih satırı ilk `PUT`'ta oluşur (okuma satır oluşturmaz); onboarding dil/tema seçtiyse satırı kendisi açar.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| `mvn -DskipTests test-compile` | Derleniyor |
| `lint-ratchet check` | **Yeşil** (yeni ihlal yok) |
| Testler | **Yazılmadı, çalıştırılmadı** (sona ertelendi) |

## Sonda yazılacak testler (toplu test fazı)

Aşağıdaki kapsam, tüm fazlar bitince tek seferde yazılacak (Checkstyle: test metot adlarında alt çizgi yok):

- **Tercihler:** varsayılanlar ve okumanın satır oluşturmaması; ilk `PUT` sürüm 0, sonraki sürüm artışı, değişiklik yoksa artmama;
  eski sürüm `409`; eşzamanlı ilk kayıt (yalnız biri başarılı); `locale` `tr`/`en` dışı `400`; eksik/tanımsız alan `400`;
  token yok/servis token'ı `401`; profil yokken ilk `PUT` profili açar.
- **Onboarding:** durum (`missing` listesi: boş profil, yalnız tür, yalnız platform, tamam); tamamlama (profil alanları + tercihler
  yazılır, `completedAt` dolar, sürüm yanıtta güncel); boş tür/platform `400 ONBOARDING_INCOMPLETE`; geçersiz kod/oyuncu türü/dil;
  sürüm uyuşmazlığı `409`; ikinci çağrı idempotent ve veri değişmez; dil/tema verilmezse tercih satırı oluşmaz; yalnız dil verilince
  tema varsayılanda kalır; eşzamanlı iki tamamlamada tek sonuç; token kuralları.
- **Silme:** `AccountErasureTest` tohumuna `user_preferences` satırı eklenmeli (FK cascade doğrulaması).
- **Faz 1-2 bağlantısı:** `UserProfile.completeOnboarding` ve `GET /me` yanıtındaki `onboardingCompletedAt`.

## Açık işler

- Testler (yukarıdaki liste), Faz 3 (Steam), Auth talepleri.
