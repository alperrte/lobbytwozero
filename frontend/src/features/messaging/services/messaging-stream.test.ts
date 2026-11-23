import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const refreshSession = vi.fn();
vi.mock("@/shared/lib/api/client", () => ({
  apiUrl: (path: string) => `http://gw${path}`,
  refreshSession: () => refreshSession()
}));

const { openMessagingStream } = await import("./messaging-stream");

class FakeSource {
  readyState = 0;
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, Array<(event: { data: string }) => void>>();
  constructor(public url: string) {}
  addEventListener(type: string, listener: (event: { data: string }) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }
  emit(type: string, data: string) {
    for (const listener of this.listeners.get(type) ?? []) listener({ data });
  }
  fail(closed: boolean) {
    this.readyState = closed ? 2 : 0;
    this.onerror?.();
  }
  close() {
    this.closed = true;
  }
}

let sources: FakeSource[];
const factory = (url: string) => {
  const source = new FakeSource(url);
  sources.push(source);
  return source as unknown as EventSource;
};

beforeEach(() => {
  sources = [];
  refreshSession.mockReset();
  refreshSession.mockResolvedValue(true);
  vi.useFakeTimers();
});

afterEach(() => vi.useRealTimers());

function handlers() {
  return { onEvent: vi.fn(), onStatus: vi.fn(), onResync: vi.fn() };
}

describe("openMessagingStream", () => {
  it("akış ucuna bağlanır, olayları JSON olarak iletir, bozuk olayı yok sayar", () => {
    const h = handlers();
    openMessagingStream(h, factory);
    expect(sources[0].url).toBe("http://gw/api/v1/messaging/stream");
    sources[0].emit("ready", '{"heartbeatSeconds":4}');
    expect(h.onStatus).toHaveBeenLastCalledWith("live");
    sources[0].emit("message", '{"id":"m1"}');
    sources[0].emit("reaction", "{bozuk");
    sources[0].emit("message_hidden", '{"messageId":"m2"}');
    expect(h.onEvent.mock.calls).toEqual([
      [{ type: "message", data: { id: "m1" } }],
      [{ type: "message_hidden", data: { messageId: "m2" } }]
    ]);
    expect(h.onResync).not.toHaveBeenCalled();
  });

  it("geçici kopmada tarayıcının yeniden bağlanmasını bekler; yeniden hazır olunca eşitleme ister", () => {
    const h = handlers();
    openMessagingStream(h, factory);
    sources[0].emit("ready", "{}");
    sources[0].fail(false);
    expect(h.onStatus).toHaveBeenLastCalledWith("reconnecting");
    expect(sources).toHaveLength(1);
    sources[0].emit("ready", "{}");
    expect(h.onResync).toHaveBeenCalledTimes(1);
  });

  it("kalıcı kapanışta (ör. süresi dolan token) oturumu yeniler ve üstel beklemeyle yeniden açar", async () => {
    const h = handlers();
    openMessagingStream(h, factory);
    sources[0].emit("ready", "{}");
    sources[0].fail(true);
    expect(sources[0].closed).toBe(true);
    await vi.advanceTimersByTimeAsync(1000);
    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(sources).toHaveLength(2);
    sources[1].fail(true);
    await vi.advanceTimersByTimeAsync(1999);
    expect(sources).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(sources).toHaveLength(3);
    sources[2].fail(true);
    expect(h.onStatus).toHaveBeenLastCalledWith("offline");
    await vi.advanceTimersByTimeAsync(4000);
    sources[3].emit("ready", "{}");
    expect(h.onStatus).toHaveBeenLastCalledWith("live");
    expect(h.onResync).toHaveBeenCalledTimes(1);
  });

  it("kapatılınca akışı kapatır ve bekleyen yeniden bağlanmayı iptal eder", async () => {
    const h = handlers();
    const stop = openMessagingStream(h, factory);
    sources[0].fail(true);
    stop();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(sources).toHaveLength(1);
  });
});
