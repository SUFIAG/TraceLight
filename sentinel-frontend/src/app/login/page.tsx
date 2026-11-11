"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { motion } from "framer-motion";
import { ArrowRight, FileText, Gauge, ShieldCheck } from "lucide-react";
import { apiClient, ApiError } from "@/lib/api-client";
import { useAuthStore } from "@/store/auth-store";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { MfaStep } from "@/components/security/MfaStep";
import { ThemeToggle } from "@/components/layout/ThemeToggle";

const schema = z.object({
  email: z.string().min(1, "Enter your work email").email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type FormValues = z.infer<typeof schema>;

const PROOF_POINTS = [
  {
    icon: Gauge,
    title: "Thirty minutes becomes three",
    body: "The agent gathers the evidence and drafts the narrative. The analyst reviews and signs.",
  },
  {
    icon: FileText,
    title: "Every decision is a record",
    body: "Each agent run is persisted with its prompt, response and confidence, and can be replayed.",
  },
  {
    icon: ShieldCheck,
    title: "Advisory, never automatic",
    body: "Tracelight recommends. A human closes the case. That boundary is enforced in the product.",
  },
];

export default function LoginPage() {
  const router = useRouter();
  const signIn = useAuthStore((state) => state.signIn);
  const pendingMfa = useAuthStore((state) => state.pendingMfa);
  const cancelMfa = useAuthStore((state) => state.cancelMfa);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  useEffect(() => {
    if (apiClient.isAuthenticated()) router.replace("/dashboard");
  }, [router]);

  const onSubmit = async (values: FormValues) => {
    setSubmitting(true);
    try {
      const result = await signIn(values.email, values.password);
      // A second factor means the password was right but the sign-in is not
      // finished. The panel switches to the code step rather than navigating.
      if (result.mfaRequired) {
        setSubmitting(false);
        return;
      }
      toast.success("Signed in", "Welcome back to Tracelight.");
      router.replace(result.mustChangePassword ? "/settings?tab=security" : "/dashboard");
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : "Sign-in failed. Try again.";
      // Attach the failure to the form as well as the toast, so the reason sits
      // next to the fields the user has to correct.
      setError("password", { message });
      toast.fromError(error, "Could not sign in");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid min-h-screen bg-page lg:grid-cols-[1fr_minmax(0,32rem)]">
      {/* Narrative panel — the pitch, visible while someone signs in. */}
      <section className="relative hidden flex-col justify-between overflow-hidden bg-surface p-12 lg:flex">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 opacity-[0.55]"
          style={{
            backgroundImage:
              "radial-gradient(circle at 18% 12%, var(--primary-wash), transparent 42%), radial-gradient(circle at 82% 78%, var(--primary-wash), transparent 46%)",
          }}
        />
        <div className="relative">
          <Logo />
        </div>

        <div className="relative max-w-lg space-y-10">
          <div className="space-y-4">
            <h2 className="text-3xl font-semibold leading-tight tracking-tight text-ink">
              Every other fraud tool gives you a score.
              <span className="block text-primary">Tracelight gives you the case file.</span>
            </h2>
            <p className="text-sm leading-relaxed text-ink-secondary">
              Score transactions, triage the queue, and let the investigation agent write
              the analyst-ready narrative — with an audit trail a regulator can read.
            </p>
          </div>

          <ul className="space-y-5">
            {PROOF_POINTS.map((point, index) => (
              <motion.li
                key={point.title}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.08 * index + 0.1, duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                className="flex gap-3"
              >
                <span className="grid size-8 shrink-0 place-items-center rounded-md border border-line bg-surface-raised">
                  <point.icon className="size-4 text-primary" />
                </span>
                <div className="space-y-0.5">
                  <p className="text-sm font-medium text-ink">{point.title}</p>
                  <p className="text-xs leading-relaxed text-ink-secondary">{point.body}</p>
                </div>
              </motion.li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-ink-muted">
          Tracelight — AI fraud intelligence
        </p>
      </section>

      {/* Form panel */}
      <section className="flex flex-col justify-center px-6 py-10 sm:px-12">
        <div className="mb-8 flex items-center justify-between lg:hidden">
          <Logo />
          <ThemeToggle />
        </div>

        <div className="mx-auto w-full max-w-sm">
          {pendingMfa ? (
            <MfaStep
              email={pendingMfa.email}
              methods={pendingMfa.methods}
              onCancel={cancelMfa}
              onVerified={() => {
                toast.success("Signed in", "Welcome back to Tracelight.");
                router.replace("/dashboard");
              }}
            />
          ) : (
            <>
          <div className="mb-7 space-y-1.5">
            <h1 className="text-xl font-semibold tracking-tight text-ink">Sign in</h1>
            <p className="text-sm text-ink-secondary">
              Use your Tracelight analyst account.
            </p>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <div className="space-y-1.5">
              <Label htmlFor="email">Work email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="analyst@yourbank.com"
                aria-invalid={Boolean(errors.email)}
                aria-describedby={errors.email ? "email-error" : undefined}
                {...register("email")}
              />
              {errors.email ? (
                <p id="email-error" role="alert" className="text-xs text-critical">
                  {errors.email.message}
                </p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                aria-invalid={Boolean(errors.password)}
                aria-describedby={errors.password ? "password-error" : undefined}
                {...register("password")}
              />
              {errors.password ? (
                <p id="password-error" role="alert" className="text-xs text-critical">
                  {errors.password.message}
                </p>
              ) : null}
            </div>

            <Button type="submit" className="w-full" loading={submitting} size="lg">
              {!submitting && <ArrowRight />}
              Sign in
            </Button>
          </form>

          <p className="mt-4 text-center text-xs">
            <Link
              href="/forgot-password"
              className="font-medium text-ink-secondary underline-offset-4 hover:text-ink hover:underline"
            >
              Forgot your password?
            </Link>
          </p>

          <p className="mt-6 text-center text-xs text-ink-secondary">
            No account yet?{" "}
            <Link
              href="/register"
              className="font-medium text-primary underline-offset-4 hover:underline"
            >
              Create one
            </Link>
          </p>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
