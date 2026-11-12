import { describe, expect, it } from "vitest";
import { coachReply } from "@/lib/coach";

describe("coachReply (W0 scripted Coach)", () => {
  it("greets on hello", () => {
    expect(coachReply("Hello there")).toMatch(/Hello! I am your EpitomeJob Coach/);
  });

  it("answers resume questions with concrete advice", () => {
    expect(coachReply("How can I improve my resume?")).toMatch(/resume/i);
  });

  it("answers job/match questions with the top matches", () => {
    expect(coachReply("Which jobs match me best?")).toMatch(/Meridian Care/);
  });

  it("answers interview questions with prep advice", () => {
    expect(coachReply("How do I prepare for interviews?")).toMatch(/approval-gate/i);
  });

  it("answers visa questions with the sponsorship filter", () => {
    expect(coachReply("Which jobs sponsor H-1B visas?")).toMatch(/sponsor/i);
  });

  it("falls back to a generic answer for unknown topics", () => {
    expect(coachReply("What is the weather like?")).toMatch(/W0 preview/);
  });
});
