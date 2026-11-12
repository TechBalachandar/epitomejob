import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/lib/auth-store";

// W1: the store holds the access token only. The refresh token lives in the
// httpOnly cookie set by the BFF and never enters this store.
const SESSION = {
  userId: "user-1",
  email: "dev@example.com",
  role: "USER",
  accessToken: "header.payload.signature",
};

describe("auth store", () => {
  it("starts logged out with no token in memory", () => {
    useAuthStore.getState().logout();
    const s = useAuthStore.getState();
    expect(s.isAuthenticated).toBe(false);
    expect(s.accessToken).toBeNull();
    expect(s.email).toBeNull();
  });

  it("login stores the session and marks the user authenticated", () => {
    useAuthStore.getState().login(SESSION);
    const s = useAuthStore.getState();
    expect(s.isAuthenticated).toBe(true);
    expect(s.email).toBe("dev@example.com");
    expect(s.accessToken).toBe("header.payload.signature");
    expect(s.userId).toBe("user-1");
  });

  it("setAccessToken replaces only the token (used after a refresh)", () => {
    useAuthStore.getState().login(SESSION);
    useAuthStore.getState().setAccessToken("new.header.signature");
    const s = useAuthStore.getState();
    expect(s.accessToken).toBe("new.header.signature");
    expect(s.isAuthenticated).toBe(true);
    expect(s.email).toBe("dev@example.com");
  });

  it("logout clears every field, including the access token", () => {
    useAuthStore.getState().login(SESSION);
    useAuthStore.getState().logout();
    const s = useAuthStore.getState();
    expect(s.isAuthenticated).toBe(false);
    expect(s.accessToken).toBeNull();
    expect(s.userId).toBeNull();
    expect(s.role).toBeNull();
  });
});
