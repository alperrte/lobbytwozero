/**
 * Gönderim öncesi görsel hazırlığı (MSG-17, web-rules §7.2). Nihai doğrulama sunucudadır (magic byte, boyut, EXIF temizliği);
 * istemci yalnız gereksiz yüklemeyi önler:
 * - Yalnız PNG, JPEG ve GIF kabul edilir (sunucunun izin listesi).
 * - GIF yeniden kodlanmaz (animasyon korunur); 1 MB'ı aşarsa reddedilir.
 * - PNG/JPEG uzun kenarı 1600 px'i ya da boyutu 1 MB'ı aşıyorsa tuvalde küçültülür (sunucu işi ve veri kullanımı azalır).
 *   PNG küçültmeden sonra hâlâ büyükse JPEG'e çevrilir. Hâlâ 1 MB'ı aşarsa reddedilir.
 */
export const MAX_UPLOAD_BYTES = 1_048_576;
export const MAX_EDGE_PX = 1600;
export const ACCEPTED_IMAGE_TYPES = ["image/png", "image/jpeg", "image/gif"] as const;

export type PreparedImage = { blob: Blob; width: number | null; height: number | null };

export type PrepareErrorKey = "unsupportedImage" | "imageTooLargeClient" | "invalidImageClient";

export class ImagePrepareError extends Error {
  constructor(public readonly key: PrepareErrorKey) {
    super(key);
    this.name = "ImagePrepareError";
  }
}

export function isAcceptedImageType(type: string): boolean {
  return (ACCEPTED_IMAGE_TYPES as readonly string[]).includes(type);
}

/** Orantılı ölçek: uzun kenar `maxEdge`'i aşmaz, küçük görsel büyütülmez. */
export function fitWithin(width: number, height: number, maxEdge = MAX_EDGE_PX): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (longest <= maxEdge || longest === 0) {
    return { width, height };
  }
  const scale = maxEdge / longest;
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

function encode(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

export type ImageCodec = {
  decode: (file: Blob) => Promise<{ width: number; height: number; draw: (canvas: HTMLCanvasElement) => void; close: () => void }>;
  encode: (canvas: HTMLCanvasElement, type: string, quality?: number) => Promise<Blob | null>;
};

const browserCodec: ImageCodec = {
  async decode(file) {
    // `from-image`: JPEG EXIF yönü uygulanır (telefonda yan dönmüş fotoğraf).
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    return {
      width: bitmap.width,
      height: bitmap.height,
      draw: (canvas) => canvas.getContext("2d")?.drawImage(bitmap, 0, 0, canvas.width, canvas.height),
      close: () => bitmap.close()
    };
  },
  encode
};

export async function prepareImage(file: File, codec: ImageCodec = browserCodec): Promise<PreparedImage> {
  if (!isAcceptedImageType(file.type)) {
    throw new ImagePrepareError("unsupportedImage");
  }
  if (file.type === "image/gif") {
    if (file.size > MAX_UPLOAD_BYTES) {
      throw new ImagePrepareError("imageTooLargeClient");
    }
    return { blob: file, width: null, height: null };
  }

  let decoded: Awaited<ReturnType<ImageCodec["decode"]>>;
  try {
    decoded = await codec.decode(file);
  } catch {
    throw new ImagePrepareError("invalidImageClient");
  }
  try {
    const target = fitWithin(decoded.width, decoded.height);
    const needsResize = target.width !== decoded.width || target.height !== decoded.height;
    if (!needsResize && file.size <= MAX_UPLOAD_BYTES) {
      return { blob: file, width: decoded.width, height: decoded.height };
    }
    const canvas = document.createElement("canvas");
    canvas.width = target.width;
    canvas.height = target.height;
    decoded.draw(canvas);

    let blob = await codec.encode(canvas, file.type, file.type === "image/jpeg" ? 0.85 : undefined);
    if (!blob || blob.size > MAX_UPLOAD_BYTES) {
      blob = await codec.encode(canvas, "image/jpeg", 0.8);
    }
    if (!blob || blob.size > MAX_UPLOAD_BYTES) {
      throw new ImagePrepareError("imageTooLargeClient");
    }
    return { blob, width: target.width, height: target.height };
  } finally {
    decoded.close();
  }
}
