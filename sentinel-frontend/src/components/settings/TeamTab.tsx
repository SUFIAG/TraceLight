"use client";

import { useState } from "react";
import {
  KeyRound,
  LockOpen,
  LogOut,
  ShieldOff,
  UserPlus,
  Users,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TableShell,
} from "@/components/ui/table";
import { ErrorState } from "@/components/data/States";
import { UserAvatar } from "@/components/security/UserAvatar";
import { apiClient, ApiError } from "@/lib/api-client";
import { fullName, humanise, relativeTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useAdminRoles, useAdminUsers } from "@/lib/hooks/useApi";
import type { InvitedUser, User } from "@/types";

/**
 * Administrator view of the team.
 *
 * <p>Every action here is one an attacker would like to perform — promote an
 * account, disable a colleague, clear a lockout — so each goes through the
 * step-up protected admin API, and the console surfaces the refusal rather
 * than hiding the button.
 */
export function TeamTab({ onStepUpRequired }: { onStepUpRequired: () => void }) {
  const users = useAdminUsers();
  const roles = useAdminRoles();

  const [inviting, setInviting] = useState(false);
  const [invited, setInvited] = useState<InvitedUser | null>(null);
  const [working, setWorking] = useState<string | null>(null);

  /** Runs an admin action, turning a step-up refusal into the re-auth prompt. */
  const run = async (key: string, action: () => Promise<unknown>, success: string) => {
    setWorking(key);
    try {
      await action();
      await users.mutate();
      toast.success(success);
    } catch (error) {
      if (error instanceof ApiError && error.code === "STEP_UP_REQUIRED") {
        onStepUpRequired();
        return;
      }
      toast.fromError(error, "That change was refused");
    } finally {
      setWorking(null);
    }
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Users className="size-4 text-primary" />
              <div>
                <CardTitle>Team</CardTitle>
                <CardDescription>Who can sign in, and what they may do</CardDescription>
              </div>
            </div>
            <Button size="sm" onClick={() => setInviting(true)}>
              <UserPlus />
              Add someone
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {users.isLoading ? (
            <div className="space-y-2 p-5">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} className="h-10 w-full" />
              ))}
            </div>
          ) : users.error ? (
            <ErrorState error={users.error} onRetry={() => users.mutate()} />
          ) : (
            <TableShell className="rounded-none border-0 shadow-none">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Person</TableHead>
                    <TableHead className="w-36">Role</TableHead>
                    <TableHead className="w-44">Security</TableHead>
                    <TableHead className="w-32">Last sign-in</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.data?.map((member) => (
                    <TableRow key={member.id}>
                      <TableCell>
                        <div className="flex items-center gap-2.5">
                          <UserAvatar user={member} size={28} />
                          <div className="min-w-0">
                            <p className="truncate text-sm font-medium text-ink">
                              {fullName(member)}
                            </p>
                            <p className="truncate text-xs text-ink-muted">{member.email}</p>
                          </div>
                        </div>
                      </TableCell>

                      <TableCell>
                        <select
                          value={member.role}
                          disabled={working === member.id}
                          onChange={(event) =>
                            run(
                              member.id,
                              () => apiClient.changeUserRole(member.id, event.target.value),
                              "Role changed"
                            )
                          }
                          className="w-full rounded border border-line bg-surface px-2 py-1 text-xs text-ink"
                          aria-label={`Role for ${member.email}`}
                        >
                          {(roles.data ?? [{ value: member.role, description: "" }]).map((role) => (
                            <option key={role.value} value={role.value}>
                              {humanise(role.value)}
                            </option>
                          ))}
                        </select>
                      </TableCell>

                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          <Badge tone={member.enabled ? "good" : "critical"}>
                            {member.enabled ? "Active" : "Disabled"}
                          </Badge>
                          {member.locked ? <Badge tone="critical">Locked</Badge> : null}
                          <Badge tone={member.mfaEnabled ? "good" : "neutral"}>
                            {member.mfaEnabled ? "MFA" : "No MFA"}
                          </Badge>
                          {member.mustChangePassword ? (
                            <Badge tone="warning">Must reset</Badge>
                          ) : null}
                        </div>
                      </TableCell>

                      <TableCell className="text-xs text-ink-muted">
                        {member.lastLogin ? relativeTime(member.lastLogin) : "Never"}
                      </TableCell>

                      <TableCell>
                        <MemberActions
                          member={member}
                          busy={working === member.id}
                          run={run}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableShell>
          )}
        </CardContent>
      </Card>

      <InviteDialog
        open={inviting}
        roles={roles.data ?? []}
        onClose={() => setInviting(false)}
        onInvited={async (result) => {
          setInviting(false);
          setInvited(result);
          await users.mutate();
        }}
        onStepUpRequired={onStepUpRequired}
      />

      <Dialog open={Boolean(invited)} onOpenChange={(open) => !open && setInvited(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Account created</DialogTitle>
            <DialogDescription>{invited?.note}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <p className="text-sm text-ink-secondary">{invited?.user.email}</p>
            <code className="block rounded border border-line bg-surface-sunken px-3 py-2 font-mono text-sm text-ink">
              {invited?.temporaryPassword}
            </code>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                void navigator.clipboard.writeText(invited?.temporaryPassword ?? "");
                toast.success("Password copied");
              }}
            >
              Copy password
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function MemberActions({
  member,
  busy,
  run,
}: {
  member: User;
  busy: boolean;
  run: (key: string, action: () => Promise<unknown>, success: string) => Promise<void>;
}) {
  return (
    <div className="flex justify-end gap-1">
      {member.locked ? (
        <IconButton
          label="Unlock"
          busy={busy}
          icon={LockOpen}
          onClick={() => run(member.id, () => apiClient.unlockUser(member.id), "Account unlocked")}
        />
      ) : null}

      <IconButton
        label="Require a password change"
        busy={busy}
        icon={KeyRound}
        onClick={() =>
          run(
            member.id,
            () => apiClient.forceUserPasswordReset(member.id),
            "They must set a new password"
          )
        }
      />

      <IconButton
        label="Sign out every device"
        busy={busy}
        icon={LogOut}
        onClick={() =>
          run(member.id, () => apiClient.revokeUserSessions(member.id), "Sessions ended")
        }
      />

      {member.mfaEnabled ? (
        <IconButton
          label="Reset their second factor"
          busy={busy}
          icon={ShieldOff}
          onClick={() =>
            run(member.id, () => apiClient.resetUserMfa(member.id), "Second factor reset")
          }
        />
      ) : null}

      <IconButton
        label={member.enabled ? "Disable account" : "Enable account"}
        busy={busy}
        icon={member.enabled ? ShieldOff : LockOpen}
        danger={member.enabled}
        onClick={() =>
          run(
            member.id,
            () => apiClient.setUserEnabled(member.id, !member.enabled),
            member.enabled ? "Account disabled" : "Account enabled"
          )
        }
      />
    </div>
  );
}

function IconButton({
  label,
  icon: Icon,
  onClick,
  busy,
  danger,
}: {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  onClick: () => void;
  busy: boolean;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      disabled={busy}
      onClick={onClick}
      className={
        danger
          ? "rounded p-1.5 text-ink-muted hover:bg-critical-wash hover:text-critical disabled:opacity-40"
          : "rounded p-1.5 text-ink-muted hover:bg-surface-sunken hover:text-ink disabled:opacity-40"
      }
    >
      <Icon className="size-3.5" />
    </button>
  );
}

function InviteDialog({
  open,
  roles,
  onClose,
  onInvited,
  onStepUpRequired,
}: {
  open: boolean;
  roles: { value: string; description: string }[];
  onClose: () => void;
  onInvited: (result: InvitedUser) => Promise<void>;
  onStepUpRequired: () => void;
}) {
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [role, setRole] = useState("ANALYST");
  const [working, setWorking] = useState(false);

  const submit = async () => {
    setWorking(true);
    try {
      const result = await apiClient.inviteUser({ email, firstName, lastName, role });
      setEmail("");
      setFirstName("");
      setLastName("");
      await onInvited(result);
    } catch (error) {
      if (error instanceof ApiError && error.code === "STEP_UP_REQUIRED") {
        onClose();
        onStepUpRequired();
        return;
      }
      toast.fromError(error, "Could not create the account");
    } finally {
      setWorking(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add someone to the team</DialogTitle>
          <DialogDescription>
            They get a one-time password that must be changed at first sign-in.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="invite-email">Work email</Label>
            <Input
              id="invite-email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="analyst@yourbank.com"
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="invite-first">First name</Label>
              <Input
                id="invite-first"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="invite-last">Last name</Label>
              <Input
                id="invite-last"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="invite-role">Role</Label>
            <select
              id="invite-role"
              value={role}
              onChange={(event) => setRole(event.target.value)}
              className="h-9 w-full rounded-md border border-line bg-surface px-2.5 text-sm text-ink"
            >
              {(roles.length ? roles : [{ value: "ANALYST", description: "" }]).map((option) => (
                <option key={option.value} value={option.value}>
                  {humanise(option.value)}
                  {option.description ? ` — ${option.description}` : ""}
                </option>
              ))}
            </select>
          </div>
          <Button
            className="w-full"
            onClick={submit}
            loading={working}
            disabled={!email.includes("@")}
          >
            Create account
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
