rootProject.name = "punit"

// Include the outcome library from the local filesystem when available (sibling folder).
// On CI, this folder won't exist and Gradle resolves outcome from JitPack instead.
val outcomeDir = file("../outcome")
if (outcomeDir.isDirectory) {
    includeBuild(outcomeDir) {
        dependencySubstitution {
            substitute(module("com.github.javai-org:outcome")).using(project(":"))
        }
    }
}
