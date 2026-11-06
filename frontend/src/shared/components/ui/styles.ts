/** Navbar kontrolleri çerçevesizdir; ayrım dolgu rengi ve hover ile yapılır (kırmızı vurgu yalnız navbar kenarında ve odakta). */
export const controlFrameClass = "transition-colors duration-150 ease-out";

/**
 * Kabuk bileşenlerinin paylaştığı Tailwind sınıf dizileri (yalnız semantik tokenlar).
 * Varsayılan hâli sadedir (dolgusuz); hover hafif yüzey, açık menü (aria-expanded) ise
 * daha belirgin yüzey + nötr halka ile hover'dan ayrışır.
 */
export const iconButtonClass = `inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-text-secondary hover:bg-surface-elevated/70 hover:text-text-primary aria-expanded:bg-surface-elevated aria-expanded:text-text-primary aria-expanded:ring-1 aria-expanded:ring-inset aria-expanded:ring-border sm:h-10 sm:w-10 ${controlFrameClass}`;

/** Navbar açılır paneli: mobilde başlığın tam genişliği, sm ve üstünde tetikleyiciye hizalı kart. */
export const popoverPanelClass =
  "absolute inset-x-0 top-full z-40 mt-2 rounded-xl border border-border bg-surface p-2 shadow-popover animate-pop-in sm:inset-x-auto sm:end-0";

export const menuItemClass =
  "flex min-h-10 w-full items-center gap-3 rounded-lg px-3 py-2 text-start text-sm font-medium text-text-primary transition-colors duration-150 hover:bg-surface-elevated focus-visible:bg-surface-elevated aria-disabled:cursor-default aria-disabled:text-text-secondary aria-disabled:hover:bg-transparent";

/** Çıkış satırı: marka kırmızısıyla vurgulu (metin için kontrastlı `brand-text` türevi). */
export const dangerMenuItemClass =
  "flex min-h-10 w-full items-center gap-3 rounded-lg border border-primary/40 bg-primary/10 px-3 py-2 text-start text-sm font-semibold text-brand-text transition-colors duration-150 hover:border-primary hover:bg-primary/20 focus-visible:bg-primary/20 aria-disabled:cursor-default";
