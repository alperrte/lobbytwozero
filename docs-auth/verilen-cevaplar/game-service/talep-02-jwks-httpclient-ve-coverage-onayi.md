# TALEP 02 — JWKS HTTPCLIENT YAŞAM DÖNGÜSÜ VE POLICY_REVIEW_REQUIRED ONAYI

**Kaynak:** `LTZ_Hamza_Kerem_Guvenlik_Koordinasyon.md` (26 Ekim 2026) · **Kimden:** Hamza (game, library, review) · **Kime:** Kerem · **Cevap tarihi:** 2026-10-26
**Durum:** CEVAPLANDI — iki konuda da Hamza'nın yapacağı iş var, Auth tarafında ek geliştirme yok

---

## NEYDİ

Hamza güvenlik taramasındaki bulguları kapatmış: Checkstyle 321 → 0, PMD 14 → 3, 349 test geçiyor. Geriye iki şey kalmış. Üç serviste aynı yerde duran PMD `CloseResource` bulgusu ve pre-push'un `POLICY_REVIEW_REQUIRED` diye durması. İkisinde de benim görüşümü istedi ve ben "uygun" demeden dokunmadı.

---

# KONU 1 — JWKS HTTPCLIENT

## NE SORULDU

- Uzun ömürlü `HttpClient`'ı Spring'in yönettiği bir yaşam döngüsüyle kapatmak uygun mu
- Daha güvenli ya da Auth tarafıyla uyumlu bir alternatif var mı
- Auth ya da Gateway sözleşmesiyle çakışan bir şey var mı

## BENİM CEVABIM

**EVET UYGUN. AMA AYRI BİR BEAN YAPMAK YERİNE YAPILANDIRMA SINIFININ İSTEMCİYİ KENDİSİNİN TUTMASINI ÖNERİYORUM.**

Fikir doğru. İstemci JWKS çağrıları arasında yeniden kullanılacak, uygulama kapanırken kapatılacak. Her istekte açıp kapatmak yanlış olurdu. Bağlantı havuzunu bozar ve gereksiz yük bindirir.

Ben bunu kendi tarafımda denedim. Bir deneme kopyasında güvenlik dalının kurallarını ve Game'in main'deki sürümünü kullandım. Hamza'nın dalına ya da kendi dalıma hiçbir şey yazmadım.

Sonuçlar:

- `ServiceTokenDecoderConfiguration` için PMD `CloseResource` kaydı **kayboldu**
- Game'de 57 test geçti: `AccountErasureTest` 10, `AuthJwtContractTest` 30, `RealAuthIntegrationTest` 5, `SecurityBoundaryTest` 9, `ServiceTokenDecoderConfigurationTest` 3
- Yeni yaşam döngüsü testimle 2 test daha geçti: istemci paylaşılıyor, bağlam kapanınca kapanıyor, public-key ve anahtarsız yolda hiç açılmıyor

Dürüst sınırım şu. Hamza'nın dalındaki diğer değişiklikleri görmedim, sadece bu dosyayı denedim. Library ve Review'ın dosyalarını Game'inkiyle karşılaştırdım ve satır satır aynı (fark 0). Yani aynı değişiklik uygulanır. Ama onların testlerini ben koşmadım.

## NEDEN AYRI BEAN DEĞİL

- Testlerde `config.serviceJwtDecoder(...)` doğrudan çağrılıyor (üç test). Ayrı bean yapınca bu metodun imzası değişir ve o testler kırılır
- Yapılandırma sınıfı zaten tek bir örnek. İstemciyi onun tutması ve kapanışta kapatması yeterli
- İstemci sadece JWKS yolunda üretiliyor. Public-key ve anahtarsız (fail-closed) yolda hiç oluşmuyor

## KOD (HER ÜÇ SERVİSTE AYNI)

Eski statik `jwksClient()` yerine:

```java
/** JWKS istemcisi bu yapılandırmaya aittir: bir kez üretilir, tüm JWKS çağrılarında yeniden kullanılır, kapanışta kapatılır. */
private java.net.http.HttpClient jwksHttpClient;

private synchronized org.springframework.web.client.RestOperations jwksClient() {
    if (jwksHttpClient == null) {
        jwksHttpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(750))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
    }
    var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(jwksHttpClient);
    factory.setReadTimeout(Duration.ofMillis(750));
    return new org.springframework.web.client.RestTemplate(factory);
}

@jakarta.annotation.PreDestroy
synchronized void closeJwksClient() {
    if (jwksHttpClient != null) {
        jwksHttpClient.close();
        jwksHttpClient = null;
    }
}
```

Tam yama dosyası bende: `jwks-httpclient-oneri.patch` (36 satır). İstersen sana gönderirim.

## KORUNAN SÖZLEŞMELER

| Konu | Durum |
|---|---|
| Bağlantı ve okuma zaman aşımı 750 ms | Aynı |
| `Redirect.NEVER` | Aynı |
| RS256, issuer, audience, `typ=svc+jwt`, `sub=svc:auth-service`, scope, `account_id` | Hiç dokunulmadı, sadece istemcinin sahibi değişti |
| Anahtarsız yolda fail-closed | Aynı, istemci oluşmuyor |
| Hata mesajlarında token, JWKS yanıtı ya da iç adres sızmaması | Aynı |
| 401, 403, 503 ve D4 silme davranışı | Aynı, 57 testle doğrulandı |
| Auth ve Gateway ile çakışma | Yok. Auth tarafında bir sözleşme değişmiyor |

## RİSKLER

- `HttpClient.close()` devam eden isteklerin bitmesini bekler. İstekler 750 ms ile sınırlı olduğundan kapanış birkaç saniyeyi geçmez
- Java 21 ve üstü gerekir. Proje Java 25
- Testte yapılandırmayı `new` ile oluşturan testler istemciyi kapatmaz. Bu sadece test ömrü için, üretimi etkilemez
- Kuralı kapatma, bastırma ya da analiz dışı bırakma yok. Çözüm yapısal

## ÖNERDİĞİM TEST

Üç serviste de şu testi ekle (Game'de denedim). Yaşam döngüsünü sabitler: istemci paylaşılıyor, bağlam kapanınca kapanıyor, anahtarsız yolda oluşmuyor.

```java
class ServiceTokenDecoderLifecycleTest {

    private static HttpClient client(Object config) throws Exception {
        Field field = ServiceTokenDecoderConfiguration.class.getDeclaredField("jwksHttpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(config);
    }

    @Test
    void jwksClientIsSharedAndClosedWithTheContext() throws Exception {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("ltz.security.jwt.jwk-set-uri", "https://auth.example.test/jwks")));
        context.register(ServiceTokenDecoderConfiguration.class);
        context.refresh();
        var config = context.getBean(ServiceTokenDecoderConfiguration.class);
        assertNotNull(config.serviceJwtDecoder("ltz-auth", "https://auth.example.test/jwks", "", "game-service"));
        HttpClient first = client(config);
        assertNotNull(first);
        config.serviceJwtDecoder("ltz-auth", "https://auth.example.test/jwks", "", "game-service");
        assertSame(first, client(config));
        assertFalse(first.isTerminated());

        context.close();

        assertTrue(first.isTerminated(), "bağlam kapanınca JWKS istemcisi kapanmalı");
        assertNull(client(config));
    }

    @Test
    void publicKeyAndFailClosedModesNeverCreateAClient() throws Exception {
        var config = new ServiceTokenDecoderConfiguration();
        assertNotNull(config.serviceJwtDecoder("ltz-auth", "", "", "game-service"));
        assertNull(client(config));
        config.closeJwksClient();
    }
}
```

Library ve Review'da `game-service` yerine kendi servis adını yaz.

## SENDEN NE BEKLİYORUM

- Üç `ServiceTokenDecoderConfiguration` dosyasında aynı değişikliği yap ve testi ekle
- Strict `clean verify` ile PMD'nin 0'a indiğini doğrula
- Library ve Review'ın mevcut D4 silme testlerini de koş

---

# KONU 2 — POLICY_REVIEW_REQUIRED

## NE SORULDU

Pre-push, testleri başlatmadan `POLICY_REVIEW_REQUIRED: current-head independent repository-owner approval is required` diye duruyor. Onayı kim verecek, nasıl, ve onay mevcut commit'e mi bağlı.

## BENİM CEVABIM

**BU ONAY HAMZA'NIN BRANCH'İNE AİT DEĞİL. GÜVENLİK ALTYAPISININ KENDİ PR'INA AİT. HAMZA'NIN BU KONUDA YAPACAĞI BİR ŞEY YOK.**

Önce kaynağı kodda doğruladım.

**KAYNAK** (`chore/security` dalı, `8ea356b`, `pre-push/ci/coverage.mjs`):

- Satır 155–177, `ownerApprovedPolicy`: onayı kontrol eden fonksiyon
- Satır 197: Hamza'nın gördüğü mesajı üreten yer
- Satır 327: yerelde onay iddia edilmesini engelleyen ikinci kontrol

**NE ZAMAN TETİKLENİR:** `validatePolicy` fonksiyonu (satır 96) `!previous || !same(policy, previous)` döndürüyor. `previous`, ana dalda (`main`) duran `backend/config/coverage-policy.json` dosyası. Bu dosya şu an main'de YOK, sadece `chore/security` dalında var. Yani bu dosyayı taşıyan her kopya "ilk politika" sayılıyor ve onay istiyor. Hamza'nın güvenlik yapılandırmasıyla kurduğu izole kopya tam bu yüzden duruyor.

**KİM ONAYLAR:** Depo sahibi. Depo `github.com/alperrte/lobbytwozero`, yani sahip GitHub hesabı **`alperrte`** (Alper). Kod bunu `GITHUB_REPOSITORY_OWNER` değerinden okuyor. Ek kural: PR'ı açan kişi sahip olamaz. Güvenlik altyapısı dalının yazarı ben olduğum için bağımsız onay veren Alper olur.

**NASIL:** GitHub'da `chore/security` PR'ında Alper "Approve" verir. Kod bunu yalnızca GitHub Actions içinde, GitHub'ın yorumlar listesinden okuyor. Yerelde hiçbir yolla onay üretilemez. Mesajdaki "no local bypass" ifadesi bunu söylüyor.

**HEAD'E BAĞLI MI:** Evet. Kod en son onayın `commit_id` değerinin PR'ın güncel head commit'iyle aynı olmasını arıyor. PR'a yeni commit eklenirse onay yeniden gerekir.

## HAMZA NE YAPACAK

**Hiçbir şey.** Şu sırayla çözülür:

1. Ben `chore/security` için PR açarım
2. Alper PR'ı güncel commit üzerinde onaylar
3. PR main'e girer. Politika dosyası artık main'dedir
4. Hamza `origin/main`'i kendi dalına alır. Bu durumda `previous` ile politika aynıdır, onay gerekmez (politika değişmediği sürece)
5. Pre-push normal çalışır: `.\pre-push\pre-push.cmd service` ya da `backend`

O zamana kadar güvenlik yapılandırmalı izole kopyada yerel pre-push bu mesajla duracak. Bu beklenen bir durum. Testlerin 349'unun geçtiği ve kapsamın %96–98 olduğu zaten ayrı koşularla kanıtlı.

## DOĞRULAMADIKLARIM

- GitHub tarafındaki depo ayarlarına (zorunlu inceleme kuralları, kimlerin onaylayabildiği) bakamıyorum. Yukarıdakiler kod ve depo adresinden çıkarım
- Alper'in hesabının Actions içinde `GITHUB_REPOSITORY_OWNER` olarak gerçekten `alperrte` döndüğünü teyit etmedim

## BYPASS YOK

Kimse `coverage-policy.json` eşiklerini değiştirmeyecek, pre-push'u atlamayacak ve sahte PASS üretmeyecek. Hamza bunları yapmadı, doğru olan da buydu.

---

## ORTAK NOTLAR

- **Bağımlılık açıkları (CVE):** `backend/pom.xml` benim. Hamza değiştirmesin. Auth'taki üç sürüm satırını ortak dosyaya ben taşıyacağım
- **D3 (eski token):** Token ömrü 60 saniye, üst sınır 90 saniye. Detay `docs-auth/acik-guvenlik-borclari.md` içinde B2
- **D4 gerçek DEV ortamı:** Silme ucu 5/16 serviste hazır. Ortak deneme için bkz. `../hardware-service/talep-03-gercek-dev-silme-kabulu.md`
- **Auth'ta aynı bulgu:** `HttpAccountDataEraser` ve `OAuthConfig`'te benim de aynı iki `HttpClient` bulgum var. Aynı kalıpla ben de çözeceğim
