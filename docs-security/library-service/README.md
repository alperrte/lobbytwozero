# library-service: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-28). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Hamza (@HmzT270)  •  **Eski borç:** ⚠️ 21 ihlal, 4 dosya

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç: kural bazında

| Kural | Adet | Ne demek | Nasıl düzeltilir |
|---|---:|---|---|
| `checkstyle:AvoidStarImport` | 20 | Yıldızlı import (`import x.*;`) | Her sınıfı tek tek import et. IDE'de "Optimize Imports"; IntelliJ'de "Class count to use import with '*'" değerini 999 yap. |
| `pmd:CloseResource` | 1 | Kaynak (ExecutorService, HttpClient, stream) kapatılmıyor | Testte try-with-resources; üretim kodunda uygulama boyu yaşayan nesneyse gerekçeli çözüm (sahibine sor). |

## Eski borç: dosya dosya

- `src/main/java/com/ltz/library/infrastructure/security/ServiceTokenDecoderConfiguration.java`: pmd:CloseResource ×1
- `src/test/java/com/ltz/library/api/controller/AccountErasureTest.java`: checkstyle:AvoidStarImport ×13
- `src/test/java/com/ltz/library/infrastructure/security/ProductionServiceTokenSupport.java`: checkstyle:AvoidStarImport ×4
- `src/test/java/com/ltz/library/infrastructure/security/ServiceTokenDecoderConfigurationTest.java`: checkstyle:AvoidStarImport ×3

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/library-service -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/library-service      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/library-service -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/library-service   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
