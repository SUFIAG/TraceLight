"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { LogoMark } from "@/components/layout/Logo";

export default function IndexPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace(apiClient.isAuthenticated() ? "/dashboard" : "/login");
  }, [router]);

  return (
    <div className="grid min-h-screen place-items-center bg-page">
      <div className="flex flex-col items-center gap-4">
        <LogoMark className="size-10" />
        <Loader2 className="size-5 animate-spin text-ink-muted" />
        <span className="sr-only">Loading Tracelight</span>
      </div>
    </div>
  );
}
