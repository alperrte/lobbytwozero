import type { MetadataRoute } from "next";
import { getSiteUrl, isIndexable } from "@/shared/config/site";

export default function robots(): MetadataRoute.Robots {
  const siteUrl = getSiteUrl();

  if (!isIndexable(siteUrl) || !siteUrl) {
    // Production site kökü doğrulanmadıkça hiçbir şey taranmaz.
    return { rules: { userAgent: "*", disallow: "/" } };
  }

  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: new URL("/sitemap.xml", siteUrl).toString()
  };
}
