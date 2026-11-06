import { test, expect, type Page } from "@playwright/test";

const pages = ["/tr/ayarlar", "/tr/sss", "/tr/hakkimizda", "/tr/iletisim"] as const;

const viewports = [
  { width: 1920, height: 1080 },
  { width: 1440, height: 900 },
  { width: 1280, height: 800 },
  { width: 768, height: 1024 },
  { width: 390, height: 844 },
  { width: 360, height: 740 }
] as const;

/** React bağlanana kadar bekler (etkileşim hydration öncesi kaybolmasın). */
async function waitForHydration(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) =>
      key.startsWith("__reactProps")
    )
  );
}

async function horizontalOverflow(page: Page) {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth
  );
}

for (const viewport of viewports) {
  test.describe(`${viewport.width}x${viewport.height}`, () => {
    test.use({ viewport });

    for (const path of pages) {
      test(`${path}: yatay taşma yok, navbar ve footer yerinde`, async ({ page }) => {
        await page.goto(path);
        await waitForHydration(page);

        expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await expect(page.getByRole("banner")).toBeVisible();

        const main = page.locator("#main-content");
        const footer = page.getByRole("contentinfo");
        const mainBox = await main.boundingBox();
        const footerBox = await footer.boundingBox();
        expect(mainBox).not.toBeNull();
        expect(footerBox).not.toBeNull();
        // Footer ana içeriğin altında, normal akışta ve tam genişlikte durur.
        expect(footerBox!.y).toBeGreaterThanOrEqual(mainBox!.y + mainBox!.height - 1);
        expect(footerBox!.width).toBeGreaterThanOrEqual(viewport.width - 1);
      });
    }
  });
}

test.describe("masaüstü kabuk", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("kenar çubuğu içeriğin solunda sabit kalır ve sayfalar onu bozmaz", async ({ page }) => {
    for (const path of pages) {
      await page.goto(path);
      await waitForHydration(page);

      const sidebar = page.locator("#app-sidebar");
      await expect(sidebar).toBeVisible();
      const sidebarBox = await sidebar.boundingBox();
      const mainBox = await page.locator("#main-content").boundingBox();
      expect(sidebarBox!.x + sidebarBox!.width).toBeLessThanOrEqual(mainBox!.x + 1);
    }
  });

  test("footer GitHub bağlantısı depoya yeni sekmede güvenle gider", async ({ page }) => {
    await page.goto("/tr/hakkimizda");
    const github = page
      .getByRole("contentinfo")
      .getByRole("link", { name: "LobbyTwoZero GitHub deposu" });

    await expect(github).toHaveAttribute("href", "https://github.com/alperrte/lobbytwozero");
    await expect(github).toHaveAttribute("target", "_blank");
    await expect(github).toHaveAttribute("rel", /noopener/);
    await expect(github).toHaveAttribute("rel", /noreferrer/);
  });
});

test.describe("İletişim demo formu", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("boş gönderim hata verir, geçerli gönderim ağ isteği yapmaz", async ({ page }) => {
    const apiCalls: string[] = [];
    page.on("request", (request) => {
      if (request.method() !== "GET") {
        apiCalls.push(`${request.method()} ${request.url()}`);
      }
    });

    await page.goto("/tr/iletisim");
    await waitForHydration(page);

    await page.getByRole("button", { name: "Formu Kontrol Et" }).click();
    await expect(page.getByRole("form", { name: "İletişim formu" }).getByRole("alert")).toContainText(
      "4 alan"
    );
    await expect(page.getByLabel(/Ad Soyad/)).toBeFocused();
    await expect(page.getByLabel(/Ad Soyad/)).toHaveAttribute("aria-invalid", "true");

    const form = page.getByRole("form", { name: "İletişim formu" });
    await form.getByRole("textbox", { name: "Ad Soyad" }).fill("Ada Lovelace");
    await form.getByRole("textbox", { name: "E-posta" }).fill("ada@example.com");
    await form.getByRole("combobox", { name: "Konu" }).selectOption("feedback");
    await form.getByRole("textbox", { name: "Mesaj" }).fill("Bu bir deneme mesajıdır.");
    await page.getByRole("button", { name: "Formu Kontrol Et" }).click();

    await expect(
      page.getByText("Form doğrulaması başarılı. Bu bir demo olduğu için mesajınız gönderilmedi.")
    ).toBeVisible();
    await expect(page.getByText(/başarıyla gönderildi/i)).toHaveCount(0);

    expect(apiCalls).toEqual([]);
    const storage = await page.evaluate(() => ({
      local: Object.entries(window.localStorage).filter(([key]) => !key.startsWith("ltz")),
      session: window.sessionStorage.length
    }));
    expect(storage.local).toEqual([]);
    expect(storage.session).toBe(0);
  });
});
