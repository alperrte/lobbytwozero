import type { SVGProps } from "react";

/**
 * Auth formlarına özgü küçük ikonlar (ortak ikon setinde göz/kilit/posta yok; ortak dosyaya dokunmamak için
 * burada tutulur). Dekoratiftir; erişilebilir ad ilgili düğme/alan etiketindedir.
 */
const paths = {
  mail: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m4 7 8 6 8-6" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </>
  ),
  eye: (
    <>
      <path d="M2 12s3.5-6.5 10-6.5S22 12 22 12s-3.5 6.5-10 6.5S2 12 2 12z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ),
  eyeOff: (
    <>
      <path d="M3 3l18 18" />
      <path d="M10.6 6.2A9.8 9.8 0 0 1 12 6c6.5 0 10 6 10 6a17 17 0 0 1-3.2 3.9M6.3 7.7A16.6 16.6 0 0 0 2 12s3.5 6 10 6a9.7 9.7 0 0 0 4-.9" />
      <path d="M9.9 10a3 3 0 0 0 4.1 4.1" />
    </>
  ),
  key: (
    <>
      <circle cx="8" cy="15" r="4" />
      <path d="m11 12 8-8m-3 3 3 3" />
    </>
  ),
  google: (
    <text x="12" y="18" fontSize="17" fontWeight="700" textAnchor="middle" fill="currentColor" stroke="none">
      G
    </text>
  ),
  steam: (
    <>
      <circle cx="9" cy="14.5" r="4.5" />
      <circle cx="16" cy="8" r="3" />
      <path d="m12.2 11.8-3 2.7" />
    </>
  ),
  arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
  back: <path d="M19 12H5m6-6-6 6 6 6" />,
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c0-4 3.6-6 8-6s8 2 8 6" />
    </>
  )
} as const;

export type AuthIconName = keyof typeof paths;

export function AuthIcon({
  name,
  className,
  ...props
}: { name: AuthIconName } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className ?? "size-5"}
      {...props}
    >
      {paths[name]}
    </svg>
  );
}
