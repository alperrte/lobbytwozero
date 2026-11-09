"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Avatar } from "@/shared/components/ui/avatar";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { useSocialMutations } from "../hooks/use-social-mutations";
import type { SocialPerson } from "../types/social.types";
import { DialogError } from "./dialog-error";
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
export function PersonActionDialog({ action, onClose }: Readonly<{ action: PersonAction | null; onClose: () => void }>) {
  const t = useTranslations("Social");
  const announce = useAnnounce();
  const mutations = useSocialMutations();
  const describe = usePerson();
  const [error, setError] = useState<string | null>(null);
  // Pencere başka bir kişi/eylem için açılınca önceki hata temizlenir.
  const [errorFor, setErrorFor] = useState<PersonAction | null>(action);
  if (action !== errorFor) {
    setErrorFor(action);
    setError(null);
  }

  if (!action) {
    return null;
  }
  const text = copy[action.kind];
  const { profile } = action.person;
  const person = describe(profile, action.kind === "unblock" ? "blocked" : "default");

  async function confirm() {
    if (!action) {
      return;
    }
    const run = { remove: mutations.removeFriend, block: mutations.block, unblock: mutations.unblock }[action.kind];
    const outcome = await run(action.person.profile.userId);
    if (outcome.ok) {
      announce(t(`announce.${text.done}`, { name: person.name }));
      onClose();
    } else {
      setError(t(`errors.${outcome.error}`));
    }
  }

  return (
    <ConfirmDialog
      key={`${action.kind}:${profile.userId}`}
      title={t(`dialogs.${text.title}`)}
      description={t(`dialogs.${text.body}`, { name: person.name })}
      confirmLabel={mutations.busy ? t(`dialogs.${text.pending}`) : t(`dialogs.${text.confirm}`)}
      cancelLabel={t("dialogs.cancel")}
      busy={mutations.busy}
      // Üçü de geri alınması zor: çıkarma/engelleme ilişkiyi bitirir, engeli kaldırma korumayı kaldırır. İlk odak "Vazgeç"te.
      tone="danger"
      onConfirm={confirm}
      onCancel={onClose}
    >
      <div className="flex items-center gap-4">
        <Avatar seed={profile.avatarSeed} imageUrl={person.avatarUrl} size="md" ring />
        <div className="min-w-0">
          <p className="truncate font-display text-lg font-semibold">{person.name}</p>
          {person.handle ? <p className="truncate text-sm text-text-secondary">{person.handle}</p> : null}
        </div>
      </div>
      {error ? <DialogError message={error} /> : null}
    </ConfirmDialog>
  );
}
