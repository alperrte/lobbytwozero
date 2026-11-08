import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";
import messages from "@/shared/lib/i18n/messages/tr.json";
import { loadGameResource } from "../services/game-api";
import { GameWorkspace } from "./game-workspace";

vi.mock("@/shared/lib/i18n/navigation", () => ({ Link: ({ href, children, ...rest }: { href: string | { pathname: string; query?: Record<string, string> }; children: React.ReactNode } & Record<string, unknown>) => <a {...rest} href={typeof href === "string" ? href : `${href.pathname}${href.query ? "?" + new URLSearchParams(href.query).toString() : ""}`}>{children}</a> }));
let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search), usePathname: () => "/tr/oyunlar/yonetim", useParams: () => ({}), useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn() }) }));
vi.mock("../services/game-api", async (original) => ({ ...await original<typeof import("../services/game-api")>(), loadGameResource: vi.fn(), mutateGame: vi.fn() }));
const load = vi.mocked(loadGameResource);
const accountId = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";
function mount() { render(<NextIntlClientProvider locale="tr" messages={messages}><GameWorkspace /></NextIntlClientProvider>); }
afterEach(() => { vi.resetAllMocks(); search = ""; });

describe("Game rol görünürlüğü", () => {
  it("misafire yönetim formu veya özel kaynak isteği açmaz", async () => {
    load.mockResolvedValue(null); mount();
    await screen.findByText(messages.Game.signInRequired);
    expect(screen.queryByRole("form")).toBeNull();
    expect(load).toHaveBeenCalledTimes(1);
  });
  it("PLAYER rolünü admin veya geliştirici gibi değerlendirmez", async () => {
    load.mockResolvedValue({ accountId, roles: ["PLAYER"] }); mount();
    await screen.findByText(messages.Game.errors["403"]);
    expect(screen.queryByRole("form")).toBeNull();
    expect(load).toHaveBeenCalledTimes(1);
  });
  it("geliştiriciye yalnız yetkili oyun listesini açar", async () => {
    load.mockImplementation(async (resource) => resource.kind === "session" ? { accountId, roles: ["GAME_DEVELOPER"] } : []);
    mount(); await screen.findByText(messages.Game.noManagedGames);
    expect(screen.queryByRole("form", { name: messages.Game.createGame })).toBeNull();
    expect(load.mock.calls.map(([resource]) => resource.kind)).toEqual(["session", "managed"]);
  });
  it("oturum rolü yenilenince admin formlarını kaldırır", async () => {
    load.mockResolvedValueOnce({ accountId, roles: ["ADMIN"] }); mount();
    await screen.findByRole("form", { name: messages.Game.createGame });
    load.mockResolvedValueOnce({ accountId, roles: ["PLAYER"] });
    fireEvent.focus(window);
    await waitFor(() => expect(screen.queryByRole("form", { name: messages.Game.createGame })).toBeNull());
    expect(screen.getByText(messages.Game.errors["403"])).toBeVisible();
  });
  it("pencereye geri dönünce yetki listesini yenilerken geliştirici taslağını korur", async () => {
    const gameId = "0198a3c2-cccc-7d44-9a55-3f1c2b4d5e66";
    load.mockImplementation(async (resource) => {
      if (resource.kind === "session") return { accountId, roles: ["GAME_DEVELOPER"] };
      if (resource.kind === "managed") return [{ gameId, title: "Fixture game" }];
      if (resource.kind === "developer") return { gameId, description: { text: "Initial description", version: 3 }, media: [], updateNotes: [] };
      return { items: [], totalElements: 0 };
    });
    mount();
    const editor = await screen.findByRole("textbox", { name: messages.Game.fields.description });
    fireEvent.change(editor, { target: { value: "Unsaved developer draft" } });
    fireEvent.focus(window);
    await waitFor(() => expect(load.mock.calls.filter(([resource]) => resource.kind === "managed")).toHaveLength(2));
    await waitFor(() => expect(screen.getByRole("textbox", { name: messages.Game.fields.description })).toHaveValue("Unsaved developer draft"));
  });
  it("?section ile seçilen bölüm açılır; ADMIN moderasyon bölümüne derin bağlantıyla gider ve admin formlarını görmez", async () => {
    search = "section=moderation";
    load.mockResolvedValue({ accountId, roles: ["ADMIN"] }); mount();
    expect(await screen.findByRole("heading", { level: 1, name: messages.Game.workspace.moderation })).toBeVisible();
    expect(screen.queryByRole("form", { name: messages.Game.createGame })).toBeNull();
    expect(screen.getByRole("link", { name: messages.Game.workspace.moderation })).toHaveAttribute("aria-current", "page");
  });
  it("yetkisiz bölüm istenirse (GAME_DEVELOPER → section=admin) ilk yetkili bölüme düşer; admin ekranı açılmaz", async () => {
    search = "section=admin";
    load.mockImplementation(async (resource) => resource.kind === "session" ? { accountId, roles: ["GAME_DEVELOPER"] } : []);
    mount();
    expect(await screen.findByRole("heading", { level: 1, name: messages.Game.workspace.developer })).toBeVisible();
    expect(screen.queryByRole("link", { name: messages.Game.workspace.admin })).toBeNull();
    expect(screen.queryByRole("form", { name: messages.Game.createGame })).toBeNull();
  });
  it("gezinti oturumu çalışma alanıyla paylaşır: tek /auth/me isteği", async () => {
    load.mockResolvedValue({ accountId, roles: ["ADMIN"] }); mount();
    await screen.findByRole("heading", { level: 1, name: messages.Game.workspace.admin });
    expect(load.mock.calls.filter(([resource]) => resource.kind === "session")).toHaveLength(1);
  });
});
