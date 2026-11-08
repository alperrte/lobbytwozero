import { describe, expect, it } from "vitest";
import { coverOf } from "./game-cover";

describe("coverOf", () => {
  it("kayıtlı kapak varsa onu kullanır", () => {
    expect(coverOf({ coverUrl: "https://shared.fastly.steamstatic.com/a.jpg", externalReferences: [{ provider: "STEAM", externalId: "10" }] })).toBe("https://shared.fastly.steamstatic.com/a.jpg");
  });
  it("kapak yoksa Steam AppID'sinden Steam başlık görselini türetir", () => {
    expect(coverOf({ coverUrl: null, externalReferences: [{ provider: "STEAM", externalId: "1245620" }] }))
      .toBe("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1245620/header.jpg");
  });
  it("Steam referansı yoksa veya AppID rakam değilse görsel uydurmaz", () => {
    expect(coverOf({ coverUrl: null })).toBeNull();
    expect(coverOf({ coverUrl: null, externalReferences: [{ provider: "IGDB", externalId: "55" }] })).toBeNull();
    expect(coverOf({ coverUrl: null, externalReferences: [{ provider: "STEAM", externalId: "12/../x" }] })).toBeNull();
  });
});
