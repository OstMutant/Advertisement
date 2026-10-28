package org.ost.feedback.repository;

import org.ost.feedback.entity.FeedbackContent;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@code feedback_content}; bespoke queries live in {@link FeedbackRepository}. */
public interface FeedbackContentCrudRepository extends CrudRepository<FeedbackContent, Long> {
}
