import { cleanup, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import type { GameSession } from "../types/game";
import { GameSectionNav, staffSections, type GameSection } from "./game-section-nav";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string | { pathname: string; query?: Record<string, string> }; children: React.ReactNode } & Record<string, unknown>) => <a {...rest} href={typeof href === "string" ? href : `${href.pathname}${href.query ? "?" + new URLSearchParams(href.query).toString() : ""}`}>{children}</a>
}));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn() }));
const load = vi.mocked(loadGameResource);
const accountId = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const t = messages.Game;
function mount(roles: string[] | null | "load", current: GameSection | null = "all") {
  const session: GameSession | null | undefined = roles === "load" ? undefined : roles === null ? null : { accountId, roles };
  return render(<NextIntlClientProvider locale="tr" messages={messages}><GameSectionNav current={current} session={session} /></NextIntlClientProvider>);
}
const hrefs = () => within(screen.getByRole("navigation", { name: t.catalogue })).getAllByRole("link").map((link) => [link.textContent, link.getAttribute("href")]);
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe("Game bölüm gezintisi (rol duyarlı)", () => {
  it("misafir yalnız herkese açık bölümleri görür", () => {
    mount(null);
    expect(hrefs()).toEqual([[t.allGames, "/games"], [t.editorsPicks, "/games?tab=picks"]]);
  });
  it("PLAYER geri bildirimlerim bölümünü görür; yönetim bağlantısı görmez", () => {
    mount(["PLAYER"]);
    expect(hrefs()).toEqual([[t.allGames, "/games"], [t.editorsPicks, "/games?tab=picks"], [t.myFeedback, "/games?tab=feedback"]]);
  });
  it("GAME_DEVELOPER yalnız geliştirici alanını görür (MODERATOR/ADMIN alanlarını görmez)", () => {
    mount(["PLAYER", "GAME_DEVELOPER"]);
    expect(hrefs().slice(3)).toEqual([[t.workspace.developer, "/games/manage?section=developer"]]);
  });
  it("MODERATOR yalnız moderasyonu görür; geliştirici ve admin yetkisi vermez", () => {
    mount(["MODERATOR"]);
    expect(hrefs().slice(3)).toEqual([[t.workspace.moderation, "/games/manage?section=moderation"]]);
  });
  it("ADMIN yönetim ve moderasyonu görür; geliştirici alanını rolü yoksa görmez", () => {
    mount(["ADMIN"]);
    expect(hrefs().slice(3)).toEqual([[t.workspace.admin, "/games/manage?section=admin"], [t.workspace.moderation, "/games/manage?section=moderation"]]);
  });
  it("etkin bölüm aria-current=page taşır", () => {
    mount(["ADMIN"], "admin");
    expect(screen.getByRole("link", { name: t.workspace.admin })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: t.allGames })).not.toHaveAttribute("aria-current");
  });
  it("oturum verilmezse kendi oturumunu tek istekle yükler", async () => {
    load.mockResolvedValue({ accountId, roles: ["GAME_DEVELOPER"] });
    mount("load");
    expect(await screen.findByRole("link", { name: t.workspace.developer })).toBeVisible();
    expect(load).toHaveBeenCalledTimes(1);
  });
  it("rol eşlemesi sıralıdır ve bilinmeyen rolleri yok sayar", () => {
    expect(staffSections(["ADMIN", "GAME_DEVELOPER", "CONTENT_CREATOR"])).toEqual(["developer", "admin", "moderation"]);
    expect(staffSections(["PLAYER"])).toEqual([]);
  });
});
