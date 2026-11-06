/**
 * Gerçekten yayınlanmış ve herkese açık rotalar. sitemap.xml yalnız bunları listeler.
 * (auth), (dashboard), (admin) ve kişisel alanlar buraya eklenmez.
 */
export const publicRoutes = [
  { path: "/", changeFrequency: "monthly", priority: 1 }
] as const;
