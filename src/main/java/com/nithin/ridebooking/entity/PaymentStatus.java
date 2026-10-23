package com.nithin.ridebooking.entity;

public enum PaymentStatus {
    PENDING,    // payment created, waiting for the card to be charged
    SUCCEEDED,  // Stripe confirmed the charge (via webhook)
    FAILED      // card declined or payment failed
}