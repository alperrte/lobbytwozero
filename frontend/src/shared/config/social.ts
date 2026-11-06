/**
 * Footer sosyal medya bağlantıları — tek kaynak.
 * `href` boş bırakıldığında ikon görünür ama tıklanamaz (kırık/uydurma bağlantı üretilmez).
 * Gerçek hesap adresi hazır olduğunda yalnız ilgili `href` doldurulur (https ile başlamalı).
 */
export type SocialId = "x" | "instagram" | "linkedin" | "github";

export type SocialLink = {
  id: SocialId;
  /** Hesap adresi; henüz yoksa boş bırakılır. */
  href: string;
};

export const socialLinks: readonly SocialLink[] = [
  { id: "x", href: "" },
  { id: "instagram", href: "" },
  { id: "linkedin", href: "" },
  { id: "github", href: "" }
];

/** Yalnız güvenli (https) dış bağlantılar etkin sayılır. */
export function isLiveSocialLink(link: SocialLink): boolean {
  return link.href.startsWith("https://");
}
