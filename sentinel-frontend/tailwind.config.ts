import type { Config } from "tailwindcss";

/**
 * Colors resolve to CSS custom properties defined in globals.css, so the light
 * and dark themes swap in one place and no component hardcodes a hex value.
 */
const config: Config = {
  darkMode: ["class", '[data-theme="dark"]'],
  content: [
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        page: "var(--page)",
        surface: {
          DEFAULT: "var(--surface)",
          raised: "var(--surface-raised)",
          sunken: "var(--surface-sunken)",
        },
        ink: {
          DEFAULT: "var(--ink)",
          secondary: "var(--ink-secondary)",
          muted: "var(--ink-muted)",
        },
        line: {
          DEFAULT: "var(--line)",
          strong: "var(--line-strong)",
        },
        grid: "var(--grid)",
        primary: {
          DEFAULT: "var(--primary)",
          hover: "var(--primary-hover)",
          ink: "var(--primary-ink)",
          wash: "var(--primary-wash)",
        },
        good: { DEFAULT: "var(--good)", wash: "var(--good-wash)" },
        warning: { DEFAULT: "var(--warning)", wash: "var(--warning-wash)" },
        serious: { DEFAULT: "var(--serious)", wash: "var(--serious-wash)" },
        critical: { DEFAULT: "var(--critical)", wash: "var(--critical-wash)" },
        series: {
          1: "var(--series-1)",
          2: "var(--series-2)",
          3: "var(--series-3)",
        },
        seq: {
          100: "var(--seq-100)",
          250: "var(--seq-250)",
          400: "var(--seq-400)",
          550: "var(--seq-550)",
          700: "var(--seq-700)",
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      fontSize: {
        hero: ["3rem", { lineHeight: "1.05", letterSpacing: "-0.02em" }],
        stat: ["1.75rem", { lineHeight: "1.15", letterSpacing: "-0.01em" }],
      },
      boxShadow: {
        card: "0 1px 2px rgba(11,11,11,0.04)",
        raised: "0 4px 16px rgba(11,11,11,0.08)",
        popover: "0 8px 32px rgba(11,11,11,0.12)",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0" },
          to: { opacity: "1" },
        },
        "rise-in": {
          from: { opacity: "0", transform: "translateY(6px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        "slide-from-right": {
          from: { opacity: "0", transform: "translateX(12px)" },
          to: { opacity: "1", transform: "translateX(0)" },
        },
        shimmer: {
          "100%": { transform: "translateX(100%)" },
        },
        "pulse-ring": {
          "0%": { transform: "scale(0.9)", opacity: "0.7" },
          "70%": { transform: "scale(1.6)", opacity: "0" },
          "100%": { transform: "scale(1.6)", opacity: "0" },
        },
        /* A single pass of light across a surface, used to mark a row that has
           just arrived. One pass only — a looping sweep reads as "loading". */
        "sweep-once": {
          "0%": { transform: "translateX(-100%)", opacity: "0" },
          "40%": { opacity: "1" },
          "100%": { transform: "translateX(200%)", opacity: "0" },
        },
        /* Ticks a counter digit upward when its value changes. */
        "tick-up": {
          "0%": { transform: "translateY(60%)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" },
        },
        /* Expands a radar ring outward from an origin point on the threat map. */
        "radar-ping": {
          "0%": { transform: "scale(0.4)", opacity: "0.55" },
          "100%": { transform: "scale(3)", opacity: "0" },
        },
        /* Traces an SVG path from nothing to its full length. Pairs with a
           stroke-dasharray set to the path length. */
        "draw-line": {
          from: { strokeDashoffset: "var(--draw-length, 1000)" },
          to: { strokeDashoffset: "0" },
        },
        /* Slow drift for the ambient grid behind hero surfaces. */
        "grid-drift": {
          from: { backgroundPosition: "0 0" },
          to: { backgroundPosition: "40px 40px" },
        },
        "breathe": {
          "0%, 100%": { opacity: "0.35" },
          "50%": { opacity: "0.75" },
        },
      },
      animation: {
        "fade-in": "fade-in 180ms ease-out",
        "rise-in": "rise-in 220ms cubic-bezier(0.16, 1, 0.3, 1)",
        "slide-from-right": "slide-from-right 220ms cubic-bezier(0.16, 1, 0.3, 1)",
        shimmer: "shimmer 1.6s infinite",
        "pulse-ring": "pulse-ring 2s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "sweep-once": "sweep-once 1100ms cubic-bezier(0.16, 1, 0.3, 1) 1",
        "tick-up": "tick-up 260ms cubic-bezier(0.16, 1, 0.3, 1)",
        "radar-ping": "radar-ping 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "draw-line": "draw-line 900ms cubic-bezier(0.16, 1, 0.3, 1) forwards",
        "grid-drift": "grid-drift 12s linear infinite",
        breathe: "breathe 3.2s ease-in-out infinite",
      },
    },
  },
  // No plugins. Every animation this app uses is a keyframe declared above, so
  // there is nothing to require() here — and a require() in this file breaks
  // `next dev`, which loads the TypeScript config as an ES module.
  plugins: [],
};

export default config;
