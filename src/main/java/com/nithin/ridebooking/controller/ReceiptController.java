package com.nithin.ridebooking.controller;

import com.nithin.ridebooking.dto.ReceiptResponse;
import com.nithin.ridebooking.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides/{rideId}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public ReceiptResponse receipt(@PathVariable Long rideId, Authentication authentication) {
        return receiptService.getReceipt(authentication.getName(), rideId);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long rideId, Authentication authentication) {
        ReceiptResponse receipt = receiptService.getReceipt(authentication.getName(), rideId);
        byte[] pdf = receiptService.renderPdf(receipt);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(receipt.receiptNumber() + ".pdf").build().toString())
                .body(pdf);
    }
}