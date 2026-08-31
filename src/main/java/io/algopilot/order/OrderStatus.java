package io.algopilot.order;

/** Valid lifecycle states; exchange adapters may only advance an existing order. */
public enum OrderStatus { CREATED, SUBMITTED, ACKNOWLEDGED, PARTIALLY_FILLED, FILLED, CANCEL_REQUESTED, CANCELLED, REJECTED, EXPIRED, FAILED }
