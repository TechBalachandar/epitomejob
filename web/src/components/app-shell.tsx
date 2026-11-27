"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  BadgeCheck,
  Bell,
  BookOpen,
  Briefcase,
  CalendarCheck,
  Clock,
  CreditCard,
  FileText,
  Inbox,
  LayoutDashboard,
  LogOut,
  Mail,
  Menu,
  MessagesSquare,
  PenLine,
  Search,
  Sparkles,
  User,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { restoreSession, signOut, useProfile, useUnreadCount } from "@/lib/api";
import { useAuthStore } from "@/lib/auth-store";
import { clockStatus, formatDeadline } from "@/lib/h1b-clock";
import { Button } from "./ui/button";

const NAV_GROUPS = [
  {
    label: "Your search",
    items: [
      { href: "/home", label: "Home", icon: LayoutDashboard },
      { href: "/clock", label: "Clock", icon: Clock },
      { href: "/profile", label: "Profile", icon: User },
    ],
  },
  {
    label: "Resumes",
    items: [
      { href: "/resumes", label: "Resumes", icon: FileText },
      { href: "/resume-builder", label: "Resume Builder", icon: PenLine },
      { href: "/cover-letter", label: "Cover Letter", icon: Mail },
    ],
  },
  {
    label: "Jobs",
    items: [
      { href: "/jobs", label: "Jobs", icon: Briefcase },
      { href: "/matches", label: "Matches", icon: Sparkles },
      { href: "/coach", label: "Coach", icon: MessagesSquare },
      { href: "/approvals", label: "Approvals", icon: Inbox },
      { href: "/interviews", label: "Interviews", icon: CalendarCheck },
      { href: "/offers", label: "Offers", icon: BadgeCheck },
      { href: "/tracker", label: "Tracker", icon: Bell },
    ],
  },
  {
    label: "Account",
    items: [
      { href: "/plans", label: "Plans", icon: CreditCard },
      { href: "/learning", label: "Learning", icon: BookOpen },
    ],
  },
];

// The landing page (`/`) is public: anyone can read it without logging
// in. `/login` and `/register` are the auth forms. Everything else needs
// a session.
const PUBLIC_PATHS = ["/", "/login", "/register"];

function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <Link href="/" className="flex items-center gap-2.5">
      <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-base font-bold text-white shadow-sm">
        E
      </span>
      {!compact && (
        <span className="text-lg font-bold tracking-tight text-slate-900">
          EpitomeJob
        </span>
      )}
    </Link>
  );
}

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
      className="relative flex h-10 w-10 items-center justify-center rounded-lg text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
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

/** Header job search: typing a query and pressing Enter opens the Jobs
 * page with the query applied (the Jobs page reads `?query=`). */
function HeaderSearch() {
  const router = useRouter();
  const [value, setValue] = useState("");
  return (
    <form
      role="search"
      className="relative hidden min-w-0 flex-1 sm:block sm:max-w-md"
      onSubmit={(e) => {
        e.preventDefault();
        const q = value.trim();
        router.push(q ? `/jobs?query=${encodeURIComponent(q)}` : "/jobs");
      }}
    >
      <Search
        className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
        aria-hidden
      />
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search jobs by title, company, or skill…"
        aria-label="Search jobs"
        className="h-10 w-full rounded-lg border border-slate-300 bg-slate-50 pl-9 pr-3 text-sm shadow-sm placeholder:text-slate-400 focus-visible:outline-none focus-visible:border-brand-600 focus-visible:bg-white focus-visible:ring-2 focus-visible:ring-brand-600/25"
      />
    </form>
  );
}

/** Small 60-day clock widget at the top of the authenticated sidebar.
 * Shows days left with a progress bar (amber when urgent, rose when
 * expired); when the clock is not set it links to the profile to set it.
 * Renders nothing while the profile is still loading or unavailable. */
function SidebarClock() {
  const { data: profile, isLoading } = useProfile();
  if (isLoading) {
    return (
      <div className="mx-3 mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3">
        <p className="text-xs text-slate-400">Loading your clock…</p>
      </div>
    );
  }
  const clock = clockStatus(profile?.h1bClockStartDate ?? null);
  if (clock.state === "not-set" || clock.daysLeft === null || !clock.deadline) {
    return (
      <Link
        href="/profile"
        className="mx-3 mt-4 block rounded-xl border border-dashed border-slate-300 bg-slate-50 p-3 transition-colors hover:border-brand-600 hover:bg-brand-50"
      >
        <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
          <Clock className="h-4 w-4 text-brand-600" aria-hidden />
          Set your 60-day clock
        </p>
        <p className="mt-1 text-xs text-slate-500">
          Add your H-1B clock start date to start the countdown.
        </p>
      </Link>
    );
  }
  const daysUsed = 60 - clock.daysLeft;
  const urgent = clock.state === "urgent";
  const expired = clock.state === "expired";
  return (
    <Link
      href="/clock"
      className={cn(
        "mx-3 mt-4 block rounded-xl border p-3 transition-shadow hover:shadow-lift",
        expired
          ? "border-rose-200 bg-rose-50"
          : urgent
            ? "border-clock-100 bg-clock-50"
            : "border-brand-100 bg-brand-50",
      )}
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
        Your 60-day clock
      </p>
      <p className="mt-1 text-2xl font-bold tabular-nums tracking-tight text-slate-900">
        {clock.daysLeft}{" "}
        <span className="text-sm font-medium text-slate-500">
          {expired ? "days past deadline" : "days left"}
        </span>
      </p>
      <div
        className="mt-2 h-2 w-full overflow-hidden rounded-full bg-white"
        role="progressbar"
        aria-valuenow={daysUsed}
        aria-valuemin={0}
        aria-valuemax={60}
        aria-label="Days of the 60-day grace period used"
      >
        <span
          className={cn(
            "block h-full rounded-full",
            expired ? "bg-rose-500" : urgent ? "bg-clock-500" : "bg-brand-600",
          )}
          style={{ width: `${Math.min((daysUsed / 60) * 100, 100)}%` }}
        />
      </div>
      <p className="mt-1.5 text-xs text-slate-500">
        Deadline {formatDeadline(clock.deadline)}
      </p>
    </Link>
  );
}

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  return (
    <nav className="flex flex-col gap-5 px-3 py-4" aria-label="Main">
      {NAV_GROUPS.map((group) => (
        <div key={group.label} className="flex flex-col gap-1">
          <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            {group.label}
          </p>
          {group.items.map(({ href, label, icon: Icon }) => {
            const active = pathname.startsWith(href);
            return (
              <Link
                key={href}
                href={href}
                onClick={onNavigate}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  active
                    ? "bg-brand-50 font-semibold text-brand-700 shadow-[inset_3px_0_0_var(--color-brand-600)]"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                )}
                aria-current={active ? "page" : undefined}
              >
                <Icon className="h-4 w-4 shrink-0" aria-hidden />
                {label}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, email } = useAuthStore();
  const [mobileOpen, setMobileOpen] = useState(false);
  const isPublic = PUBLIC_PATHS.includes(pathname);
  // Session restore on load: the access token lives in memory only, so a
  // full page reload starts unauthenticated. Before redirecting to /login,
  // try to restore the session via the httpOnly refresh cookie. The
  // redirect below waits until that check has completed, so a reload with
  // a live refresh cookie keeps the user on the same page instead of
  // bouncing to /login. A deliberate logout leaves restoreChecked true
  // (it was set on the first load), so logout still redirects at once.
  const [restoreChecked, setRestoreChecked] = useState(
    isPublic || isAuthenticated,
  );
  useEffect(() => {
    if (isPublic || isAuthenticated) return;
    let alive = true;
    void restoreSession().finally(() => {
      if (alive) setRestoreChecked(true);
    });
    return () => {
      alive = false;
    };
  }, [isPublic, isAuthenticated]);

  // W1 auth guard: every page except /login and /register needs a session.
  // It waits for the restore check above, so a reload does not redirect
  // before the refresh cookie has had its chance.
  useEffect(() => {
    if (!isPublic && !isAuthenticated && restoreChecked) router.replace("/login");
  }, [isPublic, isAuthenticated, restoreChecked, router]);

  // While the restore check is pending, hold a loading state instead of
  // flashing protected content (or the login page) for a frame.
  if (!isPublic && !isAuthenticated && !restoreChecked) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface">
        <p className="text-sm text-slate-500">Restoring your session…</p>
      </div>
    );
  }

  if (isPublic) {
    // The landing page is a full-width marketing page with its own
    // public header (Features / How it works / Pricing + Sign in /
    // Get started). The auth forms keep the centered card layout.
    if (pathname === "/") {
      return (
        <div className="flex min-h-screen flex-col bg-white">
          <header className="sticky top-0 z-20 flex h-16 items-center gap-4 border-b border-slate-200/80 bg-white/95 px-4 shadow-sm backdrop-blur sm:px-6">
            <Logo />
            <nav className="hidden items-center gap-6 text-sm font-medium text-slate-600 md:flex" aria-label="Product">
              <Link href="/#how-it-works" className="transition-colors hover:text-brand-700">How it works</Link>
              <Link href="/#features" className="transition-colors hover:text-brand-700">Features</Link>
              <Link href="/#pricing" className="transition-colors hover:text-brand-700">Pricing</Link>
            </nav>
            <span className="ml-auto flex items-center gap-2">
              <Link
                href="/login"
                className="rounded-lg px-3 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-100"
              >
                Sign in
              </Link>
              <Link
                href="/register"
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
              >
                Get started
              </Link>
            </span>
          </header>
          <main className="flex-1">{children}</main>
          <footer className="border-t border-slate-200 bg-white px-4 py-10 sm:px-6">
            <div className="mx-auto grid w-full max-w-6xl gap-8 md:grid-cols-3">
              <div>
                <Logo />
                <p className="mt-3 max-w-sm text-sm text-slate-500">
                  EpitomeJob — AI career assistance for laid-off H-1B workers.
                  One-time pricing, no subscription.
                </p>
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">Product</p>
                <ul className="mt-3 flex flex-col gap-2 text-sm text-slate-600">
                  <li><Link href="/#how-it-works" className="transition-colors hover:text-brand-700">How it works</Link></li>
                  <li><Link href="/#features" className="transition-colors hover:text-brand-700">Features</Link></li>
                  <li><Link href="/#pricing" className="transition-colors hover:text-brand-700">Pricing</Link></li>
                </ul>
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">Account</p>
                <ul className="mt-3 flex flex-col gap-2 text-sm text-slate-600">
                  <li><Link href="/login" className="transition-colors hover:text-brand-700">Sign in</Link></li>
                  <li><Link href="/register" className="transition-colors hover:text-brand-700">Get started</Link></li>
                </ul>
              </div>
            </div>
          </footer>
        </div>
      );
    }
    return (
      <div className="flex min-h-screen flex-col bg-surface">
        <header className="flex h-16 items-center border-b border-slate-200/80 bg-white px-4 shadow-sm">
          <Logo />
        </header>
        <main className="flex flex-1 items-center justify-center p-4">
          {children}
        </main>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-surface">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-slate-200/80 bg-white md:flex">
        <div className="flex h-16 items-center gap-2 border-b border-slate-200/80 px-4">
          <Logo />
        </div>
        <SidebarClock />
        <div className="flex-1 overflow-y-auto">
          <NavLinks />
        </div>
        <div className="border-t border-slate-200/80 p-3">
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
          <aside className="absolute inset-y-0 left-0 flex w-72 flex-col bg-white shadow-xl">
            <div className="flex h-16 items-center justify-between border-b border-slate-200/80 px-4">
              <Logo />
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setMobileOpen(false)}
                aria-label="Close menu"
              >
                <X className="h-5 w-5" aria-hidden />
              </Button>
            </div>
            <SidebarClock />
            <div className="flex-1 overflow-y-auto">
              <NavLinks onNavigate={() => setMobileOpen(false)} />
            </div>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col md:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-slate-200/80 bg-white/95 px-4 shadow-sm backdrop-blur">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setMobileOpen(true)}
            aria-label="Open menu"
          >
            <Menu className="h-5 w-5" aria-hidden />
          </Button>
          <span className="hidden text-sm font-semibold text-slate-700 lg:block">
            AI Career Assistance
          </span>
          <HeaderSearch />
          <span className="ml-auto flex items-center gap-1.5">
            <NotificationBell />
            <span className="hidden rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 sm:inline">
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
