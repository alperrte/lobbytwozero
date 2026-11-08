import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TwistText } from "./twist-text";

describe("TwistText", () => {
  it("ekran okuyucuya metni bir kez ve bütün verir; harfleri ayıran katman gizlidir", () => {
    const { container } = render(<TwistText text="Lobine hoş geldin" animate />);
    expect(screen.getAllByText("Lobine hoş geldin")).toHaveLength(1);
    const hidden = container.querySelector('[aria-hidden="true"]');
    expect(hidden).not.toBeNull();
    const letters = hidden!.querySelectorAll(".auth-twist-char");
    expect(letters).toHaveLength("Lobinehoşgeldin".length);
    expect(Array.from(letters).map((node) => node.textContent).join("")).toBe("Lobinehoşgeldin");
  });

  it("her harfe artan sıra ve belirleyici açı verilir", () => {
    const { container } = render(<TwistText text="Hoş geldin" animate />);
    const letters = Array.from(container.querySelectorAll<HTMLElement>(".auth-twist-char"));
    letters.forEach((node, index) => expect(node.style.getPropertyValue("--i")).toBe(String(index)));
    const again = render(<TwistText text="Hoş geldin" animate />);
    const angles = (root: HTMLElement) =>
      Array.from(root.querySelectorAll<HTMLElement>(".auth-twist-char")).map((node) => node.style.getPropertyValue("--r"));
    expect(angles(again.container)).toEqual(angles(container));
  });

  it("kelimeler satır sonunda bölünmez (her kelime tek blok)", () => {
    const { container } = render(<TwistText text="Yeni lobin seni" animate />);
    expect(container.querySelectorAll(".whitespace-nowrap")).toHaveLength(3);
  });

  it("animate kapalıyken düz metin gösterilir", () => {
    const { container } = render(<TwistText text="Lobine hoş geldin" animate={false} />);
    expect(screen.getByText("Lobine hoş geldin")).toBeInTheDocument();
    expect(container.querySelector(".auth-twist-char")).toBeNull();
  });
});
