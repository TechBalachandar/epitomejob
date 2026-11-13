// W5 skills editor (contract §10): add/remove chips with the backend's
// trim/dedupe/cap rules — max 30 skills, max 60 chars each.
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  MAX_SKILL_LENGTH,
  MAX_SKILLS,
  SkillsEditor,
  normalizeSkills,
} from "@/components/skills-editor";

describe("normalizeSkills", () => {
  it("trims, drops blanks, dedupes after trim, keeps order", () => {
    expect(
      normalizeSkills([" Java ", "Java", "", "  ", "LangGraph", "Java"]),
    ).toEqual(["Java", "LangGraph"]);
  });

  it("caps at 30 skills", () => {
    const many = Array.from({ length: 35 }, (_, i) => `Skill ${i}`);
    expect(normalizeSkills(many)).toHaveLength(MAX_SKILLS);
    expect(normalizeSkills(many)[29]).toBe("Skill 29");
  });

  it("caps each skill at 60 characters", () => {
    const long = "x".repeat(80);
    expect(normalizeSkills([long])).toEqual(["x".repeat(MAX_SKILL_LENGTH)]);
  });
});

describe("SkillsEditor", () => {
  it("adds a skill on Add click and shows it as a chip", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SkillsEditor value={[]} onChange={onChange} />);
    await user.type(screen.getByLabelText("Add a skill"), "LangGraph");
    await user.click(screen.getByRole("button", { name: "Add" }));
    expect(onChange).toHaveBeenCalledWith(["LangGraph"]);
  });

  it("adds a skill on Enter and trims it", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SkillsEditor value={["Java"]} onChange={onChange} />);
    await user.type(
      screen.getByLabelText("Add a skill"),
      "  Spring Boot  {enter}",
    );
    expect(onChange).toHaveBeenCalledWith(["Java", "Spring Boot"]);
  });

  it("does not add a duplicate or a blank skill", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SkillsEditor value={["Java"]} onChange={onChange} />);
    await user.type(screen.getByLabelText("Add a skill"), "Java");
    await user.click(screen.getByRole("button", { name: "Add" }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it("removes a skill via its chip button", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<SkillsEditor value={["Java", "LangGraph"]} onChange={onChange} />);
    await user.click(screen.getByRole("button", { name: "Remove LangGraph" }));
    expect(onChange).toHaveBeenCalledWith(["Java"]);
  });

  it("disables adding at the 30-skill cap and shows the count", async () => {
    const skills = Array.from({ length: 30 }, (_, i) => `Skill ${i}`);
    render(<SkillsEditor value={skills} onChange={vi.fn()} />);
    expect(screen.getByText("30/30 skills · up to 60 characters each")).toBeVisible();
    expect(screen.getByLabelText("Add a skill")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
  });

  it("shows the empty state when there are no skills", () => {
    render(<SkillsEditor value={[]} onChange={vi.fn()} />);
    expect(screen.getByText(/No skills yet/)).toBeVisible();
    expect(screen.getByText("0/30 skills · up to 60 characters each")).toBeVisible();
  });
});
