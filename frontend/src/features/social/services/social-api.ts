import { normalizeInviteCode } from "../schemas/invite-code";
import type {
  Block,
  CursorPage,
  Friend,
  FriendRequest,
  InviteCode,
  PageQuery,
  UserId
} from "../types/social.types";
import { socialRequest } from "./social-http";

/**
 * social-service istemcisi (`docs/api.md` → `## SOCIAL`). Tüm yollar Gateway'in `/api/v1/social/**` önekindedir.
 * Kimlik yalnız oturum çerezinden (Gateway → Bearer) gelir; gövdeye kullanıcı kimliği yazılmaz.
 * Path parametreleri `encodeURIComponent` ile kaçırılır (sunucudan gelen kimlik de olsa yol enjeksiyonuna kapalı).
 */
const BASE = "/api/v1/social";

function page(path: string, query: PageQuery = {}): `/${string}` {
  const params = new URLSearchParams();
  if (query.limit !== undefined) {
    params.set("limit", String(query.limit));
  }
  if (query.cursor) {
    params.set("cursor", query.cursor);
  }
  const search = params.toString();
  return `${BASE}${path}${search ? `?${search}` : ""}` as `/${string}`;
}

const id = (value: string) => encodeURIComponent(value);

export const socialApi = {
  listFriends(query?: PageQuery, signal?: AbortSignal) {
    return socialRequest<CursorPage<Friend>>(page("/friends", query), { signal });
  },

  removeFriend(friendId: UserId) {
    return socialRequest<void>(`${BASE}/friends/${id(friendId)}`, { method: "DELETE" });
  },

  listIncomingRequests(query?: PageQuery, signal?: AbortSignal) {
    return socialRequest<CursorPage<FriendRequest>>(page("/friend-requests/incoming", query), { signal });
  },

  listOutgoingRequests(query?: PageQuery, signal?: AbortSignal) {
    return socialRequest<CursorPage<FriendRequest>>(page("/friend-requests/outgoing", query), { signal });
  },

  acceptRequest(requestId: string) {
    return socialRequest<FriendRequest>(`${BASE}/friend-requests/${id(requestId)}/accept`, { method: "POST" });
  },

  rejectRequest(requestId: string) {
    return socialRequest<FriendRequest>(`${BASE}/friend-requests/${id(requestId)}/reject`, { method: "POST" });
  },

  cancelRequest(requestId: string) {
    return socialRequest<FriendRequest>(`${BASE}/friend-requests/${id(requestId)}/cancel`, { method: "POST" });
  },

  sendRequestByCode(code: string) {
    return socialRequest<FriendRequest>(`${BASE}/friend-requests/by-code`, {
      method: "POST",
      body: { code: normalizeInviteCode(code) }
    });
  },

  listBlocks(query?: PageQuery, signal?: AbortSignal) {
    return socialRequest<CursorPage<Block>>(page("/blocks", query), { signal });
  },

  blockUser(userId: UserId) {
    return socialRequest<Block>(`${BASE}/blocks`, { method: "POST", body: { blockedUserId: userId } });
  },

  unblockUser(userId: UserId) {
    return socialRequest<void>(`${BASE}/blocks/${id(userId)}`, { method: "DELETE" });
  },

  getInviteCode(signal?: AbortSignal) {
    return socialRequest<InviteCode>(`${BASE}/invite-code`, { signal });
  },

  /** Etkin kod varsa onu (200), yoksa yenisini (201) döner. */
  createInviteCode() {
    return socialRequest<InviteCode>(`${BASE}/invite-code`, { method: "POST" });
  },

  revokeInviteCode() {
    return socialRequest<void>(`${BASE}/invite-code`, { method: "DELETE" });
  }
};

export type SocialSource = typeof socialApi;
