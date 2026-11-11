"use client";

import { useEffect } from "react";
import { useAuthStore } from "@/store/auth-store";

/**
 * Reads the auth session, hydrating it from the stored token on first use.
 * `status` is "unknown" until that check finishes, so callers can render a
 * loading state instead of briefly flashing a signed-out view.
 */
export function useAuth() {
  const user = useAuthStore((state) => state.user);
  const status = useAuthStore((state) => state.status);
  const pendingMfa = useAuthStore((state) => state.pendingMfa);
  const hydrate = useAuthStore((state) => state.hydrate);
  const refresh = useAuthStore((state) => state.refresh);
  const signIn = useAuthStore((state) => state.signIn);
  const completeMfa = useAuthStore((state) => state.completeMfa);
  const cancelMfa = useAuthStore((state) => state.cancelMfa);
  const signOut = useAuthStore((state) => state.signOut);

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  return {
    user,
    status,
    isAuthenticated: status === "authenticated",
    isResolving: status === "unknown",
    isAdmin: user?.role === "ADMIN",
    pendingMfa,
    signIn,
    completeMfa,
    cancelMfa,
    signOut,
    refresh,
  };
}
