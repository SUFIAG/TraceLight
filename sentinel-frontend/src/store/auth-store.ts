"use client";

import { create } from "zustand";
import { apiClient } from "@/lib/api-client";
import type { LoginResponse, User } from "@/types";

/**
 * A sign-in that stopped at the second factor.
 *
 * <p>Held in memory only. The challenge is useless without the code, expires
 * in minutes, and must not survive a reload: a refreshed page should land on a
 * clean sign-in form, not half-way through somebody else's.
 */
export interface MfaChallenge {
  challengeId: string;
  methods: string[];
  email: string;
}

interface AuthState {
  user: User | null;
  /** Null until the first session check completes, so guards can wait. */
  status: "unknown" | "authenticated" | "anonymous";
  /** Set when the password was accepted but the second factor is outstanding. */
  pendingMfa: MfaChallenge | null;
  setUser: (user: User | null) => void;
  /** Resolves the current session from the stored token exactly once. */
  hydrate: () => Promise<void>;
  /** Re-reads the account, after a security change that alters it. */
  refresh: () => Promise<void>;
  signIn: (email: string, password: string) => Promise<LoginResponse>;
  completeMfa: (code: string) => Promise<User>;
  cancelMfa: () => void;
  signOut: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  status: "unknown",
  pendingMfa: null,

  setUser: (user) => set({ user, status: user ? "authenticated" : "anonymous" }),

  hydrate: async () => {
    if (get().status !== "unknown") return;
    if (!apiClient.isAuthenticated()) {
      set({ user: null, status: "anonymous" });
      return;
    }
    try {
      const user = await apiClient.currentUser();
      set({ user, status: "authenticated" });
    } catch {
      apiClient.clearSession();
      set({ user: null, status: "anonymous" });
    }
  },

  refresh: async () => {
    if (!apiClient.isAuthenticated()) return;
    try {
      set({ user: await apiClient.currentUser() });
    } catch {
      // A failure here means the session went away; the request layer already
      // redirects on 401, so there is nothing useful to do with it.
    }
  },

  signIn: async (email, password) => {
    const result = await apiClient.login({ email, password });
    if (result.mfaRequired && result.challengeId) {
      set({
        pendingMfa: {
          challengeId: result.challengeId,
          methods: result.mfaMethods ?? ["TOTP"],
          email,
        },
      });
      return result;
    }
    set({ user: result.user, status: "authenticated", pendingMfa: null });
    return result;
  },

  completeMfa: async (code) => {
    const pending = get().pendingMfa;
    if (!pending) throw new Error("There is no sign-in waiting for a code.");
    const result = await apiClient.verifyMfa(pending.challengeId, code);
    set({ user: result.user, status: "authenticated", pendingMfa: null });
    return result.user;
  },

  cancelMfa: () => set({ pendingMfa: null }),

  signOut: async () => {
    await apiClient.logout();
    set({ user: null, status: "anonymous", pendingMfa: null });
  },
}));
