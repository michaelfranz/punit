package org.javai.punit.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.javai.punit.api.CovariateCategory;
import org.javai.punit.api.StandardCovariate;

/**
 * The set of covariates declared by a use case.
 *
 * <p>A covariate declaration captures which covariates are relevant for a use case,
 * both standard (from {@link StandardCovariate}) and custom (user-defined strings),
 * along with their categories.
 *
 * <p>The declaration is used for:
 * <ul>
 *   <li>Footprint computation (covariate names contribute to the footprint)</li>
 *   <li>Covariate resolution (which covariates to capture during experiments)</li>
 *   <li>Baseline selection (matching declarations must match)</li>
 *   <li>Category-aware matching (CONFIGURATION = hard gate, others = soft match)</li>
 * </ul>
 *
 * @param standardCovariates the standard covariates in declaration order
 * @param customCovariates the custom covariate keys in declaration order (legacy, treated as INFRASTRUCTURE)
 * @param categorizedCovariates map of custom covariate key to category
 */
public record CovariateDeclaration(
        List<StandardCovariate> standardCovariates,
        List<String> customCovariates,
        Map<String, CovariateCategory> categorizedCovariates
) {

    /** An empty covariate declaration. */
    public static final CovariateDeclaration EMPTY = new CovariateDeclaration(List.of(), List.of(), Map.of());

    public CovariateDeclaration {
        standardCovariates = List.copyOf(standardCovariates);
        customCovariates = List.copyOf(customCovariates);
        categorizedCovariates = Map.copyOf(categorizedCovariates);
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public CovariateDeclaration(List<StandardCovariate> standardCovariates, List<String> customCovariates) {
        this(standardCovariates, customCovariates, Map.of());
    }

    /**
     * Returns all covariate keys in declaration order (standard first, then legacy custom, then categorized).
     *
     * @return list of all covariate keys
     */
    public List<String> allKeys() {
        var keys = new ArrayList<String>();
        standardCovariates.forEach(sc -> keys.add(sc.key()));
        keys.addAll(customCovariates);
        keys.addAll(categorizedCovariates.keySet());
        return keys;
    }

    /**
     * Computes a stable hash of the covariate declaration (names only).
     *
     * <p>This hash contributes to the invocation footprint. It ensures that
     * baselines are only matched to tests with identical covariate declarations.
     *
     * @return 8-character hex hash, or empty string if no covariates declared
     */
    public String computeDeclarationHash() {
        if (isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (String key : allKeys()) {
            sb.append(key).append("\n");
        }

        return truncateHash(sha256(sb.toString()));
    }

    /**
     * Returns true if no covariates are declared.
     *
     * @return true if all covariate collections are empty
     */
    public boolean isEmpty() {
        return standardCovariates.isEmpty() && customCovariates.isEmpty() && categorizedCovariates.isEmpty();
    }

    /**
     * Returns the total number of declared covariates.
     *
     * @return count of all covariates
     */
    public int size() {
        return standardCovariates.size() + customCovariates.size() + categorizedCovariates.size();
    }

    /**
     * Returns the category for a covariate key.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Standard covariate category (from enum)</li>
     *   <li>Categorized covariate map</li>
     *   <li>Legacy custom covariate → INFRASTRUCTURE (default)</li>
     * </ol>
     *
     * @param key the covariate key
     * @return the category, or INFRASTRUCTURE if not found
     */
    public CovariateCategory getCategory(String key) {
        // Check standard covariates
        for (StandardCovariate sc : standardCovariates) {
            if (sc.key().equals(key)) {
                return sc.category();
            }
        }
        
        // Check categorized custom covariates
        if (categorizedCovariates.containsKey(key)) {
            return categorizedCovariates.get(key);
        }
        
        // Legacy custom covariates default to INFRASTRUCTURE
        if (customCovariates.contains(key)) {
            return CovariateCategory.INFRASTRUCTURE;
        }
        
        // Unknown covariate - default to INFRASTRUCTURE
        return CovariateCategory.INFRASTRUCTURE;
    }

    /**
     * Returns all custom covariate keys (both legacy and categorized).
     *
     * @return combined list of custom keys
     */
    public List<String> allCustomKeys() {
        var keys = new ArrayList<>(customCovariates);
        keys.addAll(categorizedCovariates.keySet());
        return keys;
    }

    /**
     * Creates a declaration from arrays (convenience for annotation processing).
     *
     * @param standard array of standard covariates
     * @param custom array of custom covariate keys (legacy, treated as INFRASTRUCTURE)
     * @return the covariate declaration
     */
    public static CovariateDeclaration of(StandardCovariate[] standard, String[] custom) {
        Objects.requireNonNull(standard, "standard must not be null");
        Objects.requireNonNull(custom, "custom must not be null");
        
        if (standard.length == 0 && custom.length == 0) {
            return EMPTY;
        }
        
        return new CovariateDeclaration(List.of(standard), List.of(custom), Map.of());
    }

    /**
     * Creates a declaration with categorized custom covariates.
     *
     * @param standard array of standard covariates
     * @param legacyCustom array of legacy custom covariate keys
     * @param categorized map of custom key to category
     * @return the covariate declaration
     */
    public static CovariateDeclaration of(
            StandardCovariate[] standard, 
            String[] legacyCustom,
            Map<String, CovariateCategory> categorized) {
        Objects.requireNonNull(standard, "standard must not be null");
        Objects.requireNonNull(legacyCustom, "legacyCustom must not be null");
        Objects.requireNonNull(categorized, "categorized must not be null");
        
        if (standard.length == 0 && legacyCustom.length == 0 && categorized.isEmpty()) {
            return EMPTY;
        }
        
        return new CovariateDeclaration(List.of(standard), List.of(legacyCustom), categorized);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String truncateHash(String hash) {
        return hash.substring(0, Math.min(8, hash.length()));
    }
}

