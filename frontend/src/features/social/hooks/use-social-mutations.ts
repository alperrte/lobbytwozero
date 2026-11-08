"use client";

import { socialActions } from "../services/social-service";
import type { UserId } from "../types/social.types";
import { useSocialAction } from "./use-social-action";

/**
 * Bileşenlerin kullandığı sosyal işlemler. Her işlem kendi kimliğiyle (`accept:<id>` gibi) yürür; böylece yalnız ilgili satırın
 * düğmesi "bekliyor" görünür, diğer satırlar da çift gönderime karşı kilitlenir (`busy`).
 */
export function useSocialMutations() {
  const { pendingId, busy, run } = useSocialAction();
  return {
    pendingId,
    busy,
    accept: (requestId: string) => run(`accept:${requestId}`, () => socialActions.acceptRequest(requestId)),
    reject: (requestId: string) => run(`reject:${requestId}`, () => socialActions.rejectRequest(requestId)),
    cancel: (requestId: string) => run(`cancel:${requestId}`, () => socialActions.cancelRequest(requestId)),
    removeFriend: (userId: UserId) => run(`remove:${userId}`, () => socialActions.removeFriend(userId)),
    block: (userId: UserId) => run(`block:${userId}`, () => socialActions.blockUser(userId)),
    unblock: (userId: UserId) => run(`unblock:${userId}`, () => socialActions.unblockUser(userId)),
    sendByCode: (code: string) => run("send-code", () => socialActions.sendRequestByCode(code)),
    createInviteCode: () => run("invite-create", () => socialActions.createInviteCode()),
    regenerateInviteCode: () => run("invite-regenerate", () => socialActions.regenerateInviteCode())
  };
}
