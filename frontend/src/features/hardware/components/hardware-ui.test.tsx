import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { ApiHttpError } from "@/shared/lib/api/client";
import { Field, HardwareError } from "./hardware-ui";
import { ComparisonProvider, useComparison } from "./comparison-provider";
import type { Product } from "../types/hardware";

const push = vi.fn();
vi.mock("@/shared/lib/i18n/navigation", () => ({
  usePathname: () => "/hardware/campaigns",
  useRouter: () => ({ push, replace: vi.fn() }),
  Link: ({ children }: { children: React.ReactNode }) => <a href="#">{children}</a>
}));

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
  return <>
    {["cpu-1", "cpu-2", "cpu-3", "cpu-4", "cpu-5"].map((id) => <button key={id} onClick={() => selection.toggle(product(id, "CPU"))}>{id}</button>)}
    <button onClick={() => selection.toggle(product("gpu-1", "GPU"))}>Other category</button>
  </>;
}
describe("Karşılaştırma seçimi", () => {
  beforeEach(() => push.mockClear());
  const setup = () => render(<NextIntlClientProvider locale="tr" messages={tr}><ComparisonProvider><SelectionHarness /></ComparisonProvider></NextIntlClientProvider>);

  it("iki ile dört aynı kategori ürününü seçer; yanlış kategoriyi reddeder ve tekrarı çıkarır", () => {
    setup();
    fireEvent.click(screen.getByRole("button", { name: "cpu-1" }));
    fireEvent.click(screen.getByRole("button", { name: "Other category" }));
    expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("Aynı kategoriden");
    fireEvent.click(screen.getByRole("button", { name: "cpu-2" }));
    expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "cpu-1" }));
    expect(screen.getByRole("button", { name: "Karşılaştır" })).toBeDisabled();
  });
  it("dördüncü üründen sonra beşincisini reddeder ve sınırı bildirir", () => {
    setup();
    for (const id of ["cpu-1", "cpu-2", "cpu-3", "cpu-4"]) fireEvent.click(screen.getByRole("button", { name: id }));
    expect(screen.getByText("4 / 4 ürün seçildi")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "cpu-5" }));
    expect(screen.getByText("4 / 4 ürün seçildi")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("en fazla 4 farklı ürün");
  });
  it("Karşılaştır düğmesi açılır pencere yerine seçilen kimliklerle karşılaştırma sayfasına gider", () => {
    setup();
    for (const id of ["cpu-1", "cpu-2", "cpu-3"]) fireEvent.click(screen.getByRole("button", { name: id }));
    fireEvent.click(screen.getByRole("button", { name: "Karşılaştır" }));
    expect(push).toHaveBeenCalledWith({ pathname: "/hardware/compare", query: { ids: "cpu-1,cpu-2,cpu-3" } });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
