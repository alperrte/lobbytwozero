import type {
  BlockView,
  CursorPage,
  FriendRequestView,
  FriendView,
  InviteCode,
  PageQuery,
  SocialCount,
  SocialCounts,
  SocialPerson,
  UserId
} from "../types/social.types";
import { socialApi as source } from "./social-api";
import { previewMutualFriends, previewPresence } from "./social-preview";
import { fetchProfileCards } from "./user-profiles";

/**
 * Sosyal özelliğin tek veri girişi: hook'lar yalnız buradaki fonksiyonları çağırır (component → hook → service).
 *
 * - Kayıtlar (arkadaş, istek, engel, davet kodu) social-service'ten gelir (`social-api.ts`).
 * - Ad, kullanıcı adı ve avatar user-service toplu kartından eklenir (`user-profiles.ts`).
 * - Durum, oynanan oyun ve ortak arkadaş sayısının kaynağı yoktur; önizleme verisidir (`social-preview.ts`).
 */

/**
 * Ekranda önizleme verisi bulunduğunu belirtir; başlıkta "Önizleme verisi" rozeti gösterilir (web-rules §1 "Dürüst veri").
 * Durum/oyun/ortak arkadaş gerçek kaynaklara bağlanınca `false` yapılır.
 */
export const SOCIAL_PREVIEW_DATA = true;

/** Sekme rozetleri için okunan en çok kayıt (servisin sayfa sınırı). Fazlası "50+" gösterilir. */
export const COUNT_LIMIT = 50;

/**
 * `withProfiles: false`: user-service'e hiç sorulmaz. Engellenenler listesinde kullanılır; user-service engel ilişkisi olan
 * kartları her zaman atladığından istek boş döner, atmak yalnız trafik olurdu.
 */
async function resolvePeople(
  userIds: readonly UserId[],
  signal?: AbortSignal,
  withProfiles = true
): Promise<Map<UserId, SocialPerson>> {
  const cards = withProfiles ? await fetchProfileCards(userIds, signal) : new Map<UserId, never>();
  const now = Date.now();
  return new Map(
    userIds.map((userId) => {
      const card = cards.get(userId);
      return [
        userId,
        {
          profile: {
            userId,
            displayName: card?.displayName ?? null,
            username: card?.username ?? null,
            avatarPath: card?.avatarPath ?? null,
            avatarSeed: userId,
            mutualFriends: previewMutualFriends(userId)
          },
          presence: previewPresence(userId, now)
        }
      ];
    })
  );
}

async function enrich<T, V>(
  page: CursorPage<T>,
  idOf: (item: T) => UserId,
  build: (item: T, person: SocialPerson) => V,
  signal?: AbortSignal,
  withProfiles = true
): Promise<CursorPage<V>> {
  const people = await resolvePeople(page.items.map(idOf), signal, withProfiles);
  return {
    items: page.items.map((item) => build(item, people.get(idOf(item))!)),
    nextCursor: page.nextCursor
  };
}

export async function listFriends(query?: PageQuery, signal?: AbortSignal): Promise<CursorPage<FriendView>> {
  const page = await source.listFriends(query, signal);
  return enrich(page, (friend) => friend.friendId, (friend, person) => ({ ...person, friend }), signal);
}

export async function listIncomingRequests(
  query?: PageQuery,
  signal?: AbortSignal
): Promise<CursorPage<FriendRequestView>> {
  const page = await source.listIncomingRequests(query, signal);
  return enrich(page, (request) => request.senderId, (request, person) => ({ ...person, request }), signal);
}

export async function listOutgoingRequests(
  query?: PageQuery,
  signal?: AbortSignal
): Promise<CursorPage<FriendRequestView>> {
  const page = await source.listOutgoingRequests(query, signal);
  return enrich(page, (request) => request.receiverId, (request, person) => ({ ...person, request }), signal);
}

export async function listBlocks(query?: PageQuery, signal?: AbortSignal): Promise<CursorPage<BlockView>> {
  const page = await source.listBlocks(query, signal);
  return enrich(page, (block) => block.blockedUserId, (block, person) => ({ ...person, block }), signal, false);
}

function count(page: CursorPage<unknown>): SocialCount {
  return { count: page.items.length, more: page.nextCursor !== null };
}

/**
 * Sekme rozetleri. social-service'te sayım ucu yoktur: her liste en çok 50 kayıtla okunur (profil kartı istenmez);
 * 50'den fazlası `more` ile işaretlenir ("50+").
 */
export async function getCounts(signal?: AbortSignal): Promise<SocialCounts> {
  const query = { limit: COUNT_LIMIT };
  const [friends, incoming, outgoing, blocked] = await Promise.all([
    source.listFriends(query, signal),
    source.listIncomingRequests(query, signal),
    source.listOutgoingRequests(query, signal),
    source.listBlocks(query, signal)
  ]);
  return { friends: count(friends), incoming: count(incoming), outgoing: count(outgoing), blocked: count(blocked) };
}

export function getInviteCode(signal?: AbortSignal): Promise<InviteCode> {
  return source.getInviteCode(signal);
}

export const socialActions = {
  acceptRequest: (requestId: string) => source.acceptRequest(requestId),
  rejectRequest: (requestId: string) => source.rejectRequest(requestId),
  cancelRequest: (requestId: string) => source.cancelRequest(requestId),
  sendRequestByCode: (code: string) => source.sendRequestByCode(code),
  removeFriend: (friendId: UserId) => source.removeFriend(friendId),
  blockUser: (userId: UserId) => source.blockUser(userId),
  unblockUser: (userId: UserId) => source.unblockUser(userId),
  createInviteCode: () => source.createInviteCode(),
  /** Eski kodu iptal edip yenisini üretir (iki ayrı uç: DELETE ardından POST). */
  regenerateInviteCode: async () => {
    await source.revokeInviteCode();
    return source.createInviteCode();
  }
};

export type SocialActionName = keyof typeof socialActions;
