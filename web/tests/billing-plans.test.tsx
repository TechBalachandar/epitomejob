// W4 billing plans page tests (contract §1f). fetch is mocked at the BFF
// boundary (/api/billing/*) — the real backend is never involved. Covers:
// status off (billing-off note + disabled checkout), status on with the
// fake provider (demo note), Stripe checkout (opens the returned URL in a
// new tab), HUMAN_REVIEWED PENDING_MANUAL (thanks message, no redirect),
// and a backend 503 on checkout (message shown verbatim).
import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import PlansPage, {
  BILLING_OFF_NOTE,
  CHECKOUT_CANCELLED_MESSAGE,
  CHECKOUT_SUCCESS_MESSAGE,
  DEMO_NOTE,
  PENDING_MANUAL_MESSAGE,
} from "@/app/plans/page";

// The page reads ?checkout= via next/navigation; control it per test.
const navMock = vi.hoisted(() => ({ params: new URLSearchParams() }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => navMock.params,
}));

const PLANS = [
  {
    code: "SPRINT_PASS",
    name: "Sprint Pass",
    priceCents: 7900,
    currency: "USD",
    durationDays: 30,
    description: "30 days of full access — one-time payment.",
    active: true,
  },
  {
    code: "LIFETIME",
    name: "Lifetime",
    priceCents: 14900,
    currency: "USD",
    durationDays: null,
    description: "Lifetime access — one-time payment.",
    active: true,
  },
  {
    code: "HUMAN_REVIEWED",
    name: "Human-Reviewed Package",
    priceCents: 29900,
    currency: "USD",
    durationDays: null,
    description:
      "Human-reviewed resume + plan. Fulfilled manually by our team — we contact you after checkout.",
    active: true,
  },
];

const BILLING_NOT_CONFIGURED =
  "Billing is not connected yet — no payment is taken.";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function mockFetch(
  impl: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  return vi.fn((input: string | URL | Request, init?: RequestInit) => {
    const url = typeof input === "string" ? input : input.toString();
    return Promise.resolve(impl(url, init));
  });
}

/** Status + plans stubs; `checkout` overrides the checkout response. */
function stubBilling(opts: {
  enabled: boolean;
  provider?: "stripe" | "fake";
  checkout?: (planCode: string) => Response;
}) {
  const fetchMock = mockFetch((url, init) => {
    if (url === "/api/billing/status") {
      return jsonResponse({
        enabled: opts.enabled,
        provider: opts.provider ?? "stripe",
      });
    }
    if (url === "/api/billing/plans") {
      return opts.enabled
        ? jsonResponse(PLANS)
        : jsonResponse({ message: "Not found" }, 404);
    }
    if (url === "/api/billing/entitlement/me") {
      return jsonResponse({
        planCode: "SPRINT_PASS",
        status: "ACTIVE",
        expiresAt: "2026-12-12T00:00:00.000Z",
      });
    }
    if (url === "/api/billing/checkout") {
      if (opts.checkout) {
        const body = JSON.parse(String(init?.body ?? "{}")) as {
          planCode: string;
        };
        return opts.checkout(body.planCode);
      }
      return jsonResponse({ message: "Not found" }, 404);
    }
    return jsonResponse({ message: "not found" }, 404);
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  navMock.params = new URLSearchParams();
});

describe("PlansPage — billing off (contract §1f)", () => {
  it("shows the three plans with the billing-off note and disabled checkout", async () => {
    const fetchMock = stubBilling({ enabled: false });

    render(<PlansPage />);

    await waitFor(() =>
      expect(screen.getByText(BILLING_OFF_NOTE)).toBeInTheDocument(),
    );
    expect(screen.getByText("Sprint Pass")).toBeInTheDocument();
    expect(screen.getByText("Lifetime")).toBeInTheDocument();
    expect(screen.getByText("Human-Reviewed Package")).toBeInTheDocument();
    expect(screen.getByText("$79")).toBeInTheDocument();
    expect(screen.getByText("$149")).toBeInTheDocument();
    expect(screen.getByText("$299")).toBeInTheDocument();

    for (const name of ["Sprint Pass", "Lifetime", "Human-Reviewed Package"]) {
      expect(
        screen.getByRole("button", { name: `Checkout ${name}` }),
      ).toBeDisabled();
    }
    // Plans are the hardcoded display copy — /billing/plans is never called
    // while the flag is off (the backend 404s on it).
    expect(
      fetchMock.mock.calls.some(([url]) => url === "/api/billing/plans"),
    ).toBe(false);
  });
});

describe("PlansPage — billing on", () => {
  it("fake provider: shows the demo note and enabled checkout", async () => {
    stubBilling({ enabled: true, provider: "fake" });

    render(<PlansPage />);

    await waitFor(() =>
      expect(screen.getByText(DEMO_NOTE)).toBeInTheDocument(),
    );
    expect(screen.queryByText(BILLING_OFF_NOTE)).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Checkout Sprint Pass" }),
    ).toBeEnabled();
  });

  it("stripe provider: checkout POSTs {planCode} and opens the Stripe URL in a new tab", async () => {
    const openSpy = vi
      .spyOn(window, "open")
      .mockImplementation(() => null);
    const fetchMock = stubBilling({
      enabled: true,
      provider: "stripe",
      checkout: (planCode) => {
        expect(planCode).toBe("SPRINT_PASS");
        return jsonResponse(
          {
            checkoutSessionId: "cs_test_123",
            provider: "stripe",
            checkoutUrl: "https://checkout.stripe.test/pay/cs_test_123",
            status: "CREATED",
          },
          201,
        );
      },
    });
    const user = userEvent.setup();

    render(<PlansPage />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Checkout Sprint Pass" }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole("button", { name: "Checkout Sprint Pass" }),
    );

    await waitFor(() =>
      expect(openSpy).toHaveBeenCalledWith(
        "https://checkout.stripe.test/pay/cs_test_123",
        "_blank",
        "noopener,noreferrer",
      ),
    );
    const checkoutCall = fetchMock.mock.calls.find(
      ([url]) => url === "/api/billing/checkout",
    );
    expect(checkoutCall).toBeDefined();
    expect(checkoutCall?.[1]?.method).toBe("POST");
    expect(checkoutCall?.[1]?.body).toBe(
      JSON.stringify({ planCode: "SPRINT_PASS" }),
    );
  });

  it("HUMAN_REVIEWED: 201 PENDING_MANUAL shows the thanks message with no redirect", async () => {
    const openSpy = vi
      .spyOn(window, "open")
      .mockImplementation(() => null);
    stubBilling({
      enabled: true,
      provider: "stripe",
      checkout: () =>
        jsonResponse(
          {
            checkoutSessionId: "manual-1",
            provider: "stripe",
            checkoutUrl: null,
            status: "PENDING_MANUAL",
          },
          201,
        ),
    });
    const user = userEvent.setup();

    render(<PlansPage />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", {
          name: "Checkout Human-Reviewed Package",
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole("button", {
        name: "Checkout Human-Reviewed Package",
      }),
    );

    await waitFor(() =>
      expect(screen.getByText(PENDING_MANUAL_MESSAGE)).toBeInTheDocument(),
    );
    expect(openSpy).not.toHaveBeenCalled();
  });

  it("backend 503 on checkout: shows the backend message verbatim", async () => {
    stubBilling({
      enabled: true,
      provider: "stripe",
      checkout: () =>
        jsonResponse(
          {
            code: "BILLING_NOT_CONFIGURED",
            message: BILLING_NOT_CONFIGURED,
          },
          503,
        ),
    });
    const user = userEvent.setup();

    render(<PlansPage />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "Checkout Lifetime" }),
      ).toBeEnabled(),
    );

    await user.click(screen.getByRole("button", { name: "Checkout Lifetime" }));

    await waitFor(() =>
      expect(screen.getByText(BILLING_NOT_CONFIGURED)).toBeInTheDocument(),
    );
  });
});

describe("PlansPage — Stripe redirect (?checkout=)", () => {
  it("checkout=success: shows the confirmation and refreshes the entitlement", async () => {
    navMock.params = new URLSearchParams(
      "checkout=success&session_id=cs_test_123",
    );
    const fetchMock = stubBilling({ enabled: true, provider: "stripe" });

    render(<PlansPage />);

    await waitFor(() =>
      expect(screen.getByText(CHECKOUT_SUCCESS_MESSAGE)).toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(screen.getByText(/Current plan: SPRINT_PASS/)).toBeInTheDocument(),
    );
    expect(
      fetchMock.mock.calls.some(
        ([url]) => url === "/api/billing/entitlement/me",
      ),
    ).toBe(true);
  });

  it("checkout=cancelled: shows that no payment was taken", async () => {
    navMock.params = new URLSearchParams("checkout=cancelled");
    stubBilling({ enabled: true, provider: "stripe" });

    render(<PlansPage />);

    await waitFor(() =>
      expect(screen.getByText(CHECKOUT_CANCELLED_MESSAGE)).toBeInTheDocument(),
    );
  });
});
