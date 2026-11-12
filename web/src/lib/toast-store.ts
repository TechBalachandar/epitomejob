import { create } from "zustand";

export interface Toast {
  id: number;
  title: string;
  description?: string;
  variant: "success" | "error" | "info";
}

let nextId = 1;

interface ToastState {
  toasts: Toast[];
  push: (toast: Omit<Toast, "id">) => void;
  dismiss: (id: number) => void;
}

export const useToastStore = create<ToastState>()((set) => ({
  toasts: [],
  push: (toast) => {
    const id = nextId++;
    set((s) => ({ toasts: [...s.toasts, { ...toast, id }] }));
    window.setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
    }, 4000);
  },
  dismiss: (id) =>
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

export function toast(toast: Omit<Toast, "id">) {
  useToastStore.getState().push(toast);
}
