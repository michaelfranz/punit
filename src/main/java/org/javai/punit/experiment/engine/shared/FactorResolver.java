package org.javai.punit.experiment.engine.shared;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.javai.punit.api.Factor;
import org.javai.punit.api.FactorArguments;
import org.javai.punit.api.FactorSource;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Resolves factor information from annotations and method parameters.
 *
 * <p>Used by both MEASURE and EXPLORE modes for factor handling.
 */
public final class FactorResolver {

    private FactorResolver() {
        // Utility class
    }

    /**
     * Resolves factor arguments from the @FactorSource annotation.
     *
     * @param testMethod the test method
     * @param factorSource the @FactorSource annotation
     * @return list of factor arguments
     */
    @SuppressWarnings("unchecked")
    public static List<FactorArguments> resolveFactorArguments(
            Method testMethod, FactorSource factorSource) {

        String sourceReference = factorSource.value();

        try {
            Stream<FactorArguments> factorStream;

            if (sourceReference.contains("#")) {
                // Cross-class reference
                String[] parts = sourceReference.split("#", 2);
                String className = parts[0];
                String methodName = parts[1];
                Class<?> targetClass = resolveClass(className, testMethod.getDeclaringClass());
                Method sourceMethod = targetClass.getDeclaredMethod(methodName);
                factorStream = invokeFactorSource(sourceMethod, sourceReference);
            } else {
                // Same-class reference
                Method sourceMethod = testMethod.getDeclaringClass().getDeclaredMethod(sourceReference);
                factorStream = invokeFactorSource(sourceMethod, sourceReference);
            }

            return factorStream.toList();

        } catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException(
                    "Cannot find @FactorSource method '" + sourceReference + "'", e);
        } catch (Exception e) {
            throw new ExtensionConfigurationException(
                    "Cannot invoke @FactorSource method '" + sourceReference + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<FactorArguments> invokeFactorSource(Method sourceMethod, String sourceReference)
            throws Exception {
        sourceMethod.setAccessible(true);
        Object result = sourceMethod.invoke(null);

        if (result instanceof Stream) {
            return (Stream<FactorArguments>) result;
        } else if (result instanceof Collection) {
            return ((Collection<FactorArguments>) result).stream();
        } else {
            throw new ExtensionConfigurationException(
                    "Factor source method must return Stream or Collection: " + sourceReference);
        }
    }

    /**
     * Extracts factor info from FactorArguments and method parameters.
     *
     * @param testMethod the test method
     * @param firstArgs the first FactorArguments (for extracting names)
     * @return list of factor info
     */
    public static List<FactorInfo> extractFactorInfosFromArguments(
            Method testMethod, FactorArguments firstArgs) {

        List<FactorInfo> infos = new ArrayList<>();

        // Try to get names from FactorArguments
        String[] argNames = firstArgs.names();
        if (argNames != null) {
            for (int i = 0; i < argNames.length; i++) {
                infos.add(new FactorInfo(i, argNames[i], argNames[i], Object.class));
            }
        } else {
            // Fall back to @Factor annotations on method parameters
            int factorIndex = 0;
            for (Parameter param : testMethod.getParameters()) {
                Factor factor = param.getAnnotation(Factor.class);
                if (factor != null) {
                    infos.add(new FactorInfo(factorIndex++, factor.value(), factor.value(), param.getType()));
                }
            }
        }

        return infos;
    }

    /**
     * Extracts factor info from @FactorSource annotation and arguments.
     *
     * <p>Priority:
     * <ol>
     *   <li>Names embedded in {@link FactorArguments} (best DX - names with values)</li>
     *   <li>{@code @FactorSource(factors = {...})} annotation</li>
     *   <li>{@code @Factor} annotations on method parameters</li>
     * </ol>
     */
    public static List<FactorInfo> extractFactorInfos(
            Method method, FactorSource factorSource, List<FactorArguments> argsList) {

        // 1. Prefer names embedded in FactorArguments
        if (!argsList.isEmpty() && argsList.get(0).hasNames()) {
            String[] names = argsList.get(0).names();
            List<FactorInfo> factorInfos = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                factorInfos.add(new FactorInfo(i, names[i], names[i], Object.class));
            }
            return factorInfos;
        }

        // 2. Factor names from @FactorSource annotation
        String[] factorNames = factorSource.factors();
        if (factorNames.length > 0) {
            List<FactorInfo> factorInfos = new ArrayList<>();
            for (int i = 0; i < factorNames.length; i++) {
                factorInfos.add(new FactorInfo(i, factorNames[i], factorNames[i], Object.class));
            }
            return factorInfos;
        }

        // 3. Fall back to @Factor annotations on method parameters
        return getFactorInfosFromParameters(method);
    }

    /**
     * Extracts factor infos from @Factor-annotated method parameters.
     */
    public static List<FactorInfo> getFactorInfosFromParameters(Method method) {
        List<FactorInfo> factorInfos = new ArrayList<>();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Factor factor = parameters[i].getAnnotation(Factor.class);
            if (factor != null) {
                String name = factor.value();
                String filePrefix = factor.filePrefix().isEmpty() ? name : factor.filePrefix();
                factorInfos.add(new FactorInfo(i, name, filePrefix, parameters[i].getType()));
            }
        }
        return factorInfos;
    }

    /**
     * Extracts factor values in the order matching factorInfos.
     */
    public static Object[] extractFactorValues(FactorArguments args, List<FactorInfo> factorInfos) {
        Object[] values = new Object[factorInfos.size()];
        String[] argNames = args.names();

        for (int i = 0; i < factorInfos.size(); i++) {
            FactorInfo info = factorInfos.get(i);
            if (argNames != null) {
                // Find by name
                for (int j = 0; j < argNames.length; j++) {
                    if (argNames[j].equals(info.name())) {
                        values[i] = args.get(j);
                        break;
                    }
                }
            } else {
                // Use positional index
                values[i] = args.get(info.parameterIndex());
            }
        }

        return values;
    }

    /**
     * Builds a configuration name from factor values for file naming.
     */
    public static String buildConfigName(List<FactorInfo> factorInfos, Object[] values) {
        StringBuilder sb = new StringBuilder();

        int count = Math.min(factorInfos.size(), values.length);

        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append("_");
            FactorInfo info = factorInfos.get(i);
            Object value = values[i];
            String valueStr = formatFactorValue(value);
            sb.append(info.filePrefix()).append("-").append(valueStr);
        }

        // If there are more values than factorInfos, add generic names
        for (int i = count; i < values.length; i++) {
            if (!sb.isEmpty()) sb.append("_");
            String valueStr = formatFactorValue(values[i]);
            sb.append("f").append(i).append("-").append(valueStr);
        }

        return sb.toString();
    }

    /**
     * Formats a factor value for use in file names.
     */
    public static String formatFactorValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        return str.replace(" ", "_")
                .replace("/", "-")
                .replace("\\", "-")
                .replace(":", "-")
                .replaceAll("[^a-zA-Z0-9._-]", "");
    }

    /**
     * Resolves a class from a simple or fully qualified name.
     */
    public static Class<?> resolveClass(String className, Class<?> contextClass) {
        String packageName = contextClass.getPackageName();

        // 1. Try same package
        try {
            return Class.forName(packageName + "." + className);
        } catch (ClassNotFoundException ignored) {
        }

        // 2. Try fully qualified name
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
        }

        // 3. Try sibling packages
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPackage = packageName.substring(0, lastDot);

            for (String sibling : new String[]{"usecase", "model", "domain", "service", "api", "core"}) {
                try {
                    return Class.forName(parentPackage + "." + sibling + "." + className);
                } catch (ClassNotFoundException ignored) {
                }
            }

            try {
                return Class.forName(parentPackage + "." + className);
            } catch (ClassNotFoundException ignored) {
            }
        }

        throw new ExtensionConfigurationException(
                "Cannot resolve class '" + className + "' from context " + contextClass.getName() +
                        ". Try using the fully qualified class name.");
    }
}
