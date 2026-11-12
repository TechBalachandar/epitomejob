// Scripted mock Coach replies for W0. Keyword-based only; the real
// LangGraph Supervisor + specialists land in W3.
export function coachReply(message: string): string {
  const text = message.toLowerCase();
  if (/(hello|hi|hey|good morning|good afternoon)\b/.test(text)) {
    return "Hello! I am your EpitomeJob Coach. I can help with your resume, finding matching jobs, or interview prep. What would you like to work on first?";
  }
  if (text.includes("resume")) {
    return "Your resume is in good shape — it parsed cleanly and your Spring Boot depth stands out. Two quick wins: add one quantified result to your Navy Federal bullet (for example, API latency or adoption), and move your MCP/RAG project above the older mobile work for AI roles.";
  }
  if (text.includes("job") || text.includes("match")) {
    return "Your strongest matches right now are the Agentic AI Engineer role at Meridian Care (92%) and the Senior Backend role at Northwind Health (88%). Both sponsor visas. Want me to explain why Meridian scores highest?";
  }
  if (text.includes("interview")) {
    return "For interviews, practice the approval-gate story: how you designed a system that proposes actions but never writes without human approval. It is your best differentiator for AI roles. I can run a 3-question mock interview whenever you are ready.";
  }
  if (text.includes("h-1b") || text.includes("visa") || text.includes("sponsor")) {
    return "For H-1B roles, filter for sponsors first — 6 of your 10 saved jobs list sponsorship. Your 60-day clock widget on the dashboard tracks the deadline; keep applications to sponsored roles until the clock is comfortable.";
  }
  if (text.includes("thank")) {
    return "You are welcome. Keep the momentum — one tailored application a day beats ten generic ones.";
  }
  return "Good question. In this W0 preview I answer from a small scripted set — resume, jobs, interviews, and visa topics work best. The full Coach agent arrives in W3 and will read your real profile, resume, and matches before answering.";
}
