import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AUTH_BACKDROP } from "../services/backdrop-playlist";
import { AuthBackdrop } from "./auth-backdrop";

function mockMedia(matches: Record<string, boolean>) {
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({
        matches: matches[query] ?? false,
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn()
      }) as unknown as MediaQueryList
  );
}

const WIDE = "(min-width: 1024px)";
const REDUCED = "(prefers-reduced-motion: reduce)";

beforeEach(() => {
  vi.spyOn(HTMLMediaElement.prototype, "play").mockResolvedValue(undefined);
  vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => undefined);
  vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("AuthBackdrop", () => {
  it("geniş ekranda iki sessiz video etiketi çizer; ikisine de oynatma listesinden kaynak verir", () => {
    mockMedia({ [WIDE]: true });
    const { container } = render(<AuthBackdrop />);

    const videos = container.querySelectorAll("video");
    expect(videos).toHaveLength(2);
    const known = AUTH_BACKDROP.videos.map((video) => video.src);
    for (const video of videos) {
      expect(video.muted).toBe(true);
      expect(known).toContain(new URL(video.src).pathname);
      expect(video.hasAttribute("controls")).toBe(false);
    }
    // İki etiket aynı anda aynı videoyu yüklemez (sıradaki, oynayandan farklıdır).
    expect(new URL((videos[0] as HTMLVideoElement).src).pathname).not.toBe(new URL((videos[1] as HTMLVideoElement).src).pathname);
  });

  it("ekran okuyuculardan gizlidir ve poster arka plan olarak durur", () => {
    mockMedia({ [WIDE]: true });
    render(<AuthBackdrop />);
    const panel = screen.getByTestId("auth-backdrop");
    expect(panel).toHaveAttribute("aria-hidden", "true");
    expect(panel.style.backgroundImage).toContain(AUTH_BACKDROP.poster);
  });

  it("'hareketi azalt' açıkken video çizilmez (yalnız poster)", () => {
    mockMedia({ [WIDE]: true, [REDUCED]: true });
    const { container } = render(<AuthBackdrop />);
    expect(container.querySelectorAll("video")).toHaveLength(0);
    expect(screen.getByTestId("auth-backdrop")).toBeInTheDocument();
  });

  it("dar ekranda (mobil/tablet) video çizilmez, böylece indirilmez", () => {
    mockMedia({ [WIDE]: false });
    const { container } = render(<AuthBackdrop />);
    expect(container.querySelectorAll("video")).toHaveLength(0);
  });

  it("kaldırılınca videolar durdurulur ve kaynakları bırakılır", () => {
    mockMedia({ [WIDE]: true });
    const { container, unmount } = render(<AuthBackdrop />);
    const videos = [...container.querySelectorAll("video")];
    unmount();
    expect(HTMLMediaElement.prototype.pause).toHaveBeenCalled();
    for (const video of videos) expect(video.getAttribute("src")).toBeNull();
  });

  it("tam ekran kaplar (fixed inset-0) ve video object-cover ile oranı korur", () => {
    mockMedia({ [WIDE]: true });
    const { container } = render(<AuthBackdrop />);
    const panel = screen.getByTestId("auth-backdrop");
    expect(panel.className).toContain("inset-0");
    expect(panel.className).toContain("fixed");
    for (const video of container.querySelectorAll("video")) {
      expect(video.className).toContain("object-cover");
    }
  });

  it("tarayıcı veri tasarrufu istiyorsa video çizilmez (yalnız poster)", () => {
    mockMedia({ [WIDE]: true });
    vi.stubGlobal("navigator", { ...navigator, connection: Object.assign(new EventTarget(), { saveData: true }) });
    const { container } = render(<AuthBackdrop />);
    expect(container.querySelectorAll("video")).toHaveLength(0);
    expect(screen.getByTestId("auth-backdrop").style.backgroundImage).toContain(AUTH_BACKDROP.poster);
  });

  it("yükleme hataları sınırsız yeniden denenmez: sınırdan sonra videolar bırakılır, poster kalır", () => {
    mockMedia({ [WIDE]: true });
    const { container } = render(<AuthBackdrop />);
    const videos = [...container.querySelectorAll("video")];
    const load = HTMLMediaElement.prototype.load as unknown as ReturnType<typeof vi.fn>;
    const second = videos[1] as HTMLVideoElement;
    for (let i = 0; i < 6; i++) fireEvent.error(second);
    const loadsBeforeCap = load.mock.calls.length;
    for (let i = 0; i < 10; i++) fireEvent.error(second);
    expect(load.mock.calls.length).toBe(loadsBeforeCap); // sınırdan sonra yeniden yükleme yok
    for (const video of videos) expect(video.getAttribute("src")).toBeNull();
    expect(screen.getByTestId("auth-backdrop")).toBeInTheDocument();
  });
});
