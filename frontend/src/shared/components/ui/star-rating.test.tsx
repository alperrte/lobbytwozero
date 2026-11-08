import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { StarRating } from "./star-rating";

const props = { label: "Puan", emptyText: "Puan verilmedi", formatValue: (value: number) => `${value} / 5` };

/** jsdom yerleşim hesaplamaz: izleme alanını 0–500px (yıldız başına 100px) olarak sabitler. */
function mockTrack(element: HTMLElement) {
  element.getBoundingClientRect = () => ({ left: 0, right: 500, top: 0, bottom: 40, width: 500, height: 40, x: 0, y: 0, toJSON: () => ({}) });
}

describe("StarRating", () => {
  it("yalnız tam yıldız verir: bir yıldızın herhangi bir yerine tıklamak o yıldızı seçer", () => {
    const onChange = vi.fn();
    render(<StarRating {...props} value={null} onChange={onChange} />);
    const slider = screen.getByRole("slider", { name: "Puan" });
    mockTrack(slider);
    fireEvent.pointerDown(slider, { clientX: 210, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 210, pointerId: 1 });
    expect(onChange).toHaveBeenCalledWith(3);
  });

  it("yarım yıldız açıkken yıldızın sol yarısı .5, sağ yarısı tam puan verir", () => {
    const onChange = vi.fn();
    render(<StarRating {...props} value={null} onChange={onChange} allowHalf />);
    const slider = screen.getByRole("slider", { name: "Puan" });
    mockTrack(slider);
    fireEvent.pointerDown(slider, { clientX: 230, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 230, pointerId: 1 });
    expect(onChange).toHaveBeenLastCalledWith(2.5);
    fireEvent.pointerDown(slider, { clientX: 270, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 270, pointerId: 1 });
    expect(onChange).toHaveBeenLastCalledWith(3);
  });

  it("sürüklerken vereceğin puanı sağdaki metinde anlık gösterir, bırakınca seçer", () => {
    const onChange = vi.fn();
    render(<StarRating {...props} value={null} onChange={onChange} allowHalf />);
    const slider = screen.getByRole("slider", { name: "Puan" });
    mockTrack(slider);
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("Puan verilmedi");
    fireEvent.pointerDown(slider, { clientX: 40, pointerId: 1 });
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("0.5 / 5");
    fireEvent.pointerMove(slider, { clientX: 460, pointerId: 1 });
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("5 / 5");
    expect(onChange).not.toHaveBeenCalled();
    fireEvent.pointerUp(slider, { clientX: 460, pointerId: 1 });
    expect(onChange).toHaveBeenCalledWith(5);
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("Puan verilmedi");
  });

  it("fareyle üzerine gelince önizler, ayrılınca seçili puana döner", () => {
    render(<StarRating {...props} value={2} onChange={() => undefined} />);
    const slider = screen.getByRole("slider", { name: "Puan" });
    mockTrack(slider);
    fireEvent.pointerMove(slider, { clientX: 450 });
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("5 / 5");
    fireEvent.pointerLeave(slider);
    expect(screen.getByTestId("star-rating-text")).toHaveTextContent("2 / 5");
  });

  it("klavyeyle çalışır: oklar adım ekler/çıkarır, Home kaldırır, End en yükseğe gider", () => {
    const onChange = vi.fn();
    const { rerender } = render(<StarRating {...props} value={3} onChange={onChange} allowHalf />);
    const slider = screen.getByRole("slider", { name: "Puan" });
    expect(slider).toHaveAttribute("aria-valuenow", "3");
    expect(slider).toHaveAttribute("aria-valuetext", "3 / 5");
    fireEvent.keyDown(slider, { key: "ArrowRight" });
    expect(onChange).toHaveBeenLastCalledWith(3.5);
    fireEvent.keyDown(slider, { key: "ArrowLeft" });
    expect(onChange).toHaveBeenLastCalledWith(2.5);
    fireEvent.keyDown(slider, { key: "Home" });
    expect(onChange).toHaveBeenLastCalledWith(null);
    fireEvent.keyDown(slider, { key: "End" });
    expect(onChange).toHaveBeenLastCalledWith(5);
    rerender(<StarRating {...props} value={0.5} onChange={onChange} allowHalf />);
    fireEvent.keyDown(screen.getByRole("slider", { name: "Puan" }), { key: "ArrowLeft" });
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("yarım yıldız kapalıyken ok tuşu tam adımla ilerler ve 5'i aşmaz", () => {
    const onChange = vi.fn();
    render(<StarRating {...props} value={5} onChange={onChange} />);
    fireEvent.keyDown(screen.getByRole("slider"), { key: "ArrowUp" });
    expect(onChange).toHaveBeenLastCalledWith(5);
  });

  it("puan yokken ve seçiliyken uygun değeri bildirir; puanı kaldır düğmesi null gönderir", () => {
    const onChange = vi.fn();
    const { rerender } = render(<StarRating {...props} value={null} onChange={onChange} clearLabel="Puanı kaldır" />);
    expect(screen.getByRole("slider")).toHaveAttribute("aria-valuetext", "Puan verilmedi");
    expect(screen.queryByRole("button", { name: "Puanı kaldır" })).toBeNull();
    rerender(<StarRating {...props} value={4} onChange={onChange} clearLabel="Puanı kaldır" />);
    fireEvent.click(screen.getByRole("button", { name: "Puanı kaldır" }));
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("devre dışıyken hiçbir şey değiştirmez", () => {
    const onChange = vi.fn();
    render(<StarRating {...props} value={2} onChange={onChange} disabled />);
    const slider = screen.getByRole("slider");
    mockTrack(slider);
    fireEvent.pointerDown(slider, { clientX: 400, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 400, pointerId: 1 });
    fireEvent.keyDown(slider, { key: "ArrowRight" });
    expect(onChange).not.toHaveBeenCalled();
    expect(slider).toHaveAttribute("aria-disabled", "true");
  });

  it("onChange yoksa salt okunur görüntüdür ve değeri erişilebilir adla verir", () => {
    const { rerender } = render(<StarRating {...props} value={4.5} showText />);
    expect(screen.queryByRole("slider")).toBeNull();
    expect(screen.getByRole("img", { name: "4.5 / 5" })).toBeInTheDocument();
    expect(screen.getByText("4.5 / 5")).toBeInTheDocument();
    rerender(<StarRating {...props} value={null} />);
    expect(screen.getByRole("img", { name: "Puan verilmedi" })).toBeInTheDocument();
  });
});
