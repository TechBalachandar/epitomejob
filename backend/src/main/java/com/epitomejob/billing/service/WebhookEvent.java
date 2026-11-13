package com.epitomejob.billing.service;

/** A verified webhook event. {@code type} is CHECKOUT_COMPLETED (other provider event types pass through untouched). */
public record WebhookEvent(String type, String providerSessionId) {}
