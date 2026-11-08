"use client";

import { ApiHttpError } from "@/shared/lib/api/client";
import {
  getCounts,
  getInviteCode,
  listBlocks,
  listFriends,
  listIncomingRequests,
  listOutgoingRequests
} from "../services/social-service";
import type { InviteCode } from "../types/social.types";
import { usePagedSocialList } from "./use-paged-social-list";
import { useSocialResource } from "./use-social-resource";

export function useFriends(pageSize = 12) {
  return usePagedSocialList(listFriends, pageSize);
}

export function useIncomingRequests(pageSize = 10) {
  return usePagedSocialList(listIncomingRequests, pageSize);
}

export function useOutgoingRequests(pageSize = 10) {
  return usePagedSocialList(listOutgoingRequests, pageSize);
}

export function useBlocks(pageSize = 20) {
  return usePagedSocialList(listBlocks, pageSize);
}

export function useSocialCounts() {
  return useSocialResource(getCounts);
}

/** Etkin davet kodu yoksa sunucu 404 döner; bu hata değil "kod yok" durumudur (`null`). */
async function loadInviteCode(signal: AbortSignal): Promise<InviteCode | null> {
  try {
    return await getInviteCode(signal);
  } catch (error) {
    if (error instanceof ApiHttpError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

export function useInviteCode() {
  return useSocialResource(loadInviteCode);
}
