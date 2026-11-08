import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SNAKE_LAYERS } from "../services/snake-path";
import { AuthCardSnake } from "./auth-card-snake";

type FakeAnimation = {
  addEventListener: (type: string, listener: () => void) => void;
  cancel: ReturnType<typeof vi.fn>;
  finish: () => void;
};

let created: FakeAnimation[];
let animateCalls: Array<{ keyframes: Keyframe[]; options: KeyframeAnimationOptions }>;

function fakeAnimate(_this: unknown, keyframes: Keyframe[], options: KeyframeAnimationOptions) {
  animateCalls.push({ keyframes, options });
  const listeners: Record<string, Array<() => void>> = {};
  const animation: FakeAnimation = {
    addEventListener: (type, listener) => {
      (listeners[type] ??= []).push(listener);
    },
    cancel: vi.fn(),
    finish: () => (listeners.finish ?? []).forEach((listener) => listener())
  };
  created.push(animation);
  return animation as unknown as Animation;
}

function mockReducedMotion(reduced: boolean) {
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({
        matches: reduced && query.includes("reduce"),
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn()
      }) as unknown as MediaQueryList
  );
}

function renderCard(delay?: number) {
  const view = render(
    <div data-testid="card" className="rounded-3xl">
      <AuthCardSnake initialDelayMs={delay} />
    </div>
  );
  return { card: screen.getByTestId("card"), ...view };
}

/** Bir turu tamamlar: turun ilk katmanının `finish` olayı turu bitirir. */
function finishLap() {
  act(() => created[created.length - SNAKE_LAYERS.length]!.finish());
}

beforeEach(() => {
  vi.useFakeTimers();
  created = [];
  animateCalls = [];
  mockReducedMotion(false);
  Object.defineProperty(SVGElement.prototype, "animate", {
    configurable: true,
    writable: true,
    value(keyframes: Keyframe[], options: KeyframeAnimationOptions) {
      return fakeAnimate(this, keyframes, options);
    }
  });
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(448);
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(570);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  Reflect.deleteProperty(SVGElement.prototype, "animate");
});

const state = () => screen.getByTestId("auth-card-snake").dataset.snakeState;
const start = () => Number(screen.getByTestId("auth-card-snake").dataset.snakeStart);
const enter = (target: Element) => fireEvent(target, new Event("pointerenter"));

describe("AuthCardSnake", () => {
  it("ilk açılışta gecikme sonrası bir kez oynar", () => {
    renderCard(700);
    expect(animateCalls).toHaveLength(0);
    act(() => vi.advanceTimersByTime(699));
    expect(animateCalls).toHaveLength(0);
    act(() => vi.advanceTimersByTime(2));
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length);
    expect(state()).toBe("running");
  });

  it("gecikme verilmezse kendiliğinden oynamaz", () => {
    renderCard();
    act(() => vi.advanceTimersByTime(5000));
    expect(animateCalls).toHaveLength(0);
    expect(state()).toBe("idle");
  });

  it("başlangıç noktası geçerli aralıktadır; tur doğrusaldır ve tam bir turu çevreler", () => {
    vi.spyOn(Math, "random").mockReturnValue(0.37);
    renderCard(0);
    act(() => vi.advanceTimersByTime(1));
    expect(start()).toBeCloseTo(0.37, 4);
    for (const call of animateCalls) {
      expect(call.options.easing).toBe("linear");
      expect(call.options.duration).toBeGreaterThanOrEqual(1500);
      expect(call.options.duration).toBeLessThanOrEqual(2600);
      const first = call.keyframes[0]!;
      const last = call.keyframes[call.keyframes.length - 1]!;
      expect(Number(first.strokeDashoffset) - Number(last.strokeDashoffset)).toBeCloseTo(1, 10);
    }
  });

  it("fare üzerine gelince oynar; tur sürerken gelen yeni tetiklemeler yok sayılır (üst üste binmez)", () => {
    const { card } = renderCard();
    enter(card);
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length);
    enter(card);
    enter(card);
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length);
    expect(state()).toBe("running");
  });

  it("tur bitince durur; sonraki hover YENİ rastgele başlangıçla yeniden oynar", () => {
    const random = vi.spyOn(Math, "random").mockReturnValueOnce(0.1).mockReturnValueOnce(0.8);
    const { card } = renderCard();
    enter(card);
    expect(start()).toBeCloseTo(0.1, 4);
    finishLap();
    expect(state()).toBe("idle");
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length); // kendiliğinden tekrar etmez
    enter(card);
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length * 2);
    expect(start()).toBeCloseTo(0.8, 4);
    expect(random).toHaveBeenCalledTimes(2);
  });

  it("dokunmatik işaretçi tetiklemez", () => {
    const { card } = renderCard();
    const touch = new Event("pointerenter");
    Object.defineProperty(touch, "pointerType", { value: "touch" });
    fireEvent(card, touch);
    expect(animateCalls).toHaveLength(0);
  });

  it("klavye odağı (focus-visible) karta dışarıdan girince oynar; fare/programatik odak oynatmaz", () => {
    const { card } = renderCard();
    const inner = document.createElement("button");
    card.appendChild(inner);
    const matches = vi.spyOn(Element.prototype, "matches").mockReturnValue(false);
    fireEvent.focusIn(inner);
    expect(animateCalls).toHaveLength(0);
    matches.mockReturnValue(true);
    fireEvent.focusIn(inner);
    expect(animateCalls).toHaveLength(SNAKE_LAYERS.length);
  });

  it("odak kartın İÇİNDEN içine geçerse yeniden tetiklemez", () => {
    const { card } = renderCard();
    const first = document.createElement("button");
    const second = document.createElement("button");
    card.append(first, second);
    vi.spyOn(Element.prototype, "matches").mockReturnValue(true);
    fireEvent.focusIn(second, { relatedTarget: first });
    expect(animateCalls).toHaveLength(0);
  });

  it("'hareketi azalt' açıkken oynamaz; sabit çerçeve çizilir", () => {
    mockReducedMotion(true);
    const { card } = renderCard(0);
    act(() => vi.advanceTimersByTime(10));
    enter(card);
    expect(animateCalls).toHaveLength(0);
    expect(card.querySelector("rect.motion-reduce\\:opacity-45")).not.toBeNull();
  });

  it("boyut sıfırken (gizli kart) oynamaz", () => {
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(0);
    const { card } = renderCard();
    enter(card);
    expect(animateCalls).toHaveLength(0);
  });

  it("boyut kartın ölçüsünden alınır ve yol normalleştirilir (pathLength=1)", () => {
    const { card } = renderCard();
    enter(card);
    const rect = card.querySelector("rect[data-layer]")!;
    expect(rect.getAttribute("width")).toBe("449");
    expect(rect.getAttribute("height")).toBe("571");
    expect(rect.getAttribute("pathLength")).toBe("1");
  });

  it("kaldırılınca bekleyen zamanlayıcı iptal olur", () => {
    const { unmount } = renderCard(500);
    unmount();
    act(() => vi.advanceTimersByTime(1000));
    expect(animateCalls).toHaveLength(0);
  });

  it("kaldırılınca çalışan animasyonlar iptal edilir", () => {
    const { card, unmount } = renderCard();
    enter(card);
    const running = [...created];
    unmount();
    for (const animation of running) {
      expect(animation.cancel).toHaveBeenCalled();
    }
  });
});
