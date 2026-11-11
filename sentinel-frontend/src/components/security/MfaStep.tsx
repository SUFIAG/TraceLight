"use client";

import { useState } from "react";
import { ArrowLeft, KeyRound, ShieldCheck, Smartphone } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { CodeInput } from "@/components/security/CodeInput";
import { apiClient, ApiError } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { useAuthStore } from "@/store/auth-store";

interface MfaStepProps {
  email: string;
  methods: string[];
  onVerified: () => void;
  onCancel: () => void;
}

/**
 * The second half of a sign-in.
 *
 * <p>Offers the authenticator code first and keeps the alternatives one click
 * away. A recovery code is a different shape from a six-digit code, so it gets
 * a plain text field rather than digit boxes that would fight the user.
 */
export function MfaStep({ email, methods, onVerified, onCancel }: MfaStepProps) {
  const completeMfa = useAuthStore((state) => state.completeMfa);
  const pendingMfa = useAuthStore((state) => state.pendingMfa);

  const [mode, setMode] = useState<"TOTP" | "BACKUP_CODE" | "SMS">("TOTP");
  const [code, setCode] = useState("");
  const [backupCode, setBackupCode] = useState("");
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [smsSentTo, setSmsSentTo] = useState<string | null>(null);

  const submit = async (value: string) => {
    if (!value.trim()) return;
    setWorking(true);
    setError(null);
    try {
      await completeMfa(value.trim());
      onVerified();
    } catch (caught) {
      const message =
        caught instanceof ApiError ? caught.message : "That code could not be checked.";
      setError(message);
      setCode("");
    } finally {
      setWorking(false);
    }
  };

  const sendSms = async () => {
    if (!pendingMfa) return;
    setWorking(true);
    try {
      const result = await apiClient.sendMfaSms(pendingMfa.challengeId);
      setSmsSentTo(result.sentTo);
      setMode("SMS");
      toast.success(
        "Code sent",
        result.developmentCode
          ? `Development code ${result.developmentCode}`
          : `We sent a code to ${result.sentTo}.`
      );
    } catch (caught) {
      toast.fromError(caught, "Could not send a code");
    } finally {
      setWorking(false);
    }
  };

  return (
    <div className="space-y-5">
      <div className="space-y-1.5">
        <div className="flex size-9 items-center justify-center rounded-md border border-line bg-primary-wash">
          <ShieldCheck className="size-4 text-primary" />
        </div>
        <h1 className="pt-2 text-xl font-semibold tracking-tight text-ink">
          Confirm it is you
        </h1>
        <p className="text-sm text-ink-secondary">
          {mode === "BACKUP_CODE"
            ? "Enter one of the recovery codes you saved when you turned on multi-factor authentication."
            : mode === "SMS" && smsSentTo
              ? `Enter the six-digit code we sent to ${smsSentTo}.`
              : `Enter the six-digit code from your authenticator app for ${email}.`}
        </p>
      </div>

      {mode === "BACKUP_CODE" ? (
        <div className="space-y-1.5">
          <Label htmlFor="backup-code">Recovery code</Label>
          <Input
            id="backup-code"
            value={backupCode}
            onChange={(event) => setBackupCode(event.target.value.toUpperCase())}
            placeholder="XXXXX-XXXXX"
            autoComplete="one-time-code"
            aria-invalid={Boolean(error)}
          />
        </div>
      ) : (
        <CodeInput
          value={code}
          onChange={setCode}
          onComplete={submit}
          disabled={working}
          label="Authentication code"
        />
      )}

      {error ? (
        <p role="alert" className="text-xs text-critical">
          {error}
        </p>
      ) : null}

      <Button
        className="w-full"
        size="lg"
        loading={working}
        onClick={() => submit(mode === "BACKUP_CODE" ? backupCode : code)}
      >
        Verify and sign in
      </Button>

      <div className="flex flex-col gap-2 text-xs">
        {methods.includes("SMS") && mode !== "SMS" ? (
          <button
            type="button"
            onClick={sendSms}
            className="flex items-center gap-1.5 text-ink-secondary hover:text-ink"
          >
            <Smartphone className="size-3.5" />
            Send a code to my phone instead
          </button>
        ) : null}

        {methods.includes("BACKUP_CODE") && mode !== "BACKUP_CODE" ? (
          <button
            type="button"
            onClick={() => {
              setMode("BACKUP_CODE");
              setError(null);
            }}
            className="flex items-center gap-1.5 text-ink-secondary hover:text-ink"
          >
            <KeyRound className="size-3.5" />
            Use a recovery code
          </button>
        ) : null}

        {mode !== "TOTP" ? (
          <button
            type="button"
            onClick={() => {
              setMode("TOTP");
              setError(null);
            }}
            className="flex items-center gap-1.5 text-ink-secondary hover:text-ink"
          >
            <ShieldCheck className="size-3.5" />
            Use my authenticator app
          </button>
        ) : null}

        <button
          type="button"
          onClick={onCancel}
          className="flex items-center gap-1.5 text-ink-muted hover:text-ink"
        >
          <ArrowLeft className="size-3.5" />
          Back to sign in
        </button>
      </div>
    </div>
  );
}
