/**
 * V3 piksel-art üretim motoru. Tarayıcıda çalışmaz; yalnız sprite sayfası üretiminde ve testte kullanılır.
 * Her piksel bir palet anahtarı (tek karakter) taşır; "." boş (saydam) demektir.
 */
export const EMPTY = ".";

export type Pt = readonly [number, number];

export class PixelCanvas {
  readonly data: string[];

  constructor(
    readonly width: number,
    readonly height: number
  ) {
    this.data = new Array<string>(width * height).fill(EMPTY);
  }

  get(x: number, y: number): string {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
      return EMPTY;
    }
    return this.data[y * this.width + x];
  }

  set(x: number, y: number, key: string): void {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height || key === EMPTY) {
      return;
    }
    this.data[y * this.width + x] = key;
  }

  filled(x: number, y: number): boolean {
    return this.get(x, y) !== EMPTY;
  }

  /** Üstteki tuvali bunun üzerine çizer (boş pikseller altı göstermeye devam eder). */
  draw(top: PixelCanvas, dx = 0, dy = 0): void {
    for (let y = 0; y < top.height; y += 1) {
      for (let x = 0; x < top.width; x += 1) {
        const key = top.data[y * top.width + x];
        if (key !== EMPTY) {
          this.set(x + dx, y + dy, key);
        }
      }
    }
  }

  clone(): PixelCanvas {
    const copy = new PixelCanvas(this.width, this.height);
    copy.data.splice(0, copy.data.length, ...this.data);
    return copy;
  }

  /** Yatay ayna (sağa bakan poz için). */
  mirrored(): PixelCanvas {
    const copy = new PixelCanvas(this.width, this.height);
    for (let y = 0; y < this.height; y += 1) {
      for (let x = 0; x < this.width; x += 1) {
        copy.data[y * this.width + (this.width - 1 - x)] = this.data[y * this.width + x];
      }
    }
    return copy;
  }
}

/**
 * ASCII ızgarayı (x, y) sol üst köşesine basar. `anchor` verilirse o hücre (x, y) noktasına oturur.
 * `flip` ızgarayı yatay aynalar.
 */
export function stamp(
  canvas: PixelCanvas,
  grid: readonly string[],
  x: number,
  y: number,
  options: { anchor?: Pt; flip?: boolean } = {}
): void {
  const [ax, ay] = options.anchor ?? [0, 0];
  const width = Math.max(...grid.map((row) => row.length));
  grid.forEach((row, rowIndex) => {
    for (let column = 0; column < row.length; column += 1) {
      const key = row[column];
      const gx = options.flip ? width - 1 - column : column;
      const gax = options.flip ? width - 1 - ax : ax;
      canvas.set(x + gx - gax, y + rowIndex - ay, key);
    }
  });
}

/** Piksel merkezini (x+0.5, y+0.5) içeren tüm pikselleri doldurur (çift-tek kuralı). */
export function fillPolygon(canvas: PixelCanvas, points: readonly Pt[], key: string): void {
  const ys = points.map((p) => p[1]);
  const minY = Math.max(0, Math.floor(Math.min(...ys)));
  const maxY = Math.min(canvas.height - 1, Math.ceil(Math.max(...ys)));
  for (let y = minY; y <= maxY; y += 1) {
    const cy = y + 0.5;
    const crossings: number[] = [];
    for (let i = 0; i < points.length; i += 1) {
      const [x0, y0] = points[i];
      const [x1, y1] = points[(i + 1) % points.length];
      if ((y0 <= cy && y1 > cy) || (y1 <= cy && y0 > cy)) {
        crossings.push(x0 + ((cy - y0) / (y1 - y0)) * (x1 - x0));
      }
    }
    crossings.sort((a, b) => a - b);
    for (let i = 0; i + 1 < crossings.length; i += 2) {
      const start = Math.ceil(crossings[i] - 0.5);
      const end = Math.floor(crossings[i + 1] - 0.5);
      for (let x = start; x <= end; x += 1) {
        canvas.set(x, y, key);
      }
    }
  }
}

export function fillDisc(canvas: PixelCanvas, cx: number, cy: number, radius: number, key: string): void {
  for (let y = Math.floor(cy - radius); y <= Math.ceil(cy + radius); y += 1) {
    for (let x = Math.floor(cx - radius); x <= Math.ceil(cx + radius); x += 1) {
      const dx = x + 0.5 - cx;
      const dy = y + 0.5 - cy;
      if (dx * dx + dy * dy <= radius * radius) {
        canvas.set(x, y, key);
      }
    }
  }
}

/** a noktasından b noktasına, uç yarıçapları ra → rb olan yuvarlak uçlu uzuv. */
export function fillLimb(canvas: PixelCanvas, a: Pt, b: Pt, ra: number, rb: number, key: string): void {
  const dx = b[0] - a[0];
  const dy = b[1] - a[1];
  const length = Math.hypot(dx, dy) || 1;
  const nx = -dy / length;
  const ny = dx / length;
  fillPolygon(
    canvas,
    [
      [a[0] + nx * ra, a[1] + ny * ra],
      [b[0] + nx * rb, b[1] + ny * rb],
      [b[0] - nx * rb, b[1] - ny * rb],
      [a[0] - nx * ra, a[1] - ny * ra]
    ],
    key
  );
  fillDisc(canvas, a[0], a[1], ra, key);
  fillDisc(canvas, b[0], b[1], rb, key);
}

/** Bresenham çizgisi (1 px). */
export function drawLine(canvas: PixelCanvas, a: Pt, b: Pt, key: string): void {
  let x0 = Math.round(a[0]);
  let y0 = Math.round(a[1]);
  const x1 = Math.round(b[0]);
  const y1 = Math.round(b[1]);
  const dx = Math.abs(x1 - x0);
  const dy = -Math.abs(y1 - y0);
  const sx = x0 < x1 ? 1 : -1;
  const sy = y0 < y1 ? 1 : -1;
  let error = dx + dy;
  for (;;) {
    canvas.set(x0, y0, key);
    if (x0 === x1 && y0 === y1) {
      return;
    }
    const doubled = 2 * error;
    if (doubled >= dy) {
      error += dy;
      x0 += sx;
    }
    if (doubled <= dx) {
      error += dx;
      y0 += sy;
    }
  }
}

/**
 * Tek renkli maskeyi üç tonlu rampaya çevirir: ışığın geldiği (sol / üst) kenar açık,
 * karşı (sağ / alt) kenar koyu, iç kısım orta ton.
 */
export function shadeMask(mask: PixelCanvas, ramp: readonly [dark: string, mid: string, light: string]): PixelCanvas {
  const out = new PixelCanvas(mask.width, mask.height);
  for (let y = 0; y < mask.height; y += 1) {
    for (let x = 0; x < mask.width; x += 1) {
      if (!mask.filled(x, y)) {
        continue;
      }
      const lit = !mask.filled(x - 1, y) || !mask.filled(x, y - 1);
      const shadow = !mask.filled(x + 1, y) || !mask.filled(x, y + 1) || !mask.filled(x + 1, y + 1);
      out.set(x, y, lit && !shadow ? ramp[2] : shadow ? ramp[0] : ramp[1]);
    }
  }
  return out;
}

/**
 * Parçanın çevresine 1 px kontur çizer. `onlyEmpty` doğruysa kontur yalnız hedefteki boş piksellere düşer
 * (gövdeye yapışık parçalarda dikiş oluşmaz); değilse ön parça arkadakinden çizgiyle ayrılır.
 */
export function outlineOnto(target: PixelCanvas, part: PixelCanvas, key: string, onlyEmpty: boolean): void {
  for (let y = 0; y < part.height; y += 1) {
    for (let x = 0; x < part.width; x += 1) {
      if (part.filled(x, y)) {
        continue;
      }
      const touches =
        part.filled(x - 1, y) || part.filled(x + 1, y) || part.filled(x, y - 1) || part.filled(x, y + 1);
      if (touches && (!onlyEmpty || !target.filled(x, y))) {
        target.set(x, y, key);
      }
    }
  }
}

/** İki kemikli ters kinematik: omuz/kalça → dirsek/diz → el/bilek. `bend` +1/-1 dirseğin yönünü seçer. */
export function solveJoint(root: Pt, end: Pt, upper: number, lower: number, bend: 1 | -1): Pt {
  const dx = end[0] - root[0];
  const dy = end[1] - root[1];
  const distance = Math.min(Math.hypot(dx, dy), upper + lower - 0.001);
  const base = Math.atan2(dy, dx);
  const cos = (upper * upper + distance * distance - lower * lower) / (2 * upper * distance || 1);
  const angle = base + bend * Math.acos(Math.max(-1, Math.min(1, cos)));
  return [root[0] + Math.cos(angle) * upper, root[1] + Math.sin(angle) * upper];
}
