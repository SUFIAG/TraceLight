"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ArrowRight } from "lucide-react";
import { apiClient, ApiError } from "@/lib/api-client";
import { useAuthStore } from "@/store/auth-store";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { PasswordField } from "@/components/security/PasswordField";
import { ThemeToggle } from "@/components/layout/ThemeToggle";

const schema = z.object({
  firstName: z.string().min(1, "Enter your first name"),
  lastName: z.string().min(1, "Enter your last name"),
  email: z.string().min(1, "Enter your work email").email("Enter a valid email address"),
  password: z
    .string()
    .min(12, "Password must be at least 12 characters")
    .regex(/[A-Z]/, "Include a capital letter")
    .regex(/[a-z]/, "Include a lower-case letter")
    .regex(/[0-9]/, "Include a number")
    .regex(/[^A-Za-z0-9]/, "Include a symbol"),
});

type FormValues = z.infer<typeof schema>;

export default function RegisterPage() {
  const router = useRouter();
  const signIn = useAuthStore((state) => state.signIn);
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: "", lastName: "", email: "", password: "" },
  });

  const password = watch("password");

  const onSubmit = async (values: FormValues) => {
    setSubmitting(true);
    try {
      await apiClient.register(values);
      // Registration returns the user, not a session, so sign in straight after
      // to avoid dropping someone on the login screen they just came from.
      await signIn(values.email, values.password);
      toast.success("Account created", "Welcome to Tracelight.");
      router.replace("/dashboard");
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : "Could not create the account.";
      setError("email", { message });
      toast.fromError(error, "Registration failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-page px-6 py-10">
      <div className="mx-auto flex w-full max-w-sm items-center justify-between">
        <Logo />
        <ThemeToggle />
      </div>

      <div className="mx-auto mt-12 w-full max-w-sm">
        <div className="mb-7 space-y-1.5">
          <h1 className="text-xl font-semibold tracking-tight text-ink">Create your account</h1>
          <p className="text-sm text-ink-secondary">
            New accounts join the default organisation with the analyst role.
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="firstName">First name</Label>
              <Input
                id="firstName"
                autoComplete="given-name"
                aria-invalid={Boolean(errors.firstName)}
                {...register("firstName")}
              />
              {errors.firstName ? (
                <p role="alert" className="text-xs text-critical">
                  {errors.firstName.message}
                </p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                autoComplete="family-name"
                aria-invalid={Boolean(errors.lastName)}
                {...register("lastName")}
              />
              {errors.lastName ? (
                <p role="alert" className="text-xs text-critical">
                  {errors.lastName.message}
                </p>
              ) : null}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="email">Work email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              placeholder="analyst@yourbank.com"
              aria-invalid={Boolean(errors.email)}
              {...register("email")}
            />
            {errors.email ? (
              <p role="alert" className="text-xs text-critical">
                {errors.email.message}
              </p>
            ) : null}
          </div>

          <PasswordField
            id="password"
            label="Password"
            value={password}
            onChange={(value) => setValue("password", value, { shouldValidate: true })}
            email={watch("email")}
            error={errors.password?.message}
            showStrength
          />

          <Button type="submit" className="w-full" loading={submitting} size="lg">
            {!submitting && <ArrowRight />}
            Create account
          </Button>
        </form>

        <p className="mt-6 text-center text-xs text-ink-secondary">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
