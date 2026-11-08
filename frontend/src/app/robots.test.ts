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
      "https://ltz.example/tr",
      "https://ltz.example/en",
      "https://ltz.example/tr/hakkimizda",
      "https://ltz.example/en/about",
      "https://ltz.example/tr/sss",
      "https://ltz.example/en/faq"
    ]);
  });

  it("her sitemap kaydı karşılıklı hreflang alternatifleri ve x-default taşır", () => {
    vi.stubEnv("NEXT_PUBLIC_SITE_URL", "https://ltz.example");
    vi.stubEnv("NODE_ENV", "production");
    const about = sitemap().find((entry) => entry.url === "https://ltz.example/tr/hakkimizda");
    expect(about?.alternates?.languages).toEqual({
      tr: "https://ltz.example/tr/hakkimizda",
      en: "https://ltz.example/en/about",
      "x-default": "https://ltz.example/tr/hakkimizda"
    });
  });
});
