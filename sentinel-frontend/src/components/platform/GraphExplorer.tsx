"use client";

import { useMemo } from "react";
import type { GraphView } from "@/types";

/**
 * Link-analysis view, laid out radially around the entity under
 * investigation.
 *
 * A deterministic radial layout rather than a force simulation: an
 * investigator who reopens a network next week needs it to look the same, and
 * a layout that settles differently on every render makes screenshots and
 * shared references useless.
 */
export function GraphExplorer({
  graph,
  onSelect,
}: {
  graph: GraphView;
  onSelect?: (entityId: string) => void;
}) {
  const size = 520;
  const centre = size / 2;

  const positions = useMemo(() => {
    const map = new Map<string, { x: number; y: number }>();
    const others = graph.nodes.filter((node) => !node.root);
    map.set(graph.rootId, { x: centre, y: centre });

    // Highest-degree neighbours sit on the inner ring, where they are easiest
    // to read: in a mule network the controller is the node everything touches.
    const inner = others.slice(0, 12);
    const outer = others.slice(12);

    inner.forEach((node, index) => {
      const angle = (index / Math.max(1, inner.length)) * Math.PI * 2;
      map.set(node.id, {
        x: centre + Math.cos(angle) * 130,
        y: centre + Math.sin(angle) * 130,
      });
    });

    outer.forEach((node, index) => {
      const angle = (index / Math.max(1, outer.length)) * Math.PI * 2 + 0.3;
      map.set(node.id, {
        x: centre + Math.cos(angle) * 215,
        y: centre + Math.sin(angle) * 215,
      });
    });

    return map;
  }, [graph, centre]);

  const colourFor = (risk: number) =>
    risk >= 80 ? "var(--critical)" : risk >= 60 ? "var(--serious)" : risk >= 35 ? "var(--warning)" : "var(--good)";

  return (
    <svg
      viewBox={`0 0 ${size} ${size}`}
      className="h-[520px] w-full"
      role="img"
      aria-label={`Network around ${graph.rootId}: ${graph.nodes.length} entities, ${graph.edges.length} links`}
    >
      {graph.edges.map((edge, index) => {
        const from = positions.get(edge.source);
        const to = positions.get(edge.target);
        if (!from || !to) return null;
        return (
          <line
            key={`${edge.source}-${edge.target}-${index}`}
            x1={from.x}
            y1={from.y}
            x2={to.x}
            y2={to.y}
            stroke="currentColor"
            className="text-line-strong"
            strokeWidth={Math.max(0.5, edge.strength * 2.5)}
            strokeOpacity={0.25 + edge.strength * 0.5}
          >
            <title>
              {edge.label}
              {edge.via ? ` via ${edge.via}` : ""} · {edge.observations} sighting(s)
            </title>
          </line>
        );
      })}

      {graph.nodes.map((node) => {
        const point = positions.get(node.id);
        if (!point) return null;
        const radius = node.root ? 16 : Math.min(13, 6 + node.degree);
        return (
          <g
            key={node.id}
            transform={`translate(${point.x}, ${point.y})`}
            className={onSelect ? "cursor-pointer" : undefined}
            onClick={() => onSelect?.(node.id)}
          >
            <circle
              r={radius}
              fill={colourFor(node.risk)}
              fillOpacity={node.root ? 1 : 0.85}
              stroke="currentColor"
              className="text-surface"
              strokeWidth={2}
            >
              <title>
                {node.label} · risk {node.risk} · {node.degree} link(s)
              </title>
            </circle>
            <text
              y={radius + 11}
              textAnchor="middle"
              className="fill-ink-secondary text-[9px]"
            >
              {node.label.length > 16 ? `${node.label.slice(0, 15)}…` : node.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
