import { fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { emptyFilters } from "../types/community.types";
import { MyPostsToolbar } from "./my-posts-toolbar";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));
vi.mock("../services/community-api", () => ({ searchGames: vi.fn().mockResolvedValue([]), fetchGameCards: vi.fn().mockResolvedValue([]) }));

function mount(view: "posts" | "reposts" = "posts", onChange = vi.fn()) {
  render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <MyPostsToolbar view={view} filters={emptyFilters} onChange={onChange} />
    </NextIntlClientProvider>
  );
  return onChange;
}

describe("Gönderilerim araç çubuğu", () => {
  it("Yeni/Eski buton grubu yok; 'Öncelik' sağ grupta Filtrele'nin hemen solunda, Yeniden paylaşımlarım'dan önce yer alır", () => {
    mount();
    expect(screen.queryByRole("button", { name: "Yeni" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Eski" })).toBeNull();
    const search = screen.getByRole("search");
    const priority = screen.getByRole("button", { name: /^Öncelik/ });
    const filter = screen.getByRole("button", { name: /^Filtrele/ });
    const reposts = screen.getByRole("link", { name: "Yeniden paylaşımlarım" });
    const follows = (first: Element, second: Element) => Boolean(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING);
    expect(follows(search, priority)).toBe(true);
    expect(follows(priority, filter)).toBe(true);
    expect(follows(filter, reposts)).toBe(true);
    // Üçü aynı sağ grubun yan yana kardeşleridir; soldaki ayrı "Öncelik" etiketi yoktur.
    expect(priority.parentElement?.parentElement).toBe(filter.parentElement);
    expect(filter.parentElement).toBe(reposts.parentElement);
    expect(screen.getAllByText("Öncelik")).toHaveLength(1);
    expect(priority.className).toContain("min-h-11");
    expect(filter.className).toContain("min-h-11");
    expect(reposts.className).toContain("min-h-11");
  });

  it("kapalıyken başlık daima 'Öncelik' yazar; seçili sıralama başlıkta görünmez, menüde işaretlenir", () => {
    mount();
    const trigger = screen.getByRole("button", { name: /^Öncelik/ });
    expect(trigger).toHaveTextContent(/^Öncelik$/);
    expect(trigger).not.toHaveTextContent("Yeniden eskiye");
    fireEvent.click(trigger);
    const radios = within(screen.getByRole("menu", { name: "Öncelik" })).getAllByRole("menuitemradio");
    expect(radios.map((radio) => radio.textContent)).toEqual(["Eskiden yeniye", "Yeniden eskiye"]);
    expect(radios[0]).toHaveAttribute("aria-checked", "false");
    expect(radios[1]).toHaveAttribute("aria-checked", "true");
  });

  it("seçim tarihe göre sıralamayı değiştirir, menüyü kapatır; başlık yine 'Öncelik' kalır", () => {
    const onChange = mount();
    fireEvent.click(screen.getByRole("button", { name: /^Öncelik/ }));
    fireEvent.click(screen.getByRole("menuitemradio", { name: "Eskiden yeniye" }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ sort: "OLDEST" }));
    expect(screen.queryByRole("menu")).toBeNull();
    expect(screen.getByRole("button", { name: /^Öncelik/ })).toHaveTextContent(/^Öncelik$/);
  });

  it("menü Escape ile kapanır; zaten seçili seçeneğe basmak sıralamayı değiştirmez", () => {
    const onChange = mount();
    fireEvent.click(screen.getByRole("button", { name: /^Öncelik/ }));
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("menu")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /^Öncelik/ }));
    fireEvent.click(screen.getByRole("menuitemradio", { name: "Yeniden eskiye" }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("Sözlerim yoktur; Yeniden paylaşımlarım bağlantısı korunur", () => {
    mount();
    expect(screen.queryByRole("link", { name: "Sözlerim" })).toBeNull();
    expect(screen.getByRole("link", { name: "Yeniden paylaşımlarım" })).toHaveAttribute("href", "/community/mine/reposts");
  });

  it("yeniden paylaşımlar görünümünde de öncelik sıralaması vardır, arama yoktur", () => {
    mount("reposts");
    expect(screen.getByRole("button", { name: /^Öncelik/ })).toBeInTheDocument();
    expect(screen.queryByRole("search")).toBeNull();
    expect(screen.queryByRole("link", { name: "Yeniden paylaşımlarım" })).toBeNull();
  });
});
