# PR #13 çakışma düzeltmesi

Tarih: 2026-10-24. Kullanıcı Game dosyalarında `main` sürümünün korunmasını ve ortak kalite testinin gerekli kontroller kaybedilmeden düzeltilmesini istedi.

## Karşılaştırılan sürümler

- PR: https://github.com/alperrte/lobbytwozero/pull/13
- Feature HEAD: `685b9c238c9938ef40ac8e2ea41f86adcbb53798`.
- GitHub `main`: `6aacda9545ed3ad206cc49b7ad506a992e582c53`. Yerel `main` ve `origin/main` güncel olmadığı için kaynak alınmadı.
- Ortak taban: `be5f205b1753189060f12f1017cae53c0105deda`.

## Hazırlanan dosyalar

`docs-game` altındaki 15 belge, `main`deki `docs-Game Service` yollarına geri alındı. Güncellenen Game Auth entegrasyon belgesi dahil 15 dosyanın Git blob SHA-1 değeri GitHub `main` ile birebir doğrulandı. Yalnız taşınmış kopyalar kaldırıldı; Game servisinin kaynak kodu değiştirilmedi. Game'in `main`deki kaynak güncellemeleri PR birleştirmesinde korunur.

`pre-push/quality.test.mjs` içinde `main`in eksik Compose profilinde Maven başlamadan hata verilmesini doğrulayan testi ve ilgili Docker fixture davranışı korundu. Feature tarafındaki parola maskeleme ve Sonar ortamı izolasyonu kontrolleri korundu; `DEBUG` izolasyonu ayrı teste taşındı. Ortak taban ve güncel `main` içeriğiyle, LF normalizasyonundan sonra salt okunur `git merge-file -p` provası exit 0 verdi; prova sonucu hazırlanan test dosyasıyla aynı.

## Doğrulama

- Pre-push kalite/menu/runtime/backend-env/hygiene, Sonar runner ve Hardware Auth E2E yardımcı testleri: **69 PASS**, hata ve atlama yok. Bunlar runner/yardımcı regresyon testleridir; canlı servis E2E tekrarı değildir.
- `.\pre-push\pre-push.cmd hardware-service`: **BACKEND TEST CHECK PASSED**; Maven verify PASS, 356 test, hata/atlama 0, JaCoCo **%98,87**. Bitiş: `2026-10-24 19:45:52 +03`.
- Kanıt: `logs/pre-push-2026-10-24T16-45-05-798Z-39576-409e3749.log`.
- İlk sandbox denemesi JDK `java.security` erişimi nedeniyle testlerden önce başarısız oldu; gerekli erişimle yeniden çalıştırılan yukarıdaki koşu başarılıdır.
- `git diff --check`: PASS.

Bu bakımda tüm backend pre-push ve gerçek Sonar analizi yeniden çalıştırılmadı. Önceki Sonar kaydı geçerliliğini korur: Hardware 0, Social 17 bulgu; genel gate FAIL / A-C-A. Social kaynaklarına ve gate politikasına dokunulmadı. Genel entegrasyon teslimi **EXTERNAL_QUALITY_BLOCKED** kalır.

## Kullanıcının Git adımı

Değişiklikler yalnız çalışma dosyalarında hazırlandı. Index, branch geçmişi ve uzak PR değiştirilmedi; `git add/merge/commit/push` çalıştırılmadı. Kullanıcı bu düzeltmeleri mevcut feature branch'inde commit edip push edince aynı PR #13 güncellenir; yeni PR gerekmez. GitHub'ın uzak mergeability ve CI sonucu push sonrasında yeniden kontrol edilmelidir. Hedef `main` ilerlerse yeni değişiklikler ayrıca değerlendirilir.
