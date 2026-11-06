import { parseTransitionSound } from "@/shared/lib/preferences/preferences";
import type { BlackoutPlan, DaybreakPlan } from "./timeline";

/**
 * V3 sahne sesleri: tüfek atışı, kırılan lamba camı, işaret fişeği atışı ve havada patlaması.
 * Sesler Web Audio API ile anlık sentezlenir (gürültü + filtre + osilatör); ses dosyası, lisans veya ağ isteği yoktur.
 * AudioContext yalnız sahne açılınca (kullanıcı tema düğmesine bastıktan sonra) ve tercih `on` ise oluşturulur.
 * Zamanlar timeline.ts çizelgesinden gelir ve denetleyicinin `startedAt` saatine hizalanır.
 */
export type SoundKind = "shot" | "shatter" | "flareLaunch" | "flareBurst";
export type SoundCue = { at: number; kind: SoundKind; length?: number };

/** Her lamba: atış anında silah sesi, isabet anında cam kırılması. */
export function blackoutCues(plan: BlackoutPlan): SoundCue[] {
  return plan.shots.flatMap((shot): SoundCue[] => [
    { at: shot.fireAt, kind: "shot" },
    { at: shot.hitAt, kind: "shatter" }
  ]);
}

/** Fişek: ateşleme anında atış + yükselme ıslığı, tepe noktasında patlama ve çıtırtı. */
export function daybreakCues(plan: DaybreakPlan): SoundCue[] {
  return [
    { at: plan.fireAt, kind: "flareLaunch", length: plan.flareApexAt - plan.fireAt },
    { at: plan.flareApexAt, kind: "flareBurst" }
  ];
}

export function sceneSoundEnabled(): boolean {
  return parseTransitionSound(document.documentElement.dataset.transitionSound) === "on";
}

/** Geçmişte kalan ipucu bu kadar gecikmeyle hâlâ çalınır; daha eskisi atlanır (sahne geç açıldıysa). */
const LATE_TOLERANCE_S = 0.03;
const MASTER_GAIN = 0.32;
const SILENT = 0.0001;

type Engine = { ctx: AudioContext; noise: AudioBuffer };
type Random = () => number;

let shared: Engine | null = null;

/** Deterministik küçük PRNG: her çalışta aynı karakterde ama tekdüze olmayan cam ve çıtırtı dağılımı. */
function seeded(seed: number): Random {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let mixed = Math.imul(state ^ (state >>> 15), 1 | state);
    mixed = (mixed + Math.imul(mixed ^ (mixed >>> 7), 61 | mixed)) ^ mixed;
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
  };
}

function engine(): Engine | null {
  if (shared) {
    return shared;
  }
  const Context =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (typeof Context !== "function") {
    return null;
  }
  try {
    const ctx = new Context();
    const noise = ctx.createBuffer(1, Math.round(ctx.sampleRate * 2), ctx.sampleRate);
    const data = noise.getChannelData(0);
    const random = seeded(0x17a);
    for (let index = 0; index < data.length; index += 1) {
      data[index] = random() * 2 - 1;
    }
    shared = { ctx, noise };
    return shared;
  } catch {
    // Ses donanımı yoksa veya tarayıcı reddederse sahne sessiz oynar.
    return null;
  }
}

function envelope(param: AudioParam, at: number, peak: number, attack: number, decay: number) {
  param.setValueAtTime(SILENT, at);
  param.exponentialRampToValueAtTime(peak, at + attack);
  param.exponentialRampToValueAtTime(SILENT, at + attack + decay);
}

type NoiseSpec = {
  filter: BiquadFilterType;
  frequency: number;
  q?: number;
  peak: number;
  attack?: number;
  decay: number;
};

function noise({ ctx, noise: buffer }: Engine, out: AudioNode, at: number, spec: NoiseSpec, random: Random) {
  const attack = spec.attack ?? 0.002;
  const source = ctx.createBufferSource();
  source.buffer = buffer;
  const filter = ctx.createBiquadFilter();
  filter.type = spec.filter;
  filter.frequency.setValueAtTime(spec.frequency, at);
  filter.Q.setValueAtTime(spec.q ?? 0.8, at);
  const gain = ctx.createGain();
  envelope(gain.gain, at, spec.peak, attack, spec.decay);
  source.connect(filter).connect(gain).connect(out);
  // Tampondan farklı bir kesit çalınır; art arda atışlar birebir aynı duyulmaz.
  source.start(at, random() * (buffer.duration - 1), attack + spec.decay + 0.05);
  return filter;
}

type ToneSpec = { wave?: OscillatorType; from: number; to: number; peak: number; attack?: number; decay: number };

function tone({ ctx }: Engine, out: AudioNode, at: number, spec: ToneSpec) {
  const attack = spec.attack ?? 0.002;
  const end = at + attack + spec.decay;
  const oscillator = ctx.createOscillator();
  oscillator.type = spec.wave ?? "sine";
  oscillator.frequency.setValueAtTime(spec.from, at);
  oscillator.frequency.exponentialRampToValueAtTime(spec.to, end);
  const gain = ctx.createGain();
  envelope(gain.gain, at, spec.peak, attack, spec.decay);
  oscillator.connect(gain).connect(out);
  oscillator.start(at);
  oscillator.stop(end + 0.05);
}

/** Kısa, sert tüfek atışı: yüksek frekans çatlak, gövde, alçak darbe ve kısa yankı kuyruğu. */
function shot(sound: Engine, out: AudioNode, at: number, random: Random) {
  noise(sound, out, at, { filter: "highpass", frequency: 1800, peak: 0.9, attack: 0.001, decay: 0.06 }, random);
  noise(sound, out, at, { filter: "lowpass", frequency: 2200, peak: 1, attack: 0.001, decay: 0.16 }, random);
  tone(sound, out, at, { from: 160, to: 45, peak: 0.9, decay: 0.12 });
  noise(sound, out, at + 0.01, { filter: "bandpass", frequency: 600, q: 0.7, peak: 0.18, attack: 0.01, decay: 0.4 }, random);
}

/** Lamba camı kırılır: ampul patlaması, ardından düşen cam kırıklarının tınlaması. */
function shatter(sound: Engine, out: AudioNode, at: number, random: Random) {
  noise(sound, out, at, { filter: "highpass", frequency: 3000, peak: 0.55, attack: 0.001, decay: 0.05 }, random);
  tone(sound, out, at, { wave: "triangle", from: 900, to: 260, peak: 0.12, decay: 0.05 });
  for (let index = 0; index < 10; index += 1) {
    // Kırıkların çoğu ilk anlarda düşer; dağılım başa yığılır.
    const delay = Math.pow(random(), 1.6) * 0.3;
    noise(
      sound,
      out,
      at + delay,
      {
        filter: "bandpass",
        frequency: 3000 + random() * 6000,
        q: 8 + random() * 6,
        peak: 0.28 * (1 - delay * 2),
        attack: 0.001,
        decay: 0.03 + random() * 0.07
      },
      random
    );
  }
  for (let index = 0; index < 4; index += 1) {
    const delay = 0.02 + random() * 0.22;
    const pitch = 2400 + random() * 4000;
    tone(sound, out, at + delay, { from: pitch, to: pitch * 0.97, peak: 0.05, attack: 0.001, decay: 0.08 + random() * 0.12 });
  }
}

/** Fişek tabancası: boğuk atış, fişek yükselirken tizleşen ıslık ve cızırtı. */
function flareLaunch(sound: Engine, out: AudioNode, at: number, length: number, random: Random) {
  noise(sound, out, at, { filter: "lowpass", frequency: 1200, peak: 0.7, attack: 0.001, decay: 0.09 }, random);
  tone(sound, out, at, { from: 130, to: 55, peak: 0.6, decay: 0.1 });

  const { ctx, noise: buffer } = sound;
  const end = at + Math.max(0.12, length);
  for (const spec of [
    { filter: "bandpass" as const, from: 500, to: 2600, q: 1.4, peak: 0.34 },
    { filter: "highpass" as const, from: 4000, to: 6000, q: 0.7, peak: 0.08 }
  ]) {
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    const filter = ctx.createBiquadFilter();
    filter.type = spec.filter;
    filter.Q.setValueAtTime(spec.q, at);
    filter.frequency.setValueAtTime(spec.from, at);
    filter.frequency.exponentialRampToValueAtTime(spec.to, end);
    const gain = ctx.createGain();
    gain.gain.setValueAtTime(SILENT, at);
    gain.gain.linearRampToValueAtTime(spec.peak, at + 0.06);
    gain.gain.linearRampToValueAtTime(spec.peak * 0.7, end - 0.04);
    gain.gain.exponentialRampToValueAtTime(SILENT, end + 0.04);
    source.connect(filter).connect(gain).connect(out);
    source.start(at, random() * (buffer.duration - 1), end - at + 0.1);
  }
}

/** Fişek havada patlar: derin gümbürtü, ardından çıtırtı ve yanan magnezyumun cızırtısı. */
function flareBurst(sound: Engine, out: AudioNode, at: number, random: Random) {
  tone(sound, out, at, { from: 110, to: 38, peak: 0.85, attack: 0.003, decay: 0.35 });
  noise(sound, out, at, { filter: "lowpass", frequency: 1600, peak: 0.8, attack: 0.002, decay: 0.28 }, random);
  for (let index = 0; index < 18; index += 1) {
    const delay = 0.04 + random() * 0.7;
    noise(
      sound,
      out,
      at + delay,
      {
        filter: "highpass",
        frequency: 2500 + random() * 3000,
        peak: 0.22 * (1 - delay),
        attack: 0.001,
        decay: 0.012 + random() * 0.02
      },
      random
    );
  }
  noise(sound, out, at + 0.05, { filter: "highpass", frequency: 5000, peak: 0.07, attack: 0.05, decay: 0.9 }, random);
}

function voice(sound: Engine, out: AudioNode, at: number, cue: SoundCue, random: Random) {
  switch (cue.kind) {
    case "shot":
      shot(sound, out, at, random);
      break;
    case "shatter":
      shatter(sound, out, at, random);
      break;
    case "flareLaunch":
      flareLaunch(sound, out, at, (cue.length ?? 400) / 1000, random);
      break;
    case "flareBurst":
      flareBurst(sound, out, at, random);
      break;
  }
}

const noop = () => {};

/** Ses saati henüz akmıyorsa (cihaz yeni açılıyor) bu aralıkla bu kadar kez beklenir, sonra yine de planlanır. */
const CLOCK_WAIT_MS = 16;
const CLOCK_WAIT_TRIES = 30;

/**
 * Sahnenin 0. ms'inin ses saatindeki karşılığı. Hoparlörden o an duyulan ses anı (`getOutputTimestamp`)
 * esas alınır; böylece çıkış gecikmesi telafi edilir ve atış, cam ve patlama görüntüyle aynı anda duyulur.
 * Tarayıcı zaman damgası vermiyorsa denetleyici saati ve bildirilen çıkış gecikmesi kullanılır.
 */
function sceneOrigin(ctx: AudioContext, startedAt: number): number {
  const stamp = typeof ctx.getOutputTimestamp === "function" ? ctx.getOutputTimestamp() : undefined;
  if (stamp?.contextTime && stamp.performanceTime) {
    return stamp.contextTime - (stamp.performanceTime - startedAt) / 1000;
  }
  const latency = Number.isFinite(ctx.outputLatency) ? ctx.outputLatency : 0;
  return ctx.currentTime - (performance.now() - startedAt) / 1000 - latency;
}

/**
 * İpuçlarını `startedAt` saatine göre planlar ve durdurma işlevini döndürür (sahne kalkınca çağrılır).
 * Tercih kapalıysa, Web Audio yoksa veya tarayıcı sesi reddederse hiçbir şey çalmaz ve hata fırlatmaz.
 */
export function playSceneSound(cues: readonly SoundCue[], startedAt: number): () => void {
  if (cues.length === 0 || !sceneSoundEnabled()) {
    return noop;
  }
  const sound = engine();
  if (!sound) {
    return noop;
  }
  const { ctx } = sound;
  let cancelled = false;
  let master: GainNode;
  try {
    master = ctx.createGain();
    master.gain.value = MASTER_GAIN;
    const limiter = ctx.createDynamicsCompressor();
    master.connect(limiter).connect(ctx.destination);
  } catch {
    return noop;
  }

  let waits = 0;
  const schedule = () => {
    if (cancelled) {
      return;
    }
    // Yeni açılan bağlamda saat birkaç kare 0'da durabilir; o anki eşleme yanlış olur, akmaya başlaması beklenir.
    if (ctx.currentTime === 0 && waits < CLOCK_WAIT_TRIES) {
      waits += 1;
      window.setTimeout(schedule, CLOCK_WAIT_MS);
      return;
    }
    const now = ctx.currentTime;
    const origin = sceneOrigin(ctx, startedAt);
    // Sahnede şu an gelinen nokta, ses saatinde; bundan eski ipuçları sahne geç açıldığı için atlanır.
    const heardNow = origin + (performance.now() - startedAt) / 1000;
    const random = seeded(Math.round(startedAt));
    try {
      for (const cue of cues) {
        const at = origin + cue.at / 1000;
        if (at >= heardNow - LATE_TOLERANCE_S) {
          voice(sound, master, Math.max(at, now), cue, random);
        }
      }
    } catch {
      // Planlama hatası sahneyi bozmaz; ses sessizce atlanır.
    }
  };

  if (ctx.state === "suspended") {
    ctx.resume().then(schedule, noop);
  } else {
    schedule();
  }

  return () => {
    cancelled = true;
    try {
      const now = ctx.currentTime;
      master.gain.cancelScheduledValues(now);
      master.gain.setTargetAtTime(0, now, 0.015);
      window.setTimeout(() => master.disconnect(), 120);
    } catch {
      // Bağlam kapandıysa yapılacak bir şey yok.
    }
  };
}
