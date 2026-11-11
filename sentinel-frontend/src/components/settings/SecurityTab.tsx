"use client";

import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  CheckCircle2,
  Copy,
  KeyRound,
  Mail,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { CodeInput } from "@/components/security/CodeInput";
import { PasswordField, passwordLooksValid } from "@/components/security/PasswordField";
import { apiClient, ApiError } from "@/lib/api-client";
import { dateTime } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useAuth } from "@/lib/hooks/useAuth";
import { useSecurityOverview } from "@/lib/hooks/useApi";
import type { MfaEnrolment } from "@/types";

/**
 * Everything a user does to their own account security: change the password,
 * enrol a second factor, keep recovery codes, verify a phone and an email.
 */
export function SecurityTab() {
  const { user, refresh } = useAuth();
  const overview = useSecurityOverview();

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <PostureCard loading={overview.isLoading} />
      <PasswordCard email={user?.email} onChanged={() => void overview.mutate()} />
      <MfaCard
        enabled={Boolean(overview.data?.mfaEnabled)}
        enrolledAt={overview.data?.mfaEnrolledAt ?? null}
        backupCodesRemaining={overview.data?.backupCodesRemaining ?? 0}
        onChanged={async () => {
          await overview.mutate();
          await refresh();
        }}
      />
      <ContactCard
        emailVerified={Boolean(overview.data?.emailVerified)}
        email={overview.data?.email ?? user?.email ?? "—"}
        phoneVerified={Boolean(overview.data?.phoneVerified)}
        phoneNumber={overview.data?.phoneNumber ?? "—"}
        onChanged={async () => {
          await overview.mutate();
          await refresh();
        }}
      />
    </div>
  );

  function PostureCard({ loading }: { loading: boolean }) {
    const score = overview.data?.score ?? 0;
    const advice = overview.data?.advice ?? [];
    const tone = score >= 90 ? "good" : score >= 60 ? "warning" : "critical";

    return (
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Account protection</CardTitle>
          <CardDescription>What is switched on, and what is missing</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {loading ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <>
              <div className="flex items-end gap-3">
                <span className="tabular text-3xl font-semibold text-ink">{score}</span>
                <Badge tone={tone}>
                  {score >= 90 ? "Well protected" : score >= 60 ? "Could be stronger" : "At risk"}
                </Badge>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-surface-sunken">
                <div
                  className={
                    tone === "good" ? "h-full bg-good" : tone === "warning" ? "h-full bg-warning" : "h-full bg-critical"
                  }
                  style={{ width: `${Math.max(6, score)}%` }}
                />
              </div>
              {advice.length ? (
                <ul className="space-y-1.5">
                  {advice.map((item) => (
                    <li key={item} className="flex items-start gap-2 text-sm text-ink-secondary">
                      <ShieldAlert className="mt-0.5 size-3.5 shrink-0 text-warning" />
                      {item}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="flex items-center gap-2 text-sm text-ink-secondary">
                  <CheckCircle2 className="size-4 text-good" />
                  Nothing outstanding on this account.
                </p>
              )}
              <p className="text-xs text-ink-muted">
                Password last changed {dateTime(overview.data?.passwordChangedAt)}
                {overview.data?.passwordAgeDays != null
                  ? ` (${overview.data.passwordAgeDays} days ago)`
                  : ""}
                .
              </p>
            </>
          )}
        </CardContent>
      </Card>
    );
  }
}

/* ---------- Password ---------- */

function PasswordCard({ email, onChanged }: { email?: string; onChanged: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const mismatch = confirmation.length > 0 && next !== confirmation;
  const ready = current.length > 0 && passwordLooksValid(next, email) && !mismatch;

  const submit = async () => {
    setWorking(true);
    setError(null);
    try {
      const result = await apiClient.changePassword(current, next);
      toast.success("Password changed", result.message);
      setCurrent("");
      setNext("");
      setConfirmation("");
      onChanged();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "That change could not be made.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <Card>
      <CardHeader className="border-b border-line pb-4">
        <CardTitle>Password</CardTitle>
        <CardDescription>Changing it signs out every other device</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <PasswordField
          id="current-password"
          label="Current password"
          value={current}
          onChange={setCurrent}
          autoComplete="current-password"
        />
        <PasswordField
          id="next-password"
          label="New password"
          value={next}
          onChange={setNext}
          email={email}
          showStrength
        />
        <PasswordField
          id="confirm-new-password"
          label="Confirm new password"
          value={confirmation}
          onChange={setConfirmation}
          error={mismatch ? "Both passwords must match" : undefined}
        />
        {error ? (
          <p role="alert" className="text-xs text-critical">
            {error}
          </p>
        ) : null}
        <Button size="sm" onClick={submit} loading={working} disabled={!ready}>
          Change password
        </Button>
      </CardContent>
    </Card>
  );
}

/* ---------- Multi-factor ---------- */

function MfaCard({
  enabled,
  enrolledAt,
  backupCodesRemaining,
  onChanged,
}: {
  enabled: boolean;
  enrolledAt: string | null;
  backupCodesRemaining: number;
  onChanged: () => Promise<void>;
}) {
  const [enrolment, setEnrolment] = useState<MfaEnrolment | null>(null);
  const [code, setCode] = useState("");
  const [working, setWorking] = useState(false);
  const [backupCodes, setBackupCodes] = useState<string[] | null>(null);
  const [disabling, setDisabling] = useState(false);
  const [password, setPassword] = useState("");
  const [disableCode, setDisableCode] = useState("");

  const begin = async () => {
    setWorking(true);
    try {
      setEnrolment(await apiClient.beginMfaEnrolment());
    } catch (error) {
      toast.fromError(error, "Could not start enrolment");
    } finally {
      setWorking(false);
    }
  };

  const activate = async (value: string) => {
    setWorking(true);
    try {
      const result = await apiClient.activateMfa(value);
      setBackupCodes(result.backupCodes);
      setEnrolment(null);
      setCode("");
      await onChanged();
      toast.success("Multi-factor authentication on", "Save your recovery codes.");
    } catch (error) {
      toast.fromError(error, "That code did not match");
      setCode("");
    } finally {
      setWorking(false);
    }
  };

  const disable = async () => {
    setWorking(true);
    try {
      await apiClient.disableMfa(password, disableCode);
      setDisabling(false);
      setPassword("");
      setDisableCode("");
      await onChanged();
      toast.success("Multi-factor authentication off");
    } catch (error) {
      toast.fromError(error, "Could not turn it off");
    } finally {
      setWorking(false);
    }
  };

  const regenerate = async () => {
    const entered = window.prompt("Confirm your password to generate new recovery codes");
    if (!entered) return;
    setWorking(true);
    try {
      const result = await apiClient.regenerateBackupCodes(entered);
      setBackupCodes(result.backupCodes);
      await onChanged();
    } catch (error) {
      toast.fromError(error, "Could not generate new codes");
    } finally {
      setWorking(false);
    }
  };

  return (
    <>
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <div className="flex items-center justify-between gap-2">
            <div>
              <CardTitle>Multi-factor authentication</CardTitle>
              <CardDescription>A code from your phone, on top of your password</CardDescription>
            </div>
            <Badge tone={enabled ? "good" : "warning"}>{enabled ? "On" : "Off"}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {enabled ? (
            <>
              <p className="text-sm text-ink-secondary">
                Enrolled {dateTime(enrolledAt)}. {backupCodesRemaining} recovery
                {backupCodesRemaining === 1 ? " code" : " codes"} left.
              </p>
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="secondary" onClick={regenerate} loading={working}>
                  <KeyRound />
                  New recovery codes
                </Button>
                <Button size="sm" variant="ghost" onClick={() => setDisabling(true)}>
                  Turn off
                </Button>
              </div>
            </>
          ) : (
            <>
              <p className="text-sm leading-relaxed text-ink-secondary">
                A stolen password is the most common way an account is taken. A second factor makes
                one worthless on its own.
              </p>
              <Button size="sm" onClick={begin} loading={working}>
                <ShieldCheck />
                Set up authenticator
              </Button>
            </>
          )}
        </CardContent>
      </Card>

      {/* Enrolment */}
      <Dialog open={Boolean(enrolment)} onOpenChange={(open) => !open && setEnrolment(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Scan this with your authenticator</DialogTitle>
            <DialogDescription>
              Then enter the six digits it shows, so we know it works before switching it on.
            </DialogDescription>
          </DialogHeader>
          {enrolment ? (
            <div className="space-y-4">
              <div className="flex justify-center rounded-md border border-line bg-white p-4">
                <QRCodeSVG value={enrolment.provisioningUri} size={168} />
              </div>
              <div className="space-y-1.5">
                <Label>Or enter this key by hand</Label>
                <div className="flex items-center gap-2">
                  <code className="flex-1 overflow-x-auto rounded bg-surface-sunken px-2.5 py-2 font-mono text-xs text-ink-secondary">
                    {enrolment.secret}
                  </code>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      void navigator.clipboard.writeText(enrolment.secret);
                      toast.success("Key copied");
                    }}
                  >
                    <Copy />
                  </Button>
                </div>
              </div>
              <CodeInput value={code} onChange={setCode} onComplete={activate} disabled={working} />
              <Button className="w-full" onClick={() => activate(code)} loading={working}>
                Confirm and turn on
              </Button>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      {/* Recovery codes, shown once */}
      <Dialog open={Boolean(backupCodes)} onOpenChange={(open) => !open && setBackupCodes(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Save your recovery codes</DialogTitle>
            <DialogDescription>
              Each works once, and they are shown only now. Keep them where you keep your other
              credentials — they are the way back in if you lose your phone.
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-2">
            {backupCodes?.map((item) => (
              <code
                key={item}
                className="rounded border border-line bg-surface-sunken px-2 py-1.5 text-center font-mono text-xs text-ink"
              >
                {item}
              </code>
            ))}
          </div>
          <div className="mt-4 flex gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                void navigator.clipboard.writeText((backupCodes ?? []).join("\n"));
                toast.success("Codes copied");
              }}
            >
              <Copy />
              Copy all
            </Button>
            <Button size="sm" onClick={() => setBackupCodes(null)}>
              I have saved them
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Turning it off */}
      <Dialog open={disabling} onOpenChange={setDisabling}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Turn off multi-factor authentication</DialogTitle>
            <DialogDescription>
              Your password alone will then be enough to sign in. Confirm with both your password
              and a current code.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <PasswordField
              id="disable-password"
              label="Password"
              value={password}
              onChange={setPassword}
              autoComplete="current-password"
            />
            <div className="space-y-1.5">
              <Label htmlFor="disable-code">Authenticator or recovery code</Label>
              <Input
                id="disable-code"
                value={disableCode}
                onChange={(event) => setDisableCode(event.target.value)}
                placeholder="123456"
              />
            </div>
            <Button
              className="w-full"
              variant="danger"
              onClick={disable}
              loading={working}
              disabled={!password || !disableCode}
            >
              Turn it off
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

/* ---------- Email and phone ---------- */

function ContactCard({
  emailVerified,
  email,
  phoneVerified,
  phoneNumber,
  onChanged,
}: {
  emailVerified: boolean;
  email: string;
  phoneVerified: boolean;
  phoneNumber: string;
  onChanged: () => Promise<void>;
}) {
  const [emailCode, setEmailCode] = useState("");
  const [phone, setPhone] = useState("");
  const [phoneCode, setPhoneCode] = useState("");
  const [phoneSent, setPhoneSent] = useState(false);
  const [working, setWorking] = useState(false);

  const sendEmailCode = async () => {
    setWorking(true);
    try {
      const result = await apiClient.resendEmailVerification();
      toast.success(
        "Verification sent",
        result.developmentCode ? `Development code ${result.developmentCode}` : `Check ${email}.`
      );
    } catch (error) {
      toast.fromError(error, "Could not send the code");
    } finally {
      setWorking(false);
    }
  };

  const verifyEmail = async (value: string) => {
    setWorking(true);
    try {
      await apiClient.verifyEmail(value);
      setEmailCode("");
      await onChanged();
      toast.success("Email verified");
    } catch (error) {
      toast.fromError(error, "That code is not valid");
      setEmailCode("");
    } finally {
      setWorking(false);
    }
  };

  const sendPhoneCode = async () => {
    setWorking(true);
    try {
      const result = await apiClient.sendPhoneCode(phone);
      setPhoneSent(true);
      toast.success(
        "Code sent",
        result.developmentCode
          ? `Development code ${result.developmentCode}`
          : `Sent to ${result.sentTo}.`
      );
    } catch (error) {
      toast.fromError(error, "Could not send the code");
    } finally {
      setWorking(false);
    }
  };

  const verifyPhone = async (value: string) => {
    setWorking(true);
    try {
      await apiClient.verifyPhone(value);
      setPhoneSent(false);
      setPhone("");
      setPhoneCode("");
      await onChanged();
      toast.success("Phone verified");
    } catch (error) {
      toast.fromError(error, "That code is not valid");
      setPhoneCode("");
    } finally {
      setWorking(false);
    }
  };

  return (
    <Card>
      <CardHeader className="border-b border-line pb-4">
        <CardTitle>Ways to reach you</CardTitle>
        <CardDescription>Used for verification and for recovering the account</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <section className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <p className="flex items-center gap-2 text-sm text-ink">
              <Mail className="size-4 text-ink-muted" />
              {email}
            </p>
            <Badge tone={emailVerified ? "good" : "warning"}>
              {emailVerified ? "Verified" : "Unverified"}
            </Badge>
          </div>
          {!emailVerified ? (
            <div className="space-y-2">
              <CodeInput
                value={emailCode}
                onChange={setEmailCode}
                onComplete={verifyEmail}
                disabled={working}
                label="Code from your email"
              />
              <Button size="sm" variant="ghost" onClick={sendEmailCode} loading={working}>
                Send me a code
              </Button>
            </div>
          ) : null}
        </section>

        <section className="space-y-2 border-t border-line pt-4">
          <div className="flex items-center justify-between gap-2">
            <p className="flex items-center gap-2 text-sm text-ink">
              <Smartphone className="size-4 text-ink-muted" />
              {phoneVerified ? phoneNumber : "No phone number"}
            </p>
            <Badge tone={phoneVerified ? "good" : "neutral"}>
              {phoneVerified ? "Verified" : "Not set"}
            </Badge>
          </div>

          {phoneVerified ? (
            <Button
              size="sm"
              variant="ghost"
              loading={working}
              onClick={async () => {
                setWorking(true);
                try {
                  await apiClient.removePhone();
                  await onChanged();
                  toast.success("Phone removed");
                } catch (error) {
                  toast.fromError(error, "Could not remove it");
                } finally {
                  setWorking(false);
                }
              }}
            >
              Remove phone
            </Button>
          ) : phoneSent ? (
            <div className="space-y-2">
              <CodeInput
                value={phoneCode}
                onChange={setPhoneCode}
                onComplete={verifyPhone}
                disabled={working}
                label="Code from the text message"
              />
              <Button size="sm" variant="ghost" onClick={sendPhoneCode} loading={working}>
                Send it again
              </Button>
            </div>
          ) : (
            <div className="flex gap-2">
              <Input
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                placeholder="+44 7700 900000"
                aria-label="Phone number"
              />
              <Button size="sm" onClick={sendPhoneCode} loading={working} disabled={phone.length < 7}>
                Send code
              </Button>
            </div>
          )}
        </section>
      </CardContent>
    </Card>
  );
}
