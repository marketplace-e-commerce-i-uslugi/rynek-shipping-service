package pl.rynek.shipping.carrier;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import pl.rynek.shipping.shipments.Shipment;

/**
 * Etykieta symulatora: jednostronicowy PDF A6 bez bibliotek. Czcionki standardowe PDF (Helvetica) nie mają polskich
 * liter, więc tekst jest zapisany bez ogonków (jak na wielu etykietach przewoźników). Prawdziwy przewoźnik zwraca
 * własną etykietę z kodem kreskowym.
 */
final class LabelPdf {

    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final int WIDTH = 298;
    private static final int HEIGHT = 420;

    private LabelPdf() {
    }

    static byte[] render(Shipment s) {
        StringBuilder c = new StringBuilder();
        c.append("0.85 g 0 ").append(HEIGHT - 44).append(' ').append(WIDTH).append(" 44 re f 0 g\n");
        text(c, "F2", 16, 16, HEIGHT - 28, s.getCarrier().label());
        text(c, "F1", 8, 16, HEIGHT - 40, "SYMULATOR RYNKU - NIE NADAWAC");
        text(c, "F1", 9, 16, HEIGHT - 70, "Numer przesylki");
        text(c, "F2", s.getTrackingNumber().length() > 16 ? 13 : 18, 16, HEIGHT - 90, s.getTrackingNumber());
        c.append("16 ").append(HEIGHT - 104).append(" m ").append(WIDTH - 16).append(' ').append(HEIGHT - 104).append(" l S\n");
        text(c, "F1", 9, 16, HEIGHT - 124, "Odbiorca");
        int y = HEIGHT - 142;
        for (String line : List.of(s.getReceiverName(), s.getReceiverAddress())) {
            for (String part : wrap(line, 34)) {
                text(c, "F2", 12, 16, y, part);
                y -= 16;
            }
        }
        if (s.getReceiverPhone() != null) {
            text(c, "F1", 11, 16, y, "tel. " + s.getReceiverPhone());
            y -= 16;
        }
        if (s.getPickupPointId() != null) {
            text(c, "F2", 14, 16, y - 6, "Paczkomat " + s.getPickupPointId());
        }
        c.append("16 120 m ").append(WIDTH - 16).append(" 120 l S\n");
        text(c, "F1", 9, 16, 100, "Nadawca");
        text(c, "F2", 11, 16, 84, "Sprzedawca na Rynku");
        text(c, "F1", 10, 16, 68, "Zamowienie " + s.getOrderNumber());
        return document(c.toString());
    }

    private static void text(StringBuilder c, String font, int size, int x, int y, String value) {
        c.append("BT /").append(font).append(' ').append(size).append(" Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(escape(ascii(value))).append(") Tj ET\n");
    }

    /** Polskie litery bez ogonków: „Łódź” → „Lodz”. */
    static String ascii(String value) {
        String s = value.replace('ł', 'l').replace('Ł', 'L');
        return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static List<String> wrap(String value, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Obiekty PDF z tablicą przesunięć (xref) liczoną w bajtach. */
    private static byte[] document(String content) {
        byte[] stream = content.getBytes(StandardCharsets.US_ASCII);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + WIDTH + " " + HEIGHT + "] /Contents 4 0 R"
                        + " /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
                "<< /Length " + stream.length + " >>\nstream\n" + content + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(o -> table.append(String.format("%010d 00000 n \n", o)));
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, table.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
