import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ModalDialog } from "./modal-dialog";

describe("ModalDialog", () => {
  it("başlıkla adlandırılmış modal olarak açılır ve içeriği gösterir", () => {
    render(
      <ModalDialog title="Kurulum" description="Açıklama" onClose={() => undefined} closeLabel="Kapat">
        <p>İçerik</p>
      </ModalDialog>
    );
    const dialog = screen.getByRole("dialog", { name: "Kurulum" });
    expect(dialog).toHaveAttribute("open");
    expect(dialog).toHaveAccessibleDescription("Açıklama");
    expect(screen.getByText("İçerik")).toBeInTheDocument();
  });

  it("X, Esc ve arka plan kapatır; içeriğe tıklama kapatmaz", () => {
    const onClose = vi.fn();
    render(
      <ModalDialog title="Kurulum" onClose={onClose} closeLabel="Kapat">
        <p>İçerik</p>
      </ModalDialog>
    );
    fireEvent.click(screen.getByText("İçerik"));
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));
    fireEvent.click(screen.getByRole("dialog"));
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(onClose).toHaveBeenCalledTimes(3);
  });

  it("dismissible=false: X yok, Esc ve arka plan kapatmaz", () => {
    const onClose = vi.fn();
    render(
      <ModalDialog title="Kodlar" dismissible={false} onClose={onClose} closeLabel="Kapat">
        <p>İçerik</p>
      </ModalDialog>
    );
    expect(screen.queryByRole("button", { name: "Kapat" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("dialog"));
    const cancel = new Event("cancel", { cancelable: true });
    fireEvent(screen.getByRole("dialog"), cancel);
    expect(onClose).not.toHaveBeenCalled();
    expect(cancel.defaultPrevented).toBe(true);
  });
});
