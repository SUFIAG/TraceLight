"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/lib/hooks/useAuth";
import { useDashboard } from "@/lib/hooks/useApi";
import { CommandPalette } from "@/components/command/CommandPalette";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

/**
 * Guards every signed-in route. Renders nothing but a spinner until the
 * session is resolved, so a signed-in user never sees the login page flash.
 */
export function AppShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  const { isAuthenticated, isResolving } = useAuth();
  const router = useRouter();
  const { data: dashboard } = useDashboard();

  useEffect(() => {
    if (!isResolving && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isResolving, isAuthenticated, router]);

  if (isResolving || !isAuthenticated) {
    return (
      <div className="grid min-h-screen place-items-center bg-page">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="size-6 animate-spin text-primary" />
          <p className="text-sm text-ink-muted">Checking your session…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-page">
      <Sidebar openAlerts={dashboard?.openAlerts ?? 0} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar title={title} subtitle={subtitle} />
        <main className="flex-1 px-4 py-6 lg:px-6 lg:py-8">
          <div className="mx-auto w-full max-w-[1400px] animate-rise-in">{children}</div>
        </main>
      </div>
      {/* Mounted once at the shell so Ctrl/Cmd+K works on every signed-in page. */}
      <CommandPalette />
    </div>
  );
}
