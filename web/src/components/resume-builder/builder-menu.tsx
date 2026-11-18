// Resume Builder — Menu dropdown (top-right). Only actions that map to
// something real: Import resume (upload + parse), Duplicate this resume
// (create a copy via the create endpoint), Quick Tour (re-open the tour),
// Back to All Resumes (the home list). There is deliberately no Delete
// entry — the backend has no DELETE /resume-builder/documents endpoint,
// and a dead menu item would be a fake. Same for LinkedIn import, which
// does not exist.
"use client";

import { useEffect, useRef, useState } from "react";
import {
  Copy,
  Menu as MenuIcon,
  Sparkles,
  UploadCloud,
  Undo2,
} from "lucide-react";
import { Button } from "@/components/ui/button";

export function BuilderMenu({
  duplicateDisabled,
  onImport,
  onDuplicate,
  onQuickTour,
  onBackToResumes,
}: {
  duplicateDisabled: boolean;
  onImport: () => void;
  onDuplicate: () => void;
  onQuickTour: () => void;
  onBackToResumes: () => void;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open ]);

  const items: {
    id: string;
    label: string;
    icon: React.ReactNode;
    disabled?: boolean;
    onClick: () => void;
  }[] = [
    {
      id: "import",
      label: "Import resume",
      icon: <UploadCloud className="h-4 w-4" aria-hidden />,
      onClick: onImport,
    },
    {
      id: "duplicate",
      label: "Duplicate this resume",
      icon: <Copy className="h-4 w-4" aria-hidden />,
      disabled: duplicateDisabled,
      onClick: onDuplicate,
    },
    {
      id: "tour",
      label: "Quick Tour",
      icon: <Sparkles className="h-4 w-4" aria-hidden />,
      onClick: onQuickTour,
    },
    {
      id: "back",
      label: "Back to All Resumes",
      icon: <Undo2 className="h-4 w-4" aria-hidden />,
      onClick: onBackToResumes,
    },
  ];

  return (
    <div ref={menuRef} className="relative">
      <Button
        variant="outline"
        size="sm"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <MenuIcon className="h-4 w-4" aria-hidden />
        Menu
      </Button>
      {open && (
        <>
          <button
            type="button"
            aria-label="Close menu"
            className="fixed inset-0 z-30 cursor-default"
            onClick={() => setOpen(false)}
          />
          <div
            role="menu"
            className="absolute right-0 z-40 mt-2 w-60 rounded-xl border border-slate-200 bg-white py-1 shadow-lg"
          >
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                role="menuitem"
                disabled={item.disabled}
                onClick={() => {
                  setOpen(false);
                  item.onClick();
                }}
                className="flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {item.icon}
                {item.label}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
