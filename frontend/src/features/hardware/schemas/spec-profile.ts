import { specKeys, type Category, type Product, type Specifications, type TypedValue } from "../types/hardware";

/**
 * Kategoriye özel teknik özellik profilleri. Anahtarlar backend `source.typedSpecifications`
 * alanındaki adlardır (PcPartMapper); sekiz temel alan eski `specifications` nesnesinden de okunabilir.
 * Kayıtta olmayan değer asla uydurulmaz: `readSpec` boş döner ve arayüz "—" gösterir.
 */
export type SpecKey = string;
type Profile = { card: SpecKey[]; detail: SpecKey[] };

export const specProfiles: Partial<Record<Category, Profile>> = {
  CPU: {
    card: ["coreCount", "clockMhz", "boostClockMhz", "powerWatts"],
    detail: ["microarchitecture", "coreCount", "clockMhz", "boostClockMhz", "powerWatts", "integratedGraphics"]
  },
  GPU: {
    card: ["capacityGb", "clockMhz", "boostClockMhz", "lengthMm"],
    detail: ["chipset", "capacityGb", "clockMhz", "boostClockMhz", "lengthMm", "color"]
  },
  MOTHERBOARD: {
    card: ["socket", "formFactor", "maximumMemoryGb", "memorySlots"],
    detail: ["socket", "formFactor", "maximumMemoryGb", "memorySlots", "color"]
  },
  RAM: {
    card: ["memoryType", "capacityGb", "clockMhz", "casLatency"],
    detail: ["memoryType", "capacityGb", "moduleCount", "moduleCapacityGb", "clockMhz", "casLatency", "firstWordLatencyNs", "color"]
  },
  STORAGE: {
    card: ["storageType", "capacityGb", "interfaceType", "formFactor"],
    detail: ["storageType", "capacityGb", "interfaceType", "formFactor", "cacheMb", "rotationRpm"]
  }
};

const baseUnits: Partial<Record<keyof Specifications, string>> = { capacityGb: "GB", clockMhz: "MHz", powerWatts: "W" };
const isBase = (key: string): key is keyof Specifications => (specKeys as readonly string[]).includes(key);

export type SpecValue = { number: number | null; text: string | null; unit: string };

/** Kayıtlı değer yoksa `undefined` döner; sayı da metin de boşsa kayıt "yok" sayılır. */
export function readSpec(product: Pick<Product, "specifications" | "source">, key: SpecKey): SpecValue | undefined {
  const typed: TypedValue | undefined = product.source?.typedSpecifications?.[key] ?? undefined;
  if (typed && (typed.number !== null && typed.number !== undefined || (typed.text ?? "").trim() !== "")) {
    return { number: typed.number ?? null, text: (typed.text ?? "").trim() || null, unit: typed.unit ?? "" };
  }
  if (isBase(key)) {
    const value = product.specifications?.[key];
    if (typeof value === "number") return { number: value, text: null, unit: baseUnits[key] ?? "" };
    if (typeof value === "string" && value.trim()) return { number: null, text: value.trim(), unit: "" };
  }
  return undefined;
}

export type FormatLabels = { yes: string; no: string };
/** Sayıyı yerel biçimle yazar; 1000 GB ve üstü TB olarak gösterilir (ondalık TB, 1 TB = 1000 GB). */
export function formatSpec(value: SpecValue | undefined, locale: string, labels: FormatLabels): string | undefined {
  if (!value) return undefined;
  if (value.number !== null) {
    let amount = value.number;
    let unit = value.unit;
    if (unit === "GB" && amount >= 1000) { amount /= 1000; unit = "TB"; }
    const formatted = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(amount);
    return unit ? `${formatted} ${unit}` : formatted;
  }
  if (value.text === "true") return labels.yes;
  if (value.text === "false") return labels.no;
  return value.text ? (value.unit ? `${value.text} ${value.unit}` : value.text) : undefined;
}

/** Karşılaştırma için kararlı karşılaştırma anahtarı (biçimden bağımsız). */
export function specSignature(value: SpecValue | undefined): string {
  if (!value) return "";
  return `${value.number ?? ""}|${(value.text ?? "").toLowerCase()}|${value.unit}`;
}

/** Kartta en çok dört kayıtlı değer: önce kart listesi, eksik kalırsa ayrıntı listesinden tamamlanır. */
export function cardSpecKeys(product: Pick<Product, "category" | "specifications" | "source">, limit = 4): SpecKey[] {
  const profile = specProfiles[product.category];
  if (!profile) return [];
  const ordered = [...profile.card, ...profile.detail.filter((key) => !profile.card.includes(key))];
  // Alt başlıkta zaten gösterilen alan (GPU çipi, CPU mimarisi) kart satırlarında tekrarlanmaz.
  const subtitleKey = product.category === "GPU" ? "chipset" : product.category === "CPU" ? "microarchitecture" : undefined;
  return ordered.filter((key) => key !== subtitleKey && readSpec(product, key)).slice(0, limit);
}

/**
 * Kategoriye göre API'nin gerçekten desteklediği ve veride dolu olan filtreler.
 * - range: elle girilen en az / en fazla sayı alanı (CPU hızı, TDP).
 * - exact: kayıtlı değerlerden seçilen açılır liste; API'ye en az = en fazla olarak gider.
 * - select: kayıtlı değerlerden seçilen açılır liste; değer aynı adlı parametreye yazılır.
 * - toggle: onay kutusu; yalnız işaretliyse `true` gider, işaretsizken parametre hiç gönderilmez.
 * Açılır liste seçenekleri sabit değil, `GET /catalog/facets` ile katalogdaki gerçek değerlerden gelir.
 */
export type RangeKey = "CapacityGb" | "ClockMhz" | "CoreCount" | "PowerWatts";
export type SelectKey = "memoryType" | "socket" | "interfaceType" | "formFactor" | "chipset" | "storageType" | "maximumMemoryGb" | "memorySlots" | "casLatency";
export type ToggleKey = "integratedGraphics";
export type FilterField = { kind: "range"; key: RangeKey } | { kind: "exact"; key: RangeKey } | { kind: "select"; key: SelectKey } | { kind: "toggle"; key: ToggleKey };
export const categoryFilters: Partial<Record<Category, FilterField[]>> = {
  CPU: [{ kind: "exact", key: "CoreCount" }, { kind: "range", key: "ClockMhz" }, { kind: "range", key: "PowerWatts" }, { kind: "toggle", key: "integratedGraphics" }],
  GPU: [{ kind: "select", key: "chipset" }, { kind: "exact", key: "CapacityGb" }],
  MOTHERBOARD: [{ kind: "select", key: "socket" }, { kind: "select", key: "formFactor" }, { kind: "select", key: "maximumMemoryGb" }, { kind: "select", key: "memorySlots" }],
  RAM: [{ kind: "select", key: "memoryType" }, { kind: "exact", key: "CapacityGb" }, { kind: "exact", key: "ClockMhz" }, { kind: "select", key: "casLatency" }],
  STORAGE: [{ kind: "select", key: "storageType" }, { kind: "exact", key: "CapacityGb" }, { kind: "select", key: "interfaceType" }]
};

const lowerFirst = (value: string) => `${value[0].toLowerCase()}${value.slice(1)}`;
/** Alanın spec adı: `fields.<KATEGORİ>.<ad>` çeviri anahtarı ve facet anahtarı. */
export const specName = (field: FilterField): string => field.kind === "select" || field.kind === "toggle" ? field.key : lowerFirst(field.key);
/** Formdaki seçim alanının adı; `exact` alanlar gönderimde en az/en fazla çiftine çevrilir. */
export const exactFieldName = (key: RangeKey) => `exact${key}`;

const noLabels = { yes: "", no: "" };
/** Açılır liste seçeneğinin görünen yazısı: birimler eklenir, değer (value) değişmez. */
export function filterOptionLabel(name: string, option: { value: string; label: string }, locale: string): string {
  const amount = Number(option.value);
  if (option.value.trim() === "" || !Number.isFinite(amount)) return option.label;
  if (name === "capacityGb" || name === "maximumMemoryGb") return formatSpec({ number: amount, text: null, unit: "GB" }, locale, noLabels) ?? option.label;
  if (name === "clockMhz") return formatSpec({ number: amount, text: null, unit: "MHz" }, locale, noLabels) ?? option.label;
  if (name === "casLatency") return `CL${option.value}`;
  return option.label;
}

/** Kategoriye ait olmayan teknik filtre parametrelerini sorgudan çıkarır (kategori değişince eski değer kalmasın). */
export const technicalQueryKeys = ["minCapacityGb", "maxCapacityGb", "minClockMhz", "maxClockMhz", "minCoreCount", "maxCoreCount", "minPowerWatts", "maxPowerWatts", "memoryType", "socket", "interfaceType", "formFactor", "chipset", "storageType", "maximumMemoryGb", "memorySlots", "casLatency", "integratedGraphics"] as const;

/** "Üretici + model": üretici adı modelin başında zaten geçiyorsa tekrarlanmaz. */
export function productName(product: Pick<Product, "model" | "brands">): string {
  const brand = product.brands[0]?.name;
  if (!brand || product.model.toLowerCase().startsWith(brand.toLowerCase())) return product.model;
  return `${brand} ${product.model}`;
}

/** Kartta/başlıkta ürünü ayırt eden ikinci satır: GPU çipi, CPU mimarisi, RAM modül dizilimi. Veri yoksa boş. */
export function productSubtitle(product: Pick<Product, "category" | "brands" | "specifications" | "source">): string | undefined {
  if (product.category === "GPU") return readSpec(product, "chipset")?.text ?? undefined;
  if (product.category === "CPU") return readSpec(product, "microarchitecture")?.text ?? undefined;
  if (product.category === "RAM") {
    const count = readSpec(product, "moduleCount")?.number;
    const size = readSpec(product, "moduleCapacityGb")?.number;
    if (count && size) return `${count} × ${size} GB`;
  }
  return undefined;
}
