import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./confirm-dialog";

const base = { title: "Emin misiniz?", description: "Bu işlem değerlendirmeni yayımlar.", confirmLabel: "Evet, kaydet", cancelLabel: "Vazgeç" };

describe("ConfirmDialog", () => {
  it("başlık ve açıklamayla modal olarak açılır, adlandırılmıştır", () => {
    render(<ConfirmDialog {...base} onConfirm={() => undefined} onCancel={() => undefined} />);
    const dialog = screen.getByRole("dialog", { name: "Emin misiniz?" });
    expect(dialog).toHaveAttribute("open");
    expect(dialog).toHaveAccessibleDescription("Bu işlem değerlendirmeni yayımlar.");
  });

  it("onay ve vazgeç düğmeleri ilgili işleyiciyi çağırır", () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(<ConfirmDialog {...base} onConfirm={onConfirm} onCancel={onCancel} />);
    fireEvent.click(screen.getByRole("button", { name: "Evet, kaydet" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("Esc ve arka plana tıklama vazgeçer; içeriğe tıklama vazgeçmez", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...base} onConfirm={() => undefined} onCancel={onCancel}><p>Ek bilgi</p></ConfirmDialog>);
    const dialog = screen.getByRole("dialog");
    fireEvent.click(screen.getByText("Ek bilgi"));
    expect(onCancel).not.toHaveBeenCalled();
    fireEvent.click(dialog);
    expect(onCancel).toHaveBeenCalledTimes(1);
    fireEvent(dialog, new Event("cancel", { cancelable: true }));
    expect(onCancel).toHaveBeenCalledTimes(2);
  });

  it("işlem sürerken (busy) düğmeler kilitlenir, Esc ve arka plan kapatmaz", () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog {...base} busy onConfirm={() => undefined} onCancel={onCancel} />);
    const dialog = screen.getByRole("dialog");
    expect(screen.getByRole("button", { name: "Evet, kaydet" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Vazgeç" })).toBeDisabled();
    fireEvent.click(dialog);
    fireEvent(dialog, new Event("cancel", { cancelable: true }));
    expect(onCancel).not.toHaveBeenCalled();
    expect(dialog).toHaveAttribute("aria-busy", "true");
  });

  it("varsayılan olarak ilk odak onayda, tehlikeli işlemde vazgeçte olur; kapanınca odak geri döner", () => {
    const trigger = document.createElement("button");
    document.body.append(trigger);
    trigger.focus();
    const first = render(<ConfirmDialog {...base} onConfirm={() => undefined} onCancel={() => undefined} />);
    expect(screen.getByRole("button", { name: "Evet, kaydet" })).toHaveFocus();
    first.unmount();
    expect(trigger).toHaveFocus();
    const second = render(<ConfirmDialog {...base} tone="danger" onConfirm={() => undefined} onCancel={() => undefined} />);
    expect(screen.getByRole("button", { name: "Vazgeç" })).toHaveFocus();
    second.unmount();
    expect(trigger).toHaveFocus();
    trigger.remove();
  });
});
