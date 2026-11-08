import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { createElement, type ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { CommunityFrame, NewPostLink } from "./community-frame";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  usePathname: () => "/community",
  Link: ({ href, children, ...rest }: { href: unknown; children: ReactNode }) =>
    createElement("a", { href: typeof href === "string" ? href : JSON.stringify(href), ...rest }, children)
}));

function mount() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <CommunityFrame title="Topluluk Akış" description="Açıklama" action={<NewPostLink />}>
        <p>içerik</p>
      </CommunityFrame>
    </NextIntlClientProvider>
  );
}

describe("CommunityFrame (ortak içerik containerı)", () => {
  it("başlık, sekmeler ve içerik aynı ortalı containerdadır (mobilde %96, sm ve üstünde %93)", () => {
    mount();
    const container = screen.getByRole("heading", { level: 1 }).closest("header")?.parentElement as HTMLElement;
    expect(container.className).toContain("mx-auto");
    expect(container.className).toContain("w-[96%]");
    expect(container.className).toContain("sm:w-[93%]");
    expect(container.contains(screen.getByRole("navigation"))).toBe(true);
    expect(container.contains(screen.getByText("içerik"))).toBe(true);
  });

  it("sekme adları: Akış, Topluluk (eski 'Oyun forumları'), Gönderilerim; adresler değişmez", () => {
    mount();
    const links = screen.getAllByRole("link").filter((link) => link.closest("nav"));
    expect(links.map((link) => link.textContent)).toEqual(["Akış", "Topluluk", "Gönderilerim"]);
    expect(links.map((link) => link.getAttribute("href"))).toEqual(["/community", "/community/forums", "/community/mine"]);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Topluluk Akış");
  });

  it("'Gönderi oluştur' yalnız '+' ikonlu 40×40 butondur; erişilebilir adı ve ipucu aynıdır; oluşturma sayfasına gider", () => {
    mount();
    const link = screen.getByRole("link", { name: "Gönderi oluştur" });
    expect(link).toHaveAttribute("href", "/community/new");
    expect(link.className).toContain("size-10");
    expect(link.className).toContain("bg-primary");
    expect(link.textContent).toBe("");
    expect(screen.queryByRole("tooltip")).toBeNull();
    fireEvent.mouseEnter(link);
    expect(screen.getByRole("tooltip")).toHaveTextContent("Gönderi oluştur");
    expect(link).toHaveAttribute("aria-describedby", screen.getByRole("tooltip").id);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("tooltip")).toBeNull();
    fireEvent.focus(link);
    expect(screen.getByRole("tooltip")).toBeInTheDocument();
    fireEvent.blur(link);
    expect(screen.queryByRole("tooltip")).toBeNull();
  });
});
