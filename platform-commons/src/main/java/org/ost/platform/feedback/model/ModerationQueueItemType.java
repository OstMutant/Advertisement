package org.ost.platform.feedback.model;

/** Discriminates which underlying feedback-domain row a ModerationQueueItemDto row represents. */
public enum ModerationQueueItemType {
    FEEDBACK,
    COMMENT
}
