// Resume Builder — live preview. Re-renders from the same template HTML
// on every keystroke (the parent passes the new state, srcDoc updates).
// Sandboxed iframe, no scripts: the template HTML is rendered, never run.
// [placeholder] text is highlighted amber by renderTemplateHtml.
// The preview is a real Letter-size white page on a grey canvas; a
// Designer design (font / line-height / accent / alignment / margins)
// is applied as one override <style> so every control reflects instantly.
import {
  hasPlaceholders,
  renderTemplateHtml,
} from "@/lib/resume-builder";
import type { DesignSettings } from "@/lib/resume-builder";
import type {
  BuilderSection,
  BuilderTemplate,
  ResumeBuilderState,
} from "@/lib/types";

export function ResumePreview({
  template,
  state,
  design,
  hideSections,
}: {
  template: BuilderTemplate;
  state: ResumeBuilderState;
  design?: DesignSettings;
  hideSections?: BuilderSection[];
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex justify-center rounded-2xl bg-slate-200/80 px-3 py-6 sm:px-6">
        <div className="w-full max-w-[816px] overflow-hidden rounded-md bg-white shadow-xl ring-1 ring-slate-900/10">
          <iframe
            data-testid="resume-preview"
            title="Resume preview"
            sandbox=""
            srcDoc={renderTemplateHtml(template, state, {
              design,
              hideSections,
            })}
            className="block aspect-[8.5/11] w-full border-0 bg-white"
          />
        </div>
      </div>
      {hasPlaceholders(state) && (
        <p className="text-xs text-amber-700">
          Highlighted [placeholders] still need a real value — fill them in
          before you download or save.
        </p>
      )}
    </div>
  );
}
