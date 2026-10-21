import path from 'node:path';
import { randomBytes } from 'node:crypto';

const IMAGE = 'postgres:17-alpine';
const NAME_PREFIX = 'ltz-prepush-pg-';
const USER = 'ltz_test';
const LAST_DATABASE = 'notification_db'; // postgres/init/01-create-databases.sql içindeki son veritabanı
const NOTHING = { env: {}, secrets: [], stop: async () => {} };

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Backend testleri için geçici bir PostgreSQL 17 container'ı başlatır ve Maven'e verilecek bağlantı
 * ortamını döndürür. Servislerin `application.properties` varsayılanları (POSTGRES_HOST/PORT/USER/PASSWORD,
 * DB_*_NAME varsayılanları) bu ortamla geçici veritabanına bağlanır; `.env` dosyaları okunmaz, dev
 * veritabanına dokunulmaz. Şifre her çalıştırmada rastgele üretilir, komut satırında ve logda görünmez.
 *
 * Ortamda POSTGRES_PASSWORD zaten tanımlıysa (geliştirici kendi veritabanını seçmiştir) veya
 * LTZ_TEST_DB=off ise hiçbir şey başlatılmaz. Docker yoksa/başlamazsa uyarı verilir ve testler
 * eskisi gibi çalışır; hata saklanmaz.
 */
export async function startTestDatabase({ root, env = {}, runCommand, log = console.log, attempts = 60, delayMs = 1000, wait = sleep }) {
  if (env.LTZ_TEST_DB === 'off') {
    log('[TEST DB] LTZ_TEST_DB=off: geçici test veritabanı başlatılmadı.');
    return NOTHING;
  }
  if (env.POSTGRES_PASSWORD) {
    log('[TEST DB] POSTGRES_PASSWORD ortamda tanımlı: geçici test veritabanı başlatılmadı, mevcut ayarlar kullanılacak.');
    return NOTHING;
  }
  const docker = (args, extraEnv = {}, secrets = []) =>
    runCommand('docker', args, { cwd: root, env: { ...env, ...extraEnv }, capture: true, secrets, log });
  const name = `${NAME_PREFIX}${process.pid}-${randomBytes(3).toString('hex')}`;
  const password = randomBytes(16).toString('hex');
  const fail = async reason => {
    await docker(['rm', '-f', name]);
    log(`[WARN] Geçici test veritabanı başlatılamadı (${reason}). Veritabanı gerektiren testler başarısız olabilir.`);
    return NOTHING;
  };
  try {
    // Önceki, yarıda kesilmiş çalışmalardan kalan geçici container'ları temizle (çalışmalar kilitle seri yürür).
    const stale = await docker(['ps', '-aq', '--filter', `name=${NAME_PREFIX}`]);
    for (const id of stale.output.split(/\s+/).filter(Boolean)) await docker(['rm', '-f', id]);

    const initDir = path.join(root, 'postgres', 'init');
    const started = await docker(
      ['run', '-d', '--rm', '--name', name, '-e', 'POSTGRES_USER=' + USER, '-e', 'POSTGRES_PASSWORD',
        '-p', '127.0.0.1::5432', '-v', `${initDir}:/docker-entrypoint-initdb.d:ro`, IMAGE],
      { POSTGRES_PASSWORD: password }, [password]);
    if (started.code !== 0) return await fail('docker run başarısız; Docker açık mı?');

    const mapping = await docker(['port', name, '5432/tcp']);
    const port = /:(\d+)\s*$/m.exec(mapping.output)?.[1];
    if (mapping.code !== 0 || !port) return await fail('yayınlanan port okunamadı');

    for (let attempt = 0; attempt < attempts; attempt++) {
      // Son init veritabanı varsa başlangıç betikleri bitmiş ve sunucu TCP'de hazır demektir.
      const ready = await docker(['exec', name, 'psql', '-h', '127.0.0.1', '-U', USER, '-d', LAST_DATABASE, '-tAc', 'select 1']);
      if (ready.code === 0 && ready.output.trim() === '1') {
        log(`[TEST DB] Geçici PostgreSQL hazır (localhost:${port}); testler bu veritabanını kullanacak, dev veritabanına dokunulmaz.`);
        return {
          env: { POSTGRES_HOST: 'localhost', POSTGRES_PORT: port, POSTGRES_USER: USER, POSTGRES_PASSWORD: password },
          secrets: [password],
          stop: async () => {
            await docker(['rm', '-f', name]);
            log('[TEST DB] Geçici PostgreSQL kaldırıldı.');
          }
        };
      }
      await wait(delayMs);
    }
    return await fail('zaman aşımı');
  } catch (error) {
    return await fail(error.message);
  }
}
