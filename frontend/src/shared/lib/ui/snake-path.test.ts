import { describe, expect, it } from "vitest";
import {
  lapDuration,
  layerFraction,
  randomStart,
  roundedPerimeter,
  SNAKE_LAYERS,
  SNAKE_MAX_MS,
  SNAKE_MIN_MS,
  snakeKeyframes
} from "./snake-path";

describe("snake-path", () => {
  it("rastgele başlangıç [0, 1) aralığında kalır", () => {
    expect(randomStart(() => 0)).toBe(0);
    expect(randomStart(() => 0.5)).toBe(0.5);
    expect(randomStart(() => 1)).toBeLessThan(1);
    expect(randomStart(() => -3)).toBe(0);
    expect(randomStart(() => Number.NaN)).toBe(0);
    for (let i = 0; i < 200; i++) {
      const value = randomStart();
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThan(1);
    }
  });

  it("çevre hesabı: köşesiz dikdörtgen ve yuvarlak köşeli", () => {
    expect(roundedPerimeter(100, 50, 0)).toBe(300);
    // r=10: 2(w+h) - 8r + 2πr
    expect(roundedPerimeter(100, 50, 10)).toBeCloseTo(300 - 80 + 2 * Math.PI * 10, 6);
    // Yarıçap kısa kenarın yarısıyla sınırlanır.
    expect(roundedPerimeter(100, 20, 500)).toBeCloseTo(2 * 120 - 8 * 10 + 2 * Math.PI * 10, 6);
  });

  it("tur süresi alt ve üst sınırla kısıtlıdır", () => {
    expect(lapDuration(10)).toBe(SNAKE_MIN_MS);
    expect(lapDuration(1_000_000)).toBe(SNAKE_MAX_MS);
    expect(lapDuration(2000)).toBeGreaterThan(SNAKE_MIN_MS - 1);
  });

  it("katman oranı çevreye göre hesaplanır ve çevreyi aşmaz", () => {
    expect(layerFraction(100, 1000)).toBeCloseTo(0.1, 6);
    expect(layerFraction(100, 0)).toBe(0);
    expect(layerFraction(900, 1000)).toBeLessThanOrEqual(0.4);
  });

  it("katmanlar uzunluğa göre azalan sıradadır (yumuşak kuyruk)", () => {
    for (let i = 1; i < SNAKE_LAYERS.length; i++) {
      expect(SNAKE_LAYERS[i]!.px).toBeLessThan(SNAKE_LAYERS[i - 1]!.px);
    }
  });

  it("anahtar kareler tam BİR tur çevreler: başlangıçtan çıkıp başlangıçta biter", () => {
    const start = 0.3;
    const fraction = 0.08;
    const frames = snakeKeyframes(start, fraction, 0.2);
    const first = frames[0]!;
    const last = frames[frames.length - 1]!;
    // Baş p konumundayken parça [p - f, p]: offset = f - p. p, start'tan start+1'e gider.
    expect(Number(first.strokeDashoffset)).toBeCloseTo(fraction - start, 10);
    expect(Number(last.strokeDashoffset)).toBeCloseTo(fraction - start - 1, 10);
    expect(Number(first.strokeDashoffset) - Number(last.strokeDashoffset)).toBeCloseTo(1, 10);
    expect(first.strokeOpacity).toBe(0);
    expect(last.strokeOpacity).toBe(0);
    expect(frames.some((frame) => frame.strokeOpacity === 0.2)).toBe(true);
  });
});
