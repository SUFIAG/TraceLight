"use client";

import { useId, useMemo } from "react";

export interface SparklineProps {
  values: number[];
  /** Any CSS colour. Defaults to the primary token. */
  stroke?: string;
  /** Fills the area under the line at low opacity. */
  filled?: boolean;
  className?: string;
  width?: number;
  height?: number;
  /** Draws the line on mount instead of appearing instantly. */
  animate?: boolean;
}

/**
 * A trend line sized to sit inside a stat tile.
 *
 * Deliberately axis-free and label-free. Its job is to answer "which way is
 * this going" in peripheral vision; the exact figures live in the tile above
 * it and in the full chart on the analytics page.
 */
export function Sparkline({
  values,
  stroke = "var(--primary)",
  filled = true,
  className,
  width = 100,
  height = 28,
  animate = true,
}: SparklineProps) {
  const gradientId = useId();

  const { line, area, last } = useMemo(() => {
    if (values.length < 2) return { line: "", area: "", last: null as null | [number, number] };

    const min = Math.min(...values);
    const max = Math.max(...values);
    // A flat series would divide by zero; render it as a centred straight line.
    const span = max - min || 1;
    const stepX = width / (values.length - 1);
    // Inset by 2px top and bottom so the stroke is never clipped.
    const toY = (value: number) => height - 2 - ((value - min) / span) * (height - 4);

    const points = values.map((value, index) => [index * stepX, toY(value)] as [number, number]);
    const path = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`).join(" ");

    return {
      line: path,
      area: `${path} L${width},${height} L0,${height} Z`,
      last: points[points.length - 1],
    };
  }, [values, width, height]);

  if (!line) return null;

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      width={width}
      height={height}
      className={className}
      aria-hidden="true"
      preserveAspectRatio="none"
    >
      {filled && (
        <>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={stroke} stopOpacity="0.22" />
              <stop offset="100%" stopColor={stroke} stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={area} fill={`url(#${gradientId})`} />
        </>
      )}

      <path
        d={line}
        fill="none"
        stroke={stroke}
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
        className={animate ? "animate-draw-line" : undefined}
        style={
          animate
            ? // 600 comfortably exceeds any path length at this size, so the
              // dash fully hides the line before the animation reveals it.
              ({ strokeDasharray: 600, "--draw-length": "600" } as React.CSSProperties)
            : undefined
        }
      />

      {last && <circle cx={last[0]} cy={last[1]} r="2" fill={stroke} />}
    </svg>
  );
}
