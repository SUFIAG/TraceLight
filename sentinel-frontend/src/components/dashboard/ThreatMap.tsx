"use client";

import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import { exactNumber, percent } from "@/lib/format";
import type { GeographicRisk } from "@/types";

/**
 * Equirectangular projection constants. Longitude -180..180 maps to x 0..100,
 * latitude 90..-90 maps to y 0..100, both expressed as percentages so the
 * markers stay correct at any container width.
 */
const COUNTRY_COORDS: Record<string, [number, number]> = {
  GB: [-2.0, 54.0],  US: [-98.6, 39.8],  DE: [10.4, 51.2],  FR: [2.2, 46.6],
  ES: [-3.7, 40.2],  NL: [5.3, 52.1],    AE: [54.0, 24.0],  SA: [45.0, 24.0],
  NG: [8.7, 9.1],    RU: [99.0, 61.5],   VN: [108.3, 14.1], BR: [-51.9, -14.2],
  UA: [31.2, 48.4],  ID: [118.0, -2.5],  IN: [79.0, 20.6],  CN: [104.2, 35.9],
  JP: [138.3, 36.2], AU: [133.8, -25.3], ZA: [22.9, -30.6], CA: [-106.3, 56.1],
  MX: [-102.6, 23.6], PK: [69.3, 30.4],  EG: [30.8, 26.8],  TR: [35.2, 39.0],
  IT: [12.6, 41.9],  PL: [19.1, 51.9],   SE: [18.6, 60.1],  CH: [8.2, 46.8],
  SG: [103.8, 1.35], HK: [114.2, 22.3],  KR: [127.8, 35.9], AR: [-63.6, -38.4],
};

function project(code: string): [number, number] | null {
  const coords = COUNTRY_COORDS[code?.toUpperCase()];
  if (!coords) return null;
  const [lon, lat] = coords;
  return [((lon + 180) / 360) * 100, ((90 - lat) / 180) * 100];
}

function toneFor(fraudRate: number) {
  if (fraudRate >= 40) return { fill: "var(--critical)", label: "Critical" };
  if (fraudRate >= 20) return { fill: "var(--serious)", label: "Elevated" };
  if (fraudRate >= 5) return { fill: "var(--warning)", label: "Watch" };
  return { fill: "var(--good)", label: "Normal" };
}

/**
 * Geographic risk plotted on a graticule rather than a filled basemap.
 *
 * A coastline outline would add several hundred kilobytes and tell an analyst
 * nothing a grid does not: what matters is where the clusters are relative to
 * each other, and how hot each one is. Marker area scales with volume and
 * colour with fraud rate, so a small country with a severe problem is as
 * visible as a large one with a mild one.
 *
 * The table beneath it carries the same numbers for screen readers and for
 * anyone who needs the exact figures.
 */
export function ThreatMap({ rows }: { rows: GeographicRisk[] }) {
  const [active, setActive] = useState<string | null>(null);

  const points = useMemo(() => {
    const maxVolume = Math.max(...rows.map((row) => row.totalTransactions), 1);
    return rows
      .map((row) => {
        const position = project(row.country);
        if (!position) return null;
        const tone = toneFor(row.fraudRate);
        // Square-root scaling so marker *area*, not radius, tracks volume.
        const radius = 3 + Math.sqrt(row.totalTransactions / maxVolume) * 9;
        return { ...row, x: position[0], y: position[1], ...tone, radius };
      })
      .filter(Boolean) as Array<GeographicRisk & { x: number; y: number; fill: string; label: string; radius: number }>;
  }, [rows]);

  const hot = useMemo(
    () => [...points].sort((a, b) => b.fraudRate - a.fraudRate).slice(0, 3).map((p) => p.country),
    [points]
  );

  const selected = points.find((point) => point.country === active);

  return (
    <div className="space-y-3">
      <div className="relative aspect-[2/1] w-full overflow-hidden rounded-md border border-line bg-surface-sunken">
        {/* Graticule */}
        <div className="surface-grid absolute inset-0 animate-grid-drift" aria-hidden="true" />

        {/* Equator and prime meridian, so the projection is readable as a map. */}
        <div className="absolute inset-x-0 top-1/2 h-px bg-line-strong opacity-40" aria-hidden="true" />
        <div className="absolute inset-y-0 left-1/2 w-px bg-line-strong opacity-40" aria-hidden="true" />

        <svg viewBox="0 0 100 50" className="absolute inset-0 size-full" preserveAspectRatio="none" aria-hidden="true">
          {points.map((point) => {
            const cx = point.x;
            const cy = point.y / 2; // viewBox is 2:1, latitude percentage halves.
            const r = point.radius / 4;
            const isHot = hot.includes(point.country);

            return (
              <g key={point.country}>
                {isHot && (
                  <circle
                    cx={cx}
                    cy={cy}
                    r={r}
                    fill={point.fill}
                    opacity="0.4"
                    className="animate-radar-ping origin-center"
                    style={{ transformOrigin: `${cx}px ${cy}px` }}
                  />
                )}
                <circle
                  cx={cx}
                  cy={cy}
                  r={r}
                  fill={point.fill}
                  fillOpacity={active === point.country ? 0.95 : 0.6}
                  stroke={point.fill}
                  strokeWidth="0.3"
                  vectorEffect="non-scaling-stroke"
                  className="cursor-pointer transition-all duration-200"
                  onMouseEnter={() => setActive(point.country)}
                  onMouseLeave={() => setActive(null)}
                />
              </g>
            );
          })}
        </svg>

        {/* Country codes sit in HTML, not SVG, so they stay unsquashed by preserveAspectRatio="none". */}
        {points.map((point) => (
          <span
            key={point.country}
            className={cn(
              "pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 text-[9px] font-medium tracking-wide transition-opacity",
              active && active !== point.country ? "opacity-30" : "opacity-100"
            )}
            style={{ left: `${point.x}%`, top: `${point.y}%`, color: point.fill }}
          >
            {point.country}
          </span>
        ))}

        {selected && (
          <div className="pointer-events-none absolute bottom-2 left-2 animate-fade-in rounded-md border border-line bg-surface-raised px-2.5 py-1.5 text-xs shadow-popover">
            <p className="font-medium text-ink">{selected.country}</p>
            <p className="text-ink-secondary numeric">
              {exactNumber(selected.totalTransactions)} transactions · {exactNumber(selected.flaggedTransactions)} flagged
            </p>
            <p style={{ color: selected.fill }} className="numeric">
              {percent(selected.fraudRate, 1)} fraud rate · {selected.label}
            </p>
          </div>
        )}

        <div className="absolute right-2 top-2 flex items-center gap-2.5 rounded-md border border-line bg-surface-raised/90 px-2 py-1 text-[10px] text-ink-secondary backdrop-blur">
          {["Normal", "Watch", "Elevated", "Critical"].map((label, index) => (
            <span key={label} className="inline-flex items-center gap-1">
              <span
                className="size-2 rounded-full"
                style={{ backgroundColor: ["var(--good)", "var(--warning)", "var(--serious)", "var(--critical)"][index] }}
              />
              {label}
            </span>
          ))}
        </div>
      </div>

      <GeographyTable rows={rows} active={active} onHover={setActive} />
    </div>
  );
}

function GeographyTable({
  rows,
  active,
  onHover,
}: {
  rows: GeographicRisk[];
  active: string | null;
  onHover: (country: string | null) => void;
}) {
  const worst = Math.max(...rows.map((row) => row.fraudRate), 1);

  return (
    <div className="scrollbar-slim max-h-[180px] overflow-y-auto">
      <table className="w-full text-sm">
        <caption className="sr-only">Transaction volume and fraud rate by country</caption>
        <thead className="sticky top-0 bg-surface">
          <tr className="border-b border-line text-left">
            <th className="pb-2 text-xs font-medium uppercase tracking-wide text-ink-muted">Country</th>
            <th className="pb-2 text-right text-xs font-medium uppercase tracking-wide text-ink-muted">Total</th>
            <th className="pb-2 text-right text-xs font-medium uppercase tracking-wide text-ink-muted">Flagged</th>
            <th className="w-28 pb-2 text-right text-xs font-medium uppercase tracking-wide text-ink-muted">Rate</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={row.country}
              onMouseEnter={() => onHover(row.country)}
              onMouseLeave={() => onHover(null)}
              className={cn(
                "border-b border-line transition-colors last:border-0",
                active === row.country && "bg-surface-sunken"
              )}
            >
              <td className="py-2 font-medium text-ink">{row.country}</td>
              <td className="py-2 text-right text-ink-secondary numeric">{exactNumber(row.totalTransactions)}</td>
              <td className="py-2 text-right text-ink-secondary numeric">{exactNumber(row.flaggedTransactions)}</td>
              <td className="py-2 pl-3">
                <div className="flex items-center justify-end gap-2">
                  <span className="h-1.5 w-12 overflow-hidden rounded-full bg-seq-100">
                    <span
                      className="block h-full rounded-full transition-all duration-500"
                      style={{
                        width: `${Math.max(4, (row.fraudRate / worst) * 100)}%`,
                        backgroundColor: toneFor(row.fraudRate).fill,
                      }}
                    />
                  </span>
                  <span className="w-12 text-right text-xs text-ink numeric">{percent(row.fraudRate, 1)}</span>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
