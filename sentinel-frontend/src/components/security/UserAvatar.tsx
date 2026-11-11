"use client";

import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";
import { cn } from "@/lib/utils";
import type { User } from "@/types";

interface UserAvatarProps {
  user: Pick<User, "id" | "firstName" | "lastName" | "email" | "avatarUpdatedAt"> | null | undefined;
  size?: number;
  className?: string;
  /** Changing this re-fetches, so an upload shows immediately. */
  cacheKey?: string | null;
}

/**
 * A user's picture, or their initials.
 *
 * <p>Avatars sit behind the same authentication as everything else, so they
 * cannot be a plain `src` — the image is fetched with the access token and
 * held as an object URL, which this component revokes when it goes away.
 */
export function UserAvatar({ user, size = 36, className, cacheKey }: UserAvatarProps) {
  const [url, setUrl] = useState<string | null>(null);
  const stamp = cacheKey ?? user?.avatarUpdatedAt ?? null;

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    if (user?.id && stamp) {
      void apiClient.avatarObjectUrl(user.id).then((result) => {
        if (cancelled) {
          if (result) URL.revokeObjectURL(result);
          return;
        }
        objectUrl = result;
        setUrl(result);
      });
    } else {
      setUrl(null);
    }

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [user?.id, stamp]);

  const initials =
    [user?.firstName?.[0], user?.lastName?.[0]].filter(Boolean).join("").toUpperCase() ||
    user?.email?.[0]?.toUpperCase() ||
    "?";

  return (
    <span
      className={cn(
        "inline-grid shrink-0 place-items-center overflow-hidden rounded-full border border-line bg-primary-wash font-medium text-primary",
        className
      )}
      style={{ width: size, height: size, fontSize: Math.max(10, size * 0.36) }}
      aria-hidden={!user}
    >
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element -- object URL, not a remote asset
        <img src={url} alt="" width={size} height={size} className="size-full object-cover" />
      ) : (
        initials
      )}
    </span>
  );
}
