# KAYIT 01 — GAME ve AUTH ARASINDA KAPANAN TALEPLER

**Kimden:** Hamza (game, library, review) · **Kime:** Kerem (auth-service) · **Kayıt tarihi:** 2026-10-26
**Durum:** HEPSİ KAPANDI

---

## NEYDİ

Hamza'nın Game Auth entegrasyon raporunda (`docs-Game Service/LTZ-HAM-GAME-04-Auth-Entegrasyonu.md`) Auth sahibine yönelik "AUTH OWNER ACTION REQUIRED" etiketli maddeler vardı. Hepsinin güncel durumu burada.

## DURUM TABLOSU

| Talep | Benim cevabım | Durum |
|---|---|---|
| Auth ve Gateway için Sonar bulguları (46 Auth + 3 Gateway) | Temizlendi. Auth ve Gateway'de açık bulgu 0 | KAPANDI |
| Eksik DEV anahtarları | `node ops/generate-auth-dev-keys.mjs` eksik anahtarları üretir (JWT, OTP, TOTP, Admin istemcisi) | KAPANDI |
| Servis token'ı (D4) ve hesap silme sözleşmesi | Uygulandı. Game, Library ve Review uçlarını sözleşmeye uygun yazmış, `sub` tam eşleşme ve `account_id` denetimi dahil | KAPANDI |
| Audit kayıtlarında kişisel veri (nil UUID önerisi) | Kullanıcı ve ben mutabık kaldık. Game'in uygulaması uygun | KAPANDI |
| Silme sonrası eski token'la veri yazma yarışı (bölüm 13.4 madde 6) | Hesap silme artık 14 gün geri alma süresiyle başlıyor. Görevler o süre bitince çalışıyor, o zamana kadar eski token'lar çoktan ölmüş oluyor | KAPANDI |
| İptal yayılımı gecikmesi (D3) | Token ömrü 60 sn, üst sınır 90 sn | KAPANDI (kabul edilen sınır) |
| Admin → Auth servis kimlikli iç uç | Auth tarafı hazır | KAPANDI (Auth tarafı) |

## BİLMEN GEREKEN BİR ŞEY: TESTLERİN AUTH KAYNAĞINA BAĞLI

Game, Library ve Review testleri (`ProductionAuthSupport`, `ProductionServiceTokenSupport`) Auth kaynak dosyalarını derleyip yansımayla çağırıyor. Auth'ta bir imza değişirse senin testin kırılır. Ben bunu bu hafta yakaladım ve önledim. Auth tarafında bu imzaları kilitleyen bir test var. Hangi imzalara bağlı olduğun `docs-auth/README.md` içinde yazılı.
