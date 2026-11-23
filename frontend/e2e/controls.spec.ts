import { expect, test, type Locator, type Page } from "@playwright/test";

// Ortak oval kontroller (frontend/frontend-layout.md §3): SearchField ve Select üzerinde kırmızı iz yalnız fare
// gelince BİR tur döner; klavye odağı tetiklemez; Select klavyeyle açılıp seçilebilir.
async function gateway(page: Page) {
  await page.context().addCookies([{ name: "XSRF-TOKEN", value: "fixture-csrf", url: "http://localhost:5101" }]);
  await page.route("http://localhost:5080/api/v1/**", (route) => {
    const headers = {
      "access-control-allow-origin": "http://localhost:5101",
      "access-control-allow-credentials": "true",
      "access-control-allow-headers": "content-type, x-xsrf-token, accept",
      "access-control-allow-methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS"
    };
    if (route.request().method() === "OPTIONS") return route.fulfill({ status: 204, headers });
    return route.fulfill({ status: 404, headers, json: {} });
  });
}

async function hydrated(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) => key.startsWith("__reactProps"))
  );
}

async function expectOneLapOnHover(page: Page, control: Locator, snake: Locator) {
  await expect(snake).toHaveAttribute("data-snake-state", "idle");
  await control.hover();
  await expect(snake).toHaveAttribute("data-snake-state", "running");
  await page.mouse.move(2, 2);
  await expect(snake).toHaveAttribute("data-snake-state", "idle", { timeout: 6000 });
  await page.waitForTimeout(1500); // kendiliğinden yeni tur başlamaz
  await expect(snake).toHaveAttribute("data-snake-state", "idle");
}

test.beforeEach(async ({ page }) => {
  await gateway(page);
  await page.setViewportSize({ width: 1440, height: 900 });
});

test("SearchField: hover tek tur iz, klavye odağı tetiklemez", async ({ page }) => {
  await page.goto("/tr/donanimlar");
  await hydrated(page);
  const search = page.getByRole("searchbox", { name: "Model ara" });
  const snake = search.locator("xpath=..").getByTestId("control-snake");
  await expect(search).toBeVisible();

  await search.focus();
  await page.waitForTimeout(800);
  await expect(snake).toHaveAttribute("data-snake-state", "idle");
  await search.blur();

  await expectOneLapOnHover(page, search, snake);
  const radius = await search.evaluate((node) => getComputedStyle(node).borderTopLeftRadius);
  expect(radius).toBe("12px"); // oval (rounded-xl)
});

test("Select: hover tek tur iz, odak tetiklemez, klavyeyle seçim", async ({ page }) => {
  await page.goto("/tr/iletisim");
  await hydrated(page);
  const subject = page.getByRole("combobox", { name: /Konu/ });
  const snake = subject.getByTestId("control-snake");
  await expect(subject).toBeVisible();

  await subject.focus();
  await page.waitForTimeout(800);
  await expect(snake).toHaveAttribute("data-snake-state", "idle");

  await page.keyboard.press("ArrowDown");
  const listbox = page.getByRole("listbox");
  await expect(listbox).toBeVisible();
  await expect(subject).toHaveAttribute("aria-expanded", "true");
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("Enter");
  await expect(listbox).toBeHidden();
  await expect(subject).toHaveText(/Geri Bildirim/);
  await expect(subject).toBeFocused();
  await expect(page.locator('input[type="hidden"][name="subject"]')).toHaveValue("feedback");

  await subject.blur();
  await expectOneLapOnHover(page, subject, snake);
});
