import { beforeEach, describe, expect, it, vi } from "vitest";
import { LIMITS } from "./community.schema";
import {
  classifyFiles,
  dimensionsAllowed,
  fileSignature,
  moveItem,
  prepareImages,
  sniffImageKind,
  type ImageInfo,
  type LocalImage
} from "./image-validation";

const PNG_HEAD = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0];
const JPEG_HEAD = [0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0, 0, 0, 0, 0];
const WEBP_HEAD = [0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50];

function file(name: string, type: string, head: number[] = PNG_HEAD, extra = 20, modified = 1): File {
  return new File([new Uint8Array([...head, ...new Array(extra).fill(1)])], name, { type, lastModified: modified });
}
const ok = (width = 800, height = 600): ((f: File) => Promise<ImageInfo | null>) => async () => ({ url: "blob:x", width, height });

beforeEach(() => {
  vi.stubGlobal("URL", Object.assign(URL, { revokeObjectURL: vi.fn(), createObjectURL: vi.fn(() => "blob:x") }));
});

describe("Görsel biçim ve boyut kuralları", () => {
  it("baytlardan gerçek biçimi tanır", () => {
    expect(sniffImageKind(new Uint8Array(PNG_HEAD))).toBe("png");
    expect(sniffImageKind(new Uint8Array(JPEG_HEAD))).toBe("jpeg");
    expect(sniffImageKind(new Uint8Array(WEBP_HEAD))).toBe("other");
    expect(sniffImageKind(new Uint8Array([]))).toBe("other");
  });

  it("6000 px kenar ve 12 MP sınırını uygular", () => {
    expect(dimensionsAllowed(6000, 2000)).toBe(true);
    expect(dimensionsAllowed(6001, 100)).toBe(false);
    expect(dimensionsAllowed(4000, 3001)).toBe(false);
    expect(dimensionsAllowed(0, 10)).toBe(false);
  });
});

describe("Çoklu seçim sınıflandırması", () => {
  it("geçerli dosyaları sırayla alır, geçersizleri nedenleriyle ayırır", () => {
    const files = [
      file("a.png", "image/png"),
      file("b.webp", "image/webp"),
      file("c.jpg", "image/jpeg", JPEG_HEAD),
      new File([], "bos.png", { type: "image/png" }),
      new File([new Uint8Array(LIMITS.imageBytes + 1)], "buyuk.png", { type: "image/png" })
    ];
    const { candidates, rejected } = classifyFiles(files, new Set(), 4);
    expect(candidates.map((item) => item.name)).toEqual(["a.png", "c.jpg"]);
    expect(rejected).toEqual([
      { name: "b.webp", reason: "type" },
      { name: "bos.png", reason: "empty" },
      { name: "buyuk.png", reason: "size" }
    ]);
  });

  it("tam 2 MiB kabul edilir", () => {
    const edge = new File([new Uint8Array(LIMITS.imageBytes)], "edge.png", { type: "image/png" });
    expect(classifyFiles([edge], new Set(), 4).candidates).toHaveLength(1);
  });

  it("aynı dosyayı yinelenen olarak reddeder (seçim içinde ve mevcutlara karşı)", () => {
    const a = file("a.png", "image/png");
    const again = file("a.png", "image/png");
    const sameNameOtherSize = file("a.png", "image/png", PNG_HEAD, 99);
    const { candidates, rejected } = classifyFiles([a, again, sameNameOtherSize], new Set([fileSignature(file("z.png", "image/png"))]), 4);
    expect(candidates).toHaveLength(2);
    expect(rejected).toEqual([{ name: "a.png", reason: "duplicate" }]);
    expect(classifyFiles([a], new Set([fileSignature(a)]), 4).rejected[0]?.reason).toBe("duplicate");
  });

  it("kalan yer kadar alır, fazlasını 'count' olarak bildirir; geçerli olanlar korunur", () => {
    const files = [1, 2, 3, 4, 5].map((n) => file(`f${n}.png`, "image/png", PNG_HEAD, 20, n));
    const { candidates, rejected } = classifyFiles(files, new Set(), 2);
    expect(candidates.map((item) => item.name)).toEqual(["f1.png", "f2.png"]);
    expect(rejected.map((item) => item.reason)).toEqual(["count", "count", "count"]);
  });
});

describe("prepareImages", () => {
  it("geçerli, WebP içeriği png adıyla gelen, çözülemeyen ve aşırı büyük boyutlu dosyaları ayırır", async () => {
    const good = file("iyi.png", "image/png");
    const fake = file("sahte.png", "image/png", WEBP_HEAD);
    const huge = file("dev.png", "image/png", PNG_HEAD, 20, 2);
    const broken = file("bozuk.png", "image/png", PNG_HEAD, 20, 3);
    const decode = vi.fn(async (item: File): Promise<ImageInfo | null> => {
      if (item === huge) return { url: "blob:h", width: 7000, height: 100 };
      if (item === broken) return null;
      return { url: "blob:g", width: 800, height: 600 };
    });
    const { added, rejected } = await prepareImages([good, fake, huge, broken], [], 0, decode);
    expect(added.map((item) => item.file.name)).toEqual(["iyi.png"]);
    expect(added[0]).toMatchObject({ width: 800, height: 600 });
    expect(rejected).toEqual(
      expect.arrayContaining([
        { name: "sahte.png", reason: "type" },
        { name: "dev.png", reason: "dimensions" },
        { name: "bozuk.png", reason: "unreadable" }
      ])
    );
    // Sahte (WebP içerikli) dosya çözümlemeye hiç girmez (kimlik karşılaştırması: dosyalar yapısal olarak eşit görünür).
    expect(decode.mock.calls.some(([candidate]) => candidate === fake)).toBe(false);
  });

  it("sunucudaki görselleri ve mevcut seçimleri toplam sınıra sayar", async () => {
    const existing: LocalImage[] = [{ key: "k", file: file("x.png", "image/png"), url: "blob:x", width: 1, height: 1 }];
    const files = [1, 2, 3].map((n) => file(`n${n}.png`, "image/png", PNG_HEAD, 20, n + 10));
    const { added, rejected } = await prepareImages(files, existing, 1, ok());
    expect(added).toHaveLength(2);
    expect(rejected).toEqual([{ name: "n3.png", reason: "count" }]);
  });

  it("anahtarlar benzersizdir", async () => {
    const { added } = await prepareImages([file("a.png", "image/png"), file("b.png", "image/png", PNG_HEAD, 20, 5)], [], 0, ok());
    expect(new Set(added.map((item) => item.key)).size).toBe(2);
  });
});

describe("moveItem", () => {
  it("öğeyi taşır, sınır dışında değiştirmez", () => {
    expect(moveItem(["a", "b", "c"], 0, 2)).toEqual(["b", "c", "a"]);
    expect(moveItem(["a", "b", "c"], 2, 1)).toEqual(["a", "c", "b"]);
    expect(moveItem(["a", "b"], 0, 5)).toEqual(["a", "b"]);
    expect(moveItem(["a", "b"], -1, 0)).toEqual(["a", "b"]);
  });
});
