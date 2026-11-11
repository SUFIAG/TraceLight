"use client";

import { useRef } from "react";
import { cn } from "@/lib/utils";

interface CodeInputProps {
  value: string;
  onChange: (value: string) => void;
  /** Fired when the last box is filled, so the user need not press a button. */
  onComplete?: (value: string) => void;
  length?: number;
  disabled?: boolean;
  label?: string;
}

/**
 * Six boxes for a one-time code.
 *
 * <p>Typing advances, backspace retreats, and a pasted code fills the row —
 * because people do paste codes, and a field that silently drops a paste is
 * the fastest way to make somebody give up on enabling MFA.
 */
export function CodeInput({
  value,
  onChange,
  onComplete,
  length = 6,
  disabled = false,
  label = "Verification code",
}: CodeInputProps) {
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const digits = value.padEnd(length, " ").slice(0, length).split("");

  const commit = (next: string) => {
    const clean = next.replace(/\D/g, "").slice(0, length);
    onChange(clean);
    if (clean.length === length) onComplete?.(clean);
  };

  return (
    <div className="space-y-1.5">
      <span className="text-xs font-medium text-ink-secondary">{label}</span>
      <div className="flex gap-2" role="group" aria-label={label}>
        {digits.map((digit, index) => (
          <input
            key={index}
            ref={(element) => {
              refs.current[index] = element;
            }}
            value={digit.trim()}
            disabled={disabled}
            inputMode="numeric"
            autoComplete={index === 0 ? "one-time-code" : "off"}
            maxLength={1}
            aria-label={`Digit ${index + 1}`}
            onChange={(event) => {
              const typed = event.target.value.replace(/\D/g, "");
              if (!typed) return;
              const next = (value.slice(0, index) + typed + value.slice(index + 1)).slice(0, length);
              commit(next);
              refs.current[Math.min(index + typed.length, length - 1)]?.focus();
            }}
            onKeyDown={(event) => {
              if (event.key === "Backspace") {
                event.preventDefault();
                const next = value.slice(0, Math.max(0, index)) + value.slice(index + 1);
                commit(next);
                refs.current[Math.max(0, index - 1)]?.focus();
              }
              if (event.key === "ArrowLeft") refs.current[Math.max(0, index - 1)]?.focus();
              if (event.key === "ArrowRight") refs.current[Math.min(length - 1, index + 1)]?.focus();
            }}
            onPaste={(event) => {
              event.preventDefault();
              commit(event.clipboardData.getData("text"));
              refs.current[length - 1]?.focus();
            }}
            className={cn(
              "h-11 w-11 rounded-md border border-line bg-surface text-center text-base tabular text-ink",
              "transition-colors focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30",
              disabled && "opacity-60"
            )}
          />
        ))}
      </div>
    </div>
  );
}
