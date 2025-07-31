#!/usr/bin/env kotlin

import java.util.concurrent.TimeUnit

val userDefinedVersion = args[0].toSemver()
val mainBranch = args.getOrElse(1) { "master" }

fun runCommand(command: String): String {
    val process = ProcessBuilder("/bin/sh", "-c", command)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    process.waitFor(10, TimeUnit.SECONDS)
    return process.inputStream.bufferedReader().readText().trim()
}

data class Semver(val major: Int, val minor: Int, val patch: Int, val branchVersion: Int? = null) {
    fun majorMinorMatches(other: Semver) = major == other.major && minor == other.minor
}

fun String.toSemver(): Semver {
    val split = this.removePrefix("v").substringBefore("-").split('.').map { it.toIntOrNull() }
    // ex. in v1.0.0-branchName.5, branchVersion is 5
    val branchVersion = this.substringAfterLast('-', "").substringAfterLast(".", "").toIntOrNull()
    return Semver(split.getOrNull(0) ?: 0, split.getOrNull(1) ?: 0, split.getOrNull(2) ?: 0, branchVersion)
}

val EMPTY_SEMVER = Semver(0, 0, 0)

val currentBranch = runCommand("git rev-parse --abbrev-ref HEAD")
val shortenedBranchName = when(currentBranch) {
    "develop" -> "dev"
    else -> currentBranch
}

// Latest tag on main branch
val tagOnMain = runCommand("git describe --tags --abbrev=0 origin/$mainBranch --match 'v*' --exclude '*-*'").toSemver()
    // Fallback to main branch without remote
    .takeIf { it != EMPTY_SEMVER } ?:  runCommand("git describe --tags --abbrev=0 $mainBranch --match 'v*'  --exclude '*-*'").toSemver()

// Latest tag which includes this branch's name
val tagOnBranch =
    if (currentBranch == mainBranch) tagOnMain else runCommand("git describe --tags --abbrev=0 --match 'v*-$shortenedBranchName.*'").toSemver()

val newPatch = when {
    // When version bumped by user
    !userDefinedVersion.majorMinorMatches(tagOnBranch) -> when {
        // If main branch matches this new version, use its patch plus 1
        userDefinedVersion.majorMinorMatches(tagOnMain) -> tagOnMain.patch + 1
        // Otherwise set back to 0
        else -> 0
    }
    // Otherwise, if the user version matches main branch, use its patch + 1
    userDefinedVersion.majorMinorMatches(tagOnMain) -> tagOnMain.patch + 1
    // Otherwise, main likely had a bump but we didn't, so just keep the same patch
    else -> tagOnBranch.patch
}

val nextVersion = when {
    currentBranch != mainBranch -> {
        val branchVersion = when {
            // If user hasn't bumped the version, increase branch version by 1
            userDefinedVersion.majorMinorMatches(tagOnBranch) && newPatch == tagOnBranch.patch ->
                tagOnBranch.branchVersion?.plus(1) ?: 0
            // Otherwise reset version
            else -> 0
        }
        "v${userDefinedVersion.major}.${userDefinedVersion.minor}.$newPatch-${shortenedBranchName}.${branchVersion}"
    }

    else -> "v${userDefinedVersion.major}.${userDefinedVersion.minor}.$newPatch"
}


println(nextVersion)
