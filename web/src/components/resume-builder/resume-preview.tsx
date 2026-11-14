// Resume Builder — live preview. Re-renders from the same template HTML
// on every keystroke (the parent passes the new state, srcDoc updates).
// Sandboxed iframe, no scripts: the template HTML is rendered, never run.
// [placeholder] text is highlighted amber by renderTemplateHtml.
import { hasPlaceholders, renderTemplateHtml } from "@/lib/resume-builder";
import type { BuilderTemplate, ResumeBuilderState } from "@/lib/types";

export function ResumePreview({
  template,
  state,
}: {
  template: BuilderTemplate;
  state: ResumeBuilderState;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <iframe
        data-testid="resume-preview"
        title="Resume preview"
        sandbox=""
        srcDoc={renderTemplateHtml(template, state)}
        className="h-[700px] w-full rounded-md border border-slate-200 bg-white"
      />
      {hasPlaceholders(state) && (
        <p className="text-xs text-amber-700">
          Highlighted [placeholders] still need a real value — fill them in
          before you download or save.
        </p>
      )}
    </div>
  );
}
