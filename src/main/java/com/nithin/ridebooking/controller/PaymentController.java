package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.PaymentResponse;
import com.nithin.ridebooking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // Rider starts paying for a completed ride
    @PostMapping("/api/rides/{rideId}/payment")
    public ResponseEntity<PaymentResponse> pay(@PathVariable Long rideId, Authentication authentication) {
        PaymentResponse response = paymentService.createPayment(authentication.getName(), rideId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Rider (or admin) checks the payment status
    @GetMapping("/api/rides/{rideId}/payment")
    public PaymentResponse status(@PathVariable Long rideId, Authentication authentication) {
        return paymentService.getPayment(authentication.getName(), rideId);
    }

    // Called by Stripe, not by your app. Public, but protected by the signature check.
    // The body must be the RAW text, because the signature covers the exact bytes.
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false)
                                        String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}