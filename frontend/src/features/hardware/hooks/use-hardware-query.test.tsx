import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";
import { useHardwareMutation, useHardwareQuery } from "./use-hardware-query";
describe("Hardware veri yaşam döngüsü", () => {
  it("eski istek iptal edilir ve geç gelen yanıt yeni seçimi ezmez", async () => {
    let firstResolve!: (value: string) => void;
    let firstSignal!: AbortSignal;
    const first = new Promise<string>((resolve) => { firstResolve = resolve; });
    const { result, rerender } = renderHook(({ key }) => useHardwareQuery(key, (signal) => { if (key === "a") { firstSignal = signal; return first; } return Promise.resolve("new"); }), { initialProps: { key: "a" } });
    rerender({ key: "b" });
    await waitFor(() => expect(result.current.data).toBe("new"));
    expect(firstSignal.aborted).toBe(true);
    await act(async () => firstResolve("stale"));
    expect(result.current.data).toBe("new");
  });
  it("reload sırasında eski özel veri görünmez, 401 boş sonuç sayılmaz", async () => {
    const loader = vi.fn().mockResolvedValueOnce({ secret: "owner" }).mockRejectedValueOnce(new ApiHttpError(401));
    const { result } = renderHook(() => useHardwareQuery("private", loader));
    await waitFor(() => expect(result.current.data).toEqual({ secret: "owner" }));
    act(() => result.current.reload()); expect(result.current.data).toBeUndefined();
    await waitFor(() => expect(result.current.error).toMatchObject({ status: 401 }));
  });
  it("aynı anda iki mutation göndermez; başarı yalnız sunucu sonucu sonrası oluşur", async () => {
    let finish!: (value: string) => void;
    const action = vi.fn(() => new Promise<string>((resolve) => { finish = resolve; }));
    const saved = vi.fn();
    const { result } = renderHook(() => useHardwareMutation(saved));
    let request!: Promise<unknown>;
    act(() => { request = result.current.run(action); void result.current.run(action); });
    expect(action).toHaveBeenCalledTimes(1); expect(result.current.success).toBe(false);
    await act(async () => { finish("ok"); await request; });
    expect(saved).toHaveBeenCalledTimes(1); expect(result.current.success).toBe(true);
  });
  it("409 ve sonucu belirsiz ağ hatası güncel durumu sorgulatır", async () => {
    const reload = vi.fn();
    const { result } = renderHook(() => useHardwareMutation(reload));
    await act(async () => { await result.current.run(() => Promise.reject(new ApiHttpError(409))); });
    expect(reload).toHaveBeenCalledTimes(1); expect(result.current.success).toBe(false);
    await act(async () => { await result.current.run(() => Promise.reject(new TypeError("network"))); });
    expect(reload).toHaveBeenCalledTimes(2);
  });
});
