import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";
import { assertSafeApiBaseUrl, securityHeaders } from "./src/shared/config/security-headers";

const withNextIntl = createNextIntlPlugin(
  "./src/shared/lib/i18n/request.ts"
);

const isProduction = process.env.NODE_ENV === "production";

// Üretim derlemesinde http (yerel olmayan) API adresi derlemeyi durdurur; boş değer göreli yol demektir.
assertSafeApiBaseUrl(process.env.NEXT_PUBLIC_API_BASE_URL, isProduction);

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Güvenlik başlıkları (docs/SECURITY.md §11): frame-ancestors, nosniff, Referrer-Policy, Permissions-Policy, HSTS (üretim).
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders(isProduction) }];
  },
  // Tailwind CSS v4: Turbopack loader (PostCSS gerekmez). Renk/ölçü tokenları src/app/globals.css içindedir.
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css"
      }
    }
  }
};

export default withNextIntl(nextConfig);
