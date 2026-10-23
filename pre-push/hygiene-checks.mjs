import fs from 'node:fs';
import path from 'node:path';

// Depo hijyeni: izlenen dosyalarin ICERIGINDE sir, ENV ignore kurallari, Compose port baglamalari ve anahtar
// uretici script testleri. Hicbir sir degeri ekrana yazilmaz; yalniz dosya, satir ve anahtar adi bildirilir.

const SECRET_NAMES = [
  'AUTH_JWT_PRIVATE_KEY', 'AUTH_OTP_HMAC_KEY', 'AUTH_TOTP_ENCRYPTION_KEY', 'POSTGRES_PASSWORD',
  'SONAR_DB_PASSWORD', 'SONAR_TOKEN', 'GOOGLE_CLIENT_SECRET',
];
// Baslik dizesi kaynakta bitisik yazilmaz; bu dosya kendi kendini yakalamasin.
const PRIVATE_KEY_PATTERN = '-----BEGIN [A-Z ]*' + 'PRIVATE KEY';
const ASSIGNMENT_PATTERN = `(${SECRET_NAMES.join('|')})=[^[:space:]]{12,}`;
const PLACEHOLDER = /^(CHANGE|REPLACE|YOUR|<|\$\{|x{3,}|\.\.\.)/i;
// Testler ve bu kontrol kodunun kendisi bilerek sahte degerler icerir.
const EXCLUDED = [':!pre-push/*.test.mjs', ':!ops/*.test.mjs', ':!sonarqube/*.test.mjs'];
const IGNORED_PATHS = ['.env', '.env.dev', '.env.prod', 'frontend/.env.local'];

// `git grep -n` ciktisi (dosya:satir:icerik) icinden yer tutucu olmayan uzun sir atamalarini bulur.
export function findSecretAssignments(grepOutput) {
  const findings = [];
  const matcher = new RegExp(`\\b(${SECRET_NAMES.join('|')})=(\\S{12,})`);
  for (const entry of String(grepOutput).split(/\r?\n/)) {
    const parts = entry.match(/^([^:]+):(\d+):(.*)$/);
    if (!parts) continue;
    const hit = parts[3].match(matcher);
    if (!hit || PLACEHOLDER.test(hit[2])) continue;
    findings.push({ file: parts[1], line: Number(parts[2]), name: hit[1] });
  }
  return findings;
}

// Compose `ports:` listelerinde 127.0.0.1 disina baglanan girdileri bulur (servisler yalniz yerelden erisilir).
export function nonLoopbackPorts(composeText) {
  const lines = String(composeText).split(/\r?\n/);
  const exposed = [];
  let portsIndent = -1;
  for (const line of lines) {
    const indent = line.length - line.trimStart().length;
    if (/^\s*ports:\s*$/.test(line)) { portsIndent = indent; continue; }
    if (portsIndent >= 0) {
      if (line.trim() === '' || line.trim().startsWith('#')) continue;
      if (indent <= portsIndent && !line.trimStart().startsWith('- ')) { portsIndent = -1; continue; }
      const entry = line.trim().replace(/^-\s*/, '').replace(/^["']|["']$/g, '');
      if (!entry.includes(':')) continue; // yalniz konteyner portu
      if (!entry.startsWith('127.0.0.1:')) exposed.push(entry);
    }
  }
  return exposed;
}

export async function checkRepositoryHygiene({ run, log, root }) {
  const failures = [];

  // 1) Ozel anahtar basliklari (yalniz dosya adi).
  const keys = await run('git', ['grep', '-I', '-l', '-E', '-e', PRIVATE_KEY_PATTERN, '--', '.', ...EXCLUDED], root, true);
  if (keys.code > 1) throw new Error('git grep failed while scanning for private keys.');
  for (const file of keys.output.split(/\r?\n/).filter(Boolean)) {
    log('[PRIVATE KEY] ' + JSON.stringify(file));
    failures.push('private key material in a tracked file');
  }

  // 2) Gercek gorunen sir atamalari (deger yazdirilmaz).
  const assignments = await run('git', ['grep', '-I', '-n', '-E', '-e', ASSIGNMENT_PATTERN, '--', '.', ...EXCLUDED], root, true);
  if (assignments.code > 1) throw new Error('git grep failed while scanning for secret assignments.');
  for (const finding of findSecretAssignments(assignments.output)) {
    log(`[SECRET VALUE] ${JSON.stringify(finding.file)}:${finding.line} ${finding.name}`);
    failures.push('secret-looking value in a tracked file');
  }

  // 3) ENV dosyalari Git tarafindan yok sayilmali (.gitignore'dan silinirse yakalanir).
  for (const target of IGNORED_PATHS) {
    if ((await run('git', ['check-ignore', '-q', target], root, true)).code !== 0) {
      log('[NOT IGNORED] ' + target);
      failures.push(target + ' is not ignored by Git');
    }
  }

  // 4) Compose ve .dockerignore (yalniz Compose dosyasi bulunan gercek depoda zorunlu).
  const compose = ['docker-compose.yml', 'docker-compose.sonar.yml'].map(name => path.join(root, name)).filter(fs.existsSync);
  if (compose.length) {
    const dockerignore = path.join(root, '.dockerignore');
    if (!fs.existsSync(dockerignore) || !/^\.env/m.test(fs.readFileSync(dockerignore, 'utf8'))) {
      log('[DOCKERIGNORE] .env files must be excluded from the Docker build context');
      failures.push('.dockerignore does not exclude .env files');
    }
    for (const file of compose) {
      for (const entry of nonLoopbackPorts(fs.readFileSync(file, 'utf8'))) {
        log(`[PORT EXPOSED] ${path.basename(file)}: ${entry.replace(/\$\{[^}]*\}/g, '${...}')}`);
        failures.push('Compose port is not bound to 127.0.0.1');
      }
    }
  } else {
    log('[SKIPPED] Compose/.dockerignore checks: no Compose file in this repository root.');
  }

  // 5) Anahtar uretici scriptin guvenlik bekcisi testi.
  if (fs.existsSync(path.join(root, 'ops/generate-auth-dev-keys.test.mjs'))) {
    const tooling = await run(process.execPath, ['--test', 'ops/generate-auth-dev-keys.test.mjs'], root, true);
    if (tooling.code !== 0) {
      log('[TOOLING TEST] ops/generate-auth-dev-keys.test.mjs failed; the key generator guard was changed or broken.');
      failures.push('key generator guard test failed');
    }
  }

  // 6) gitleaks varsa tum Git gecmisini tara; yoksa atla (ek kurulum zorunlu degil).
  let scanner = false;
  try { scanner = (await run('gitleaks', ['version'], root, true)).code === 0; } catch { scanner = false; }
  if (scanner) {
    const scan = await run('gitleaks', ['detect', '--no-banner', '--redact', '--log-opts=--all'], root, true);
    if (scan.code !== 0) {
      log('[GITLEAKS] leaks found; run `gitleaks detect --redact -v` locally to see them.');
      failures.push('gitleaks reported leaks');
    }
  } else {
    log('[SKIPPED] gitleaks is not installed; history secret scan not performed.');
  }

  if (failures.length) throw new Error('Repository hygiene checks failed: ' + [...new Set(failures)].join('; ') + '.');
  log('Repository hygiene checks passed (private keys, secret values, ENV ignore rules, loopback ports, key generator guard).');
  return true;
}
