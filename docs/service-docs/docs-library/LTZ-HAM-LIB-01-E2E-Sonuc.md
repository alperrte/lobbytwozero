# Library Faz 1 — Gerçek ltz-dev E2E kanıtı

UTC: 2026-10-30T14:03:32.390Z

Sonuç: PASS
Kontroller: 62/62

Pre-push ve SonarQube çalıştırılmadı; kullanıcı tarafından manuel yapılacaktır.

## Beklenen ve gerçekleşen sonuçlar

- [x] Real Game batch fixture HTTP: beklenen=200, gerçekleşen=200
- [x] Canonical test game available: beklenen=624a50d2-d41a-4404-8fba-451ce01a1e5c, gerçekleşen=624a50d2-d41a-4404-8fba-451ce01a1e5c
- [x] A register HTTP: beklenen=202, gerçekleşen=202
- [x] A email verification HTTP: beklenen=204, gerçekleşen=204
- [x] A CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] A login HTTP: beklenen=200, gerçekleşen=200
- [x] A session CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] B register HTTP: beklenen=202, gerçekleşen=202
- [x] B email verification HTTP: beklenen=204, gerçekleşen=204
- [x] B CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] B login HTTP: beklenen=200, gerçekleşen=200
- [x] B session CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] A POST collect HTTP: beklenen=200, gerçekleşen=200
- [x] New favorite false: beklenen=false, gerçekleşen=false
- [x] New completed false: beklenen=false, gerçekleşen=false
- [x] A POST collect HTTP: beklenen=200, gerçekleşen=200
- [x] Repeated add stable entry: beklenen=9aac380b-2b27-4b56-a113-dc73d912dd46, gerçekleşen=9aac380b-2b27-4b56-a113-dc73d912dd46
- [x] Repeated add one DB row: beklenen=1, gerçekleşen=1
- [x] A PUT /624a50d2-d41a-4404-8fba-451ce01a1e5c/favorite HTTP: beklenen=200, gerçekleşen=200
- [x] Favorite persisted: beklenen=true, gerçekleşen=true
- [x] A PUT /624a50d2-d41a-4404-8fba-451ce01a1e5c/completed HTTP: beklenen=200, gerçekleşen=200
- [x] Completed persisted: beklenen=true, gerçekleşen=true
- [x] Completed preserves favorite: beklenen=true, gerçekleşen=true
- [x] B POST collect HTTP: beklenen=400, gerçekleşen=400
- [x] B cannot select A via path HTTP: beklenen=403, gerçekleşen=403
- [x] B DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] B delete preserves A entry: beklenen=1, gerçekleşen=1
- [x] B PUT /624a50d2-d41a-4404-8fba-451ce01a1e5c/favorite HTTP: beklenen=200, gerçekleşen=200
- [x] B auto-collects own row: beklenen=1, gerçekleşen=1
- [x] B auto favorite only: beklenen=false, gerçekleşen=false
- [x] A flags unaffected by B: beklenen=true, gerçekleşen=true
- [x] B DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] A DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] Delete removes collection and flags: beklenen=0, gerçekleşen=0
- [x] A DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] A PUT /624a50d2-d41a-4404-8fba-451ce01a1e5c/completed HTTP: beklenen=200, gerçekleşen=200
- [x] Auto complete only: beklenen=false, gerçekleşen=false
- [x] A DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] A POST collect HTTP: beklenen=404, gerçekleşen=404
- [x] A PUT /ef884669-3b4f-49e4-82e8-690ec495a5ac/favorite HTTP: beklenen=404, gerçekleşen=404
- [x] Invalid JWT HTTP: beklenen=401, gerçekleşen=401
- [x] Cookie without CSRF HTTP: beklenen=403, gerçekleşen=403
- [x] Valid JWT Bearer through Gateway HTTP: beklenen=200, gerçekleşen=200
- [x] A DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] A POST collect HTTP: beklenen=503, gerçekleşen=503
- [x] A PUT /624a50d2-d41a-4404-8fba-451ce01a1e5c/favorite HTTP: beklenen=503, gerçekleşen=503
- [x] Outage writes no row: beklenen=0, gerçekleşen=0
- [x] A POST collect HTTP: beklenen=200, gerçekleşen=200
- [x] A DELETE /624a50d2-d41a-4404-8fba-451ce01a1e5c HTTP: beklenen=204, gerçekleşen=204
- [x] A CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] A login HTTP: beklenen=200, gerçekleşen=200
- [x] A session CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] A cleanup request HTTP: beklenen=202, gerçekleşen=202
- [x] A cleanup confirm HTTP: beklenen=202, gerçekleşen=202
- [x] A test mailbox empty: beklenen=0, gerçekleşen=0
- [x] B CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] B login HTTP: beklenen=200, gerçekleşen=200
- [x] B session CSRF HTTP: beklenen=204, gerçekleşen=204
- [x] B cleanup request HTTP: beklenen=202, gerçekleşen=202
- [x] B cleanup confirm HTTP: beklenen=202, gerçekleşen=202
- [x] B test mailbox empty: beklenen=0, gerçekleşen=0
- [x] Only test game removed: beklenen=0, gerçekleşen=0

## Temizlik

- Game restored: health 200
- A: Auth deletion requested through API; existing 14-day grace period retained
- B: Auth deletion requested through API; existing 14-day grace period retained
- Scoped Library test rows and Game fixture removed; existing databases/volumes retained

Parola, OTP, JWT, cookie ve silme token değerleri bu rapora veya loglara yazılmadı.
E2E geçerli Auth oturumlarını kullandı; Auth DB üzerinde SQL veya süre/politika değişikliği yapılmadı.
Faz 1 otomatik kabul testleri geçerlidir; E2E eksikse ayrıntı ana sonuç raporunda belirtilir.
Faz 1: 5/5 · Genel: 5/18

## Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü

- [x] LIB-01
- [x] LIB-02
- [x] LIB-12
- [x] LIB-13
- [x] LIB-15

## Faz 2 — Favori ve Bitirildi İşlemleri

- [ ] LIB-03
- [ ] LIB-04

## Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik

- [ ] LIB-05
- [ ] LIB-06
- [ ] LIB-07
- [ ] LIB-08
- [ ] LIB-09
- [ ] LIB-16

## Faz 4 — Gizlilik ve Servis Entegrasyonları

- [ ] LIB-10
- [ ] LIB-11
- [ ] LIB-14
- [ ] LIB-18

## Faz 5 — Arşivlenen Oyunlarda Dayanıklılık

- [ ] LIB-17
