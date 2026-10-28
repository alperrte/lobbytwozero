import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, readModules } from '../../lib/quality-tools.mjs';

// Deliberately independent of lint-ratchet.mjs: that module imports this one lazily from its CLI.
const baselineFile = 'backend/config/quality-baseline.json';
function readBaseline(root) {
  const data = JSON.parse(fs.readFileSync(path.join(root, baselineFile), 'utf8'));
  if (data?.version !== 1 || typeof data.modules !== 'object' || data.modules === null) throw new Error('Invalid quality baseline');
  return data;
}

// docs-security/<servis>/README.md is generated from backend/config/quality-baseline.json so it can never drift
// from the real frozen debt; docs-security/<servis>/NOTLAR.md is hand-written and never overwritten.
export const docsDirectory = 'docs-security';
const command = 'node pre-push/ci/lint-ratchet.mjs docs';

const OWNERS = {
  'hardware-service': ['Alper', '@alperrte'], 'recommendation-service': ['Alper', '@alperrte'], 'performance-service': ['Alper', '@alperrte'],
  'game-service': ['Hamza', '@HmzT270'], 'library-service': ['Hamza', '@HmzT270'], 'review-service': ['Hamza', '@HmzT270'],
  'auth-service': ['Kerem', '@KeremKalyoncu'], 'user-service': ['Kerem', '@KeremKalyoncu'], 'mail-service': ['Kerem', '@KeremKalyoncu'],
  'community-service': ['Nisa', '@Camcinisa'], 'content-service': ['Nisa', '@Camcinisa'], 'event-service': ['Nisa', '@Camcinisa'],
  'social-service': ['Umut', '@Umutys61'], 'messaging-service': ['Umut', '@Umutys61'], 'lfp-service': ['Umut', '@Umutys61'],
};
const owner = service => OWNERS[service] ?? ['Ortak (sahip atanmadı)', ''];

const RULES = {
  'checkstyle:AvoidStarImport': ['Yıldızlı import (`import x.*;`)', 'Her sınıfı tek tek import et. IDE\'de "Optimize Imports"; IntelliJ\'de "Class count to use import with \'*\'" değerini 999 yap.'],
  'checkstyle:UnusedImports': ['Kullanılmayan import', 'Sil (Optimize Imports).'],
  'checkstyle:RedundantImport': ['Gereksiz import', 'Sil.'],
  'checkstyle:MethodName': ['Metot adında alt çizgi', 'camelCase yaz (`kayitBasarili201Doner`); IDE\'nin Rename özelliğini kullan.'],
  'checkstyle:MemberName': ['Alan adı kuralı', 'camelCase yap.'],
  'checkstyle:ParameterName': ['Parametre adı kuralı', 'camelCase yap.'],
  'checkstyle:TypeName': ['Sınıf adı kuralı', 'PascalCase yap.'],
  'checkstyle:OneTopLevelClass': ['Dosyada birden çok üst sınıf', 'Her üst sınıf kendi dosyasında olsun.'],
  'checkstyle:OuterTypeFilename': ['Dosya adı sınıf adıyla aynı değil', 'Dosyayı veya sınıfı yeniden adlandır.'],
  'pmd:PreserveStackTrace': ['Yeni hata fırlatılırken eski hatanın izi kayboluyor', 'Eski hatayı sebep olarak ekle. **Güvenlik sınırında** (token, parola, dış servis cevabı) sebep gizli bilgi taşıyabilir: körlemesine ekleme, güvenlik gerekçeli dar çözüm bul.'],
  'pmd:CloseResource': ['Kaynak (ExecutorService, HttpClient, stream) kapatılmıyor', 'Testte try-with-resources; üretim kodunda uygulama boyu yaşayan nesneyse gerekçeli çözüm (sahibine sor).'],
  'pmd:CyclomaticComplexity': ['Metot/sınıf çok dallı ve karmaşık', 'Davranışı değiştirmeden küçük metotlara böl; önce mevcut testlerin geçtiğinden emin ol.'],
  'pmd:EmptyCatchBlock': ['Boş catch bloğu', 'Hatayı işle veya gerekçeyle belgele.'],
  'pmd:AvoidPrintStackTrace': ['printStackTrace kullanımı', 'Logger kullan.'],
  'pmd:AvoidCatchingNPE': ['NullPointerException yakalanıyor', 'Null kontrolü yap, NPE yakalama.'],
  'pmd:AvoidThrowingNullPointerException': ['Elle NullPointerException fırlatılıyor', 'Uygun hata türünü (IllegalArgumentException vb.) kullan.'],
  'pmd:BrokenNullCheck': ['Bozuk null kontrolü', 'Koşulu düzelt.'],
};

const KNOWN_NOTES = {
  'game-service': ['Windows\'ta `core.autocrlf=true` ise SQL migration dosyaları CRLF kalır ve 8 migration testi kırmızı çıkar (kod hatası değil, Linux CI\'da yok). `.gitattributes` (`*.sql text eol=lf`) bunu çözer; eski checkout\'ta migration klasörünü silip `git checkout -- <klasör>` ile LF\'e çevir.'],
  'auth-service': ['Testler paylaşılan (CI) test veritabanında ikinci koşuda veri kalıntısı yüzünden kırılabilir; GitHub CI\'da her koşu sıfır veritabanı açar. Yerelde `node pre-push/ci/backend.mjs` her seferinde yeni geçici veritabanı kullanır.'],
  'library-service': ['`contextLoads` testi `AUTH_SERVICE_URL` ister; CI test profili (`-Pci`) bunu sağlar. Profil dışı koşuda değişkeni ver.'],
  'review-service': ['`contextLoads` testi `AUTH_SERVICE_URL` ister; CI test profili (`-Pci`) bunu sağlar. Profil dışı koşuda değişkeni ver.'],
};

function byRule(files) {
  const totals = {};
  for (const rules of Object.values(files)) for (const [rule, count] of Object.entries(rules)) totals[rule] = (totals[rule] ?? 0) + count;
  return Object.fromEntries(Object.entries(totals).sort(([a], [b]) => (a < b ? -1 : 1)));
}

const sum = totals => Object.values(totals).reduce((a, b) => a + b, 0);

export function renderService(service, files, generated) {
  const [name, login] = owner(service);
  const totals = byRule(files);
  const debt = sum(totals);
  const lines = [
    `# ${service}: güvenlik ve kalite durumu`, '',
    `> **Bu dosya otomatik üretilir, elle düzenleme.** Kaynak: \`backend/config/quality-baseline.json\` (${generated || '?'}). Yenilemek için: \`${command}\`.`,
    '> Elle tutulan notlar için: [NOTLAR.md](NOTLAR.md).', '',
    `**Sahip:** ${name}${login ? ` (${login})` : ''}  •  **Eski borç:** ${debt ? `⚠️ ${debt} ihlal, ${Object.keys(files).length} dosya` : '✅ yok (temiz)'}`, '',
    '## Önce bunu oku (geliştirici ve yapay zekâ ajanı için)', '',
    `1. Bu servise dokunmadan önce bu dosyayı ve [NOTLAR.md](NOTLAR.md)'yi oku.`,
    '2. Checkstyle/PMD kapısı **yeni ihlal** arar: aşağıdaki eski borç donmuştur ve PR\'ı bloklamaz, ama **yeni ihlal eklersen CI kırmızı** olur.',
    '3. Bulunan bir ihlali kural kapatarak, `@SuppressWarnings`/`NOPMD`/`CHECKSTYLE:OFF` ile veya dosyayı hariç tutarak **gizleme**; gerçekten düzelt.',
    '4. Bir dosyanın borcunu azaltırsan kazancı kilitle (aşağıda "Borcu kapatınca"). Baseline dosyasına borç **ekleyemezsin**.',
    '5. Davranışı değiştirme; önce servisin testlerini çalıştır. `git commit`/`push` kararını sahibine bırak (`AGENTS.md`).', '',
  ];
  if (debt) {
    lines.push('## Eski borç: kural bazında', '', '| Kural | Adet | Ne demek | Nasıl düzeltilir |', '|---|---:|---|---|');
    for (const [rule, count] of Object.entries(totals)) {
      const [meaning, fix] = RULES[rule] ?? ['', ''];
      lines.push(`| \`${rule}\` | ${count} | ${meaning} | ${fix} |`);
    }
    lines.push('', '## Eski borç: dosya dosya', '');
    for (const [file, rules] of Object.entries(files).sort(([a], [b]) => (a < b ? -1 : 1))) {
      lines.push(`- \`${file}\`: ${Object.entries(rules).map(([rule, count]) => `${rule} ×${count}`).join(', ')}`);
    }
    lines.push('');
  } else {
    lines.push('## Eski borç', '', 'Bu serviste dondurulmuş borç yok. Kapı **sıfır yeni ihlal** bekler: yeni kod Checkstyle ve PMD\'den temiz geçmeli.', '');
  }
  lines.push('## Doğrulama', '', '```powershell',
    '$env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-25.0.4.1"   # JDK 25 şart',
    `mvn -B -f backend/pom.xml -pl services/${service} -DskipTests verify        # raporları üretir`,
    `node pre-push/ci/lint-ratchet.mjs check --modules services/${service}      # yeni ihlal var mı?`,
    `mvn -B -f backend/pom.xml -pl services/${service} -Pstrict-quality -DskipTests verify   # sıfır tolerans (borç temizlerken)`,
    '```', '',
    '## Borcu kapatınca', '',
    '```powershell',
    `node pre-push/ci/lint-ratchet.mjs update --modules services/${service}   # kazancı baseline\'a kilitler ve bu klasörü yeniler`,
    '```', '',
    'Ardından `backend/config/quality-baseline.json` ve `docs-security/` değişikliklerini commit\'e ekle.', '');
  return lines.join('\n');
}

export function renderNotes(service) {
  const [name] = owner(service);
  const notes = KNOWN_NOTES[service] ?? [];
  return ['# ' + service + ': notlar (elle tutulur)', '',
    `> Bu dosya **elle** yazılır ve otomatik üretilen \`README.md\` tarafından ezilmez. Sahip: ${name}.`,
    '> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.', '',
    '## Bilinen durumlar', '',
    ...(notes.length ? notes.map(note => `- ${note}`) : ['- Henüz kayıtlı bir not yok.']), ''].join('\n');
}

export function renderIndex(rows, generated) {
  const total = rows.reduce((sum, row) => sum + row.debt, 0);
  const lines = ['# docs-security: servis başına güvenlik ve kalite durumu', '',
    `> **Bu dosya otomatik üretilir** (kaynak: \`backend/config/quality-baseline.json\`, ${generated || '?'}). Yenilemek için: \`${command}\`.`, '',
    'Her servisin klasöründe iki dosya var:', '',
    '- `README.md`: otomatik üretilir. O servisteki eski borç (Checkstyle/PMD), dosya dosya liste, nasıl düzeltileceği ve doğrulama komutları.',
    '- `NOTLAR.md`: **elle** tutulur. Bilinen test/ortam sorunları ve servise özgü güvenlik notları.', '',
    '**Bir servise dokunmadan önce (insan veya yapay zekâ ajanı) kendi servisinin klasörünü oku.**', '',
    '| Servis | Sahip | Eski borç | Klasör |', '|---|---|---:|---|'];
  for (const row of rows) lines.push(`| ${row.service} | ${row.owner} | ${row.debt ? row.debt : '✅ 0'} | [${row.service}](${row.service}/README.md) |`);
  lines.push('', `**Toplam donmuş borç: ${total}.** Borç yalnızca azalabilir; yeni ihlal PR'ı kırmızı yapar. Ayrıntı: [docs/engineering.md](../docs/engineering.md).`, '');
  return lines.join('\n');
}

export function generateDocs({ root = repositoryRoot, baseline = readBaseline(root), modules = readModules(root) } = {}) {
  const files = new Map();
  const rows = [];
  for (const module of modules) {
    const service = module.replace(/^services\//, '');
    const debtFiles = baseline.modules[module] ?? {};
    files.set(`${docsDirectory}/${service}/README.md`, renderService(service, debtFiles, baseline.generated));
    rows.push({ service, owner: owner(service)[0], debt: sum(byRule(debtFiles)) });
  }
  files.set(`${docsDirectory}/README.md`, renderIndex(rows, baseline.generated));
  return files;
}

export function writeDocs({ root = repositoryRoot, baseline = readBaseline(root), modules = readModules(root) } = {}) {
  const generated = generateDocs({ root, baseline, modules });
  for (const [relative, content] of generated) {
    const file = path.join(root, relative);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, content);
  }
  // Hand-written notes are created once and never overwritten.
  for (const module of modules) {
    const service = module.replace(/^services\//, '');
    const notes = path.join(root, docsDirectory, service, 'NOTLAR.md');
    if (!fs.existsSync(notes)) fs.writeFileSync(notes, renderNotes(service));
  }
  return [...generated.keys()];
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(writeDocs().length + ' files written to ' + docsDirectory); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
