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
 * sağda kart. Sağ taraf semantik tokenlardan renklenir (açık/koyu tema). Sol panelin ARKASINDAKİ video
 * `(auth)/layout.tsx` içindeki `AuthBackdrop`'tur (sayfalar arası kesilmesin diye burada değil orada durur); bu dosyadaki
 * sol bölüm yalnız videonun üstündeki yazıdır ve video koyu perdeli olduğu için tema fark etmeksizin beyazdır.
 */
export function AuthShell({ variant, children }: AuthShellProps) {
  const t = useTranslations("Auth.shell");

  return (
    <div className="flex min-h-dvh flex-col text-text-primary">
      <header className="relative z-10 flex items-center justify-between gap-3 border-b border-border bg-background px-4 py-3 sm:px-8">
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
        <section aria-hidden="true" className="relative hidden lg:block">
          <div className="relative z-10 flex h-full flex-col justify-center px-12 py-16 text-white xl:px-20">
            <p className="text-xs font-semibold tracking-[0.22em] text-white/80 uppercase">
              {t(`${variant}.eyebrow`)}
            </p>
            <h2 className="mt-5 font-display text-5xl leading-[1.05] font-bold xl:text-6xl">
              {t(`${variant}.headlineStart`)}{" "}
              <span className="text-primary">{t(`${variant}.headlineAccent`)}</span>
            </h2>
            <p className="mt-6 max-w-md text-lg leading-relaxed text-white/85">
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

      <footer className="relative z-10 flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-t border-border bg-background px-4 py-4 text-xs text-text-secondary sm:px-8">
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
