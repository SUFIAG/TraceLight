"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, MailWarning } from "lucide-react";
import { AppShell } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { StepUpDialog } from "@/components/security/StepUpDialog";
import { ProfileTab } from "@/components/settings/ProfileTab";
import { SecurityTab } from "@/components/settings/SecurityTab";
import { SessionsTab } from "@/components/settings/SessionsTab";
import { TeamTab } from "@/components/settings/TeamTab";
import { SystemTab } from "@/components/settings/SystemTab";
import { useAuth } from "@/lib/hooks/useAuth";
import { useSecurityOverview } from "@/lib/hooks/useApi";

/**
 * The account screen.
 *
 * <p>Organised by what a person came to do — change something about
 * themselves, harden the account, check where they are signed in, manage the
 * team — rather than by which backend owns the data. Anything an
 * administrator alone can do is hidden from everybody else rather than shown
 * and refused.
 */
function SettingsContent() {
  const router = useRouter();
  const params = useSearchParams();
  const { user, isAdmin } = useAuth();
  const overview = useSecurityOverview();

  const [tab, setTab] = useState(params.get("tab") ?? "profile");
  const [stepUpOpen, setStepUpOpen] = useState(false);

  return (
    <AppShell title="Settings" subtitle="Your account, your security, and your team">
      <div className="space-y-4">
        {user?.mustChangePassword ? (
          <Banner
            tone="critical"
            icon={AlertTriangle}
            title="Set a new password"
            body="This account is on a one-time password. Until it is changed, the rest of the platform is closed to it."
            action={
              <Button size="sm" onClick={() => setTab("security")}>
                Change password
              </Button>
            }
          />
        ) : null}

        {overview.data && !overview.data.emailVerified ? (
          <Banner
            tone="warning"
            icon={MailWarning}
            title="Verify your email address"
            body={`We need to confirm ${overview.data.email} before it can be used to recover the account.`}
            action={
              <Button size="sm" variant="secondary" onClick={() => setTab("security")}>
                Verify now
              </Button>
            }
          />
        ) : null}

        <Tabs
          value={tab}
          onValueChange={(value) => {
            setTab(value);
            router.replace(`/settings?tab=${value}`, { scroll: false });
          }}
        >
          <TabsList>
            <TabsTrigger value="profile">Profile</TabsTrigger>
            <TabsTrigger value="security">Security</TabsTrigger>
            <TabsTrigger value="sessions">Devices &amp; activity</TabsTrigger>
            {isAdmin ? <TabsTrigger value="team">Team</TabsTrigger> : null}
            {isAdmin ? <TabsTrigger value="system">System</TabsTrigger> : null}
          </TabsList>

          <TabsContent value="profile">
            <ProfileTab />
          </TabsContent>

          <TabsContent value="security">
            <SecurityTab />
          </TabsContent>

          <TabsContent value="sessions">
            <SessionsTab />
          </TabsContent>

          {isAdmin ? (
            <TabsContent value="team">
              <TeamTab onStepUpRequired={() => setStepUpOpen(true)} />
            </TabsContent>
          ) : null}

          {isAdmin ? (
            <TabsContent value="system">
              <SystemTab />
            </TabsContent>
          ) : null}
        </Tabs>
      </div>

      <StepUpDialog
        open={stepUpOpen}
        onOpenChange={setStepUpOpen}
        mfaEnabled={Boolean(overview.data?.mfaEnabled)}
        onConfirmed={() => undefined}
      />
    </AppShell>
  );
}

function Banner({
  tone,
  icon: Icon,
  title,
  body,
  action,
}: {
  tone: "warning" | "critical";
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  body: string;
  action?: React.ReactNode;
}) {
  return (
    <div
      className={
        tone === "critical"
          ? "flex flex-wrap items-center gap-3 rounded-md border border-critical/40 bg-critical-wash px-4 py-3"
          : "flex flex-wrap items-center gap-3 rounded-md border border-warning/40 bg-warning-wash px-4 py-3"
      }
    >
      <Icon className={tone === "critical" ? "size-4 text-critical" : "size-4 text-warning"} />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-ink">{title}</p>
        <p className="text-xs text-ink-secondary">{body}</p>
      </div>
      {action}
    </div>
  );
}

export default function SettingsPage() {
  return (
    // useSearchParams needs a boundary so the shell can still be prerendered.
    <Suspense fallback={null}>
      <SettingsContent />
    </Suspense>
  );
}
