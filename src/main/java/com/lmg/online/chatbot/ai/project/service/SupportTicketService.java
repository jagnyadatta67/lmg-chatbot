package com.lmg.online.chatbot.ai.project.service;

import com.lmg.online.chatbot.ai.common.ConceptBaseUrlResolver;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketRequest;
import com.lmg.online.chatbot.ai.tools.support.dto.SupportTicketResponse;
import com.lmg.online.chatbot.ai.tools.support.entity.SupportTicket;
import com.lmg.online.chatbot.ai.tools.support.repository.SupportTicketRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

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
    private final RestTemplate               restTemplate;

    @Value("${support.email.to:support@landmarkgroup.in}")
    private String supportEmailTo;

    @Value("${support.email.from:chatbot-noreply@landmarkgroup.in}")
    private String supportEmailFrom;

    // ── Public API ────────────────────────────────────────────────────────────

    @Async
    public void submitTicket(SupportTicketRequest req) {
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
            main(req.getMessage(),req.getMessage()+req.getCategory(),new Date(),req.getEmail());
            log.info("💾 Support ticket {} saved to DB", ticketId);
        } catch (Exception dbEx) {
            // Don't abort — still try to send the email even if DB write fails
            log.error("⚠️  Failed to persist ticket {} to DB: {}", ticketId, dbEx.getMessage(), dbEx);
        }

        // ── Step 2: Call Hybris feedback/submit API ───────────────────────────
        submitToHybris(req, ticketId);

        // ── Step 3: Send notification email ──────────────────────────────────
        try {
            sendEmail(req, ticketId);

            // Mark email as sent
            ticket.setEmailSent(true);
            ticketRepository.save(ticket);

            log.info("✉️  Support ticket {} raised — concept={}, category={}", ticketId, req.getConcept(), req.getCategory());


        } catch (Exception emailEx) {
            log.error("❌ Failed to send support email — ticketId={}: {}", ticketId, emailEx.getMessage(), emailEx);

            String phone = ConceptBaseUrlResolver.getRawPhoneNumber(
                    req.getConcept() != null ? req.getConcept() : "LIFESTYLE");


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

    /**
     * Forwards the form submission to the Hybris storefront feedback API.
     * POST {concept+env base}/in/en/feedback/submit  (application/x-www-form-urlencoded)
     * Runs on the async executor — fire-and-forget, never blocks the API response.
     */

    public void submitToHybris(SupportTicketRequest req, String ticketId) {
        try {
            String url = ConceptBaseUrlResolver.buildReactUrl(
                    req.getConcept(), req.getEnv(), "/feedback/submit");

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("platformType",  req.getPlatformType() != null ? req.getPlatformType() : "Online");
            form.add("name",          safe(req.getName()));
            form.add("email",         safe(req.getEmail()));
            form.add("mobileNumber",  safe(req.getPhone()));
            form.add("city",          safe(req.getCity()));
            form.add("lmrNumber",     req.getRewards() != null ? req.getRewards() : "");
            form.add("feedbackType",  safe(req.getCategory()));
            form.add("message",       safe(req.getMessage()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

            log.info("📤 [{}] Forwarding to Hybris feedback API: {}", ticketId, url);
            log.info("📋 [{}] Payload: platformType={}, name={}, email={}, mobileNumber={}, city={}, feedbackType={}",
                    ticketId,
                    form.getFirst("platformType"),
                    form.getFirst("name"),
                    form.getFirst("email"),
                    form.getFirst("mobileNumber"),
                    form.getFirst("city"),
                    form.getFirst("feedbackType"));

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            log.info("✅ [{}] Hybris feedback response — status={}, body={}",
                    ticketId, response.getStatusCode(), response.getBody());

        } catch (Exception e) {
            log.error("❌ [{}] Hybris feedback API call failed: {}", ticketId, e.getMessage(), e);
        }
    }

    private String generateTicketId() {
        return "TKT-" + System.currentTimeMillis();
    }

    private String safe(String value) {
        return value != null ? value : "—";
    }


    public void main(String title, String ticketDetails, Date date,String email) {

        String url = "https://devapi.kapturecrm.com/add-ticket-from-other-source.html/v.2.0";

        RestTemplate restTemplate = new RestTemplate();

        // 🔹 Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic YOUR_TOKEN"); // replace token

        // Optional cookies (usually not required unless API enforces it)
        headers.set("Cookie", "JSESSIONID=; _KAPTURECRM_SESSION=");
        Calendar cal = Calendar.getInstance();

        // Add 5 days
        cal.add(Calendar.DAY_OF_MONTH, 5);

        Date updatedDate = cal.getTime();

        // Format to String
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String formattedDate = sdf.format(updatedDate);

        // 🔹 Body (List of tickets)
        List<Map<String, Object>> requestBody = new ArrayList<>();

        Map<String, Object> ticket = new HashMap<>();
        ticket.put("title", title);
        ticket.put("ticket_details", formattedDate);
        ticket.put("due_date",updatedDate.toString() );
        ticket.put("email_id", "");

        requestBody.add(ticket);

        // 🔹 Request Entity
        HttpEntity<List<Map<String, Object>>> request =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            System.out.println("Status Code: " + response.getStatusCode());
            System.out.println("Response: " + response.getBody());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
