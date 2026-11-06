import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Accordion } from "./accordion";

const items = [
  { id: "a", title: "Birinci soru", content: <p>Birinci cevap</p> },
  { id: "b", title: "İkinci soru", content: <p>İkinci cevap</p> }
];

describe("Accordion", () => {
  it("tüm soruları kapalı başlatır", () => {
    render(<Accordion items={items} />);

    for (const button of screen.getAllByRole("button")) {
      expect(button).toHaveAttribute("aria-expanded", "false");
    }
  });

  it("kapalı paneldeki cevabı erişilebilirlik ağacından gizler (inert)", () => {
    render(<Accordion items={items} />);

    const panel = document.getElementById(
      screen.getByRole("button", { name: "Birinci soru" }).getAttribute("aria-controls") as string
    ) as HTMLElement;
    expect(panel).toHaveAttribute("inert");
    expect(screen.queryByText("Birinci cevap", { ignore: "[inert] *" })).toBeNull();
  });

  it("tıklayınca açılır, aria-controls ilgili paneli gösterir ve tekrar tıklayınca kapanır", () => {
    render(<Accordion items={items} />);
    const button = screen.getByRole("button", { name: "Birinci soru" });

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "true");
    const panel = document.getElementById(button.getAttribute("aria-controls") as string);
    expect(panel).not.toBeNull();
    expect(panel).not.toHaveAttribute("inert");
    expect(panel).toHaveTextContent("Birinci cevap");
    expect(panel?.className).toContain("grid-rows-[1fr]");

    fireEvent.click(button);
    expect(button).toHaveAttribute("aria-expanded", "false");
    expect(panel).toHaveAttribute("inert");
    expect(panel?.className).toContain("grid-rows-[0fr]");
  });

  it("satırlar bağımsız açılır", () => {
    render(<Accordion items={items} />);

    fireEvent.click(screen.getByRole("button", { name: "Birinci soru" }));
    fireEvent.click(screen.getByRole("button", { name: "İkinci soru" }));

    expect(screen.getByRole("button", { name: "Birinci soru" })).toHaveAttribute(
      "aria-expanded",
      "true"
    );
    expect(screen.getByRole("button", { name: "İkinci soru" })).toHaveAttribute(
      "aria-expanded",
      "true"
    );
  });

  it("başlıklar yerel düğmedir (Enter/Space tarayıcıdan gelir), odaklanabilir ve hareket azaltmayı destekler", () => {
    render(<Accordion items={items} />);
    const button = screen.getByRole("button", { name: "Birinci soru" });

    expect(button.tagName).toBe("BUTTON");
    expect(button).toHaveAttribute("type", "button");
    button.focus();
    expect(button).toHaveFocus();
    expect(button.parentElement?.tagName).toBe("H3");
    const chevron = button.querySelector("svg") as SVGElement;
    expect(chevron.getAttribute("class")).toContain("motion-reduce:transition-none");
  });

  it("açıkken chevron döner", () => {
    render(<Accordion items={items} />);
    const button = screen.getByRole("button", { name: "Birinci soru" });
    const chevron = button.querySelector("svg") as SVGElement;

    expect(chevron.getAttribute("class")).not.toContain("rotate-180");
    fireEvent.click(button);
    expect(chevron.getAttribute("class")).toContain("rotate-180");
  });

  it("başlık seviyesi ayarlanabilir", () => {
    render(<Accordion items={items} headingLevel="h4" />);

    expect(screen.getAllByRole("heading", { level: 4 })).toHaveLength(2);
  });
});
