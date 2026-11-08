import { apiRequest, apiUrl } from "@/shared/lib/api/client";
import {
  DELETED_ACCOUNT_ID,
  type AuthorCard,
  type CommentPage,
  type CommentView,
  type Forum,
  type ForumPage,
  type GameCard,
  type GameContentType,
  type LikeState,
  type PostCategory,
  type PostImage,
  type PostPage,
  type PostRecord,
  type PostView,
  type ReportReason
} from "../types/community.types";
import { communityGet, communitySend, communityUpload } from "./community-http";
import { invalidateMyPosts } from "./my-posts-snapshot";

const PAGE = 20;

/** Veri değiştiren isteğin başarısında "Gönderilerim" anlık görüntüsü geçersiz kılınır (bayat liste gösterilmez). */
function changed<T>(request: Promise<T>): Promise<T> {
  return request.then((value) => {
    invalidateMyPosts();
    return value;
  });
}

function cursorQuery(limit: number, cursor?: string | null): string {
  const query = new URLSearchParams();
  query.set("limit", String(limit));
  if (cursor) query.set("cursor", cursor);
  return query.toString();
}

/* ------------------------------- Gönderiler ------------------------------ */

export const postsPath = "/api/v1/community/posts" as const;

export const fetchPostPage = (query: string, signal?: AbortSignal, userId?: string) =>
  communityGet<PostPage>(userId ? `/api/v1/community/users/${userId}/posts?${query}` : `${postsPath}?${query}`, signal);

export const fetchPost = (postId: string, signal?: AbortSignal) => communityGet<PostView>(`${postsPath}/${postId}`, signal);

export type CreatePostInput = {
  body: string;
  category?: PostCategory;
  gameId?: string;
  forumId?: string;
  contentType?: GameContentType;
  title?: string;
};

export const createPost = (input: CreatePostInput) => changed(communitySend<PostRecord>(postsPath, "POST", input));

export const updatePost = (postId: string, input: { body: string; category?: PostCategory; title?: string }) =>
  changed(communitySend<PostRecord>(`${postsPath}/${postId}`, "PATCH", input));

export const deletePost = (postId: string) => changed(communitySend<void>(`${postsPath}/${postId}`, "DELETE"));

export const linkPostGame = (postId: string, gameId: string) =>
  changed(communitySend<PostRecord>(`${postsPath}/${postId}/game`, "PUT", { gameId }));
export const unlinkPostGame = (postId: string) => changed(communitySend<void>(`${postsPath}/${postId}/game`, "DELETE"));

export const likePost = (postId: string) => communitySend<LikeState>(`${postsPath}/${postId}/like`, "PUT");
export const unlikePost = (postId: string) => communitySend<LikeState>(`${postsPath}/${postId}/like`, "DELETE");

export const repostPost = (postId: string, comment: string) =>
  changed(communitySend<PostView>(`${postsPath}/${postId}/reposts`, "POST", comment ? { comment } : undefined));

export const reportPost = (postId: string, reason: ReportReason, detail: string) =>
  communitySend<unknown>(`${postsPath}/${postId}/reports`, "POST", detail ? { reason, detail } : { reason });

export const uploadPostImage = (postId: string, file: File) => changed(communityUpload<PostImage>(`${postsPath}/${postId}/images`, file));
export const deletePostImage = (postId: string, imageId: string) =>
  changed(communitySend<void>(`${postsPath}/${postId}/images/${imageId}`, "DELETE"));

/** Görsel adresi gateway'e göredir; `<img src>` ile doğrudan yüklenir (herkese açık gönderiler için kimlik gerekmez). */
export function imageSrc(image: Pick<PostImage, "postId" | "id">): string {
  return apiUrl(`/api/v1/community/posts/${image.postId}/images/${image.id}`);
}

/* -------------------------------- Yorumlar ------------------------------- */

/** `limit` yalnız kart içi yorum bölmesi içindir (küçük sayfalar); varsayılan 20. */
export const fetchComments = (postId: string, cursor: string | null, signal?: AbortSignal, limit: number = PAGE) =>
  communityGet<CommentPage>(`${postsPath}/${postId}/comments?${cursorQuery(limit, cursor)}`, signal);

export const fetchReplies = (commentId: string, cursor: string | null, signal?: AbortSignal) =>
  communityGet<CommentPage>(`/api/v1/community/comments/${commentId}/replies?${cursorQuery(PAGE, cursor)}`, signal);

export const createComment = (postId: string, body: string, parentCommentId?: string) =>
  communitySend<CommentView>(`${postsPath}/${postId}/comments`, "POST", parentCommentId ? { body, parentCommentId } : { body });

export const updateComment = (commentId: string, body: string) =>
  communitySend<CommentView>(`/api/v1/community/comments/${commentId}`, "PATCH", { body });

export const deleteComment = (commentId: string) => communitySend<void>(`/api/v1/community/comments/${commentId}`, "DELETE");

export const reportComment = (commentId: string, reason: ReportReason, detail: string) =>
  communitySend<unknown>(`/api/v1/community/comments/${commentId}/reports`, "POST", detail ? { reason, detail } : { reason });

/* --------------------------------- Forumlar ------------------------------- */

export const fetchForums = (cursor: string | null, signal?: AbortSignal) =>
  communityGet<ForumPage>(`/api/v1/community/forums?${cursorQuery(PAGE, cursor)}`, signal);

export const fetchForum = (forumId: string, signal?: AbortSignal) => communityGet<Forum>(`/api/v1/community/forums/${forumId}`, signal);

export const createForum = (input: { slug: string; title: string; description?: string; gameId?: string }) =>
  communitySend<Forum>("/api/v1/community/forums", "POST", input);

/* ------------------------- Diğer servisler (yalnız okuma) ----------------- */

type RawProfile = {
  userId?: unknown;
  username?: unknown;
  displayName?: unknown;
  avatar?: { small?: unknown; large?: unknown } | null;
};

const MEDIA_PATH = /^\/api\/v1\/user\/media\/[0-9a-f]{32}(-s)?\.(jpg|png|gif)$/;

/** Avatar adresi yalnız Gateway'in kullanıcı medya yolu ya da https olabilir; başka biçim yok sayılır. */
export function safeAvatarUrl(value: unknown): string | null {
  if (typeof value !== "string" || value === "") return null;
  if (MEDIA_PATH.test(value)) return apiUrl(value as `/${string}`);
  try {
    const url = new URL(value);
    if (url.protocol === "https:") return url.toString();
    if (url.protocol === "http:" && ["localhost", "127.0.0.1"].includes(url.hostname) && MEDIA_PATH.test(url.pathname)) {
      return url.toString();
    }
  } catch {
    // göreli ya da bozuk adres: gösterilmez
  }
  return null;
}

/** User Service toplu profil kartı (oturum ister). Yok/engelli/yasaklı hesaplar yanıtta sessizce atlanır. */
export async function fetchAuthorCards(userIds: readonly string[], signal?: AbortSignal): Promise<AuthorCard[]> {
  const ids = [...new Set(userIds)].filter((id) => id !== DELETED_ACCOUNT_ID).slice(0, 50);
  if (ids.length === 0) return [];
  const response = await communitySend<{ items?: RawProfile[] }>("/api/v1/user/profiles/batch", "POST", { userIds: ids }, signal);
  return (response.items ?? []).flatMap((item) =>
    typeof item.userId === "string"
      ? [
          {
            userId: item.userId,
            username: typeof item.username === "string" ? item.username : null,
            displayName: typeof item.displayName === "string" ? item.displayName : null,
            avatarUrl: safeAvatarUrl(item.avatar?.small)
          }
        ]
      : []
  );
}

type RawGameCard = { gameId?: unknown; title?: unknown; coverUrl?: unknown };

function toGameCard(raw: RawGameCard | undefined): GameCard[] {
  if (!raw || typeof raw.gameId !== "string" || typeof raw.title !== "string") return [];
  let coverUrl: string | null = null;
  if (typeof raw.coverUrl === "string") {
    try {
      coverUrl = new URL(raw.coverUrl).protocol === "https:" ? raw.coverUrl : null;
    } catch {
      coverUrl = null;
    }
  }
  return [{ gameId: raw.gameId, title: raw.title, coverUrl }];
}

/** Game Service toplu kart (anonim). Bulunamayan oyunlar `unavailable` olur ve listeden düşer. */
export async function fetchGameCards(gameIds: readonly string[], signal?: AbortSignal): Promise<GameCard[]> {
  const ids = [...new Set(gameIds)].slice(0, 50);
  if (ids.length === 0) return [];
  const response = await apiRequest<{ items?: RawGameCard[] }>("/api/v1/game/games/batch", {
    method: "POST",
    signal,
    cache: "no-store",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ gameIds: ids })
  });
  return (response.items ?? []).flatMap(toGameCard);
}

/** Oyun seçici araması (Game yerel katalog, anonim). */
export async function searchGames(q: string, signal?: AbortSignal): Promise<GameCard[]> {
  const query = new URLSearchParams({ q: q.trim(), size: "8" });
  const response = await apiRequest<{ items?: Array<{ game?: RawGameCard }> }>(`/api/v1/game/games/search?${query}`, {
    signal,
    cache: "no-store",
    headers: { Accept: "application/json" }
  });
  return (response.items ?? []).flatMap((item) => toGameCard(item.game));
}
