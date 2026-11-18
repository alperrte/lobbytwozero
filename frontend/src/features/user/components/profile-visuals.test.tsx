import { cleanup, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import type { Remote } from "../hooks/use-remote";
import type { OnboardingState } from "../services/onboarding-api";
import type { MyProfile } from "../types/profile.types";

vi.mock("@/shared/lib/i18n/navigation", () => ({
  Link: ({ href, children, ...rest }: { href: string | { pathname: string; hash?: string }; children: ReactNode }) => (
    <a href={typeof href === "string" ? href : `${href.pathname}#${href.hash ?? ""}`} {...rest}>
      {children}
    </a>
  ),
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/profile"
}));

import { ProfileCompletion } from "./profile-completion";
import { ProfileHeader } from "./profile-header";
import { Chips, Empty } from "./profile-parts";

const empty: MyProfile = {
  username: "kerem",
  displayName: null,
  bio: null,
  country: null,
  playerTypes: [],
  favoriteGenres: [],
  favoritePlatforms: [],
  avatar: null,
  cover: null,
  memberSince: null,
  version: 0
};

function wrap(node: ReactNode) {
  return render(
    <NextIntlClientProvider locale="tr" messages={tr} timeZone="Europe/Istanbul">
      {node}
    </NextIntlClientProvider>
  );
}

afterEach(cleanup);

describe("ProfileCompletion", () => {
  it("eksik adımlar ilgili düzenleme bölümüne bağlanır, Steam ayarlara gider", () => {
    wrap(<ProfileCompletion profile={empty} steam={{ linked: false, showOnProfile: false }} />);
    const href = (name: string) => screen.getByRole("link", { name }).getAttribute("href");
    expect(href("Profil fotoğrafı ekle")).toBe("/profile/edit#images");
    expect(href("Biyografi yaz")).toBe("/profile/edit#info");
    expect(href("Favori türleri seç")).toBe("/profile/edit#prefs");
    expect(href("Steam hesabını bağla")).toBe("/settings");
  });

  it("kurulum bitmediyse aynı kartta kurulum çağrısı çıkar; yükleniyor, hata ve tamamlandıda çıkmaz", () => {
    const remote = (state: unknown) => ({ state, retry: vi.fn() }) as unknown as Remote<OnboardingState>;
    const pending = { status: "ready", data: { completed: false, completedAt: null, missing: [], version: 1 } };
    wrap(<ProfileCompletion profile={empty} steam={null} onboarding={remote(pending)} />);
    expect(screen.getByRole("link", { name: tr.Onboarding.prompt.action }).getAttribute("href")).toBe("/onboarding");
    cleanup();

    for (const state of [
      { status: "loading" },
      { status: "error", code: null },
      { status: "ready", data: { completed: true, completedAt: null, missing: [], version: 1 } }
    ]) {
      wrap(<ProfileCompletion profile={empty} steam={null} onboarding={remote(state)} />);
      expect(screen.queryByRole("link", { name: tr.Onboarding.prompt.action })).not.toBeInTheDocument();
      cleanup();
    }
  });

  it("adımlar tamam ama kurulum bitmediyse yalnız kurulum çağrısı kalır; ikisi de tamamsa kart yoktur", () => {
    const full: MyProfile = {
      ...empty,
      bio: "x",
      country: "TR",
      playerTypes: ["CASUAL"],
      favoriteGenres: ["rpg"],
      favoritePlatforms: ["steam"],
      avatar: { small: "a", large: "a" },
      cover: { small: "c", large: "c" }
    };
    const steam = { linked: true, showOnProfile: false };
    const pending = { state: { status: "ready", data: { completed: false, completedAt: null, missing: [], version: 1 } }, retry: vi.fn() };
    wrap(<ProfileCompletion profile={full} steam={steam} onboarding={pending as unknown as Remote<OnboardingState>} />);
    expect(screen.getByRole("link", { name: tr.Onboarding.prompt.action })).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    cleanup();
    const { container } = wrap(<ProfileCompletion profile={full} steam={steam} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("tamamlanan adım bağlantı değildir", () => {
    wrap(<ProfileCompletion profile={{ ...empty, bio: "x" }} steam={null} />);
    expect(screen.queryByRole("link", { name: "Biyografi yaz" })).not.toBeInTheDocument();
    expect(screen.getByText("Biyografi yaz")).toBeInTheDocument();
  });
});

describe("boş değerler", () => {
  it("addSection verilince Ekle bağlantısı gösterir, verilmezse yalnız Seçilmedi", () => {
    wrap(
      <>
        <Chips items={[]} addSection="prefs" />
        <Empty />
      </>
    );
    expect(screen.getByRole("link", { name: "Ekle" }).getAttribute("href")).toBe("/profile/edit#prefs");
    expect(screen.getAllByText("Seçilmedi")).toHaveLength(2);
  });

  it("dolu liste çip olarak gelir, Ekle yok", () => {
    wrap(<Chips items={["RPG", "FPS"]} addSection="prefs" />);
    expect(screen.getByText("RPG")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ekle" })).not.toBeInTheDocument();
  });
});

describe("ProfileHeader", () => {
  it("kapak yokken varsayılan kapağı gösterir, varsa göstermez", () => {
    const { unmount } = wrap(<ProfileHeader profile={{ username: "k", displayName: null, avatar: null, cover: null }} />);
    expect(screen.getByTestId("default-cover")).toBeInTheDocument();
    unmount();
    wrap(<ProfileHeader profile={{ username: "k", displayName: null, avatar: null, cover: { small: "http://x/s.png", large: "http://x/l.png" } }} />);
    expect(screen.queryByTestId("default-cover")).not.toBeInTheDocument();
  });
});
