"use client";

import { useEffect, useMemo, useState } from "react";
import { Check, Eye, EyeOff, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

interface PasswordFieldProps {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  placeholder?: string;
  error?: string;
  /** Shows the live strength meter and rule checklist. Off for "current password". */
  showStrength?: boolean;
  /** The user's own email, so the "does not contain your email" rule can be checked here. */
  email?: string;
  minLength?: number;
}

/**
 * A password input that says what is wrong while the user types.
 *
 * <p>The rules are checked in the browser purely so the user is not made to
 * submit a form to learn that a password is four characters short. The backend
 * applies the same policy and is the only authority: a browser check is a
 * courtesy, never a control.
 */
export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete = "new-password",
  placeholder = "••••••••••••",
  error,
  showStrength = false,
  email,
  minLength = 12,
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  const rules = useMemo(() => {
    const local = email?.split("@")[0]?.toLowerCase() ?? "";
    return [
      { label: `At least ${minLength} characters`, met: value.length >= minLength },
      { label: "A capital and a lower-case letter", met: /[A-Z]/.test(value) && /[a-z]/.test(value) },
      { label: "A number", met: /[0-9]/.test(value) },
      { label: "A symbol", met: /[^A-Za-z0-9]/.test(value) },
      {
        label: "Does not contain your email",
        met: local.length < 3 || !value.toLowerCase().includes(local),
      },
    ];
  }, [value, email, minLength]);

  const met = rules.filter((rule) => rule.met).length;
  const strength = value.length === 0 ? 0 : Math.round((met / rules.length) * 100);

  const tone =
    strength >= 100 ? "bg-good" : strength >= 60 ? "bg-warning" : "bg-critical";
  const toneLabel =
    strength >= 100 ? "Strong" : strength >= 60 ? "Getting there" : "Too weak";

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          type={visible ? "text" : "password"}
          value={value}
          autoComplete={autoComplete}
          placeholder={placeholder}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
          className="pr-10"
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? "Hide password" : "Show password"}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-ink-muted hover:text-ink"
        >
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>

      {error ? (
        <p id={`${id}-error`} role="alert" className="text-xs text-critical">
          {error}
        </p>
      ) : null}

      {showStrength && value.length > 0 ? (
        <div className="space-y-2 pt-1">
          <div className="flex items-center gap-2">
            <div className="h-1 flex-1 overflow-hidden rounded-full bg-surface-sunken">
              <div
                className={cn("h-full transition-all duration-200", tone)}
                style={{ width: `${Math.max(8, strength)}%` }}
              />
            </div>
            <span className="text-[11px] text-ink-muted">{toneLabel}</span>
          </div>
          <ul className="space-y-0.5">
            {rules.map((rule) => (
              <li
                key={rule.label}
                className={cn(
                  "flex items-center gap-1.5 text-[11px]",
                  rule.met ? "text-ink-secondary" : "text-ink-muted"
                )}
              >
                {rule.met ? (
                  <Check className="size-3 text-good" />
                ) : (
                  <X className="size-3 text-ink-muted" />
                )}
                {rule.label}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}

/** True when every browser-side rule passes. The backend still has final say. */
export function passwordLooksValid(value: string, email?: string, minLength = 12): boolean {
  const local = email?.split("@")[0]?.toLowerCase() ?? "";
  return (
    value.length >= minLength &&
    /[A-Z]/.test(value) &&
    /[a-z]/.test(value) &&
    /[0-9]/.test(value) &&
    /[^A-Za-z0-9]/.test(value) &&
    (local.length < 3 || !value.toLowerCase().includes(local))
  );
}

/** Shared hook for the "did you type the same thing twice" check. */
export function useConfirmation(password: string, confirmation: string) {
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (confirmation.length > 0) setTouched(true);
  }, [confirmation]);
  return {
    mismatch: touched && confirmation.length > 0 && password !== confirmation,
  };
}
