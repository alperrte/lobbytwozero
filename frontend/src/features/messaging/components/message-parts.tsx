"use client";

import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { cn } from "@/shared/utils/cn";
import type { Message, MessageType, StreamStatus, ThreadMessage } from "../types/messaging.types";

/** Liste satırı ve alıntı için tek satırlık düz metin özet (içerik HTML olarak yorumlanmaz). */
export function useMessageSnippet() {
  const t = useTranslations("Messaging.snippet");
  return (message: Pick<Message, "type" | "body" | "deleted">): string => {
    if (message.deleted) return t("deleted");
    const body = message.body?.replace(/\s+/g, " ").trim();
    return body || typeLabel(t, message.type);
  };
}

export function typeLabel(t: (key: "image" | "gif" | "voice" | "text") => string, type: MessageType | null): string {
  switch (type) {
    case "IMAGE":
      return t("image");
    case "GIF":
      return t("gif");
    case "VOICE":
      return t("voice");
    default:
      return t("text");
  }
}

/**
 * Gönderim durumu (web-rules §7.2: gönderiliyor / gönderildi / okundu / başarısız ayrı gösterilir). Durum yalnız renge
 * dayanmaz: ikon + ekran okuyucu metni birlikte verilir.
 */
export function DeliveryStatus({ message, className }: { message: ThreadMessage | Message; className?: string }) {
  const t = useTranslations("Messaging.status");
  const local = "local" in message ? message.local : undefined;
  if (local?.state === "sending") {
    return (
      <span className={cn("inline-flex items-center text-text-secondary", className)} title={t("sending")}>
        <Icon name="clock" className="size-3.5" />
        <span className="sr-only">{t("sending")}</span>
      </span>
    );
  }
  if (local?.state === "failed") {
    return (
      <span className={cn("inline-flex items-center gap-1 text-brand-text", className)}>
        <Icon name="info" className="size-3.5" />
        <span className="text-xs font-semibold">{t("failed")}</span>
      </span>
    );
  }
  if (message.readStatus === "READ") {
    return (
      <span className={cn("inline-flex items-center text-brand-text", className)} title={t("read")}>
        <Icon name="checkDouble" className="size-4" />
        <span className="sr-only">{t("read")}</span>
      </span>
    );
  }
  if (message.readStatus === "SENT") {
    return (
      <span className={cn("inline-flex items-center text-text-secondary", className)} title={t("sent")}>
        <Icon name="check" className="size-3.5" />
        <span className="sr-only">{t("sent")}</span>
      </span>
    );
  }
  return null;
}

/** Okunmamış rozeti: sunucu 100'de durur, arayüz `99+` gösterir. */
export function UnreadBadge({ count, label }: { count: number; label: string }) {
  if (count <= 0) return null;
  return (
    <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[0.6875rem] font-bold text-primary-foreground">
      <span aria-hidden="true">{count >= 100 ? "99+" : count}</span>
      <span className="sr-only">{label}</span>
    </span>
  );
}

/** Canlı akış göstergesi (web-rules §16: bağlantı durumu kullanıcıya gösterilir). */
export function ConnectionStatus({ status }: { status: StreamStatus }) {
  const t = useTranslations("Messaging.connection");
  const tone =
    status === "live" ? "bg-success" : status === "offline" ? "bg-primary" : "bg-warning motion-safe:animate-pulse";
  return (
    <span role="status" aria-live="polite" className="inline-flex items-center gap-1.5 text-xs font-medium text-text-secondary">
      <span aria-hidden="true" className={cn("size-2 rounded-full", tone)} />
      {t(status)}
    </span>
  );
}
