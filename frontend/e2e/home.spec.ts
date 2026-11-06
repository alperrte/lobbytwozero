import { test, expect } from "@playwright/test";

test("Ana sayfa acilir ve varsayilan dil Turkcedir", async ({
  page
}) => {
  await page.goto("/");

  await expect(page).toHaveTitle(/LobbyTwoZero/);
  await expect(page.locator("html")).toHaveAttribute("lang", "tr");
  await expect(
    page.getByRole("heading", { name: "LobbyTwoZero" })
  ).toBeVisible();
});

test("Dil Ingilizceye degistirilebilir ve ayni rotada kalir", async ({
  page
}) => {
  await page.goto("/");

  await page.getByRole("button", { name: "İngilizce (EN)" }).click();

  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(
    page.getByText("A new meeting place for the gaming community.")
  ).toBeVisible();
  await expect(page).toHaveURL(/\/$/);
});
