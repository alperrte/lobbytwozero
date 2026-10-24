# Kerem — Auth Takip Listesi

> Tarih: 2026-10-24 · Auth sahibinin açık işleri ve ekipten beklenenler. `feature/auth-service`, main'le çakışmasız.

## Hemen

1. **`pre-push full dev` + Docker stack doğrulaması.** Bakılacak: Auth gerçek ENV ile açılıyor mu (açılışta 16 hedefin
   `*_SERVICE_URL`'ini doğruluyor), 18 servis UP mı, gitleaks temiz mi.
2. **Main'e PR.** Gateway'e dokunduğu için Alper onayı gerekir.
3. Ekibe haber ([00_OKU_BENI_GONDERIM_PLANI.md](00_OKU_BENI_GONDERIM_PLANI.md)).

## Kerem'in servisleri: user ve mail da silme hedefi

- **user-service**: `DELETE /internal/v1/accounts/{accountId}`, profil, tercih, avatar dosyası; rehberdeki 17 test. En çok kişisel
  veri burada; uç olmadan hiçbir silme tamamlanmaz.
- **mail-service**: aynı uç; gönderim kayıtları/alıcı adresleri silinir veya maskelenir. Mail Service geliştirmesi başlayınca ilk iş.
- Migration gerekirse user/mail aralıkları (`docs/decisions/migrations.md`); auth V61–V64 donuk, yeni auth migration V65+.

## Açık Auth işleri

| # | İş | Not |
| --- | --- | --- |
| 1 | Admin → Auth servis kimlikli iç uç (rol/durum değişikliği) | Şimdilik admin kullanıcı token'ıyla; `/internal` uç Auth'ta yok |
| 2 | B18: iç ağ düz `http` | Prod kararı Alper'le (TLS/mTLS veya ağ politikası) |
| 3 | D3: eski token'ın azami gecikmesi / iptal yayılımı | Açık karar |
| 4 | Audit istisnaları | Game (`game_*_audits`, `actor_id` NOT NULL, aralık dolu → nil UUID önerildi) ve admin denetim kaydı |
| 5 | Silme görevlerini izleme | Dev'de `account_deletion_tasks.last_error` (ör. `SERVICE_ERASURE_HTTP_404` = uç yok); servisler bağlandıkça birlikte kontrol |

## Ekipten beklenen ve takip edilecek

| Kim | Ne | Durum |
| --- | --- | --- |
| Umut | social: `sub` tam + `account_id` denetimi; messaging, lfp uçları | Bildirildi |
| Hamza | game (yeni hedef, REF/audit kararı), library, review uçları | Bildirildi |
| Nisa | community, content, event uçları | Bildirildi |
| Alper | hardware, recommendation, performance uçları; notification/admin sahibi; kapsam kuralı; B18; PR onayı | Bildirildi |
