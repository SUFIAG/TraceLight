"use client";

import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { TooltipProps } from "recharts";
import { compactNumber, exactNumber, percent, shortDate } from "@/lib/format";
import type { FraudTrendPoint } from "@/types";
import { TooltipRow, TooltipSurface, type SeriesKey } from "./ChartFrame";

const AXIS_TICK = { fontSize: 11, fill: "var(--ink-muted)" };

export const TREND_SERIES: SeriesKey[] = [
  { key: "totalTransactions", label: "Transactions", color: "var(--series-1)" },
  { key: "flaggedTransactions", label: "Flagged", color: "var(--series-2)" },
];

function TrendTooltip({ active, payload, label }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as FraudTrendPoint;
  return (
    <TooltipSurface>
      <p className="mb-1 text-xs font-medium text-ink">{shortDate(label as string)}</p>
      <TooltipRow
        color="var(--series-1)"
        label="Transactions"
        value={exactNumber(point.totalTransactions)}
      />
      <TooltipRow
        color="var(--series-2)"
        label="Flagged"
        value={exactNumber(point.flaggedTransactions)}
      />
      <TooltipRow label="Fraud rate" value={percent(point.fraudRate)} />
    </TooltipSurface>
  );
}

/**
 * Transaction volume with the flagged subset overlaid on one shared axis.
 * Both series count the same unit, so a second y-scale would misstate them.
 */
export function FraudTrendChart({ points }: { points: FraudTrendPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height={260}>
      <ComposedChart data={points} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
        <defs>
          <linearGradient id="trend-total" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--series-1)" stopOpacity={0.14} />
            <stop offset="100%" stopColor="var(--series-1)" stopOpacity={0.01} />
          </linearGradient>
        </defs>

        <CartesianGrid stroke="var(--grid)" strokeWidth={1} vertical={false} />
        <XAxis
          dataKey="date"
          tickFormatter={(value) => shortDate(value)}
          tick={AXIS_TICK}
          tickLine={false}
          axisLine={{ stroke: "var(--line-strong)" }}
          minTickGap={24}
        />
        <YAxis
          tick={AXIS_TICK}
          tickLine={false}
          axisLine={false}
          tickFormatter={(value) => compactNumber(value as number)}
          width={56}
        />
        <Tooltip
          content={<TrendTooltip />}
          cursor={{ stroke: "var(--line-strong)", strokeWidth: 1 }}
        />

        <Area
          type="monotone"
          dataKey="totalTransactions"
          stroke="var(--series-1)"
          strokeWidth={2}
          fill="url(#trend-total)"
          activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--surface)" }}
        />
        <Line
          type="monotone"
          dataKey="flaggedTransactions"
          stroke="var(--series-2)"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--surface)" }}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}
