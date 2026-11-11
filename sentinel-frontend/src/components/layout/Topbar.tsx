"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { ChevronDown, LogOut, Menu, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { fullName } from "@/lib/format";
import { useAuth } from "@/lib/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "./ThemeToggle";
import { SeedDemoButton } from "./SeedDemoButton";
import { Logo } from "./Logo";
import { UserAvatar } from "@/components/security/UserAvatar";
import { NAV } from "./Sidebar";

export function Topbar({ title, subtitle }: { title: string; subtitle?: string }) {
  const { user, signOut } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-line bg-surface/85 px-4 backdrop-blur-md lg:px-6">
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden"
          aria-label="Open navigation"
          onClick={() => setMobileOpen(true)}
        >
          <Menu />
        </Button>

        <div className="min-w-0 flex-1">
          <h1 className="truncate text-base font-semibold text-ink">{title}</h1>
          {subtitle ? (
            <p className="truncate text-xs text-ink-muted">{subtitle}</p>
          ) : null}
        </div>

        <SeedDemoButton />
        <ThemeToggle />

        <DropdownMenu.Root>
          <DropdownMenu.Trigger asChild>
            <button
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors hover:bg-surface-sunken focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              aria-label="Account menu"
            >
              <UserAvatar user={user} size={28} />
              <span className="hidden text-left sm:block">
                <span className="block max-w-[10rem] truncate text-xs font-medium text-ink">
                  {fullName(user)}
                </span>
                <span className="block text-[10px] uppercase tracking-wide text-ink-muted">
                  {user?.role ?? ""}
                </span>
              </span>
              <ChevronDown className="size-3.5 text-ink-muted" />
            </button>
          </DropdownMenu.Trigger>

          <DropdownMenu.Portal>
            <DropdownMenu.Content
              align="end"
              sideOffset={8}
              className="z-50 min-w-[14rem] rounded-lg border border-line bg-surface-raised p-1 shadow-popover data-[state=open]:animate-rise-in"
            >
              <div className="px-3 py-2">
                <p className="truncate text-sm font-medium text-ink">{fullName(user)}</p>
                <p className="truncate text-xs text-ink-muted">{user?.email}</p>
              </div>
              <DropdownMenu.Separator className="my-1 h-px bg-line" />
              <DropdownMenu.Item asChild>
                <Link
                  href="/settings"
                  className="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm text-ink-secondary outline-none data-[highlighted]:bg-surface-sunken data-[highlighted]:text-ink"
                >
                  Settings
                </Link>
              </DropdownMenu.Item>
              <DropdownMenu.Item
                onSelect={signOut}
                className="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm text-critical outline-none data-[highlighted]:bg-critical-wash"
              >
                <LogOut className="size-4" />
                Sign out
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
      </header>

      {/* Mobile navigation */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div
            className="absolute inset-0 bg-black/40 animate-fade-in"
            onClick={() => setMobileOpen(false)}
            aria-hidden
          />
          <div className="absolute inset-y-0 left-0 w-64 border-r border-line bg-surface p-4 animate-slide-from-right">
            <div className="mb-4 flex items-center justify-between">
              <Logo />
              <Button
                variant="ghost"
                size="icon"
                aria-label="Close navigation"
                onClick={() => setMobileOpen(false)}
              >
                <X />
              </Button>
            </div>
            <nav className="space-y-0.5" aria-label="Main">
              {NAV.map((item) => {
                const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      "flex items-center gap-3 rounded-md px-3 py-2 text-sm",
                      active
                        ? "bg-primary-wash font-medium text-ink"
                        : "text-ink-secondary hover:bg-surface-sunken"
                    )}
                  >
                    <Icon className={cn("size-4", active ? "text-primary" : "text-ink-muted")} />
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>
        </div>
      )}
    </>
  );
}
