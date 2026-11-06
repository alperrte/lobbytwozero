import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { BrandLogo } from "@/shared/components/layout/brand-logo";
import { LanguageMenu } from "@/shared/components/layout/language-menu";
import { ThemeSwitch } from "@/shared/components/ui/theme-switch";
import { Link } from "@/shared/lib/i18n/navigation";
import { AuthIcon } from "./auth-icons";

type AuthShellProps = {
  /** Sol panel başlığı için `Auth.shell.<ad>` anahtar öneki (ör. `login`, `register`). */
  variant: "login" | "register";
  children: ReactNode;
};

/**
 * Giriş/kayıt sayfalarının ortak çerçevesi: kabuksuz (navbar/sidebar yok), solda marka paneli (yalnız lg+),
 * sağda kart. Renkler yalnız semantik tokenlardan gelir; açık/koyu tema ikisinde de okunur. Sol panel şimdilik
 * CSS ile çizilir; resmî görsel hazır olunca yalnız bu dosya değişir.
 */
export function AuthShell({ variant, children }: AuthShellProps) {
  const t = useTranslations("Auth.shell");

  return (
    <div className="flex min-h-dvh flex-col bg-background text-text-primary">
      <header className="flex items-center justify-between gap-3 border-b border-border px-4 py-3 sm:px-8">
        <Link href="/" aria-label={t("brand")} className="rounded-control">
          <BrandLogo
            wordmarkClassName="h-auto w-24 object-contain sm:w-28"
            markClassName="hidden"
          />
        </Link>
        <div className="flex items-center gap-1 sm:gap-2">
          <Link
            href="/"
            className="inline-flex min-h-10 items-center gap-2 rounded-control px-2 text-sm font-medium text-text-primary transition-colors duration-150 hover:text-brand-text motion-reduce:transition-none"
          >
            <AuthIcon name="back" className="size-4 text-brand-text" />
            {t("back")}
          </Link>
          <LanguageMenu />
          <ThemeSwitch />
        </div>
      </header>

      <main className="grid flex-1 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <section
          aria-hidden="true"
          className="relative hidden overflow-hidden border-e border-border lg:block"
        >
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_72%_38%,color-mix(in_srgb,var(--color-primary)_45%,transparent)_0%,transparent_58%)]" />
          <div className="absolute inset-0 bg-[linear-gradient(115deg,transparent_42%,color-mix(in_srgb,var(--color-primary)_22%,transparent)_43%,transparent_44%,transparent_58%,color-mix(in_srgb,var(--color-secondary)_16%,transparent)_59%,transparent_60%)]" />
          <div className="absolute inset-x-0 bottom-0 h-1/2 bg-[linear-gradient(to_top,var(--color-background),transparent)]" />
          <div className="relative flex h-full flex-col justify-center px-12 py-16 xl:px-20">
            <p className="text-xs font-semibold tracking-[0.22em] text-brand-text uppercase">
              {t(`${variant}.eyebrow`)}
            </p>
            <h2 className="mt-5 font-display text-5xl leading-[1.05] font-bold xl:text-6xl">
              {t(`${variant}.headlineStart`)}{" "}
              <span className="text-brand-text">{t(`${variant}.headlineAccent`)}</span>
            </h2>
            <p className="mt-6 max-w-md text-lg leading-relaxed text-text-secondary">
              {t(`${variant}.subline`)}
            </p>
          </div>
        </section>

        <section className="flex items-center justify-center px-4 py-8 sm:px-8 sm:py-12">
          <div className="w-full max-w-md rounded-panel border border-border bg-surface p-6 shadow-popover sm:p-8">
            {children}
          </div>
        </section>
      </main>

      <footer className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-border px-4 py-4 text-xs text-text-secondary sm:px-8">
        <span>{t("copyright")}</span>
        <nav aria-label={t("legalNav")} className="flex gap-5">
          <Link href="/legal/privacy" className="hover:text-text-primary">
            {t("privacy")}
          </Link>
          <Link href="/legal/terms" className="hover:text-text-primary">
            {t("terms")}
          </Link>
        </nav>
      </footer>
    </div>
  );
}
