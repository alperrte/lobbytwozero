import { describe, expect, it } from "vitest";
import en from "./messages/en.json";
import tr from "./messages/tr.json";

function keyPaths(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) =>
    keyPaths(child, prefix ? prefix + "." + key : key)
  );
}

function leaves(value: unknown): string[] {
  if (typeof value === "string") {
    return [value];
  }
  return Object.values(value as object).flatMap(leaves);
}

describe("TR / EN mesajları", () => {
  it("anahtar kümeleri birebir aynıdır", () => {
    expect(keyPaths(tr).sort()).toEqual(keyPaths(en).sort());
  });

  it("boş metin içermez", () => {
    for (const text of [...leaves(tr), ...leaves(en)]) {
      expect(text.trim()).not.toBe("");
    }
  });

  it("dile özgü metinler gerçekten çevrilmiştir", () => {
    expect(tr.Navigation.hardware).toBe("Donanımlar");
    expect(en.Navigation.hardware).toBe("Hardware");
    expect(tr.Navigation.campaigns).not.toBe(en.Navigation.campaigns);
  });
});
