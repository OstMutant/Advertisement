package org.ost.orchestrator.dto;

/** Discriminates which underlying feedback-domain row a ModerationQueueItemDto row represents. */
public enum ModerationQueueItemType {
    FEEDBACK,
    COMMENT
}
