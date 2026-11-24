import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { recommendationApi } from "../services/recommendation-api";
import { RecommendationScopeContext } from "./recommendation-scope";
import { useItemFeedback } from "./use-item-feedback";

vi.mock("../services/recommendation-api", () => ({ recommendationApi: { getFeedback: vi.fn(), putFeedback: vi.fn() } }));

const api = vi.mocked(recommendationApi);
const consentRequired = vi.fn();
const wrapper = ({ children }: { children: ReactNode }) => <RecommendationScopeContext.Provider value={{ accountId: "acc", consentRequired }}>{children}</RecommendationScopeContext.Provider>;

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  api.getFeedback.mockImplementation(async (_runId, itemId) => ({ itemId, value: null, version: 2 }));
});
afterEach(() => vi.clearAllMocks());

describe("useItemFeedback", () => {
  it("mevcut değerleri okur ve iyimser güncellemeyi sunucu sürümüyle onaylar", async () => {
    const { result } = renderHook(() => useItemFeedback("run", ["a"]), { wrapper });
    await waitFor(() => expect(result.current.entryOf("a").known).toBe(true));
    const put = deferred<{ itemId: string; value: "LIKE"; version: number }>();
    api.putFeedback.mockReturnValue(put.promise);
    act(() => void result.current.submit("a", "LIKE"));
    expect(result.current.entryOf("a").value).toBe("LIKE");
    expect(result.current.pending.a).toBe(true);
    expect(api.putFeedback).toHaveBeenCalledWith("run", "a", "LIKE", 2);
    await act(async () => put.resolve({ itemId: "a", value: "LIKE", version: 3 }));
    expect(result.current.entryOf("a")).toEqual({ value: "LIKE", version: 3, known: true });
    expect(result.current.pending.a).toBe(false);
  });

  it("bekleyen istek varken ikinci tıklama yok sayılır", async () => {
    const { result } = renderHook(() => useItemFeedback("run", ["a"], true), { wrapper });
    const put = deferred<{ itemId: string; value: "LIKE"; version: number }>();
    api.putFeedback.mockReturnValue(put.promise);
    act(() => {
      void result.current.submit("a", "LIKE");
      void result.current.submit("a", "NOT_INTERESTED");
    });
    expect(api.putFeedback).toHaveBeenCalledTimes(1);
    await act(async () => put.resolve({ itemId: "a", value: "LIKE", version: 1 }));
  });

  it("yeni çalışmada okuma isteği atmaz ve sürüm 0 ile gönderir", async () => {
    api.putFeedback.mockResolvedValue({ itemId: "a", value: "NOT_INTERESTED", version: 1 });
    const { result } = renderHook(() => useItemFeedback("run", ["a", "b"], true), { wrapper });
    await act(() => result.current.submit("a", "NOT_INTERESTED"));
    expect(api.getFeedback).not.toHaveBeenCalled();
    expect(api.putFeedback).toHaveBeenCalledWith("run", "a", "NOT_INTERESTED", 0);
  });

  it("aynı değere tekrar basmak istek atmaz", async () => {
    api.putFeedback.mockResolvedValue({ itemId: "a", value: "LIKE", version: 1 });
    const { result } = renderHook(() => useItemFeedback("run", ["a"], true), { wrapper });
    await act(() => result.current.submit("a", "LIKE"));
    await act(() => result.current.submit("a", "LIKE"));
    expect(api.putFeedback).toHaveBeenCalledTimes(1);
  });

  it("hata olursa önceki değere döner ve sorunu bildirir", async () => {
    api.putFeedback.mockRejectedValue(new ApiHttpError(503, { code: "SERVICE_UNAVAILABLE" }));
    const { result } = renderHook(() => useItemFeedback("run", ["a"], true), { wrapper });
    await act(() => result.current.submit("a", "LIKE"));
    expect(result.current.entryOf("a").value).toBeNull();
    expect(result.current.problem).toMatchObject({ itemId: "a", kind: "failed" });
    act(() => result.current.dismissProblem());
    expect(result.current.problem).toBeUndefined();
  });

  it("409 sürüm çatışmasında sunucudaki güncel değeri gösterir", async () => {
    api.putFeedback.mockRejectedValue(new ApiHttpError(409, { code: "FEEDBACK_VERSION_CONFLICT" }));
    api.getFeedback.mockResolvedValue({ itemId: "a", value: "NOT_INTERESTED", version: 5 });
    const { result } = renderHook(() => useItemFeedback("run", ["a"], true), { wrapper });
    await act(() => result.current.submit("a", "LIKE"));
    expect(result.current.entryOf("a")).toEqual({ value: "NOT_INTERESTED", version: 5, known: true });
    expect(result.current.problem).toMatchObject({ itemId: "a", kind: "conflict" });
  });

  it("CONSENT_REQUIRED rıza kapısını yeniden açar", async () => {
    api.putFeedback.mockRejectedValue(new ApiHttpError(403, { code: "CONSENT_REQUIRED" }));
    const { result } = renderHook(() => useItemFeedback("run", ["a"], true), { wrapper });
    await act(() => result.current.submit("a", "LIKE"));
    expect(consentRequired).toHaveBeenCalledTimes(1);
  });

  it("okumaları en fazla 3 eşzamanlı istekle yapar", async () => {
    let active = 0;
    let peak = 0;
    api.getFeedback.mockImplementation(async (_runId, itemId) => {
      active += 1;
      peak = Math.max(peak, active);
      await new Promise((resolve) => setTimeout(resolve, 5));
      active -= 1;
      return { itemId, value: null, version: 0 };
    });
    const ids = ["a", "b", "c", "d", "e", "f", "g"];
    const { result } = renderHook(() => useItemFeedback("run", ids), { wrapper });
    await waitFor(() => expect(ids.every((id) => result.current.entryOf(id).known)).toBe(true));
    expect(api.getFeedback).toHaveBeenCalledTimes(7);
    expect(peak).toBeLessThanOrEqual(3);
  });

  it("kapsam dışında kullanılırsa açık hata verir", () => {
    expect(() => renderHook(() => useItemFeedback("run", []))).toThrow(/ConsentGate/);
  });
});
