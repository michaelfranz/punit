package org.javai.punit.examples.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.javai.outcome.Outcome;
import org.javai.punit.examples.infrastructure.llm.ChatResponse;

/**
 * Validates and parses LLM responses into {@link ShoppingAction} instances.
 *
 * <p>This validator attempts to deserialize JSON content into shopping actions,
 * capturing validation failures as {@link Outcome} results rather than exceptions.
 */
class ShoppingActionValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The result of validating an LLM response.
     *
     * @param actions the parsed actions (empty if validation failed)
     */
    record ValidationResult(List<ShoppingAction> actions) {
        static ValidationResult of(List<ShoppingAction> actions) {
            return new ValidationResult(List.copyOf(actions));
        }

        static ValidationResult of(ShoppingAction action) {
            return new ValidationResult(List.of(action));
        }
    }

    /**
     * Parses and validates a chat response as shopping actions.
     *
     * <p>Accepts either:
     * <ul>
     *   <li>A single action object: {@code {"context": "SHOP", "name": "add", ...}}</li>
     *   <li>An array of actions: {@code [{"context": "SHOP", ...}, ...]}</li>
     * </ul>
     *
     * @param response the chat response containing JSON content
     * @return an outcome containing the validation result, or a failure with details
     */
    static Outcome<ValidationResult> validate(ChatResponse response) {
        String json = response.content();
        if (json == null || json.isBlank()) {
            return Outcome.fail("validation", "Response content is null or blank");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return Outcome.fail("validation", "Invalid JSON: " + e.getMessage());
        }

        return parseActions(root);
    }

    private static Outcome<ValidationResult> parseActions(JsonNode root) {
        if (root.isArray()) {
            return parseActionArray(root);
        } else if (root.isObject()) {
            return parseSingleAction(root);
        } else {
            return Outcome.fail("validation", "Expected JSON object or array, got: " + root.getNodeType());
        }
    }

    private static Outcome<ValidationResult> parseSingleAction(JsonNode node) {
        try {
            ShoppingAction action = MAPPER.treeToValue(node, ShoppingAction.class);
            return Outcome.ok(ValidationResult.of(action));
        } catch (JsonProcessingException e) {
            return Outcome.fail("validation", "Failed to parse action: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Thrown by ShoppingAction compact constructor for invalid action names
            return Outcome.fail("validation", e.getMessage());
        }
    }

    private static Outcome<ValidationResult> parseActionArray(JsonNode arrayNode) {
        List<ShoppingAction> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        int index = 0;
        for (JsonNode node : arrayNode) {
            try {
                ShoppingAction action = MAPPER.treeToValue(node, ShoppingAction.class);
                actions.add(action);
            } catch (JsonProcessingException e) {
                errors.add("Action[%d]: %s".formatted(index, e.getMessage()));
            } catch (IllegalArgumentException e) {
                errors.add("Action[%d]: %s".formatted(index, e.getMessage()));
            }
            index++;
        }

        if (!errors.isEmpty()) {
            return Outcome.fail("validation", String.join("; ", errors));
        }

        if (actions.isEmpty()) {
            return Outcome.fail("validation", "Empty actions array");
        }

        return Outcome.ok(ValidationResult.of(actions));
    }
}
