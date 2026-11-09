import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import { emptyFilters, type PostFilters } from "../types/community.types";
import { PostFilterBar } from "./post-filters";

vi.mock("../services/community-api", () => ({ searchGames: vi.fn().mockResolvedValue([]), fetchGameCards: vi.fn().mockResolvedValue([]) }));

const changes: PostFilters[] = [];

function Harness({ initial = emptyFilters }: { initial?: PostFilters }) {
  const [filters, setFilters] = useState(initial);
  return (
    <NextIntlClientProvider locale="tr" messages={tr}>
      <PostFilterBar
        filters={filters}
        onChange={(next) => {
          changes.push(next);
          setFilters(next);
        }}
      />
    </NextIntlClientProvider>
  );
}

const search = () => screen.getByRole("searchbox");
const advance = (ms: number) => act(() => void vi.advanceTimersByTime(ms));

beforeEach(() => {
  vi.useFakeTimers();
  changes.length = 0;
});
afterEach(() => vi.useRealTimers());

describe("Akış arama ve sıralama çubuğu", () => {
  it("'Ara' ve En yeni/Popüler/En eski buton grubu yoktur; arama solda, sağ grupta 'Filtrele' ve 'Daha fazla filtre' vardır", () => {
    render(<Harness />);
    expect(screen.queryByRole("button", { name: "Ara" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Popüler" })).toBeNull();
    expect(screen.queryByRole("button", { name: "En eski" })).toBeNull();
    const search = screen.getByRole("search");
    const filter = screen.getByRole("button", { name: /^Filtrele/ });
    const more = screen.getByRole("button", { name: "Daha fazla filtre" });
    expect(search.compareDocumentPosition(filter) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(filter.compareDocumentPosition(more) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(filter.parentElement?.parentElement).toBe(more.parentElement);
    // Soldaki ayrı "Filtrele" etiketi yoktur.
    expect(screen.getAllByText("Filtrele")).toHaveLength(1);
  });

  it("tetikleyici kapalıyken daima 'Filtrele' yazar; seçim başlığın yerine geçmez", () => {
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: /^Filtrele/ });
    expect(trigger).toHaveTextContent("Filtrele");
    fireEvent.click(trigger);
    fireEvent.click(screen.getByRole("checkbox", { name: "En eski" }));
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(screen.getByRole("button", { name: /^Filtrele/ })).toHaveTextContent(/^Filtrele/);
    expect(screen.getByRole("button", { name: /^Filtrele/ })).not.toHaveTextContent("En eski");
  });

  it("seçenekler alt alta istenen sırayla, onay kutularıyla sunulur; varsayılan En yeni işaretlidir; açıklama ve 'Yakında' yazısı yoktur", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    const boxes = screen.getAllByRole("checkbox");
    expect(boxes.map((box) => (box.closest("li") as HTMLElement).textContent)).toEqual([
      expect.stringContaining("En yeni"),
      expect.stringContaining("En eski"),
      expect.stringContaining("En fazla beğeni alan"),
      expect.stringContaining("En fazla yeniden paylaşılan")
    ]);
    expect(boxes[0]).toBeChecked();
    boxes.slice(1).forEach((box) => {
      expect(box).not.toBeChecked();
      expect(box).toBeEnabled();
    });
    expect(screen.queryByText("Yakında")).toBeNull();
    expect(screen.queryByText(/ana sıralamadır/)).toBeNull();
  });

  it("seçim menüyü kapatmaz; seçili seçeneğe tekrar basmak seçimi kaldırır", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    const oldest = screen.getByRole("checkbox", { name: "En eski" });
    fireEvent.click(oldest);
    expect(screen.getByRole("group", { name: "Filtrele" })).toBeInTheDocument();
    expect(oldest).toBeChecked();
    fireEvent.click(oldest);
    expect(oldest).not.toBeChecked();
    expect(screen.getByRole("group", { name: "Filtrele" })).toBeInTheDocument();
    // Uygulanmadan filtre değişmez.
    expect(changes).toHaveLength(0);
  });

  it("çoklu seçim: dört seçenek aynı anda işaretli kalır, yeni seçim öncekini kaldırmaz; menü açık kalır", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En eski" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla beğeni alan" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla yeniden paylaşılan" }));
    const boxes = screen.getAllByRole("checkbox");
    expect(boxes.map((box) => (box as HTMLInputElement).checked)).toEqual([true, true, true, true]);
    expect(screen.getByRole("group", { name: "Filtrele" })).toBeInTheDocument();
    // Yalnız basılan seçeneğin işareti kalkar.
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla beğeni alan" }));
    expect(boxes.map((box) => (box as HTMLInputElement).checked)).toEqual([true, true, false, true]);
  });

  it("panel odaklanabilirdir: etikete/boşluğa basınca odak sayfaya kaçıp menüyü kapatmaz (çoklu seçim bozulmaz)", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    expect(screen.getByRole("group", { name: "Filtrele" })).toHaveAttribute("tabindex", "-1");
  });

  it("seçim sırası küçük sıra numaralarıyla gösterilir; kaldırılan seçim numaraları yeniden sıralar", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En yeni" })); // kaldır
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla yeniden paylaşılan" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla beğeni alan" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En yeni" }));
    const row = (name: string) => screen.getByRole("checkbox", { name }).closest("li") as HTMLElement;
    expect(within(row("En fazla yeniden paylaşılan")).getByText("1. sıra")).toBeInTheDocument();
    expect(within(row("En fazla beğeni alan")).getByText("2. sıra")).toBeInTheDocument();
    expect(within(row("En yeni")).getByText("3. sıra")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla yeniden paylaşılan" }));
    expect(within(row("En fazla beğeni alan")).getByText("1. sıra")).toBeInTheDocument();
    expect(within(row("En yeni")).getByText("2. sıra")).toBeInTheDocument();
  });

  it("zıt yönler (En yeni + En eski) birlikte seçilirse önce seçilen uygulanır ve menüde açıkça belirtilir", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En eski" })); // En yeni zaten 1. sırada
    expect(screen.getByRole("status")).toHaveTextContent("zıt yönlerdir: önce seçtiğin (“En yeni”) uygulanır");
    expect(within((screen.getByRole("checkbox", { name: "En eski" }).closest("li")) as HTMLElement).getByText("Etkisiz (zıt yön)")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(changes.at(-1)).toMatchObject({ sort: "NEWEST" });
  });

  it("'Uygula' bütün seçimi birlikte uygular, menüyü kapatır ve yeniden açınca seçimler ve numaralar korunur; arama korunur", () => {
    render(<Harness initial={{ ...emptyFilters, q: "harita" }} />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En yeni" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En eski" }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En yeni" }));
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(changes.at(-1)).toMatchObject({ sort: "OLDEST", q: "harita" });
    expect(screen.queryByRole("group", { name: "Filtrele" })).toBeNull();
    expect(screen.getByRole("button", { name: /^Filtrele/ })).toHaveTextContent("Filtrele");

    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    expect(screen.getByRole("checkbox", { name: "En eski" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "En yeni" })).toBeChecked();
    expect(within(screen.getByRole("checkbox", { name: "En eski" }).closest("li") as HTMLElement).getByText("1. sıra")).toBeInTheDocument();
    expect(within(screen.getByRole("checkbox", { name: "En yeni" }).closest("li") as HTMLElement).getByText("2. sıra")).toBeInTheDocument();
  });

  it("API'nin sıralayamadığı ölçüt işaretlidir ama 'Uygula' bunu uygulamaz: hiçbir istek/filtre değişmez, seçim korunur, neden yazılır", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla beğeni alan" }));
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(changes).toHaveLength(0);
    expect(screen.getByRole("alert")).toHaveTextContent("“En fazla beğeni alan” sıralaması API tarafında henüz desteklenmiyor");
    expect(screen.getByRole("group", { name: "Filtrele" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "En fazla beğeni alan" })).toBeChecked();
    // Seçim kaldırılınca uyarı kalkar ve uygulanabilir.
    fireEvent.click(screen.getByRole("checkbox", { name: "En fazla beğeni alan" }));
    expect(screen.queryByRole("alert")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Uygula" }));
    expect(changes.at(-1)).toMatchObject({ sort: "NEWEST" });
  });

  it("'Temizle' tüm seçimleri ve sıra numaralarını sıfırlar; sıralama En yeni olur", () => {
    render(<Harness initial={{ ...emptyFilters, sort: "OLDEST" }} />);
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    fireEvent.click(screen.getByRole("checkbox", { name: "En yeni" }));
    expect(screen.getByText("1. sıra")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Temizle" }));
    expect(changes.at(-1)).toMatchObject({ sort: "NEWEST" });
    fireEvent.click(screen.getByRole("button", { name: /^Filtrele/ }));
    screen.getAllByRole("checkbox").forEach((box) => expect(box).not.toBeChecked());
    expect(screen.queryByText(/\. sıra/)).toBeNull();
    expect(screen.getByRole("button", { name: /^Filtrele/ })).toHaveTextContent(/^Filtrele$/);
  });

  it("yazarken debounce sonrası aranır (her tuşta değil), Enter hemen aratır", () => {
    render(<Harness />);
    fireEvent.change(search(), { target: { value: "ha" } });
    fireEvent.change(search(), { target: { value: "harita" } });
    advance(300);
    expect(changes).toHaveLength(0);
    advance(150);
    expect(changes).toHaveLength(1);
    expect(changes[0].q).toBe("harita");

    fireEvent.change(search(), { target: { value: "silah" } });
    fireEvent.submit(screen.getByRole("search"));
    expect(changes.at(-1)?.q).toBe("silah");
    advance(1000);
    expect(changes).toHaveLength(2);
  });

  it("alan temizlenince arama beklemeden sıfırlanır; arama sıralamayla birlikte korunur", () => {
    render(<Harness initial={{ ...emptyFilters, q: "harita", sort: "OLDEST" }} />);
    expect(search()).toHaveValue("harita");
    fireEvent.change(search(), { target: { value: "" } });
    expect(changes.at(-1)).toMatchObject({ q: "", sort: "OLDEST" });
  });

  it("tek karakter aranmaz (sunucu kuralı) ve ipucu gösterilir", () => {
    render(<Harness />);
    fireEvent.change(search(), { target: { value: "a" } });
    advance(1000);
    expect(changes).toHaveLength(0);
    expect(screen.getByText(/en az 2 karakter/i)).toBeInTheDocument();
  });
});
