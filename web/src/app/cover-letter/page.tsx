// Cover Letter Generator — page. The editor is self-contained; this
// page only provides the heading and layout.
import { CoverLetterEditor } from "@/components/cover-letter/cover-letter-editor";

export default function CoverLetterPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Cover Letter</h1>
        <p className="mt-1 text-sm text-slate-500">
          Generate a tailored cover letter from one of your resumes and a
          job description, edit it, and save or export it.
        </p>
      </div>
      <CoverLetterEditor />
    </div>
  );
}
