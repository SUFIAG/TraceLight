import Link from "next/link";
import { Button } from "@/components/ui/button";
import { LogoMark } from "@/components/layout/Logo";

export default function NotFound() {
  return (
    <div className="grid min-h-screen place-items-center bg-page px-6">
      <div className="max-w-md space-y-5 text-center">
        <LogoMark className="mx-auto size-10" />
        <div className="space-y-2">
          <p className="text-sm font-medium uppercase tracking-wide text-ink-muted">404</p>
          <h1 className="text-xl font-semibold tracking-tight text-ink">
            This page does not exist
          </h1>
          <p className="text-sm leading-relaxed text-ink-secondary">
            The link may be out of date. Everything in the sidebar resolves to a working
            screen — start from the overview.
          </p>
        </div>
        <Button asChild>
          <Link href="/dashboard">Back to overview</Link>
        </Button>
      </div>
    </div>
  );
}
