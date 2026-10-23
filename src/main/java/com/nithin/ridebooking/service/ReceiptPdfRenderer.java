package com.nithin.ridebooking.service;

import com.nithin.ridebooking.dto.ReceiptResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReceiptPdfRenderer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final float LEFT = 50f;
    private static final float VALUE_X = 190f;

    private ReceiptPdfRenderer() {
    }

    public static byte[] render(ReceiptResponse r) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = 790f;
                y = write(cs, bold, 22, LEFT, y, "Ride Receipt") - 4;
                y = write(cs, regular, 11, LEFT, y, r.receiptNumber()) - 6;

                cs.moveTo(LEFT, y);
                cs.lineTo(545, y);
                cs.stroke();
                y -= 26;

                y = row(cs, bold, regular, y, "Ride id", String.valueOf(r.rideId()));
                y = row(cs, bold, regular, y, "Requested", date(r.requestedAt()));
                y = row(cs, bold, regular, y, "Completed", date(r.completedAt()));
                y = row(cs, bold, regular, y, "Rider", clean(r.riderName(), 60));
                y = row(cs, bold, regular, y, "Driver", clean(r.driverName(), 60));
                y = row(cs, bold, regular, y, "Vehicle", vehicle(r));
                y = row(cs, bold, regular, y, "Pickup", clean(r.pickupAddress(), 70));
                y = row(cs, bold, regular, y, "Drop-off", clean(r.dropAddress(), 70));
                y = row(cs, bold, regular, y, "Distance",
                        r.distanceKm() == null ? "-" : r.distanceKm() + " km");
                y = row(cs, bold, regular, y, "Payment", clean(r.paymentStatus(), 30));

                y -= 6;
                cs.moveTo(LEFT, y);
                cs.lineTo(545, y);
                cs.stroke();
                y -= 28;

                write(cs, bold, 16, LEFT, y, "Total fare");
                write(cs, bold, 16, VALUE_X, y, money(r));
                y -= 60;

                write(cs, regular, 9, LEFT, y, "This receipt was generated automatically.");
            }

            document.save(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Could not generate the receipt PDF", e);
        }
    }

    private static float write(PDPageContentStream cs, PDFont font, float size,
                               float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y - (size + 6);
    }

    private static float row(PDPageContentStream cs, PDFont bold, PDFont regular,
                             float y, String label, String value) throws IOException {
        write(cs, bold, 11, LEFT, y, label);
        write(cs, regular, 11, VALUE_X, y, value);
        return y - 20;
    }

    // The built-in PDF fonts only support basic characters. Anything else would make
    // PDFBox throw, so unsupported characters become '?'.
    private static String clean(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c >= 32 && c <= 126 ? c : '?');
        }
        String s = sb.toString();
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private static String date(LocalDateTime d) {
        return d == null ? "-" : DATE.format(d);
    }

    private static String vehicle(ReceiptResponse r) {
        if (r.vehicleModel() == null || r.vehicleNumber() == null) {
            return "-";
        }
        return clean(r.vehicleModel() + " (" + r.vehicleNumber() + ")", 60);
    }

    private static String money(ReceiptResponse r) {
        if (r.fare() == null) {
            return "-";
        }
        return clean(r.currency(), 3).toUpperCase() + " "
                + r.fare().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}