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
  it("facet seçeneklerini kategoriyle ister; kategori yoksa yalnız üretici listesini döndürür", async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ category: "RAM", facets: { casLatency: [{ value: "30", label: "30", count: 2 }] } })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: "b1", name: "Maker", status: "ACTIVE", version: 0 }], page: 0, size: 100, totalElements: 1, totalPages: 1 })));
    vi.stubGlobal("fetch", fetch);
    expect(await hardwareApi.filterOptions("RAM")).toEqual({ casLatency: [{ value: "30", label: "30", count: 2 }] });
    expect(fetch.mock.calls[0][0]).toContain("/api/v1/hardware/catalog/facets?category=RAM");
    expect(await hardwareApi.filterOptions(undefined)).toEqual({ brandId: [{ value: "b1", label: "Maker", count: 0 }] });
    expect(fetch.mock.calls[1][0]).toContain("/api/v1/hardware/catalog/brands?page=0&size=100");
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
  it("CSRF çerezi yoksa yazmadan önce /auth/csrf çağrılır ve yazma isteği belirteçle gider", async () => {
    const fetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith("/api/v1/auth/csrf")) { document.cookie = "XSRF-TOKEN=fresh-csrf; path=/"; return new Response(null, { status: 204 }); }
      return new Response(JSON.stringify({ id: "r1" }), { status: 201 });
    });
    vi.stubGlobal("fetch", fetch);
    await hardwareApi.saveReview("p", { body: "Experience", rating: 4 });
    expect(fetch.mock.calls.map(([url]) => String(url).replace(/^.*\/api\/v1/, ""))).toEqual(["/auth/csrf", "/hardware/catalog/products/p/reviews"]);
    expect(fetch.mock.calls[1][1]).toMatchObject({ method: "POST", headers: { "X-XSRF-TOKEN": "fresh-csrf" } });
  });
  it("CSRF_TOKEN_INVALID (403) dönerse çerez yenilenir ve istek bir kez tekrarlanır", async () => {
    document.cookie = "XSRF-TOKEN=stale; path=/";
    let writes = 0;
    const fetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith("/api/v1/auth/csrf")) { document.cookie = "XSRF-TOKEN=renewed; path=/"; return new Response(null, { status: 204 }); }
      writes += 1;
      return writes === 1 ? new Response(JSON.stringify({ code: "CSRF_TOKEN_INVALID" }), { status: 403 }) : new Response(JSON.stringify({ id: "r1" }), { status: 201 });
    });
    vi.stubGlobal("fetch", fetch);
    await expect(hardwareApi.saveReview("p", { body: "Experience", rating: 5 })).resolves.toEqual({ id: "r1" });
    expect(writes).toBe(2);
    expect(fetch.mock.calls.at(-1)?.[1]).toMatchObject({ headers: { "X-XSRF-TOKEN": "renewed" } });
  });
  it("başka 403 nedenlerinde istek tekrarlanmaz ve hata olduğu gibi döner", async () => {
    document.cookie = "XSRF-TOKEN=ok; path=/";
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "ACCESS_DENIED" }), { status: 403 }));
    vi.stubGlobal("fetch", fetch);
    await expect(hardwareApi.saveReview("p", { body: "Experience", rating: 5 })).rejects.toMatchObject({ status: 403, code: "ACCESS_DENIED" });
    expect(fetch).toHaveBeenCalledTimes(1);
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

describe("allBrands", () => {
  it("tüm marka sayfalarını birleştirir ve sayfa sayısını beşle sınırlar", async () => {
    const page = (index: number, totalPages: number) => new Response(JSON.stringify({ items: [{ id: `b${index}`, name: `Marka ${index}`, status: "ACTIVE", version: 0 }], page: index, size: 100, totalPages, totalElements: totalPages }));
    const fetch = vi.fn().mockImplementation((url: string) => Promise.resolve(page(Number(new URL(url, "http://x").searchParams.get("page")), 9)));
    vi.stubGlobal("fetch", fetch);
    const brands = await hardwareApi.allBrands();
    expect(brands.map((brand) => brand.id)).toEqual(["b0", "b1", "b2", "b3", "b4"]);
    expect(fetch).toHaveBeenCalledTimes(5);
    expect(fetch.mock.calls[0][0]).toContain("/catalog/brands?page=0&size=100");
  });
  it("tek sayfada ek istek atmaz", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [], page: 0, size: 100, totalPages: 1, totalElements: 0 })));
    vi.stubGlobal("fetch", fetch);
    await hardwareApi.allBrands();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
