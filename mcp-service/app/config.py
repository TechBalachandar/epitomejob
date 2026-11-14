"""EpitomeJob Phase 5B mcp-service — environment settings.

Deliberate duplicate of agent-brain's app/config.py, TRIMMED to what the
MCP path uses: mcp_port, java_base_url, ollama_*, database_url. Dropped:
agent_brain_port, temporal_address, temporal_enabled, langsmith_enabled —
the MCP server never talks to Temporal or LangSmith. Every variable has a
localhost default so the service runs on the Mac with no env file at all.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All mcp-service settings."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    mcp_port: int = 8001
    java_base_url: str = "http://localhost:8080"
    ollama_base_url: str = "http://localhost:11434"
    ollama_chat_model: str = "llama3.1:8b"
    ollama_embed_model: str = "nomic-embed-text"
    ollama_timeout_s: float = 120.0
    database_url: str = "postgresql://epitomejob:epitomejob-dev@localhost:5432/epitomejob"


settings = Settings()
