import { afterEach, describe, expect, it, vi } from "vitest";
import { csrfHeader, hardwareApi, queryString } from "./hardware-api";

afterEach(() => { vi.unstubAllGlobals(); document.cookie = "XSRF-TOKEN=; max-age=0; path=/"; });
describe("Hardware HTTP sözleşmeleri", () => {
  it("GET filtrelerini Gateway'e no-store, cookie ve iptal sinyaliyle gönderir", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [] })));
    vi.stubGlobal("fetch", fetch);
    const controller = new AbortController();
    await hardwareApi.products(queryString({ category: "CPU", q: "model & 2", page: 3, brand: "" }), controller.signal);
    const [url, options] = fetch.mock.calls[0];
    expect(url).toContain("/api/v1/hardware/catalog/products?category=CPU&q=model+%26+2&page=3");
    expect(options).toMatchObject({ cache: "no-store", credentials: "include" });
    controller.abort(); expect(options.signal.aborted).toBe(true);
  });
  it.each([
    ["createProfile", () => hardwareApi.createProfile({ name: "Test PC", deviceType: "DESKTOP" }), "/profiles", "POST", { name: "Test PC", deviceType: "DESKTOP" }],
    ["updateProfile", () => hardwareApi.updateProfile("pc", { name: "New", deviceType: "LAPTOP", profileVersion: 7 }), "/profiles/pc", "PUT", { name: "New", deviceType: "LAPTOP", profileVersion: 7 }],
    ["deleteProfile", () => hardwareApi.deleteProfile({ id: "pc", version: 7 } as Parameters<typeof hardwareApi.deleteProfile>[0]), "/profiles/pc?profileVersion=7", "DELETE", undefined],
    ["saveComponent", () => hardwareApi.saveComponent("pc", { profileVersion: 8, category: "GPU", quantity: 1, productId: "product" }, "part"), "/profiles/pc/components/part", "PUT", { profileVersion: 8, category: "GPU", quantity: 1, productId: "product" }],
    ["deleteComponent", () => hardwareApi.deleteComponent("pc", "part", 9), "/profiles/pc/components/part?profileVersion=9", "DELETE", undefined],
    ["compareProducts", () => hardwareApi.compareProducts("a", "b"), "/comparisons/catalog-products", "POST", { leftId: "a", rightId: "b" }],
    ["compareProfiles", () => hardwareApi.compareProfiles("a", "b"), "/comparisons/profiles", "POST", { leftId: "a", rightId: "b" }],
    ["compareComponents", () => hardwareApi.compareComponents("pc", "a", "b"), "/comparisons/components", "POST", { left: { profileId: "pc", componentId: "a" }, right: { profileId: "pc", componentId: "b" } }],
    ["createReview", () => hardwareApi.saveReview("p", { body: "Experience", rating: null }), "/catalog/products/p/reviews", "POST", { body: "Experience", rating: null }],
    ["updateReview", () => hardwareApi.saveReview("p", { body: "Updated", rating: 4, reviewVersion: 3 }, "r"), "/reviews/r", "PUT", { body: "Updated", rating: 4, reviewVersion: 3 }],
    ["reportReview", () => hardwareApi.reportReview("r", { reason: "SPAM" }), "/reviews/r/reports", "POST", { reason: "SPAM" }]
  ] as const)("%s doğru method/body/version/CSRF kullanır", async (_name, action, path, method, body) => {
    document.cookie = "XSRF-TOKEN=test-csrf; path=/";
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch); await action();
    expect(fetch.mock.calls[0][0]).toContain(`/api/v1/hardware${path}`);
    expect(fetch.mock.calls[0][1]).toMatchObject({ method, headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "test-csrf" } });
    expect(fetch.mock.calls[0][1].body).toBe(body === undefined ? undefined : JSON.stringify(body));
  });
  it("CSRF yalnız izinli çerez adlarından okunur; token yoksa uydurulmaz", () => {
    expect(csrfHeader("ltz_access=private; XSRF-TOKEN=hello%3Dworld")).toEqual({ "X-XSRF-TOKEN": "hello=world" });
    expect(csrfHeader("XSRF-TOKEN=%XX")).toEqual({});
    expect(csrfHeader("access_token=secret")).toEqual({});
    expect(csrfHeader("XSRF-TOKEN=dev; __Host-XSRF-TOKEN=prod")).toEqual({ "X-XSRF-TOKEN": "prod" });
  });
  it("hata statüsü ve güvenli ProblemDetail code korunur, iç hata metni gösterilmez", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "DATA_CONFLICT", detail: "Internal SQL text" }), { status: 409 })));
    await expect(hardwareApi.profile("pc", 0)).rejects.toMatchObject({ status: 409, problem: { code: "DATA_CONFLICT" } });
  });
});
