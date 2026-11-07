import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { Field, HardwareError } from "./hardware-ui";
import { ComparisonProvider, useComparison } from "./comparison-provider";
import type { Product } from "../types/hardware";

describe("Hardware erişilebilir durumları", () => {
  it("yardım metni alanın adını değiştirmez; açıklama olarak bağlanır", () => {
    render(<Field label="Model" hint="Optional manufacturer"><input /></Field>);
    const field = screen.getByRole("textbox", { name: "Model" });
    expect(field).toHaveAccessibleDescription("Optional manufacturer");
  });
  it.each([401, 403, 404, 409, 422, 429, 500, 503])("%i hata durumu ayrı yerelleştirilir", (status) => {
    render(<NextIntlClientProvider locale="tr" messages={tr}><HardwareError error={new ApiHttpError(status)} /></NextIntlClientProvider>);
    expect(screen.getByRole("alert")).toHaveTextContent(tr.Hardware.errors[String(status) as keyof typeof tr.Hardware.errors].title);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});

function SelectionHarness() {
  const selection = useComparison();
  const product = (id: string, category: "CPU" | "GPU") => ({ id, category, model: id } as Product);
  return <><button onClick={() => selection.toggle(product("cpu-1", "CPU"))}>First</button><button onClick={() => selection.toggle(product("cpu-2", "CPU"))}>Second</button><button onClick={() => selection.toggle(product("gpu-1", "GPU"))}>Other category</button></>;
}
it("karşılaştırma iki aynı kategori seçer; yanlış kategoriyi ve tekrarı yönetir", () => {
  render(<NextIntlClientProvider locale="tr" messages={tr}><ComparisonProvider><SelectionHarness /></ComparisonProvider></NextIntlClientProvider>);
  fireEvent.click(screen.getByRole("button", { name: "First" }));
  fireEvent.click(screen.getByRole("button", { name: "Other category" }));
  expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeDisabled();
  expect(screen.getByRole("status")).toHaveTextContent("Aynı kategoriden");
  fireEvent.click(screen.getByRole("button", { name: "Second" }));
  expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeEnabled();
  fireEvent.click(screen.getByRole("button", { name: "First" }));
  expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeDisabled();
});
