import { beforeEach, describe, expect, it, vi } from "vitest";

const userRequest = vi.fn();
vi.mock("./user-http", () => ({ userRequest: (...args: unknown[]) => userRequest(...args) }));

import {
  getConsents,
  getPreferences,
  getPrivacy,
  saveConsents,
  savePreferences,
  savePrivacy,
  toConsents,
  toPreferences,
  toPrivacySettings
} from "./settings-api";

beforeEach(() => userRequest.mockReset());

describe("toPrivacySettings", () => {
  it("geçerli değerleri aynen okur", () => {
    expect(
      toPrivacySettings({
        friendsList: "FRIENDS",
        library: "PUBLIC",
        hardware: "PRIVATE",
        performance: "FRIENDS",
        guestProfileAccess: "DETAILED",
        version: 3
      })
    ).toEqual({
      friendsList: "FRIENDS",
      library: "PUBLIC",
      hardware: "PRIVATE",
      performance: "FRIENDS",
      guestProfileAccess: "DETAILED",
      version: 3
    });
  });

  it("tanınmayan ya da eksik değeri en kısıtlayıcı seçeneğe çeker", () => {
    const result = toPrivacySettings({ library: "EVERYONE", guestProfileAccess: "FULL", version: -1 });
    expect(result.library).toBe("PRIVATE");
    expect(result.friendsList).toBe("PRIVATE");
    expect(result.guestProfileAccess).toBe("BASIC");
    expect(result.version).toBe(0);
  });

  it("beklenmeyen gövdede çökmez", () => {
    expect(toPrivacySettings(null).hardware).toBe("PRIVATE");
    expect(toPrivacySettings("x").performance).toBe("PRIVATE");
  });
});

describe("toPreferences", () => {
  it("geçerli değerleri okur; bilinmeyeni varsayılana çeker", () => {
    expect(toPreferences({ locale: "en", theme: "DARK", reducedMotion: true, compactLayout: false, version: 2 })).toEqual({
      locale: "en",
      theme: "DARK",
      reducedMotion: true,
      compactLayout: false,
      version: 2
    });
    expect(toPreferences({ locale: "de", theme: "NEON" })).toMatchObject({ locale: "tr", theme: "SYSTEM", version: 0 });
  });
});

describe("toConsents", () => {
  it("rıza yalnız açıkça true ise verilmiş sayılır", () => {
    expect(toConsents({ recommendation: true, recommendationChangedAt: "2026-11-09T10:00:00Z", version: 1 })).toEqual({
      recommendation: true,
      recommendationChangedAt: "2026-11-09T10:00:00Z",
      version: 1
    });
    expect(toConsents({ recommendation: "true" }).recommendation).toBe(false);
    expect(toConsents({}).recommendation).toBe(false);
    expect(toConsents(null).recommendation).toBe(false);
  });

  it("okunamayan tarihi null yapar", () => {
    expect(toConsents({ recommendationChangedAt: "dün" }).recommendationChangedAt).toBeNull();
  });
});

describe("uç çağrıları", () => {
  it("okumalar doğru yolu GET ile çağırır", async () => {
    userRequest.mockResolvedValue({});
    await getPrivacy();
    await getPreferences();
    await getConsents();
    expect(userRequest.mock.calls.map((call) => call[0])).toEqual([
      "/api/v1/user/me/privacy",
      "/api/v1/user/me/preferences",
      "/api/v1/user/me/consents"
    ]);
    for (const call of userRequest.mock.calls) {
      expect(call[1].method).toBeUndefined();
    }
  });

  it("savePrivacy ayarların tamamını sürümle PUT eder ve yanıtı okur", async () => {
    userRequest.mockResolvedValue({ friendsList: "PUBLIC", library: "PUBLIC", hardware: "PUBLIC", performance: "PUBLIC", guestProfileAccess: "BASIC", version: 5 });
    const settings = { friendsList: "PUBLIC", library: "PUBLIC", hardware: "PUBLIC", performance: "PUBLIC", guestProfileAccess: "BASIC", version: 4 } as const;

    const result = await savePrivacy(settings);

    expect(userRequest).toHaveBeenCalledWith("/api/v1/user/me/privacy", expect.objectContaining({ method: "PUT", json: settings }));
    expect(result.version).toBe(5);
  });

  it("savePreferences tercihlerin tamamını geri gönderir", async () => {
    userRequest.mockResolvedValue({ locale: "en", theme: "LIGHT", reducedMotion: true, compactLayout: true, version: 1 });
    const preferences = { locale: "en", theme: "LIGHT", reducedMotion: true, compactLayout: true, version: 0 } as const;

    await savePreferences(preferences);

    expect(userRequest).toHaveBeenCalledWith("/api/v1/user/me/preferences", expect.objectContaining({ method: "PUT", json: preferences }));
  });

  it("saveConsents yalnız sürüm ve rızayı gönderir", async () => {
    userRequest.mockResolvedValue({ recommendation: true, recommendationChangedAt: "2026-11-09T10:00:00Z", version: 0 });

    const result = await saveConsents({ recommendation: true, recommendationChangedAt: "2026-01-01T00:00:00Z", version: 0 });

    expect(userRequest).toHaveBeenCalledWith(
      "/api/v1/user/me/consents",
      expect.objectContaining({ method: "PUT", json: { version: 0, recommendation: true } })
    );
    expect(result.recommendation).toBe(true);
  });
});
