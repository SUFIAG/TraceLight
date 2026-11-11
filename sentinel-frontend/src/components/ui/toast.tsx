"use client";

import * as React from "react";
import * as ToastPrimitive from "@radix-ui/react-toast";
import { AlertTriangle, CheckCircle2, Info, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { useToastStore, type ToastVariant } from "@/lib/hooks/use-toast";

const variantStyles: Record<
  ToastVariant,
  { border: string; icon: React.ComponentType<{ className?: string }>; iconColor: string }
> = {
  default: { border: "border-line", icon: Info, iconColor: "text-primary" },
  success: { border: "border-good/40", icon: CheckCircle2, iconColor: "text-good" },
  error: { border: "border-critical/40", icon: AlertTriangle, iconColor: "text-critical" },
};

/**
 * Renders the toast queue. Mounted once in the root layout so every failure in
 * the application is visible to the user rather than only to the console.
 */
export function Toaster() {
  const toasts = useToastStore((state) => state.toasts);
  const dismiss = useToastStore((state) => state.dismiss);

  return (
    <ToastPrimitive.Provider swipeDirection="right" duration={6000}>
      {toasts.map((toast) => {
        const style = variantStyles[toast.variant];
        const Icon = style.icon;
        return (
          <ToastPrimitive.Root
            key={toast.id}
            open
            onOpenChange={(open) => {
              if (!open) dismiss(toast.id);
            }}
            className={cn(
              "flex w-full items-start gap-3 rounded-lg border bg-surface-raised p-4 shadow-popover",
              "data-[state=open]:animate-slide-from-right data-[state=closed]:animate-fade-in",
              style.border
            )}
          >
            <Icon className={cn("mt-0.5 size-4 shrink-0", style.iconColor)} aria-hidden />
            <div className="flex-1 space-y-0.5">
              <ToastPrimitive.Title className="text-sm font-medium text-ink">
                {toast.title}
              </ToastPrimitive.Title>
              {toast.description ? (
                <ToastPrimitive.Description className="text-xs leading-relaxed text-ink-secondary">
                  {toast.description}
                </ToastPrimitive.Description>
              ) : null}
            </div>
            <ToastPrimitive.Close
              aria-label="Dismiss notification"
              className="rounded p-0.5 text-ink-muted transition-colors hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <X className="size-4" />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        );
      })}
      <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] flex w-full max-w-sm flex-col gap-2 p-4 outline-none" />
    </ToastPrimitive.Provider>
  );
}
