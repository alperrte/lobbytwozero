/**
 * Arayüz sınırları. Gönderi (2000) ve yorum/yanıt (1000) sınırları sunucunun kabul ettiği üst sınırlardan (gönderi 5000, yorum 2000)
 * daha SIKIDIR; oluşturma, hızlı paylaşım, düzenleme, yorum ve yanıt aynı ortak sabitleri kullanır. Eski uzun içerik okunur; düzenlenip kaydedilirken
 * yeni sınır uygulanır. Diğer sınırlar Community API doğrulamasıyla birebir aynıdır (docs/api.md → COMMUNITY).
 */
export const LIMITS = {
  postBody: 2000,
  title: 120,
  comment: 1000,
  repostNote: 500,
  reportDetail: 500,
  forumTitle: 120,
  forumDescription: 1000,
  slugMin: 3,
  slugMax: 80,
  imageBytes: 2 * 1024 * 1024,
  imagesPerPost: 4
} as const;

export const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png"] as const;

/** Çok satırlı metinde satır sonu serbesttir; diğer kontrol karakterleri reddedilir. */
const CONTROL = /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/;
const CONTROL_STRICT = /[\u0000-\u001f\u007f]/;

export type FieldError = "required" | "tooLong" | "invalidChars" | "tooShort" | "invalidSlug";

/** Düz metin: kırpılmış, (zorunluysa) boş olamaz, en çok `max`. */
export function validateText(
  value: string,
  max: number,
  options: { required?: boolean; multiline?: boolean } = {}
): FieldError | null {
  const trimmed = value.trim();
  if (trimmed === "") return options.required === false ? null : "required";
  if (trimmed.length > max) return "tooLong";
  return (options.multiline === false ? CONTROL_STRICT : CONTROL).test(trimmed) ? "invalidChars" : null;
}

export function validateSlug(value: string): FieldError | null {
  const slug = value.trim();
  if (slug.length < LIMITS.slugMin) return "tooShort";
  if (slug.length > LIMITS.slugMax) return "tooLong";
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) ? null : "invalidSlug";
}

/** Başlık yalnız oyuna bağlı türlü içerikte vardır ve satır sonu içeremez. */
export function validateTitle(value: string): FieldError | null {
  return validateText(value, LIMITS.title, { multiline: false });
}

export type ImageIssue = "type" | "size" | "count" | "empty";

export function validateImage(file: File, existingCount: number): ImageIssue | null {
  if (existingCount >= LIMITS.imagesPerPost) return "count";
  if (file.size === 0) return "empty";
  if (!(ACCEPTED_IMAGE_TYPES as readonly string[]).includes(file.type)) return "type";
  return file.size > LIMITS.imageBytes ? "size" : null;
}
