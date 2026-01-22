package org.javai.punit.contract;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A single postcondition (ensure clause) in a service contract.
 *
 * <p>A postcondition consists of:
 * <ul>
 *   <li>A description — human-readable name for the condition</li>
 *   <li>A predicate — the condition to evaluate against the derived value</li>
 * </ul>
 *
 * <h2>Lazy Evaluation</h2>
 * <p>Postconditions are stored as predicates and evaluated lazily when
 * {@link #evaluate(Object)} is called. This supports early termination
 * strategies and parallel evaluation.
 *
 * <h2>Usage</h2>
 * <p>Postconditions are typically created through the {@link ServiceContract} builder:
 * <pre>{@code
 * .deriving("Valid JSON", this::parseJson)
 *     .ensure("Has operations array", json -> json.has("operations"))
 *     .ensure("All operations valid", json -> allOpsValid(json))
 * }</pre>
 *
 * @param description the human-readable description
 * @param predicate the condition to evaluate
 * @param <T> the type of value this postcondition evaluates
 * @see ServiceContract
 * @see PostconditionResult
 */
public record Postcondition<T>(String description, Predicate<T> predicate) {

    /**
     * Creates a new postcondition.
     *
     * @throws NullPointerException if description or predicate is null
     * @throws IllegalArgumentException if description is blank
     */
    public Postcondition {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }

    /**
     * Evaluates this postcondition against a value.
     *
     * @param value the value to evaluate against
     * @return the result of evaluation (Passed or Failed)
     */
    public PostconditionResult evaluate(T value) {
        try {
            boolean result = predicate.test(value);
            return result
                    ? new PostconditionResult.Passed(description)
                    : new PostconditionResult.Failed(description);
        } catch (Exception e) {
            return new PostconditionResult.Failed(description, e.getMessage());
        }
    }

    /**
     * Creates a skipped result for this postcondition.
     *
     * <p>Used when a prerequisite derivation fails and this postcondition
     * cannot be meaningfully evaluated.
     *
     * @param reason the reason for skipping
     * @return a skipped result
     */
    public PostconditionResult skip(String reason) {
        return new PostconditionResult.Skipped(description, reason);
    }
}
