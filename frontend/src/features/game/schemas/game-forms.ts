import type { AdminGame, GameDetail } from "../types/game";

export type FormField = { name: string; kind?: "textarea" | "number" | "date" | "url" | "checkbox"; required?: boolean; minLength?: number; max?: number; pattern?: string; options?: readonly string[] };
export const gameFields: readonly FormField[] = [
  { name: "title", required: true, max: 256 }, { name: "description", kind: "textarea", max: 16000 },
  { name: "releaseDate", kind: "date" }, { name: "earlyAccess", options: ["", "true", "false"] },
  { name: "gameType", required: true, options: ["MAIN_GAME", "DLC", "DEMO", "SOUNDTRACK", "SOFTWARE", "VIDEO", "PACKAGE", "HARDWARE", "UNKNOWN"] },
  ...["developers", "publishers", "languages", "genres", "categories", "tags", "platforms"].map((name) => ({ name, max: 2000 })),
  { name: "coverUrl", kind: "url", max: 1024 }
];
export const reasonField: FormField = { name: "reason", kind: "textarea", required: true, minLength: 8, max: 1000 };
export const uuidPattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const listFields = ["developers", "publishers", "languages", "genres", "categories", "tags", "platforms"];
export function gameDefaults(detail: GameDetail): Record<string, string> {
  const metadata = detail.metadata;
  return {
    title: detail.game.title, description: detail.description ?? "", releaseDate: detail.releaseDate ?? "",
    gameType: detail.game.gameType ?? "UNKNOWN", earlyAccess: metadata.earlyAccess == null ? "" : String(metadata.earlyAccess),
    developers: metadata.developers.join(", "), publishers: metadata.publishers.join(", "), languages: metadata.languages.join(", "),
    genres: metadata.genres.map((value) => value.code ?? "").filter(Boolean).join(", "),
    categories: metadata.categories?.map((value) => value.code).filter(Boolean).join(", ") ?? "",
    tags: metadata.tags?.map((value) => value.code).filter(Boolean).join(", ") ?? "",
    platforms: metadata.platforms.map((value) => value.code ?? "").filter(Boolean).join(", "), coverUrl: detail.game.coverUrl ?? ""
  };
}
export function gameEdit(values: Record<string, string>, current?: AdminGame): Record<string, unknown> {
  const initial = current ? gameDefaults(current.game) : undefined;
  const body: Record<string, unknown> = current ? { version: current.version } : {};
  for (const field of gameFields) {
    const value = values[field.name]?.trim() ?? "";
    if (initial && value === initial[field.name]) continue;
    if (listFields.includes(field.name)) body[field.name] = [...new Set(value.split(",").map((item) => item.trim()).filter(Boolean))];
    else if (field.name === "earlyAccess") { if (value) body.earlyAccess = value === "true"; }
    else if (value) body[field.name] = value;
  }
  return body;
}
