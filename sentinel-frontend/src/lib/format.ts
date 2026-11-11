import { format, formatDistanceToNowStrict, parseISO } from "date-fns";

/** Large standalone figures read better compacted: 1,284 / 12.9K / 4.2M. */
export function compactNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  if (Math.abs(value) < 10_000) return value.toLocaleString("en-US");
  return new Intl.NumberFormat("en-US", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function exactNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return value.toLocaleString("en-US");
}

export function money(
  value: number | string | null | undefined,
  currency = "USD"
): string {
  if (value === null || value === undefined) return "—";
  const amount = typeof value === "string" ? Number(value) : value;
  if (Number.isNaN(amount)) return "—";
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    // Unknown currency codes should not break a table row.
    return `${currency} ${amount.toLocaleString("en-US", { maximumFractionDigits: 2 })}`;
  }
}

export function percent(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return `${value.toFixed(digits)}%`;
}

function toDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const parsed = parseISO(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function dateTime(value: string | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, "d MMM yyyy, HH:mm") : "—";
}

export function dateOnly(value: string | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, "d MMM yyyy") : "—";
}

export function shortDate(value: string | null | undefined): string {
  const date = toDate(value);
  return date ? format(date, "d MMM") : "—";
}

export function relativeTime(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) return "—";
  return `${formatDistanceToNowStrict(date)} ago`;
}

/** Shows the first segment of a UUID, which is enough to identify a row. */
export function shortId(value: string | null | undefined): string {
  if (!value) return "—";
  return value.split("-")[0].toUpperCase();
}

/** Turns RAPID_TRANSACTION into "Rapid transaction". */
export function humanise(value: string | null | undefined): string {
  if (!value) return "—";
  const spaced = value.replace(/_/g, " ").toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function fullName(user: {
  firstName?: string | null;
  lastName?: string | null;
  email?: string;
} | null | undefined): string {
  if (!user) return "—";
  const name = [user.firstName, user.lastName].filter(Boolean).join(" ").trim();
  return name || user.email || "—";
}

export function initials(user: {
  firstName?: string | null;
  lastName?: string | null;
  email?: string;
} | null | undefined): string {
  if (!user) return "?";
  const first = user.firstName?.[0];
  const last = user.lastName?.[0];
  if (first || last) return `${first ?? ""}${last ?? ""}`.toUpperCase();
  return (user.email?.[0] ?? "?").toUpperCase();
}
