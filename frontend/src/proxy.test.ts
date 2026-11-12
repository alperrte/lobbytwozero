import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { ADMIN_BASE_PATH } from "@/features/admin/config";
import proxy from "./proxy";

describe("locale proxy rewrite regression", () => {
  it("Türkçe dış adresi dahili rotaya rewrite eder", () => {
    const response = proxy(new NextRequest("http://localhost:5001/tr/donanimlar?category=CPU"));
    expect(response.status).toBe(200);
    expect(response.headers.get("x-middleware-rewrite")).toContain("/tr/hardware?category=CPU");
    expect(response.headers.get("location")).toBeNull();
  });
  it("next-intl'in ikinci iç geçişini tekrar kanonik adrese yönlendirmez", () => {
    const response = proxy(new NextRequest("http://localhost:5001/tr/hardware", { headers: { "x-next-intl-locale": "tr" } }));
    expect(response.status).toBe(200);
    expect(response.headers.get("x-middleware-next")).toBe("1");
    expect(response.headers.get("location")).toBeNull();
  });
  it("normal dahili alias istekleri kanonik adrese yönlenir ve query korunur", () => {
    const response = proxy(new NextRequest("http://localhost:5001/tr/hardware?category=GPU"));
    expect(response.status).toBe(308);
    expect(response.headers.get("location")).toBe("http://localhost:5001/tr/donanimlar?category=GPU");
  });
});

describe("yönetim paneli adresi", () => {
  it("dil öneksiz sunulur, yönlendirilmez; önbelleğe ve arama motoruna kapalıdır", () => {
    const response = proxy(new NextRequest(`http://localhost:5001${ADMIN_BASE_PATH}`));
    expect(response.headers.get("x-middleware-next")).toBe("1");
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("x-robots-tag")).toContain("noindex");
  });
});
