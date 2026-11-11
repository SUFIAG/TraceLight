import * as React from "react";
import {
  AlertOctagon,
  AlertTriangle,
  Ban,
  CheckCircle2,
  CircleDot,
  Clock,
  Eye,
  Minus,
  ShieldCheck,
  ShieldQuestion,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { humanise } from "@/lib/format";
import type {
  AlertSeverity,
  AlertStatus,
  CasePriority,
  CaseStatus,
  RiskLevel,
  TransactionStatus,
  TrustLevel,
} from "@/types";

type Tone = "neutral" | "good" | "warning" | "serious" | "critical" | "info";

const toneClasses: Record<Tone, string> = {
  neutral: "bg-surface-sunken text-ink-secondary border-line",
  good: "bg-good-wash text-ink border-good/30",
  warning: "bg-warning-wash text-ink border-warning/40",
  serious: "bg-serious-wash text-ink border-serious/40",
  critical: "bg-critical-wash text-ink border-critical/40",
  info: "bg-primary-wash text-ink border-primary/30",
};

const dotClasses: Record<Tone, string> = {
  neutral: "text-ink-muted",
  good: "text-good",
  warning: "text-warning",
  serious: "text-serious",
  critical: "text-critical",
  info: "text-primary",
};

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
  icon?: React.ComponentType<{ className?: string }>;
}

/**
 * Status is never carried by colour alone: every badge ships an icon and a
 * readable label, so it survives colour-blind vision, greyscale print and
 * forced-colors mode.
 */
export function Badge({ tone = "neutral", icon: Icon, className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
        toneClasses[tone],
        className
      )}
      {...props}
    >
      {Icon ? <Icon className={cn("size-3.5 shrink-0", dotClasses[tone])} /> : null}
      {children}
    </span>
  );
}

/* ---------- Domain badges ---------- */

const severityMap: Record<AlertSeverity, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  MEDIUM: { tone: "warning", icon: AlertTriangle },
  HIGH: { tone: "serious", icon: AlertOctagon },
  CRITICAL: { tone: "critical", icon: AlertOctagon },
};

export function SeverityBadge({ severity }: { severity: AlertSeverity }) {
  const { tone, icon } = severityMap[severity] ?? severityMap.MEDIUM;
  return <Badge tone={tone} icon={icon}>{humanise(severity)}</Badge>;
}

const alertStatusMap: Record<AlertStatus, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  OPEN: { tone: "info", icon: CircleDot },
  INVESTIGATING: { tone: "warning", icon: Eye },
  RESOLVED: { tone: "good", icon: CheckCircle2 },
  FALSE_POSITIVE: { tone: "neutral", icon: Minus },
};

export function AlertStatusBadge({ status }: { status: AlertStatus }) {
  const { tone, icon } = alertStatusMap[status] ?? alertStatusMap.OPEN;
  return <Badge tone={tone} icon={icon}>{humanise(status)}</Badge>;
}

const caseStatusMap: Record<CaseStatus, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  OPEN: { tone: "info", icon: CircleDot },
  INVESTIGATING: { tone: "warning", icon: Eye },
  RESOLVED: { tone: "good", icon: CheckCircle2 },
  CLOSED: { tone: "neutral", icon: Minus },
};

export function CaseStatusBadge({ status }: { status: CaseStatus }) {
  const { tone, icon } = caseStatusMap[status] ?? caseStatusMap.OPEN;
  return <Badge tone={tone} icon={icon}>{humanise(status)}</Badge>;
}

const priorityMap: Record<CasePriority, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  LOW: { tone: "neutral", icon: Minus },
  MEDIUM: { tone: "warning", icon: AlertTriangle },
  HIGH: { tone: "serious", icon: AlertOctagon },
  CRITICAL: { tone: "critical", icon: AlertOctagon },
};

export function PriorityBadge({ priority }: { priority: CasePriority }) {
  const { tone, icon } = priorityMap[priority] ?? priorityMap.MEDIUM;
  return <Badge tone={tone} icon={icon}>{humanise(priority)}</Badge>;
}

const transactionStatusMap: Record<
  TransactionStatus,
  { tone: Tone; icon: React.ComponentType<{ className?: string }> }
> = {
  PENDING: { tone: "neutral", icon: Clock },
  FLAGGED: { tone: "serious", icon: AlertTriangle },
  APPROVED: { tone: "good", icon: CheckCircle2 },
  REJECTED: { tone: "critical", icon: Ban },
};

export function TransactionStatusBadge({ status }: { status: TransactionStatus }) {
  const { tone, icon } = transactionStatusMap[status] ?? transactionStatusMap.PENDING;
  return <Badge tone={tone} icon={icon}>{humanise(status)}</Badge>;
}

const riskLevelMap: Record<RiskLevel, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  LOW: { tone: "good", icon: ShieldCheck },
  MEDIUM: { tone: "warning", icon: AlertTriangle },
  HIGH: { tone: "serious", icon: AlertOctagon },
  CRITICAL: { tone: "critical", icon: AlertOctagon },
};

export function RiskLevelBadge({ level }: { level: RiskLevel }) {
  const { tone, icon } = riskLevelMap[level] ?? riskLevelMap.LOW;
  return <Badge tone={tone} icon={icon}>{humanise(level)} risk</Badge>;
}

const trustMap: Record<TrustLevel, { tone: Tone; icon: React.ComponentType<{ className?: string }> }> = {
  TRUSTED: { tone: "good", icon: ShieldCheck },
  UNKNOWN: { tone: "neutral", icon: ShieldQuestion },
  SUSPICIOUS: { tone: "serious", icon: AlertTriangle },
  BLACKLISTED: { tone: "critical", icon: Ban },
};

export function TrustBadge({ level }: { level: TrustLevel }) {
  const { tone, icon } = trustMap[level] ?? trustMap.UNKNOWN;
  return <Badge tone={tone} icon={icon}>{humanise(level)}</Badge>;
}
