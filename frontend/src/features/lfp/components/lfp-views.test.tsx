import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Listing } from "../types/lfp.types";

const ME = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const OWNER = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const GAME = "22222222-2222-4222-8222-222222222222";

const api = { search: vi.fn(), mine: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), close: vi.fn(), contact: vi.fn() };
vi.mock("../services/lfp-api", () => ({
  lfpApi: api,
  fetchGameCards: vi.fn(async () => ({ items: [] }))
}));

vi.mock("@/features/auth", () => ({ useRequireSession: () => ({ status: "authenticated", me: { accountId: ME } }) }));
vi.mock("@/features/social", () => ({
  fetchProfileCards: async (ids: readonly string[]) =>
    new Map(ids.filter((id) => id === OWNER).map((id) => [id, { userId: id, username: "deniz", displayName: "Deniz", avatarPath: null }])),
  SocialHero: ({ title, action }: { title: string; action?: ReactNode }) => (
    <header>
      <h1>{title}</h1>
      {action}
    </header>
  )
}));
vi.mock("@/features/game/components/game-cover", () => ({
  coverOf: () => null,
  GameCover: ({ children }: { children?: ReactNode }) => <div data-testid="cover">{children}</div>
}));
vi.mock("@/features/game/components/game-picker", () => ({
  GamePicker: ({ onPick }: { onPick: (game: { gameId: string; title: string }) => void }) => (
    <button type="button" onClick={() => onPick({ gameId: GAME, title: "Rift Protocol" })}>
      Test oyununu seç
    </button>
  )
}));

let search = new URLSearchParams();
const replace = vi.fn();
const push = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/takim-bul",
  useSearchParams: () => search,
  useRouter: () => ({ replace, push, prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const { ListingsView, MyListingsView, CreateListingView } = await import("../index");

function renderView(view: ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul" now={new Date("2026-11-24T09:00:00Z")}>
      {view}
    </NextIntlClientProvider>
  );
}

function listing(patch: Partial<Listing> = {}): Listing {
  return {
    id: "11111111-1111-4111-8111-111111111111",
    ownerId: OWNER,
    gameId: GAME,
    gameTitle: "Rift Protocol",
    platform: "PC",
    skillLevel: null,
    roles: ["Destek"],
    playersNeeded: 2,
    playTimeStart: null,
    playTimeEnd: null,
    micRequired: true,
    description: "Akşam ranked için iki oyuncu.",
    status: "OPEN",
    expiresAt: "2026-11-25T09:00:00Z",
    createdAt: "2026-11-24T09:00:00Z",
    updatedAt: "2026-11-24T09:00:00Z",
    closedAt: null,
    version: 0,
    ...patch
  };
}

beforeEach(() => {
  for (const fn of Object.values(api)) fn.mockReset();
  search = new URLSearchParams();
  replace.mockReset();
  push.mockReset();
});

describe("ListingsView", () => {
  it("açık ilanları kart olarak listeler: sahip, oyun, etiketler, mikrofon ve süre", async () => {
    api.search.mockResolvedValue({ items: [listing(), listing({ id: "33333333-3333-4333-8333-333333333333", ownerId: ME, gameTitle: "Ashen Crown", skillLevel: "Altın" })], nextCursor: null });
    renderView(<ListingsView />);
    const card = (await screen.findByRole("heading", { name: "Rift Protocol", level: 3 })).closest("article")!;
    expect(await within(card).findByText(/^Deniz/)).toHaveTextContent("Deniz@deniz");
    expect(within(card).getByText("Seviye belirtilmedi")).toBeInTheDocument();
    expect(within(card).getByText("Mikrofon gerekli")).toBeInTheDocument();
    expect(within(card).getByText("2 oyuncu aranıyor")).toBeInTheDocument();
    expect(within(card).getByRole("button", { name: "Deniz ile mesajlaş" })).toBeInTheDocument();
    const mine = screen.getByRole("heading", { name: "Ashen Crown", level: 3 }).closest("article")!;
    expect(within(mine).getByRole("link", { name: /Düzenle/ })).toHaveAttribute("href", expect.stringContaining("/tr/takim-bul/33333333-3333-4333-8333-333333333333/duzenle"));
    expect(screen.getByRole("heading", { name: "2 aktif ilan" })).toBeInTheDocument();
  });

  it("adres çubuğundaki süzgeçleri istekte kullanır; süzgeç değişince adresi günceller", async () => {
    search = new URLSearchParams({ platform: "XBOX", mic: "required", q: "ranked" });
    api.search.mockResolvedValue({ items: [], nextCursor: null });
    renderView(<ListingsView />);
    await screen.findByText("Eşleşen ilan yok");
    expect(api.search).toHaveBeenCalledWith({ q: "ranked", platform: "XBOX", micRequired: true, gameId: undefined, skillLevel: undefined, role: undefined, when: undefined }, { limit: 12 }, expect.anything());
    fireEvent.click(screen.getByRole("combobox", { name: "Platform" }));
    fireEvent.click(screen.getByRole("option", { name: "PC" }));
    expect(replace).toHaveBeenLastCalledWith("/tr/takim-bul?q=ranked&platform=PC&mic=required", { scroll: false });
    fireEvent.click(screen.getByRole("button", { name: "Temizle" }));
    expect(replace).toHaveBeenLastCalledWith("/tr/takim-bul?q=ranked", { scroll: false });
  });

  it("Mesaj Gönder: iletişim izni alınır ve mesajlar sayfasına gidilir; engelde neden gösterilir", async () => {
    api.search.mockResolvedValue({ items: [listing()], nextCursor: null });
    api.contact.mockResolvedValueOnce({ listingId: listing().id, recipientId: OWNER });
    renderView(<ListingsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Deniz ile mesajlaş" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith(`/tr/mesajlar?to=${OWNER}`));
    expect(api.contact).toHaveBeenCalledWith(listing().id);

    // Yönlendirme sürerken düğme "açılıyor" durumunda kalır; hata durumu ayrı bir açılışta denenir.
    cleanup();
    api.contact.mockRejectedValueOnce(new ApiHttpError(409, { code: "LISTING_NOT_OPEN" }));
    renderView(<ListingsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Deniz ile mesajlaş" }));
    expect(await screen.findByText("İlan kapatılmış ya da süresi dolmuş.")).toBeInTheDocument();
  });

  it("devamı varsa daha fazla ilan imleçle yüklenir; hata yeniden denenebilir", async () => {
    api.search
      .mockResolvedValueOnce({ items: [listing()], nextCursor: "c1" })
      .mockResolvedValueOnce({ items: [listing({ id: "44444444-4444-4444-8444-444444444444", gameTitle: "Iron Tide" })], nextCursor: null });
    renderView(<ListingsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Daha fazla ilan" }));
    expect(await screen.findByRole("heading", { name: "Iron Tide", level: 3 })).toBeInTheDocument();
    expect(api.search.mock.calls[1][1]).toEqual({ limit: 12, cursor: "c1" });
    expect(screen.queryByRole("button", { name: "Daha fazla ilan" })).not.toBeInTheDocument();
  });

  it("liste okunamazsa neden ve tekrar dene gösterir", async () => {
    api.search.mockRejectedValueOnce(new ApiHttpError(503, { code: "BLOCK_CHECK_UNAVAILABLE" })).mockResolvedValueOnce({ items: [], nextCursor: null });
    renderView(<ListingsView />);
    expect(await screen.findByText("Gizlilik denetimi şu an yapılamıyor. Biraz sonra tekrar dene.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));
    expect(await screen.findByText("Şu an açık ilan yok")).toBeInTheDocument();
  });
});

describe("CreateListingView", () => {
  it("eksik alanları gösterir ve ilk hatalı alana odaklanır", async () => {
    renderView(<CreateListingView />);
    fireEvent.click(screen.getByRole("button", { name: /İlanı Yayınla/ }));
    expect(await screen.findByText("Bir oyun seç.")).toBeInTheDocument();
    expect(screen.getByText("Bir platform seç.")).toBeInTheDocument();
    expect(screen.getByText("En az bir rol ekle.")).toBeInTheDocument();
    expect(screen.getByText("Açıklama yaz.")).toBeInTheDocument();
    expect(api.create).not.toHaveBeenCalled();
  });

  it("doldurulan formu sunucu sözleşmesindeki gövdeyle gönderir ve ilanlarıma döner", async () => {
    api.create.mockResolvedValue(listing({ ownerId: ME }));
    renderView(<CreateListingView />);
    fireEvent.click(screen.getByRole("button", { name: "Oyun seç" }));
    fireEvent.click(screen.getByRole("button", { name: "Test oyununu seç" }));
    fireEvent.click(screen.getByRole("radio", { name: "PlayStation" }));
    const roles = screen.getByRole("textbox", { name: /Rol/ });
    fireEvent.change(roles, { target: { value: "Destek" } });
    fireEvent.keyDown(roles, { key: "Enter" });
    fireEvent.click(screen.getByRole("button", { name: "Bir artır" }));
    fireEvent.click(screen.getByRole("radio", { name: "Gerekli" }));
    fireEvent.change(screen.getByRole("textbox", { name: "İlan açıklaması" }), { target: { value: "  Sakin iletişim.  " } });
    // Önizleme canlıdır.
    const preview = screen.getByRole("complementary", { name: "İlan Önizlemesi" });
    expect(within(preview).getByText("Sakin iletişim.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /İlanı Yayınla/ }));
    await waitFor(() => expect(api.create).toHaveBeenCalled());
    expect(api.create.mock.calls[0][0]).toEqual({
      gameId: GAME,
      platform: "PLAYSTATION",
      roles: ["Destek"],
      playersNeeded: 2,
      micRequired: true,
      description: "Sakin iletişim.",
      durationHours: 24
    });
    await waitFor(() => expect(push).toHaveBeenCalledWith("/tr/takim-bul/ilanlarim"));
  });

  it("sunucu hatasını gösterir (açık ilan sınırı)", async () => {
    api.create.mockRejectedValue(new ApiHttpError(409, { code: "OPEN_LISTING_LIMIT" }));
    renderView(<CreateListingView />);
    fireEvent.click(screen.getByRole("button", { name: "Oyun seç" }));
    fireEvent.click(screen.getByRole("button", { name: "Test oyununu seç" }));
    fireEvent.click(screen.getByRole("radio", { name: "PC" }));
    const roles = screen.getByRole("textbox", { name: /Rol/ });
    fireEvent.change(roles, { target: { value: "Nişancı" } });
    fireEvent.change(screen.getByRole("textbox", { name: "İlan açıklaması" }), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: /İlanı Yayınla/ }));
    expect(await screen.findByText("Aynı anda en fazla 5 açık ilanın olabilir. Önce birini kapat.")).toBeInTheDocument();
    // Yazılıp eklenmemiş rol de gönderime katılır.
    expect(api.create.mock.calls[0][0].roles).toEqual(["Nişancı"]);
  });
});

describe("MyListingsView", () => {
  const open = listing({ ownerId: ME });
  const closed = listing({ id: "55555555-5555-4555-8555-555555555555", ownerId: ME, gameTitle: "Iron Tide", status: "CLOSED", closedAt: "2026-11-24T08:00:00Z" });
  const expired = listing({ id: "66666666-6666-4666-8666-666666666666", ownerId: ME, gameTitle: "Neon Drift", status: "EXPIRED" });

  it("durum sayaçları ve sekmeler; kapalı ilanda yalnız detay", async () => {
    api.mine.mockResolvedValue({ items: [open, closed, expired], nextCursor: null });
    renderView(<MyListingsView />);
    const tabs = await screen.findByRole("tablist", { name: "Duruma göre süz" });
    await waitFor(() => expect(within(tabs).getByRole("tab", { name: /Tümü\s*3/ })).toBeInTheDocument());
    fireEvent.click(within(tabs).getByRole("tab", { name: /Kapalı\s*1/ }));
    expect(screen.queryByRole("button", { name: "Rift Protocol ilanını kapat" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Iron Tide ilanının detayı" }));
    expect(await screen.findByRole("dialog", { name: "İlan detayı" })).toBeInTheDocument();
  });

  it("açık ilan onayla kapatılır ve durumu güncellenir", async () => {
    api.mine.mockResolvedValue({ items: [open], nextCursor: null });
    api.close.mockResolvedValue({ ...open, status: "CLOSED", closedAt: "2026-11-24T10:00:00Z" });
    renderView(<MyListingsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Rift Protocol ilanını kapat" }));
    expect(screen.getByText(/aktif listeden kaldırılacak/)).toBeInTheDocument();
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "İlanı Kapat" }));
    await waitFor(() => expect(api.close).toHaveBeenCalledWith(open.id));
    expect(await screen.findByText("Rift Protocol ilanı kapatıldı.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Rift Protocol ilanının detayı" })).toBeInTheDocument();
  });
});
