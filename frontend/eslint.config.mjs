import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

// Ortak sayfa layout'u (frontend/frontend-layout.md §1): genişlik ve boşluk yalnız AppShell'den gelir.
const pageContainerRule = {
  name: "@/shared/components/layout/page-container",
  message: "PageContainer yalnız AppShell içinde bir kez kullanılır. Sayfa kendi kabını kurmaz: frontend/frontend-layout.md §1."
};
const appShellRule = {
  name: "@/shared/components/layout/app-shell",
  message: "AppShell yalnız (public) ve (dashboard) grup layout'larında mount edilir: frontend/frontend-layout.md §1."
};
const nativeControlsRule = {
  selector: "JSXOpeningElement[name.name='select']",
  message: "Yerel <select> kullanılmaz; ortak Select/MultiSelect kullan: frontend/frontend-layout.md §3."
};

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    files: ["src/**/*.{ts,tsx}"],
    ignores: ["src/shared/components/layout/**"],
    rules: {
      "no-restricted-imports": ["error", { paths: [pageContainerRule, appShellRule] }],
      "no-restricted-syntax": ["error", nativeControlsRule]
    }
  },
  {
    // Kabuğu mount eden tek yerler: grup layout'ları. PageContainer burada da yasak (AppShell kendisi kurar).
    files: ["src/app/*/(public)/layout.tsx", "src/app/*/(dashboard)/layout.tsx"],
    rules: { "no-restricted-imports": ["error", { paths: [pageContainerRule] }] }
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "coverage/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
