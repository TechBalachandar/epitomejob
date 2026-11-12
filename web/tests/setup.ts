import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// jsdom has no layout engine, so scrollIntoView does not exist — stub it
// (the Coach page scrolls the chat to the newest message after each reply).
Element.prototype.scrollIntoView = () => {};

// Vitest runs with globals off, so Testing Library's automatic cleanup
// does not register itself — unmount after every test explicitly.
afterEach(() => {
  cleanup();
});
