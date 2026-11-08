import { test, expect, type Page } from "@playwright/test";

/** React bağlanana kadar bekler (etkileşim hydration öncesi kaybolmasın). */
async function waitForHydration(page: Page) {
  await page.waitForFunction(() =>
    Object.keys(document.querySelector("button") ?? {}).some((key) =>
      key.startsWith("__reactProps")
    )
  );
}

test("Ana sayfa acilir ve varsayilan dil Turkcedir", async ({
  page
}) => {
  await page.goto("/");

  await expect(page).toHaveTitle("LTZ | Anasayfa");
  await expect(page.locator("html")).toHaveAttribute("lang", "tr");
  await expect(
    page.getByRole("heading", { name: "LobbyTwoZero" })
  ).toBeVisible();
});

test("Dil Ingilizceye degistirilebilir ve ayni rotada kalir", async ({
  page
}) => {
  await page.goto("/");
  await waitForHydration(page);

  await page.getByRole("button", { name: "Dil: Türkçe" }).click();
  await page.getByRole("menuitemradio", { name: "English" }).click();

  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(
    page.getByText(
      "A game-focused social platform where players discover, talk and play together."
    )
  ).toBeVisible();
  await expect(page).toHaveURL(/\/en\/?$/);
});
