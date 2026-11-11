import { cn } from "@/lib/utils";

/**
 * Tracelight mark: a beam picking out one marked node from a trail of
 * unremarkable ones — the product's job in one glyph.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 28 28"
      fill="none"
      aria-hidden
      className={cn("size-7", className)}
    >
      <circle cx="14" cy="14" r="13" className="fill-primary" />
      <path
        d="M6 18.5 L10.5 14 L15 16 L22 8"
        stroke="var(--primary-ink)"
        strokeWidth="1.9"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.55"
      />
      <circle cx="22" cy="8" r="3.1" stroke="var(--primary-ink)" strokeWidth="1.9" />
      <circle cx="22" cy="8" r="1.1" fill="var(--primary-ink)" />
    </svg>
  );
}

export function Logo({ collapsed = false }: { collapsed?: boolean }) {
  return (
    <div className="flex items-center gap-2.5">
      <LogoMark />
      {!collapsed && (
        <div className="leading-none">
          <div className="text-[15px] font-semibold tracking-tight text-ink">Tracelight</div>
          <div className="mt-0.5 text-[10px] uppercase tracking-[0.14em] text-ink-muted">
            Fraud intelligence
          </div>
        </div>
      )}
    </div>
  );
}
