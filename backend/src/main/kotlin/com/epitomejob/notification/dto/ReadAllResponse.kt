package com.epitomejob.notification.dto

/** POST /notifications/read-all response, per the M13 contract §1g. */
data class ReadAllResponse(val readCount: Int)
