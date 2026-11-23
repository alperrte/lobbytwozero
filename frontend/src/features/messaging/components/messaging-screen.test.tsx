import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { DIRECT_ID, GROUP_ID, ME, OTHER, PEER, conversation, message, summary } from "../services/messaging-mock-data";
import type { StreamHandlers } from "../services/messaging-stream";
import type { Message } from "../types/messaging.types";

const api = {
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  openDirect: vi.fn(),
  history: vi.fn(),
  send: vi.fn(),
  markRead: vi.fn(),
  react: vi.fn(),
  unreact: vi.fn(),
  remove: vi.fn(),
  forward: vi.fn(),
  reads: vi.fn(),
  reactions: vi.fn()
};
vi.mock("../services/messaging-api", () => ({ messagingApi: api }));

let stream: StreamHandlers | null = null;
vi.mock("../services/messaging-stream", () => ({
  openMessagingStream: (handlers: StreamHandlers) => {
    stream = handlers;
    return () => {
      stream = null;
    };
  }
}));

vi.mock("@/features/auth", () => ({
  useRequireSession: () => ({ status: "authenticated", me: { accountId: ME } })
}));

vi.mock("@/features/social", () => ({
  fetchProfileCards: async (ids: readonly string[]) =>
    new Map(
      ids
        .filter((id) => id === PEER || id === OTHER)
        .map((id) => [id, { userId: id, username: id === PEER ? "deniz" : "ece", displayName: id === PEER ? "Deniz" : "Ece", avatarPath: null }])
    ),
  socialApi: { listFriends: vi.fn(async () => ({ items: [{ friendId: PEER, since: "2026-01-01T00:00:00Z" }], nextCursor: null })) }
}));

let search = new URLSearchParams();
const replace = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/mesajlar",
  useSearchParams: () => search,
  useRouter: () => ({ replace, push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

const { MessagingScreen } = await import("./messaging-screen");

function renderScreen() {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul" now={new Date("2026-11-20T12:00:00Z")}>
      <MessagingScreen />
    </NextIntlClientProvider>
  );
}

const lastFromPeer = message({ seq: 2, body: "naber" });

beforeEach(() => {
  for (const fn of Object.values(api)) fn.mockReset();
  search = new URLSearchParams();
  replace.mockReset();
  Element.prototype.scrollTo = vi.fn();
  Element.prototype.scrollIntoView = vi.fn();
  api.listConversations.mockResolvedValue({
    items: [
      summary({ lastMessage: lastFromPeer, unreadCount: 100, lastActivityAt: lastFromPeer.createdAt }),
      summary({ id: GROUP_ID, type: "GROUP", title: "Gece Takımı", peerUserId: null, memberCount: 3, lastActivityAt: "2026-11-19T10:00:00Z" })
    ],
    nextCursor: null
  });
  api.getConversation.mockResolvedValue(conversation());
  api.history.mockResolvedValue({ items: [lastFromPeer, message({ seq: 1, senderId: ME, body: "selam", readStatus: "SENT" })], hasMore: false });
  api.markRead.mockResolvedValue({ conversationId: DIRECT_ID, lastReadSeq: 2, unreadCount: 0 });
});

afterEach(() => {
  stream = null;
});

function openDirect() {
  search = new URLSearchParams({ c: DIRECT_ID });
}

/** Açık sohbet bölgesi (liste satırı da son mesajı gösterdiği için metin sorguları buraya daraltılır). */
async function thread() {
  return screen.findByRole("region", { name: "Deniz" });
}

async function bubble(text: string) {
  return (await within(await thread()).findByText(text)).closest<HTMLElement>("[data-message-id]")!;
}

describe("MessagingScreen", () => {
  it("sohbet listesi: ad, son mesaj, 99+ okunmamış ve grup bilgisi", async () => {
    renderScreen();
    const list = await screen.findByRole("region", { name: "Mesajlar" });
    const row = await within(list).findByRole("button", { name: /Deniz/ });
    expect(row).toHaveTextContent("naber");
    expect(within(row).getByText("99+")).toBeInTheDocument();
    expect(within(list).getByRole("button", { name: /Gece Takımı/ })).toHaveTextContent("3 üye");
    expect(screen.getByText("Bir sohbet seç")).toBeInTheDocument();
  });

  it("sohbet seçimi adres çubuğuna yazılır; arama listeyi süzer", async () => {
    renderScreen();
    fireEvent.click(await screen.findByRole("button", { name: /Gece Takımı/ }));
    expect(replace).toHaveBeenCalledWith(expect.stringContaining(`c=${GROUP_ID}`), expect.anything());
    fireEvent.change(screen.getByRole("searchbox", { name: "Sohbetlerde ara" }), { target: { value: "deniz" } });
    await waitFor(() => expect(screen.queryByRole("button", { name: /Gece Takımı/ })).not.toBeInTheDocument());
  });

  it("açık sohbet geçmişi çizer, okundu işaretler ve okunmamışı sıfırlar", async () => {
    openDirect();
    renderScreen();
    const thread = await screen.findByRole("region", { name: "Deniz" });
    expect(await within(thread).findByText("naber")).toBeInTheDocument();
    expect(within(thread).getByText("Gönderildi")).toBeInTheDocument();
    await waitFor(() => expect(api.markRead).toHaveBeenCalledWith(DIRECT_ID, 2));
    await waitFor(() => expect(screen.queryByText("99+")).not.toBeInTheDocument());
  });

  it("gönderim iyimserdir; başarısızsa aynı clientMessageId ile tekrar denenir", async () => {
    openDirect();
    api.send.mockRejectedValueOnce(new TypeError("ağ"));
    renderScreen();
    await bubble("naber");
    const input = screen.getByRole("textbox", { name: "Mesaj yaz" });
    fireEvent.change(input, { target: { value: "gg wp 🎮" } });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(await within(await thread()).findByText("gg wp 🎮")).toBeInTheDocument();
    expect(input).toHaveValue("");
    expect(await screen.findByText("Bağlantı kurulamadı. İnternetini kontrol et.")).toBeInTheDocument();
    const first = api.send.mock.calls[0][1];
    expect(first).toEqual({ clientMessageId: expect.stringMatching(/^[A-Za-z0-9_-]+$/), body: "gg wp 🎮" });

    const saved = message({ seq: 3, senderId: ME, body: "gg wp 🎮", clientMessageId: first.clientMessageId, readStatus: "SENT" });
    api.send.mockResolvedValueOnce(saved);
    fireEvent.click(screen.getByRole("button", { name: "Tekrar dene" }));
    await waitFor(() => expect(screen.queryByText("Gönderilemedi")).not.toBeInTheDocument());
    expect(api.send.mock.calls[1][1]).toEqual(first);
    expect(within(await thread()).getAllByText("gg wp 🎮")).toHaveLength(1);
  });

  it("canlı akış: gelen mesaj eklenir, read olayı kendi mesajını Okundu yapar", async () => {
    openDirect();
    renderScreen();
    await bubble("naber");
    act(() => stream?.onEvent({ type: "message", data: message({ seq: 3, body: "yeni geldi" }) }));
    expect(await within(await thread()).findByText("yeni geldi")).toBeInTheDocument();
    act(() =>
      stream?.onEvent({ type: "read", data: { conversationId: DIRECT_ID, readerId: PEER, readUpToSeq: 1, readAt: null, allReadUpToSeq: 1 } })
    );
    expect(await screen.findByText("Okundu")).toBeInTheDocument();
  });

  it("menü: tepki, benden sil ve herkesten sil farklı onay metinleriyle", async () => {
    openDirect();
    const mine: Message = message({ seq: 3, senderId: ME, body: "silinecek", readStatus: "SENT", deletableForEveryoneUntil: "2999-01-01T00:00:00Z" });
    api.history.mockResolvedValue({ items: [mine, lastFromPeer], hasMore: false });
    api.react.mockResolvedValue({ summary: [{ emoji: "❤️", count: 1 }], mine: "❤️" });
    api.remove.mockResolvedValue(undefined);
    renderScreen();

    const peerRow = await bubble("naber");
    fireEvent.click(within(peerRow).getByRole("button", { name: "Deniz mesajı için işlemler" }));
    const peerMenu = screen.getByRole("menu", { name: "Mesaj işlemleri" });
    expect(within(peerMenu).queryByRole("menuitem", { name: "Herkesten sil" })).not.toBeInTheDocument();
    fireEvent.click(within(peerMenu).getByRole("menuitemradio", { name: "❤️ ile tepki ver" }));
    await waitFor(() => expect(api.react).toHaveBeenCalledWith(DIRECT_ID, lastFromPeer.id, "❤️"));
    expect(await within(peerRow).findByRole("button", { name: "❤️ 1 tepki" })).toHaveAttribute("aria-pressed", "true");

    const mineRow = await bubble("silinecek");
    fireEvent.click(within(mineRow).getByRole("button", { name: /mesajı için işlemler/ }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Benden sil" }));
    expect(await screen.findByText("Mesaj senden silinsin mi?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));

    fireEvent.click(within(mineRow).getByRole("button", { name: /mesajı için işlemler/ }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Herkesten sil" }));
    expect(await screen.findByText("Mesaj herkesten silinsin mi?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Herkesten Sil" }));
    await waitFor(() => expect(api.remove).toHaveBeenCalledWith(DIRECT_ID, mine.id, "everyone"));
    expect(await within(await thread()).findByText("Bu mesaj silindi")).toBeInTheDocument();
  });

  it("yanıtla: alıntı çubuğu görünür ve gönderim yanıt hedefini taşır", async () => {
    openDirect();
    api.send.mockImplementation(async (_id: string, input: { clientMessageId: string; body: string }) =>
      message({ seq: 9, senderId: ME, body: input.body, clientMessageId: input.clientMessageId })
    );
    renderScreen();
    const row = await bubble("naber");
    fireEvent.click(within(row).getByRole("button", { name: "Deniz mesajı için işlemler" }));
    fireEvent.click(screen.getByRole("menuitem", { name: "Yanıtla" }));
    expect(screen.getByText("Deniz kişisine yanıt")).toBeInTheDocument();
    const input = screen.getByRole("textbox", { name: "Mesaj yaz" });
    fireEvent.change(input, { target: { value: "iyiyim" } });
    fireEvent.click(screen.getByRole("button", { name: "Gönder" }));
    await waitFor(() => expect(api.send).toHaveBeenCalled());
    expect(api.send.mock.calls[0][1]).toMatchObject({ body: "iyiyim", replyToMessageId: lastFromPeer.id });
    expect(screen.queryByText("Deniz kişisine yanıt")).not.toBeInTheDocument();
  });

  it("?to= ile gelinince birebir sohbet açılır ve adres sohbete çevrilir; engelde neden gösterilir", async () => {
    search = new URLSearchParams({ to: PEER });
    api.openDirect.mockResolvedValueOnce(conversation());
    const { unmount } = renderScreen();
    await waitFor(() => expect(api.openDirect).toHaveBeenCalledWith(PEER));
    await waitFor(() => expect(replace).toHaveBeenCalledWith(`/tr/mesajlar?c=${DIRECT_ID}`));
    unmount();

    search = new URLSearchParams({ to: OTHER });
    api.openDirect.mockRejectedValueOnce(new ApiHttpError(403, { code: "MESSAGING_NOT_ALLOWED" }));
    renderScreen();
    expect(await screen.findByText("Bu kişiyle mesajlaşamazsın.")).toBeInTheDocument();
  });
});
