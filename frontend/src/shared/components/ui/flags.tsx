import type { Locale } from "@/shared/lib/i18n/config";

/*
 * Dil bayrakları: yerel, sade SVG (harici görsel/emoji yok). Renkler ulusal bayrak
 * renkleridir (illüstrasyon); UI token'ı değildir. Boyut 3:2'ye yakın, 1px iç çerçeveli.
 */

function pentagram(cx: number, cy: number, radius: number): string {
  const inner = radius * 0.382;
  const points: string[] = [];
  for (let i = 0; i < 10; i += 1) {
    const r = i % 2 === 0 ? radius : inner;
    const angle = (Math.PI * 2 * i) / 10; // ilk köşe sağa (hilalin açıklığına) bakar
    points.push(
      `${(cx + r * Math.cos(angle)).toFixed(2)},${(cy + r * Math.sin(angle)).toFixed(2)}`
    );
  }
  return points.join(" ");
}

const turkeyStar = pentagram(19.4, 10, 2.9);

function TurkeyFlag() {
  return (
    <>
      <rect width="30" height="20" fill="#e30a17" />
      <circle cx="10.6" cy="10" r="5.2" fill="#ffffff" />
      <circle cx="12" cy="10" r="4.2" fill="#e30a17" />
      <polygon points={turkeyStar} fill="#ffffff" />
    </>
  );
}

function BritainFlag() {
  return (
    <>
      <rect width="30" height="20" fill="#012169" />
      <path d="M0 0l30 20M30 0 0 20" stroke="#ffffff" strokeWidth="4" />
      <path d="M0 0l30 20M30 0 0 20" stroke="#c8102e" strokeWidth="1.6" />
      <path d="M15 0v20M0 10h30" stroke="#ffffff" strokeWidth="6" />
      <path d="M15 0v20M0 10h30" stroke="#c8102e" strokeWidth="3.4" />
    </>
  );
}

type FlagProps = { locale: Locale; className?: string };

export function Flag({ locale, className }: FlagProps) {
  return (
    <svg
      viewBox="0 0 30 20"
      width="24"
      height="16"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <clipPath id={`flag-clip-${locale}`}>
        <rect width="30" height="20" rx="2.5" />
      </clipPath>
      <g clipPath={`url(#flag-clip-${locale})`}>
        {locale === "tr" ? <TurkeyFlag /> : <BritainFlag />}
      </g>
      <rect
        x="0.5"
        y="0.5"
        width="29"
        height="19"
        rx="2"
        fill="none"
        stroke="currentColor"
        strokeOpacity="0.35"
      />
    </svg>
  );
}
