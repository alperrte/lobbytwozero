# REC-22 — çalışma ve dağıtım yönergesi

2026-11-15 · Alper · [Kabul edilen ADR 0003](../../docs/decisions/0003-recommendation-python-yardimcisi.md).

## Çalışma modeli

`POST /api/v1/recommendation/assistant` → owner JWT/rıza/ACTIVE/quota → Java bağlam sınırı →
AssistantEngine → PythonAssistantEngine → sabit `assistant_rules.py`. Python standart
kütüphaneli CPython ≥3.12'dir. assistant-v1 protokolü ve assistant-rules-v1 algoritması
uyuşmalıdır. Aynı izinli bağlam aynı rehberi verir; serbest metin anlama/LLM yoktur.

GAME/SELECT_GAME yalnız seçilen türle katalog keşfi ve gereksinim kontrolünü anlatır;
bu mod gerçek Game aday listesi oluşturmaz. Genre değeri bile Python'a taşınmaz, yalnız
genreProvided boolean'ı gider. LTZ_USAGE VIEW_HISTORY/GIVE_FEEDBACK sabit kullanım adımlarıdır.
HARDWARE/ASSESS_GAME tam owner girdisinde mevcut assessment'ı kullanır; Python'a sadece
minimum/recommended/compatibility durum kodları gider. Ham profil, model, UUID ve token gitmez.

## Runtime ve ayarlar

- Dockerfile aynı runtime tabanında `python3-minimal libpython3-stdlib` kurar; JSON modülü
  minimal pakette tek başına bulunmadığı için standart kütüphane paketi zorunludur.
  `--no-install-recommends`, non-root `ltz` ve Java entrypoint korunur; pip kurulmaz.
- Default executable `python3`; host Windows çalıştırmada güvenilir mutlak yol şu application
  property/JVM ayarıyla verilir: `-Dltz.recommendation.assistant.python-executable=C:/.../python.exe`.
  Request veya kullanıcı metni executable/script/çalışma dizini seçemez.
- `ltz.recommendation.assistant.timeout-ms`: varsayılan 2000; 1–2000 kabul edilir.
  Yeni ENV placeholder veya `.env.example` sözleşmesi eklenmedi.
- Windows testlerinde açık onayla indirilen CPython 3.14.10 embeddable paketi
  `.cache/tools/python/3.14.10/python.exe` kullanıldı. Resmî SHA-256 ADR'de kayıtlıdır;
  sistem kurulumu/PATH değişikliği yapılmadı. Runtime Git/image source'una dahil edilmez.
- Linux testlerinde `python3` veya test JVM'ine verilen aynı executable property kullanılır.
  Runtime yoksa test atlanmaz; build başarısız olur. Java testleri gerçek Python başlatır.

## Sınırlar ve hata davranışı

Child argv yalnız güvenilir interpreter, `-I -S -B` ve classpath'ten çıkarılan sabit script
dosyası yoludur. Windows uzun `-c` argümanındaki quoting sorunu dosya modeliyle giderildi.
Stdin UTF-8 JSON; child environment temizdir, Windows için yalnız SystemRoot korunur.
Normal bean kapanışında script/dizin temizlenir. İçerikleri kullanıcı verisi taşımaz.

8 KiB stdin, stdout/stderr ayrı 16 KiB, 2 saniye deadline ve en çok 2 process; dolu slotta
kuyruk oluşturulmadan ASSISTANT_BUSY. Timeout/interrupt/IO/exit/bozuk JSON kontrollü 503
ProblemDetail olur. Child/descendants durdurulur, stream/executor kapatılır; hata cause'u
sunucuda korunur ama response'a aktarılmaz. Ana uygulama sırları child'a miras kalmaz.
Bu sınırlar OS sandbox veya tüm CPU/bellek güvenliği garantisi değildir; çalıştırılan kod
güvenilen repo script'idir ve kullanıcı kodu çalıştırmaz. Python iş mantığında ağ, subprocess,
eval/exec/pickle yoktur; step kodları uygulanan mutasyon değildir.

## Doğrulama

```powershell
& ./.cache/tools/python/3.14.10/python.exe -I -S -B -m unittest discover -s backend/services/recommendation-service/src/test/python -v
```

Maven/disposable CI komutu, Docker build/smoke ve image tarama sonuçları
[TASK 5 test raporunda](../testings/task-05-tests.md); final pre-push/Sonar kararları
[pre-merge raporunda](../testings/pre-merge-quality.md). Native araçlar gerçek `.env.dev`
okuduğu için bu oturumda güvenlik korumasıyla BLOCKED; secretsiz CI PASS onların yerine yazılmaz.
