import { describe, expect, it } from "vitest";
import { AUTH_BACKDROP, chooseNext, initialPlaylistState, type PlaylistState } from "./backdrop-playlist";

const equal = [1, 1, 1, 1, 1, 1];

/** Seçilebilir son aralıkta rng. */
function seeded(seed: number) {
  let value = seed;
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296;
    return value / 4294967296;
  };
}

function run(weights: number[], steps: number, rng: () => number, factor = 0.15) {
  let state: PlaylistState = initialPlaylistState;
  const order: number[] = [];
  for (let step = 0; step < steps; step++) {
    const result = chooseNext({ weights, state, factor, rng });
    order.push(result.index);
    state = result.state;
  }
  return order;
}

describe("chooseNext", () => {
  it("arka arkaya aynı videoyu seçmez", () => {
    const order = run(equal, 2000, seeded(1));
    for (let i = 1; i < order.length; i++) expect(order[i]).not.toBe(order[i - 1]);
  });

  it("tek videoda hep onu seçer", () => {
    expect(run([1], 5, seeded(2))).toEqual([0, 0, 0, 0, 0]);
  });

  it("her turda (6 seçim) önce oynamamış videolar gelir: ilk 6 seçim hepsini bir kez içerir", () => {
    // Çarpan 0 ise oynamış video turda bir daha seçilemez: tam bir permütasyon çıkar.
    for (let seed = 1; seed <= 50; seed++) {
      const first = run(equal, 6, seeded(seed), 0);
      expect(new Set(first).size).toBe(6);
    }
  });

  it("tur bitince herkes normal ağırlığa döner (7. seçimde yine her video seçilebilir)", () => {
    const seen = new Set<number>();
    for (let seed = 1; seed <= 400; seed++) {
      const order = run(equal, 7, seeded(seed), 0);
      seen.add(order[6] as number);
    }
    // 7. seçim yeni turun ilkidir: son oynayan hariç tüm videolar görülebilmeli.
    expect(seen.size).toBeGreaterThanOrEqual(5);
  });

  it("daha önce oynayanın şansı azalır ama sıfır olmaz (çarpan 0.15)", () => {
    const state: PlaylistState = { played: new Set([0, 1, 2, 3]), last: 5 };
    const counts = new Array<number>(6).fill(0);
    const rng = seeded(7);
    for (let i = 0; i < 20000; i++) {
      // Her seferinde aynı başlangıç durumundan tek seçim (tur sıfırlanmasın diye 4 oynamış).
      counts[chooseNext({ weights: equal, state, factor: 0.15, rng }).index]!++;
    }
    expect(counts[5]).toBe(0); // son oynayan hariç
    expect(counts[4]).toBeGreaterThan(counts[0]! * 3); // oynamamış, oynamıştan çok daha sık
    expect(counts[0]).toBeGreaterThan(0); // oynamış olan yine de seçilebilir
  });

  it("oranlar (weight) gözetilir: ağırlığı 3 olan video diğerlerinden belirgin sık gelir", () => {
    const counts = new Array<number>(3).fill(0);
    const rng = seeded(11);
    for (let i = 0; i < 20000; i++) {
      counts[chooseNext({ weights: [3, 1, 1], state: initialPlaylistState, factor: 0.15, rng }).index]!++;
    }
    expect(counts[0]!).toBeGreaterThan(counts[1]! * 2);
  });

  it("sıfır ağırlıklı videolar seçilmez; hepsi sıfırsa eşit dağıtır", () => {
    const rng = seeded(3);
    for (let i = 0; i < 200; i++) {
      expect(chooseNext({ weights: [0, 1, 1], state: initialPlaylistState, factor: 0.15, rng }).index).not.toBe(0);
    }
    const picked = new Set<number>();
    for (let i = 0; i < 200; i++) picked.add(chooseNext({ weights: [0, 0, 0], state: initialPlaylistState, factor: 0.15, rng }).index);
    expect(picked.size).toBe(3);
  });

  it("boş listede hata verir", () => {
    expect(() => chooseNext({ weights: [], state: initialPlaylistState, factor: 0.15, rng: Math.random })).toThrow();
  });
});

describe("AUTH_BACKDROP", () => {
  it("6 video tanımlı, hepsi /auth-videos/ altında ve oranları pozitif", () => {
    expect(AUTH_BACKDROP.videos).toHaveLength(6);
    for (const video of AUTH_BACKDROP.videos) {
      expect(video.src).toMatch(/^\/auth-videos\/bg-[0-9]+\.mp4$/);
      expect(video.weight).toBeGreaterThan(0);
    }
  });
});
