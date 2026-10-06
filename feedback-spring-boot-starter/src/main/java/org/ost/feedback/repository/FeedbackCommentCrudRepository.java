package org.ost.feedback.repository;

import org.ost.feedback.entity.FeedbackComment;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@code feedback_comment}; bespoke queries live in {@link FeedbackRepository}. */
public interface FeedbackCommentCrudRepository extends CrudRepository<FeedbackComment, Long> {
}
