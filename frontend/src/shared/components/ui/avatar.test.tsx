import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Avatar, hashSeed, portraitFor } from "./avatar";

describe("Avatar", () => {
  it("aynı tohum her zaman aynı portreyi verir (sunucu ve istemci aynı çizer)", () => {
    expect(portraitFor("nova:1")).toEqual(portraitFor("nova:1"));
    expect(hashSeed("a")).not.toBe(hashSeed("b"));
  });

  it("farklı tohumlar farklı portre türlerine dağılır", () => {
    const gears = new Set(Array.from({ length: 40 }, (_, index) => portraitFor(`user-${index}`).gear));
    expect(gears.size).toBeGreaterThan(3);
  });

  it("fotoğraf yoksa dekoratif SVG çizer; varsa boş alt metinli fotoğraf gösterir", () => {
    const { container, rerender } = render(<Avatar seed="ece:2" />);
    expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(container.querySelector("img")).toBeNull();

    rerender(<Avatar seed="ece:2" imageUrl="https://example.test/ece.png" />);
    const image = container.querySelector("img");
    expect(image).toHaveAttribute("src", "https://example.test/ece.png");
    expect(image).toHaveAttribute("alt", "");
  });
});
