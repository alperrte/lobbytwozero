/**
 * Giriş/kayıt sayfalarının sol panelindeki arka plan videoları. Dosyalar `public/auth-videos/` altındadır: 1080x1080 (panel
 * kareye yakın olduğu için merkezden kırpılmış), 30 kare/sn, H.264 (CRF 22), sessiz; video başına ~2 MB, toplam ~12 MB.
 * Ziyaretçi her an en fazla iki videoyu (oynayan + sıradaki) indirir. Yeni video eklemek için dosyayı oraya koyup aşağıdaki
 * listeye bir satır ekle. Hazırlama: `ffmpeg -i kaynak.mp4 -an -vf "crop=1080:1080,fps=30" -c:v libx264 -preset slow -crf 22
 * -pix_fmt yuv420p -movflags +faststart bg-N.mp4`. (HEVC/H.265 kaynak kullanma: birçok tarayıcı oynatmaz.)
 *
 * `weight`: oynama oranı (göreli). Hepsi 1 ise eşit şans. Örn. bir videoyu iki kat sık görmek için 2 yaz.
 */
export const AUTH_BACKDROP = {
  poster: "/auth-videos/poster.jpg",
  /**
   * Bu turda oynamış videonun şansı bu çarpanla çarpılır. 0 = karıştırılmış deste: her video turda tam BİR kez oynar, hepsi
   * oynamadan hiçbiri tekrar gelmez; hepsi oynayınca tur başa döner (yeni tur sırası yine rastgele). 0'dan büyük bir değer
   * (örn. 0.15) oynamışları tamamen dışlamaz, yalnız seyrekleştirir.
   */
  playedWeightFactor: 0,
  /** İki video arasındaki çapraz solma süresi (sn). CSS geçişi (`duration-700`) ile aynı tutulur. */
  crossfadeSeconds: 0.7,
  videos: [
    { src: "/auth-videos/bg-1.mp4", weight: 1 },
    { src: "/auth-videos/bg-2.mp4", weight: 1 },
    { src: "/auth-videos/bg-3.mp4", weight: 1 },
    { src: "/auth-videos/bg-4.mp4", weight: 1 },
    { src: "/auth-videos/bg-5.mp4", weight: 1 },
    { src: "/auth-videos/bg-6.mp4", weight: 1 }
  ]
} as const;

export type PlaylistState = {
  /** Bu turda oynamış videoların dizinleri. Hepsi oynayınca boşaltılır (yeni tur). */
  played: ReadonlySet<number>;
  /** En son oynayan video: arka arkaya aynısı gelmez. */
  last: number | null;
};

export const initialPlaylistState: PlaylistState = { played: new Set(), last: null };

type ChooseOptions = {
  weights: readonly number[];
  state: PlaylistState;
  factor: number;
  rng: () => number;
};

/**
 * Ağırlıklı rastgele seçim. Kurallar:
 * 1. En son oynayan seçilemez (video sayısı 1'den fazlaysa).
 * 2. Bu turda oynamış videonun ağırlığı `factor` ile çarpılır (azalır ama sıfırlanmaz).
 * 3. Seçimden sonra tüm videolar bu turda oynamış olduysa tur sıfırlanır ve herkes normal ağırlığa döner.
 */
export function chooseNext({ weights, state, factor, rng }: ChooseOptions): { index: number; state: PlaylistState } {
  const count = weights.length;
  if (count === 0) {
    throw new Error("Oynatma listesi boş.");
  }
  const candidates = [...weights.keys()].filter((index) => count === 1 || index !== state.last);
  const effective = candidates.map((index) => {
    const base = Math.max(0, weights[index] ?? 0);
    return state.played.has(index) ? base * factor : base;
  });
  const total = effective.reduce((sum, value) => sum + value, 0);

  let chosen = candidates[candidates.length - 1] ?? 0;
  if (total > 0) {
    let target = rng() * total;
    for (let position = 0; position < candidates.length; position++) {
      target -= effective[position] ?? 0;
      if (target < 0) {
        chosen = candidates[position] ?? chosen;
        break;
      }
    }
  } else {
    chosen = candidates[Math.min(candidates.length - 1, Math.floor(rng() * candidates.length))] ?? chosen;
  }

  const played = new Set(state.played).add(chosen);
  return { index: chosen, state: { played: played.size >= count ? new Set() : played, last: chosen } };
}
