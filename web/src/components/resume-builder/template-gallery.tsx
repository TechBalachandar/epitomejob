// Resume Builder — template gallery. Every card renders a LIVE thumbnail
// from the same template HTML the editor preview uses (renderTemplateHtml
// with the sample or the parsed document state) — no server-side images.
// The iframe is a scaled-down A4 page (794×1123 at 0.42), pointer-events
// off so the card itself stays the click target.
import { renderTemplateHtml } from "@/lib/resume-builder";
import type { BuilderTemplate, ResumeBuilderState } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export function TemplateGallery({
  templates,
  state,
  selectedId,
  onSelect,
}: {
  templates: BuilderTemplate[];
  state: ResumeBuilderState;
  selectedId: string | null;
  onSelect: (template: BuilderTemplate) => void;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {templates.map((template) => {
        const selected = template.id === selectedId;
        return (
          <Card
            key={template.id}
            className={cn(
              "overflow-hidden",
              selected && "ring-2 ring-indigo-600",
            )}
          >
            <div
              className="relative h-[300px] cursor-pointer overflow-hidden bg-white"
              onClick={() => onSelect(template)}
              aria-hidden
            >
              <iframe
                title={`${template.name} template preview`}
                tabIndex={-1}
                srcDoc={renderTemplateHtml(template, state)}
                className="pointer-events-none absolute border-0 bg-white"
                style={{
                  width: 794,
                  height: 1123,
                  transform: "scale(0.42)",
                  transformOrigin: "top left",
                  left: "50%",
                  marginLeft: -167,
                }}
              />
            </div>
            <CardHeader>
              <div className="flex items-center gap-2">
                <CardTitle>{template.name}</CardTitle>
                {selected && <Badge variant="success">Selected</Badge>}
              </div>
              {template.description && (
                <CardDescription>{template.description}</CardDescription>
              )}
            </CardHeader>
            <CardContent>
              <Button
                className="w-full"
                variant={selected ? "default" : "outline"}
                onClick={() => onSelect(template)}
              >
                {selected ? "Selected — continue editing" : `Use ${template.name}`}
              </Button>
            </CardContent>
          </Card>
        );
      })}
    </div>
  );
}
