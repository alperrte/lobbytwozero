# community-service: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-29). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Nisa (@Camcinisa)  •  **Eski borç:** ⚠️ 6 ihlal, 3 dosya

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç: kural bazında

| Kural | Adet | Ne demek | Nasıl düzeltilir |
|---|---:|---|---|
| `pmd:CloseResource` | 3 | Kaynak (ExecutorService, HttpClient, stream) kapatılmıyor | Testte try-with-resources; üretim kodunda uygulama boyu yaşayan nesneyse gerekçeli çözüm (sahibine sor). |
| `pmd:CyclomaticComplexity` | 1 | Metot/sınıf çok dallı ve karmaşık | Davranışı değiştirmeden küçük metotlara böl; önce mevcut testlerin geçtiğinden emin ol. |
| `pmd:PreserveStackTrace` | 2 | Yeni hata fırlatılırken eski hatanın izi kayboluyor | Eski hatayı sebep olarak ekle. **Güvenlik sınırında** (token, parola, dış servis cevabı) sebep gizli bilgi taşıyabilir: körlemesine ekleme, güvenlik gerekçeli dar çözüm bul. |

## Eski borç: dosya dosya

- `src/main/java/com/ltz/community/application/service/ImageSanitizer.java`: pmd:CyclomaticComplexity ×1, pmd:PreserveStackTrace ×2
- `src/test/java/com/ltz/community/application/service/CommentServiceIntegrationTest.java`: pmd:CloseResource ×2
- `src/test/java/com/ltz/community/application/service/PostLikeServiceIntegrationTest.java`: pmd:CloseResource ×1

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/community-service -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/community-service      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/community-service -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/community-service   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
