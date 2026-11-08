import { ApiHttpError } from "@/shared/lib/api/client";
import { normalizeInviteCode, validateInviteCode } from "../schemas/invite-code";
import type {
  Block,
  CursorPage,
  Friend,
  FriendRequest,
  InviteCode,
  PageQuery,
  UserId
} from "../types/social.types";
import {
  MOCK_ME,
  MOCK_MY_INVITE_CODE,
  MOCK_TIME,
  mockAges,
  mockIds,
  mockInviteCodes
} from "./social-mock-data";
import type { SocialSource } from "./social-api";

/**
 * SAHTE social-service (TEST ÇİFTESİ) — bellekte çalışır. Üretim kodu `social-api.ts` ile gerçek servise gider; bu depo
 * bileşen testlerinde `social-api` yerine konur (`vi.mock`).
 *
 * Uçların imzası (`SocialSource`), sayfa biçimi (`items` + `nextCursor`), sayfa boyutu sınırı (1–50) ve hata kodları gerçek
 * servisle aynıdır (`SocialErrorCode`); engel, arkadaşlığı ve bekleyen istekleri sunucudaki gibi sonlandırır.
 */

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const INVITE_VALID_DAYS = 30;
const INVITE_MAX_USES = 25;

type State = {
  friends: Friend[];
  requests: FriendRequest[];
  blocks: Block[];
  inviteCode: InviteCode | null;
  inviteCodes: Record<string, UserId>;
  sequence: number;
};

let latencyMs = 280;
let state: State = seed(Date.now());

function iso(now: number, agoMs: number): string {
  return new Date(now - agoMs).toISOString();
}

function seed(now: number): State {
  const { DAY, MINUTE } = MOCK_TIME;
  return {
    friends: mockIds.friends.map((friendId, index) => ({
      friendId,
      since: iso(now, mockAges.friendSinceDays(index) * DAY)
    })),
    requests: [
      ...mockIds.incoming.map((senderId, index) => ({
        id: `req-in-${index + 1}`,
        senderId,
        receiverId: MOCK_ME,
        status: "PENDING" as const,
        createdAt: iso(now, mockAges.incomingMinutes[index] * MINUTE),
        respondedAt: null
      })),
      ...mockIds.outgoing.map((receiverId, index) => ({
        id: `req-out-${index + 1}`,
        senderId: MOCK_ME,
        receiverId,
        status: "PENDING" as const,
        createdAt: iso(now, mockAges.outgoingMinutes[index] * MINUTE),
        respondedAt: null
      }))
    ],
    blocks: mockIds.blocked.map((blockedUserId, index) => ({
      blockedUserId,
      createdAt: iso(now, mockAges.blockedDays[index] * DAY)
    })),
    inviteCode: {
      code: MOCK_MY_INVITE_CODE,
      createdAt: iso(now, 2 * DAY),
      expiresAt: iso(now, -(INVITE_VALID_DAYS - 2) * DAY),
      maxUses: INVITE_MAX_USES,
      usedCount: 3
    },
    inviteCodes: { ...mockInviteCodes },
    sequence: 100
  };
}

/** Testler için: depoyu ilk hâline döndürür ve gecikmeyi ayarlar. */
export function resetSocialMock(options: { latencyMs?: number; now?: number } = {}): void {
  state = seed(options.now ?? Date.now());
  latencyMs = options.latencyMs ?? 280;
}

function fail(status: number, code: string): never {
  throw new ApiHttpError(status, { code });
}

function respond<T>(produce: () => T, signal?: AbortSignal): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const run = () => {
      if (signal?.aborted) {
        reject(new DOMException("Aborted", "AbortError"));
        return;
      }
      try {
        resolve(structuredClone(produce()));
      } catch (error) {
        reject(error);
      }
    };
    if (latencyMs <= 0) {
      queueMicrotask(run);
    } else {
      setTimeout(run, latencyMs);
    }
  });
}

function paginate<T>(all: readonly T[], query: PageQuery = {}): CursorPage<T> {
  const limit = query.limit ?? DEFAULT_PAGE_SIZE;
  if (!Number.isInteger(limit) || limit < 1 || limit > MAX_PAGE_SIZE) {
    fail(400, "INVALID_PAGE_SIZE");
  }
  let offset = 0;
  if (query.cursor) {
    const match = /^m(\d+)$/.exec(query.cursor);
    if (!match) {
      fail(400, "INVALID_CURSOR");
    }
    offset = Number(match[1]);
  }
  const items = all.slice(offset, offset + limit);
  const next = offset + limit;
  return { items, nextCursor: next < all.length ? `m${next}` : null };
}

function newest<T>(items: readonly T[], at: (item: T) => string): T[] {
  return [...items].sort((left, right) => at(right).localeCompare(at(left)));
}

function isBlockedByMe(userId: UserId): boolean {
  return state.blocks.some((block) => block.blockedUserId === userId);
}

function pendingBetween(userId: UserId): FriendRequest | undefined {
  return state.requests.find(
    (request) =>
      request.status === "PENDING" &&
      ((request.senderId === MOCK_ME && request.receiverId === userId) ||
        (request.senderId === userId && request.receiverId === MOCK_ME))
  );
}

function pendingRequest(requestId: string, side: "incoming" | "outgoing"): FriendRequest {
  const request = state.requests.find((entry) => entry.id === requestId);
  const mine = side === "incoming" ? request?.receiverId === MOCK_ME : request?.senderId === MOCK_ME;
  if (!request || !mine) {
    fail(404, "FRIEND_REQUEST_NOT_FOUND");
  }
  if (request.status !== "PENDING") {
    fail(409, "FRIEND_REQUEST_NOT_PENDING");
  }
  return request;
}

function answer(request: FriendRequest, status: FriendRequest["status"]): FriendRequest {
  const answered = { ...request, status, respondedAt: new Date().toISOString() };
  state.requests = state.requests.map((entry) => (entry.id === request.id ? answered : entry));
  return answered;
}

export const socialMockApi: SocialSource = {
  listFriends(query?: PageQuery, signal?: AbortSignal) {
    return respond(() => paginate(newest(state.friends, (friend) => friend.since), query), signal);
  },

  removeFriend(friendId: UserId) {
    return respond(() => {
      if (!state.friends.some((friend) => friend.friendId === friendId)) {
        fail(404, "FRIENDSHIP_NOT_FOUND");
      }
      state.friends = state.friends.filter((friend) => friend.friendId !== friendId);
    });
  },

  listIncomingRequests(query?: PageQuery, signal?: AbortSignal) {
    return respond(() => {
      const incoming = state.requests.filter(
        (request) => request.receiverId === MOCK_ME && request.status === "PENDING"
      );
      return paginate(newest(incoming, (request) => request.createdAt), query);
    }, signal);
  },

  listOutgoingRequests(query?: PageQuery, signal?: AbortSignal) {
    return respond(() => {
      const outgoing = state.requests.filter(
        (request) => request.senderId === MOCK_ME && request.status === "PENDING"
      );
      return paginate(newest(outgoing, (request) => request.createdAt), query);
    }, signal);
  },

  acceptRequest(requestId: string) {
    return respond(() => {
      const request = pendingRequest(requestId, "incoming");
      state.friends = [{ friendId: request.senderId, since: new Date().toISOString() }, ...state.friends];
      return answer(request, "ACCEPTED");
    });
  },

  rejectRequest(requestId: string) {
    return respond(() => answer(pendingRequest(requestId, "incoming"), "REJECTED"));
  },

  cancelRequest(requestId: string) {
    return respond(() => answer(pendingRequest(requestId, "outgoing"), "CANCELLED"));
  },

  sendRequestByCode(rawCode: string) {
    return respond(() => {
      if (validateInviteCode(rawCode) !== null) {
        fail(400, "VALIDATION_FAILED");
      }
      const receiverId = state.inviteCodes[normalizeInviteCode(rawCode)];
      if (!receiverId) {
        fail(404, "INVITE_CODE_NOT_FOUND");
      }
      if (receiverId === MOCK_ME) {
        fail(400, "CANNOT_REQUEST_SELF");
      }
      if (isBlockedByMe(receiverId)) {
        fail(409, "USER_BLOCKED");
      }
      if (state.friends.some((friend) => friend.friendId === receiverId)) {
        fail(409, "ALREADY_FRIENDS");
      }
      const pending = pendingBetween(receiverId);
      if (pending) {
        fail(409, pending.senderId === MOCK_ME ? "FRIEND_REQUEST_ALREADY_PENDING" : "INCOMING_FRIEND_REQUEST_PENDING");
      }
      state.sequence += 1;
      const request: FriendRequest = {
        id: `req-out-${state.sequence}`,
        senderId: MOCK_ME,
        receiverId,
        status: "PENDING",
        createdAt: new Date().toISOString(),
        respondedAt: null
      };
      state.requests = [...state.requests, request];
      return request;
    });
  },

  listBlocks(query?: PageQuery, signal?: AbortSignal) {
    return respond(() => paginate(newest(state.blocks, (block) => block.createdAt), query), signal);
  },

  blockUser(userId: UserId) {
    return respond(() => {
      if (userId === MOCK_ME) {
        fail(400, "CANNOT_BLOCK_SELF");
      }
      const existing = state.blocks.find((block) => block.blockedUserId === userId);
      if (existing) {
        return existing;
      }
      // Sunucuyla aynı: engel, arkadaşlığı ve iki yöndeki bekleyen istekleri sonlandırır.
      state.friends = state.friends.filter((friend) => friend.friendId !== userId);
      state.requests = state.requests.map((request) =>
        request.status === "PENDING" && (request.senderId === userId || request.receiverId === userId)
          ? { ...request, status: "CANCELLED" as const, respondedAt: new Date().toISOString() }
          : request
      );
      const block = { blockedUserId: userId, createdAt: new Date().toISOString() };
      state.blocks = [block, ...state.blocks];
      return block;
    });
  },

  unblockUser(userId: UserId) {
    return respond(() => {
      if (!state.blocks.some((block) => block.blockedUserId === userId)) {
        fail(404, "BLOCK_NOT_FOUND");
      }
      state.blocks = state.blocks.filter((block) => block.blockedUserId !== userId);
    });
  },

  getInviteCode(signal?: AbortSignal) {
    return respond(() => state.inviteCode ?? fail(404, "INVITE_CODE_NOT_FOUND"), signal);
  },

  /** Kod yoksa üretir, varsa aynısını döner (sunucudaki `POST /invite-code` gibi). */
  createInviteCode() {
    return respond(() => {
      if (!state.inviteCode) {
        const now = Date.now();
        const code = generateCode(state.sequence);
        state.sequence += 1;
        state.inviteCode = {
          code,
          createdAt: new Date(now).toISOString(),
          expiresAt: new Date(now + INVITE_VALID_DAYS * MOCK_TIME.DAY).toISOString(),
          maxUses: INVITE_MAX_USES,
          usedCount: 0
        };
        state.inviteCodes = { ...state.inviteCodes, [code]: MOCK_ME };
      }
      return state.inviteCode;
    });
  },

  revokeInviteCode() {
    return respond(() => {
      const revoked = state.inviteCode?.code;
      state.inviteCodes = Object.fromEntries(
        Object.entries(state.inviteCodes).filter(([code]) => code !== revoked)
      );
      state.inviteCode = null;
    });
  }
};

const CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

function generateCode(salt: number): string {
  let value = Date.now() + salt * 7919;
  let code = "";
  for (let index = 0; index < 10; index += 1) {
    code += CODE_ALPHABET[value % CODE_ALPHABET.length];
    value = Math.floor(value / CODE_ALPHABET.length) + (index + 1) * 104729;
  }
  return code;
}
