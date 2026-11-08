import { useTranslations } from "next-intl";
import { EdgeSnake } from "@/shared/components/layout/edge-snake";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AppPathname } from "@/shared/lib/i18n/routing";

const LEGAL_LINKS: ReadonlyArray<{ href: AppPathname; key: "privacy" | "terms" | "cookies" | "kvkk" }> = [
  { href: "/legal/privacy", key: "privacy" },
  { href: "/legal/terms", key: "terms" },
  { href: "/legal/cookies", key: "cookies" },
  { href: "/legal/kvkk", key: "kvkk" }
];

/**
 * Auth sayfalarının alt çubuğu: üstteki navbar ile aynı yüzen kart dili (aynı genişlik, yarıçap ve sürekli dönen `EdgeSnake` ışık izi).
 * İçerik kısa olduğunda sahnenin en altında, uzun olduğunda doğal akışta durur (üst öğe `flex-col`, `main` büyür).
 * Kart yüzeyi video üzerinde de okunur kalır (opak `bg-surface`).
 */
export function AuthFooter() {
  const t = useTranslations("Auth.shell");
  const links = useTranslations("Footer.links");

  return (
    <div className="relative z-10 px-3 pb-3 sm:px-5">
      <footer className="relative mx-auto flex w-full max-w-[60rem] flex-wrap items-center justify-between gap-x-6 gap-y-2 rounded-navbar border border-border bg-surface px-4 py-3.5 text-xs text-text-secondary shadow-float sm:px-5">
        <EdgeSnake />
        <span>{t("copyright")}</span>
        <nav aria-label={t("legalNav")} className="flex flex-wrap gap-x-5 gap-y-1">
          {LEGAL_LINKS.map((link) => (
            <Link key={link.key} href={link.href} className="rounded-md py-0.5 hover:text-text-primary">
              {links(link.key)}
            </Link>
          ))}
        </nav>
      </footer>
    </div>
  );
}
