"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import {
  BarChart3,
  Bell,
  Bot,
  Brain,
  FileSearch,
  FolderKanban,
  Gavel,
  LayoutDashboard,
  Network,
  Plug,
  Receipt,
  ScrollText,
  Settings,
  Share2,
  ShieldAlert,
  Sparkles,
  TrendingUp,
  UserCheck,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Logo } from "./Logo";

/**
 * Every entry here resolves to a real page. Navigation that 404s is worse than
 * navigation that is short.
 *
 * Grouped because the platform now spans four jobs — working the queue,
 * hunting networks, onboarding identities, and supervising the automation —
 * and a flat list of eighteen links hides all four.
 */
interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

const NAV_GROUPS: Array<{ label: string; items: NavItem[] }> = [
  {
    label: "Operations",
    items: [
      { href: "/dashboard", label: "Overview", icon: LayoutDashboard },
      { href: "/alerts", label: "Alert queue", icon: Bell },
      { href: "/cases", label: "Cases", icon: FolderKanban },
      { href: "/transactions", label: "Transactions", icon: Receipt },
      { href: "/disputes", label: "Disputes", icon: Gavel },
    ],
  },
  {
    label: "Detection",
    items: [
      { href: "/scams", label: "Scam defence", icon: ShieldAlert },
      { href: "/network", label: "Networks & mules", icon: Network },
      { href: "/identity", label: "Identity & KYC", icon: UserCheck },
      { href: "/aml", label: "AML & reporting", icon: ScrollText },
      { href: "/genai", label: "GenAI defence", icon: Bot },
    ],
  },
  {
    label: "Intelligence",
    items: [
      { href: "/autonomy", label: "AI operations", icon: Sparkles },
      { href: "/strategy", label: "Rule studio", icon: Brain },
      { href: "/patterns", label: "Patterns", icon: FileSearch },
      { href: "/consortium", label: "Consortium", icon: Share2 },
    ],
  },
  {
    label: "Assurance",
    items: [
      { href: "/roi", label: "Value & forecast", icon: TrendingUp },
      { href: "/analytics", label: "Analytics", icon: BarChart3 },
      { href: "/governance", label: "Model governance", icon: ShieldAlert },
      { href: "/integrations", label: "Integrations", icon: Plug },
      { href: "/settings", label: "Settings", icon: Settings },
    ],
  },
];

/** Flattened, for the mobile menu and the command palette. */
const NAV = NAV_GROUPS.flatMap((group) => group.items);

export function Sidebar({ openAlerts = 0 }: { openAlerts?: number }) {
  const pathname = usePathname();

  return (
    <aside className="hidden w-60 shrink-0 flex-col border-r border-line bg-surface lg:flex">
      <div className="flex h-16 items-center px-5">
        <Link href="/dashboard" className="rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
          <Logo />
        </Link>
      </div>

      <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-2" aria-label="Main">
        {NAV_GROUPS.map((group) => (
          <div key={group.label} className="space-y-0.5">
            <p className="px-3 pb-1 text-[10px] font-semibold uppercase tracking-wider text-ink-muted">
              {group.label}
            </p>
            {group.items.map((item) => {
              const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "relative flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary",
                    active
                      ? "font-medium text-ink"
                      : "text-ink-secondary hover:bg-surface-sunken hover:text-ink"
                  )}
                >
                  {active && (
                    <motion.span
                      layoutId="nav-active"
                      className="absolute inset-0 -z-10 rounded-md bg-primary-wash"
                      transition={{ type: "spring", stiffness: 420, damping: 34 }}
                    />
                  )}
                  <Icon className={cn("size-4 shrink-0", active ? "text-primary" : "text-ink-muted")} />
                  <span className="flex-1">{item.label}</span>
                  {item.href === "/alerts" && openAlerts > 0 && (
                    <span className="rounded-full bg-critical px-1.5 py-0.5 text-[10px] font-semibold tabular text-white">
                      {openAlerts > 99 ? "99+" : openAlerts}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="border-t border-line p-4">
        <p className="text-[11px] leading-relaxed text-ink-muted">
          Decisions are advisory. Every AI output is recorded and replayable.
        </p>
      </div>
    </aside>
  );
}

export { NAV, NAV_GROUPS };
