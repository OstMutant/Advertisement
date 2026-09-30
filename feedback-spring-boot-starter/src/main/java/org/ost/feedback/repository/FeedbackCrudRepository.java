package org.ost.feedback.repository;

import org.ost.feedback.entity.Feedback;
import org.springframework.data.repository.CrudRepository;

/** Trivial save/find for {@code feedback}; bespoke queries live in {@link FeedbackRepository}. */
public interface FeedbackCrudRepository extends CrudRepository<Feedback, Long> {
}
