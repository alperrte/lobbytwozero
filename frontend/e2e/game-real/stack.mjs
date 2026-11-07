// Game web arayüzü için GERÇEK yığın: Auth + Game + Community + API Gateway (yerel JAR'lar), etiketli geçici PostgreSQL
// konteynerleri ve Mailpit. DEV/prod veritabanına, ENV dosyalarına ve çalışan LTZ konteynerlerine dokunmaz.
// Süreç ortamı yalnız izin listesiyle (isolatedEnvironment) aktarılır; gizli değerler çıktıdan temizlenir.
import path from "node:path";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { createHmac, generateKeyPairSync, randomBytes, randomUUID } from "node:crypto";
import { fileURLToPath } from "node:url";
import { setTimeout as delay } from "node:timers/promises";
import { CookieJar, captureCommand, isolatedEnvironment, requireFreshJar, scrub } from "../../../ops/hardware-auth-e2e.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
export const frontendPort = 5102;
export const frontendOrigin = `http://localhost:${frontendPort}`;

async function until(action, name, timeout = 90000) {
  const deadline = Date.now() + timeout;
  do { const value = await action(); if (value) return value; await delay(250); } while (Date.now() < deadline);
  throw new Error(`Timeout: ${name}`);
}
async function freePort() {
  const server = createServer();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const port = server.address().port;
  await new Promise((resolve) => server.close(resolve));
  return port;
}

/** RFC 6238 TOTP (SHA-1, 6 hane, 30 sn); secret Base32. */
export function totpCode(secret, nowMs = Date.now()) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = "";
  for (const char of secret.replaceAll("=", "").toUpperCase()) bits += alphabet.indexOf(char).toString(2).padStart(5, "0");
  const key = Buffer.from(bits.match(/.{8}/g).map((byte) => parseInt(byte, 2)));
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(nowMs / 30000)));
  const digest = createHmac("sha1", key).update(counter).digest();
  const offset = digest[19] & 0xf;
  return String(((digest.readUInt32BE(offset) & 0x7fffffff) % 1000000)).padStart(6, "0");
}

export async function startStack(log = console.log) {
  for (const service of ["auth-service", "game-service", "community-service", "api-gateway"]) requireFreshJar(root, service);
  const label = randomUUID(), containers = [], children = [], secrets = [];
  const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true }), binary, secrets);
  const container = async (image, ports, env = {}) => {
    const args = ["run", "-d", "--label", `ltz.game-real-e2e=${label}`];
    for (const port of ports) args.push("-p", `127.0.0.1::${port}`);
    for (const name of Object.keys(env)) args.push("-e", name);
    const id = await command("docker", [...args, image], env);
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error("Invalid disposable container");
    containers.push(id);
    const mapped = {};
    for (const port of ports) {
      const address = await command("docker", ["port", id, `${port}/tcp`]);
      if (!/^127\.0\.0\.1:\d+$/.test(address.split("\n")[0])) throw new Error("Fixture must be loopback only");
      mapped[port] = address.split("\n")[0].split(":")[1];
    }
    return { id, ports: mapped };
  };
  const sql = async (db, query) => {
    const actual = await command("docker", ["inspect", "--format", '{{ index .Config.Labels "ltz.game-real-e2e" }}', db.id]);
    if (actual !== label) throw new Error("Refusing SQL outside owned disposable fixture");
    return command("docker", ["exec", db.id, "psql", "-U", "fixture", "-d", "fixture", "-v", "ON_ERROR_STOP=1", "-tAc", query]);
  };
  const launch = async (service, port, extra) => {
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java") : "java";
    const child = spawn(java, ["-Xmx384m", "-jar", requireFreshJar(root, service), "--server.address=127.0.0.1", `--server.port=${port}`, "--ltz.game.rating.sync.enabled=false"], { cwd: root, env: isolatedEnvironment({ LOG_LEVEL: "WARN", ...extra }), windowsHide: true });
    children.push(child);
    let output = "", failed = false;
    child.once("error", () => { failed = true; });
    for (const stream of [child.stdout, child.stderr]) stream.on("data", (data) => { output = (output + scrub(data.toString(), secrets)).slice(-6000); });
    await until(async () => {
      if (failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${output}`);
      try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
    }, service);
    log(`[stack] ${service} hazır (127.0.0.1:${port})`);
  };
  const stop = async () => {
    const failures = [];
    for (const child of children.reverse()) {
      if (!child.pid || child.exitCode !== null || child.signalCode !== null) continue;
      try {
        const closed = new Promise((resolve) => child.once("exit", resolve));
        if (process.platform === "win32") await command("taskkill", ["/PID", String(child.pid), "/T", "/F"]); else child.kill();
        await Promise.race([closed, delay(5000)]);
      } catch (error) { failures.push(error); }
    }
    for (const id of containers.reverse()) {
      try {
        const actual = await command("docker", ["inspect", "--format", '{{ index .Config.Labels "ltz.game-real-e2e" }}', id]);
        if (actual !== label) throw new Error("Refusing foreign container cleanup");
        await command("docker", ["rm", "-f", "-v", id]);
      } catch (error) { failures.push(error); }
    }
    if (failures.length) throw new AggregateError(failures, "Game real stack cleanup failed");
    log("[stack] süreçler ve etiketli geçici konteynerler temizlendi");
  };

  try {
    const password = randomBytes(24).toString("base64url"), dbPassword = randomBytes(32).toString("base64url");
    const privateKey = generateKeyPairSync("rsa", { modulusLength: 3072 }).privateKey.export({ format: "der", type: "pkcs8" }).toString("base64");
    const hmac = randomBytes(32).toString("base64"), totp = randomBytes(32).toString("base64");
    secrets.push(password, dbPassword, privateKey, hmac, totp);
    const dbEnv = { POSTGRES_USER: "fixture", POSTGRES_DB: "fixture", POSTGRES_PASSWORD: dbPassword };
    const authDb = await container("postgres:17-alpine", [5432], dbEnv), gameDb = await container("postgres:17-alpine", [5432], dbEnv), communityDb = await container("postgres:17-alpine", [5432], dbEnv);
    const mail = await container("axllent/mailpit:v1.31.0", [1025, 8025]);
    for (const db of [authDb, gameDb, communityDb]) await until(async () => { try { return await sql(db, "SELECT 1") === "1"; } catch { return false; } }, "PostgreSQL");
    // Gateway portu sabitlenebilir: NEXT_PUBLIC_API_BASE_URL derleme anında gömüldüğünden sabit port, derlemenin yeniden kullanılmasını sağlar.
    const [authPort, gamePort, communityPort] = [await freePort(), await freePort(), await freePort()];
    const gatewayPort = Number(process.env.GAME_REAL_GATEWAY_PORT) || await freePort();
    const origin = (port) => `http://127.0.0.1:${port}`;
    // Tarayıcı çerezleri için Gateway ve arayüz aynı host (localhost) altında olmalıdır; portlar çerez kapsamını ayırmaz.
    const gatewayOrigin = `http://localhost:${gatewayPort}`;
    const props = (db) => ({ POSTGRES_HOST: "127.0.0.1", POSTGRES_PORT: db.ports[5432], POSTGRES_USER: "fixture", POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: "fixture", DB_GAME_NAME: "fixture", DB_COMMUNITY_NAME: "fixture" });
    await launch("auth-service", authPort, { ...props(authDb), SPRING_PROFILES_ACTIVE: "dev", AUTH_JWT_PRIVATE_KEY: privateKey, AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gatewayOrigin, FRONTEND_PUBLIC_URL: frontendOrigin, LTZ_AUTH_MAIL_DEVSMTP_HOST: "127.0.0.1", LTZ_AUTH_MAIL_DEVSMTP_PORT: mail.ports[1025] });
    await launch("game-service", gamePort, { ...props(gameDb), AUTH_SERVICE_URL: origin(authPort), GAME_CATALOG_SYNC_ENABLED: "false" });
    await launch("community-service", communityPort, { ...props(communityDb), AUTH_SERVICE_URL: origin(authPort), GAME_SERVICE_URL: origin(gamePort) });
    await launch("api-gateway", gatewayPort, { AUTH_SERVICE_URL: origin(authPort), GAME_SERVICE_URL: origin(gamePort), COMMUNITY_SERVICE_URL: origin(communityPort), CORS_ALLOWED_ORIGINS: frontendOrigin, GATEWAY_AUTH_RATE_LIMIT_PER_MINUTE: process.env.GAME_REAL_AUTH_RATE_LIMIT ?? "3000" });

    const base = origin(gatewayPort);
    const post = (route, body, status, extra = {}) => fetch(base + route, { method: "POST", headers: { "Content-Type": "application/json", Origin: frontendOrigin, ...extra.headers }, body: body === undefined ? undefined : JSON.stringify(body) }).then(async (response) => {
      if (response.status !== status) throw new Error(`${route}: expected ${status}, got ${response.status}`);
      extra.jar?.accept(response.headers);
      const text = await response.text();
      return text ? JSON.parse(text) : undefined;
    });
    const register = async (name) => {
      const suffix = randomUUID().replaceAll("-", "").slice(0, 10), username = `${name}_${suffix}`.slice(0, 20), email = `${username}@example.test`;
      secrets.push(email);
      await post("/api/v1/auth/register", { email, username, password }, 202);
      const code = await until(async () => {
        const list = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/search?query=${encodeURIComponent("to:" + email)}`)).json();
        for (const summary of list.messages || []) {
          const detail = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/message/${summary.ID}`)).json();
          const match = String(detail.Text).match(/\b(\d{6})\b/);
          if (match) { secrets.push(match[1]); return match[1]; }
        }
      }, "Mailpit doğrulama kodu", 25000);
      await post("/api/v1/auth/email-verification/confirm", { email, code, password }, 204);
      return { email, username, password };
    };
    /**
     * Auth politikası: ayrıcalıklı roller (ADMIN, MODERATOR, GAME_DEVELOPER) TOTP olmadan giriş yapamaz (403 TWO_FACTOR_ENROLLMENT_REQUIRED).
     * Bu yüzden hesap önce PLAYER olarak gerçek akıştan TOTP'ye kaydedilir; rol ancak ondan sonra verilir.
     */
    const enrollTotp = async (user) => {
      const jar = new CookieJar();
      const csrf = async (route) => { await fetch(base + "/api/v1/auth/csrf", { headers: { Origin: frontendOrigin } }).then((response) => jar.accept(response.headers)); return { jar, headers: { Cookie: jar.header(route), "X-XSRF-TOKEN": jar.get("__Host-XSRF-TOKEN") } }; };
      const login = await post("/api/v1/auth/login", { identifier: user.email, password: user.password }, 200, await csrf("/api/v1/auth/login"));
      if (login.status !== "AUTHENTICATED") throw new Error("TOTP kaydı için PLAYER girişi beklenir");
      const setup = await post("/api/v1/auth/2fa/totp/setup", undefined, 200, await csrf("/api/v1/auth/2fa/totp/setup"));
      secrets.push(setup.secret);
      await post("/api/v1/auth/2fa/totp/activate", { code: totpCode(setup.secret) }, 200, await csrf("/api/v1/auth/2fa/totp/activate"));
      return setup.secret;
    };
    const grantRoles = async (email, roles) => {
      for (const role of roles) {
        await sql(authDb, `INSERT INTO account_roles(account_id, role_id) SELECT a.id, r.id FROM accounts a, roles r WHERE a.email_normalized = lower('${email}') AND r.code = '${role}' ON CONFLICT DO NOTHING`);
      }
    };
    return { gatewayOrigin, frontendOrigin, register, enrollTotp, grantRoles, password, sql, authDb, gameDb, communityDb, mailPort: mail.ports[8025], stop };
  } catch (error) {
    await stop().catch(() => {});
    throw error;
  }
}
