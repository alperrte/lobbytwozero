"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n/navigation";
import type { AuthorCard } from "../types/review";
import { focusRing } from "./review-ui";

/**
 * Yazar satırı. Ad ve kullanıcı adı KULLANICI İÇERİĞİDİR: düz metin olarak çizilir. Kartı olmayan yazar (User yanıtında yok:
 * silinmiş, engelli, devre dışı ya da uç kesintisi) için "bir oyuncu" gösterilir ve profil bağlantısı VERİLMEZ; neden
 * ayrıştırılmaz. Çözülme bitmeden hiçbir şey çizilmez (yanlış "bir oyuncu" parlamasını önler).
 */
export function AuthorBadge({ card, resolved }: { card: AuthorCard | undefined; resolved: boolean }) {
  const t = useTranslations("Review");
  if (!resolved) return null;
  const name = card?.displayName?.trim() || card?.username?.trim() || null;
  if (!card || !name) return <span className="font-semibold text-text-primary">{t("card.anonymousAuthor")}</span>;
  return (
    <Link href={{ pathname: "/players/[userId]", params: { userId: card.userId } }} className={`inline-flex min-w-0 items-center gap-2 rounded-control font-semibold text-text-primary underline-offset-2 hover:underline ${focusRing}`}>
      {card.avatarPath ? (
        // eslint-disable-next-line @next/next/no-img-element -- Gateway'e göreli User medyası; boyutlar sabit.
        <img src={card.avatarPath} alt="" width={24} height={24} className="size-6 shrink-0 rounded-full border border-border object-cover" />
      ) : null}
      <span className="min-w-0 truncate">{name}</span>
    </Link>
  );
}
