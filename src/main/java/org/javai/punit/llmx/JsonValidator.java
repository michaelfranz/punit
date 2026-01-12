package org.javai.punit.llmx;

import java.util.regex.Pattern;

/**
 * Lightweight JSON validation utilities for LLM response evaluation.
 *
 * <p>This class provides basic JSON validation without requiring external
 * JSON parsing libraries. For more sophisticated validation, consider
 * using a full JSON parser like Jackson or Gson.
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Uses regex-based validation (not a full parser)</li>
 *   <li>May have false positives for complex nested structures</li>
 *   <li>For production use with complex schemas, use a proper JSON library</li>
 * </ul>
 */
public final class JsonValidator {

    private JsonValidator() {}

    // Simple pattern to detect if string starts/ends like JSON
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("^\\s*\\{.*\\}\\s*$", Pattern.DOTALL);
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("^\\s*\\[.*\\]\\s*$", Pattern.DOTALL);

    /**
     * Checks if the given string appears to be valid JSON.
     *
     * <p>This is a lightweight check that verifies basic JSON structure
     * (starts with { or [ and ends with } or ]) and attempts to validate
     * balanced brackets.
     *
     * @param content the string to check
     * @return true if the content appears to be valid JSON
     */
    public static boolean isValid(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        
        String trimmed = content.trim();
        
        // Check if it looks like JSON (object or array)
        boolean isObject = JSON_OBJECT_PATTERN.matcher(trimmed).matches();
        boolean isArray = JSON_ARRAY_PATTERN.matcher(trimmed).matches();
        
        if (!isObject && !isArray) {
            return false;
        }
        
        // Basic bracket balance check
        return areBracketsBalanced(trimmed);
    }

    /**
     * Attempts to parse JSON content.
     *
     * <p>This is a simple structural parse that returns a placeholder object
     * if the JSON is valid. For actual JSON parsing, use a proper library.
     *
     * @param content the JSON string to parse
     * @return a non-null object if parsing succeeds, null otherwise
     */
    public static Object parse(String content) {
        if (isValid(content)) {
            // Return a marker object indicating successful parse
            return new ParsedJsonMarker(content);
        }
        return null;
    }

    /**
     * Checks if the JSON content contains all specified field names.
     *
     * <p>This uses a simple string search for field names in JSON object format.
     * For reliable field detection in production, use a proper JSON parser.
     *
     * @param content the JSON string
     * @param fields the field names to check for
     * @return true if all fields appear to be present
     */
    public static boolean hasFields(String content, String... fields) {
        if (content == null || content.isBlank()) {
            return false;
        }
        
        for (String field : fields) {
            // Look for "fieldName": pattern
            String pattern = "\"" + field + "\"\\s*:";
            if (!Pattern.compile(pattern).matcher(content).find()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts a string value for a given field name (simple extraction).
     *
     * <p>This is a basic extraction that works for simple string values.
     * For complex extractions, use a proper JSON parser.
     *
     * @param content the JSON string
     * @param fieldName the field to extract
     * @return the string value, or null if not found
     */
    public static String extractStringField(String content, String fieldName) {
        if (content == null) {
            return null;
        }
        
        // Pattern: "fieldName": "value" or "fieldName":"value"
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*?)\"";
        java.util.regex.Matcher matcher = Pattern.compile(pattern).matcher(content);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Checks if brackets/braces are balanced in the string.
     */
    private static boolean areBracketsBalanced(String content) {
        int curlyBalance = 0;
        int squareBalance = 0;
        boolean inString = false;
        char prevChar = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            // Handle string boundaries
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }
            
            if (!inString) {
                switch (c) {
                    case '{' -> curlyBalance++;
                    case '}' -> curlyBalance--;
                    case '[' -> squareBalance++;
                    case ']' -> squareBalance--;
                }
                
                // Early exit on imbalance
                if (curlyBalance < 0 || squareBalance < 0) {
                    return false;
                }
            }
            
            prevChar = c;
        }
        
        return curlyBalance == 0 && squareBalance == 0 && !inString;
    }

    /**
     * Marker class indicating successful JSON parse.
     */
    static final class ParsedJsonMarker {
        private final String content;
        
        ParsedJsonMarker(String content) {
            this.content = content;
        }
        
        public String getContent() {
            return content;
        }
        
        @Override
        public String toString() {
            return "ParsedJson[" + content.length() + " chars]";
        }
    }
}

