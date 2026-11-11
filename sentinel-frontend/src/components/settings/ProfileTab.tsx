"use client";

import { useRef, useState } from "react";
import { Camera, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { UserAvatar } from "@/components/security/UserAvatar";
import { apiClient } from "@/lib/api-client";
import { dateTime, humanise } from "@/lib/format";
import { toast } from "@/lib/hooks/use-toast";
import { useAuth } from "@/lib/hooks/useAuth";

const MAX_BYTES = 2 * 1024 * 1024;
const ACCEPTED = ["image/png", "image/jpeg", "image/webp", "image/gif"];

/** Name, title, timezone, and picture. The parts of an account that are not a control. */
export function ProfileTab() {
  const { user, refresh } = useAuth();
  const fileInput = useRef<HTMLInputElement | null>(null);

  const [firstName, setFirstName] = useState(user?.firstName ?? "");
  const [lastName, setLastName] = useState(user?.lastName ?? "");
  const [jobTitle, setJobTitle] = useState(user?.jobTitle ?? "");
  const [timezone, setTimezone] = useState(user?.timezone ?? "");
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [avatarStamp, setAvatarStamp] = useState<string | null>(user?.avatarUpdatedAt ?? null);

  const dirty =
    firstName !== (user?.firstName ?? "") ||
    lastName !== (user?.lastName ?? "") ||
    jobTitle !== (user?.jobTitle ?? "") ||
    timezone !== (user?.timezone ?? "");

  const save = async () => {
    setSaving(true);
    try {
      await apiClient.updateProfile({ firstName, lastName, jobTitle, timezone });
      await refresh();
      toast.success("Profile saved");
    } catch (error) {
      toast.fromError(error, "Could not save your profile");
    } finally {
      setSaving(false);
    }
  };

  const upload = async (file: File) => {
    // Checked here so an obviously wrong file is refused instantly; the
    // backend re-checks and is the one that decides.
    if (!ACCEPTED.includes(file.type)) {
      toast.fromError(new Error("Use a PNG, JPEG, WebP, or GIF image"), "Unsupported image");
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.fromError(new Error("Images must be smaller than 2 MB"), "Image too large");
      return;
    }

    setUploading(true);
    try {
      const result = await apiClient.uploadAvatar(file);
      setAvatarStamp(result.updatedAt);
      await refresh();
      toast.success("Picture updated");
    } catch (error) {
      toast.fromError(error, "Could not upload that image");
    } finally {
      setUploading(false);
      if (fileInput.current) fileInput.current.value = "";
    }
  };

  const remove = async () => {
    setUploading(true);
    try {
      await apiClient.removeAvatar();
      setAvatarStamp(null);
      await refresh();
      toast.success("Picture removed");
    } catch (error) {
      toast.fromError(error, "Could not remove the picture");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Your details</CardTitle>
          <CardDescription>How your name appears on cases you touch</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4">
            <UserAvatar user={user} size={64} cacheKey={avatarStamp} />
            <div className="space-y-2">
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  loading={uploading}
                  onClick={() => fileInput.current?.click()}
                >
                  <Camera />
                  {avatarStamp ? "Replace picture" : "Upload picture"}
                </Button>
                {avatarStamp ? (
                  <Button size="sm" variant="ghost" onClick={remove} disabled={uploading}>
                    <Trash2 />
                    Remove
                  </Button>
                ) : null}
              </div>
              <p className="text-[11px] text-ink-muted">
                PNG, JPEG, WebP, or GIF, up to 2 MB. Cropped to a square and stored as PNG.
              </p>
            </div>
            <input
              ref={fileInput}
              type="file"
              accept={ACCEPTED.join(",")}
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void upload(file);
              }}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="first-name">First name</Label>
              <Input
                id="first-name"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="last-name">Last name</Label>
              <Input
                id="last-name"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="job-title">Job title</Label>
              <Input
                id="job-title"
                value={jobTitle}
                placeholder="Fraud analyst"
                onChange={(event) => setJobTitle(event.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="timezone">Timezone</Label>
              <Input
                id="timezone"
                value={timezone}
                placeholder="Europe/London"
                onChange={(event) => setTimezone(event.target.value)}
              />
            </div>
          </div>

          <Button size="sm" onClick={save} loading={saving} disabled={!dirty}>
            Save changes
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-line pb-4">
          <CardTitle>Account</CardTitle>
          <CardDescription>Set by your administrator</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-4">
            <Field label="Email">{user?.email ?? "—"}</Field>
            <Field label="Role">
              {user ? <Badge tone="info">{humanise(user.role)}</Badge> : "—"}
            </Field>
            <Field label="Status">
              {user ? (
                <Badge tone={user.enabled ? "good" : "critical"}>
                  {user.enabled ? "Active" : "Disabled"}
                </Badge>
              ) : (
                "—"
              )}
            </Field>
            <Field label="Email verified">
              {user ? (
                <Badge tone={user.emailVerified ? "good" : "warning"}>
                  {user.emailVerified ? "Verified" : "Not verified"}
                </Badge>
              ) : (
                "—"
              )}
            </Field>
            <Field label="Last sign-in">{dateTime(user?.lastLogin)}</Field>
            <Field label="Account created">{dateTime(user?.createdAt)}</Field>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className="mt-1 truncate text-sm text-ink-secondary">{children}</dd>
    </div>
  );
}
