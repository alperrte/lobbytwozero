import { describe, expect, it } from "vitest";
import {
  activeFilterCount,
  addRole,
  emptyForm,
  filtersFromParams,
  filtersToQuery,
  formFromListing,
  isoToLocal,
  toCreateInput,
  toUpdateInput,
  validateForm,
  type ListingFormState
} from "./listing-form";

const NOW = new Date(2026, 10, 24, 12, 0);
const G = "22222222-2222-4222-8222-222222222222";

function valid(patch: Partial<ListingFormState> = {}): ListingFormState {
  return { ...emptyForm(), gameId: G, gameTitle: "Oyun", platform: "PC", roles: ["Destek"], playersNeeded: 2, description: "Akşam ranked", ...patch };
}

describe("validateForm (sunucu kuralının aynası)", () => {
  it("zorunlu alanlar: oyun, platform, rol, açıklama", () => {
    expect(validateForm(emptyForm(), { mode: "create", now: NOW })).toMatchObject({
      gameId: "gameRequired",
      platform: "platformRequired",
      roles: "rolesRequired",
      description: "descriptionRequired"
    });
    expect(validateForm(valid(), { mode: "create", now: NOW })).toEqual({});
  });

  it("seviye isteğe bağlıdır ama en çok 50 karakter; oyuncu sayısı 1–50", () => {
    expect(validateForm(valid({ skillLevel: "x".repeat(51) }), { mode: "create", now: NOW }).skillLevel).toBe("skillTooLong");
    expect(validateForm(valid({ playersNeeded: 0 }), { mode: "create", now: NOW }).playersNeeded).toBe("playersRange");
    expect(validateForm(valid({ playersNeeded: 51 }), { mode: "create", now: NOW }).playersNeeded).toBe("playersRange");
  });

  it("açıklama karakter (code point) ile sayılır; satır sonu serbest, kontrol karakteri değil", () => {
    expect(validateForm(valid({ description: "🎮".repeat(1000) }), { mode: "create", now: NOW })).toEqual({});
    expect(validateForm(valid({ description: "a".repeat(1001) }), { mode: "create", now: NOW }).description).toBe("descriptionTooLong");
    expect(validateForm(valid({ description: "satır\nsatır" }), { mode: "create", now: NOW })).toEqual({});
    expect(validateForm(valid({ description: "a\u0007b" }), { mode: "create", now: NOW }).description).toBe("invalidCharacters");
  });

  it("oyun zamanı: geçmişte olamaz, ilan bitmeden başlar, bitiş sonra ve en çok 24 saat", () => {
    const check = (patch: Partial<ListingFormState>) => validateForm(valid(patch), { mode: "create", now: NOW });
    expect(check({ playTimeStart: "2026-11-24T10:00" }).playTimeStart).toBe("startInPast");
    expect(check({ playTimeStart: "2026-11-26T10:00", durationHours: 24 }).playTimeStart).toBe("startAfterExpiry");
    expect(check({ playTimeStart: "2026-11-24T20:00", playTimeEnd: "2026-11-24T19:00" }).playTimeEnd).toBe("endBeforeStart");
    expect(check({ playTimeStart: "2026-11-24T20:00", playTimeEnd: "2026-11-25T21:00", durationHours: 48 }).playTimeEnd).toBe("spanTooLong");
    expect(check({ playTimeEnd: "2026-11-24T20:00" }).playTimeEnd).toBe("endNeedsStart");
    expect(check({ playTimeStart: "2026-11-24T20:00", playTimeEnd: "2026-11-24T23:00" })).toEqual({});
  });

  it("düzenlemede oyun istenmez; değişmeyen geçmiş başlangıç kabul edilir", () => {
    const start = new Date(2026, 10, 24, 10, 0).toISOString();
    const form = valid({ gameId: "", playTimeStart: isoToLocal(start) });
    expect(validateForm(form, { mode: "edit", now: NOW, expiresAt: new Date(2026, 10, 25).toISOString(), originalStart: start })).toEqual({});
    expect(validateForm(form, { mode: "edit", now: NOW, expiresAt: new Date(2026, 10, 25).toISOString(), originalStart: null }).playTimeStart).toBe("startInPast");
  });
});

describe("addRole", () => {
  it("boş, uzun, tekrarlanan (büyük/küçük harf farkıyla) ve 5'ten fazla rolü eklemez", () => {
    expect(addRole([], "  Destek ")).toEqual({ roles: ["Destek"] });
    expect(addRole(["Destek"], "destek").issue).toBe("duplicate");
    expect(addRole([], " ").issue).toBe("empty");
    expect(addRole([], "x".repeat(31)).issue).toBe("tooLong");
    expect(addRole(["a", "b", "c", "d", "e"], "f").issue).toBe("tooMany");
  });
});

describe("gövde dönüşümü", () => {
  it("oluşturma: boş seviye ve oyun zamanı gönderilmez, açıklama kırpılır", () => {
    expect(toCreateInput(valid({ description: "  merhaba  ", micRequired: true }))).toEqual({
      gameId: G,
      platform: "PC",
      roles: ["Destek"],
      playersNeeded: 2,
      micRequired: true,
      description: "merhaba",
      durationHours: 24
    });
  });

  it("güncelleme: oyun ve süre yok, sürüm var; zamanlar UTC ISO", () => {
    const body = toUpdateInput(valid({ skillLevel: " Altın ", playTimeStart: "2026-11-24T20:00", playTimeEnd: "2026-11-24T22:00" }), 7);
    expect(body).not.toHaveProperty("gameId");
    expect(body).not.toHaveProperty("durationHours");
    expect(body).toMatchObject({ version: 7, skillLevel: "Altın" });
    expect(body.playTimeStart).toBe(new Date(2026, 10, 24, 20, 0).toISOString());
  });

  it("ilandan form: yerel saat alanına çevrilir", () => {
    const start = new Date(2026, 10, 24, 20, 5).toISOString();
    const form = formFromListing(
      {
        id: "l", ownerId: "o", gameId: G, gameTitle: "Oyun", platform: "XBOX", skillLevel: null, roles: ["A"], playersNeeded: 3,
        playTimeStart: start, playTimeEnd: null, micRequired: true, description: "d", status: "OPEN", expiresAt: start,
        createdAt: start, updatedAt: start, closedAt: null, version: 1
      },
      null
    );
    expect(form).toMatchObject({ platform: "XBOX", skillLevel: "", playTimeStart: "2026-11-24T20:05", micRequired: true });
  });
});

describe("süzgeç ↔ adres", () => {
  it("geçerli değerleri okur, geçersizleri sessizce atlar", () => {
    const filters = filtersFromParams(new URLSearchParams({ q: " ranked ", game: G, platform: "PC", skill: "Altın", role: "Destek", mic: "required", when: "today" }));
    expect(filters).toEqual({ q: "ranked", gameId: G, platform: "PC", skillLevel: "Altın", role: "Destek", micRequired: true, when: "today" });
    expect(filtersFromParams(new URLSearchParams({ game: "x", platform: "SEGA", when: "yesterday", role: "x".repeat(31), mic: "?" }))).toEqual({
      q: undefined, gameId: undefined, platform: undefined, skillLevel: undefined, role: undefined, micRequired: undefined, when: undefined
    });
  });

  it("geri yazar ve aramayı süzgeç sayısına katmaz", () => {
    const filters = { q: "x", platform: "PC" as const, micRequired: false };
    expect(filtersToQuery(filters)).toEqual({ q: "x", platform: "PC", mic: "optional" });
    expect(activeFilterCount(filters)).toBe(2);
  });
});
