package com.nithin.ridebooking.payment;

import com.nithin.ridebooking.exception.BadRequestException;
import com.nithin.ridebooking.exception.PaymentProviderException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.HasId;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class StripePaymentGateway implements PaymentGateway {

    private final String secretKey;
    private final String webhookSecret;

    public StripePaymentGateway(@Value("${stripe.secret-key}") String secretKey,
                                @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public IntentResult createIntent(long amountMinor, String currency, Long rideId) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentProviderException("Payments are not configured (stripe.secret-key is empty)");
        }

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountMinor)
                    .setCurrency(currency)
                    .putMetadata("rideId", String.valueOf(rideId))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(
                                            PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setApiKey(secretKey)
                    .setIdempotencyKey("ride-" + rideId + "-" + UUID.randomUUID())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, options);
            return new IntentResult(intent.getId(), intent.getClientSecret());

        } catch (StripeException e) {
            log.error("Stripe createIntent failed for ride {}: {}", rideId, e.getMessage());
            throw new PaymentProviderException("The payment provider rejected the request. Try again later.");
        }
    }

    @Override
    public WebhookEvent parseWebhook(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentProviderException("Webhooks are not configured (stripe.webhook-secret is empty)");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Missing Stripe-Signature header");
        }

        try {
            // Verifies the signature first, then parses. Forged requests are rejected.
            Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret, 300L);

            StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
            if (object == null) {
                object = event.getDataObjectDeserializer().deserializeUnsafe();
            }

            String id = (object instanceof HasId hasId && hasId.getId() != null) ? hasId.getId() : "";
            return new WebhookEvent(event.getType(), id);

        } catch (SignatureVerificationException e) {
            throw new BadRequestException("Invalid webhook signature");
        } catch (Exception e) {
            log.warn("Could not read webhook payload: {}", e.getMessage());
            throw new BadRequestException("Malformed webhook payload");
        }
    }
}