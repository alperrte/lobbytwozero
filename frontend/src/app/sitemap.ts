import type { MetadataRoute } from "next";
import { publicRoutes } from "@/shared/config/public-routes";
import { getSiteUrl, isIndexable } from "@/shared/config/site";

export default function sitemap(): MetadataRoute.Sitemap {
  const siteUrl = getSiteUrl();

  // Mutlak URL gerekir; doğrulanmış site kökü yoksa domain uydurmak yerine boş döner.
  if (!isIndexable(siteUrl) || !siteUrl) {
    return [];
  }

  return publicRoutes.map((route) => ({
    url: new URL(route.path, siteUrl).toString(),
    changeFrequency: route.changeFrequency,
    priority: route.priority
  }));
}
