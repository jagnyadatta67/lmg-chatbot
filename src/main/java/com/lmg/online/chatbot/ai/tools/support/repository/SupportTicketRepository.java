package com.lmg.online.chatbot.ai.tools.support.repository;

import com.lmg.online.chatbot.ai.tools.support.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /** Look up a ticket by its human-readable ID (e.g. TKT-1715000000000) */
    Optional<SupportTicket> findByTicketId(String ticketId);

    /** All tickets for a given logged-in user, newest first */
    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(String userId);

    /** All open tickets for a concept (brand), newest first */
    List<SupportTicket> findByConceptAndStatusOrderByCreatedAtDesc(String concept, String status);

    /** Count open tickets — used by DataInitializer for startup health check */
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.status = 'OPEN'")
    long countOpenTickets();
}
