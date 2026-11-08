import { cn } from "@/shared/utils/cn";

type AvatarSize = "sm" | "md" | "lg";

type AvatarProps = {
  /** Fotoğraf yoksa çizilecek portrenin tohumu (aynı tohum her yerde aynı portreyi verir). */
  seed: string;
  /** Kullanıcının yüklediği fotoğraf (https). Verilirse portre yerine o gösterilir. */
  imageUrl?: string | null;
  size?: AvatarSize;
  /** Kırmızı marka halkası (kart ve satırlarda). */
  ring?: boolean;
  className?: string;
};

const sizeClass: Record<AvatarSize, string> = {
  sm: "size-10",
  md: "size-14",
  lg: "size-20"
};

const tones = ["fill-primary", "fill-secondary", "fill-accent"] as const;
const gears = ["hood", "helmet", "headset", "cap", "mask"] as const;
type Gear = (typeof gears)[number];

/** FNV-1a: küçük, deterministik dağılım (kriptografik değildir; yalnız görünüm seçer). */
export function hashSeed(seed: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < seed.length; index += 1) {
    hash ^= seed.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function portraitFor(seed: string): { gear: Gear; tone: (typeof tones)[number]; tilt: number } {
  const hash = hashSeed(seed);
  return {
    gear: gears[hash % gears.length],
    tone: tones[Math.floor(hash / gears.length) % tones.length],
    tilt: (Math.floor(hash / 97) % 7) - 3
  };
}

const ink = "fill-text-primary/85";

function Gear({ gear, tone }: { gear: Gear; tone: string }) {
  switch (gear) {
    case "hood":
      return (
        <>
          <path className={ink} d="M16 44C14 24 21 11 32 11s18 13 16 33h-6c1-14-3-22-10-22s-11 8-10 22z" />
          <path className={tone} d="M24 28h16l-2 3H26z" />
        </>
      );
    case "helmet":
      return (
        <>
          <path className={ink} d="M19 29c0-11 6-16 13-16s13 5 13 16z" />
          <path className={tone} d="M20 26h24l-2 5H22z" />
        </>
      );
    case "headset":
      return (
        <>
          <path className={ink} d="M21 26c0-9 5-14 11-14s11 5 11 14h-3c0-7-3-11-8-11s-8 4-8 11z" />
          <path className={tone} d="M17 24h5v10h-5zM42 24h5v10h-5z" />
          <path className={ink} d="M43 34h2v4h-8v-2h6z" />
        </>
      );
    case "cap":
      return (
        <>
          <path className={ink} d="M20 24c1-8 6-11 12-11s11 3 12 11z" />
          <path className={ink} d="M31 22h18v3H31z" />
          <path className={tone} d="M26 28h4v2h-4zM34 28h4v2h-4z" />
        </>
      );
    case "mask":
      return (
        <>
          <path className={ink} d="M21 25c0-9 5-13 11-13s11 4 11 13c-4-4-7-5-11-5s-7 1-11 5z" />
          <path className={ink} d="M22 30h20v6l-4 3H26l-4-3z" />
          <path className={tone} d="M22 32h20v1.5H22z" />
        </>
      );
  }
}

/**
 * Oyuncu avatarı. Fotoğraf yoksa tohumdan türeyen, özgün ve deterministik bir "operatör" portresi çizer (kapüşon, kask, kulaklık,
 * şapka veya maske; marka tonlarından biri). Renkler yalnız tokenlardan gelir, Light/Dark'ta kendiliğinden uyar.
 * Ad her zaman yanında metin olarak yazıldığından görsel dekoratiftir (`alt=""` / `aria-hidden`).
 */
export function Avatar({ seed, imageUrl, size = "md", ring = false, className }: AvatarProps) {
  const portrait = portraitFor(seed);
  const frame = cn(
    "relative inline-flex shrink-0 overflow-hidden rounded-full bg-surface-elevated",
    sizeClass[size],
    ring && "ring-2 ring-primary ring-offset-2 ring-offset-surface",
    className
  );

  if (imageUrl) {
    return (
      <span className={frame}>
        {/* Kullanıcı fotoğrafı dış kaynaklı olabilir; boyutu küçük ve sabit olduğundan next/image gerekmez. */}
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={imageUrl} alt="" className="size-full object-cover" loading="lazy" decoding="async" />
      </span>
    );
  }

  return (
    <span className={frame} data-avatar-gear={portrait.gear}>
      <svg viewBox="0 0 64 64" aria-hidden="true" focusable="false" className="size-full">
        <circle cx="32" cy="24" r="24" className={cn(portrait.tone, "opacity-20")} />
        <g transform={`rotate(${portrait.tilt} 32 40)`}>
          <path className="fill-text-secondary/55" d="M7 66c2-15 12-22 25-22s23 7 25 22z" />
          <circle cx="32" cy="28" r="11" className="fill-text-secondary/75" />
          <Gear gear={portrait.gear} tone={portrait.tone} />
        </g>
      </svg>
    </span>
  );
}
