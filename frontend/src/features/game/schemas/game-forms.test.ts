import { describe, expect, it } from "vitest";
import { gameDefaults, gameEdit } from "./game-forms";
import type { AdminGame } from "../types/game";

const current: AdminGame = { gameId: "fixture", status: "ACTIVE", version: 9, manualOverrides: [], game: { game: { gameId: "fixture", title: "Original", gameType: "MAIN_GAME", origin: "EXTERNAL", coverUrl: null, metadataCompleteness: "PARTIAL" }, description: "Original body", releaseDate: null, freshness: { status: "FRESH", fetchedAt: null }, metadata: { earlyAccess: false, developers: ["Studio"], publishers: [], languages: ["tr"], genres: [{ id: "genre", code: "rpg", nameTr: "Rol yapma", nameEn: "Role-playing" }], platforms: [] } } };

describe("Manuel oyun düzenlemesi", () => {
  it("değişmeyen alanları yeniden göndererek gereksiz sağlayıcı kilidi oluşturmaz", () => {
    expect(gameEdit(gameDefaults(current.game), current)).toEqual({ version: 9 });
  });
  it("iyimser sürümü korur; listeleri tekilleştirir ve açık false değerini kaybetmez", () => {
    const form = { ...gameDefaults(current.game), title: "Updated", publishers: "A, A, B", earlyAccess: "true" };
    expect(gameEdit(form, current)).toEqual({ version: 9, title: "Updated", publishers: ["A", "B"], earlyAccess: true });
    expect(gameEdit({ title: "New", gameType: "MAIN_GAME", earlyAccess: "false" })).toMatchObject({ earlyAccess: false });
  });
  it("bilinmeyen oyun türünü ana oyun diye varsaymaz", () => {
    expect(gameDefaults({ ...current.game, game: { ...current.game.game, gameType: undefined } }).gameType).toBe("UNKNOWN");
  });
});
