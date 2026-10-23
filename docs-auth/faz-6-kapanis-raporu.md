# LTZ-KER-AUTH-06 — Faz 6 Kapanış Raporu (Güvenli Hesap Silme ve Servis Koordinasyonu)

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-16, AUTH-17

## 1. Durum

| Görev | Durum | Not |
| --- | --- | --- |
| AUTH-16 Hesap silme (Auth tarafı) | IMPLEMENTED | Yeniden doğrulama, e-posta token'ı, açık onay, oturum iptali, idempotent talep, koordinasyon, anonimleştirme |
| AUTH-16 Diğer servislerde silme | **PENDING** | Hedef servislerde silme ucu ve servis kimliği (D4) yok; görevler PENDING kalır, hesap `DELETED` olmaz |
| AUTH-17 Servis sınırları ve sözleşmeler | HAZIR (sözleşme) / **PENDING** (entegrasyon) | `servis-sozlesmeleri.md`; karşı servisler geliştirilmedi, tamamlanmış gösterilmedi |

## 2. Tasarım

- **Yeniden doğrulama:** parolalı hesapta parola; TOTP etkinse ikinci faktör (replay korumalı, tek kullanımlık); parolasız sosyal hesapta son 10 dk içinde açılmış oturum (`REAUTHENTICATION_REQUIRED`). Denemeler hesap bazında sınırlı. ADMIN hesabı silinemez (önce rol kaldırılır).
- **Onay:** 256 bit, 30 dk, tek kullanımlık e-posta token'ı (özet saklanır, hesaba bağlı) + kullanıcı adının yazılması. Başarıda hesap `DELETION_REQUESTED`, tüm oturumlar iptal, hesap başına **tek** talep + her servis için görev (eşzamanlı 3 onaydan 1'i başarılı).
- **Koordinasyon:** yalnız **dışa doğru** idempotent çağrı (`AccountDataEraserPort`); onay alan iç uç yok. Görev kiralama + SKIP LOCKED, çağrı işlem dışında, üstel geri çekilme (1 dk → 6 sa), yalnız başarısız görev tekrarlanır, son onay tek işlemde (talep kilitli) tamamlama yapar. Testlerle doğrulandı: kısmi hata, geri çekilme, kira, 4 eşzamanlı işlemci (her servis 1 kez çağrılır).
- **KVKK ayrımı:** silinen — e-posta, kullanıcı adı, parola özeti, harici kimlikler, TOTP, kurtarma kodları, oturumlar, tüm token'lar; saklanan — güvenlik olayları, durum/rol geçmişi, talep/görev kayıtları, anonim hesap iskeleti.
- **Migration/ENV:** `V64` (silme token, talep, görev tabloları; `ACCOUNT_DELETION` oturum nedeni `V62`'de). V65–V70 boş. Yeni ENV anahtarı **yok**; politika `ltz.auth.deletion.*` (tip güvenli, doğrulamalı; prod'da https bağlantı zorunlu). Yeni bağımlılık yok. Ortak dosya (Gateway, Compose, parent pom) değiştirilmedi.

## 3. Kontroller (bu faz kapsamı)

| Araç | Sonuç |
| --- | --- |
| Gitleaks | Kaynak, doküman, `.env.example`, Compose temiz; yalnız Git dışı `.env*`/`target/`/`logs/` yanlış pozitifleri (önceki fazlarla aynı) |
| PMD 7.17 | 0 ihlal |
| SpotBugs 4.9.8 + FindSecBugs 1.14 | Yeni açık yok. `SQL_INJECTION_SPRING_JDBC` (AuthDataEraser) **düzeltildi** → tamamen sabit SQL ifadeleri. Kalanlar önceki yanlış pozitifler |
| Trivy 0.76 | 0 HIGH/CRITICAL (Tomcat 11.0.25 ve Jackson 2.21.7/3.1.7 yamaları korunuyor) |
| Hardcode/magic/ENV | Süreler, hedefler, geri çekilme `application.properties`'te ve doğrulamalı; sabitler adlandırılmış; güvensiz varsayılan yok |

Kapsamlı repository güvenlik denetimi bu fazda yapılmadı (altı faz ve ortak Security güncellemesi sonrasına planlandı).

## 4. Açık maddeler

Tümü `docs-auth/acik-guvenlik-borclari.md` içinde izleniyor (B1–B17): güvenilir IP (K8), D3, Gateway CORS/Bearer, D4, production Mail, hedef servis silme uçları, ilk ADMIN, BANNED/süreli askı, saklama süresi temizliği, hesap bağlama, parolasız hesap, bildirim e-postaları, diğer servislerin bağımlılık yamaları.
