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

/**
 * Highest tag matching [pattern], ordered by version rather than by reachability from HEAD.
 *
 * git describe only sees tags that are ancestors of HEAD, so rebasing a branch orphans its own tags and the
 * version silently restarts at 0, republishing over a release that already exists.
 */
fun highestTag(pattern: String, releasesOnly: Boolean = false): Semver =
    runCommand("git tag --list '$pattern' --sort=-v:refname")
        .lineSequence()
        .filter { it.isNotBlank() }
        .filterNot { releasesOnly && '-' in it.removePrefix("v") }
        .firstOrNull()
        ?.toSemver() ?: EMPTY_SEMVER

// Latest release tag, ignoring prereleases
val tagOnMain = highestTag("v*", releasesOnly = true)

// Latest tag which includes this branch's name
val tagOnBranch =
    if (currentBranch == mainBranch) tagOnMain else highestTag("v*-$shortenedBranchName.*")

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
        "${userDefinedVersion.major}.${userDefinedVersion.minor}.$newPatch-${shortenedBranchName}.${branchVersion}"
    }

    else -> "${userDefinedVersion.major}.${userDefinedVersion.minor}.$newPatch"
}


println(nextVersion)
