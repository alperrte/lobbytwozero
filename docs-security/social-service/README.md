# social-service: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-28). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Umut (@Umutys61)  •  **Eski borç:** ⚠️ 137 ihlal, 25 dosya

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç: kural bazında

| Kural | Adet | Ne demek | Nasıl düzeltilir |
|---|---:|---|---|
| `checkstyle:MethodName` | 126 | Metot adında alt çizgi | camelCase yaz (`kayitBasarili201Doner`); IDE'nin Rename özelliğini kullan. |
| `pmd:CloseResource` | 7 | Kaynak (ExecutorService, HttpClient, stream) kapatılmıyor | Testte try-with-resources; üretim kodunda uygulama boyu yaşayan nesneyse gerekçeli çözüm (sahibine sor). |
| `pmd:CyclomaticComplexity` | 1 | Metot/sınıf çok dallı ve karmaşık | Davranışı değiştirmeden küçük metotlara böl; önce mevcut testlerin geçtiğinden emin ol. |
| `pmd:PreserveStackTrace` | 3 | Yeni hata fırlatılırken eski hatanın izi kayboluyor | Eski hatayı sebep olarak ekle. **Güvenlik sınırında** (token, parola, dış servis cevabı) sebep gizli bilgi taşıyabilir: körlemesine ekleme, güvenlik gerekçeli dar çözüm bul. |

## Eski borç: dosya dosya

- `src/main/java/com/ltz/social/api/controller/AuthenticatedUser.java`: pmd:PreserveStackTrace ×1
- `src/main/java/com/ltz/social/application/service/FriendRequestService.java`: pmd:CyclomaticComplexity ×1, pmd:PreserveStackTrace ×1
- `src/main/java/com/ltz/social/application/service/KeysetCursor.java`: pmd:PreserveStackTrace ×1
- `src/test/java/com/ltz/social/ServiceBoundaryTest.java`: checkstyle:MethodName ×2
- `src/test/java/com/ltz/social/api/controller/AccountErasureTest.java`: checkstyle:MethodName ×10
- `src/test/java/com/ltz/social/api/controller/BlockControllerTest.java`: checkstyle:MethodName ×16
- `src/test/java/com/ltz/social/api/controller/FriendRequestControllerTest.java`: checkstyle:MethodName ×22
- `src/test/java/com/ltz/social/api/controller/FriendsVisibilityDefaultAdapterTest.java`: checkstyle:MethodName ×1
- `src/test/java/com/ltz/social/api/controller/FriendsVisibilityTest.java`: checkstyle:MethodName ×10
- `src/test/java/com/ltz/social/api/controller/FriendshipControllerTest.java`: checkstyle:MethodName ×11, pmd:CloseResource ×1
- `src/test/java/com/ltz/social/api/controller/InviteCodeControllerTest.java`: checkstyle:MethodName ×13, pmd:CloseResource ×2
- `src/test/java/com/ltz/social/application/service/BlockConcurrencyTest.java`: checkstyle:MethodName ×4, pmd:CloseResource ×2
- `src/test/java/com/ltz/social/application/service/FriendRequestConcurrencyTest.java`: checkstyle:MethodName ×4, pmd:CloseResource ×1
- `src/test/java/com/ltz/social/application/service/InviteCodeGeneratorTest.java`: checkstyle:MethodName ×3
- `src/test/java/com/ltz/social/application/service/KeysetCursorTest.java`: checkstyle:MethodName ×1
- `src/test/java/com/ltz/social/application/service/OutboxEventsTest.java`: checkstyle:MethodName ×3
- `src/test/java/com/ltz/social/application/service/OutboxRelayTest.java`: checkstyle:MethodName ×3, pmd:CloseResource ×1
- `src/test/java/com/ltz/social/application/service/PendingRequestLimitTest.java`: checkstyle:MethodName ×3
- `src/test/java/com/ltz/social/domain/entity/FriendRequestTest.java`: checkstyle:MethodName ×1
- `src/test/java/com/ltz/social/domain/entity/FriendshipIdTest.java`: checkstyle:MethodName ×2
- `src/test/java/com/ltz/social/domain/entity/InviteCodeTest.java`: checkstyle:MethodName ×4
- `src/test/java/com/ltz/social/domain/entity/OutboxEventTest.java`: checkstyle:MethodName ×2
- `src/test/java/com/ltz/social/infrastructure/persistence/FlywayMigrationTest.java`: checkstyle:MethodName ×3
- `src/test/java/com/ltz/social/infrastructure/security/JwtValidationTest.java`: checkstyle:MethodName ×7
- `src/test/java/com/ltz/social/infrastructure/security/RequestBodySizeLimitTest.java`: checkstyle:MethodName ×1

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/social-service -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/social-service      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/social-service -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/social-service   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
