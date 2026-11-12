"""EpitomeJob W3 agent-brain — environment settings.

Contract: W3-API-CONTRACT.md §10. Every variable has a localhost default so
the service runs on the Mac with no env file at all. No cloud LLM key, no
LangSmith key — LANGSMITH_ENABLED is false by default and never required.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All agent-brain settings (contract §10)."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    agent_brain_port: int = 8000
    mcp_port: int = 8001
    java_base_url: str = "http://localhost:8080"
    ollama_base_url: str = "http://localhost:11434"
    ollama_chat_model: str = "llama3.1:8b"
    ollama_embed_model: str = "nomic-embed-text"
    ollama_timeout_s: float = 120.0
    database_url: str = "postgresql://epitomejob:epitomejob-dev@localhost:5432/epitomejob"
    temporal_address: str = "localhost:7233"
    temporal_enabled: bool = True
    langsmith_enabled: bool = False


settings = Settings()
