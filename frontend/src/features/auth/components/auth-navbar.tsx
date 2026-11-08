"use client";

import { useTranslations } from "next-intl";
import { BrandLogo } from "@/shared/components/layout/brand-logo";
import { EdgeSnake } from "@/shared/components/layout/edge-snake";
import { LanguageMenu } from "@/shared/components/layout/language-menu";
import { ThemeSwitch } from "@/shared/components/ui/theme-switch";
import { Link, usePathname } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";

const PUBLIC_LINKS: ReadonlyArray<{ href: AppPathname; namespace: "Navigation" | "Footer.links"; key: string }> = [
  { href: "/", namespace: "Navigation", key: "home" },
  { href: "/games", namespace: "Navigation", key: "games" },
  { href: "/about", namespace: "Footer.links", key: "about" },
  { href: "/faq", namespace: "Footer.links", key: "faq" }
];

const linkClass =
  "inline-flex min-h-10 items-center rounded-xl px-3 text-sm font-medium text-text-secondary transition-colors duration-150 hover:text-text-primary motion-reduce:transition-none";

/**
 * Auth sayfalarının yüzen üst çubuğu. Ana uygulamanın navbar'ıyla aynı aileden: ortalanmış, kenarlardan ayrık,
 * `rounded-navbar`, ince kenarlık, `shadow-float` ve çerçevesinde sürekli dönen `EdgeSnake` ışık izi. Ana navbar'ın sidebar, bildirim ve oturum
 * bağımlılıkları yoktur; yalnız herkese açık sayfalar, dil, tema ve giriş/kayıt gezinmesi bulunur.
 */
export function AuthNavbar() {
  const t = useTranslations("Auth.shell");
  const nav = useTranslations("Navigation");
  const footer = useTranslations("Footer.links");
  const shell = useTranslations("Shell");
  const pathname = usePathname();
  const onLogin = pathname === "/login";
  const onRegister = pathname === "/register";

  return (
    <div className="auth-intro-top relative z-20 flex justify-center px-3 pt-3 sm:px-5">
      <header
        aria-label={shell("navbarLabel")}
        className="relative flex h-header w-full max-w-[60rem] items-center gap-2 rounded-navbar border border-border bg-surface px-3 shadow-float sm:gap-3 sm:px-4"
      >
        <EdgeSnake />
        <Link href="/" aria-label={t("brand")} className="rounded-xl">
          <BrandLogo wordmarkClassName="h-auto w-24 object-contain sm:w-28" markClassName="hidden" />
        </Link>

        <nav aria-label={t("navLabel")} className="ms-2 hidden items-center gap-0.5 md:flex">
          {PUBLIC_LINKS.map((link) => (
            <Link key={link.href} href={link.href} className={linkClass}>
              {link.namespace === "Navigation" ? nav(link.key as "home") : footer(link.key as "about")}
            </Link>
          ))}
        </nav>

        <div className="ms-auto flex shrink-0 items-center gap-0.5 sm:gap-1.5">
          <LanguageMenu />
          <ThemeSwitch />
          <Link
            href="/login"
            aria-current={onLogin ? "page" : undefined}
            className={`${linkClass} ${onLogin ? "hidden sm:inline-flex" : ""} text-text-primary`}
          >
            {t("navLogin")}
          </Link>
          <Link
            href="/register"
            aria-current={onRegister ? "page" : undefined}
            className={`inline-flex min-h-10 items-center rounded-xl bg-primary px-4 text-sm font-semibold text-primary-foreground transition-colors duration-150 hover:bg-primary/90 motion-reduce:transition-none ${
              onRegister ? "hidden sm:inline-flex" : ""
            }`}
          >
            {t("navRegister")}
          </Link>
        </div>
      </header>
    </div>
  );
}
