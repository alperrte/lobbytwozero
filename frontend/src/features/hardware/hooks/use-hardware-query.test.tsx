import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useHardwareQuery } from "./use-hardware-query";

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

describe("useHardwareQuery keepPrevious", () => {
  it("anahtar değişirken önceki veriyi loading ile korur, yenisi gelince değiştirir", async () => {
    const second = deferred<string>();
    const { result, rerender } = renderHook(({ key }) => useHardwareQuery(key, () => key === "a" ? Promise.resolve("A") : second.promise, true, "g"), { initialProps: { key: "a" } });
    await waitFor(() => expect(result.current.data).toBe("A"));
    rerender({ key: "b" });
    expect(result.current.data).toBe("A");
    expect(result.current.loading).toBe(true);
    await act(async () => { second.resolve("B"); });
    await waitFor(() => expect(result.current.data).toBe("B"));
    expect(result.current.loading).toBe(false);
  });

  it("grup değişince önceki veri korunmaz", async () => {
    const second = deferred<string>();
    const { result, rerender } = renderHook(({ key, group }) => useHardwareQuery(key, () => key === "a" ? Promise.resolve("A") : second.promise, true, group), { initialProps: { key: "a", group: "cpu" } });
    await waitFor(() => expect(result.current.data).toBe("A"));
    rerender({ key: "b", group: "gpu" });
    expect(result.current.data).toBeUndefined();
    expect(result.current.loading).toBe(true);
    await act(async () => { second.resolve("B"); });
  });

  it("keepPrevious kapalıyken anahtar değişince veri hemen boşalır", async () => {
    const second = deferred<string>();
    const { result, rerender } = renderHook(({ key }) => useHardwareQuery(key, () => key === "a" ? Promise.resolve("A") : second.promise), { initialProps: { key: "a" } });
    await waitFor(() => expect(result.current.data).toBe("A"));
    rerender({ key: "b" });
    expect(result.current.data).toBeUndefined();
    expect(result.current.loading).toBe(true);
    await act(async () => { second.resolve("B"); });
  });
});
