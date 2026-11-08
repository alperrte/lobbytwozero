import type { ReactNode, SVGProps } from "react";

/**
 * Tek, tutarlı çizgi ikon seti: 24px ızgara, 1.75 çizgi, köşeli uç ve birleşim
 * (logodaki keskin hatlarla uyumlu). Dış ikon paketi kullanılmaz.
 */
const paths = {
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  close: <path d="M6 6l12 12M18 6 6 18" />,
  panel: <path d="M3 4h18v16H3zM9 4v16" />,
  home: <path d="M4 11 12 4l8 7M6 9.5V20h12V9.5" />,
  user: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20v-1.5A4.5 4.5 0 0 1 9.5 14h5a4.5 4.5 0 0 1 4.5 4.5V20" />
    </>
  ),
  desktop: <path d="M3 4h18v12H3zM8 20h8M12 16v4" />,
  cpu: (
    <>
      <path d="M7 7h10v10H7z" />
      <path d="M10 3v4M14 3v4M10 17v4M14 17v4M3 10h4M3 14h4M17 10h4M17 14h4" />
    </>
  ),
  tag: (
    <>
      <path d="M3 12V4h8l10 10-8 8z" />
      <circle cx="7.5" cy="8.5" r="1" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: <path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z" />,
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m15.5 15.5 5.5 5.5" />
    </>
  ),
  bell: (
    <>
      <path d="M6 17v-6a6 6 0 0 1 12 0v6l2 2H4z" />
      <path d="M10 21.5h4" />
    </>
  ),
  chevron: <path d="m6 9 6 6 6-6" />,
  settings: (
    <>
      <path d="M4 7h9M19 7h1M4 17h1M11 17h9" />
      <circle cx="16" cy="7" r="2.25" />
      <circle cx="8" cy="17" r="2.25" />
    </>
  ),
  logout: <path d="M10 4H4v16h6M14 8l4 4-4 4M18 12H9" />,
  check: <path d="m5 12.5 5 5L19 7" />,
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5.5M12 7.5v.5" />
    </>
  ),
  arrowRight: <path d="M4 12h16M14 6l6 6-6 6" />,
  contrast: (
    <>
      <circle cx="12" cy="12" r="8" />
      <path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" />
    </>
  ),
  // Kenar çubuğu ikonları (aynı 24px ızgara ve çizgi dili).
  gamepad: (
    <>
      <path d="M7 7h10l4 5v4l-2 2h-2l-2-3H9l-2 3H5l-2-2v-4z" />
      <path d="M8 10v3M6.5 11.5h3M15.5 10.5h.5M17.5 12.5h.5" />
    </>
  ),
  library: <path d="M4 4v16M8 4v16M12 6v14M16 6l4 14" />,
  star: <path d="m12 3 2.7 5.8 6.3.8-4.6 4.4 1.2 6.3-5.6-3.1-5.6 3.1 1.2-6.3L3 9.6l6.3-.8z" />,
  newspaper: <path d="M4 4h13v16H4zM17 9h3v11h-3M7 8h7M7 12h7M7 16h4" />,
  sparkles: (
    <>
      <path d="m10 4 1.8 4.7 4.7 1.8-4.7 1.8L10 17l-1.8-4.7-4.7-1.8 4.7-1.8z" />
      <path d="M18 3v4M16 5h4M18 16v4M16 18h4" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20v-1.5A4.5 4.5 0 0 1 7 14h4a4.5 4.5 0 0 1 4.5 4.5V20" />
      <path d="M16 4.7a3.5 3.5 0 0 1 0 6.6M18 14.3a4.5 4.5 0 0 1 3.5 4.2V20" />
    </>
  ),
  userCheck: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20v-1.5A4.5 4.5 0 0 1 7 14h4a4.5 4.5 0 0 1 4.5 4.5V20" />
      <path d="m16 11 2.5 2.5L22 9" />
    </>
  ),
  usersRound: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 21a6.5 6.5 0 0 1 13 0" />
      <circle cx="17.5" cy="9" r="2.5" />
      <path d="M17.5 14a4.5 4.5 0 0 1 4.5 4.5" />
    </>
  ),
  messages: (
    <>
      <path d="M3 4h13v9H9l-3 3v-3H3z" />
      <path d="M16 8h5v10h-2v3l-3-3h-6v-3" />
    </>
  ),
  calendar: <path d="M4 5h16v15H4zM4 10h16M8 3v4M16 3v4M8 14h1M12 14h1M16 14h1" />,
  chart: <path d="M3 15l5.5-5.5 4 4L21 5M4 18v3M8 14v7M12 17v4M16 14v7M20 10v11" />,
  panelLeftClose: <path d="M3 4h18v16H3zM9 4v16M16 15l-3-3 3-3" />,
  panelLeftOpen: <path d="M3 4h18v16H3zM9 4v16M14 9l3 3-3 3" />,
  pin: <path d="M9 4h6v5l3 4H6l3-4zM12 13v7" />,
  eyeOff: (
    <>
      <path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M4 4l16 16" />
    </>
  ),
  fileText: <path d="M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 16h6" />,
  crosshair: (
    <>
      <circle cx="12" cy="12" r="7" />
      <path d="M12 2v6M12 16v6M2 12h6M16 12h6" />
    </>
  ),
  volume: <path d="M4 9h4l5-4v14l-5-4H4zM16.5 9.5a3.5 3.5 0 0 1 0 5M19 7a7 7 0 0 1 0 10" />,
  volumeOff: <path d="M4 9h4l5-4v14l-5-4H4zM16 9.5l5 5M21 9.5l-5 5" />,
  // Sosyal ekranlar (arkadaşlar, istekler, engellenenler).
  userPlus: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20v-1.5A4.5 4.5 0 0 1 7 14h4a4.5 4.5 0 0 1 4.5 4.5V20" />
      <path d="M19 8v6M16 11h6" />
    </>
  ),
  userMinus: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20v-1.5A4.5 4.5 0 0 1 7 14h4a4.5 4.5 0 0 1 4.5 4.5V20" />
      <path d="M16 11h6" />
    </>
  ),
  send: <path d="M21 3 3 10.5l7 3 3 7.5zM10 13.5 21 3" />,
  ban: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="m6 6 12 12" />
    </>
  ),
  shield: <path d="M12 3 4.5 6v5.5c0 4.5 3.2 8 7.5 9.5 4.3-1.5 7.5-5 7.5-9.5V6z" />,
  copy: <path d="M8 8h12v12H8zM16 8V4H4v12h4" />,
  more: (
    <>
      <circle cx="5.5" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none" />
      <circle cx="18.5" cy="12" r="1.6" fill="currentColor" stroke="none" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  share: (
    <>
      <circle cx="6" cy="12" r="2.5" />
      <circle cx="18" cy="6" r="2.5" />
      <circle cx="18" cy="18" r="2.5" />
      <path d="m8.2 10.9 7.6-3.8M8.2 13.1l7.6 3.8" />
    </>
  ),
  refresh: <path d="M20 11a8 8 0 0 0-14.5-4.5L4 8M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5L20 16M20 20v-4h-4" />,
  message: <path d="M4 5h16v11h-9l-4 3.5V16H4z" />
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof paths;

type IconProps = Omit<SVGProps<SVGSVGElement>, "name"> & { name: IconName };

export function Icon({ name, className, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="square"
      strokeLinejoin="miter"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...props}
    >
      {paths[name]}
    </svg>
  );
}
