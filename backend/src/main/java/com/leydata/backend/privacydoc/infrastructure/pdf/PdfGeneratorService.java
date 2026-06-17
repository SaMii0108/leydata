package com.leydata.backend.privacydoc.infrastructure.pdf;

import com.leydata.backend.entity.PrivacyDocuments;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Genera el PDF de un Documento de Privacidad usando Apache PDFBox 3.x.
 *
 * El PDF se genera completamente en memoria (sin escritura a disco) y se devuelve
 * como byte[]. PostgreSQL lo almacena como bytea en la columna pdf_content.
 *
 * Flujo al publicar:
 *   1. renderContent() construye el PDF en un PDDocument en memoria
 *   2. pdf.save(ByteArrayOutputStream) serializa el binario
 *   3. Se calcula SHA-256 sobre el byte[] resultante
 *   4. PrivacyDocumentService persiste bytes + hash en la entidad
 */
@Slf4j
@Service
public class PdfGeneratorService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float MARGIN        = 50f;
    private static final float PAGE_WIDTH    = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT   = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // ── API pública ──────────────────────────────────────────────────────────────

    /**
     * Genera el PDF del documento y lo devuelve como bytes + hash SHA-256.
     * No escribe nada a disco.
     */
    public PdfResult generate(PrivacyDocuments document) {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            renderContent(pdf, document);
            pdf.save(baos);

            byte[] pdfBytes = baos.toByteArray();
            String hash     = computeSha256(pdfBytes);

            log.info("PDF generado en memoria | id={} v{} | {} bytes | SHA-256: {}",
                    document.getId(), document.getVersion(), pdfBytes.length, hash);

            return new PdfResult(pdfBytes, hash);

        } catch (IOException e) {
            throw new RuntimeException("Error al generar el PDF del documento " + document.getId(), e);
        }
    }

    /**
     * Verifica si el SHA-256 de los bytes almacenados coincide con el hash registrado.
     * Detecta corrupción o alteración del binario en la BD.
     */
    public boolean verify(byte[] pdfContent, String storedHash) {
        if (pdfContent == null || pdfContent.length == 0 || storedHash == null) {
            return false;
        }
        return computeSha256(pdfContent).equals(storedHash);
    }

    /**
     * Calcula el SHA-256 actual de los bytes almacenados.
     * Útil para el endpoint de verificación cuando el hash no coincide.
     */
    public String computeCurrentHash(byte[] pdfContent) {
        return computeSha256(pdfContent);
    }

    // ── Renderizado ──────────────────────────────────────────────────────────────

    private void renderContent(PDDocument pdf, PrivacyDocuments doc) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);

        var bold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        var normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        var italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
            float y = PAGE_HEIGHT - MARGIN;

            // ── Título ──────────────────────────────────────────────────────────
            cs.beginText();
            cs.setFont(bold, 16);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(doc.getName());
            cs.endText();
            y -= 24;

            // ── Metadatos ───────────────────────────────────────────────────────
            cs.beginText();
            cs.setFont(normal, 10);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Categoría: " + doc.getCategory().name()
                    + "   |   Versión: " + doc.getVersion()
                    + "   |   Publicado: "
                    + (doc.getPublishAt() != null ? doc.getPublishAt().format(DATE_FMT) : "—"));
            cs.endText();
            y -= 20;

            // ── Separador ───────────────────────────────────────────────────────
            cs.moveTo(MARGIN, y);
            cs.lineTo(PAGE_WIDTH - MARGIN, y);
            cs.stroke();
            y -= 20;

            // ── Contenido legal ─────────────────────────────────────────────────
            cs.beginText();
            cs.setFont(bold, 11);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Contenido");
            cs.endText();
            y -= 16;

            if (doc.getContent() != null && !doc.getContent().isBlank()) {
                y = renderWrappedText(cs, normal, 10, doc.getContent(), MARGIN, y, CONTENT_WIDTH, 14);
            }

            // ── Propósitos ──────────────────────────────────────────────────────
            y -= 20;
            cs.beginText();
            cs.setFont(bold, 11);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Propósitos cubiertos");
            cs.endText();
            y -= 16;

            if (doc.getDocumentPurposes() != null) {
                for (var dp : doc.getDocumentPurposes()) {
                    if (y < MARGIN + 40) break;
                    cs.beginText();
                    cs.setFont(normal, 10);
                    cs.newLineAtOffset(MARGIN + 10, y);
                    cs.showText("• " + dp.getId().getPurposeId());
                    cs.endText();
                    y -= 14;
                }
            }

            // ── Pie de página ───────────────────────────────────────────────────
            cs.beginText();
            cs.setFont(italic, 8);
            cs.newLineAtOffset(MARGIN, MARGIN);
            cs.showText("Documento generado por LeyData · Ley 21.719 (Chile) · ID: " + doc.getId());
            cs.endText();
        }
    }

    private float renderWrappedText(PDPageContentStream cs, PDType1Font font, float fontSize,
                                    String text, float x, float startY,
                                    float maxWidth, float leading) throws IOException {
        float y = startY;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;
            if (width > maxWidth && !line.isEmpty()) {
                writeLine(cs, font, fontSize, x, y, line.toString());
                y -= leading;
                line = new StringBuilder(word);
                if (y < MARGIN + 40) return y;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            writeLine(cs, font, fontSize, x, y, line.toString());
            y -= leading;
        }
        return y;
    }

    private void writeLine(PDPageContentStream cs, PDType1Font font, float fontSize,
                           float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en este JVM", e);
        }
    }

    // ── Record de resultado ──────────────────────────────────────────────────────

    /** Resultado de la generación del PDF: bytes binarios + hash de integridad. */
    public record PdfResult(byte[] pdfBytes, String sha256Hash) {}
}
