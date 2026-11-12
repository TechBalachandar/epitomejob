import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Vitest runs with globals off, so Testing Library's automatic cleanup
// does not register itself — unmount after every test explicitly.
afterEach(() => {
  cleanup();
});
