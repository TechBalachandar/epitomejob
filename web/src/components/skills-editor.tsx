// Skills tag editor (W5 contract §10): add/remove chips with the same
// trim/dedupe/cap rules as the backend — max 30 skills, max 60 chars each.
"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export const MAX_SKILLS = 30;
export const MAX_SKILL_LENGTH = 60;

/** Trim, drop blanks, dedupe (exact match after trim), cap at 30. */
export function normalizeSkills(skills: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of skills) {
    const skill = raw.trim();
    if (!skill || seen.has(skill)) continue;
    seen.add(skill);
    out.push(skill.slice(0, MAX_SKILL_LENGTH));
    if (out.length >= MAX_SKILLS) break;
  }
  return out;
}

export function SkillsEditor({
  value,
  onChange,
}: {
  value: string[];
  onChange: (skills: string[]) => void;
}) {
  const [draft, setDraft] = useState("");
  const skills = normalizeSkills(value);
  const atCap = skills.length >= MAX_SKILLS;

  function addSkill(raw: string) {
    const skill = raw.trim().slice(0, MAX_SKILL_LENGTH);
    if (!skill || skills.includes(skill) || atCap) return;
    onChange([...skills, skill]);
    setDraft("");
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {skills.map((skill) => (
          <Badge key={skill} variant="secondary" className="gap-1 pr-1">
            {skill}
            <button
              type="button"
              aria-label={`Remove ${skill}`}
              onClick={() =>
                onChange(skills.filter((s) => s !== skill))
              }
              className="rounded-full px-1 text-slate-500 hover:text-slate-900"
            >
              ×
            </button>
          </Badge>
        ))}
        {skills.length === 0 && (
          <span className="text-sm text-slate-400">
            No skills yet — add the ones you want matched on.
          </span>
        )}
      </div>
      <div className="flex gap-2">
        <Input
          value={draft}
          maxLength={MAX_SKILL_LENGTH}
          disabled={atCap}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addSkill(draft);
            }
          }}
          placeholder={atCap ? "Maximum 30 skills" : "Add a skill, e.g. LangGraph"}
          aria-label="Add a skill"
        />
        <Button
          type="button"
          variant="outline"
          disabled={atCap || draft.trim().length === 0}
          onClick={() => addSkill(draft)}
        >
          Add
        </Button>
      </div>
      <p className="text-xs text-slate-500">
        {skills.length}/{MAX_SKILLS} skills · up to {MAX_SKILL_LENGTH}{" "}
        characters each
      </p>
    </div>
  );
}
