# ADR 0003 — Recommendation içinde sınırlı Python yardımcısı

Durum: **KABUL — kullanıcı 2026-11-15 tarihinde bu kapsamı açıkça onayladı**.
Tarih: 2026-11-15 · Sahip: Alper · REC-22.

## Karar önerisi

Recommendation Java servisi içinde sabit bir Python script'ini `ProcessBuilder` argüman
listesiyle çalıştırmak. Ayrı servis/sidecar, shell, LLM, bulut çağrısı veya pip paketi yoktur.
Script yalnız CPython standart kütüphanesi ve kapalı konu/intent/yanıt şablonları kullanır.
Oyun seçimi yönlendirmesi, owner donanım assessment açıklaması ve LTZ geçmiş/feedback
kullanım adımları sunulur; kapsamlı serbest metin sohbeti iddiası yoktur.

## Somut dağıtım değişikliği

- Mevcut Recommendation `eclipse-temurin:25-jre-noble` runtime image'ına Ubuntu güvenlik
  güncellemelerini izleyen `python3-minimal` ve gerekli standart kütüphane paketi
  `libpython3-stdlib` eklenmesi; mevcut non-root kullanıcı korunur. Minimal paket tek başına
  JSON modülünü taşımadığı için standart kütüphane ayrıca zorunludur; pip paketi değildir.
- Windows testleri için resmî python.org CPython 3.14 bakım sürümünün embeddable ZIP'i,
  yayımlanan SHA-256 doğrulanarak yalnız ignore edilen `.cache/tools/python/` altına açıldı.
  Sistem kurulumu, registry/PATH veya kullanıcı Python yapılandırması değiştirilmez.
- Docker/host için aynı standart kütüphane protokolü; Python ≥3.12. Yeni ENV anahtarı yoktur.
  Executable yolu uygulama property/JVM `-D` ayarıyla sabitlenir; request'ten alınmaz.
- Runtime yokluğu/hatası 503 ProblemDetail'dır; Java yanıtı Python çalışmış gibi sunmaz.

## Güvenlik sınırları

Kimlik mevcut JWT/rol, owner rızası/ACTIVE denetimi ve quota üzerinden gelir. Python'a
token, kullanıcı/profil kimliği, ham donanım snapshot'ı veya private library/rating gönderilmez.
Donanım için mevcut owner assessment'ın yalnız durum kodları; oyun yönlendirmesinde yalnız
izinli sınırlı tercih bağlamı kullanılır. LTZ_USAGE sabit kullanım adımlarıdır.

Stdin tek JSON; sürüm ve alan allowlist'i iki tarafta kontrol edilir. En çok 8 KiB input,
16 KiB stdout/stderr, en çok 2 eşzamanlı process ve 2 saniye deadline; aşımda process kapatılır.
Child environment temizlenir; yalnız runtime için zorunlu işletim sistemi alanları korunur.
`-I -S -B` izolasyonu, sabit script/argv ve sınırlı çalışma dizini kullanılır. Kullanıcı girdisi
komut, path, Python kodu veya URL olarak yorumlanmaz. Eval/exec/pickle/shell/subprocess/network
script iş mantığında yoktur. Bu, işletim sistemi sandbox'ı olduğu iddiası değildir; güvenilen
repo script'i çalışır. Profil değiştirme/satın alma/arkadaş ekleme/harici fetch yapılmaz.

## Onay gereğinin kaynağı

Master v2 §9.1: “yeni container/sidecar/process için izin gerekiyorsa karar kaydı ve onay
olmadan yürütme.” `docs/architecture.md` §10 bu çalışma modelini KARAR BEKLİYOR sayar;
`docs/SECURITY.md` §19 yeni bağımlılık/mimari değişiklikten önce onay ister.
Kullanıcı onayı: “Onaylıyorum; bu kapsamda uygula”. Runtime kurulumu, Dockerfile ve process
entegrasyonu bu onaydan sonra yapıldı. Yeni ENV/pip/sidecar eklenmedi.

## Uygulama ayrıntısı ve kaynak

Sabit script JAR resources içinden uygulama açılışında yalnız servisin oluşturduğu geçici
dizine çıkarılır; ProcessBuilder'a script dosyası yolu verilir. Windows'ta uzun `-c` script
argümanının kaçış sorunu testte görüldü ve dosya yolu modeliyle giderildi. Kullanıcı girdisi
yalnız stdin JSON'dur; child environment'ta sadece gerekiyorsa SystemRoot korunur. Normal
bean kapanışında geçici script ve dizin silinir; ani OS/process ölümünde kalan dosya kimlik
veya kullanıcı verisi değil yalnız sabit repo kodudur.

Windows test runtime'ı CPython **3.14.10**, resmî 64-bit embeddable ZIP SHA-256:
`90905e64a840557d8c1bc4f5b74db3d7ee85e3ffb217611cac1c7813acea4159`.
[Resmî sürüm, checksum ve lisans bağlantıları](https://www.python.org/downloads/release/python-31410/).
CPython PSF lisanslıdır; ek pip bağımlılığı yoktur. Linux dağıtım paketlerinin güvenlik
güncellemeleri Ubuntu deposundan gelir; Windows sürüm numarasıyla aynı patch numarası
iddia edilmez. İşletim sistemi image taraması JAR dependency gate'inden ayrı raporlanır.
