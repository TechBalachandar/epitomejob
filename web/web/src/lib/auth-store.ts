import { create } from "zustand";

// Auth state lives in memory only. The access token is never written to
// localStorage (or any other persistent storage), and the refresh token is
// not held here at all: in W1 the BFF sets it as an httpOnly cookie and the
// browser sends it automatically. Consequence: a full page reload still
// loses the session and returns to /login (session restore on load is a
// known W1 follow-up, not part of the W1 gate).
interface AuthState {
  userId: string | null;
  email: string | null;
  role: string | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  login: (input: {
    userId: string;
    email: string;
    role: string;
    accessToken: string;
  }) => void;
  /** Replace the access token after a refresh; leaves everything else. */
  setAccessToken: (accessToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  userId: null,
  email: null,
  role: null,
  accessToken: null,
  isAuthenticated: false,
  login: ({ userId, email, role, accessToken }) =>
    set({ userId, email, role, accessToken, isAuthenticated: true }),
  setAccessToken: (accessToken) => set({ accessToken }),
  logout: () =>
    set({
      userId: null,
      email: null,
      role: null,
      accessToken: null,
      isAuthenticated: false,
    }),
}));
