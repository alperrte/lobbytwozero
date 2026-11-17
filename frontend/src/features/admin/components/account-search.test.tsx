import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccountSearch } from "./account-search";

const search = vi.hoisted(() => vi.fn());
vi.mock("@/features/user", () => ({ searchAdminProfiles: search }));

const ID = "11111111-2222-3333-4444-555555555555";
const hit = { userId: ID, username: "kerem", displayName: "Kerem G", accountStatus: "SUSPENDED", memberSince: null };

async function type(value: string) {
  fireEvent.change(screen.getByRole("textbox"), { target: { value } });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(400);
  });
}

describe("AccountSearch", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    search.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("@ işaretini atıp aranır ve sonuç seçilince onPick çağrılır", async () => {
    search.mockResolvedValue([hit]);
    const onPick = vi.fn();
    render(<AccountSearch busy={false} onPick={onPick} />);
    await type("@ker");
    expect(search).toHaveBeenCalledWith("ker", expect.any(AbortSignal));
    fireEvent.click(screen.getByRole("button", { name: /@kerem/ }));
    expect(onPick).toHaveBeenCalledWith(ID);
    expect(screen.getByText("Kerem G")).toBeInTheDocument();
  });

  it("tek karakterde aramaz", async () => {
    render(<AccountSearch busy={false} onPick={() => undefined} />);
    await type("@k");
    expect(search).not.toHaveBeenCalled();
  });

  it("bekleme süresi dolmadan istek atmaz; yazı değişince yalnız sonuncuyu arar", async () => {
    search.mockResolvedValue([]);
    render(<AccountSearch busy={false} onPick={() => undefined} />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "ke" } });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(100);
    });
    expect(search).not.toHaveBeenCalled();
    await type("ker");
    expect(search).toHaveBeenCalledTimes(1);
    expect(search).toHaveBeenCalledWith("ker", expect.any(AbortSignal));
  });

  it("sonuç yoksa bilgi verir", async () => {
    search.mockResolvedValue([]);
    render(<AccountSearch busy={false} onPick={() => undefined} />);
    await type("zzz");
    expect(screen.getByText(/ile başlayan hesap bulunamadı/)).toBeInTheDocument();
  });

  it("hata uyarı olarak gösterilir", async () => {
    search.mockRejectedValue(new Error("boom"));
    render(<AccountSearch busy={false} onPick={() => undefined} />);
    await type("kerem");
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("UUID yazılırsa aranmaz, Kimlikle aç doğrudan seçer", async () => {
    const onPick = vi.fn();
    render(<AccountSearch busy={false} onPick={onPick} />);
    await type(ID);
    expect(search).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Kimlikle aç" }));
    expect(onPick).toHaveBeenCalledWith(ID);
  });

  it("Enter ilk sonucu seçer", async () => {
    search.mockResolvedValue([hit]);
    const onPick = vi.fn();
    render(<AccountSearch busy={false} onPick={onPick} />);
    await type("ker");
    fireEvent.submit(screen.getByRole("search"));
    expect(onPick).toHaveBeenCalledWith(ID);
  });
});
