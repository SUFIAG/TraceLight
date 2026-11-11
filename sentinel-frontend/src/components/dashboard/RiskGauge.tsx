"use client";

import { useMemo } from "react";
import { useCountUp } from "@/lib/hooks/useCountUp";
import { cn } from "@/lib/utils";

export interface RiskGaugeProps {
  /** 0-100. */
  score: number | undefined;
  size?: number;
  /** Renders the band name under the figure. */
  showLabel?: boolean;
  className?: string;
}

/** Bands match RiskLevel on the backend. Keep the two in step. */
const BANDS = [
  { max: 30, name: "Low", token: "var(--good)", text: "text-good" },
  { max: 60, name: "Medium", token: "var(--warning)", text: "text-warning" },
  { max: 80, name: "High", token: "var(--serious)", text: "text-serious" },
  { max: 100, name: "Critical", token: "var(--critical)", text: "text-critical" },
] as const;

function bandFor(score: number) {
  return BANDS.find((band) => score <= band.max) ?? BANDS[BANDS.length - 1];
}

/**
 * A 240-degree arc gauge for a single risk score.
 *
 * The arc is split into the four scoring bands so the number is read against
 * the thresholds that decide what happens to the transaction, not against a
 * bare 0-100 range. Colour is never the only carrier: the band name is printed
 * and the needle position encodes the same information.
 */
export function RiskGauge({ score, size = 160, showLabel = true, className }: RiskGaugeProps) {
  const animated = useCountUp(score ?? 0, 900);
  const value = Math.max(0, Math.min(100, animated));
  const band = bandFor(score ?? 0);

  const geometry = useMemo(() => {
    const radius = size / 2 - 14;
    const centre = size / 2;
    // 240-degree sweep, opening downward: start at 150 degrees, end at 390.
    const START = 150;
    const SWEEP = 240;

    const point = (angleDeg: number, r: number) => {
      const rad = (angleDeg * Math.PI) / 180;
      return [centre + r * Math.cos(rad), centre + r * Math.sin(rad)] as const;
    };

    const arc = (fromPct: number, toPct: number, r: number) => {
      const a0 = START + (fromPct / 100) * SWEEP;
      const a1 = START + (toPct / 100) * SWEEP;
      const [x0, y0] = point(a0, r);
      const [x1, y1] = point(a1, r);
      const large = a1 - a0 > 180 ? 1 : 0;
      return `M${x0},${y0} A${r},${r} 0 ${large} 1 ${x1},${y1}`;
    };

    return { radius, centre, point, arc, START, SWEEP };
  }, [size]);

  const { radius, centre, point, arc, START, SWEEP } = geometry;
  const needleAngle = START + (value / 100) * SWEEP;
  const [needleX, needleY] = point(needleAngle, radius - 6);

  return (
    <div className={cn("relative inline-flex flex-col items-center", className)}>
      <svg
        width={size}
        height={size * 0.78}
        viewBox={`0 0 ${size} ${size * 0.78}`}
        role="img"
        aria-label={`Risk score ${Math.round(score ?? 0)} of 100, ${band.name}`}
      >
        {/* Track */}
        <path
          d={arc(0, 100, radius)}
          fill="none"
          stroke="var(--surface-sunken)"
          strokeWidth="10"
          strokeLinecap="round"
        />

        {/* Band segments, drawn at low opacity so the needle stays dominant. */}
        {BANDS.map((segment, index) => {
          const from = index === 0 ? 0 : BANDS[index - 1].max;
          return (
            <path
              key={segment.name}
              d={arc(from, segment.max, radius)}
              fill="none"
              stroke={segment.token}
              strokeWidth="10"
              strokeLinecap="butt"
              opacity={0.26}
            />
          );
        })}

        {/* Filled portion up to the current score. */}
        <path
          d={arc(0, Math.max(0.5, value), radius)}
          fill="none"
          stroke={band.token}
          strokeWidth="10"
          strokeLinecap="round"
        />

        {/* Needle */}
        <line
          x1={centre}
          y1={centre}
          x2={needleX}
          y2={needleY}
          stroke="var(--ink)"
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx={centre} cy={centre} r="4" fill="var(--ink)" />
        <circle cx={centre} cy={centre} r="8" fill="none" stroke={band.token} strokeWidth="1.5" opacity="0.5" />
      </svg>

      <div className="-mt-7 flex flex-col items-center">
        <span className={cn("text-stat font-semibold numeric", band.text)}>
          {Math.round(value)}
        </span>
        {showLabel && (
          <span className="text-[11px] uppercase tracking-wide text-ink-muted">{band.name} risk</span>
        )}
      </div>
    </div>
  );
}
