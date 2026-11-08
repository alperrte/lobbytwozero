import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { languageRegion } from "../services/language-options";
import { GameLanguage, GameLanguageList } from "./game-language";

const wrap = (node: React.ReactNode) => render(<NextIntlClientProvider locale="tr" messages={messages}>{node}</NextIntlClientProvider>);
afterEach(cleanup);

describe("Dil gösterimi: bayrak + dil adı", () => {
  it("bölge kodundan bayrak görseli ve yanında dil adı gösterir; ham kod (de-de) gösterilmez", () => {
    const { container } = wrap(<GameLanguage code="de-de" />);
    expect(screen.getByText("Almanca (Almanya)")).toBeVisible();
    expect(screen.queryByText("de-de")).toBeNull();
    const flag = container.querySelector("img")!;
    expect(decodeURIComponent(flag.getAttribute("src") ?? "")).toContain("flagcdn.com/w80/de.png");
    expect(flag).toHaveAttribute("alt", "");
  });
  it("bölgesiz dilde (ar) bayrak uydurmaz; nötr simge gösterir", () => {
    const { container } = wrap(<GameLanguage code="ar" />);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("Arapça")).toBeVisible();
  });
  it("bayrak görseli açılmazsa nötr simgeye düşer ama dil adı kalır", () => {
    const { container } = wrap(<GameLanguage code="en-us" />);
    fireEvent.error(container.querySelector("img")!);
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByText("Amerikan İngilizcesi")).toBeVisible();
  });
  it("liste her dili ayrı öğede gösterir", () => {
    wrap(<GameLanguageList codes={["tr-tr", "en-gb", "pt-br"]} />);
    expect(screen.getAllByRole("listitem")).toHaveLength(3);
    expect(screen.getByText(/^Türkçe/)).toBeVisible();
  });
  it("yalnız geçerli iki harfli bölge kodundan bayrak türetilir", () => {
    expect(languageRegion("tr-tr")).toBe("TR");
    expect(languageRegion("zh-tw")).toBe("TW");
    expect(languageRegion("ar")).toBeUndefined();
    expect(languageRegion("xx-1")).toBeUndefined();
    expect(languageRegion("en-us-posix")).toBe("US");
  });
});
