"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, MailCheck } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";

/**
 * Starts a password reset.
 *
 * <p>The confirmation is identical whether or not the address exists, because
 * the backend answers that way on purpose: a page that says "no such account"
 * is a free customer list for anyone with a wordlist.
 */
export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [working, setWorking] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setWorking(true);
    try {
      const result = await apiClient.forgotPassword(email.trim());
      setSent(true);
      if (result.developmentToken) {
        // Only returned while the platform has no mail provider wired up.
        toast.info("Development reset token", result.developmentToken);
        router.push(`/reset-password?token=${encodeURIComponent(result.developmentToken)}`);
      }
    } catch (error) {
      toast.fromError(error, "Could not start the reset");
    } finally {
      setWorking(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-page">
      <header className="flex items-center justify-between px-6 py-5">
        <Logo />
        <ThemeToggle />
      </header>

      <main className="flex flex-1 items-center justify-center px-6 pb-20">
        <div className="w-full max-w-sm">
          {sent ? (
            <div className="space-y-4">
              <div className="flex size-9 items-center justify-center rounded-md border border-line bg-primary-wash">
                <MailCheck className="size-4 text-primary" />
              </div>
              <h1 className="text-xl font-semibold tracking-tight text-ink">Check your email</h1>
              <p className="text-sm leading-relaxed text-ink-secondary">
                If an account exists for <span className="text-ink">{email}</span>, a reset link is
                on its way. It expires in 30 minutes, and using it signs that account out of every
                device.
              </p>
              <Link href="/login">
                <Button variant="secondary" className="w-full">
                  <ArrowLeft />
                  Back to sign in
                </Button>
              </Link>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4" noValidate>
              <div className="space-y-1.5">
                <h1 className="text-xl font-semibold tracking-tight text-ink">
                  Reset your password
                </h1>
                <p className="text-sm text-ink-secondary">
                  Enter the address you sign in with and we will send a reset link.
                </p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="email">Work email</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  autoComplete="email"
                  placeholder="analyst@yourbank.com"
                  onChange={(event) => setEmail(event.target.value)}
                  required
                />
              </div>

              <Button type="submit" className="w-full" size="lg" loading={working}>
                Send reset link
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
          )}
        </div>
      </main>
    </div>
  );
}
