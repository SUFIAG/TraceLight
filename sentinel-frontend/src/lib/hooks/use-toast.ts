"use client";

import { create } from "zustand";
import { ApiError } from "@/lib/api-client";

export type ToastVariant = "default" | "success" | "error";

export interface ToastRecord {
  id: string;
  title: string;
  description?: string;
  variant: ToastVariant;
}

interface ToastState {
  toasts: ToastRecord[];
  push: (toast: Omit<ToastRecord, "id">) => void;
  dismiss: (id: string) => void;
}

let counter = 0;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (toast) => {
    const id = `toast-${++counter}`;
    set((state) => ({ toasts: [...state.toasts, { ...toast, id }] }));
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

/**
 * Toast helpers. `toast.fromError` turns an ApiError into the backend's own
 * message, so the user sees the real reason a request failed.
 */
export const toast = {
  success: (title: string, description?: string) =>
    useToastStore.getState().push({ title, description, variant: "success" }),

  error: (title: string, description?: string) =>
    useToastStore.getState().push({ title, description, variant: "error" }),

  info: (title: string, description?: string) =>
    useToastStore.getState().push({ title, description, variant: "default" }),

  fromError: (error: unknown, fallbackTitle = "Something went wrong") => {
    if (error instanceof ApiError) {
      useToastStore.getState().push({
        title: fallbackTitle,
        description: error.message,
        variant: "error",
      });
      return;
    }
    useToastStore.getState().push({
      title: fallbackTitle,
      description: error instanceof Error ? error.message : "Unexpected error.",
      variant: "error",
    });
  },
};

export function useToast() {
  return toast;
}
