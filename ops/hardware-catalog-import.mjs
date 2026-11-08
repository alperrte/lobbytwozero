import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = path.join(root, 'backend/services/hardware-service/src/main/resources/catalog/pc-part-dataset.json');
const container = 'ltz-dev-hardware-service-1';
const remote = '/tmp/ltz-pc-part-import';
export const MODES = ['download', 'dry-run', 'import'];
export const USAGE = 'Kullanım: node ops/hardware-catalog-import.mjs download|dry-run|import <dataset-klasörü> [--standalone]';
export const STANDALONE = '--standalone';

/** Java komutunun çıktısından yalnız özet satırlarını süzer (ayrıntı/ENV sızdırmaz). */
export function importSummary(output) {
  return output.split(/\r?\n/)
    .filter(line => /^(DATASET_FILES |PC_PART_IMPORT_RESULT |PC_PART_IMPORT_FAILED:)/.test(line)).join('\n');
}

function docker(args) {
  const result = spawnSync('docker', args, { encoding: 'utf8', maxBuffer: 2_000_000 });
  if (result.status !== 0) {
    const summary = importSummary(result.stdout + '\n' + result.stderr);
    throw new Error('Docker işlemi başarısız: ' + args[0] + (summary ? '\n' + summary : ''));
  }
  return result.stdout.trim();
}

/**
 * `[mod = dry-run] <klasör> [--standalone]` argümanlarını doğrular; geçersizse kullanım hatası fırlatır.
 * `--standalone`: çalışan Hardware konteyneri GEREKMEZ; tek seferlik konteyner açılır (dev.mjs servis başlamadan önce bunu kullanır).
 */
export function parseArguments(argv) {
  const [mode = 'dry-run', input, ...extra] = argv;
  const standalone = extra.length === 1 && extra[0] === STANDALONE;
  if (!MODES.includes(mode) || !input || (extra.length && !standalone)) throw new Error(USAGE);
  return { mode, directory: path.resolve(input), standalone };
}

export function sha256Of(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

/** Dataset klasöründeki her dosyanın manifestteki SHA-256 ile eşleştiğini doğrular. */
export async function verifyDatasetFiles(directory, manifest) {
  for (const file of manifest.files) {
    const bytes = await fs.readFile(path.join(directory, file.file));
    if (sha256Of(bytes) !== file.sha256) throw new Error('Dosya hash uyuşmazlığı: ' + file.file);
  }
}

/** Yalnız ltz-dev yığınındaki Hardware konteyneri kullanılabilir (Compose etiketlerine bakılır; ENV okunmaz). */
export function assertHardwareContainer(labels) {
  if (labels['com.docker.compose.project'] !== 'ltz-dev' || labels['com.docker.compose.service'] !== 'hardware-service') {
    throw new Error('Yalnız ltz-dev Hardware konteyneri kullanılabilir');
  }
}

/** Konteyner içinde çalışacak Java içe aktarma komutu; yalnız `import` kipinde `--execute` eklenir. */
export function buildImportCommand(mode) {
  const command = ['exec', container, 'java', '-Dloader.main=com.ltz.hardware.infrastructure.config.PcPartImportCommand',
    '-cp', remote + '/import.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher', remote];
  if (mode === 'import') command.push('--execute');
  return command;
}

/**
 * Hardware servisi AYAKTA OLMADAN içe aktarma: aynı imajdan tek seferlik konteyner (`compose run --rm --no-deps`).
 * ENV değerleri Compose servis tanımından gelir (ENV dosyası okunmaz), veri seti salt okunur bağlanır, Flyway göçlerini içe aktarma komutu uygular.
 */
export function buildStandaloneCommand(mode, directory) {
  const command = ['compose', '-p', 'ltz-dev', '--env-file', '.env.dev', '--profile', 'dev-mail', 'run', '--rm', '--no-deps', '-T',
    '--entrypoint', 'java', '--volume', `${directory}:/dataset:ro`, 'hardware-service',
    '-Dloader.main=com.ltz.hardware.infrastructure.config.PcPartImportCommand',
    '-cp', '/app/app.jar', 'org.springframework.boot.loader.launch.PropertiesLauncher', '/dataset'];
  if (mode === 'import') command.push('--execute');
  return command;
}

export async function download(directory, manifest) {
  await fs.mkdir(directory, { recursive: true });
  const downloaded = { revision: manifest.revision, downloadedAt: new Date().toISOString(), files: {} };
  for (const file of manifest.files) {
    const url = `https://raw.githubusercontent.com/docyx/pc-part-dataset/${manifest.revision}/data/json/${file.file}`;
    const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(60000) });
    if (!response.ok) throw new Error('Kaynak indirilemedi: ' + file.file);
    const bytes = Buffer.from(await response.arrayBuffer());
    const sha256 = sha256Of(bytes);
    if (bytes.length > 16_000_000 || sha256 !== file.sha256) throw new Error('Bütünlük doğrulanamadı: ' + file.file);
    await fs.writeFile(path.join(directory, file.file), bytes);
    downloaded.files[file.file] = { sha256, bytes: bytes.length, downloadedAt: new Date().toISOString() };
  }
  await fs.writeFile(path.join(directory, 'download.json'), JSON.stringify(downloaded, null, 2) + '\n');
  console.log(JSON.stringify(downloaded));
}

export async function run(argv = process.argv.slice(2)) {
  const { mode, directory, standalone } = parseArguments(argv);
  const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  if (mode === 'download') return download(directory, manifest);
  await verifyDatasetFiles(directory, manifest);
  if (standalone) {
    const result = spawnSync('docker', buildStandaloneCommand(mode, directory), { cwd: root, encoding: 'utf8', maxBuffer: 2_000_000 });
    const lines = importSummary([result.stdout, result.stderr].join(String.fromCharCode(10)));
    if (result.status !== 0) throw new Error(['Tek seferlik içe aktarma başarısız oldu', lines].filter(Boolean).join(String.fromCharCode(10)));
    console.log(lines);
    return;
  }
  // Read only Compose labels, never inspect Config.Env or load a real ENV file.
  assertHardwareContainer(JSON.parse(docker(['inspect', '--format', '{{json .Config.Labels}}', container])));
  const jar = path.join(root, 'backend/services/hardware-service/target/hardware-service-0.0.1-SNAPSHOT.jar');
  await fs.access(jar);
  docker(['exec', container, 'mkdir', '-p', remote]);
  docker(['cp', jar, `${container}:${remote}/import.jar`]);
  for (const file of manifest.files) docker(['cp', path.join(directory, file.file), `${container}:${remote}/${file.file}`]);
  console.log(docker(buildImportCommand(mode)));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await run(); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
