export type ModuleType = "ACHIEVEMENTS" | "GUIDES" | "EASTER_EGGS" | "EXPERIENCES";
export const moduleTypes: readonly ModuleType[] = ["ACHIEVEMENTS", "GUIDES", "EASTER_EGGS", "EXPERIENCES"];

export type GameCard = {
  gameId: string;
  title: string;
  origin: "INTERNAL" | "EXTERNAL";
  metadataCompleteness: string;
  gameType?: string;
  coverUrl: string | null;
};

export type GameDetail = {
  game: GameCard;
  description: string | null;
  releaseDate: string | null;
  freshness: { status: string; fetchedAt: string | null };
  metadata: {
    developers: string[];
    publishers: string[];
    languages: string[];
    genres: Term[];
    platforms: Term[];
    categories?: Term[];
    tags?: Term[];
    earlyAccess?: boolean | null;
    metadataLanguage?: string | null;
    languageDataState?: string;
    languageSupport?: LanguageSupport[];
    provenance?: { field: string; source: string; dataState: string; recordedAt: string; verifiedAt: string | null }[];
    media?: { type: string; url: string; provider: string; sortOrder: number }[];
    systemRequirements?: { level: string; platform: string; os: string | null; cpu: string | null; gpu: string | null; ramMb: number | null; storageMb: number | null; rawText: string | null }[];
  };
};

export type GameRating = { status: string; averageRating: number | null; ratingCount: number | null };
export type CardsResponse = { items: GameCard[]; unavailable: string[] };
export type DiscoveryItem = { game: GameCard; releaseDate: string | null; earlyAccess?: boolean | null; languages?: string[]; editorialRank?: number | null; popularityUpdatedAt?: string | null; rating?: GameRating };
export type DiscoveryPage = {
  items: DiscoveryItem[];
  page: number;
  size: number;
  totalElements: number;
  popularityBasis?: string;
  ratingsSyncedAt?: string | null;
};

/** `GET /api/v1/game/catalog/status`: Steam indeks aktarımının güvenli operasyon özeti. */
export type CatalogStatus = {
  provider: string;
  runMode: string | null;
  syncStatus: string;
  cursor: number | string | null;
  processedCount: number;
  indexedEntries: number;
  mainGameEntries: number;
  unclassifiedEntries: number;
  providerTotal: number | null;
  runStartedAt: string | null;
  lastSuccessfulAt: string | null;
  lastErrorCode: string | null;
  failureCount: number;
  nextAttemptAt: string | null;
};

export type Achievement = { id: string; title: string; description: string | null };
export type GameModule = {
  gameId: string;
  type: ModuleType;
  state: "AVAILABLE" | "EMPTY" | "EXTERNAL" | "INTEGRATION_PENDING";
  items: Achievement[];
  page: number;
  size: number;
  totalElements: number | null;
  contentSource?: { service: string; listPath: string };
};

export type CommunitySummary = {
  id: string;
  gameId: string;
  type: Exclude<ModuleType, "ACHIEVEMENTS">;
  title: string;
  excerpt: string;
  authorId: string;
  publishedAt: string;
  updatedAt: string;
};
export type CommunityPage = { items: CommunitySummary[]; page: number; size: number; totalElements: number };
export type CommunityDetail = CommunitySummary & {
  body: string;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
};
export type ModulePage =
  | { kind: "achievements"; items: Achievement[]; totalElements: number; page: number; size: number }
  | ({ kind: "community" } & CommunityPage);

export type GameResource =
  | { kind: "session" }
  | { kind: "managed" }
  | { kind: "admin"; gameId: string }
  | { kind: "grants"; gameId: string }
  | { kind: "developer"; gameId: string }
  | { kind: "publicDeveloper"; gameId: string }
  | { kind: "prices"; gameIds: string[]; country: string; currency: string }
  | { kind: "feedback"; gameId: string; developer: boolean; page: number; status?: string; type?: string }
  | { kind: "adminAchievements"; gameId: string; page: number }
  | { kind: "discovery"; query: string }
  | { kind: "popular"; page: number; size?: number }
  | { kind: "catalogStatus" }
  | { kind: "cards"; gameIds: string[] }
  | { kind: "feedbackItem"; id: string; gameId?: string; developer: boolean }
  | { kind: "myFeedback"; page: number; gameId?: string; status?: string; type?: string }
  | { kind: "detail"; gameId: string }
  | { kind: "module"; gameId: string; type: ModuleType; page: number }
  | { kind: "content"; gameId: string; contentId: string };

export type Term = { id: string; code?: string; nameTr: string; nameEn: string };
export type LanguageSupport = { languageCode: string; interfaceSupport: string; audioSupport: string; subtitleSupport: string };
export type GameSession = { accountId: string; roles: string[] };
export type PriceOffer = { store: string; regularPrice: number; currentPrice: number; discountPercent: number; source: string; fetchedAt: string; expiresAt: string; sourceUpdatedAt: string; url: string };
export type PriceState = "AVAILABLE" | "NO_OFFER" | "UNAVAILABLE" | "STALE";
export type GamePricesResponse = { market: { country: string; currency: string }; items: { gameId: string; status: string; offers: PriceOffer[] }[] };
export type AdminGame = { gameId: string; status: string; version: number; game: GameDetail; manualOverrides: { field: string; markedAt: string }[] };
export type DeveloperGrant = { grantId: string; gameId: string; developerUserId: string; grantedAt: string };
export type ManagedGame = { gameId: string; title: string };
export type DeveloperContent = {
  gameId: string;
  description: { text: string; version?: number; status?: string; moderationReason?: string | null; updatedAt: string } | null;
  media: { id: string; type: string; url: string; altText: string | null; status?: string; moderationReason?: string | null; createdAt: string }[];
  updateNotes: { id: string; title: string; body: string; version?: number; status?: string; moderationReason?: string | null; createdAt: string; updatedAt: string }[];
};
export type Feedback = { id: string; gameId: string; type: "GENERAL" | "BUG_REPORT" | "EARLY_ACCESS"; status: "OPEN" | "IN_PROGRESS" | "RESOLVED"; title: string; body: string; version: number; createdAt: string; updatedAt: string };
export type FeedbackPage = { items: Feedback[]; page: number; size: number; totalElements: number };
export type ModerationTarget = "DESCRIPTION" | "MEDIA" | "UPDATE_NOTE";
export type GameOperation = "create" | "update" | "archive" | "restore" | "release" | "grant" | "revoke" | "popular" | "description" | "addMedia" | "deleteMedia" | "addNote" | "updateNote" | "deleteNote" | "moderate" | "feedback" | "feedbackStatus" | "achievement";
export type GameMutation = { operation: GameOperation; gameId?: string; targetId?: string; field?: string; body?: Record<string, unknown> };
