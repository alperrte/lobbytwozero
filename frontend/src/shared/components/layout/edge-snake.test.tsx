import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { EdgeSnake } from "./edge-snake";

function renderSnake() {
  return render(
    <div style={{ borderRadius: "18px" }}>
      <EdgeSnake />
    </div>
  );
}

describe("EdgeSnake", () => {
  it("etkileşimi engellemez ve erişilebilirlik ağacından gizlidir", () => {
    const { container } = renderSnake();
    const svg = container.querySelector("svg");
    expect(svg).toHaveAttribute("aria-hidden", "true");
    expect(svg?.getAttribute("class")).toContain("pointer-events-none");
  });

  it("tek bir ışık izi çizer: tüm katmanlar aynı animasyonu paylaşır, hareket azaltmada gizlenir", () => {
    const { container } = renderSnake();
    const trail = container.querySelectorAll("rect.edge-trail");
    expect(trail.length).toBeGreaterThan(1);
    for (const layer of trail) {
      expect(layer.getAttribute("class")).toContain("motion-reduce:hidden");
    }
    // Hareket azaltma için sade statik çerçeve de vardır.
    expect(container.querySelectorAll("rect")).toHaveLength(trail.length + 1);
  });

  it("izin en uzun katmanı 70-100px aralığındadır", () => {
    const { container } = renderSnake();
    const lengths = [...container.querySelectorAll<SVGRectElement>("rect.edge-trail")].map(
      (rect) => parseFloat(rect.style.getPropertyValue("--len"))
    );
    expect(Math.max(...lengths)).toBeGreaterThanOrEqual(70);
    expect(Math.max(...lengths)).toBeLessThanOrEqual(100);
  });
});
