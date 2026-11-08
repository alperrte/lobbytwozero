import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { MediaCarousel, type MediaItem } from "./media-carousel";

const item = (n: number, width?: number, height?: number): MediaItem => ({ key: `k${n}`, src: `http://x.test/${n}.png`, alt: `Görsel ${n}`, width, height });
const THREE = [item(1, 1200, 600), item(2, 600, 900), item(3, 500, 500)];

function mount(items: MediaItem[], extra: Partial<React.ComponentProps<typeof MediaCarousel>> = {}) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr}>
      <MediaCarousel items={items} variant="feed" label="Test galerisi" {...extra} />
    </NextIntlClientProvider>
  );
}

describe("MediaCarousel", () => {
  it("bütün fotoğraflar (dikey/yatay/kare) aynı 4:3, en çok 640 px'lik, ortalı ve kenarlıksız medya alanını kullanır", () => {
    for (const [width, height] of [
      [600, 900],
      [1600, 900],
      [500, 500]
    ] as const) {
      const view = mount([item(1, width, height)]);
      const frame = screen.getByRole("region", { name: "Test galerisi" });
      expect(frame.className).toContain("aspect-[4/3]");
      expect(frame.className).toContain("max-w-[40rem]");
      expect(frame.className).toContain("mx-auto");
      expect(frame.className).toContain("w-full");
      expect(frame.className).not.toMatch(/\bborder\b|bg-background/);
      expect(frame.getAttribute("style")).toBeNull();
      view.unmount();
    }
  });

  it("hiçbir fotoğrafın hiçbir kenarı kesilmez: object-contain + object-center, kırpma/yakınlaştırma/dönüşüm kuralı yok", () => {
    for (const [width, height] of [
      [600, 900],
      [1600, 900],
      [500, 500]
    ] as const) {
      const view = mount([item(1, width, height)]);
      const img = screen.getByRole("img", { name: "Görsel 1", hidden: true });
      expect(img.className).toContain("object-contain");
      expect(img.className).toContain("object-center");
      expect(img.className).not.toMatch(/object-cover|scale-|min-w-|min-h-|\bw-\[|\bh-\[|translate/);
      // Boş kalan alan kartın kendi arka planıdır: çerçevede arka plan, kenarlık ve köşe kırpması yoktur.
      const frame = screen.getByRole("region", { name: "Test galerisi" });
      expect(frame.className).not.toMatch(/\bborder\b|bg-|rounded/);
      view.unmount();
    }
  });

  it("görüntüleyici (viewer) de fotoğrafın tamamını gösterir (object-contain)", () => {
    mount([item(1, 600, 900)], { variant: "viewer" });
    expect(screen.getByRole("img", { name: "Görsel 1", hidden: true }).className).toContain("object-contain");
  });

  it("slider'da fotoğraf değişince medya alanı değişmez: tüm slaytlar aynı sabit çerçevede, her biri kendi oranıyla contain ile görünür", () => {
    mount(THREE);
    const frame = screen.getByRole("region", { name: "Test galerisi" });
    const before = frame.className;
    const images = screen.getAllByRole("img", { hidden: true });
    expect(images).toHaveLength(3);
    images.forEach((img) => expect(img.className).toContain("object-contain"));
    fireEvent.click(screen.getByRole("button", { name: "Sonraki görsel" }));
    fireEvent.click(screen.getByRole("button", { name: "Sonraki görsel" }));
    expect(frame.className).toBe(before);
    expect(frame.className).toContain("aspect-[4/3]");
  });

  it("tek görselde ok, sayaç ve nokta göstermez; çerçeve yine aynıdır", () => {
    mount([item(1, 800, 600)]);
    expect(screen.getByRole("region", { name: "Test galerisi" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sonraki görsel" })).toBeNull();
    expect(screen.queryByText("1/1")).toBeNull();
  });

  it("sayaç, noktalar ve oklar bulunur; oklar her zaman görünür (hover'a bağlı değil)", () => {
    mount(THREE);
    expect(screen.getByText("1/3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Önceki görsel" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Sonraki görsel" })).toBeEnabled();
    expect(screen.getAllByRole("button", { name: /görsele git/ })).toHaveLength(3);
    expect(screen.getByRole("button", { name: "1. görsele git" })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "Sonraki görsel" }).className).not.toMatch(/opacity-0|group-hover:opacity/);
  });

  it("ok düğmeleri ve noktalar sayfayı değiştirir; uçlarda devre dışı kalır; durum duyurulur", () => {
    mount(THREE);
    fireEvent.click(screen.getByRole("button", { name: "Sonraki görsel" }));
    expect(screen.getByText("2/3")).toBeInTheDocument();
    expect(screen.getByText("Görsel 2/3")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "3. görsele git" }));
    expect(screen.getByText("3/3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sonraki görsel" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Önceki görsel" }));
    expect(screen.getByText("2/3")).toBeInTheDocument();
  });

  it("klavye: ok tuşları, Home ve End", () => {
    mount(THREE);
    const scroller = screen.getByRole("region", { name: "Test galerisi" }).querySelector("[tabindex='0']") as HTMLElement;
    fireEvent.keyDown(scroller, { key: "ArrowRight" });
    expect(screen.getByText("2/3")).toBeInTheDocument();
    fireEvent.keyDown(scroller, { key: "End" });
    expect(screen.getByText("3/3")).toBeInTheDocument();
    fireEvent.keyDown(scroller, { key: "ArrowRight" });
    expect(screen.getByText("3/3")).toBeInTheDocument();
    fireEvent.keyDown(scroller, { key: "Home" });
    expect(screen.getByText("1/3")).toBeInTheDocument();
    fireEvent.keyDown(scroller, { key: "ArrowLeft" });
    expect(screen.getByText("1/3")).toBeInTheDocument();
  });

  it("görünmeyen sayfalar ekran okuyucudan gizlenir ve kaydırma-yakalamayı bozan inert kullanılmaz", () => {
    mount(THREE);
    const slides = screen.getByRole("region", { name: "Test galerisi" }).querySelectorAll("[role='group']");
    expect(slides[0]).not.toHaveAttribute("aria-hidden");
    expect(slides[1]).toHaveAttribute("aria-hidden", "true");
    slides.forEach((slide) => expect(slide).not.toHaveAttribute("inert"));
  });

  it("dışarıdan denetlenir: öğe sayısı azalınca aktif sayfa kısılır, sıra değişince verilen sayfa gösterilir", () => {
    function Harness() {
      const [items, setItems] = useState(THREE);
      const [index, setIndex] = useState(2);
      return (
        <NextIntlClientProvider locale="tr" messages={tr}>
          <MediaCarousel items={items} variant="preview" label="Denetimli" index={index} onIndexChange={setIndex} />
          <button onClick={() => setItems((current) => current.slice(0, 2))}>kaldır</button>
          <button onClick={() => setIndex(0)}>başa</button>
        </NextIntlClientProvider>
      );
    }
    render(<Harness />);
    expect(screen.getByText("3/3")).toBeInTheDocument();
    fireEvent.click(screen.getByText("kaldır"));
    expect(screen.getByText("2/2")).toBeInTheDocument();
    fireEvent.click(screen.getByText("başa"));
    expect(screen.getByText("1/2")).toBeInTheDocument();
  });

  it("kaydırma olayı aktif sayfayı günceller ve durunca en yakın sayfaya hizalar", () => {
    vi.useFakeTimers();
    try {
      mount(THREE);
      const scroller = screen.getByRole("region", { name: "Test galerisi" }).querySelector("[tabindex='0']") as HTMLElement;
      const scrollTo = vi.fn();
      Object.defineProperty(scroller, "clientWidth", { configurable: true, value: 300 });
      Object.defineProperty(scroller, "scrollLeft", { configurable: true, writable: true, value: 330 });
      scroller.scrollTo = scrollTo as unknown as typeof scroller.scrollTo;
      fireEvent.scroll(scroller);
      expect(screen.getByText("2/3")).toBeInTheDocument();
      act(() => {
        vi.advanceTimersByTime(200);
      });
      expect(scrollTo).toHaveBeenCalledWith(expect.objectContaining({ left: 300 }));
    } finally {
      vi.useRealTimers();
    }
  });

  it("açma geri çağrısı yalnız verildiğinde düğme üretir", () => {
    const onOpen = vi.fn();
    mount(THREE, { onOpen });
    fireEvent.click(screen.getByRole("button", { name: "1. görseli tam boyutta aç" }));
    expect(onOpen).toHaveBeenCalledWith(0);
  });

  it("görsel yüklenemezse yer tutucu gösterir", () => {
    mount([item(1, 10, 10)]);
    fireEvent.error(screen.getByRole("img", { name: "Görsel 1" }));
    expect(screen.getByText("Görsel yüklenemedi")).toBeInTheDocument();
  });
});
