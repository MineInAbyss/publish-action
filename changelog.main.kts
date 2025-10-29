#!/usr/bin/env kotlin

import kotlin.io.path.Path
import kotlin.io.path.notExists
import kotlin.io.path.useLines
import kotlin.system.exitProcess

val changelogPath = Path("CHANGELOG.md")
val releaseVersion = args[0]
val headerPrefix = "## "
val releaseHeader = "$headerPrefix[${releaseVersion}]"
val unreleasedHeader = "$headerPrefix[Unreleased]"

if (changelogPath.notExists()) exitProcess(1)

val changelog = changelogPath.useLines {
    var count = 0
    it.dropWhile { line -> !line.startsWith(releaseHeader) && !line.startsWith(unreleasedHeader) } // Take only headers declaring version info
        .takeWhile { line ->  count++ == 0 || !line.startsWith(headerPrefix) } // Take until next version header
        .joinToString("\n")
}

if(changelog.isEmpty()) exitProcess(1)

println(changelog)