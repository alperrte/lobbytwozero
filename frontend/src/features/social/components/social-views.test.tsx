import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { resetSocialMock, socialMockApi } from "../services/social-mock-store";
import { BlockedView } from "./blocked-view";
import { FriendsView } from "./friends-view";
import { RequestsView } from "./requests-view";

vi.mock("../services/social-api", async () => ({
  socialApi: (await import("../services/social-mock-store")).socialMockApi
}));
vi.mock("../services/user-profiles", async () => {
  const { mockProfileCards } = await import("../services/social-mock-data");
  return { fetchProfileCards: async (ids: readonly string[]) => mockProfileCards(ids) };
});

vi.mock("next/navigation", () => ({
  usePathname: () => "/tr/arkadaslar",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  redirect: vi.fn(),
  permanentRedirect: vi.fn()
}));

function renderView(view: ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul" now={new Date()}>
      {view}
    </NextIntlClientProvider>
  );
}

function tab(name: RegExp) {
  return within(screen.getByRole("navigation", { name: "Arkadaşlık bölümleri" })).getByRole("link", { name });
}

beforeEach(() => {
  resetSocialMock({ latencyMs: 0 });
});

describe("Arkadaşlar sayfası", () => {
  it("arkadaş kartlarını, sekme sayılarını ve etkin sekmeyi gösterir", async () => {
    renderView(<FriendsView />);
    expect(await screen.findByRole("heading", { name: "Deniz", level: 3 })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Arkadaşlarım", level: 1 })).toBeInTheDocument();
    await waitFor(() => expect(tab(/Arkadaşlar\s*24/)).toHaveAttribute("aria-current", "page"));
    expect(tab(/Gelen İstekler\s*3/)).not.toHaveAttribute("aria-current");
    expect(screen.getByText("12 / 24 arkadaş gösteriliyor")).toBeInTheDocument();
  });

  it("arama ve durum süzgeci yüklenen listeyi daraltır", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });

    fireEvent.change(screen.getByRole("searchbox", { name: "Arkadaşlarında ara" }), { target: { value: "mert" } });
    expect(screen.getAllByRole("article")).toHaveLength(1);
    expect(screen.getByRole("heading", { name: "Mert", level: 3 })).toBeInTheDocument();

    fireEvent.change(screen.getByRole("searchbox", { name: "Arkadaşlarında ara" }), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: /Oyunda/ }));
    expect(screen.getByRole("button", { name: /Oyunda/ })).toHaveAttribute("aria-pressed", "true");
    for (const card of screen.getAllByRole("article")) {
      expect(within(card).getByText("Oyunda")).toBeInTheDocument();
    }
  });

  it("yüklenmemiş arkadaş varken arama/süzgeç sonucunun eksik olabileceğini söyler", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });
    const note = /yalnız şu ana kadar yüklenen 12 arkadaşına uygulanıyor/;
    expect(screen.queryByText(note)).not.toBeInTheDocument();

    const search = screen.getByRole("searchbox", { name: "Arkadaşlarında ara" });
    fireEvent.change(search, { target: { value: "mert" } });
    expect(screen.getByText(note)).toBeInTheDocument();

    fireEvent.change(search, { target: { value: "" } });
    expect(screen.queryByText(note)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Çevrimiçi/ }));
    expect(screen.getByText(note)).toBeInTheDocument();
  });

  it("yeni davet kodu üretimi iptalden sonra düşerse geçersiz kodu ekranda bırakmaz", async () => {
    renderView(<FriendsView />);
    expect(await screen.findByText("7KX4M-9QBTR")).toBeInTheDocument();
    const create = vi
      .spyOn(socialMockApi, "createInviteCode")
      .mockRejectedValueOnce(new ApiHttpError(503, {}));

    fireEvent.click(screen.getByRole("button", { name: "Yeni kod oluştur" }));
    const dialog = screen.getByRole("dialog", { name: "Davet kodu yenilensin mi?" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Yeni kod oluştur" }));

    expect(await within(dialog).findByText("Sosyal servis şu an yanıt vermiyor. Biraz sonra tekrar dene.")).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText("7KX4M-9QBTR")).not.toBeInTheDocument());
    expect(screen.getByText("Henüz etkin bir davet kodun yok.")).toBeInTheDocument();
    create.mockRestore();
  });

  it("menüden arkadaşlıktan çıkarma onay ister, sonra kartı kaldırır ve duyurur", async () => {
    renderView(<FriendsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Deniz için diğer işlemler" }));
    const menu = screen.getByRole("menu");
    expect(within(menu).getByRole("menuitem", { name: /Profili Gör/ })).toHaveAttribute("aria-disabled", "true");
    fireEvent.click(within(menu).getByRole("menuitem", { name: "Arkadaşlıktan Çıkar" }));

    const dialog = screen.getByRole("dialog", { name: "Arkadaşlıktan çıkarılsın mı?" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Arkadaşlıktan Çıkar" }));

    await waitFor(() => expect(screen.queryByRole("heading", { name: "Deniz", level: 3 })).not.toBeInTheDocument());
    expect(await screen.findByText("Deniz arkadaş listenden çıkarıldı.")).toBeInTheDocument();
    await waitFor(() => expect(tab(/Arkadaşlar\s*23/)).toBeInTheDocument());
  });

  it("'Mesaj Gönder' messaging yayınlanana kadar pasiftir ve nedenini söyler", async () => {
    renderView(<FriendsView />);
    const card = (await screen.findByRole("heading", { name: "Deniz", level: 3 })).closest("article")!;
    const message = within(card).getByRole("button", { name: "Mesaj Gönder" });
    expect(message).toHaveAttribute("aria-disabled", "true");
    expect(message).toHaveAccessibleDescription("Mesajlaşma yakında açılacak");
  });

  it("davet koduyla ekleme biçim ve sunucu hatasını alana bağlar, başarıda alanı temizler", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });
    const input = screen.getByRole("textbox", { name: "Arkadaşının davet kodu" });
    const submit = screen.getByRole("button", { name: "İstek Gönder" });

    fireEvent.change(input, { target: { value: "abc" } });
    fireEvent.click(submit);
    expect(await screen.findByText("Davet kodu 10 karakter olmalı.")).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "true");

    fireEvent.change(input, { target: { value: "DENZ1-PKYS2" } });
    fireEvent.click(submit);
    expect(await screen.findByText("Bu kullanıcıyla zaten arkadaşsınız.")).toBeInTheDocument();
    expect(input).toHaveValue("DENZ1-PKYS2");

    fireEvent.change(input, { target: { value: "sarp7-q2m4k" } });
    fireEvent.click(submit);
    await waitFor(() => expect(input).toHaveValue(""));
    expect(await screen.findByText(/Arkadaşlık isteği gönderildi/)).toBeInTheDocument();
    await waitFor(() => expect(tab(/Gönderilenler\s*3/)).toBeInTheDocument());
  });

  it("başarıdan sonra gelen hata eski 'gönderildi' bandını kaldırır; iki çelişkili mesaj aynı anda görünmez", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });
    const input = screen.getByRole("textbox", { name: "Arkadaşının davet kodu" });
    const submit = screen.getByRole("button", { name: "İstek Gönder" });

    fireEvent.change(input, { target: { value: "sarp7-q2m4k" } });
    fireEvent.click(submit);
    expect(await screen.findByText(/Arkadaşlık isteği gönderildi/)).toBeInTheDocument();

    fireEvent.change(input, { target: { value: "ZZZZZ-ZZZZZ" } });
    fireEvent.click(submit);
    expect(await screen.findByText("Bu davet kodu bulunamadı ya da artık kullanılamıyor.")).toBeInTheDocument();
    expect(screen.queryByText(/Arkadaşlık isteği gönderildi/)).not.toBeInTheDocument();
  });

  it("biçim hatası da eski 'gönderildi' bandını kaldırır", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });
    const input = screen.getByRole("textbox", { name: "Arkadaşının davet kodu" });
    const submit = screen.getByRole("button", { name: "İstek Gönder" });

    fireEvent.change(input, { target: { value: "sarp7-q2m4k" } });
    fireEvent.click(submit);
    expect(await screen.findByText(/Arkadaşlık isteği gönderildi/)).toBeInTheDocument();

    fireEvent.change(input, { target: { value: "abc" } });
    fireEvent.click(submit);
    expect(await screen.findByText("Davet kodu 10 karakter olmalı.")).toBeInTheDocument();
    expect(screen.queryByText(/Arkadaşlık isteği gönderildi/)).not.toBeInTheDocument();
  });
});

describe("İstekler sayfası", () => {
  it("gelen isteği kabul edince listeden düşer ve arkadaş sayısı artar", async () => {
    renderView(<RequestsView />);
    const accept = await screen.findByRole("button", { name: "Nova isteğini kabul et" });
    expect(screen.getByRole("list", { name: "Gönderilen arkadaşlık istekleri" })).toBeInTheDocument();
    fireEvent.click(accept);
    await waitFor(() => expect(screen.queryByRole("button", { name: "Nova isteğini kabul et" })).not.toBeInTheDocument());
    expect(await screen.findByText("Nova artık arkadaşın.")).toBeInTheDocument();
    await waitFor(() => expect(tab(/Arkadaşlar\s*25/)).toBeInTheDocument());
    expect(tab(/Gelen İstekler\s*2/)).toBeInTheDocument();
  });

  it("gönderilen isteği iptal eder", async () => {
    renderView(<RequestsView />);
    fireEvent.click(await screen.findByRole("button", { name: "Barış kullanıcısına gönderdiğin isteği iptal et" }));
    expect(await screen.findByText("Barış kullanıcısına gönderilen istek iptal edildi.")).toBeInTheDocument();
  });
});

describe("Engellenenler sayfası", () => {
  it("engeli onayla kaldırır; Vazgeç hiçbir şey yapmaz", async () => {
    renderView(<BlockedView />);
    const unblock = await screen.findByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" });

    fireEvent.click(unblock);
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Vazgeç" }));
    expect(screen.getByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Engeli Kaldır" }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" })).not.toBeInTheDocument());
    expect(await screen.findByText("Engellenen oyuncu #00000050 kullanıcısının engeli kaldırıldı.")).toBeInTheDocument();
  });

  it("ad gelmediği için arama sunmaz; önizleme verisi göstermediği için rozet taşımaz", async () => {
    renderView(<BlockedView />);
    await screen.findByRole("button", { name: "Engeli kaldır: Engellenen oyuncu #00000050" });
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
    expect(screen.queryByText("Önizleme verisi")).not.toBeInTheDocument();
  });

  it("önizleme rozeti önizleme verisi gösteren sayfalarda kalır", async () => {
    renderView(<FriendsView />);
    await screen.findByRole("heading", { name: "Deniz", level: 3 });
    expect(screen.getAllByText("Önizleme verisi").length).toBeGreaterThan(0);
  });
});
