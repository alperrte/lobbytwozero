import { act, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { changeTheme, finishThemeTransition } from "@/shared/lib/theme/theme-transition";
import { ThemeTransitionLayer } from "./theme-transition-layer";

const html = () => document.documentElement;

function stubMedia({ reduce = false, compact = false } = {}) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("reduced-motion") ? reduce : query.includes("max-width") ? compact : false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    }))
  );
}

function renderLayer() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      <button type="button">odak</button>
      <ThemeTransitionLayer />
    </NextIntlClientProvider>
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  localStorage.clear();
  html().dataset.theme = "dark";
  stubMedia();
});

afterEach(() => {
  act(() => finishThemeTransition());
  vi.useRealTimers();
  vi.unstubAllGlobals();
  html().removeAttribute("data-theme");
  html().removeAttribute("data-theme-pref");
  html().removeAttribute("data-transition");
});

describe("ThemeTransitionLayer", () => {
  it("geçiş yokken hiçbir şey çizmez", () => {
    renderLayer();
    expect(screen.queryByTestId("theme-transition")).toBeNull();
  });

  it.each([
    [undefined, "light", "v3-daybreak"],
    [undefined, "dark", "v3-blackout"],
    ["v2", "light", "daybreak"],
    ["v2", "dark", "blackout"],
    ["v1", "light", "sunrise"],
    ["v1", "dark", "storm"]
  ] as const)("paket %s → %s: %s sahnesini çizer", (pack, target, scene) => {
    if (pack) {
      html().dataset.transition = pack;
    }
    html().dataset.theme = target === "light" ? "dark" : "light";
    renderLayer();
    act(() => changeTheme(target));
    const layer = screen.getByTestId("theme-transition");
    expect(layer).toHaveAttribute("data-scene", scene);
    expect(layer).toHaveAttribute("aria-hidden", "true");
    expect(layer).toHaveClass("ltz-fx");
  });

  it("odağı çalmaz ve süre sonunda DOM'dan kalkar", () => {
    renderLayer();
    const button = screen.getByRole("button", { name: "odak" });
    button.focus();
    act(() => changeTheme("light"));
    expect(document.activeElement).toBe(button);
    expect(screen.getByTestId("theme-transition").querySelector("[tabindex], button, a")).toBeNull();

    act(() => {
      vi.advanceTimersByTime(2600);
    });
    expect(screen.queryByTestId("theme-transition")).toBeNull();
    expect(html().dataset.theme).toBe("light");
  });

  it("tema değişmeden geri tıklanırsa örtü hemen kalkar ve tema yerinde kalır", () => {
    renderLayer();
    act(() => changeTheme("light"));
    act(() => {
      vi.advanceTimersByTime(300);
    });
    act(() => changeTheme("dark"));
    expect(screen.queryByTestId("theme-transition")).toBeNull();
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(html().dataset.theme).toBe("dark");
  });

  it("tema değiştikten sonra tekrar tıklanırsa tek örtü kalır ve son istek kazanır", () => {
    renderLayer();
    act(() => changeTheme("light"));
    act(() => {
      vi.advanceTimersByTime(1900);
    });
    expect(html().dataset.theme).toBe("light");
    act(() => changeTheme("dark"));
    expect(screen.getAllByTestId("theme-transition")).toHaveLength(1);
    expect(screen.getByTestId("theme-transition")).toHaveAttribute("data-scene", "v3-blackout");
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.queryByTestId("theme-transition")).toBeNull();
    expect(html().dataset.theme).toBe("dark");
  });

  it("hareket azaltmada örtü hiç oluşmaz", () => {
    stubMedia({ reduce: true });
    renderLayer();
    act(() => changeTheme("light"));
    expect(screen.queryByTestId("theme-transition")).toBeNull();
    expect(html().dataset.theme).toBe("light");
  });
});
