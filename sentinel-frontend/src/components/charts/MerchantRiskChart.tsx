"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { TooltipProps } from "recharts";
import { exactNumber } from "@/lib/format";
import type { MerchantRisk } from "@/types";
import { TooltipRow, TooltipSurface } from "./ChartFrame";

/** Magnitude on one ordered dimension: a single-hue sequential ramp, darkest = worst. */
const RAMP = ["var(--seq-700)", "var(--seq-550)", "var(--seq-400)", "var(--seq-250)"];

function rampStep(index: number, total: number): string {
  if (total <= 1) return RAMP[0];
  const bucket = Math.floor((index / total) * RAMP.length);
  return RAMP[Math.min(bucket, RAMP.length - 1)];
}

function MerchantTooltip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload as MerchantRisk;
  return (
    <TooltipSurface>
      <p className="mb-1 text-xs font-medium text-ink">{point.merchantId}</p>
      <TooltipRow label="Flagged transactions" value={exactNumber(point.flaggedCount)} />
    </TooltipSurface>
  );
}

export function MerchantRiskChart({ merchants }: { merchants: MerchantRisk[] }) {
  const height = Math.max(200, merchants.length * 34);

  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart
        layout="vertical"
        data={merchants}
        margin={{ top: 0, right: 44, bottom: 0, left: 0 }}
        barCategoryGap="22%"
      >
        <CartesianGrid stroke="var(--grid)" strokeWidth={1} horizontal={false} />
        <XAxis
          type="number"
          tick={{ fontSize: 11, fill: "var(--ink-muted)" }}
          tickLine={false}
          axisLine={false}
          allowDecimals={false}
        />
        <YAxis
          type="category"
          dataKey="merchantId"
          tick={{ fontSize: 11, fill: "var(--ink-secondary)" }}
          tickLine={false}
          axisLine={{ stroke: "var(--line-strong)" }}
          width={150}
        />
        <Tooltip content={<MerchantTooltip />} cursor={{ fill: "var(--surface-sunken)" }} />
        {/* 4px rounded data-end, square at the baseline; capped thickness leaves air in the band. */}
        <Bar dataKey="flaggedCount" radius={[0, 4, 4, 0]} maxBarSize={20} isAnimationActive={false}>
          {merchants.map((merchant, index) => (
            <Cell key={merchant.merchantId} fill={rampStep(index, merchants.length)} />
          ))}
          <LabelList
            dataKey="flaggedCount"
            position="right"
            offset={8}
            className="tabular"
            style={{ fontSize: 11, fill: "var(--ink-secondary)" }}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
