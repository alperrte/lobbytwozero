import { expect, type Locator } from "@playwright/test";

/**
 * Ortak `Select` (combobox düğmesi + portal listbox) için `locator.selectOption` karşılığı.
 * Yerel `<select>` olmadığından (frontend-layout.md §3) seçim, listeyi açıp seçeneğe tıklayarak yapılır.
 * `choice` seçeneğin değeridir (`data-value`) ya da `{ index }` ile sıra numarasıdır.
 */
export async function chooseOption(trigger: Locator, choice: string | { index: number }) {
  await trigger.click();
  await expect(trigger).toHaveAttribute("aria-expanded", "true");
  const listbox = trigger.page().locator(`#${await trigger.getAttribute("aria-controls")}`);
  const option =
    typeof choice === "string"
      ? listbox.locator(`[role="option"][data-value="${choice}"]`)
      : listbox.getByRole("option").nth(choice.index);
  await option.click();
  await expect(trigger).toHaveAttribute("aria-expanded", "false");
}
