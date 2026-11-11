"use client";

import { useState } from "react";
import { Ban, CheckCircle2, Gavel, Search } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { toast } from "@/lib/hooks/use-toast";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import type { CaseResolution, FraudCase } from "@/types";

const RESOLUTIONS: {
  value: CaseResolution;
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  accent: string;
}[] = [
  {
    value: "FRAUD",
    label: "Confirmed fraud",
    description: "The activity was fraudulent. Feeds the pattern learning loop.",
    icon: Ban,
    accent: "text-critical",
  },
  {
    value: "FALSE_POSITIVE",
    label: "False positive",
    description: "Legitimate activity. Helps tune the rules away from this signature.",
    icon: CheckCircle2,
    accent: "text-good",
  },
  {
    value: "MANUAL_REVIEW",
    label: "Manual review",
    description: "Inconclusive. Keep the record and revisit with more evidence.",
    icon: Search,
    accent: "text-warning",
  },
];

/**
 * Resolution is a deliberate, explicit act — the analyst picks the outcome and
 * it is written to the audit history. The AI never reaches this step.
 */
export function ResolveCaseDialog({
  caseId,
  disabled,
  onResolved,
}: {
  caseId: string;
  disabled?: boolean;
  onResolved: (updated: FraudCase) => void | Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<CaseResolution>("FRAUD");
  const [submitting, setSubmitting] = useState(false);

  const resolve = async () => {
    setSubmitting(true);
    try {
      const updated = await apiClient.updateCaseStatus(caseId, "RESOLVED", selected);
      await onResolved(updated);
      setOpen(false);
      toast.success("Case resolved", "The outcome is recorded in the audit history.");
    } catch (error) {
      toast.fromError(error, "Could not resolve the case");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" disabled={disabled}>
          <Gavel />
          Resolve
        </Button>
      </DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>Resolve this case</DialogTitle>
          <DialogDescription>
            Your choice is written to the audit history with your name and the time.
            It cannot be edited afterwards.
          </DialogDescription>
        </DialogHeader>

        <fieldset className="space-y-2">
          <legend className="sr-only">Resolution</legend>
          {RESOLUTIONS.map((option) => {
            const active = selected === option.value;
            const Icon = option.icon;
            return (
              <label
                key={option.value}
                className={cn(
                  "flex cursor-pointer items-start gap-3 rounded-md border p-3 transition-colors",
                  active
                    ? "border-primary bg-primary-wash"
                    : "border-line hover:border-line-strong hover:bg-surface-sunken"
                )}
              >
                <input
                  type="radio"
                  name="resolution"
                  value={option.value}
                  checked={active}
                  onChange={() => setSelected(option.value)}
                  className="sr-only"
                />
                <Icon className={cn("mt-0.5 size-4 shrink-0", option.accent)} />
                <span className="space-y-0.5">
                  <span className="block text-sm font-medium text-ink">{option.label}</span>
                  <span className="block text-xs leading-relaxed text-ink-secondary">
                    {option.description}
                  </span>
                </span>
              </label>
            );
          })}
        </fieldset>

        <DialogFooter>
          <DialogClose asChild>
            <Button variant="secondary" size="sm">
              Cancel
            </Button>
          </DialogClose>
          <Button size="sm" onClick={resolve} loading={submitting}>
            Resolve case
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
