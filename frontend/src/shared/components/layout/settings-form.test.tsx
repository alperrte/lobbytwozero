import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { SettingsForm } from "./settings-form";

const replaceMock = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({
  // next-intl/navigation bu dışa aktarımlara ihtiyaç duyar; test yalnız yönlendirmeyi gözler.
  usePathname: () => "/tr/ayarlar",
  useRouter: () => ({ replace: replaceMock, push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

beforeEach(() => {
  replaceMock.mockReset();
  window.localStorage.clear();
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: false,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn()
  }));
  const root = document.documentElement;
  for (const attribute of ["data-theme", "data-theme-pref", "data-navbar", "data-sidebar", "data-transition"]) {
    root.removeAttribute(attribute);
  }
});

function renderForm() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <SettingsForm />
    </NextIntlClientProvider>
  );
}

describe("SettingsForm", () => {
  it("dört ayar grubunu Görünüm, Dil, Üst çubuk, Kenar çubuğu, Tema geçişi sırasıyla sunar; hesap ve güvenlik ayarı yoktur", () => {
    renderForm();
    const names = ["Görünüm", "Dil", "Üst çubuk", "Kenar çubuğu", "Tema geçişi"];
    const regions = names.map((name) => screen.getByRole("region", { name }));
    regions.forEach((region, index) => {
      if (index > 0) {
        expect(
          regions[index - 1].compareDocumentPosition(region) & Node.DOCUMENT_POSITION_FOLLOWING
        ).toBeTruthy();
      }
    });
    expect(screen.queryByText(/güvenlik|parola|şifre/i)).not.toBeInTheDocument();
  });

  it("her grubun yanında yuvarlak 'i' bilgi düğmesi bulunur ve ne işe yaradığını anlatır", () => {
    renderForm();
    for (const subject of ["Görünüm", "Üst çubuk", "Kenar çubuğu", "Dil", "Tema geçişi"]) {
      const button = screen.getByRole("button", { name: `${subject} hakkında bilgi` });
      fireEvent.focus(button);
      expect(screen.getByRole("tooltip").textContent?.length).toBeGreaterThan(20);
      fireEvent.blur(button);
    }
  });

  it("tercihler anında uygulanır: tema, üst çubuk ve kenar çubuğu", () => {
    renderForm();
    const root = document.documentElement;

    fireEvent.click(screen.getByRole("radio", { name: /^Koyu/ }));
    expect(root.dataset.theme).toBe("dark");

    fireEvent.click(screen.getByRole("radio", { name: /^Sabit/ }));
    expect(root.dataset.navbar).toBe("fixed");

    fireEvent.click(screen.getByRole("radio", { name: /^Daraltılmış/ }));
    expect(root.dataset.sidebar).toBe("collapsed");
  });

  it("masaüstünde iki sütun, dar ekranda tek sütun ızgarası kullanır", () => {
    const { container } = renderForm();
    const grid = container.querySelector("section")?.parentElement as HTMLElement;
    expect(grid.className).toContain("lg:grid-cols-2");
    expect(grid.className).not.toMatch(/(^|\s)(sm:|md:)?grid-cols-2/);
  });

  it("seçenekler seçili durumu işaretler ve Kaydet düğmesi yoktur", () => {
    renderForm();
    expect(screen.getByRole("radio", { name: /^Sistem/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /^Otomatik gizle/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /^Geniş/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /^Türkçe/ })).toBeChecked();
    expect(screen.queryByRole("button", { name: /kaydet/i })).not.toBeInTheDocument();
    expect(screen.getByText(/yalnızca bu cihazda saklanır/)).toBeInTheDocument();
  });

  it("dil seçimi eşdeğer sayfaya yönlendirir; mevcut dil yeniden seçilince yönlendirmez", async () => {
    renderForm();
    fireEvent.click(screen.getByRole("radio", { name: /^Türkçe/ }));
    expect(replaceMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("radio", { name: /^English/ }));
    // /tr/ayarlar → /en/settings: aynı sayfanın İngilizce karşılığına geçilir.
    await waitFor(() => expect(replaceMock).toHaveBeenCalledTimes(1));
    expect(replaceMock.mock.calls[0][0]).toBe("/en/settings");
  });

  it("tema, üst çubuk ve kenar çubuğu tercihleri bu cihazda kalıcı saklanır", () => {
    renderForm();

    fireEvent.click(screen.getByRole("radio", { name: /^Açık/ }));
    fireEvent.click(screen.getByRole("radio", { name: /^Sabit/ }));
    fireEvent.click(screen.getByRole("radio", { name: /^Daraltılmış/ }));

    expect(window.localStorage.getItem("ltz-theme")).toBe("light");
    expect(window.localStorage.getItem("ltz-navbar")).toBe("fixed");
    expect(window.localStorage.getItem("ltz-sidebar")).toBe("collapsed");
  });

  it("tema geçişi paketi Taktik V3 varsayılanıyla gelir; V2 legacy, V1 ve Sade seçilince uygulanır, kalıcı saklanır ve temayı değiştirmez", () => {
    renderForm();
    const root = document.documentElement;
    const options = within(screen.getByRole("group", { name: "Tema geçişi" })).getAllByRole("radio");
    expect(options).toHaveLength(4);
    expect(screen.getByRole("radio", { name: /^Taktik V3/ })).toBeChecked();

    fireEvent.click(screen.getByRole("radio", { name: /^Taktik V2 — Eski sürüm/ }));
    expect(root.dataset.transition).toBe("v2");
    expect(window.localStorage.getItem("ltz-transition")).toBe("v2");
    expect(screen.getByRole("radio", { name: /^Taktik V2/ })).toBeChecked();

    fireEvent.click(screen.getByRole("radio", { name: /^Klasik V1/ }));
    expect(root.dataset.transition).toBe("v1");
    expect(window.localStorage.getItem("ltz-transition")).toBe("v1");
    expect(screen.getByRole("radio", { name: /^Klasik V1/ })).toBeChecked();

    fireEvent.click(screen.getByRole("radio", { name: /^Sade/ }));
    expect(root.dataset.transition).toBe("minimal");
    expect(window.localStorage.getItem("ltz-transition")).toBe("minimal");

    fireEvent.click(screen.getByRole("radio", { name: /^Taktik V3/ }));
    expect(root.dataset.transition).toBeUndefined();
    expect(window.localStorage.getItem("ltz-transition")).toBeNull();
    expect(root.dataset.theme).toBeUndefined();
  });
});
