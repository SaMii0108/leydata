package com.leydata.backend.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendPurposeReviewEmail(String toEmail, String recipientName,
                                       String purposeTitle, String status, String notes) {
        boolean approved = "APPROVED".equals(status);
        String subject = approved
                ? "[LeyData] Solicitud aprobada: " + purposeTitle
                : "[LeyData] Solicitud rechazada: " + purposeTitle;
        String body = approved
                ? buildApprovedBody(recipientName, purposeTitle)
                : buildRejectedBody(recipientName, purposeTitle, notes);
        sendHtml(toEmail, subject, body);
    }

    @Async
    public void sendDocumentPublishedEmail(String toEmail, String recipientName,
                                           String documentName, String domainName) {
        String subject = "[LeyData] Documento publicado: " + documentName;
        sendHtml(toEmail, subject, buildPublishedBody(recipientName, documentName, domainName));
    }

    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom("noreply@leydata.cl");
            mailSender.send(message);
            log.info("Email enviado a {}: {}", to, subject);
        } catch (Exception e) {
            log.warn("No se pudo enviar email a {}: {}", to, e.getMessage());
        }
    }

    private String buildApprovedBody(String name, String title) {
        return """
                <html><body style="font-family:sans-serif;">
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu solicitud de propósito <strong>"%s"</strong> ha sido
                <strong style="color:#16a34a;">aprobada</strong> por el DPO.</p>
                <p>El propósito ya está disponible para ser vinculado a un documento de privacidad.</p>
                <br><p style="color:#6b7280;">— LeyData · Ley 21.719</p>
                </body></html>
                """.formatted(name, title);
    }

    private String buildRejectedBody(String name, String title, String notes) {
        return """
                <html><body style="font-family:sans-serif;">
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu solicitud de propósito <strong>"%s"</strong> ha sido
                <strong style="color:#dc2626;">rechazada</strong> por el DPO.</p>
                <p><strong>Motivo:</strong> %s</p>
                <p>Puedes crear una nueva solicitud corrigiendo los puntos indicados.</p>
                <br><p style="color:#6b7280;">— LeyData · Ley 21.719</p>
                </body></html>
                """.formatted(name, title, notes != null ? notes : "Sin notas adicionales");
    }

    private String buildPublishedBody(String name, String documentName, String domainName) {
        return """
                <html><body style="font-family:sans-serif;">
                <p>Hola <strong>%s</strong>,</p>
                <p>El documento de privacidad <strong>"%s"</strong> del dominio <strong>%s</strong>
                ha sido publicado por el DPO.</p>
                <p>El widget de consentimiento para los titulares ya está activo.</p>
                <br><p style="color:#6b7280;">— LeyData · Ley 21.719</p>
                </body></html>
                """.formatted(name, documentName, domainName);
    }
}
