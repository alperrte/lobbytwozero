import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { loadReviewResource } from "../services/review-api";
import type { ReviewResource } from "../types/review";
import { useReviewResource } from "./use-review-resource";

vi.mock("../services/review-api", () => ({ loadReviewResource: vi.fn() }));
const load = vi.mocked(loadReviewResource);
const gameA = "0198a3c2-aaaa-7d44-9a55-3f1c2b4d5e66";
const gameB = "0198a3c2-bbbb-7d44-9a55-3f1c2b4d5e66";

type Deferred = { resolve: (value: unknown) => void; reject: (error: unknown) => void; signal: AbortSignal };

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useReviewResource", () => {
  it("kaynak null iken istek atmaz ve yükleniyor demez", () => {
    const { result } = renderHook(() => useReviewResource(null));
    expect(load).not.toHaveBeenCalled();
    expect(result.current).toMatchObject({ data: undefined, error: undefined, loading: false });
  });

  it("veriyi yükler", async () => {
    load.mockResolvedValue({ ok: true });
    const { result } = renderHook(() => useReviewResource<{ ok: boolean }>({ kind: "statistics", gameId: gameA }));
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.data).toEqual({ ok: true }));
    expect(result.current.loading).toBe(false);
  });

  it("hızlı oyun değişiminde eski yanıt yeni ekranı EZMEZ ve eski istek iptal edilir", async () => {
    const pending: Deferred[] = [];
    load.mockImplementation((_resource, signal) => new Promise((resolve, reject) => { pending.push({ resolve, reject, signal }); }));
    const { result, rerender } = renderHook(({ gameId }) => useReviewResource<string>({ kind: "statistics", gameId }), { initialProps: { gameId: gameA } });
    rerender({ gameId: gameB });
    expect(pending).toHaveLength(2);
    expect(pending[0].signal.aborted).toBe(true);
    expect(pending[1].signal.aborted).toBe(false);

    // Yeni isteğin yanıtı önce, eskisininki sonra gelir.
    await act(async () => pending[1].resolve("B verisi"));
    await act(async () => pending[0].resolve("A verisi"));
    expect(result.current.data).toBe("B verisi");
  });

  it("anahtar değişince önceki oyunun verisini yeni oyun için göstermez", async () => {
    load.mockImplementation(async (resource: ReviewResource) => (resource.kind === "statistics" ? `veri-${resource.gameId.slice(9, 13)}` : null));
    const { result, rerender } = renderHook(({ gameId }) => useReviewResource<string>({ kind: "statistics", gameId }), { initialProps: { gameId: gameA } });
    await waitFor(() => expect(result.current.data).toBe("veri-aaaa"));
    load.mockImplementation(() => new Promise(() => undefined));
    rerender({ gameId: gameB });
    expect(result.current.data).toBeUndefined();
    expect(result.current.loading).toBe(true);
  });

  it("yeniden yüklemede önceki veriyi korur (ekran boşalmaz)", async () => {
    load.mockResolvedValueOnce("birinci");
    const { result } = renderHook(() => useReviewResource<string>({ kind: "statistics", gameId: gameA }));
    await waitFor(() => expect(result.current.data).toBe("birinci"));
    let finish: (value: unknown) => void = () => undefined;
    load.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.loading).toBe(true));
    expect(result.current.data).toBe("birinci");
    await act(async () => finish("ikinci"));
    expect(result.current.data).toBe("ikinci");
    expect(result.current.loading).toBe(false);
  });

  it("yeniden yükleme hatasında önceki veriyi korur ve hatayı bildirir", async () => {
    load.mockResolvedValueOnce("birinci");
    const { result } = renderHook(() => useReviewResource<string>({ kind: "statistics", gameId: gameA }));
    await waitFor(() => expect(result.current.data).toBe("birinci"));
    load.mockRejectedValueOnce(new ApiHttpError(503));
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiHttpError));
    expect(result.current.data).toBe("birinci");
  });

  it("hata verir ve 'tekrar dene' ile toparlanır", async () => {
    load.mockRejectedValueOnce(new ApiHttpError(500));
    const { result } = renderHook(() => useReviewResource<string>({ kind: "statistics", gameId: gameA }));
    await waitFor(() => expect(result.current.error).toBeInstanceOf(ApiHttpError));
    load.mockResolvedValueOnce("toparlandı");
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.data).toBe("toparlandı"));
    expect(result.current.error).toBeUndefined();
  });

  it("bileşen kapanınca isteği iptal eder ve sonradan durum yazmaz", async () => {
    let captured: AbortSignal | undefined;
    let finish: (value: unknown) => void = () => undefined;
    load.mockImplementation((_resource, signal) => new Promise((resolve) => { captured = signal; finish = resolve; }));
    const { unmount } = renderHook(() => useReviewResource<string>({ kind: "statistics", gameId: gameA }));
    unmount();
    expect(captured?.aborted).toBe(true);
    await expect(Promise.resolve().then(() => finish("geç gelen yanıt"))).resolves.toBeUndefined();
  });

  it("aynı kaynak nesnesi yeniden oluşturulsa bile gereksiz yeniden istek atmaz", async () => {
    load.mockResolvedValue("x");
    const { result, rerender } = renderHook(() => useReviewResource<string>({ kind: "statistics", gameId: gameA }));
    await waitFor(() => expect(result.current.data).toBe("x"));
    rerender();
    rerender();
    expect(load).toHaveBeenCalledTimes(1);
  });
});
