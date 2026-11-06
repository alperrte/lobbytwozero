import Image from "next/image";
import { getTranslations } from "next-intl/server";

export async function Footer() {
  const t = await getTranslations("Footer");
  const shell = await getTranslations("Shell");
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-border bg-surface px-4 py-5 sm:px-6 lg:px-10">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <Image
            src="/assets/brand/ltz-mark.png"
            alt=""
            width={256}
            height={181}
            sizes="32px"
            className="h-auto w-8 shrink-0"
          />
          <p className="max-w-md text-sm text-text-secondary">{t("tagline")}</p>
        </div>
        <p className="text-sm text-text-secondary">
          {t("copyright", { year, brand: shell("brand") })}
        </p>
      </div>
    </footer>
  );
}
