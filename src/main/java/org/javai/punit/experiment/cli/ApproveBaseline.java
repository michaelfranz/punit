package org.javai.punit.experiment.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.javai.punit.experiment.engine.BaselineLoader;
import org.javai.punit.experiment.engine.SpecificationGenerator;
import org.javai.punit.experiment.engine.SpecificationWriter;
import org.javai.punit.experiment.model.EmpiricalBaseline;
import org.javai.punit.experiment.model.ExecutionSpecification;

/**
 * CLI tool for approving baselines and generating specifications.
 *
 * <p>This tool reads baseline files from the pending-approval directory,
 * generates specifications, and writes them to the specs directory.
 *
 * <h2>Usage</h2>
 * <pre>
 * java ApproveBaseline [options]
 *
 * Options:
 *   --baseline=&lt;path&gt;     Path to specific baseline file (or directory for all)
 *   --output=&lt;path&gt;       Output directory for specs (default: src/test/resources/punit/specs)
 *   --approver=&lt;name&gt;     Name of approver (default: current user)
 *   --notes=&lt;text&gt;        Approval notes
 *   --dry-run             Show what would be done without writing files
 * </pre>
 */
public final class ApproveBaseline {

    private static final String DEFAULT_PENDING_DIR = "punit/pending-approval";
    private static final String DEFAULT_SPECS_DIR = "src/test/resources/punit/specs";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    static void run(String[] args) throws IOException {
        // Parse arguments
        String baselinePath = null;
        String outputDir = DEFAULT_SPECS_DIR;
        String approver = System.getProperty("user.name", "unknown");
        String notes = "";
        boolean dryRun = false;

        for (String arg : args) {
            if (arg.startsWith("--baseline=")) {
                baselinePath = arg.substring("--baseline=".length());
            } else if (arg.startsWith("--output=")) {
                outputDir = arg.substring("--output=".length());
            } else if (arg.startsWith("--approver=")) {
                approver = arg.substring("--approver=".length());
            } else if (arg.startsWith("--notes=")) {
                notes = arg.substring("--notes=".length());
            } else if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                return;
            }
        }

        // Resolve baseline path
        Path baselineDir = baselinePath != null
                ? Paths.get(baselinePath)
                : Paths.get(DEFAULT_PENDING_DIR);

        if (!Files.exists(baselineDir)) {
            System.err.println("Baseline path does not exist: " + baselineDir);
            System.exit(1);
        }

        // Find baseline files
        List<Path> baselineFiles;
        if (Files.isDirectory(baselineDir)) {
            baselineFiles = Files.list(baselineDir)
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .filter(p -> !p.getFileName().toString().startsWith("README"))
                    .collect(Collectors.toList());
        } else {
            baselineFiles = List.of(baselineDir);
        }

        if (baselineFiles.isEmpty()) {
            System.out.println("No baseline files found in: " + baselineDir);
            return;
        }

        System.out.println("Found " + baselineFiles.size() + " baseline file(s) to approve:");
        for (Path file : baselineFiles) {
            System.out.println("  - " + file.getFileName());
        }
        System.out.println();

        // Process each baseline
        Path specsDir = Paths.get(outputDir);
        int successCount = 0;
        int errorCount = 0;

        for (Path baselineFile : baselineFiles) {
            try {
                processBaseline(baselineFile, specsDir, approver, notes, dryRun);
                successCount++;
            } catch (Exception e) {
                System.err.println("Failed to process " + baselineFile.getFileName() + ": " + e.getMessage());
                errorCount++;
            }
        }

        System.out.println();
        System.out.println("Summary:");
        System.out.println("  Approved: " + successCount);
        System.out.println("  Errors: " + errorCount);

        if (!dryRun && successCount > 0) {
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("  1. Review generated specs in: " + specsDir);
            System.out.println("  2. Commit specs to version control");
            System.out.println("  3. Remove approved baselines from: " + DEFAULT_PENDING_DIR);
        }
    }

    private static final DateTimeFormatter ARCHIVE_TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static void processBaseline(Path baselineFile, Path specsDir, String approver,
                                         String notes, boolean dryRun) throws IOException {
        System.out.println("Processing: " + baselineFile.getFileName());

        // Load baseline
        EmpiricalBaseline baseline = BaselineLoader.load(baselineFile);
        System.out.println("  Use case: " + baseline.getUseCaseId());
        System.out.println("  Experiment: " + baseline.getExperimentId());
        System.out.println("  Samples: " + baseline.getExecution().samplesExecuted());
        System.out.println("  Observed success rate: " +
                String.format("%.2f%%", baseline.getStatistics().observedSuccessRate() * 100));

        // Generate specification
        ExecutionSpecification spec = SpecificationGenerator.generate(baseline, approver, notes);

        // Determine output path: specs/{useCaseId}.yaml (flat structure)
        String useCaseId = baseline.getUseCaseId().replace('.', '-');
        Path specPath = specsDir.resolve(useCaseId + ".yaml");
        
        // Create specs directory if needed
        Files.createDirectories(specsDir);

        // Archive existing spec if present
        if (Files.exists(specPath)) {
            archiveExistingSpec(specsDir, useCaseId, specPath, dryRun);
        }

        System.out.println("  Min success rate (threshold): " +
                String.format("%.2f%%", spec.getThresholds().getMinSuccessRate() * 100));
        System.out.println("  Output: " + specPath);

        if (dryRun) {
            System.out.println("  [DRY RUN] Would write spec to: " + specPath);
        } else {
            SpecificationWriter.write(spec, specPath);
            System.out.println("  ✓ Spec written successfully");
        }
    }

    private static void archiveExistingSpec(Path specsDir, String useCaseId, Path specPath, 
            boolean dryRun) throws IOException {
        Path archiveDir = specsDir.resolve("archive");
        String timestamp = LocalDateTime.now().format(ARCHIVE_TIMESTAMP_FORMAT);
        Path archivePath = archiveDir.resolve(useCaseId + "-" + timestamp + ".yaml");

        if (dryRun) {
            System.out.println("  [DRY RUN] Would archive existing spec to: " + archivePath);
        } else {
            Files.createDirectories(archiveDir);
            Files.copy(specPath, archivePath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  Archived existing spec to: " + archivePath);
        }
    }

    private static void printHelp() {
        System.out.println("PUnit Baseline Approval Tool");
        System.out.println();
        System.out.println("Usage: punitApprove [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --baseline=<path>  Path to baseline file or directory");
        System.out.println("                     Default: " + DEFAULT_PENDING_DIR);
        System.out.println("  --output=<path>    Output directory for specs");
        System.out.println("                     Default: " + DEFAULT_SPECS_DIR);
        System.out.println("  --approver=<name>  Name of approver (default: current user)");
        System.out.println("  --notes=<text>     Approval notes");
        System.out.println("  --dry-run          Show what would be done without writing");
        System.out.println("  --help, -h         Show this help message");
    }
}

