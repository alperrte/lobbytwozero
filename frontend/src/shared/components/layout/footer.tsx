import Image from "next/image";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { SocialIcon } from "@/shared/components/ui/social-icons";
import {
  legalPages,
  pages,
  supportPages,
  type PageEntry,
  type PageId
} from "@/shared/config/pages";
import { isLiveSocialLink, socialLinks } from "@/shared/config/social";
import { cn } from "@/shared/utils/cn";

type LinkId = PageId;

const platformEntries = pages.filter((page) => page.section === "platform");

const linkClass =
  "rounded-control text-sm text-text-secondary underline-offset-4 transition-colors hover:text-text-primary hover:underline";

type LinkColumnProps = {
  heading: string;
  headingId: string;
  children: React.ReactNode;
};

function LinkColumn({ heading, headingId, children }: LinkColumnProps) {
  return (
    <nav aria-labelledby={headingId}>
      <h2 id={headingId} className="font-display text-sm font-semibold text-text-primary">
        {heading}
      </h2>
      <ul className="mt-4 space-y-3">{children}</ul>
    </nav>
  );
}

type PageLinksProps = {
  entries: readonly PageEntry[];
  label: (id: LinkId) => string;
};

function PageLinks({ entries, label }: PageLinksProps) {
  return entries.map((entry) => (
    <li key={entry.id}>
      <Link href={entry.href} className={linkClass}>
        {label(entry.id)}
      </Link>
    </li>
  ));
}

/**
 * Yüzen kart footer: solda marka + açıklama + sosyal ikonlar, sağda Platform / Destek / Yakında
 * sütunları; altta ayırıcı çizgi, telif ve yasal bağlantılar. Kart, navbar ile aynı yan boşluklara
 * hizalanır ve navbar'a yapışmaz. Bağlantılar `shared/config/pages.ts` içindeki gerçek rotalardan
 * gelir; sosyal ikonlar `shared/config/social.ts` içinde `href` doldurulana kadar tıklanamazdır.
 */
export async function Footer() {
  const t = await getTranslations("Footer");
  const nav = await getTranslations("Navigation");
  const year = new Date().getFullYear();
  const label = (id: LinkId) => t(`links.${id}`);

  return (
    <footer
      aria-label={t("label")}
      className="mx-3 mb-3 mt-10 sm:mx-5 sm:mb-5 sm:mt-12"
    >
      <div className="edge-light relative rounded-float border border-border bg-surface p-6 shadow-float sm:p-8 lg:p-10">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-16">
          <div className="max-w-sm">
            <Image
              src="/assets/brand/ltz-wordmark.png"
              alt="LobbyTwoZero"
              width={480}
              height={147}
              sizes="144px"
              translate="no"
              className="h-auto w-36"
            />
            <p className="mt-4 text-sm leading-relaxed text-text-secondary">
              {t("tagline")}
            </p>

            <ul aria-label={t("socialLabel")} className="mt-6 flex items-center gap-2.5">
              {socialLinks.map((link) => {
                const name = t(`social.${link.id}`);
                const frame =
                  "inline-flex size-10 items-center justify-center rounded-control border border-primary/40 text-text-secondary transition-colors";

                return (
                  <li key={link.id}>
                    {isLiveSocialLink(link) ? (
                      <a
                        href={link.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label={name}
                        className={cn(
                          frame,
                          "hover:border-primary hover:bg-primary/10 hover:text-text-primary"
                        )}
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

          <div className="grid grid-cols-2 gap-x-6 gap-y-10 sm:grid-cols-3">
            <LinkColumn heading={t("platform")} headingId="footer-platform">
              <PageLinks entries={platformEntries} label={label} />
            </LinkColumn>
            <LinkColumn heading={t("support")} headingId="footer-support">
              <PageLinks entries={supportPages} label={label} />
            </LinkColumn>
            <LinkColumn heading={t("soon")} headingId="footer-soon">
              <li>
                <span className="text-sm text-text-secondary">{nav("hardware")}</span>
              </li>
            </LinkColumn>
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-4 border-t border-border pt-6 lg:flex-row lg:items-center lg:justify-between">
          <p className="text-sm text-text-secondary">{t("copyright", { year })}</p>
          <nav aria-label={t("legalNav")}>
            <ul className="flex flex-wrap gap-x-6 gap-y-2">
              <PageLinks entries={legalPages} label={label} />
            </ul>
          </nav>
        </div>
      </div>
    </footer>
  );
}
