import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { SettingsForm } from "./settings-form";

vi.mock("next/navigation", () => ({
  usePathname: () => "/settings",
  useRouter: () => ({ refresh: vi.fn() })
}));
vi.mock("@/shared/lib/i18n/actions", () => ({ setLocale: vi.fn() }));

beforeEach(() => {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: false,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn()
  }));
  const root = document.documentElement;
  for (const attribute of ["data-theme", "data-theme-pref", "data-navbar", "data-sidebar"]) {
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
  it("dört ayar grubunu kartlar halinde sunar; hesap ve güvenlik ayarı yoktur", () => {
    renderForm();
    for (const name of ["Görünüm", "Üst çubuk", "Kenar çubuğu", "Dil"]) {
      expect(screen.getByRole("region", { name })).toBeInTheDocument();
    }
    expect(screen.queryByText(/güvenlik|parola|şifre/i)).not.toBeInTheDocument();
  });

  it("her grubun yanında yuvarlak 'i' bilgi düğmesi bulunur ve ne işe yaradığını anlatır", () => {
    renderForm();
    for (const subject of ["Görünüm", "Üst çubuk", "Kenar çubuğu", "Dil"]) {
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
});
