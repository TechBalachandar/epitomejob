// Module-level TanStack Query client (single instance for the whole app).
// Providers renders with this instance so api.ts can clear the cache on
// auth change: when the user logs in or out, every cached query is wiped,
// so data fetched for one user can never be served to another user
// (P0 #16 — the resumes list was served from the previous user's cache
// because the query keys were user-agnostic and login/logout never cleared).
import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

/** Drop every cached query (all users' data). Call on login and on logout. */
export function clearCachedUserData(): void {
  queryClient.clear();
}
