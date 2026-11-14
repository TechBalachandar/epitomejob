# Phase 5B (MCP split): DELIBERATE DUPLICATE of agent-brain's app/auth.py.
# agent-brain still needs validate_token for /chat (app/main.py); the MCP server needs it for per-connection Bearer validation.
# Kept verbatim so behaviour is identical on both sides of the split.
# There is intentionally no shared package: fix a bug here AND in
# agent-brain's copy until a shared-helper decision is revisited.

"""Bearer-token validation against the Java backend (contract §2).

The agent-brain holds no user store and no shared secret. A token is valid
iff Java ``GET /profiles/me`` accepts it with the same Bearer header.
The ``userId`` used everywhere is the one from that validated profile.
"""

from __future__ import annotations

import httpx


class InvalidTokenError(Exception):
    """Java answered 401 (or another auth failure) for the token."""


class JavaUnavailableError(Exception):
    """The agent-brain could not reach Java at all (maps to HTTP 502)."""


async def validate_token(
    token: str,
    java_base_url: str,
    client: httpx.AsyncClient | None = None,
) -> dict[str, object]:
    """Validate ``token`` via Java GET /profiles/me.

    Returns the profile JSON (contains ``userId``). Raises
    :class:`InvalidTokenError` on 401, :class:`JavaUnavailableError` when
    Java cannot be reached.
    """
    own_client = client is None
    if own_client:
        # trust_env=False: Java is localhost; never route it via a proxy.
        client = httpx.AsyncClient(base_url=java_base_url.rstrip("/"), timeout=10.0, trust_env=False)
    assert client is not None
    try:
        resp = await client.get(
            "/profiles/me", headers={"Authorization": f"Bearer {token}"}
        )
    except (httpx.ConnectError, httpx.TimeoutException) as exc:
        raise JavaUnavailableError(f"could not reach Java at {java_base_url}: {exc}") from exc
    finally:
        if own_client:
            await client.aclose()
    if resp.status_code == 401:
        raise InvalidTokenError("Java rejected the token (401)")
    if resp.status_code != 200:
        raise InvalidTokenError(f"Java /profiles/me returned {resp.status_code}")
    data = resp.json()
    if not isinstance(data, dict) or "userId" not in data:
        raise InvalidTokenError("Java profile response has no userId")
    return data
