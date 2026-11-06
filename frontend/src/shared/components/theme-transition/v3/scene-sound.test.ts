import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { blackoutPlan, daybreakPlan } from "./timeline";

/** jsdom Web Audio uygulamaz; düğümleri ve başlatma anlarını kaydeden sahte bir bağlam. */
type FakeOptions = {
  state?: "running" | "suspended";
  currentTime?: number;
  outputLatency?: number;
  outputTimestamp?: { contextTime: number; performanceTime: number };
};

function fakeAudio({ state = "running", currentTime = 10, outputLatency, outputTimestamp }: FakeOptions = {}) {
  const starts: number[] = [];
  const gains: FakeParam[] = [];
  const disconnects = vi.fn();
  const contexts: { currentTime: number }[] = [];
  let constructed = 0;

  class FakeParam {
    value = 1;
    setValueAtTime = vi.fn();
    linearRampToValueAtTime = vi.fn();
    exponentialRampToValueAtTime = vi.fn();
    cancelScheduledValues = vi.fn();
    setTargetAtTime = vi.fn();
  }
  const node = () => {
    const self = {
      connect: (target: unknown) => target,
      disconnect: disconnects,
      start: (at: number) => starts.push(at),
      stop: vi.fn(),
      frequency: new FakeParam(),
      Q: new FakeParam(),
      gain: new FakeParam(),
      type: "",
      buffer: null as unknown
    };
    return self;
  };

  class FakeAudioContext {
    currentTime = currentTime;
    sampleRate = 8000;
    destination = {};
    state = state;
    outputLatency = outputLatency;
    getOutputTimestamp = outputTimestamp ? () => outputTimestamp : undefined;
    constructor() {
      constructed += 1;
      contexts.push(this);
    }
    createBuffer(_channels: number, length: number) {
      const data = new Float32Array(length);
      return { duration: length / this.sampleRate, getChannelData: () => data };
    }
    createBufferSource = node;
    createBiquadFilter = node;
    createOscillator = node;
    createDynamicsCompressor = node;
    createGain() {
      const gain = node();
      gains.push(gain.gain);
      return gain;
    }
    resume = vi.fn(() => {
      this.state = "running";
      return Promise.resolve();
    });
  }

  vi.stubGlobal("AudioContext", FakeAudioContext);
  return {
    starts,
    gains,
    disconnects,
    contexts,
    get constructed() {
      return constructed;
    }
  };
}

async function loadModule() {
  // Modül, ses bağlamını tek kez oluşturup saklar; her test temiz bir modülle başlar.
  vi.resetModules();
  return import("./scene-sound");
}

beforeEach(() => {
  vi.spyOn(performance, "now").mockReturnValue(1000);
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.useRealTimers();
  delete document.documentElement.dataset.transitionSound;
});

describe("ses ipuçları", () => {
  it("Blackout her lamba için atış anında silah, isabet anında cam kırılması çalar", async () => {
    const { blackoutCues } = await loadModule();
    const plan = blackoutPlan(false);
    const cues = blackoutCues(plan);
    expect(cues).toHaveLength(plan.shots.length * 2);
    plan.shots.forEach((shot, index) => {
      expect(cues[index * 2]).toEqual({ at: shot.fireAt, kind: "shot" });
      expect(cues[index * 2 + 1]).toEqual({ at: shot.hitAt, kind: "shatter" });
    });
    expect(blackoutCues(blackoutPlan(true))).toHaveLength(4);
  });

  it("Daybreak ateşleme anında fişek atışı, tepe noktasında patlama çalar", async () => {
    const { daybreakCues } = await loadModule();
    for (const compact of [false, true]) {
      const plan = daybreakPlan(compact);
      expect(daybreakCues(plan)).toEqual([
        { at: plan.fireAt, kind: "flareLaunch", length: plan.flareApexAt - plan.fireAt },
        { at: plan.flareApexAt, kind: "flareBurst" }
      ]);
    }
  });
});

describe("playSceneSound", () => {
  it("ipuçlarını denetleyici saatine hizalar: sahne 0. ms'i ses saatinde şimdiye denk gelir", async () => {
    const audio = fakeAudio();
    const { playSceneSound } = await loadModule();
    playSceneSound([{ at: 1000, kind: "shot" }], 1000);
    expect(audio.constructed).toBe(1);
    expect(audio.starts.length).toBeGreaterThan(0);
    // Ses saati 10 sn; atış 1000 ms sonra, yani 11 sn'de başlar (yankı kuyruğu 10 ms gecikmeli).
    expect(Math.min(...audio.starts)).toBeCloseTo(11, 5);
    expect(Math.max(...audio.starts)).toBeLessThan(11.05);
  });

  it("çıkış gecikmesini telafi eder: ses, hoparlöre görüntüyle aynı anda ulaşacak kadar erken planlanır", async () => {
    const audio = fakeAudio({ outputLatency: 0.04 });
    const { playSceneSound } = await loadModule();
    playSceneSound([{ at: 1000, kind: "shot" }], 1000);
    expect(Math.min(...audio.starts)).toBeCloseTo(10.96, 5);
  });

  it("tarayıcı çıkış zaman damgası veriyorsa duyulan ses anına hizalar", async () => {
    // Sayfa saati 1000 ms iken hoparlörden ses saatinin 9,95. sn'si duyuluyor.
    const audio = fakeAudio({ outputTimestamp: { contextTime: 9.95, performanceTime: 1000 }, outputLatency: 0.2 });
    const { playSceneSound } = await loadModule();
    playSceneSound([{ at: 1000, kind: "shot" }], 1000);
    expect(Math.min(...audio.starts)).toBeCloseTo(10.95, 5);
  });

  it("ses saati henüz akmıyorsa akmaya başlayınca planlar", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout"] });
    const audio = fakeAudio({ currentTime: 0 });
    const { playSceneSound } = await loadModule();
    playSceneSound([{ at: 1000, kind: "shot" }], 1000);
    vi.advanceTimersByTime(40);
    expect(audio.starts).toHaveLength(0);

    audio.contexts[0].currentTime = 0.2;
    vi.advanceTimersByTime(20);
    expect(Math.min(...audio.starts)).toBeCloseTo(1.2, 5);
  });

  it("sahne geç açıldıysa geçmişte kalan ipuçlarını atlar, kalanları çalar", async () => {
    const audio = fakeAudio();
    const { playSceneSound } = await loadModule();
    vi.mocked(performance.now).mockReturnValue(1000 + 1500);
    playSceneSound(
      [
        { at: 1000, kind: "shot" },
        { at: 2000, kind: "shot" }
      ],
      1000
    );
    expect(audio.starts.length).toBeGreaterThan(0);
    expect(Math.min(...audio.starts)).toBeCloseTo(10.5, 5);
  });

  it("dört ses türü de hata vermeden planlanır", async () => {
    const audio = fakeAudio();
    const { blackoutCues, daybreakCues, playSceneSound } = await loadModule();
    expect(() => {
      playSceneSound([...blackoutCues(blackoutPlan(false)), ...daybreakCues(daybreakPlan(false))], 1000);
    }).not.toThrow();
    expect(audio.starts.length).toBeGreaterThan(40);
  });

  it("tercih kapalıysa ses bağlamı hiç oluşturulmaz", async () => {
    const audio = fakeAudio();
    document.documentElement.dataset.transitionSound = "off";
    const { playSceneSound } = await loadModule();
    const stop = playSceneSound([{ at: 100, kind: "shot" }], 1000);
    expect(audio.constructed).toBe(0);
    expect(() => stop()).not.toThrow();
  });

  it("Web Audio yoksa sessizce hiçbir şey yapmaz", async () => {
    vi.stubGlobal("AudioContext", undefined);
    const { playSceneSound } = await loadModule();
    const stop = playSceneSound([{ at: 100, kind: "flareBurst" }], 1000);
    expect(() => stop()).not.toThrow();
  });

  it("askıdaki bağlamı sürdürür ve ancak sonra planlar", async () => {
    const audio = fakeAudio({ state: "suspended" });
    const { playSceneSound } = await loadModule();
    playSceneSound([{ at: 200, kind: "flareLaunch", length: 400 }], 1000);
    expect(audio.starts).toHaveLength(0);
    await Promise.resolve();
    expect(audio.starts.length).toBeGreaterThan(0);
  });

  it("durdurunca ana ses kısılır ve bağlantı kesilir; bağlam yeniden kullanılır", async () => {
    vi.useFakeTimers({ toFake: ["setTimeout"] });
    const audio = fakeAudio();
    const { playSceneSound } = await loadModule();
    const stop = playSceneSound([{ at: 100, kind: "shatter" }], 1000);
    const master = audio.gains[0];
    stop();
    expect(master.cancelScheduledValues).toHaveBeenCalled();
    expect(master.setTargetAtTime).toHaveBeenCalledWith(0, 10, expect.any(Number));
    vi.advanceTimersByTime(200);
    expect(audio.disconnects).toHaveBeenCalled();

    playSceneSound([{ at: 100, kind: "shot" }], 1000);
    expect(audio.constructed).toBe(1);
  });
});
