import Image from "next/image";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import {
  legalPages,
  platformPages,
  type PageEntry,
  type PageId
} from "@/shared/config/pages";

type LinkId = Exclude<PageId, "home">;

type LinkGroupProps = {
  heading: string;
  headingId: string;
  entries: readonly PageEntry[];
  label: (id: LinkId) => string;
};

function LinkGroup({ heading, headingId, entries, label }: LinkGroupProps) {
  return (
    <nav aria-labelledby={headingId}>
      <h2
        id={headingId}
        className="font-display text-sm font-semibold text-text-primary"
      >
        {heading}
      </h2>
      <ul className="mt-3 space-y-2">
        {entries.map((entry) => (
          <li key={entry.id}>
            <Link
              href={entry.href}
              className="rounded-control text-sm text-text-secondary underline-offset-4 transition-colors hover:text-text-primary hover:underline"
            >
              {label(entry.id as LinkId)}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/**
 * Sade, dengeli footer: marka + kısa açıklama, "Platform" ve "Yasal" bağlantı grupları, telif satırı.
 * Bağlantılar `shared/config/pages.ts` içindeki gerçek rotalardan gelir; `href="#"` kullanılmaz.
 */
export async function Footer() {
  const t = await getTranslations("Footer");
  const year = new Date().getFullYear();
  const label = (id: LinkId) => t(`links.${id}`);

  return (
    <footer
      aria-label={t("label")}
      className="border-t border-border bg-surface px-4 py-8 sm:px-6 lg:px-10"
    >
      <div className="mx-auto w-full max-w-5xl">
        <div className="grid gap-8 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:gap-12">
          <div className="max-w-sm">
            <Image
              src="/assets/brand/ltz-wordmark.png"
              alt="LobbyTwoZero"
              width={480}
              height={147}
              sizes="112px"
              translate="no"
              className="h-auto w-28"
            />
            <p className="mt-3 text-sm text-text-secondary">{t("tagline")}</p>
          </div>

          <LinkGroup
            heading={t("platform")}
            headingId="footer-platform"
            entries={platformPages}
            label={label}
          />
          <LinkGroup
            heading={t("legal")}
            headingId="footer-legal"
            entries={legalPages}
            label={label}
          />
        </div>

        <p className="mt-8 border-t border-border pt-5 text-sm text-text-secondary">
          {t("copyright", { year })}
        </p>
      </div>
    </footer>
  );
}
