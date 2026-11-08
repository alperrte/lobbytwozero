import type { SVGProps } from "react";

/**
 * Community'ye özgü çizgi ikonlar (ortak ikon setiyle aynı ölçü: 24px ızgara, 1.75 çizgi). Ortak `Icon` bileşenindeki
 * ikonlar (arama, kapat, ok…) orada kalır; yalnız burada eksik olanlar tanımlıdır.
 */
const paths = {
  heart: "M12 20.5s-7.5-4.6-9.2-9.4C1.7 7.700 3.700 4.500 7 4.500c2 0 3.600 1.100 5 3 1.400-1.900 3-3 5-3 3.300 0 5.300 3.200 4.200 6.600-1.700 4.800-9.200 9.400-9.200 9.400Z",
  comment: "M4 5.500h16a1 1 0 0 1 1 1v9.500a1 1 0 0 1-1 1h-8l-5 3.500V17H4a1 1 0 0 1-1-1V6.500a1 1 0 0 1 1-1Z",
  repost: "M4 11V9a3 3 0 0 1 3-3h11m0 0-3-3m3 3-3 3M20 13v2a3 3 0 0 1-3 3H6m0 0 3 3m-3-3 3-3",
  pencil: "M4 20h4L19 9a2.800 2.800 0 0 0-4-4L4 16v4ZM13.500 6.500l4 4",
  trash: "M4 7h16M10 11v6M14 11v6M6 7l1 12a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-12M9 7V4h6v3",
  flag: "M5 21V4m0 0h11l-2 4 2 4H5",
  image: "M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Zm0 11 5-5 4 4 3-3 5 5M8.500 9.500h.01",
  send: "M21 3 3 10.500l7 2.500 2.500 7L21 3ZM10 13l11-10",
  plus: "M12 5v14M5 12h14",
  reply: "M10 8 4 13l6 5v-3.500c5 0 8.500 1 10 4.500 0-6-3.500-10-10-10V8Z",
  more: "M5 12h.01M12 12h.01M19 12h.01",
  forum: "M4 4h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H9l-4 3v-3H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm14 6h2a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-1v3l-4-3h-5a1 1 0 0 1-1-1v-1",
  clock: "M12 7v5l3 2M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z",
  list: "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"
} as const;

export type CommunityIconName = keyof typeof paths;

type Props = Omit<SVGProps<SVGSVGElement>, "name"> & { name: CommunityIconName; filled?: boolean };

export function CommunityIcon({ name, filled = false, className, ...props }: Props) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
