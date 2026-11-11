"use client";

import { useState } from "react";
import { ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CodeInput } from "@/components/security/CodeInput";
import { PasswordField } from "@/components/security/PasswordField";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";

interface StepUpDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** True when the account has a second factor, so a code is asked for instead of a password. */
  mfaEnabled: boolean;
  onConfirmed: () => void;
}

/**
 * Re-confirms identity so a protected action can go through.
 *
 * <p>Shown when the API answers {@code STEP_UP_REQUIRED}. Without it the
 * refusal would be a dead end — the user would be told their session is too
 * old to change a role, with no way to make it fresh.
 */
export function StepUpDialog({ open, onOpenChange, mfaEnabled, onConfirmed }: StepUpDialogProps) {
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [working, setWorking] = useState(false);

  const confirm = async (value?: string) => {
    setWorking(true);
    try {
      const result = await apiClient.stepUp(
        mfaEnabled ? { code: value ?? code } : { password }
      );
      setCode("");
      setPassword("");
      onOpenChange(false);
      onConfirmed();
      toast.success(
        "Identity confirmed",
        `Sensitive changes are unlocked for ${result.validForMinutes} minutes.`
      );
    } catch (error) {
      toast.fromError(error, "That did not match");
      setCode("");
    } finally {
      setWorking(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="size-4 text-primary" />
            Confirm it is you
          </DialogTitle>
          <DialogDescription>
            This change affects who can do what, so it needs a fresh check.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {mfaEnabled ? (
            <CodeInput value={code} onChange={setCode} onComplete={confirm} disabled={working} />
          ) : (
            <PasswordField
              id="step-up-password"
              label="Password"
              value={password}
              onChange={setPassword}
              autoComplete="current-password"
            />
          )}
          <Button
            className="w-full"
            onClick={() => confirm()}
            loading={working}
            disabled={mfaEnabled ? code.length < 6 : password.length === 0}
          >
            Confirm
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
