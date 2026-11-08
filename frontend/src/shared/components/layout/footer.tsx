import Image from "next/image";
import { getTranslations } from "next-intl/server";
import { SocialIcon } from "@/shared/components/ui/social-icons";
import { footerGroups, type FooterEntry } from "@/shared/config/footer-navigation";
import { legalPages } from "@/shared/config/pages";
import { isLiveSocialLink, socialLinks } from "@/shared/config/social";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";

const linkClass =
  "inline-block rounded-control py-0.5 text-sm text-text-secondary underline decoration-transparent underline-offset-4 transition-colors duration-200 hover:text-text-primary hover:decoration-primary";

/** Henüz yayınlanmamış özellik: adı görünür, bağlantı değildir (tıklanamaz, odaklanamaz, hover yok). */
const inactiveClass = "inline-block cursor-default py-0.5 text-sm text-text-secondary";

/**
 * Tam genişlikli alt footer. AppShell onu sidebar ve ana içerik satırının ALTINA, bağımsız bir satır
 * olarak yerleştirir; belge akışında kalır ve yüksekliği içeriğine göre oluşur.
 *
 * İçerik en çok 1440px genişliğinde ortalanmış tek bir kompozisyondur. Masaüstünde beş blok:
 * solda marka (logo, açıklama, sosyal ikonlar), sağda Keşfet, Topluluk, LTZ Performance ve Destek.
 * Gruplar `shared/config/footer-navigation.ts` içinden gelir; ürün öğeleri sidebar ile aynı kaynağı
 * kullanır. Rotası olmayan öğe düz metin olarak görünür (sahte link ve "Yakında" rozeti yoktur).
 * Alt satırda telif ve yasal bağlantılar yer alır; footer'ın en altında statik kırmızı çizgi durur.
 */
export async function Footer() {
  const t = await getTranslations("Footer");
  const nav = await getTranslations("Navigation");
  const year = new Date().getFullYear();

  const entryLabel = (entry: FooterEntry) =>
    entry.source === "navigation" ? nav(entry.id) : t(`links.${entry.id}`);

  return (
    <footer aria-label={t("label")} className="relative border-t border-border bg-surface">
      <div className="mx-auto w-full max-w-[90rem] px-5 sm:px-8 lg:px-12">
        <div className="grid grid-cols-2 gap-x-6 gap-y-8 py-8 sm:py-10 md:grid-cols-4 lg:grid-cols-[minmax(0,1.9fr)_repeat(4,minmax(0,1fr))] lg:gap-x-10">
          <div className="col-span-2 md:col-span-4 lg:col-span-1">
            <Image
              src="/assets/brand/ltz-wordmark.png"
              alt="LobbyTwoZero"
              width={480}
              height={147}
              sizes="136px"
              translate="no"
              className="h-auto w-[8.5rem] object-contain"
            />
            <p className="mt-4 max-w-[27.5rem] text-[0.8125rem] leading-relaxed text-text-secondary sm:text-sm">
              {t("tagline")}
            </p>
            <ul aria-label={t("socialLabel")} className="mt-5 flex items-center gap-1">
              {socialLinks.map((link) => {
                const name = t(`social.${link.id}`);
                const frame =
                  "inline-flex size-9 items-center justify-center rounded-control text-text-secondary transition-colors duration-200";

                return (
                  <li key={link.id}>
                    {isLiveSocialLink(link) ? (
                      <a
                        href={link.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={name}
                        className={cn(frame, "hover:bg-surface-elevated hover:text-brand-text")}
                      >
                        <SocialIcon id={link.id} className="size-[18px]" />
                      </a>
                    ) : (
                      <span
                        role="img"
                        aria-label={t("socialSoon", { name })}
                        title={t("socialSoon", { name })}
                        className={cn(frame, "cursor-default")}
                      >
                        <SocialIcon id={link.id} className="size-[18px]" />
                      </span>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>

          {footerGroups.map((group) => {
            const headingId = `footer-group-${group.id}`;

            return (
              <nav key={group.id} aria-labelledby={headingId} className="min-w-0">
                <h2
                  id={headingId}
                  className="font-display text-sm font-semibold text-text-primary lg:whitespace-nowrap"
                >
                  {t(`groups.${group.id}`)}
                </h2>
                <ul className="mt-3 space-y-2">
                  {group.entries.map((entry) => (
                    <li key={`${entry.source}-${entry.id}`}>
                      {entry.href ? (
                        <Link href={entry.href} className={linkClass}>
                          {entryLabel(entry)}
                        </Link>
                      ) : (
                        <span className={inactiveClass}>{entryLabel(entry)}</span>
                      )}
                    </li>
                  ))}
                </ul>
              </nav>
            );
          })}
        </div>

        <div className="flex flex-col gap-3 border-t border-border py-5 md:flex-row md:items-center md:justify-between md:gap-6">
          <p className="text-[0.8125rem] text-text-secondary">{t("copyright", { year })}</p>
          <nav aria-label={t("legalNav")}>
            <ul className="flex flex-wrap gap-x-5 gap-y-1">
              {legalPages.map((entry) => (
                <li key={entry.id}>
                  <Link href={entry.href} className={cn(linkClass, "text-[0.8125rem]")}>
                    {t(`links.${entry.id}`)}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </div>
      {/* LTZ imzası: alt kenarda tam genişlikte, statik kırmızı → pembe → kırmızı çizgi (animasyon ve glow yok). */}
      <span
        aria-hidden="true"
        data-footer-accent
        className="pointer-events-none absolute inset-x-0 bottom-0 h-0.5 bg-linear-to-r from-primary via-secondary to-primary"
      />
    </footer>
  );
}
