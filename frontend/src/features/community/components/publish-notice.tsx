"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { cn } from "@/shared/utils/cn";
import { dismissFlash, FADE_MS, FLASH_MS, usePublishState, type PublishFlash } from "../services/publish-flow";

/**
 * "Gönderin paylaşıldı." bildirimi: görünür olduktan 2 saniye sonra yumuşak geçişle kendiliğinden kaybolur. Bu sürede
 * "Gönderiyi aç" ve "Kapat" kullanılabilir. Her yeni başarılı paylaşım (`flash.at`) bileşeni yeniden kurar ve süreyi baştan
 * başlatır; bileşen kapanırken zamanlayıcılar temizlenir. Süre dolunca depodaki kayıt da silinir (bkz. `publish-flow`), bu
 * yüzden bildirim hiçbir durumda (fare/odak üzerindeyken, başka sayfaya gidip dönünce) kalıcı kalmaz.
 */
export function PublishNotice() {
  const { flash } = usePublishState();
  return flash ? <NoticeBody key={flash.at} flash={flash} /> : null;
}

function NoticeBody({ flash }: { flash: PublishFlash }) {
  const t = useTranslations("Community");
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    const remaining = Math.max(0, FLASH_MS - (Date.now() - flash.at));
    const timer = setTimeout(() => setLeaving(true), remaining);
    return () => clearTimeout(timer);
  }, [flash.at]);

  useEffect(() => {
    if (!leaving) return;
    const timer = setTimeout(() => dismissFlash(flash.at), FADE_MS);
    return () => clearTimeout(timer);
  }, [leaving, flash.at]);

  return (
    <div
      role="status"
      className={cn(
        "mb-5 flex flex-wrap items-center gap-x-4 gap-y-1 rounded-control border border-success/60 bg-success/10 px-4 py-2 text-sm text-text-primary transition-[opacity,transform] duration-300 ease-out motion-reduce:transition-none",
        leaving && "-translate-y-1 opacity-0"
      )}
    >
      <Icon name="check" className="size-4 shrink-0 text-success" />
      <span className="font-medium">{t("feed.published")}</span>
      <Link
        href={{ pathname: "/community/posts/[postId]", params: { postId: flash.postId } }}
        className="inline-flex min-h-11 items-center font-semibold text-brand-text underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
      >
        {t("feed.openPublished")}
      </Link>
      <button
        type="button"
        className="ms-auto inline-flex min-h-11 items-center px-2 text-text-secondary hover:text-text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
        onClick={() => setLeaving(true)}
      >
        {t("state.close")}
      </button>
    </div>
  );
}
