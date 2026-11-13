package com.epitomejob.billing.dto;

/** POST /billing/webhook response — always {"received": true} for a well-formed event. */
public record WebhookResponse(boolean received) {}
