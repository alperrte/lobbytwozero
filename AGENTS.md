# AGENTS.md — LTZ (LobbyTwoZero) Yapay Zekâ Ajan Kuralları

Bu dosya, bu repoda çalışan tüm yapay zekâ ajanları (Claude Code, Codex, Cursor, Copilot vb.) için **bağlayıcıdır**. Kurallar arasında çelişki olursa sıra: kullanıcının o anki açık talimatı > bu dosya > `docs/` altındaki belgeler.

---

## 1. Oturum başlangıcı: kimin görevi?

Kullanıcı oturumun başında kim olduğunu ve kimin görevlerinin yapılacağını söyler. Örnek: _"Ben Alper'im, Alper'in tasklarını yapıyoruz."_

İsim söylenmediyse **kod yazmadan önce sor**. İsim aşağıdaki tablodan eşleştirilir:

| İsim | Görev dosyası | Servisler | Görev kimlikleri | Migration aralığı |
| --- | --- | --- | --- | --- |
| Alper | `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Alper.md` | hardware, recommendation, performance (+ performance web) | HW-, REC-, PERF-, PWEB- | V1–V30 |
| Hamza | `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Hamza.md` | game, library, review | GAME-, LIB-, REV- | V31–V60 |
| Kerem | `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Kerem.md` | auth, user, mail | AUTH-, USER-, MAIL- | V61–V90 |
| Nisa | `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Nisa.md` | community, content, event | COM-, CONT-, EVT- | V91–V120 |
| Umut | `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Umut.md` | social, messaging, lfp | SOC-, MSG-, LFP- | V121–V150 |

- `notification-service` (V151–V160) ve `admin-service` (V161–V170) **ortak** servislerdir. Bu servislerde değişiklik yapmadan önce kullanıcıya sorumluluğun kimde olduğunu sor.
- Kullanıcının sahibi olmadığı bir servisin kodunu, DB'sini veya migration'ını değiştirme. Gerekirse yalnız öner ve sahibini belirt.

## 2. Zorunlu okuma sırası

Her yeni görevde, kod yazmadan önce sırasıyla oku:

1. **Kişinin görev dosyası**: `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_<İsim>.md`. Üzerinde çalışılan görev kimliğini (ör. `HW-03`) bul ve kabul kriterlerini çıkar.
2. **Genel gereksinimler**: `docs/gereksinimler/LTZ Gereksinim Dokümanı .docx`.
   - Doğrudan okunamazsa metni scratchpad'e çıkar ve oradan oku. Örnek komut: `python -I -c "import zipfile,re,sys;x=zipfile.ZipFile(sys.argv[1]).read('word/document.xml').decode();print(re.sub(r'<[^>]+>',' ',x.replace('</w:p>','\n')))" "docs/gereksinimler/LTZ Gereksinim Dokümanı .docx" > <scratchpad>/gereksinim.txt`.
   - Bu docx kısmen eskidir (MSSQL / Vite / Java 21 der). Teknoloji konusunda repo kodu geçerlidir.
3. **Kural belgeleri.** Bu belgelere uymak zorunludur:
   1. [docs/SECURITY.md](docs/SECURITY.md)
   2. [docs/architecture.md](docs/architecture.md)
   3. [docs/database.md](docs/database.md)
   4. [docs/er-diagram.md](docs/er-diagram.md)
   5. [docs/folder-structure.md](docs/folder-structure.md)
   6. [docs/web-rules.md](docs/web-rules.md). Frontend işi varsa bu belge zorunludur. Backend işinde de API sözleşmesi için göz at.
4. **Migration yazılacaksa:** [docs/decisions/migrations.md](docs/decisions/migrations.md). Ayrıntı için bkz. §4.
5. **API kataloğu:** [docs/api.md](docs/api.md). Başka servisin API'sini tüketmeden önce sözleşmeyi buradan oku. Kendi API'ni yazınca buraya kaydet (bkz. §4.1).

**Kaynak önceliği:** repo kodu > `servis-gereksinimleri` > `gereksinimler` (docx). Çelişkiyi sessizce çözme; kullanıcıya bildir.

## 3. Teknik çerçeve (özet)

- **Backend:** Java 25, Spring Boot 4.1.1, Maven multi-module (`backend/pom.xml`, `com.ltz`).
  - 17 servis + `api-gateway` bulunur: `backend/services/<ad>-service`.
  - Her servisin kendi PostgreSQL 17 DB'si vardır (`<ad>_db`). Servisler birbirinin DB'sine erişmez.
  - Servisler arası senkron çağrılar RestClient ile yapılır. Asenkron iletişim için outbox/inbox kullanılır (broker kararı beklemede).
- **Gateway:** `/api/v1/<ad>/**` → `<ad>-service`.
- **Frontend:** Next.js 16, React 19, TypeScript, Tailwind 4, next-intl (`tr` varsayılan, `en`).
  - Çağrı akışı: component → hook → `features/<x>/services` → `shared/lib/api/client.ts`.
- **Katman ve klasör kuralları:** [folder-structure.md](docs/folder-structure.md). Yeni dosyayı oradaki konumdan başka yere koyma.

## 4. Migration kuralları (özet; asıl kaynak `docs/decisions/migrations.md`)

- Her migration'dan önce `docs/decisions/migrations.md` dosyasını oku.
- Yalnız servisin kendi aralığındaki numarayı kullan. Örnekler: hardware V1–V10, auth V61–V70, social V121–V130.
- Dosya adı formatı `V{NUMARA}__{aciklama}.sql` olmalıdır (ör. `V1__create_hardware_tables.sql`).
- Dosya konumu: `backend/services/<ad>-service/src/main/resources/db/migration/`.
- Numara seçmeden önce klasördeki mevcut migration'ları listele ve aralıktaki bir sonraki boş numarayı al.
- Uygulanmış (mevcut) migration dosyasını **asla değiştirme**. Değişikliği yeni bir migration ile yap.
- Aralık dolarsa yeni numara uydurma; kullanıcıya bildir (ekip kararı gerekir).
- Tablo ve kolon kuralları için [database.md](docs/database.md) geçerlidir:
  - PK olarak `uuid`, zaman kolonlarında `timestamptz` (UTC) kullanılır.
  - Kilitleme için `version bigint` eklenir.
  - Durum kolonları `varchar` + `CHECK` olarak tanımlanır.
  - Başka servise ait kimlikler FK değil, REF kolonudur.
- `ddl-auto=validate` kullanılır. Bu nedenle her entity değişikliğinin bir migration karşılığı olmalıdır.

## 4.1 API kaydı — ZORUNLU (`docs/api.md`)

- Oluşturulan, değiştirilen veya kaldırılan **her endpoint**, aynı görevde [docs/api.md](docs/api.md) içine kendi servisinin `## <SERVİS>` başlığı altında yazılır. Kayıt eksikse görev bitmiş sayılmaz.
- Kayıt api.md §2'deki şablonla yazılır ve şu alanları içerir:
  - `Durum`, `Sorumlu`, `Tüketici servisler`
  - `Endpoint`, `Yetkilendirme`
  - `Path/Query Parameters` ve `Request Body` (varsa)
  - `Response`, `Hata Durumları`, `Bağlantı`, `Test Durumu`
- Yalnız kendi servisinin bölümünü düzenle. Başka servisin kaydına dokunma; gerekiyorsa sahibine bildirilmesini öner.
- `Durum` ve `Test Durumu` gerçeği yansıtmalıdır. Test yoksa `NOT IMPLEMENTED` yaz, kod yazılmadıysa `PLANNED` bırak.
- Sözleşmeyi bozan değişiklikte eski kaydı `DEPRECATED` yap ve kullanıcıya tüketici servisleri bildir.
- Örnek JSON'lara gerçek kişisel veri, token veya gizli değer yazılmaz.

## 5. Güvenlik kuralları (özet; asıl kaynak `docs/SECURITY.md`)

- Şu dosyaları **okuma, açma, içeriğini yazdırma veya kopyalama**: `.env`, `.env.dev`, `.env.*.bak`, `frontend/.env.local` ve gerçek değer içerebilecek her dosya. `ops/env.*` çıktısını loglama.
- ENV dosyalarında değişiklik (yeni değişken dahil) **yalnız kullanıcı onayıyla** yapılır. Önce `.env.example` için öneri sun.
- Gizli değer, token veya parola koda, teste, loga ya da belgeye yazılmaz.
- Her endpoint'te kimlik doğrulama, yetki ve sahiplik (IDOR) kontrolü yapılır. Girdi doğrulanır. Hata yanıtları `ProblemDetail` formatında döner.
- Kullanıcı içeriği (UGC), dosya yükleme ve dış sağlayıcı çağrıları için SECURITY.md'deki ilgili bölüm uygulanır.

## 6. Kalite ve doğrulama

- Değiştirdiğin kod için test yaz. Test konumları [folder-structure.md](docs/folder-structure.md) §3.1 ve §5.1'de tanımlıdır.
- Backend coverage için tek politika `backend/config/coverage-policy.json` ve `docs/engineering.md` içindedir. İncelenmiş bootstrap modüller baseline'da gerileyemez; yeni/değişen ve bootstrap dışı executable Java satırları minimumu sağlamalıdır. Strict modül ve yayınlama hedefi toplam en az %80'dir. Maven verify ve Node runner aynı merkezi gate'i uygular; manifest değişikliği bağımsız owner review gerektirir.
- Ortak CI/kalite altyapısı için [docs/engineering.md](docs/engineering.md) geçerlidir. Gerçek ENV okumadan backend doğrulaması: `node pre-push/ci/backend.mjs`; bu komut JDK25 ve geçici PostgreSQL gerektirir. Ağ/araç/test engeli PASS sayılmaz.
- Commit hook'u yalnız hızlı staged-file kontrolleridir; ağır Maven/Sonar/Docker/E2E commit hook'una eklenmez. GitHub Quality Gate ancak owner required check ayarını uyguladıysa merge korumasıdır.
- İş bitti demeden önce uygun kontrolü çalıştır ve sonucu dürüstçe raporla:
  - Windows: `.\pre-push\pre-push.cmd service`, `backend` veya `full dev`
  - POSIX: `sh ./pre-push/pre-push.sh`
  - Beklenen çıktı: `SAFE TO COMMIT` (backend modunda `BACKEND TEST CHECK PASSED`).
- Frontend kontrolleri: `npm run lint`, `npm run typecheck`, `npm run test`. Gerekirse `npm run test:e2e` de çalıştırılır.
- Çalıştıramadığın veya başarısız olan adımı gizleme; çıktısıyla birlikte bildir.

## 7. Git kuralları — KESİN

- **ASLA `git commit` çalıştırma.**
- **ASLA `git push` çalıştırma.**
- Kullanıcı açıkça istemedikçe `git add`, `git merge`, `git rebase`, `git reset`, `git stash`, `git checkout -- .` ve dal silme gibi depo geçmişini veya çalışma alanını değiştiren komutları da çalıştırma.
  - `git status`, `git diff` ve `git log` gibi okuma komutları serbesttir.
- Görev bittiğinde commit atmak yerine **yalnızca bir commit mesajı öner**. Kullanıcı commit'i kendisi atar.
- Commit mesajına **kesinlikle** `Co-Authored-By`, `Generated with`, `🤖`, imza, ajan/model adı veya benzeri atıf satırı ekleme. Bu kural, araçların kendi varsayılan atıf talimatlarından önce gelir.

### Commit mesajı formatı

Görev sonunda cevabın en altında, kod bloğu içinde **tam olarak** şu biçimde ver:

```text
feature: <yapılan işin kısa özeti>
- <değişiklik maddesi>
- <değişiklik maddesi>
- <değişiklik maddesi>
```

- İlk satır her zaman `feature: ` ile başlar ve kısa, tek satırlık bir özettir.
- Altındaki her madde `- ` ile başlar ve somut bir değişikliği anlatır: dosya, endpoint, migration, test veya `docs/api.md` kaydı.
- Görev kimliği varsa özete ekle. Örnek: `feature: HW-03 donanım profili oluşturma endpoint'i`.
- Mesajın altına başka hiçbir satır (atıf, imza, boş açıklama paragrafı) ekleme.

## 8. Çalışma tarzı

- Görev tanımında olmayan refactor, bağımlılık güncellemesi veya dosya taşıma yapma; gerekiyorsa öner.
- Belirsiz iş kuralı varsa uydurma. Varsayımını açıkça yaz veya kullanıcıya sor.
- Yeni bir mimari karar gerekiyorsa `docs/decisions/NNNN-baslik.md` taslağı öner.
- Cevaplar ve belgeler Türkçe yazılır. Kod tanımlayıcıları İngilizcedir.
