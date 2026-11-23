import { describe, expect, it, vi } from "vitest";
import { fitWithin, ImagePrepareError, MAX_UPLOAD_BYTES, prepareImage, type ImageCodec } from "./image-prepare";

function file(type: string, size: number) {
  return new File([new Uint8Array(size)], "x", { type });
}

function codec(width: number, height: number, encodedSizes: Array<number | null>): ImageCodec & { encode: ReturnType<typeof vi.fn> } {
  const sizes = [...encodedSizes];
  return {
    decode: vi.fn(async () => ({ width, height, draw: vi.fn(), close: vi.fn() })),
    encode: vi.fn(async (_canvas: HTMLCanvasElement, type: string) => {
      const size = sizes.shift();
      return size === null || size === undefined ? null : new Blob([new Uint8Array(size)], { type });
    })
  };
}

describe("fitWithin", () => {
  it("uzun kenarı 1600 px'e orantılı indirir, küçük görseli büyütmez", () => {
    expect(fitWithin(4000, 2000)).toEqual({ width: 1600, height: 800 });
    expect(fitWithin(1000, 3200)).toEqual({ width: 500, height: 1600 });
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  });
});

describe("prepareImage", () => {
  it("izin listesi dışındaki türü reddeder (SVG, WebP)", async () => {
    await expect(prepareImage(file("image/svg+xml", 10))).rejects.toMatchObject({ key: "unsupportedImage" });
    await expect(prepareImage(file("image/webp", 10))).rejects.toBeInstanceOf(ImagePrepareError);
  });

  it("GIF'i yeniden kodlamaz; 1 MB'ı aşarsa reddeder", async () => {
    const small = file("image/gif", 1000);
    await expect(prepareImage(small)).resolves.toMatchObject({ blob: small });
    await expect(prepareImage(file("image/gif", MAX_UPLOAD_BYTES + 1))).rejects.toMatchObject({ key: "imageTooLargeClient" });
  });

  it("küçük ve hafif görseli olduğu gibi bırakır", async () => {
    const original = file("image/png", 2000);
    const fake = codec(800, 600, []);
    await expect(prepareImage(original, fake)).resolves.toEqual({ blob: original, width: 800, height: 600 });
    expect(fake.encode).not.toHaveBeenCalled();
  });

  it("büyük görseli 1600 px'e küçültür; PNG hâlâ büyükse JPEG'e çevirir", async () => {
    const fake = codec(3200, 1600, [MAX_UPLOAD_BYTES + 5, 400_000]);
    const result = await prepareImage(file("image/png", 3_000_000), fake);
    expect(result).toMatchObject({ width: 1600, height: 800 });
    expect(result.blob.type).toBe("image/jpeg");
    expect(fake.encode.mock.calls.map((call) => call[1])).toEqual(["image/png", "image/jpeg"]);
  });

  it("küçültme sonrası da 1 MB'ı aşarsa ya da çözülemezse reddeder", async () => {
    await expect(prepareImage(file("image/jpeg", 3_000_000), codec(1000, 1000, [MAX_UPLOAD_BYTES + 1, MAX_UPLOAD_BYTES + 1]))).rejects.toMatchObject({
      key: "imageTooLargeClient"
    });
    const broken: ImageCodec = { decode: () => Promise.reject(new Error("bozuk")), encode: vi.fn() };
    await expect(prepareImage(file("image/png", 10), broken)).rejects.toMatchObject({ key: "invalidImageClient" });
  });
});
