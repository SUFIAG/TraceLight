"use client";

import { cn } from "@/lib/utils";

export interface FilterOption<T extends string> {
  value: T | "ALL";
  label: string;
  count?: number;
}

/**
 * Filters sit in one row above the data, and selection is marked by weight and
 * a surface change rather than colour alone.
 */
export function FilterBar<T extends string>({
  options,
  value,
  onChange,
  label,
  children,
}: {
  options: FilterOption<T>[];
  value: T | "ALL";
  onChange: (value: T | "ALL") => void;
  label: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div role="group" aria-label={label} className="flex flex-wrap items-center gap-1">
        {options.map((option) => {
          const active = option.value === value;
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => onChange(option.value)}
              aria-pressed={active}
              className={cn(
                "rounded-md px-3 py-1.5 text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                active
                  ? "bg-primary-wash font-medium text-ink"
                  : "text-ink-secondary hover:bg-surface-sunken hover:text-ink"
              )}
            >
              {option.label}
              {option.count !== undefined && (
                <span className="ml-1.5 text-ink-muted tabular">{option.count}</span>
              )}
            </button>
          );
        })}
      </div>
      {children}
    </div>
  );
}
