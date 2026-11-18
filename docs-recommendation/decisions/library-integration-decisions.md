# Library entegrasyonu — Recommendation kararları ve Hamza'ya teslim (REC-LIB)

Tarih: 2026-11-18 · Sahip: Alper · Kaynak talep: `docs-library/request/recommendation-library-Integration-request.md` (30 Ekim 2026, değiştirilmedi)
Durum: Talep metni eskidir (Recommendation için "tek Java dosyası" der). Aşağıdaki kayıt güncel koda göre yazılmıştır.

## 1. Seçilen sözleşme (A.4 teslim şablonu)

1. **Yaklaşım:** Library'nin önerdiği T2/E1 (`library:recommendation:read` + `/service-tokens/delegated`) **seçilmedi**.
   Auth'un yayımlanmış hesaba bağlı `signals:read` sözleşmesi kullanılır; Library kodu zaten buna uyarlıdır
   (`LibraryInternalReadController`, `AccountErasureSecurityConfiguration`: `SCOPE_signals:read`, `account_id` claim'i).
2. **Uç:** `GET {LIBRARY_SERVICE_URL}/internal/v1/library/users/{userId}/recommendation-signals?page=&size=50`.
   Token: Auth `POST /internal/v1/service-tokens`, audience `library-service`, scope `signals:read`, `accountId` = doğrulanmış kullanıcı `sub`'ı.
   Token isteği başına hesap bazlı alınır, kullanıcılar arasında paylaşılmaz (`RecommendationServiceTokenClient.libraryToken`).
3. **Yanıt:** `{items:[{gameId,favorite,completed}],page,size,totalElements}`; yalnız bu alanlar okunur.
4. **Hata davranışı (Recommendation):**
   | Library yanıtı | Recommendation sonucu |
   | --- | --- |
   | 200 geçerli sayfa | kullanılır; sayfa/size/totalElements/öğe sayısı tutarsızsa 503 |
   | 403 | 403 `SOURCE_ACCESS_DENIED`; boş koleksiyon sayılmaz, öneri üretilmez |
   | 401, 5xx, zaman aşımı, 3xx, bozuk JSON, 400/404 | 503 `SOURCE_UNAVAILABLE`; boş liste sayılmaz |
   Yeniden deneme yoktur (istemci tek deneme yapar).
5. **Zaman aşımı:** `RecommendationHttpClient` bağlantı/okuma sınırları; yönlendirme izlenmez; yanıt 256 KB ile sınırlı.
6. **İzin iptali (ZG-11):** Her üretimde User `me/consents` yeniden okunur; `recommendation=false` ise 403 `CONSENT_REQUIRED`.
   Ayrıca Library her istekte User'dan izni yeniden sorar. Etkili olma süresi: **bir sonraki istek**; önbellek yoktur.
7. **Test kanıtı:** Gerçek Auth issuer/JWKS ile `RecommendationApiIntegrationTest` (Library sahte HTTP sunucu). Gerçek Library ile E2E: **NOT VERIFIED**.

## 2. Mimari kararlar

- **D-07 → Seçenek A (her istekte güncel kontrol).** Ham sinyal kalıcı saklanmaz; yalnız oyun kimlikleri o istekte bellekte hariç tutma kümesi olur.
  Öneri geçmişine (REC-18) yalnız önerilen oyunlar yazılır, Library sinyal listesi yazılmaz.
- **D-12 → Seçenek A (eksik veride durdur).** En çok 10 sayfa / 500 sinyal; `totalElements > 500` veya sayfa tutarsızlığı 503'tür.
  Hata kaynaklı eksik veri hiçbir yolda "tam" sayılmaz. Seçenek D (genel öneriye düşme) uygulanmadı: Denied/Unavailable'da istek bilerek durur.
- **Mevcut kullanım:** favori/tamamlanan oyunlar yalnız tekrar önermeyi dışlamak için kullanılır. Favori/tamamlanan **benzerliği** (REC-02/05) 2026-11-18'de ayrıca bağlandı: HttpLibraryTasteSource aynı signals ucunu ve Game GAME-15 tür kodlarını okuyup FAVORITE_AFFINITY/COMPLETED_AFFINITY üretir.

## 3. ZG eşlemesi (Recommendation'ı ilgilendirenler)

ZG-2/7: tüm hata ve 403 yolları ayrı hata koduyla durur. ZG-3/4: kullanıcı JWT'si Library'ye iletilmez; hedef yalnız doğrulanmış `sub`.
ZG-6/11: ayrı rıza + her istekte yeniden okuma. ZG-8: Library DB/public uçları çağrılmaz, Game metadata Library'den alınmaz. ZG-9: token ve oyun listesi loglanmaz.

## 4. REC-LIB durumu (talep belgesindeki `[ ]` kutuları değiştirilmedi)

| Madde | Durum |
| --- | --- |
| 01 karar kaydı | Bu belge |
| 02 sonuç modeli | Tip yerine istisna kodları (403 `SOURCE_ACCESS_DENIED` / 503 `SOURCE_UNAVAILABLE`); eşdeğer ayrım |
| 03 token istemcisi | Auth `signals:read` hesaba bağlı grant; kullanıcı başına token |
| 04 adaptör | `HttpPersonalizationSource.library` — yönlendirme yok, iç yol |
| 05 sayfalama | Seçenek A, 10 sayfa / 500 sinyal |
| 06 öneri entegrasyonu | Hariç tutma ve REC-02/05 benzerlik tüketimi uygulandı |
| 07 izin geri alma | A seçeneği, kalıcı ham sinyal yok |
| 08 IDOR | Hedef yalnız `sub`; parametre yok |
| 09 dayanıklılık | Tek deneme. Talepteki "401'de bir kez yeni token" **bilinçli uygulanmadı**: token her istekte Auth'tan taze (≤60 sn) alınır; aynı hesap için yeniden alınan token sonucu değiştirmez, yalnız Auth/Library yükünü ikiye katlar. 401 → 503 `SOURCE_UNAVAILABLE` |
| 10 belgeler | api.md kaydı mevcut; `docs-security/recommendation-service/NOTLAR.md` güncellendi |
| 11 ENV | Değişiklik yok |
| 12 Hamza'ya bildirim | Bu belge teslim notudur |

## 5. Açık / doğrulanmadı

- Gerçek Auth + User + Library E2E (izin açık 200, kapalı 403, User kapalı 503) koşulmadı → `NOT VERIFIED`.
- Library tarafında LIB-14 kabulü Hamza'nındır; bu belge Library kodunda değişiklik gerektirmez.
- Kapandı: `HttpPersonalizationLibrarySignalsTest` (4 test PASS) 403'ün Denied olduğunu (Game keşfi çağrılmaz), 5xx'te tek deneme yapıldığını, tutarsız/500 üstü sayfaların 503 olduğunu ve yalnız HTTP 200 boş sayfanın Available sayıldığını doğrular.
