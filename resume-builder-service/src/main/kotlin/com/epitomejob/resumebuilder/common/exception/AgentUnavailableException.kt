package com.epitomejob.resumebuilder.common.exception

/** A downstream service could not be reached (timeout / connect failure) → 502. */
class AgentUnavailableException(message: String?, cause: Throwable?) : RuntimeException(message, cause)
