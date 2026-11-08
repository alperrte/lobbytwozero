import type { ReactNode, SVGProps } from "react";
import type { Category } from "../types/hardware";

// Same 24px grid and 1.75px square-ended strokes as the LTZ icon registry.
const paths: Record<Category | "cube" | "filter" | "grid" | "list" | "compare" | "arrow" | "detail", ReactNode> = {
  CPU: <><rect x="6" y="6" width="12" height="12" rx="1" /><path d="M9 9h6v6H9zM9 2v4m6-4v4M9 18v4m6-4v4M2 9h4m-4 6h4M18 9h4m-4 6h4" /></>,
  GPU: <><rect x="2" y="5" width="20" height="13" rx="1" /><circle cx="8" cy="11.5" r="3.5" /><circle cx="16" cy="11.5" r="3.5" /><path d="M5 18v3m3-3v3m5-3v2m3-2v2" /></>,
  RAM: <><path d="M2 5h20v13H2zM5 18v3m4-3v3m4-3v3m4-3v3m4-3v3M5 8h5v6H5zM14 8h5v6h-5z" /></>,
  MOTHERBOARD: <><rect x="4" y="2" width="16" height="20" rx="1" /><path d="M7 5h7v7H7zM17 5v8M7 15h10M7 18h5M15 18h2M7 5V2" /></>,
  STORAGE: <><rect x="5" y="2" width="14" height="20" rx="2" /><path d="M8 5h8v5H8zM8 18h1m3 0h1m3 0h1" /><circle cx="12" cy="14" r="1.5" /></>,
  COOLING: <><rect x="2" y="2" width="20" height="20" rx="3" /><circle cx="12" cy="12" r="2" /><path d="M12 10C5 9 6 3 10 5c2 1 2 3 2 5Zm2 2c1-7 7-6 5-2-1 2-3 2-5 2Zm-2 2c7 1 6 7 2 5-2-1-2-3-2-5Zm-2-2c-1 7-7 6-5 2 1-2 3-2 5-2Z" /></>,
  PSU: <><path d="M3 4h18v16H3z" /><circle cx="11" cy="12" r="5" /><path d="m9 8 4 8m2-6-8 4m12-6v4m0 3v1" /></>,
  MONITOR: <><path d="M2 3h20v14H2zM8 21h8M12 17v4" /></>,
  KEYBOARD: <><path d="M2 6h20v13H2zM5 9h1m3 0h1m3 0h1m3 0h1M5 12h1m3 0h1m3 0h1m3 0h1M6 16h12" /></>,
  MOUSE: <><rect x="5" y="2" width="14" height="20" rx="7" /><path d="M12 2v8M5 10h14M12 5v2" /></>,
  HEADSET: <><path d="M3 14v-3a9 9 0 0 1 18 0v3M6 11H3v8h4v-8H6Zm12 0h3v8h-4v-8h1ZM21 19v1a2 2 0 0 1-2 2h-5" /></>,
  OTHER: <><path d="m12 2 10 5v10l-10 5L2 17V7l10-5ZM2 7l10 5 10-5M12 12v10" /></>,
  cube: <><path d="m12 2 10 5v10l-10 5L2 17V7l10-5ZM2 7l10 5 10-5M12 12v10" /></>,
  filter: <path d="M2 3h20l-8 9v8l-4 2V12L2 3Z" />,
  grid: <path d="M3 3h6v6H3zm12 0h6v6h-6zM3 15h6v6H3zm12 0h6v6h-6z" />,
  list: <path d="M3 5h2m4 0h12M3 12h2m4 0h12M3 19h2m4 0h12" />,
  compare: <><path d="m7 3-4 4 4 4M3 7h14m0 14 4-4-4-4m4 4H7M7 14v3m10-7V7" /></>,
  arrow: <path d="M3 12h17m-6-6 6 6-6 6" />,
  detail: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h5" /></>
};

export function CatalogIcon({ name, ...props }: SVGProps<SVGSVGElement> & { name: keyof typeof paths }) {
  return <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="square" strokeLinejoin="miter" aria-hidden="true" focusable="false" {...props}>{paths[name]}</svg>;
}
