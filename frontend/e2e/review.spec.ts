import { expect, test } from "@playwright/test";
import { fixture, gameId, now, otherReviewId } from "./fixtures/review-gateway";


test("merkez → oyun → puan → tavsiye → inceleme yaz → detay → düzenle → sil", async ({ page }) => {
  const world = await fixture(page);
  await page.goto("/tr/incelemeler");
  const main = page.getByRole("main");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  await expect(main.getByRole("heading", { level: 1, name: "İncelemeler" })).toBeVisible();
  await expect(main.getByRole("heading", { name: "Review Fixture Game" })).toBeVisible();
  await expect(main.getByText("Henüz puan yok")).toBeVisible();
  // Kenar çubuğundaki İncelemeler öğesi gerçek bağlantıdır ve aktiftir.
  await expect(page.getByRole("navigation", { name: "Ana gezinme" }).getByRole("link", { name: "İncelemeler" })).toHaveAttribute("aria-current", "page");

  await main.getByRole("searchbox", { name: "Katalogda oyun ara" }).fill("Review");
  await main.getByRole("button", { name: "Ara", exact: true }).click();
  await expect(page).toHaveURL(/\?q=Review/);
  await main.getByRole("link", { name: /İncelemeleri aç: Review Fixture Game/ }).click();
  await expect(page).toHaveURL(`/tr/incelemeler/oyun/${gameId}`);
  await expect(main.getByRole("heading", { level: 1, name: "Review Fixture Game" })).toBeVisible();
  await expect(main.getByText("Başka oyuncunun incelemesi")).toBeVisible();
  // Yazar adı User toplu kartından gelir (tek istek) ve profil bağlantısına çıkar.
  await expect(main.getByRole("link", { name: "Fixture Yazar" })).toHaveAttribute("href", "/tr/oyuncular/0198a3c2-0000-7d44-9a55-3f1c2b4d5e02");
  expect(world.requests.filter((entry) => entry === "POST /api/v1/user/profiles/batch")).toHaveLength(1);

  // Puan: yalnız seçince kaydedilir; kayıt yokluğu 0 puan değildir.
  await expect(main.getByText("Henüz puan vermedin.")).toBeVisible();
  await expect(main.getByRole("button", { name: "Puanı kaydet" })).toBeDisabled();
  await main.getByRole("radio", { name: "8 puan" }).click();
  await main.getByRole("button", { name: "Puanı kaydet" }).click();
  await expect(main.getByText("Puanın kaydedildi.")).toBeVisible();
  await expect(main.getByText("8 / 10").first()).toBeVisible();

  // Tavsiye puandan bağımsızdır.
  await main.getByRole("radio", { name: "Önermiyorum" }).click();
  await main.getByRole("button", { name: "Tavsiyeyi kaydet" }).click();
  await expect(main.getByText("Tavsiyen kaydedildi.")).toBeVisible();
  expect(world.rating?.rating).toBe(8);
  expect(world.recommendation?.recommended).toBe(false);

  // İnceleme yaz.
  await main.getByRole("link", { name: "İnceleme yaz" }).click();
  await expect(page).toHaveURL(`/tr/incelemeler/oyun/${gameId}/yaz`);
  await main.getByLabel("İnceleme metni").fill("İlk incelemem");
  await main.getByRole("button", { name: "İncelemeyi yayınla" }).click();
  await expect(page).toHaveURL(/\/tr\/incelemeler\/0198a3c2-9999-7d44-9a55-3f1c2b4d5e66$/);
  await expect(main.getByRole("heading", { level: 1, name: "Senin incelemen" })).toBeVisible();
  await expect(main.getByText("İlk incelemem")).toBeVisible();

  // Düzenle.
  await main.getByRole("button", { name: "Düzenle" }).click();
  await main.getByLabel("İnceleme metni").fill("Düzenlenmiş incelemem");
  await main.getByRole("button", { name: "Değişiklikleri kaydet" }).click();
  await expect(main.getByText("İncelemen güncellendi.")).toBeVisible();
  await expect(main.getByText("Düzenlenmiş incelemem")).toBeVisible();

  // Sil: açıklayıcı onay, puan ve tavsiye kalır.
  await main.getByRole("button", { name: "Sil" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("puan ve tavsiye korunur");
  await expect(dialog.getByRole("button", { name: "Vazgeç" })).toBeFocused();
  await dialog.getByRole("button", { name: "İncelemeyi sil" }).click();
  await expect(page).toHaveURL(`/tr/incelemeler/oyun/${gameId}`);
  expect(world.rating?.rating).toBe(8);
  expect(world.recommendation?.recommended).toBe(false);
  expect(world.reviews.some((item) => item.ownedByActor)).toBe(false);

  // Her yazma isteği CSRF başlığıyla ve doğru gövdeyle gitti.
  const bodies = world.writes.map((write) => `${write.method} ${write.path.replace(gameId, "G").replace("0198a3c2-9999-7d44-9a55-3f1c2b4d5e66", "R")}`);
  expect(bodies).toEqual([
    "PUT /api/v1/review/games/G/rating",
    "PUT /api/v1/review/games/G/recommendation",
    "POST /api/v1/review/games/G/reviews",
    "PATCH /api/v1/review/reviews/R",
    "DELETE /api/v1/review/reviews/R"
  ]);
  for (const write of world.writes) expect(write.csrf).toBe("fixture-csrf");
  // Merkez, sayfa başına TEK toplu istekle puanları okur (oyun başına istek yok): ilk yükleme + arama.
  expect(world.batchCsrf).toEqual(["fixture-csrf", "fixture-csrf"]);
  expect(world.writes[3].body).toEqual({ version: 0, body: "Düzenlenmiş incelemem" });
  // Tarayıcı yalnız Gateway'e (5080) gitti; Review servisine doğrudan istek yok.
  expect(world.requests.some((request) => request.includes("/internal/"))).toBe(false);
});

test("başkasının incelemesi beğenilir; kendi incelemende beğeni yoktur", async ({ page }) => {
  const world = await fixture(page);
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Benim incelemem", ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 2, liked: false });
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const main = page.getByRole("main");
  const others = main.getByRole("article").filter({ hasText: "Başka oyuncunun incelemesi" });
  await others.getByRole("button", { name: /Beğen/ }).click();
  await expect(others.getByRole("button", { name: /Beğeniyi kaldır/ })).toHaveAttribute("aria-pressed", "true");
  await expect(others.getByText("11", { exact: true })).toBeVisible();
  await others.getByRole("button", { name: /Beğeniyi kaldır/ }).click();
  await expect(others.getByText("10", { exact: true })).toBeVisible();
  const mine = main.getByRole("article").filter({ hasText: "Benim incelemem" });
  await expect(mine.getByRole("button", { name: /Beğen/ })).toHaveCount(0);
  expect(world.writes.map((write) => write.method)).toEqual(["PUT", "DELETE"]);
});

test("asgari beğeni filtresi URL'de tutulur ve listeyi gerçek sunucu yanıtıyla daraltır", async ({ page }) => {
  await fixture(page);
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const main = page.getByRole("main");
  await expect(main.getByText("1 inceleme")).toBeVisible();
  await main.getByLabel("En az beğeni").fill("50");
  await main.getByRole("button", { name: "Uygula" }).click();
  await expect(page).toHaveURL(/minLikes=50/);
  await expect(main.getByText("Bu filtreyle inceleme yok")).toBeVisible();
  await page.goBack();
  await expect(main.getByText("Başka oyuncunun incelemesi")).toBeVisible();
});

test("kota (429) ve yarışta oluşan tekrar inceleme (409) durumlarını açıklar, metni korur", async ({ page }) => {
  const world = await fixture(page);
  await page.goto(`/tr/incelemeler/oyun/${gameId}/yaz`);
  const main = page.getByRole("main");
  const field = main.getByLabel("İnceleme metni");
  await field.fill("Yazdığım uzun metin");
  world.createStatus = 429;
  await main.getByRole("button", { name: "İncelemeyi yayınla" }).click();
  await expect(main.getByText(/saniye sonra tekrar deneyebilirsin/)).toBeVisible();
  await expect(main.getByRole("button", { name: "İncelemeyi yayınla" })).toBeDisabled();
  await expect(field).toHaveValue("Yazdığım uzun metin");

  // Kota dolmadan önce başka yerde inceleme oluşmuş: sunucu 409 verir, sayfa mevcut incelemeyi sunucudan okur.
  await page.reload();
  const again = main.getByLabel("İnceleme metni");
  await again.fill("İkinci deneme");
  world.createStatus = 409;
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Başka sekmede yazdığım", ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 0, liked: false });
  await main.getByRole("button", { name: "İncelemeyi yayınla" }).click();
  await expect(main.getByRole("link", { name: "İncelemeni aç ve düzenle" })).toBeVisible();
  await expect(main.getByText(/Yazdığın taslak bu sayfada duruyor/)).toBeVisible();
  expect(world.writes.filter((write) => write.method === "POST")).toHaveLength(2);
});

test("sürüm çakışmasında düzenleme taslağı korunur ve güncel hal kullanıcı isteğiyle yüklenir", async ({ page }) => {
  const world = await fixture(page);
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Eski metin", ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 0, liked: false });
  await page.goto("/tr/incelemeler/0198a3c2-8888-7d44-9a55-3f1c2b4d5e66");
  const main = page.getByRole("main");
  await main.getByRole("button", { name: "Düzenle" }).click();
  world.nextVersionConflict = true;
  await main.getByLabel("İnceleme metni").fill("Benim taslağım");
  await main.getByRole("button", { name: "Değişiklikleri kaydet" }).click();
  await expect(main.getByText("İnceleme başka bir yerde değişmiş")).toBeVisible();
  await expect(main.getByLabel("İnceleme metni")).toHaveValue("Benim taslağım");
  await main.getByRole("button", { name: "Güncel halini yükle" }).first().click();
  await expect(main.getByText("Başka sekmede değiştirilen metin")).toBeVisible();
  await expect(main.getByLabel("İnceleme metni")).toHaveValue("Benim taslağım");
  await main.getByRole("button", { name: "Değişiklikleri kaydet" }).click();
  await expect(main.getByText("İncelemen güncellendi.")).toBeVisible();
  expect(world.writes.map((write) => (write.body as { version?: number } | null)?.version)).toEqual([0, 1]);
});

test("istatistik ekranı gerçek istatistik yanıtını gösterir", async ({ page }) => {
  const world = await fixture(page);
  world.rating = { gameId, rating: 7, version: 0, createdAt: now, updatedAt: now };
  world.recommendation = { gameId, recommended: true, version: 0, createdAt: now, updatedAt: now };
  await page.goto(`/tr/incelemeler/oyun/${gameId}/istatistikler`);
  const main = page.getByRole("main");
  await expect(main.getByRole("heading", { level: 1, name: "İnceleme istatistikleri" })).toBeVisible();
  await expect(main.getByRole("list", { name: "Puan dağılımı" }).getByRole("listitem")).toHaveCount(10);
  await expect(main.getByText("7 puan: 1 kullanıcı")).toBeAttached();
  await expect(main.getByText("100%").first()).toBeVisible();
});

test("oturum yokken korumalı sayfa girişe yönlendirir ve dönüş adresini taşır", async ({ page }) => {
  await fixture(page, false);
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  await expect(page).toHaveURL(/\/tr\/giris\?next=/);
  expect(decodeURIComponent(page.url())).toContain(`/tr/incelemeler/oyun/${gameId}`);
});

test("geçersiz kimlikli adresler 404 verir", async ({ page }) => {
  await fixture(page);
  const response = await page.goto("/tr/incelemeler/oyun/730");
  expect(response?.status()).toBe(404);
  const second = await page.goto("/tr/incelemeler/bu-bir-uuid-degil");
  expect(second?.status()).toBe(404);
});

test("mobil görünümde yatay taşma olmaz ve İngilizce metinler çalışır", async ({ page }) => {
  await fixture(page);
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto(`/en/reviews/games/${gameId}`);
  const main = page.getByRole("main");
  await expect(main.getByRole("link", { name: "Write a review" })).toBeVisible();
  await expect(main.getByRole("radiogroup", { name: "Game rating" })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});

test("zaten incelemen varsa oyun sayfası ve yazma ekranı mevcut incelemeye yönlendirir; İncelemelerim listeler", async ({ page }) => {
  const world = await fixture(page);
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Benim eski incelemem", ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 0, liked: false });
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const main = page.getByRole("main");
  await expect(main.getByRole("link", { name: "İncelemeni aç" })).toHaveAttribute("href", /\/tr\/incelemeler\/0198a3c2-8888-7d44-9a55-3f1c2b4d5e66$/);
  await expect(main.getByRole("link", { name: "İnceleme yaz", exact: true })).toHaveCount(0);

  await page.goto(`/tr/incelemeler/oyun/${gameId}/yaz`);
  await expect(main.getByText("Bu oyun için zaten bir incelemen var.")).toBeVisible();
  await expect(main.getByLabel("İnceleme metni")).toHaveCount(0);
  await main.getByRole("link", { name: "İncelemeni aç ve düzenle" }).click();
  await expect(main.getByText("Benim eski incelemem")).toBeVisible();

  await page.goto("/tr/incelemeler");
  await main.getByRole("link", { name: "İncelemelerim" }).click();
  await expect(page).toHaveURL("/tr/incelemeler/incelemelerim");
  await expect(main.getByRole("heading", { level: 1, name: "İncelemelerim" })).toBeVisible();
  await expect(main.getByRole("heading", { name: "Review Fixture Game" })).toBeVisible();
  await expect(main.getByText("Benim eski incelemem")).toBeVisible();
  await expect(main.getByText("1 inceleme")).toBeVisible();
  expect(world.writes).toHaveLength(0);
});

test("öne çıkanlarda sunucunun likedByActor değeri ilk durumda kullanılır", async ({ page }) => {
  const world = await fixture(page);
  world.reviews[0].liked = true;
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const card = page.getByRole("main").getByRole("article").filter({ hasText: "Başka oyuncunun incelemesi" });
  await expect(card.getByRole("button", { name: /Beğeniyi kaldır/ })).toHaveAttribute("aria-pressed", "true");
});

test("başkasının incelemesini raporlar: doğrulama, kayıt, mükerrer engeli ve dürüst bilgilendirme", async ({ page }) => {
  const world = await fixture(page);
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const main = page.getByRole("main");
  const card = main.getByRole("article").filter({ hasText: "Başka oyuncunun incelemesi" });
  await card.getByRole("button", { name: "Raporla" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByLabel("Gerekçe")).toBeFocused();
  await expect(dialog).toContainText("moderatörler tarafından incelenir");
  // Gerekçesiz gönderim sunucuya gitmez.
  await dialog.getByRole("button", { name: "Raporu gönder" }).click();
  await expect(dialog.getByRole("alert")).toContainText("Bir gerekçe seçmelisin.");
  expect(world.writes).toHaveLength(0);

  await dialog.getByLabel("Gerekçe").selectOption("SPAM");
  await dialog.getByLabel("Açıklama (isteğe bağlı)").fill("Reklam içeriyor");
  await dialog.getByRole("button", { name: "Raporu gönder" }).click();
  await expect(dialog).toBeHidden();
  await expect(card.getByText(/Raporladın ·/)).toBeVisible();
  await expect(card.getByRole("button", { name: "Raporla" })).toHaveCount(0);
  expect(world.writes).toHaveLength(1);
  expect(world.writes[0]).toMatchObject({ method: "POST", csrf: "fixture-csrf", body: { reason: "SPAM", detail: "Reklam içeriyor" } });
  expect(world.writes[0].path).toBe(`/api/v1/review/reviews/${otherReviewId}/reports`);

  // Detay sayfası mevcut raporu sunucudan okur ve tekrar raporlatmaz.
  await page.goto(`/tr/incelemeler/${otherReviewId}`);
  await expect(main.getByText(/Raporladın ·/)).toBeVisible();
  await expect(main.getByRole("button", { name: "Raporla" })).toHaveCount(0);
});

test("rapor kotası (429) ve mükerrer rapor (409) durumlarını açıklar; kendi incelemende rapor yoktur", async ({ page }) => {
  const world = await fixture(page);
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Benim incelemem", ownedByActor: true, version: 0, createdAt: now, updatedAt: now, likes: 0, liked: false });
  await page.goto(`/tr/incelemeler/oyun/${gameId}`);
  const main = page.getByRole("main");
  const mine = main.getByRole("article").filter({ hasText: "Benim incelemem" });
  await expect(mine.getByRole("button", { name: "Raporla" })).toHaveCount(0);

  const card = main.getByRole("article").filter({ hasText: "Başka oyuncunun incelemesi" });
  await card.getByRole("button", { name: "Raporla" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Gerekçe").selectOption("OTHER");
  world.reportStatus = 429;
  await dialog.getByRole("button", { name: "Raporu gönder" }).click();
  await expect(dialog.getByText(/saniye sonra tekrar deneyebilirsin/)).toBeVisible();
  await expect(dialog.getByRole("button", { name: "Raporu gönder" })).toBeDisabled();
  await expect(dialog.getByLabel("Gerekçe")).toHaveValue("OTHER");
  await dialog.getByRole("button", { name: "Vazgeç" }).click();

  // Başka yerde zaten raporlanmış: sunucu 409 verir, arayüz "zaten raporladın" der.
  world.reportStatus = 201;
  world.reports[otherReviewId] = { id: "0198a3c2-7777-7d44-9a55-3f1c2b4d5e66", reason: "SPAM", detail: null, createdAt: now };
  await card.getByRole("button", { name: "Raporla" }).click();
  await page.getByRole("dialog").getByLabel("Gerekçe").selectOption("SPAM");
  await page.getByRole("dialog").getByRole("button", { name: "Raporu gönder" }).click();
  await expect(card.getByText("Bu incelemeyi zaten raporladın.")).toBeVisible();
});

test("moderatörün gizlediği inceleme yazara durumuyla görünür: düzenleme yok, silme var; İncelemelerim rozet gösterir", async ({ page }) => {
  const world = await fixture(page);
  world.reviews.push({ id: "0198a3c2-8888-7d44-9a55-3f1c2b4d5e66", gameId, body: "Gizlenen incelemem", ownedByActor: true, version: 1, createdAt: now, updatedAt: now, likes: 0, liked: false, status: "HIDDEN" });
  await page.goto("/tr/incelemeler/incelemelerim");
  const main = page.getByRole("main");
  await expect(main.getByText("Gizlendi", { exact: true })).toBeVisible();
  await page.goto("/tr/incelemeler/0198a3c2-8888-7d44-9a55-3f1c2b4d5e66");
  await expect(main.getByText("Bu inceleme moderatörler tarafından gizlendi")).toBeVisible();
  await expect(main.getByText("Gizlenen incelemem")).toBeVisible();
  await expect(main.getByRole("button", { name: "Düzenle" })).toHaveCount(0);
  await expect(main.getByRole("button", { name: "Sil" })).toBeVisible();
  // Beğeni ucu gizli incelemede hiç istenmez (backend 404 verir).
  expect(world.requests.some((request) => request.endsWith("/like"))).toBe(false);
});
