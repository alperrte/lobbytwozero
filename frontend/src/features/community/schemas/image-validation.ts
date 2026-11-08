import { ACCEPTED_IMAGE_TYPES, LIMITS } from "./community.schema";

/**
 * Görsel ön doğrulama. Sınırlar Community sunucusundaki `PostImage`/`ImageSanitizer` sabitleriyle birebir aynıdır:
 * gönderi başına en çok 4, dosya başına en çok 2 MiB, en çok 6000 px kenar, en çok 12 milyon piksel, yalnız gerçek
 * içeriği JPEG/PNG olan dosyalar. Asıl karar sunucudadır (yeniden kodlar, 413/415/422 döner); bu katman kullanıcıyı
 * yüklemeden önce bilgilendirir ve geçerli seçimleri kaybettirmez.
 */
export const MAX_SIDE_PIXELS = 6000;
export const MAX_PIXELS = 12_000_000;

export type RejectionReason = "type" | "size" | "empty" | "duplicate" | "count" | "dimensions" | "unreadable";
export type ImageRejection = { name: string; reason: RejectionReason };

/** Yüklenmeyi bekleyen yerel görsel: dosya + önizleme adresi + çözülmüş boyutlar. */
export type LocalImage = { key: string; file: File; url: string; width: number; height: number };

export type ImageInfo = { url: string; width: number; height: number };

/** Aynı dosyanın tekrar eklenmesini yakalar (ad + boyut + değişiklik zamanı). */
export function fileSignature(file: File): string {
  return `${file.name}|${file.size}|${file.lastModified}`;
}

export function dimensionsAllowed(width: number, height: number): boolean {
  return width > 0 && height > 0 && width <= MAX_SIDE_PIXELS && height <= MAX_SIDE_PIXELS && width * height <= MAX_PIXELS;
}

/** Dosyanın ilk baytlarından gerçek biçimi (uzantı ve `type` yalan söyleyebilir; sunucu da içeriğe bakar). */
export function sniffImageKind(bytes: Uint8Array): "jpeg" | "png" | "other" {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "jpeg";
  const png = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];
  if (bytes.length >= png.length && png.every((value, index) => bytes[index] === value)) return "png";
  return "other";
}

/** Eşzamanlı (dosya meta verisiyle) kontroller: boş, tür, boyut, yinelenen ve kalan yer. Sıra korunur. */
export function classifyFiles(
  files: readonly File[],
  known: ReadonlySet<string>,
  slotsLeft: number
): { candidates: File[]; rejected: ImageRejection[] } {
  const candidates: File[] = [];
  const rejected: ImageRejection[] = [];
  const seen = new Set(known);
  for (const file of files) {
    const signature = fileSignature(file);
    if (file.size === 0) rejected.push({ name: file.name, reason: "empty" });
    else if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) rejected.push({ name: file.name, reason: "type" });
    else if (file.size > LIMITS.imageBytes) rejected.push({ name: file.name, reason: "size" });
    else if (seen.has(signature)) rejected.push({ name: file.name, reason: "duplicate" });
    else if (candidates.length >= slotsLeft) rejected.push({ name: file.name, reason: "count" });
    else {
      seen.add(signature);
      candidates.push(file);
    }
  }
  return { candidates, rejected };
}

async function readHead(file: File): Promise<Uint8Array | null> {
  try {
    return new Uint8Array(await file.slice(0, 12).arrayBuffer());
  } catch {
    return null;
  }
}

/** Tarayıcıda çözümleyerek boyutları okur; çözülemezse `null` (bozuk ya da görsel değil). */
export function decodeImageInfo(file: File): Promise<ImageInfo | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => resolve({ url, width: image.naturalWidth, height: image.naturalHeight });
    image.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    image.src = url;
  });
}

let counter = 0;

/**
 * Seçilen dosyaları doğrular ve çözer. Geçerli olanlar seçim sırasıyla `added` olur; geçersizler dosya adı ve nedenle
 * `rejected` olur. Bir dosyanın reddi diğerlerini etkilemez (mevcut seçimler korunur).
 */
export async function prepareImages(
  files: readonly File[],
  existing: readonly LocalImage[],
  remoteCount: number,
  decode: (file: File) => Promise<ImageInfo | null> = decodeImageInfo
): Promise<{ added: LocalImage[]; rejected: ImageRejection[] }> {
  const slotsLeft = Math.max(0, LIMITS.imagesPerPost - remoteCount - existing.length);
  const { candidates, rejected } = classifyFiles(files, new Set(existing.map((image) => fileSignature(image.file))), slotsLeft);
  const added: LocalImage[] = [];
  for (const file of candidates) {
    const head = await readHead(file);
    if (head && sniffImageKind(head) === "other") {
      rejected.push({ name: file.name, reason: "type" });
      continue;
    }
    const info = await decode(file);
    if (!info) {
      rejected.push({ name: file.name, reason: "unreadable" });
    } else if (!dimensionsAllowed(info.width, info.height)) {
      URL.revokeObjectURL(info.url);
      rejected.push({ name: file.name, reason: "dimensions" });
    } else {
      counter += 1;
      added.push({ key: `img-${counter}`, file, url: info.url, width: info.width, height: info.height });
    }
  }
  return { added, rejected };
}

export function releaseImage(image: LocalImage): void {
  URL.revokeObjectURL(image.url);
}

/** `from` konumundaki öğeyi `to` konumuna taşır (yeni dizi). Sınır dışı indeks değişiklik yapmaz. */
export function moveItem<T>(list: readonly T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return [...list];
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item as T);
  return next;
}
