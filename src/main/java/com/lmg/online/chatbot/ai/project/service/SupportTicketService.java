package com.lmg.online.chatbot.ai.project.service;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketRequest;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketResponse;
import com.lmg.online.chatbot.ai.tools.support.entity.SupportTicket;
import com.lmg.online.chatbot.ai.tools.support.repository.SupportTicketRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Handles support ticket lifecycle:
 *  1. Persists the ticket to the DB (support_tickets table).
 *  2. Sends an HTML notification email to the support inbox.
 *
 * Triggered when:
 *  a) The user explicitly asks to write / contact support (WRITE_US intent).
 *  b) RAG returns 0 documents and PolicyIntentHandler escalates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private final JavaMailSender             mailSender;
    private final SupportTicketRepository    ticketRepository;

    @Value("${support.email.to:support@landmarkgroup.in}")
    private String supportEmailTo;

    @Value("${support.email.from:chatbot-noreply@landmarkgroup.in}")
    private String supportEmailFrom;

    // ── Public API ────────────────────────────────────────────────────────────

    public SupportTicketResponse submitTicket(SupportTicketRequest req) {
        String ticketId = generateTicketId();

        // ── Step 1: Persist to DB ─────────────────────────────────────────────
        SupportTicket ticket = SupportTicket.builder()
                .ticketId(ticketId)
                .name(req.getName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .category(req.getCategory())
                .message(req.getMessage())
                .concept(req.getConcept())
                .userId(req.getUserId())
                .appid(req.getAppid())
                .env(req.getEnv())
                .status("OPEN")
                .emailSent(false)
                .build();

        try {
            ticketRepository.save(ticket);
            log.info("💾 Support ticket {} saved to DB", ticketId);
        } catch (Exception dbEx) {
            // Don't abort — still try to send the email even if DB write fails
            log.error("⚠️  Failed to persist ticket {} to DB: {}", ticketId, dbEx.getMessage(), dbEx);
        }

        // ── Step 2: Send notification email ──────────────────────────────────
        try {
            sendEmail(req, ticketId);

            // Mark email as sent
            ticket.setEmailSent(true);
            ticketRepository.save(ticket);

            log.info("✉️  Support ticket {} raised — concept={}, category={}", ticketId, req.getConcept(), req.getCategory());
            return SupportTicketResponse.builder()
                    .success(true)
                    .ticketId(ticketId)
                    .message("✅ Your ticket **" + ticketId + "** has been raised! We'll get back to you within 24 hours.")
                    .build();

        } catch (Exception emailEx) {
            log.error("❌ Failed to send support email — ticketId={}: {}", ticketId, emailEx.getMessage(), emailEx);

            String phone = ConceptBaseUrlResolver.getRawPhoneNumber(
                    req.getConcept() != null ? req.getConcept() : "LIFESTYLE");

            return SupportTicketResponse.builder()
                    .success(false)
                    .ticketId(ticketId)   // ticket IS in DB even if email failed
                    .message("😔 Your ticket **" + ticketId + "** was saved but we couldn't send the confirmation email. " +
                             "Please call " + phone + " for immediate help.")
                    .build();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendEmail(SupportTicketRequest req, String ticketId) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        helper.setFrom(supportEmailFrom);
        helper.setTo(supportEmailTo);

        helper.setSubject(String.format("[%s | %s] Support Ticket %s — %s",
                safe(req.getConcept()), safe(req.getAppid()), ticketId, safe(req.getCategory())));
        helper.setText(buildHtmlBody(req, ticketId), true);

        mailSender.send(mime);
    }

    private String buildHtmlBody(SupportTicketRequest req, String ticketId) {
        return """
                <html>
                <body style="font-family:Arial,sans-serif;color:#333;max-width:600px">
                  <h2 style="color:#e07b00">🎫 Support Ticket — %s</h2>
                  <table border="1" cellpadding="10" cellspacing="0"
                         style="border-collapse:collapse;width:100%%">
                    <tr style="background:#f5f5f5">
                      <td><b>Ticket ID</b></td><td>%s</td>
                    </tr>
                    <tr><td><b>Brand</b></td><td>%s</td></tr>
                    <tr><td><b>Category</b></td><td>%s</td></tr>
                    <tr><td><b>Name</b></td><td>%s</td></tr>
                    <tr><td><b>Email</b></td><td>%s</td></tr>
                    <tr><td><b>Phone</b></td><td>%s</td></tr>
                    <tr><td><b>User ID</b></td><td>%s</td></tr>
                    <tr><td><b>App</b></td><td>%s</td></tr>
                    <tr><td><b>Env</b></td><td>%s</td></tr>
                    <tr style="background:#fff8f0">
                      <td><b>Message</b></td>
                      <td style="white-space:pre-wrap">%s</td>
                    </tr>
                  </table>
                  <p style="color:#888;font-size:12px;margin-top:16px">
                    Sent by Landmark Online Chatbot — do not reply directly to this email.
                  </p>
                </body>
                </html>
                """.formatted(
                safe(req.getConcept()),
                ticketId,
                safe(req.getConcept()),
                safe(req.getCategory()),
                safe(req.getName()),
                safe(req.getEmail()),
                safe(req.getPhone()),
                req.getUserId() != null ? req.getUserId() : "Guest",
                safe(req.getAppid()),
                safe(req.getEnv()),
                safe(req.getMessage())
        );
    }

    private String generateTicketId() {
        return "TKT-" + System.currentTimeMillis();
    }

    private String safe(String value) {
        return value != null ? value : "—";
    }
}
