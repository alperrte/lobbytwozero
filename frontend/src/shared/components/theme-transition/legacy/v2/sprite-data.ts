/**
 * LTZ Operatör: V2 taktik geçişin özgün piksel karakteri (26 × 34 ızgara, sola bakar).
 * Boonie şapka, hafif sakal, taktik yelek, gece görüş gözlüğü ve küçük LTZ kırmızısı taşır;
 * hiçbir film/oyun karakterinin kopyası değildir. Veri parça (segment) listesidir:
 * `[satır, sütun, "metin"]`, metindeki her karakter bir palet rengidir, `.` boşluktur.
 * Parçalar `compileLayer` ile yatay koşullara (run) birleştirilip renk başına tek <path> olur;
 * böylece küçük bir SVG, ağ isteği olmadan ve keskin piksellerle çizilir.
 */
export const SPRITE_WIDTH = 26;
export const SPRITE_HEIGHT = 34;

export type SpriteSegment = readonly [row: number, column: number, text: string];

/** Palet anahtarları CSS'te `.c-<anahtar>` sınıfına ve `--op-<anahtar>` değişkenine karşılık gelir. */
export const spritePaletteKeys = [
  "h", // şapka
  "H", // şapka ışığı
  "j", // şapka gölgesi (kenar altı)
  "s", // cilt
  "S", // cilt gölgesi
  "e", // saç
  "b", // sakal
  "v", // yelek
  "V", // yelek ışığı
  "g", // donanım / ped
  "r", // LTZ kırmızısı
  "u", // pantolon / kol
  "U", // pantolon ışığı
  "k", // bot / silah siyahı
  "G", // silah gri
  "n", // gözlük gövdesi
  "l" // gözlük camı (yeşil parıltı)
] as const;

export type SpritePaletteKey = (typeof spritePaletteKeys)[number];

export type SpriteLayerName =
  | "body"
  | "legsStand"
  | "legsA"
  | "legsB"
  | "carry"
  | "aim"
  | "flare"
  | "salute"
  | "gogglesUp"
  | "gogglesDown";

const head: readonly SpriteSegment[] = [
  // Boonie şapka
  [1, 10, "hhhhhhh"],
  [2, 9, "hHHhhhhhh"],
  [3, 8, "rrhhhhhhhhh"],
  [4, 6, "hhhhhhhhhhhhhhh"],
  [5, 5, "jjjjjjjjjjjjjjjj"],
  // Yüz ve saç
  [6, 7, "sssssssseee"],
  [7, 7, "sskssSsseee"],
  [8, 6, "sssssssseee"],
  // Sakal ve boyun
  [9, 5, "bbbbbbbbbbee"],
  [10, 6, "bbbbbbbbb"],
  [11, 7, "bbbbbbb"],
  [12, 9, "SSSSSS"]
];

const torso: readonly SpriteSegment[] = [
  [13, 7, "vvvvvvvvvvvv"],
  [14, 6, "vvVVvvvvvvvvvvv"],
  [15, 6, "vVVrrvvvvvvvvvvv"],
  [16, 6, "vvvrrvvvvvvvvvvv"],
  [17, 6, "vggvggvvvvvvvvvv"],
  [18, 6, "vggvggvvvvvvvvvv"],
  [19, 6, "vvvvvvvvvvvvvvvv"],
  [20, 7, "uuuuuuuuuuuuuu"],
  [21, 7, "uuugUuuuuuuuuu"],
  [22, 7, "uuuuuuuuuuuuuu"],
  [23, 7, "uuuuuuuuuuuuuu"]
];

/** Durarken bacaklar (y 24–32). */
const legsStand: readonly SpriteSegment[] = [
  [24, 8, "uuuu"], [24, 14, "uuuuu"],
  [25, 8, "uuuu"], [25, 14, "uuuuu"],
  [26, 8, "uuuu"], [26, 14, "uuuuu"],
  [27, 8, "uUuu"], [27, 14, "uuUuu"],
  [28, 8, "uuuu"], [28, 14, "uuuuu"],
  [29, 8, "uuuu"], [29, 14, "uuuuu"],
  [30, 8, "uuuu"], [30, 14, "uuuuu"],
  [31, 7, "kkkkk"], [31, 14, "kkkkkk"],
  [32, 6, "kkkkkk"], [32, 14, "kkkkkkk"]
];

/** Yürüyüş adımı A: ön bacak ileri (sola), arka bacak geride. */
const legsA: readonly SpriteSegment[] = [
  [24, 7, "uuuu"], [24, 14, "uuuuu"],
  [25, 7, "uuuu"], [25, 15, "uuuuu"],
  [26, 6, "uuuu"], [26, 15, "uuuuu"],
  [27, 6, "uUuu"], [27, 16, "uuUuu"],
  [28, 5, "uuuu"], [28, 16, "uuuuu"],
  [29, 5, "uuuu"], [29, 17, "uuuu"],
  [30, 4, "uuuu"], [30, 17, "uuuu"],
  [31, 3, "kkkkkk"], [31, 17, "kkkkkk"],
  [32, 2, "kkkkkkk"], [32, 17, "kkkkkkk"]
];

/** Yürüyüş adımı B: bacaklar yan yana geçiyor. */
const legsB: readonly SpriteSegment[] = [
  [24, 10, "uuuuu"],
  [25, 10, "uuuuu"],
  [26, 10, "uuuuu"],
  [27, 10, "uUuuu"],
  [28, 10, "uuuuu"],
  [29, 10, "uuuuu"],
  [30, 10, "uuuuu"],
  [31, 9, "kkkkkkk"],
  [32, 8, "kkkkkkkk"]
];

/** Tüfek göğüs hizasında taşınır. */
const carry: readonly SpriteSegment[] = [
  [17, 6, "GG"],
  [17, 13, "UUUU"],
  [18, 1, "kkkkkkkkkkkkkUUUU"],
  [19, 3, "GGGGGGGGGGssUUU"],
  [20, 8, "kk"],
  [21, 8, "kk"],
  [16, 13, "UUUU"],
  [15, 13, "UUUU"],
  [14, 13, "UUUU"]
];

/** Nişan: tüfek öne uzatılmış; namlu ucu (x=1, y=14.5) sprite'ın sol kenarına yakın. */
const aim: readonly SpriteSegment[] = [
  [13, 15, "GGGGG"],
  [14, 1, "kkkkkkkkkkkkkkk"],
  [15, 3, "GGGGGGGGGGGG"],
  [15, 15, "UUUU"],
  [16, 6, "ssUUUUUUUUUUU"],
  [17, 10, "kk"]
];

/** Sinyal fişeği tabancası yukarı doğrultulmuş; uç (x=3, y=7). */
const flare: readonly SpriteSegment[] = [
  [7, 2, "rr"],
  [8, 2, "GG"],
  [9, 2, "GG"],
  [10, 2, "GG"],
  [11, 2, "GG"],
  [12, 2, "GG"],
  [13, 1, "kkkk"],
  [14, 2, "ssUUUUUUUUUUUUU"],
  [15, 4, "UUUUUUUUUUUUU"],
  [16, 14, "UUUU"],
  [17, 14, "UUUU"]
];

/** Selam / şapka ayarı: el şapka kenarında. */
const salute: readonly SpriteSegment[] = [
  [4, 4, "ss"],
  [5, 3, "ss"],
  [5, 5, "UU"],
  [6, 4, "UU"],
  [7, 3, "UU"],
  [8, 3, "UU"],
  [9, 3, "UU"],
  [10, 3, "UU"],
  [11, 3, "UU"],
  [12, 4, "UUUUU"],
  [13, 4, "UUUUUU"],
  [14, 5, "UUUU"]
];

/** Gece görüş gözlüğü şapkaya kaldırılmış. */
const gogglesUp: readonly SpriteSegment[] = [
  [2, 8, "nnnnnn"],
  [3, 8, "nlnnln"]
];

/** Gece görüş gözlüğü gözlerin önünde; camlar yeşil parlar. */
const gogglesDown: readonly SpriteSegment[] = [
  [6, 6, "nnnnnnnnnn"],
  [7, 6, "nllnnllnnn"],
  [8, 7, "nllnnlln"]
];

export const spriteLayers: Record<SpriteLayerName, readonly SpriteSegment[]> = {
  body: [...head, ...torso],
  legsStand,
  legsA,
  legsB,
  carry,
  aim,
  flare,
  salute,
  gogglesUp,
  gogglesDown
};

export type CompiledRun = { key: string; d: string };

/**
 * Parçaları ızgaraya yazar (sonraki parça öncekini ezer) ve renk başına tek bir SVG `d` verisi üretir:
 * her yatay koşul `M x y h w v1 h-w z` olur. Çıktı deterministiktir.
 */
export function compileLayer(segments: readonly SpriteSegment[]): CompiledRun[] {
  const grid: string[][] = Array.from({ length: SPRITE_HEIGHT }, () =>
    Array.from({ length: SPRITE_WIDTH }, () => ".")
  );

  for (const [row, column, text] of segments) {
    for (let offset = 0; offset < text.length; offset += 1) {
      const target = grid[row];
      if (target !== undefined && column + offset < SPRITE_WIDTH) {
        target[column + offset] = text.charAt(offset);
      }
    }
  }

  const byKey = new Map<string, string[]>();
  grid.forEach((cells, row) => {
    let column = 0;
    while (column < cells.length) {
      const key = cells[column];
      if (key === undefined || key === ".") {
        column += 1;
        continue;
      }
      let end = column + 1;
      while (end < cells.length && cells[end] === key) {
        end += 1;
      }
      const width = end - column;
      const list = byKey.get(key) ?? [];
      list.push(`M${column} ${row}h${width}v1h-${width}z`);
      byKey.set(key, list);
      column = end;
    }
  });

  return spritePaletteKeys
    .filter((key) => byKey.has(key))
    .map((key) => ({ key, d: (byKey.get(key) ?? []).join("") }));
}

export const compiledLayers = Object.fromEntries(
  (Object.keys(spriteLayers) as SpriteLayerName[]).map((name) => [name, compileLayer(spriteLayers[name])])
) as Record<SpriteLayerName, CompiledRun[]>;
