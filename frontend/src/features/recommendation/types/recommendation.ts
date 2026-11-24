/**
 * Recommendation Service sözleşmesi (backend koduyla birebir; `docs/api.md` → RECOMMENDATION).
 * Skor bir olasılık/yüzde DEĞİLDİR: 1..10000 aralığında sıralama ağırlığıdır; arayüzde yalnız sıra ve gerekçe gösterilir.
 */
export const runStatuses = ["EMPTY", "PARTIAL"] as const;
export type RunStatus = (typeof runStatuses)[number];

export const feedbackValues = ["LIKE", "NOT_INTERESTED"] as const;
export type FeedbackValue = (typeof feedbackValues)[number];

export type RecommendedItem = {
  itemId: string;
  gameId: string;
  score: number;
  rank: number;
  reasonCode: string;
  contribution: number;
  matchedGenres: string[];
};

export type RecommendationRun = {
  runId: string;
  algorithmVersion: string;
  createdAt: string;
  status: RunStatus;
  genre: string | null;
  platform: string | null;
  items: RecommendedItem[];
  limitations: string[];
};

export type RunSummary = { runId: string; algorithmVersion: string; createdAt: string; resultCount: number };
export type RunPage = { items: RunSummary[]; nextCursor: string | null };

export type ItemFeedback = { itemId: string; value: FeedbackValue | null; version: number };

export type GenerateGamesRequest = { genre?: string; platform?: string; size: number };

export const requirementPlatforms = ["windows", "mac", "linux"] as const;
export type RequirementPlatform = (typeof requirementPlatforms)[number];

export const compatibilityStatuses = ["MEETS_MINIMUM_COMPUTE", "UNKNOWN"] as const;
export type CompatibilityStatus = (typeof compatibilityStatuses)[number];
export const compatibilityReasons = ["MINIMUM_CPU_GPU_RAM_PROVEN", "MINIMUM_NOT_PROVEN", "REQUIREMENTS_MISSING"] as const;
export type CompatibilityReason = (typeof compatibilityReasons)[number];

export type CompatibleGame = {
  gameId: string;
  affinity: number;
  matchedGenres: string[];
  compatibility: CompatibilityStatus;
  reason: CompatibilityReason;
};

export type HardwareCompatibleResult = {
  algorithmVersion: string;
  createdAt: string;
  hardwareProfileId: string;
  platform: string;
  items: CompatibleGame[];
  excludedBelowMinimum: number;
  limitations: string[];
};

export const graphicsPresets = ["LOW", "MEDIUM", "HIGH", "ULTRA"] as const;
export type GraphicsPreset = (typeof graphicsPresets)[number];
export const renderingModes = ["NATIVE_RASTER"] as const;
export type RenderingMode = (typeof renderingModes)[number];

export type AssessmentRequest = {
  hardwareProfileId: string;
  gameId: string;
  platform: string;
  resolution?: string;
  graphicsPreset?: GraphicsPreset;
  renderingMode?: RenderingMode;
};

export const suitabilities = ["BELOW_MINIMUM", "UNKNOWN"] as const;
export type Suitability = (typeof suitabilities)[number];
export const checkStatuses = ["MEETS", "BELOW", "UNKNOWN"] as const;
export type CheckStatus = (typeof checkStatuses)[number];
export const checkFields = ["CPU", "GPU", "RAM", "VRAM", "STORAGE", "OS", "REQUIREMENTS"] as const;
export type CheckField = (typeof checkFields)[number];
export const requirementLevels = ["MINIMUM", "RECOMMENDED"] as const;
export type RequirementLevel = (typeof requirementLevels)[number];

export type RequirementCheck = {
  field: CheckField;
  status: CheckStatus;
  actual: string | null;
  required: string | null;
  reason: string;
};

export type LevelAssessment = {
  level: RequirementLevel;
  computeStatus: CheckStatus;
  overallStatus: CheckStatus;
  checks: RequirementCheck[];
};

export const fpsStatuses = ["ESTIMATED", "INSUFFICIENT_DATA"] as const;
export type FpsStatus = (typeof fpsStatuses)[number];
export const fpsConfidences = ["LOW", "NONE"] as const;
export type FpsConfidence = (typeof fpsConfidences)[number];

export type FpsEvidence = {
  contractVersion: string | null;
  source: string | null;
  contextVerification: string | null;
  method: string | null;
  renderingMode: string | null;
  consideredSessionCount: number;
  inlierSampleCount: number;
  excludedSampleCount: number;
  firstMeasuredAt: string | null;
  windowLimited: boolean;
  limitations: string[];
};

export type FpsAssessment = {
  status: FpsStatus;
  estimatedLowerFps: number | null;
  estimatedUpperFps: number | null;
  confidence: FpsConfidence;
  observedSampleCount: number;
  observedSessionCount: number;
  producerSampleThresholdMet: boolean;
  lastObservedAt: string | null;
  requestedResolution: string | null;
  requestedPreset: string | null;
  reason: string;
  evidence: FpsEvidence | null;
};

export type GraphicsAssessment = { status: "CONDITIONAL" | "INSUFFICIENT_DATA"; suggestedPreset: string | null; reason: string };

export type UpgradeSuggestion = { code: string; field: string; requirementLevel: string; required: string | null; reason: string };

export type ComponentCompatibilityCheck = { field: string; status: "DECLARED_MATCH" | "INCOMPATIBLE" | "UNKNOWN"; reason: string };
export type ComponentCompatibility = { status: "INCOMPATIBLE" | "UNKNOWN"; checks: ComponentCompatibilityCheck[]; limitations: string[] };

export type HardwareAssessment = {
  hardwareProfileId: string;
  hardwareProfileVersion: number;
  gameId: string;
  platform: string;
  assessedAt: string;
  suitability: Suitability;
  minimum: LevelAssessment;
  recommended: LevelAssessment;
  fps: FpsAssessment;
  graphics: GraphicsAssessment;
  upgrades: UpgradeSuggestion[];
  componentCompatibility: ComponentCompatibility;
  limitations: string[];
};

export const playerRecommendationKinds = ["SIMILAR_PLAYERS", "FRIEND_SUGGESTIONS"] as const;
export type PlayerRecommendationKind = (typeof playerRecommendationKinds)[number];

export type PlayerSuggestion = {
  userId: string;
  score: number;
  sharedGenres: string[];
  sharedPlatforms: string[];
  sharedPlayerTypes: string[];
};

export type PlayerRecommendations = {
  kind: PlayerRecommendationKind;
  algorithmVersion: string;
  createdAt: string;
  items: PlayerSuggestion[];
  limitations: string[];
};

export type PlayerFilters = { size: number; genre?: string; platform?: string };

export type EventSuggestion = {
  eventId: string;
  type: string;
  title: string;
  startsAt: string;
  endsAt: string;
  gameId: string | null;
  score: number;
  sharedGenres: string[];
  sharedPlatforms: string[];
};

export type EventRecommendations = { algorithmVersion: string; createdAt: string; items: EventSuggestion[]; limitations: string[] };

export const assistantTopics = ["GAME", "HARDWARE", "LTZ_USAGE"] as const;
export type AssistantTopic = (typeof assistantTopics)[number];
export const assistantIntents = ["SELECT_GAME", "ASSESS_GAME", "VIEW_HISTORY", "GIVE_FEEDBACK"] as const;
export type AssistantIntent = (typeof assistantIntents)[number];
export const assistantStatuses = ["GUIDANCE", "NEEDS_INPUT", "INSUFFICIENT_DATA"] as const;
export type AssistantStatus = (typeof assistantStatuses)[number];
export const assistantMissingFields = ["GENRE", "HARDWARE_PROFILE_ID", "GAME_ID", "PLATFORM"] as const;
export type AssistantMissingField = (typeof assistantMissingFields)[number];
export const assistantSteps = [
  "OPEN_GAME_DISCOVERY",
  "FILTER_GENRE",
  "VERIFY_GAME_REQUIREMENTS",
  "REVIEW_MINIMUM_DEFICIT",
  "COMPARE_RECOMMENDED_LEVEL",
  "CHECK_UNKNOWN_REQUIREMENTS",
  "MEASURE_IN_GAME",
  "OPEN_RECOMMENDATION_HISTORY",
  "SELECT_OWN_ITEM",
  "SEND_FEEDBACK"
] as const;
export type AssistantStep = (typeof assistantSteps)[number];

/** İstemcinin seçtiği akış: konu + amaç, backend'in izin verdiği dört kombinasyon. */
export const assistantTasks = ["SELECT_GAME", "ASSESS_GAME", "VIEW_HISTORY", "GIVE_FEEDBACK"] as const;
export type AssistantTask = (typeof assistantTasks)[number];

export type AssistantRequest = {
  contractVersion: "assistant-v1";
  topic: AssistantTopic;
  intent: AssistantIntent;
  locale: "tr" | "en";
  genre?: string;
  hardwareProfileId?: string;
  gameId?: string;
  platform?: string;
};

export type AssistantResponse = {
  contractVersion: string;
  algorithmVersion: string;
  topic: AssistantTopic;
  intent: AssistantIntent;
  locale: string;
  status: AssistantStatus;
  template: string;
  message: string;
  steps: AssistantStep[];
  missingFields: AssistantMissingField[];
};

/** Diğer servislerden zenginleştirme verisi. */
export type GameCard = {
  gameId: string;
  title: string;
  coverUrl: string | null;
  externalReferences?: { provider: string; externalId: string }[];
};
export type GameMetadata = { gameId: string; genres: string[]; platforms: string[] };
export type CatalogTerm = { code: string; nameTr: string | null; nameEn: string | null; gameCount: number };
export type CatalogGame = { gameId: string; title: string };
export type ProfileCard = { userId: string; username: string | null; displayName: string | null; avatarPath: string | null };
export type RecommendationConsent = { recommendation: boolean; version: number };
export type EventParticipation = { id: string; eventId: string; status: string };
