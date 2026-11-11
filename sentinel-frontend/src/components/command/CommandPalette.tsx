"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  AlertTriangle,
  BarChart3,
  Bell,
  Briefcase,
  CornerDownLeft,
  Database,
  Gauge,
  Loader2,
  Receipt,
  Search,
  Settings,
  Sparkles,
  SunMoon,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { cn } from "@/lib/utils";
import { shortId } from "@/lib/format";

type Action = {
  id: string;
  label: string;
  hint?: string;
  group: "Go to" | "Actions" | "Alerts" | "Cases";
  icon: React.ComponentType<{ className?: string }>;
  run: () => void | Promise<void>;
  keywords?: string;
};

/**
 * Keyboard-first command surface, opened with Ctrl/Cmd+K.
 *
 * An analyst working a queue should never have to leave the keyboard to reach
 * a page, open a specific alert, or kick off an AI investigation. Everything
 * reachable here is reachable by mouse too; this exists to make the common path
 * three keystrokes instead of three clicks and a scroll.
 */
export function CommandPalette() {
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [cursor, setCursor] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [remote, setRemote] = useState<Action[]>([]);

  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  /* ---------- open / close ---------- */

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setOpen((current) => !current);
      }
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (!open) {
      setQuery("");
      setCursor(0);
      setRemote([]);
      return;
    }
    // Focus after the dialog has painted, or the caret lands nowhere.
    const timer = setTimeout(() => inputRef.current?.focus(), 10);
    return () => clearTimeout(timer);
  }, [open]);

  /* ---------- static actions ---------- */

  const toggleTheme = useCallback(() => {
    const root = document.documentElement;
    const next = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = next;
    window.localStorage.setItem("tracelight.theme", next);
  }, []);

  const staticActions = useMemo<Action[]>(
    () => [
      { id: "go-dashboard", label: "Overview", group: "Go to", icon: Gauge, run: () => router.push("/dashboard"), keywords: "home dashboard kpi" },
      { id: "go-alerts", label: "Alerts", group: "Go to", icon: Bell, run: () => router.push("/alerts"), keywords: "queue detections" },
      { id: "go-cases", label: "Cases", group: "Go to", icon: Briefcase, run: () => router.push("/cases"), keywords: "investigations" },
      { id: "go-transactions", label: "Transactions", group: "Go to", icon: Receipt, run: () => router.push("/transactions"), keywords: "payments ledger" },
      { id: "go-analytics", label: "Analytics", group: "Go to", icon: BarChart3, run: () => router.push("/analytics"), keywords: "charts reports trend" },
      { id: "go-patterns", label: "Patterns", group: "Go to", icon: Sparkles, run: () => router.push("/patterns"), keywords: "ai discovered" },
      { id: "go-settings", label: "Settings", group: "Go to", icon: Settings, run: () => router.push("/settings"), keywords: "profile account" },

      {
        id: "act-seed",
        label: "Load demo data",
        hint: "Generates a realistic dataset through the live pipeline",
        group: "Actions",
        icon: Database,
        keywords: "seed sample populate",
        run: async () => {
          setBusy("act-seed");
          try {
            const result = await apiClient.seedDemoData(true);
            if (result.skipped) {
              toast.info("Seed skipped", result.message);
            } else {
              toast.success(
                "Demo data loaded",
                `${result.transactionsCreated} transactions, ${result.transactionsFlagged} flagged, ${result.casesCreated} cases.`
              );
            }
            router.refresh();
          } catch (error) {
            toast.fromError(error, "Could not load demo data");
          } finally {
            setBusy(null);
          }
        },
      },
      {
        id: "act-patterns",
        label: "Run pattern discovery",
        hint: "Re-mines the transaction history for emerging fraud patterns",
        group: "Actions",
        icon: Sparkles,
        keywords: "ai analyze mine",
        run: async () => {
          setBusy("act-patterns");
          try {
            const patterns = await apiClient.analyzePatterns();
            toast.success("Pattern discovery complete", `${patterns.length} patterns in scope.`);
            router.push("/patterns");
          } catch (error) {
            toast.fromError(error, "Pattern discovery failed");
          } finally {
            setBusy(null);
          }
        },
      },
      // A fixed icon rather than one derived from the current theme: this list is
      // built during server render too, where `document` does not exist.
      { id: "act-theme", label: "Toggle light / dark", group: "Actions", icon: SunMoon, run: toggleTheme, keywords: "theme appearance contrast dark mode" },
    ],
    [router, toggleTheme]
  );

  /* ---------- remote lookup ---------- */

  useEffect(() => {
    const term = query.trim();
    if (term.length < 2) {
      setRemote([]);
      return;
    }

    let cancelled = false;
    // Debounced so typing a word does not fire a request per keystroke.
    const timer = setTimeout(async () => {
      try {
        const [alerts, cases] = await Promise.all([
          apiClient.listAlerts({ page: 0, size: 30 }),
          apiClient.listCases({ page: 0, size: 30 }),
        ]);
        if (cancelled) return;

        const lower = term.toLowerCase();

        const alertHits: Action[] = alerts.content
          .filter(
            (alert) =>
              alert.id.toLowerCase().includes(lower) ||
              alert.reason?.toLowerCase().includes(lower) ||
              alert.triggeredRules?.some((rule) => rule.toLowerCase().includes(lower))
          )
          .slice(0, 5)
          .map((alert) => ({
            id: `alert-${alert.id}`,
            label: alert.reason || `Alert ${shortId(alert.id)}`,
            hint: `${alert.severity} · ${alert.status}`,
            group: "Alerts" as const,
            icon: AlertTriangle,
            run: () => router.push(`/alerts/${alert.id}`),
          }));

        const caseHits: Action[] = cases.content
          .filter((item) => item.id.toLowerCase().includes(lower) || item.title?.toLowerCase().includes(lower))
          .slice(0, 5)
          .map((item) => ({
            id: `case-${item.id}`,
            label: item.title || `Case ${shortId(item.id)}`,
            hint: `${item.priority} · ${item.status}`,
            group: "Cases" as const,
            icon: Briefcase,
            run: () => router.push(`/cases/${item.id}`),
          }));

        setRemote([...alertHits, ...caseHits]);
      } catch {
        // A failed lookup must not break navigation; static actions still filter.
        if (!cancelled) setRemote([]);
      }
    }, 220);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, router]);

  /* ---------- filtering ---------- */

  const results = useMemo(() => {
    const term = query.trim().toLowerCase();
    const matches = term
      ? staticActions.filter(
          (action) =>
            action.label.toLowerCase().includes(term) ||
            action.keywords?.toLowerCase().includes(term)
        )
      : staticActions;
    return [...matches, ...remote];
  }, [query, staticActions, remote]);

  useEffect(() => setCursor(0), [query]);

  useEffect(() => {
    listRef.current?.querySelector('[data-active="true"]')?.scrollIntoView({ block: "nearest" });
  }, [cursor]);

  // Awaited before closing, so a long-running action keeps its spinner visible
  // instead of the palette vanishing while the request is still in flight.
  const execute = useCallback(async (action: Action) => {
    await action.run();
    setOpen(false);
  }, []);

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setCursor((index) => Math.min(index + 1, results.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setCursor((index) => Math.max(index - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const action = results[cursor];
      if (action) void execute(action);
    }
  };

  if (!open) return null;

  let lastGroup = "";

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center p-4 pt-[12vh]"
      role="dialog"
      aria-modal="true"
      aria-label="Command palette"
    >
      <button
        type="button"
        aria-label="Close command palette"
        className="absolute inset-0 animate-fade-in cursor-default bg-ink/40 backdrop-blur-sm"
        onClick={() => setOpen(false)}
      />

      <div className="relative w-full max-w-xl animate-rise-in overflow-hidden rounded-lg border border-line-strong bg-surface-raised shadow-popover">
        <div className="flex items-center gap-2 border-b border-line px-3">
          <Search className="size-4 shrink-0 text-ink-muted" aria-hidden="true" />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Search pages, alerts, cases, or run an action…"
            className="h-12 w-full bg-transparent text-sm text-ink outline-none placeholder:text-ink-muted"
            aria-label="Command"
            aria-controls="command-results"
            autoComplete="off"
          />
          <kbd className="hidden shrink-0 rounded border border-line px-1.5 py-0.5 text-[10px] text-ink-muted sm:block">
            ESC
          </kbd>
        </div>

        <ul ref={listRef} id="command-results" className="scrollbar-slim max-h-[56vh] overflow-y-auto p-1.5">
          {results.length === 0 ? (
            <li className="px-3 py-8 text-center text-sm text-ink-muted">
              Nothing matches “{query}”.
            </li>
          ) : (
            results.map((action, index) => {
              const showGroup = action.group !== lastGroup;
              lastGroup = action.group;
              const active = index === cursor;
              const Icon = action.icon;

              return (
                <li key={action.id}>
                  {showGroup && (
                    <p className="px-2 pb-1 pt-3 text-[10px] font-medium uppercase tracking-wide text-ink-muted first:pt-1">
                      {action.group}
                    </p>
                  )}
                  <button
                    type="button"
                    data-active={active}
                    onMouseEnter={() => setCursor(index)}
                    onClick={() => void execute(action)}
                    className={cn(
                      "flex w-full items-center gap-2.5 rounded-md px-2 py-2 text-left text-sm transition-colors",
                      active ? "bg-primary-wash text-ink" : "text-ink-secondary hover:bg-surface-sunken"
                    )}
                  >
                    {busy === action.id ? (
                      <Loader2 className="size-4 shrink-0 animate-spin text-primary" />
                    ) : (
                      <Icon className={cn("size-4 shrink-0", active ? "text-primary" : "text-ink-muted")} />
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate">{action.label}</span>
                      {action.hint && <span className="block truncate text-[11px] text-ink-muted">{action.hint}</span>}
                    </span>
                    {active && <CornerDownLeft className="size-3.5 shrink-0 text-ink-muted" aria-hidden="true" />}
                  </button>
                </li>
              );
            })
          )}
        </ul>

        <div className="flex items-center justify-between border-t border-line px-3 py-2 text-[11px] text-ink-muted">
          <span className="flex items-center gap-2">
            <kbd className="rounded border border-line px-1">↑</kbd>
            <kbd className="rounded border border-line px-1">↓</kbd>
            to navigate
          </span>
          <span className="flex items-center gap-1.5">
            <kbd className="rounded border border-line px-1">↵</kbd> to select
          </span>
        </div>
      </div>
    </div>
  );
}
