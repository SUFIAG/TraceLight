"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ShieldCheck } from "lucide-react";
import { apiClient, ApiError } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { PasswordField, passwordLooksValid } from "@/components/security/PasswordField";

/**
 * Finishes a password reset.
 *
 * <p>The token normally arrives in the link. It is also editable, because the
 * development sender writes codes to the log rather than to an inbox, and
 * because a user who copies a token out of an email client that mangled the
 * link should still be able to finish.
 */
function ResetPasswordForm() {
  const router = useRouter();
  const params = useSearchParams();

  const [token, setToken] = useState(params.get("token") ?? "");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  const mismatch = confirmation.length > 0 && password !== confirmation;
  const ready = token.trim().length > 0 && passwordLooksValid(password) && !mismatch;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!ready) return;
    setWorking(true);
    setError(null);
    try {
      await apiClient.resetPassword(token.trim(), password);
      toast.success("Password reset", "Sign in with your new password.");
      router.replace("/login");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "That reset could not be completed.");
    } finally {
      setWorking(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <div className="space-y-1.5">
        <div className="flex size-9 items-center justify-center rounded-md border border-line bg-primary-wash">
          <ShieldCheck className="size-4 text-primary" />
        </div>
        <h1 className="pt-2 text-xl font-semibold tracking-tight text-ink">Choose a new password</h1>
        <p className="text-sm text-ink-secondary">
          Resetting signs this account out of every device, including this one.
        </p>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="token">Reset token</Label>
        <Input
          id="token"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder="Paste the token from your email"
          className="font-mono text-xs"
        />
      </div>

      <PasswordField
        id="new-password"
        label="New password"
        value={password}
        onChange={setPassword}
        showStrength
      />

      <PasswordField
        id="confirm-password"
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

      <Button type="submit" className="w-full" size="lg" loading={working} disabled={!ready}>
        Reset password
      </Button>

      <p className="text-center text-xs">
        <Link
          href="/login"
          className="font-medium text-ink-secondary underline-offset-4 hover:text-ink hover:underline"
        >
          Back to sign in
        </Link>
      </p>
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <div className="flex min-h-screen flex-col bg-page">
      <header className="flex items-center justify-between px-6 py-5">
        <Logo />
        <ThemeToggle />
      </header>
      <main className="flex flex-1 items-center justify-center px-6 pb-20">
        <div className="w-full max-w-sm">
          {/* useSearchParams needs a boundary for the static shell. */}
          <Suspense fallback={null}>
            <ResetPasswordForm />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
