import { apiUrl } from "@/shared/lib/api/client";
import type { ProfileSummary } from "../types/profile.types";

/** HTTP istemcisi dışarıdan verilir (oturum yenileme politikası çağıranın katmanındadır; feature'lar birbirinin iç dosyasını almaz). */
export type UserRequest = (path: "/api/v1/user/me", signal?: AbortSignal) => Promise<unknown>;

const MEDIA_PREFIX = "/api/v1/user/media/";

function text(value: unknown): string | null {
  return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
}

/** Yalnız User medya yolunu kabul eder; başka yol ya da mutlak adres (dış kaynak) yok sayılır. */
function mediaUrl(value: unknown): string | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const small = (value as Record<string, unknown>).small;
  return isMediaPath(small) ? apiUrl(small) : null;
}

function isMediaPath(value: unknown): value is `/${string}` {
  return typeof value === "string" && value.startsWith(MEDIA_PREFIX) && !value.includes("..");
}

/** `GET /api/v1/user/me` yanıtından kabuk için ad ve küçük avatarı çıkarır; alanlar savunmacı okunur. */
export async function getMyProfileSummary(request: UserRequest, signal?: AbortSignal): Promise<ProfileSummary> {
  const body = await request("/api/v1/user/me", signal);
  const record = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
  return {
    name: text(record.displayName) ?? text(record.username),
    avatarUrl: mediaUrl(record.avatar)
  };
}
