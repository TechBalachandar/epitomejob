"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Bell,
  BookOpen,
  Briefcase,
  CalendarCheck,
  CreditCard,
  FileText,
  Inbox,
  LayoutDashboard,
  LogOut,
  Menu,
  MessagesSquare,
  Sparkles,
  User,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { signOut, useUnreadCount } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "./ui/button";

const NAV = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/profile", label: "Profile", icon: User },
  { href: "/resumes", label: "Resumes", icon: FileText },
  { href: "/jobs", label: "Jobs", icon: Briefcase },
  { href: "/matches", label: "Matches", icon: Sparkles },
  { href: "/coach", label: "Coach", icon: MessagesSquare },
  { href: "/approvals", label: "Approvals", icon: Inbox },
  { href: "/interviews", label: "Interviews", icon: CalendarCheck },
  { href: "/tracker", label: "Tracker", icon: Bell },
  { href: "/plans", label: "Plans", icon: CreditCard },
  { href: "/learning", label: "Learning", icon: BookOpen },
];

const PUBLIC_PATHS = ["/login", "/register"];

/** M13 header bell: links to /notifications, badge from useUnreadCount
 * (30s poll). Badge caps at 9+ and hides at 0; aria-label carries the
 * count. Rendered only in the authenticated header, never on public
 * paths. No NAV entry — the Tracker entry already uses the Bell icon. */
function NotificationBell() {
  const { data } = useUnreadCount();
  const count = data?.unreadCount ?? 0;
  return (
    <Link
      href="/notifications"
      aria-label={count > 0 ? `Notifications, ${count} unread` : "Notifications"}
      className="relative flex h-9 w-9 items-center justify-center rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
    >
      <Bell className="h-5 w-5" aria-hidden />
      {count > 0 && (
        <span
          aria-hidden
          className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold leading-none text-white"
        >
          {count > 9 ? "9+" : count}
        </span>
      )}
    </Link>
  );
}

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav className="flex flex-col gap-1 px-3 py-4" aria-label="Main">
      {NAV.map(({ href, label, icon: Icon }) => {
        const active =
          href === "/" ? pathname === "/" : pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            onClick={onNavigate}
            className={cn(
              "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
              active
                ? "bg-indigo-50 text-indigo-700"
                : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
            )}
            aria-current={active ? "page" : undefined}
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, email } = useAuthStore();
  const [mobileOpen, setMobileOpen] = useState(false);
  const isPublic = PUBLIC_PATHS.includes(pathname);

  // W1 auth guard: every page except /login and /register needs a session.
  // The access token lives in memory only, so a full page reload still
  // returns here (the refresh cookie does not restore the session yet).
  useEffect(() => {
    if (!isPublic && !isAuthenticated) router.replace("/login");
  }, [isPublic, isAuthenticated, router]);

  if (isPublic) {
    return (
      <div className="flex min-h-screen flex-col bg-slate-50">
        <header className="flex h-14 items-center border-b border-slate-200 bg-white px-4">
          <Link href="/" className="flex items-center gap-2 font-semibold text-slate-900">
            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-600 text-sm font-bold text-white">
              E
            </span>
            EpitomeJob
          </Link>
        </header>
        <main className="flex flex-1 items-center justify-center p-4">
          {children}
        </main>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-slate-200 bg-white md:flex">
        <div className="flex h-14 items-center gap-2 border-b border-slate-200 px-4">
          <span className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-600 text-sm font-bold text-white">
            E
          </span>
          <span className="font-semibold text-slate-900">EpitomeJob</span>
        </div>
        <div className="flex-1 overflow-y-auto">
          <NavLinks />
        </div>
        <div className="border-t border-slate-200 p-3">
          <p className="truncate px-2 text-xs text-slate-500">{email}</p>
          <Button
            variant="ghost"
            size="sm"
            className="mt-1 w-full justify-start"
            onClick={() => {
              void signOut().finally(() => router.replace("/login"));
            }}
          >
            <LogOut className="h-4 w-4" aria-hidden /> Sign out
          </Button>
        </div>
      </aside>

      {/* Mobile sidebar */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <div
            className="absolute inset-0 bg-slate-900/50"
            onClick={() => setMobileOpen(false)}
            aria-hidden
          />
          <aside className="absolute inset-y-0 left-0 flex w-64 flex-col bg-white shadow-xl">
            <div className="flex h-14 items-center justify-between border-b border-slate-200 px-4">
              <span className="font-semibold text-slate-900">EpitomeJob</span>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setMobileOpen(false)}
                aria-label="Close menu"
              >
                <X className="h-5 w-5" aria-hidden />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto">
              <NavLinks onNavigate={() => setMobileOpen(false)} />
            </div>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col md:pl-60">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-slate-200 bg-white px-4">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setMobileOpen(true)}
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" aria-hidden />
          </Button>
          <span className="text-sm font-medium text-slate-700">
            AI Career Assistance
          </span>
          <span className="ml-auto flex items-center gap-1">
            <NotificationBell />
            <span className="hidden rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-medium text-indigo-700 sm:inline">
              W1 · Live backend
            </span>
          </span>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 sm:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
