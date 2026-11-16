/** Profil düzenleme sayfasının bölümleri; kimlikleri sayfadaki bölüm `id`leridir (bağlantılar `#<id>` ile açar). */
export const EDIT_SECTIONS = ["images", "info", "prefs"] as const;
export type EditSection = (typeof EDIT_SECTIONS)[number];

/** Düzenleme sayfasındaki bir bölüme giden bağlantı (tipli yol + parça). */
export function editHref(section: EditSection) {
  return { pathname: "/profile/edit", hash: section } as const;
}
