import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import tr from "@/shared/lib/i18n/messages/tr.json";
import * as validation from "../schemas/image-validation";
import type { LocalImage } from "../schemas/image-validation";
import { ImagePicker } from "./image-picker";

vi.mock("../schemas/image-validation", async (importOriginal) => {
  const original = await importOriginal<typeof import("../schemas/image-validation")>();
  return { ...original, prepareImages: vi.fn(), releaseImage: vi.fn() };
});

const prepare = vi.mocked(validation.prepareImages);

function local(name: string, key = name): LocalImage {
  return { key, file: new File(["x"], name, { type: "image/png" }), url: `blob:${name}`, width: 100, height: 100 };
}

function Harness({ initial = [] as LocalImage[], remote = 0 }: { initial?: LocalImage[]; remote?: number }) {
  const [items, setItems] = useState(initial);
  const [active, setActive] = useState<string | null>(null);
  return (
    <NextIntlClientProvider locale="tr" messages={tr}>
      <ImagePicker items={items} setItems={setItems} remoteCount={remote} activeKey={active} onActivate={setActive} />
      <output data-testid="order">{items.map((item) => item.file.name).join(",")}</output>
      <output data-testid="active">{active ?? ""}</output>
    </NextIntlClientProvider>
  );
}

const order = () => screen.getByTestId("order").textContent;

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ImagePicker", () => {
  it("birden fazla dosya seçilir, sonradan eklenince önceki seçimler korunur", async () => {
    prepare.mockResolvedValueOnce({ added: [local("a.png"), local("b.png")], rejected: [] });
    render(<Harness />);
    const input = document.querySelector("input[type=file]") as HTMLInputElement;
    expect(input.multiple).toBe(true);
    fireEvent.change(input, { target: { files: [new File(["1"], "a.png"), new File(["2"], "b.png")] } });
    await waitFor(() => expect(order()).toBe("a.png,b.png"));
    expect(screen.getByText("Daha fazla ekle")).toBeInTheDocument();

    prepare.mockResolvedValueOnce({ added: [local("c.png")], rejected: [] });
    fireEvent.change(input, { target: { files: [new File(["3"], "c.png")] } });
    await waitFor(() => expect(order()).toBe("a.png,b.png,c.png"));
    expect(screen.getByTestId("active")).toHaveTextContent("c.png");
  });

  it("geçersiz dosyalar adı ve nedeniyle bildirilir; geçerli seçimler kaybolmaz; uyarı kapatılabilir", async () => {
    prepare.mockResolvedValueOnce({
      added: [local("iyi.png")],
      rejected: [
        { name: "a.webp", reason: "type" },
        { name: "buyuk.png", reason: "size" },
        { name: "iyi.png", reason: "duplicate" }
      ]
    });
    render(<Harness />);
    fireEvent.change(document.querySelector("input[type=file]") as HTMLInputElement, { target: { files: [new File(["1"], "x.png")] } });
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("3 dosya eklenmedi; geçerli seçimlerin korundu.");
    expect(alert).toHaveTextContent("a.webp — Yalnız JPEG ve PNG");
    expect(alert).toHaveTextContent("buyuk.png — 2 MB sınırını aşıyor.");
    expect(alert).toHaveTextContent("iyi.png — Bu dosya zaten ekli.");
    expect(order()).toBe("iyi.png");
    fireEvent.click(within(alert).getByRole("button", { name: "Uyarıyı kapat" }));
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("sunucudaki görseller toplam sınıra dahildir ve doluysa ekleme kapanır", () => {
    render(<Harness initial={[local("a.png"), local("b.png")]} remote={2} />);
    expect(screen.getByText("4/4")).toBeInTheDocument();
    expect(document.querySelector("input[type=file]")).toBeDisabled();
  });

  it("düğmelerle sıralar; odak taşınan görselin düğmesinde kalır ve durum duyurulur", async () => {
    render(<Harness initial={[local("a.png", "k1"), local("b.png", "k2"), local("c.png", "k3")]} />);
    fireEvent.click(screen.getByRole("button", { name: "c.png görselini öne al" }));
    expect(order()).toBe("a.png,c.png,b.png");
    await waitFor(() => expect(screen.getByRole("button", { name: "c.png görselini öne al" })).toHaveFocus());
    expect(screen.getByText("c.png, 3 görselden 2. sıraya taşındı")).toBeInTheDocument();
    expect(screen.getByTestId("active")).toHaveTextContent("k3");
    fireEvent.click(screen.getByRole("button", { name: "a.png görselini arkaya al" }));
    expect(order()).toBe("c.png,a.png,b.png");
  });

  it("ilk görsel öne, son görsel arkaya alınamaz", () => {
    render(<Harness initial={[local("a.png", "k1"), local("b.png", "k2")]} />);
    expect(screen.getByRole("button", { name: "a.png görselini öne al" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "b.png görselini arkaya al" })).toBeDisabled();
  });

  it("klavye: Alt+Ok ile sıralar", () => {
    render(<Harness initial={[local("a.png", "k1"), local("b.png", "k2"), local("c.png", "k3")]} />);
    const second = screen.getByRole("button", { name: /2\. görsel: b\.png/ });
    fireEvent.keyDown(second, { key: "ArrowRight", altKey: true });
    expect(order()).toBe("a.png,c.png,b.png");
    const moved = screen.getByRole("button", { name: /3\. görsel: b\.png/ });
    fireEvent.keyDown(moved, { key: "ArrowRight", altKey: true });
    expect(order()).toBe("a.png,c.png,b.png");
    fireEvent.keyDown(moved, { key: "ArrowLeft" });
    expect(order()).toBe("a.png,c.png,b.png");
    fireEvent.keyDown(moved, { key: "ArrowLeft", altKey: true });
    expect(order()).toBe("a.png,b.png,c.png");
  });

  it("görseli tek tek kaldırır, bellek adresini serbest bırakır ve komşuyu etkinleştirir", () => {
    render(<Harness initial={[local("a.png", "k1"), local("b.png", "k2"), local("c.png", "k3")]} />);
    fireEvent.click(screen.getByRole("button", { name: "b.png görselini kaldır" }));
    expect(order()).toBe("a.png,c.png");
    expect(validation.releaseImage).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("active")).toHaveTextContent("k3");
    expect(screen.getByText("b.png kaldırıldı")).toBeInTheDocument();
  });

  it("küçük resme tıklamak önizlemede göstermek içindir", () => {
    render(<Harness initial={[local("a.png", "k1"), local("b.png", "k2")]} />);
    fireEvent.click(screen.getByRole("button", { name: /2\. görsel: b\.png/ }));
    expect(screen.getByTestId("active")).toHaveTextContent("k2");
  });
});
