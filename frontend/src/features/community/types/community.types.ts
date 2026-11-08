/** Community API tipleri (kaynak: docs/api.md → COMMUNITY). Gövde düz metindir; HTML olarak yorumlanmaz. */

export const postCategories = ["GENERAL", "GUIDE", "QUESTION", "SCREENSHOT"] as const;
export type PostCategory = (typeof postCategories)[number];

export const gameContentTypes = ["GUIDES", "EASTER_EGGS", "EXPERIENCES"] as const;
export type GameContentType = (typeof gameContentTypes)[number];

export const postSorts = ["NEWEST", "POPULAR", "OLDEST"] as const;
export type PostSort = (typeof postSorts)[number];

export const reportReasons = [
  "SPAM",
  "HARASSMENT",
  "HATE_SPEECH",
  "SEXUAL_CONTENT",
  "VIOLENCE",
  "ILLEGAL_CONTENT",
  "MISINFORMATION",
  "OTHER"
] as const;
export type ReportReason = (typeof reportReasons)[number];

export type PostImage = {
  id: string;
  postId: string;
  mimeType: "image/jpeg" | "image/png";
  sizeBytes: number;
  width: number;
  height: number;
  sortOrder: number;
  contentUrl: string;
  createdAt: string;
};

/** Repost'un orijinali. `available=false` ise yalnız `postId` vardır (silinmiş, gizli ya da engelli). */
export type RepostSource = {
  postId: string;
  available: boolean;
  authorId?: string | null;
  gameId?: string | null;
  category?: PostCategory;
  body?: string | null;
  createdAt?: string;
  images?: PostImage[];
};

export type PostView = {
  id: string;
  authorId: string;
  gameId: string | null;
  forumId: string | null;
  category: PostCategory;
  body: string | null;
  status: "VISIBLE" | "HIDDEN" | "DELETED";
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  images: PostImage[];
  repostOf: RepostSource | null;
  contentType: GameContentType | null;
  title: string | null;
};

/** Oluşturma/düzenleme yanıtı (sayaçsız `PostResponse`). */
export type PostRecord = Omit<PostView, "likeCount" | "commentCount" | "likedByMe" | "images" | "repostOf">;

export type PostPage = { items: PostView[]; nextCursor: string | null };

export type LikeState = { postId: string; liked: boolean; likeCount: number };

export type CommentView = {
  id: string;
  postId: string;
  authorId: string | null;
  parentCommentId: string | null;
  body: string | null;
  status: "VISIBLE" | "DELETED";
  createdAt: string;
  updatedAt: string;
  replyCount: number;
};

export type CommentPage = { items: CommentView[]; nextCursor: string | null };

export type Forum = {
  id: string;
  gameId: string | null;
  slug: string;
  title: string;
  description: string | null;
  status: "VISIBLE" | "HIDDEN";
  createdAt: string;
  updatedAt: string;
};

export type ForumPage = { items: Forum[]; nextCursor: string | null };

export type AuthorCard = {
  userId: string;
  username: string | null;
  displayName: string | null;
  avatarUrl: string | null;
};

export type GameCard = { gameId: string; title: string; coverUrl: string | null };

/** Silinmiş hesabın yer tutucu kimliği (Community hesap silmede içeriği bu kimliğe taşır). */
export const DELETED_ACCOUNT_ID = "00000000-0000-0000-0000-000000000000";

export type PostFilters = {
  q: string;
  gameId: string;
  category: PostCategory | "";
  /** Yerel tarih (`YYYY-MM-DD`); boş = sınır yok. */
  fromDate: string;
  toDate: string;
  sort: PostSort;
};

export const emptyFilters: PostFilters = {
  q: "",
  gameId: "",
  category: "",
  fromDate: "",
  toDate: "",
  sort: "NEWEST"
};
