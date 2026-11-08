import { render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import en from "@/shared/lib/i18n/messages/en.json";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { toKeyframes } from "./scene-player";
import { BlackoutV3Scene, DaybreakV3Scene } from "./v3-scenes";

function withIntl(node: ReactNode, locale: "tr" | "en" = "tr") {
  return (
    <NextIntlClientProvider locale={locale} messages={locale === "tr" ? tr : en} timeZone="Europe/Istanbul">
      {node}
    </NextIntlClientProvider>
  );
}

type FakeAnimation = { keyframes: Keyframe[]; currentTime: number | null; cancel: ReturnType<typeof vi.fn> };

/** jsdom WAAPI uygulamaz; Element.animate'i kaydeden sahte bir uygulama ile değiştirir. */
function stubAnimate() {
  const created: FakeAnimation[] = [];
  const original = Element.prototype.animate;
  Element.prototype.animate = function animate(keyframes: Keyframe[] | PropertyIndexedKeyframes | null) {
    const animation: FakeAnimation = { keyframes: keyframes as Keyframe[], currentTime: null, cancel: vi.fn() };
    created.push(animation);
    return animation as unknown as Animation;
  };
  return {
    created,
    restore() {
      Element.prototype.animate = original;
    }
  };
}

let restoreAnimate: (() => void) | undefined;

afterEach(() => {
  restoreAnimate?.();
  restoreAnimate = undefined;
});

describe("V3 sahneleri · durağan çizim (WAAPI yok)", () => {
  it("Blackout masaüstünde üç lamba, telsiz levhası ve operatör çizer", () => {
    const { container } = render(withIntl(<BlackoutV3Scene compact={false} startedAt={0} />));
    expect(container.querySelectorAll("[data-part='lamp']")).toHaveLength(3);
    expect(container.querySelectorAll("[data-part='tracer']")).toHaveLength(3);
    expect(container.querySelectorAll("[data-part='flash']")).toHaveLength(3);
    expect(container.querySelector("[data-part='body']")).not.toBeNull();
    expect(container).toHaveTextContent("LTZ BİRİMİ // KARARTMA BAŞLIYOR");
    expect(container).toHaveTextContent("OPERASYON KARARTMA");
  });

  it("Blackout dar ekranda iki lamba çizer ve compact sınıfını alır", () => {
    const { container } = render(withIntl(<BlackoutV3Scene compact startedAt={0} />));
    expect(container.querySelectorAll("[data-part='lamp']")).toHaveLength(2);
    expect(container.querySelector(".v3")).toHaveClass("v3--compact");
  });

  it("Daybreak üç sırt katmanı, güneş, fişek izi ve İngilizce levha çizer", () => {
    const { container } = render(withIntl(<DaybreakV3Scene compact={false} startedAt={0} />, "en"));
    expect(container.querySelectorAll(".v3-ridge")).toHaveLength(3);
    expect(container.querySelector("[data-part='sun']")).not.toBeNull();
    expect(container.querySelectorAll("[data-part='trail']")).toHaveLength(5);
    expect(container).toHaveTextContent("OPERATION DAYBREAK");
    expect(container).toHaveTextContent("LTZ UNIT // FIRST LIGHT");
    // Gece ve gündüz levhası aynı kalıpta: üstte birim satırı, altta operasyon adı.
    const lines = Array.from(container.querySelectorAll("[data-part='caption-line']"), (line) => line.textContent);
    expect(lines).toEqual(["LTZ UNIT // FIRST LIGHT", "OPERATION DAYBREAK"]);
  });

  it("sprite sayfası sahne değişkeninde yüklenir, operatör 128 px kare olur", () => {
    const { container } = render(withIntl(<BlackoutV3Scene compact={false} startedAt={0} />));
    const scene = container.querySelector<HTMLElement>(".v3");
    expect(scene?.style.getPropertyValue("--v3-sprite")).toMatch(/^url\(.+operator-sprites.*\.png\)$/);
    expect(scene?.style.getPropertyValue("--v3-size")).toBe("128px");
  });
});

describe("V3 sahneleri · WAAPI izleri", () => {
  it.each([
    ["blackout", BlackoutV3Scene],
    ["daybreak", DaybreakV3Scene]
  ] as const)("%s: tüm izler geçerli anahtar kare üretir ve sahne kalkınca iptal edilir", (_, Scene) => {
    const animate = stubAnimate();
    restoreAnimate = animate.restore;
    vi.spyOn(performance, "now").mockReturnValue(1300);

    const { unmount } = render(withIntl(<Scene compact={false} startedAt={1000} />));
    expect(animate.created.length).toBeGreaterThan(20);
    for (const animation of animate.created) {
      // Denetleyici saatine hizalı: sahne 300 ms geç açılsa da kaldığı yerden oynar.
      expect(animation.currentTime).toBe(300);
      const offsets = animation.keyframes.map((frame) => frame.offset as number);
      expect(offsets[0]).toBe(0);
      expect(offsets.at(-1)).toBe(1);
      offsets.forEach((offset, index) => {
        expect(offset).toBeGreaterThanOrEqual(index === 0 ? 0 : offsets[index - 1]);
      });
    }

    unmount();
    for (const animation of animate.created) {
      expect(animation.cancel).toHaveBeenCalledTimes(1);
    }
    vi.restoreAllMocks();
  });
});

describe("toKeyframes", () => {
  it("ilk an 0'dan sonraysa başa sabit kare ekler, sona son değeri uzatır", () => {
    const frames = toKeyframes("opacity", [[500, 0], [1000, 1]], 2000);
    expect(frames).toEqual([
      { offset: 0, opacity: 0, easing: "step-end" },
      { offset: 0.25, opacity: 0, easing: "linear" },
      { offset: 0.5, opacity: 1, easing: "linear" },
      { offset: 1, opacity: 1 }
    ]);
  });

  it("süre dışı ve sırası bozuk anları sınırlar (WAAPI hatası yok)", () => {
    const frames = toKeyframes("opacity", [[0, 0], [900, 1], [800, 0.5], [3000, 0]], 1000);
    const offsets = frames.map((frame) => frame.offset);
    expect(offsets).toEqual([0, 0.9, 0.9, 1]);
  });

  it("boş iz anahtar kare üretmez", () => {
    expect(toKeyframes("opacity", [], 1000)).toEqual([]);
  });
});
