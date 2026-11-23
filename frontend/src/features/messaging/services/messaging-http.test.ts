import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError } from "@/shared/lib/api/client";

const apiRequest = vi.fn();
const refreshSession = vi.fn();
let csrf: string | null = "token-1";

vi.mock("@/shared/lib/api/client", async (original) => ({
  ...(await original<typeof import("@/shared/lib/api/client")>()),
  apiRequest: (...args: unknown[]) => apiRequest(...args),
  refreshSession: () => refreshSession(),
  apiUrl: (path: string) => `http://gw${path}`
}));
vi.mock("@/shared/lib/session/csrf", () => ({
  CSRF_HEADER: "X-XSRF-TOKEN",
  CSRF_PATH: "/api/v1/auth/csrf",
  readCsrfCookie: () => csrf
}));

const { messagingRequest, uploadRequest } = await import("./messaging-http");

beforeEach(() => {
  apiRequest.mockReset();
  refreshSession.mockReset();
  csrf = "token-1";
});

describe("messagingRequest", () => {
  it("okumayı CSRF başlığı olmadan, önbelleksiz gönderir", async () => {
    apiRequest.mockResolvedValue({ items: [] });
    await messagingRequest("/api/v1/messaging/conversations");
    expect(apiRequest).toHaveBeenCalledWith("/api/v1/messaging/conversations", expect.objectContaining({ method: "GET", cache: "no-store" }));
    expect(apiRequest.mock.calls[0][1].headers).toBeUndefined();
  });

  it("durum değiştiren isteğe CSRF başlığı ve JSON gövde ekler", async () => {
    apiRequest.mockResolvedValue({ id: "m" });
    await messagingRequest("/api/v1/messaging/x", { method: "PUT", body: { emoji: "👍" } });
    expect(apiRequest.mock.calls[0][1]).toMatchObject({
      method: "PUT",
      body: JSON.stringify({ emoji: "👍" }),
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "token-1" }
    });
  });

  it("CSRF reddinde çerezi yenileyip bir kez tekrarlar; başka hata tekrarlanmaz", async () => {
    apiRequest
      .mockRejectedValueOnce(new ApiHttpError(403, { code: "CSRF_TOKEN_INVALID" }))
      .mockResolvedValueOnce(undefined) // CSRF yenileme
      .mockResolvedValueOnce({ ok: true });
    await expect(messagingRequest("/api/v1/messaging/x", { method: "POST" })).resolves.toEqual({ ok: true });
    expect(apiRequest).toHaveBeenCalledTimes(3);

    apiRequest.mockReset();
    apiRequest.mockRejectedValue(new ApiHttpError(429, { code: "TOO_MANY_MESSAGES" }));
    await expect(messagingRequest("/api/v1/messaging/x", { method: "POST" })).rejects.toMatchObject({ status: 429 });
    expect(apiRequest).toHaveBeenCalledTimes(1);
  });

  it("CSRF çerezi alınamazsa isteği hiç göndermez", async () => {
    csrf = null;
    apiRequest.mockResolvedValue(undefined);
    await expect(messagingRequest("/api/v1/messaging/x", { method: "DELETE" })).rejects.toMatchObject({ code: "CSRF_TOKEN_INVALID" });
    expect(apiRequest).toHaveBeenCalledTimes(1);
    expect(apiRequest.mock.calls[0][0]).toBe("/api/v1/auth/csrf");
  });
});

type FakeXhr = {
  open: ReturnType<typeof vi.fn>;
  send: ReturnType<typeof vi.fn>;
  abort: ReturnType<typeof vi.fn>;
  setRequestHeader: ReturnType<typeof vi.fn>;
  getResponseHeader: (name: string) => string | null;
  upload: { onprogress: ((event: { lengthComputable: boolean; loaded: number; total: number }) => void) | null };
  onload: (() => void) | null;
  onerror: (() => void) | null;
  onabort: (() => void) | null;
  ontimeout: (() => void) | null;
  status: number;
  response: unknown;
  withCredentials: boolean;
  headers: Record<string, string>;
};

describe("uploadRequest", () => {
  const instances: FakeXhr[] = [];
  let respond: (xhr: FakeXhr) => void;

  beforeEach(() => {
    instances.length = 0;
    respond = (xhr) => {
      xhr.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 });
      xhr.status = 201;
      xhr.response = { id: "a1" };
      xhr.onload?.();
    };
    vi.stubGlobal(
      "XMLHttpRequest",
      vi.fn(function (this: FakeXhr) {
        this.headers = {};
        this.open = vi.fn();
        this.setRequestHeader = vi.fn((name: string, value: string) => {
          this.headers[name] = value;
        });
        this.getResponseHeader = () => null;
        this.upload = { onprogress: null };
        this.abort = vi.fn(() => this.onabort?.());
        this.send = vi.fn(() => queueMicrotask(() => respond(this)));
        instances.push(this);
      })
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it("dosyayı ham gövdeyle, tür ve CSRF başlığıyla, çerezle yükler ve ilerlemeyi bildirir", async () => {
    const file = new Blob(["data"], { type: "image/png" });
    const progress = vi.fn();
    const handle = uploadRequest<{ id: string }>("/api/v1/messaging/conversations/c/attachments", file, progress);
    await expect(handle.promise).resolves.toEqual({ id: "a1" });
    const xhr = instances[0];
    expect(xhr.open).toHaveBeenCalledWith("POST", "http://gw/api/v1/messaging/conversations/c/attachments");
    expect(xhr.withCredentials).toBe(true);
    expect(xhr.headers).toEqual({ "Content-Type": "image/png", "X-XSRF-TOKEN": "token-1" });
    expect(xhr.send).toHaveBeenCalledWith(file);
    expect(progress).toHaveBeenCalledWith(0.5);
  });

  it("401'de oturumu yenileyip bir kez tekrarlar; sunucu hatasını yalnız güvenli kodla taşır", async () => {
    let attempt = 0;
    respond = (xhr) => {
      attempt += 1;
      xhr.status = attempt === 1 ? 401 : 422;
      xhr.response = { code: attempt === 1 ? "UNAUTHENTICATED" : "INVALID_IMAGE", detail: "gizli" };
      xhr.onload?.();
    };
    refreshSession.mockResolvedValue(true);
    const handle = uploadRequest("/api/v1/messaging/x", new Blob(["d"], { type: "image/png" }), vi.fn());
    const error = await handle.promise.catch((cause: unknown) => cause);
    expect(error).toBeInstanceOf(ApiHttpError);
    expect(error).toMatchObject({ status: 422, code: "INVALID_IMAGE" });
    expect(JSON.stringify(error)).not.toContain("gizli");
    expect(instances).toHaveLength(2);
    expect(refreshSession).toHaveBeenCalledTimes(1);
  });

  it("iptal edilince AbortError ile biter", async () => {
    respond = () => undefined;
    const handle = uploadRequest("/api/v1/messaging/x", new Blob(["d"], { type: "image/png" }), vi.fn());
    await vi.waitFor(() => expect(instances).toHaveLength(1));
    handle.abort();
    await expect(handle.promise).rejects.toMatchObject({ name: "AbortError" });
  });
});
