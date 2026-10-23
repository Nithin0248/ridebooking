package com.nithin.ridebooking.payment;

public interface PaymentGateway {

    /** Creates a payment at the provider. amountMinor is in the smallest unit (paise for INR). */
    IntentResult createIntent(long amountMinor, String currency, Long rideId);

    /** Verifies the webhook signature and returns what happened. Throws if invalid. */
    WebhookEvent parseWebhook(String payload, String signatureHeader);

    record IntentResult(String id, String clientSecret) {}

    record WebhookEvent(String type, String paymentIntentId) {}
}