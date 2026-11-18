import { ApiHttpError } from "@/shared/lib/api/client";
import { normalizeSearch } from "./players-api";
import { userRequest } from "./user-http";

/** Yönetim paneli arama satırı: `userId` Auth'taki hesap kimliğidir. */
export type AdminProfileHit = {
  userId: string;
  username: string | null;
  displayName: string | null;
  /** User'daki hesap durumu kopyası (doğruluk kaynağı Auth). */
  accountStatus: string | null;
  memberSince: string | null;
};

const USER_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value : null;
}

/** Yanıtı savunmacı okur: kimliği UUID olmayan satır atılır, bilinmeyen alan yok sayılır. */
export function toAdminProfileHits(body: unknown): AdminProfileHit[] {
  const items = typeof body === "object" && body !== null ? (body as Record<string, unknown>).items : null;
  if (!Array.isArray(items)) {
    return [];
  }
  return items.flatMap((entry: unknown) => {
    const r = typeof entry === "object" && entry !== null ? (entry as Record<string, unknown>) : {};
    if (typeof r.userId !== "string" || !USER_ID.test(r.userId)) {
      return [];
    }
    return [{
      userId: r.userId,
      username: text(r.username),
      displayName: text(r.displayName),
      accountStatus: text(r.accountStatus),
      memberSince: text(r.memberSince)
    }];
  });
}

/**
 * Yönetici araması: `@kullanıcıadı` ya da görünen ad önekiyle en çok 20 hesap; askıdaki ve yasaklı hesaplar da çıkar.
 * Baştaki `@` atılır. Kısa/uzun sorgu için istek atılmaz. Yetki sunucudadır (yalnız ADMIN); başkası 403 alır.
 */
export async function searchAdminProfiles(query: string, signal?: AbortSignal): Promise<AdminProfileHit[]> {
  const value = normalizeSearch(query.trim().replace(/^@/, ""));
  if (value === null) {
    throw new ApiHttpError(400, { code: "INVALID_SEARCH_QUERY" });
  }
  return toAdminProfileHits(
    await userRequest<unknown>(`/api/v1/user/admin/profiles/search?q=${encodeURIComponent(value)}`, { signal })
  );
}
