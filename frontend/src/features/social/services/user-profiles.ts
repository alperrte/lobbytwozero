import type { UserId } from "../types/social.types";
import { socialRequest } from "./social-http";

/**
 * user-service toplu profil kartı (`POST /api/v1/user/profiles/batch`, `docs/api.md` → USER). Sosyal kayıtlar yalnız kimlik
 * taşır; ad, kullanıcı adı ve avatar buradan eklenir.
 *
 * - En çok 50 kimlik/istek; fazlası parçalanır, yinelenenler tek gönderilir.
 * - Olmayan/yasaklı hesaplar ve izleyiciyle aralarında engel olanlar yanıtta YOKTUR (sessizce atlanır). Engellenenler listesi
 *   bu yüzden hiçbir zaman ad almaz; arayüz yedek ad gösterir.
 * - user-service yanıt vermezse sosyal liste yine açılır (kartlar boş döner); iptal (Abort) ise yukarı iletilir.
 */
export type ProfileCard = {
  userId: UserId;
  username: string | null;
  displayName: string | null;
  /** Doğrulanmış, gateway'e göre göreli küçük avatar yolu. */
  avatarPath: string | null;
};

type BatchResponse = {
  items: Array<{
    userId: UserId;
    username: string | null;
    displayName: string | null;
    avatar: { small?: string | null; large?: string | null } | null;
  }>;
};

const BATCH_LIMIT = 50;

/** Yalnız user-service medya adresi kabul edilir (sunucudan gelse bile başka bir kaynağa görsel isteği atılmaz). */
const MEDIA_PATH = /^\/api\/v1\/user\/media\/[0-9a-f]{32}(-s)?\.(jpg|png|gif)$/;

export function safeAvatarPath(value: string | null | undefined): string | null {
  return value && MEDIA_PATH.test(value) ? value : null;
}

function chunks<T>(values: readonly T[], size: number): T[][] {
  const result: T[][] = [];
  for (let index = 0; index < values.length; index += size) {
    result.push(values.slice(index, index + size));
  }
  return result;
}

export async function fetchProfileCards(
  userIds: readonly UserId[],
  signal?: AbortSignal
): Promise<Map<UserId, ProfileCard>> {
  const unique = [...new Set(userIds)];
  const cards = new Map<UserId, ProfileCard>();
  if (unique.length === 0) {
    return cards;
  }
  try {
    const responses = await Promise.all(
      chunks(unique, BATCH_LIMIT).map((ids) =>
        socialRequest<BatchResponse>("/api/v1/user/profiles/batch", {
          method: "POST",
          body: { userIds: ids },
          signal
        })
      )
    );
    for (const response of responses) {
      for (const item of response.items ?? []) {
        cards.set(item.userId, {
          userId: item.userId,
          username: item.username ?? null,
          displayName: item.displayName ?? null,
          avatarPath: safeAvatarPath(item.avatar?.small ?? item.avatar?.large)
        });
      }
    }
  } catch (error) {
    if (signal?.aborted) {
      throw error;
    }
    // Profil kartı yardımcı bilgidir: user-service hatası sosyal listeyi düşürmez, yedek adlar gösterilir.
  }
  return cards;
}
