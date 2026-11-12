import { beforeEach, describe, expect, it, vi } from "vitest";

const userRequest = vi.fn();
vi.mock("./user-http", () => ({ userRequest: (...args: unknown[]) => userRequest(...args) }));

import { completeOnboarding, getOnboarding, toOnboardingState } from "./onboarding-api";

beforeEach(() => userRequest.mockReset());

describe("toOnboardingState", () => {
  it("yalnız açık true tamamlandı sayılır", () => {
    expect(toOnboardingState({ completed: true, version: 4 }).completed).toBe(true);
    expect(toOnboardingState({ completed: "true" }).completed).toBe(false);
    expect(toOnboardingState(null)).toEqual({ completed: false, completedAt: null, missing: [], version: 0 });
  });

  it("bilinmeyen adımları ve bozuk değerleri eler", () => {
    const state = toOnboardingState({ missing: ["FAVORITE_GENRES", "X", 3], completedAt: "bozuk", version: -1 });
    expect(state.missing).toEqual(["FAVORITE_GENRES"]);
    expect(state.completedAt).toBeNull();
    expect(state.version).toBe(0);
  });
});

describe("uçlar", () => {
  it("durumu okur ve tamamlamayı POST ile gönderir", async () => {
    userRequest.mockResolvedValue({ completed: true, version: 2 });
    await getOnboarding();
    const choices = { version: 1, playerTypes: [], favoriteGenres: ["rpg"], favoritePlatforms: ["pc"] };
    const result = await completeOnboarding(choices);
    expect(result.completed).toBe(true);
    expect(userRequest).toHaveBeenLastCalledWith("/api/v1/user/me/onboarding/complete", expect.objectContaining({ method: "POST", json: choices }));
  });
});
