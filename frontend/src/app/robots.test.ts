import { afterEach, describe, expect, it, vi } from "vitest";
import robots from "./robots";
import sitemap from "./sitemap";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("robots ve sitemap", () => {
  it("site kökü yokken her şeyi kapatır ve sitemap boştur", () => {
    vi.stubEnv("NEXT_PUBLIC_SITE_URL", "");
    vi.stubEnv("NODE_ENV", "production");
    expect(robots()).toEqual({ rules: { userAgent: "*", disallow: "/" } });
    expect(sitemap()).toEqual([]);
  });

  it("production dışında site kökü olsa da kapalıdır", () => {
    vi.stubEnv("NEXT_PUBLIC_SITE_URL", "https://ltz.example");
    vi.stubEnv("NODE_ENV", "development");
    expect(robots()).toEqual({ rules: { userAgent: "*", disallow: "/" } });
    expect(sitemap()).toEqual([]);
  });

  it("production + doğrulanmış kökte yalnız herkese açık rotaları listeler", () => {
    vi.stubEnv("NEXT_PUBLIC_SITE_URL", "https://ltz.example");
    vi.stubEnv("NODE_ENV", "production");
    expect(robots()).toEqual({
      rules: { userAgent: "*", allow: "/" },
      sitemap: "https://ltz.example/sitemap.xml"
    });
    expect(sitemap().map((entry) => entry.url)).toEqual([
      "https://ltz.example/",
      "https://ltz.example/about",
      "https://ltz.example/faq"
    ]);
  });
});
