package com.epitomejob.releasecontrol.model;

/** Server-owned lifecycle states. Agents cannot set arbitrary values. */
public enum ReleaseState {
  CREATED,
  ARCHITECTURE_REVIEW,
  QA_PENDING,
  UAT_PENDING,
  UAT_APPROVED,
  PREPROD_PENDING,
  PREPROD_APPROVED,
  PROD_PENDING,
  PROD_APPROVED,
  COMPLETED,
  BLOCKED,
  REJECTED,
  ROLLBACK_PENDING,
  ROLLED_BACK,
  CANCELLED
}
