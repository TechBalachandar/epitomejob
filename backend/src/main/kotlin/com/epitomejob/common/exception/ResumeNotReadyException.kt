package com.epitomejob.common.exception

import com.epitomejob.common.constants.AppConstants

/**
 * A resume exists and belongs to the caller, but its parsed text is not
 * ready yet (W3 contract §3) → 400 with code RESUME_NOT_READY.
 */
class ResumeNotReadyException :
  RuntimeException(AppConstants.Errors.RESUME_NOT_READY)
