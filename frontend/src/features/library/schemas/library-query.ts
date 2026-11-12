import type { LibraryQuery, LibraryView } from "../types/library";

export function isLibraryId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
function integer(value: string | null, fallback: number, min: number, max: number): number {
  if (!value || !/^\d+$/.test(value)) return fallback;
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= min && number <= max ? number : fallback;
}
export function libraryQuery(params: URLSearchParams, view: LibraryView = "all"): LibraryQuery {
  const boolean = (key: string) => params.get(key) === "true" ? true : params.get(key) === "false" ? false : undefined;
  return {
    favorite: view === "favorites" ? true : boolean("favorite"),
    completed: view === "completed" ? true : boolean("completed"),
    sort: params.get("sort") === "TITLE" ? "TITLE" : "ADDED_AT",
    direction: params.get("direction") === "ASC" ? "ASC" : "DESC",
    page: integer(params.get("page"), 0, 0, 2147483647),
    size: integer(params.get("size"), 20, 1, 50)
  };
}
export function libraryQueryString(query: LibraryQuery): string {
  const clean = libraryQuery(new URLSearchParams(Object.entries(query).filter(([, value]) => value !== undefined).map(([key, value]) => [key, String(value)])));
  const params = new URLSearchParams({ sort: clean.sort, direction: clean.direction, page: String(clean.page), size: String(clean.size) });
  if (clean.favorite !== undefined) params.set("favorite", String(clean.favorite));
  if (clean.completed !== undefined) params.set("completed", String(clean.completed));
  return params.toString();
}
