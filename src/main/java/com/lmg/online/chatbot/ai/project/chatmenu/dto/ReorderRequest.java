package com.lmg.online.chatbot.ai.project.chatmenu.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for reordering menus or sub-menus.
 *
 * {@code orderedIds} is the complete list of entity IDs in the desired
 * display order.  The service assigns displayOrder = 0, 1, 2, … based
 * on the list position.
 */
public record ReorderRequest(

        @NotEmpty(message = "orderedIds must not be empty")
        List<Long> orderedIds
) {}
