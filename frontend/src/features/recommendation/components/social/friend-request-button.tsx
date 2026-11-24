"use client";

import { useTranslations } from "next-intl";
import { buttonClass } from "@/shared/components/ui/button-styles";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { useFriendRequest } from "../../hooks/use-social-recommendations";
import { InlineError } from "../recommendation-ui";

/**
 * Arkadaşlık isteği gönderir (Social Service). Arkadaşlık durumu sorgu ucu olmadığından durum isteğin sonucundan okunur;
 * başarı yalnız 201'de söylenir. Gelen istek varsa kullanıcı istekler sayfasına yönlendirilir.
 */
export function FriendRequestButton({ receiverId, name, compact = false }: { receiverId: string; name: string; compact?: boolean }) {
  const t = useTranslations("Recommendation.social");
  const common = useTranslations("Recommendation.common");
  const request = useFriendRequest(receiverId);
  const size = compact ? "sm" : "md";

  if (request.outcome === "INCOMING_FRIEND_REQUEST_PENDING") {
    return <div className="space-y-1.5" role="status">
      <p className="text-xs leading-5 text-text-secondary">{t("outcome.INCOMING_FRIEND_REQUEST_PENDING")}</p>
      <Link href="/friends/requests" className={buttonClass("secondary", size, "w-full")}>{t("openRequests")}</Link>
    </div>;
  }
  if (request.outcome) {
    return <p role="status" className="flex min-h-9 items-center justify-center gap-1.5 rounded-control border border-border px-3 text-sm text-text-secondary">
      {request.outcome === "SENT" || request.outcome === "ALREADY_FRIENDS" ? <Icon name="check" className="size-4 shrink-0 text-success" /> : <Icon name="info" className="size-4 shrink-0" />}
      {t(`outcome.${request.outcome}`)}
    </p>;
  }
  return <div className="space-y-1.5">
    <button type="button" className={buttonClass("primary", size, "w-full")} disabled={request.pending || request.cooldown > 0} aria-busy={request.pending}
      aria-label={t("addFriendLabel", { name })} onClick={() => void request.send()}>
      <Icon name="userPlus" className="size-4" />
      {request.pending ? common("working") : request.cooldown > 0 ? common("retryIn", { seconds: request.cooldown }) : t("addFriend")}
    </button>
    <InlineError error={request.error} />
  </div>;
}
