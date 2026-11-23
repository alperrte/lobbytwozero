# Auth Talebi 02 — Hesap silmede anonimleştirme kararının onayı ve dev doğrulaması

> Tarih: 2026-10-27 · Gönderen: Nisa (community-service) · Muhatap: Kerem (Auth) · Durum: **CEVAPLANDI (2026-10-28); rapor saklama süresi Alper'de, dev doğrulaması bekliyor (`auth-requests-04.md`)**
> Kaynak kural: `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md` §3 ("Saklama istisnası ... Auth sahibiyle birlikte kararlaştırılır") ve `04_NISA_community_content_event.md` ("Anonim saklama seçersen ... seçimi servis belgene yaz ve bana haber ver").

## 1. Ne yaptık

Community `DELETE /internal/v1/accounts/{accountId}` ucunu uyguladı. Hesabın kişisel verisi tek işlemde **silinir ya da anonimleştirilir**; uç `204` döner (idempotent). Ayrıntılı tablo `auth-integration-02.md` §4'te.

Kısaca:

| Veri | Karar | Gerekçe |
| --- | --- | --- |
| Hesabın gönderileri ve yorumları | **Anonimleştirilir**: metin `[removed]`, yazar "silinmiş hesap" kimliği (`00000000-…`), durum `DELETED`, görseller silinir | Başkalarının yorumları/yanıtları/repostları bu satırlara FK ile bağlı; başkasının verisi silinmez (Kerem'in notu: "başkasının gönderisi silinmez") |
| Hesabın beğenileri | **Silinir**, gönderi sayacı düşer | Satır yalnızca bu hesabın verisi |
| Hesabın yaptığı raporlar | **Silinir** | Raporlayanın kimliği kişisel veri |
| Hesabın içeriği hakkındaki raporlar | **Saklanır**, yalnız yazar referansı anonimleşir | Moderasyon kaydı (hukuki/denetim) anlamlı kalsın; kişi tanımlanmaz |
| Engellemeler (social yansıması) | **Silinir** | Yalnız bu hesabın ilişkisi |
| Outbox olayları | Hesabın kimliğini taşıyan **hepsi silinir** | Rehber §3 son madde |
| Başkasının hesabın gönderisine yorum/beğenisi | **Dokunulmaz** | Başkasının verisi |

## 2. İstenen onay (3 nokta)

1. **Anonimleştirme yeterli mi?** `author_id` yerine sabit "silinmiş hesap" UUID'si ve `[removed]` metni, "kişisel veri kalmadı" (`204`) anlamını taşıyor kabul ediliyor mu? Satırların kimliği (UUID), oluşturulma zamanı ve (varsa) oyun/forum bağlantısı kişiyi tanımlamaz; yine de Auth sahibinin onayını istiyoruz.
2. **Rapor istisnası:** Hesabın içeriği hakkındaki raporlar (gerekçe kodu, tarih, hedef içerik kimliği) anonim biçimde saklanıyor. Bu, rehberdeki "hukuki gerekçe" istisnası olarak kabul edilebilir mi, yoksa tamamen silinmeli mi? (Tamamen silme, çözülmüş moderasyon kararlarının izini de kaldırır.)
3. **Silinen hesabın yorum yer tutucuları:** Yanıtı olan bir yorum anonim yer tutucu olarak listelenmeye devam eder (içerik ve yazar yok, yalnız yanıt ağacı korunur). Bu "anonim saklama" olarak kabul ediliyor mu?

> **Cevap (Kerem, 2026-10-28):** Sabit "silinmiş hesap" kimliği ve `[removed]` Auth sözleşmesiyle **çelişmiyor** (§2.1, §2.3 uygun). Rapor saklama süresi/hukuki gerekçe (§2.2) **Alper'in kararı**; netleşene kadar raporlar anonim saklanır, süre ayarlanabilir tutulur. Dev doğrulaması için silme bekleme süresi (14 gün) dev'de `PT0S` yapılacak ve yeni Auth + güncel community imajı gerekir.

## 3. Doğrulama talebi (dev)

Rehber §6.5: "dev'de bir test hesabı silinip o servisin görevinin `CONFIRMED` olması birlikte kontrol edilir." Community ucu hazır; Auth tarafında test hesabı silindiğinde `account_deletion_tasks` içinde `community` görevinin `CONFIRMED` olduğunu birlikte görmek istiyoruz. Ön koşullar: dev ortamında community-service container'ının güncel imajla çalışması ve Auth'un `COMMUNITY_SERVICE_URL`'e ulaşması.

## 4. Neden önemli

Auth, tüm hedefler `204` dönmeden hesabı `DELETED` yapmaz. Community onaylamazsa hesap silme `DELETION_REQUESTED` durumunda kalır. Yanlış anonimleştirme kararı ya hukuki risk (kişisel veri kalması) ya da gereksiz veri kaybı (başkalarının içeriğinin silinmesi) yaratır; bu yüzden karar Auth sahibiyle birlikte verilir.

## 5. Kabul kriteri

- Kerem §2'deki üç noktayı yazılı onaylar (ya da değişiklik ister). Onay `auth-integration-02.md` §4'e işlenir.
- Dev'de test hesabı silme: `community` görevi `CONFIRMED`.

## 6. Kerem'e iletilecek sorular

1. Sabit "silinmiş hesap" kimliği (`00000000-0000-0000-0000-000000000000`) kabul edilebilir mi, yoksa Auth ortak bir değer mi belirliyor (başka servislerle tutarlılık için)?
2. Rapor kayıtlarını anonim saklamak (§2.2) için bir saklama süresi/hukuki gerekçe var mı?
3. Silme çağrısı 5 sn sınırına karşı: community tek işlemde bitiriyor (set tabanlı). Çok büyük bir hesapta `503` + kısmi ilerleme isteniyor mu, yoksa tek işlem yeterli mi?

## 7. Etkilenen community kodu (bilgi için)

`api/controller/AccountErasureController`, `application/service/AccountErasureService`, `application/port/AccountErasurePort` (sabit kimlik ve yer tutucu metin), `infrastructure/persistence/AccountDataEraser` (SQL).
