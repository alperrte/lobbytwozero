const filterKeys = ["q", "genre", "platform", "language", "earlyAccess", "releasedAfter", "releasedBefore", "popularOnly", "minRating", "discountedOnly"] as const;

/** Boş filtreler gönderilmez: q= ve boş tür/dil backend'de geçerli bir filtre değildir. */
export function discoveryQuery(input: URLSearchParams): URLSearchParams {
  const result = new URLSearchParams({ page: input.get("page") || "0", size: "20" });
  for (const key of filterKeys) {
    const value = input.get(key)?.trim();
    if (value) result.set(key, value);
  }
  result.set("sort", ["TITLE", "RELEASE_DATE", "POPULARITY", "RATING"].includes(input.get("sort") ?? "") ? input.get("sort")! : "TITLE");
  result.set("direction", input.get("direction") === "DESC" ? "DESC" : "ASC");
  result.set("country", input.get("country") || "TR");
  result.set("currency", input.get("currency") || "TRY");
  return result;
}

export function safeExternalUrl(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !url.username && !url.password ? url.href : undefined;
  } catch { return undefined; }
}
