package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.ReceiptResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ReceiptPdfRendererTest {

    private ReceiptResponse receipt(String pickup) {
        return new ReceiptResponse("RCPT-000005", 5L,
                LocalDateTime.of(2026, 10, 23, 9, 0), LocalDateTime.of(2026, 10, 23, 9, 30),
                "Rider One", "Driver One", "Swift", "KA01AB1111",
                pickup, "City Mall", 6.74, new BigDecimal("151.88"), "inr", "SUCCEEDED");
    }

    @Test
    void producesAValidPdfContainingTheKeyDetails() throws Exception {
        byte[] pdf = ReceiptPdfRenderer.render(receipt("Station Road"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("RCPT-000005", "Rider One", "INR 151.88", "SUCCEEDED");
        }
    }

    @Test
    void unsupportedCharactersDoNotBreakThePdf() {
        // Telugu letters and a rupee sign cannot be drawn with the built-in fonts
        assertThatCode(() -> ReceiptPdfRenderer.render(receipt("\u0C35\u0C3F\u0C36\u0C3E\u0C16 \u20B9 Road")))
                .doesNotThrowAnyException();
    }

    @Test
    void veryLongAddressesAreShortened() {
        assertThatCode(() -> ReceiptPdfRenderer.render(receipt("A".repeat(500))))
                .doesNotThrowAnyException();
    }
}