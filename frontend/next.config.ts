import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin(
  "./src/shared/lib/i18n/request.ts"
);

const nextConfig: NextConfig = {
  reactStrictMode: true,
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
