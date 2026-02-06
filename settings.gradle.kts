rootProject.name = "punit"

// ═══════════════════════════════════════════════════════════════════════════
// Local sibling project support
// ═══════════════════════════════════════════════════════════════════════════
// If the outcome library exists as a sibling folder (../outcome), use it directly
// for faster development iteration. Otherwise, fall back to the published artifact.

val outcomeDir = file("../outcome")
if (outcomeDir.exists() && file("$outcomeDir/build.gradle.kts").exists()) {
    includeBuild(outcomeDir) {
        dependencySubstitution {
            substitute(module("org.javai:outcome")).using(project(":"))
        }
    }
    println("Using local outcome library from: ${outcomeDir.absolutePath}")
}
