"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { useSocialMutations } from "../hooks/use-social-mutations";
import type { SocialPerson } from "../types/social.types";
import { useAnnounce } from "./social-announcer";
import { usePerson } from "./use-person";

export type PersonAction = { kind: "remove" | "block" | "unblock"; person: SocialPerson };

const copy = {
  remove: { title: "removeTitle", body: "removeBody", confirm: "removeConfirm", pending: "removing", done: "removed" },
  block: { title: "blockTitle", body: "blockBody", confirm: "blockConfirm", pending: "blocking", done: "blocked" },
  unblock: { title: "unblockTitle", body: "unblockBody", confirm: "unblockConfirm", pending: "unblocking", done: "unblocked" }
} as const;

/**
 * Geri alınması zor kişi işlemleri için onay penceresi: arkadaşlıktan çıkarma, engelleme, engeli kaldırma (web-rules §4).
 * Başarıda pencere kapanır ve sonuç duyurulur; hata olursa pencere açık kalır ve nedeni içinde gösterilir.
 */
export function PersonActionDialog({ action, onClose }: { action: PersonAction | null; onClose: () => void }) {
  const t = useTranslations("Social");
  const announce = useAnnounce();
  const mutations = useSocialMutations();
  const describe = usePerson();
  const [error, setError] = useState<string | null>(null);
  // Kapanış animasyonu sırasında içerik boşalmasın diye son eylem tutulur.
  const [shown, setShown] = useState<PersonAction | null>(action);
  if (action && action !== shown) {
    setShown(action);
    setError(null);
  }

  const current = action ?? shown;
  if (!current) {
    return null;
  }
  const text = copy[current.kind];
  const { profile } = current.person;
  const person = describe(profile, current.kind === "unblock" ? "blocked" : "default");

  async function confirm() {
    if (!action) {
      return;
    }
    const userId = action.person.profile.userId;
    const outcome =
      action.kind === "remove"
        ? await mutations.removeFriend(userId)
        : action.kind === "block"
          ? await mutations.block(userId)
          : await mutations.unblock(userId);
    if (outcome.ok) {
      announce(t(`announce.${text.done}`, { name: person.name }));
      onClose();
    } else {
      setError(t(`errors.${outcome.error}`));
    }
  }

  return (
    <ConfirmDialog
      open={action !== null}
      title={t(`dialogs.${text.title}`)}
      description={t(`dialogs.${text.body}`, { name: person.name })}
      confirmLabel={t(`dialogs.${text.confirm}`)}
      pendingLabel={t(`dialogs.${text.pending}`)}
      cancelLabel={t("dialogs.cancel")}
      closeLabel={t("dialogs.close")}
      pending={mutations.busy}
      error={error}
      onConfirm={confirm}
      onClose={onClose}
    >
      <div className="flex items-center gap-4">
        <Avatar seed={profile.avatarSeed} imageUrl={person.avatarUrl} size="md" ring />
        <div className="min-w-0">
          <p className="truncate font-display text-lg font-semibold">{person.name}</p>
          {person.handle ? <p className="truncate text-sm text-text-secondary">{person.handle}</p> : null}
        </div>
      </div>
    </ConfirmDialog>
  );
}
