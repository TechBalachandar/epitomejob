// W6: the ONE shared resume-selection mechanism — the URL (?resumeId=).
// Dashboard and Matches both use this hook, so picking a resume on either
// page selects it on the other, survives reload, and is shareable. No
// Zustand store: the URL is the store.
"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ResumeItem } from "./types";
import { resolveSelectedResumeId } from "./resume-selection";

export function useSelectedResumeId(
  resumes: ResumeItem[] | undefined,
): { selectedResumeId: string | null; setSelectedResumeId: (id: string) => void } {
  const searchParams = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const param = searchParams.get("resumeId");
  const selectedResumeId = resolveSelectedResumeId(resumes, param);

  function setSelectedResumeId(id: string) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("resumeId", id);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }

  return { selectedResumeId, setSelectedResumeId };
}

/** Link to the Matches page that keeps the current resume selection. */
export function matchesHref(resumeId: string | null): string {
  return resumeId ? `/matches?resumeId=${encodeURIComponent(resumeId)}` : "/matches";
}
