# api-gateway: güvenlik ve kalite durumu

> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: `backend/config/quality-baseline.json` (2026-10-28). Yenilemek için: `node pre-push/ci/lint-ratchet.mjs docs`.
> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).

**Sahip:** Ortak (sahip atanmadı)  •  **Eski borç:** ✅ yok (temiz)

## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)

1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.
2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.
3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.
4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.
5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).

## Eski borç

Bu serviste dondurulmuş borç yok. Kapı **sıfır yeni ihlal** bekler: yeni kod Checkstyle ve PMD'den temiz geçmeli.

## Doğrulama

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4.1"   # JDK 25 şart
mvn -B -f backend/pom.xml -pl services/api-gateway -DskipTests verify        # raporları üretir
node pre-push/ci/lint-ratchet.mjs check --modules services/api-gateway      # yeni ihlal var mı?
mvn -B -f backend/pom.xml -pl services/api-gateway -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)
```

## Borcu kapatınca

```powershell
node pre-push/ci/lint-ratchet.mjs update --modules services/api-gateway   # kazancı baseline'a kilitler ve bu klasörü yeniler
```

Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit'e ekle.
