"use client";

import { useEffect, useRef, useState } from "react";

/**
 * Animates a number from its previous value to the next one.
 *
 * Used on monitoring tiles so a figure that changes under a polling refresh
 * visibly moves rather than silently swapping. A number that jumps is easy to
 * miss; a number that travels is not.
 *
 * Respects `prefers-reduced-motion`: readers who have asked for less movement
 * get the final value immediately.
 */
export function useCountUp(target: number | undefined, durationMs = 650): number {
  const [display, setDisplay] = useState(target ?? 0);
  const fromRef = useRef(target ?? 0);
  const frameRef = useRef<number | null>(null);

  useEffect(() => {
    if (target === undefined || Number.isNaN(target)) return;

    const reduced =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const from = fromRef.current;
    const delta = target - from;

    if (reduced || delta === 0) {
      fromRef.current = target;
      setDisplay(target);
      return;
    }

    const start = performance.now();

    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / durationMs);
      // Ease-out cubic: fast first, settling at the end, so the final value
      // is readable well before the animation technically finishes.
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(from + delta * eased);

      if (progress < 1) {
        frameRef.current = requestAnimationFrame(step);
      } else {
        fromRef.current = target;
      }
    };

    frameRef.current = requestAnimationFrame(step);
    return () => {
      if (frameRef.current) cancelAnimationFrame(frameRef.current);
      fromRef.current = target;
    };
  }, [target, durationMs]);

  return display;
}
