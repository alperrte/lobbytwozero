import assert from "node:assert/strict";
import test from "node:test";
import { CORE_SERVICES, decideMailStep, estimateMemoryMb, memoryVerdict, needsInstall, parseArgs, summaryLines, validateArgs } from "./dev-lib.mjs";

test("argüman ayrıştırma: komut verilmezse start, bayraklar ayrılır, mail argümanları iletilir", () => {
  assert.deepEqual(parseArgs([]), { command: "start", rest: [], flags: new Set(), withServices: [] });
  const parsed = parseArgs(["--no-mail", "start", "--no-frontend"]);
  assert.equal(parsed.command, "start");
  assert.deepEqual([...parsed.flags].sort(), ["--no-frontend", "--no-mail"]);
  assert.deepEqual(parseArgs(["mail", "on"]).rest, ["on"]);
});

test("argüman doğrulama: bilinmeyen komut ve bayrak reddedilir", () => {
  assert.doesNotThrow(() => validateArgs(parseArgs(["--dry-run", "--no-build", "--skip-keys", "--no-services"])));
  assert.throws(() => validateArgs(parseArgs(["calistir"])), /Bilinmeyen komut/);
  assert.throws(() => validateArgs(parseArgs(["--hizli"])), /Bilinmeyen seçenek/);
});

test("e-posta kararı: öncelik sırası", () => {
  const base = { skip: false, real: false, passwordSet: false, interactive: true, envPassword: undefined };
  assert.equal(decideMailStep({ ...base, skip: true, real: true, passwordSet: true }), "skip");
  assert.equal(decideMailStep({ ...base, real: true, passwordSet: true }), "already");
  assert.equal(decideMailStep({ ...base, envPassword: "x" }), "env");
  assert.equal(decideMailStep(base), "ask");
  assert.equal(decideMailStep({ ...base, interactive: false }), "mailpit");
});

test("e-posta kararı: şifresiz yarım kurulum 'already' sayılmaz (yeniden sorulur); etkileşimsizde değişken yoksa Mailpit", () => {
  const base = { skip: false, real: true, passwordSet: false, interactive: true, envPassword: undefined };
  assert.equal(decideMailStep(base), "ask");
  assert.equal(decideMailStep({ ...base, interactive: false }), "mailpit");
  assert.equal(decideMailStep({ ...base, interactive: false, envPassword: "x" }), "env");
});

test("npm ci kararı", () => {
  assert.equal(needsInstall({ modulesTime: null, lockTime: 100 }), true);
  assert.equal(needsInstall({ modulesTime: 200, lockTime: 100 }), false);
  assert.equal(needsInstall({ modulesTime: 100, lockTime: 200 }), true);
  assert.equal(needsInstall({ modulesTime: 100, lockTime: null }), false);
});

test("özet: beş hesap, parola değeri yok, e-posta kipi doğru", () => {
  const mailpit = summaryLines({ domain: "ltz.com", realMail: false, frontendRunning: true }).join("\n");
  for (const name of ["user", "creator", "developer", "moderator", "admin"]) assert.match(mailpit, new RegExp(`${name}@ltz\\.com`));
  assert.match(mailpit, /Mailpit/);
  assert.doesNotMatch(mailpit, /ltz1234/);
  assert.doesNotMatch(mailpit, /frontend: cd frontend/);
  const real = summaryLines({ domain: "ltz.test", realMail: true, frontendRunning: false }).join("\n");
  assert.match(real, /GERÇEK/);
  assert.match(real, /user@ltz\.test/);
  assert.match(real, /cd frontend && npx next dev/);
});

test("--with= servis listesi: virgülle ayrılır, bayrak sayılmaz, adı doğrulanır", () => {
  const parsed = parseArgs(["--with=game-service,library-service", "--no-mail", "--with=review-service"]);
  assert.deepEqual(parsed.withServices, ["game-service", "library-service", "review-service"]);
  assert.deepEqual([...parsed.flags], ["--no-mail"]);
  assert.doesNotThrow(() => validateArgs(parsed));
  assert.throws(() => validateArgs(parseArgs(["--with=Game Service"])), /Geçersiz servis adı/);
  assert.throws(() => validateArgs(parseArgs(["--with=a;rm -rf"])), /Geçersiz servis adı/);
  assert.doesNotThrow(() => validateArgs(parseArgs(["--all", "--force"])));
});

test("bellek tahmini ve karar: çekirdek 6 servis yeter, 20 servis 7,4 GB'a sığmaz", () => {
  const gb = 1024 ** 3;
  assert.equal(estimateMemoryMb(CORE_SERVICES), 150 + 30 + 4 * 550);
  assert.equal(estimateMemoryMb([...CORE_SERVICES, "auth-service"]), estimateMemoryMb(CORE_SERVICES));
  assert.equal(memoryVerdict({ services: CORE_SERVICES, dockerMemBytes: 7.4 * gb }).ok, true);
  const all = [...CORE_SERVICES, ...Array.from({ length: 14 }, (_, i) => `servis-${i}-service`)];
  const verdict = memoryVerdict({ services: all, dockerMemBytes: 7.4 * gb });
  assert.equal(verdict.ok, false);
  assert.ok(verdict.needMb > verdict.haveMb * 0.85);
  assert.equal(memoryVerdict({ services: all, dockerMemBytes: 16 * gb }).ok, true);
});
