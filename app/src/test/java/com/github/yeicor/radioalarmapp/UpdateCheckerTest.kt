package com.github.yeicor.radioalarmapp

import com.github.yeicor.radioalarmapp.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun isTagNewer_higherPatch_returnsTrue() {
        assertTrue(UpdateChecker.isTagNewer("v0.0.6", "0.0.5"))
        assertTrue(UpdateChecker.isTagNewer("0.0.6", "0.0.5"))
    }

    @Test
    fun isTagNewer_higherMinor_returnsTrue() {
        assertTrue(UpdateChecker.isTagNewer("v0.1.0", "0.0.5"))
        assertTrue(UpdateChecker.isTagNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun isTagNewer_sameVersion_returnsFalse() {
        assertFalse(UpdateChecker.isTagNewer("v0.0.5", "0.0.5"))
        assertFalse(UpdateChecker.isTagNewer("0.0.5", "0.0.5"))
    }

    @Test
    fun isTagNewer_olderVersion_returnsFalse() {
        assertFalse(UpdateChecker.isTagNewer("v0.0.4", "0.0.5"))
        assertFalse(UpdateChecker.isTagNewer("0.0.1", "1.0.0"))
    }

    @Test
    fun isNightlyNewer_sameCommitSha_returnsFalse() {
        // When jumping from stable to nightly on the same release commit
        val isNewer = UpdateChecker.isNightlyNewer(
            artifactCreatedAtMs = 1700000100000L,
            artifactHeadSha = "abc123def",
            localBuildTimeMs = 1700000000000L,
            localGitCommitSha = "abc123def",
            latestStablePublishedAtMs = 1700000050000L,
            localVersionName = "0.0.6",
            latestStableTag = "v0.0.6",
        )
        assertFalse(isNewer)
    }

    @Test
    fun isNightlyNewer_sameReleasedVersionWindow_returnsFalse() {
        // Even if commit SHA is unknown or different, if artifact was created within 5 min of latest stable release
        // and user is running that stable version, don't detect same release as nightly update
        val releaseTime = 1700000000000L
        val artifactTime = releaseTime + 90_000L // 90 seconds after release
        val isNewer = UpdateChecker.isNightlyNewer(
            artifactCreatedAtMs = artifactTime,
            artifactHeadSha = "head1",
            localBuildTimeMs = releaseTime + 120_000L,
            localGitCommitSha = "head2",
            latestStablePublishedAtMs = releaseTime,
            localVersionName = "0.0.6",
            latestStableTag = "v0.0.6",
        )
        assertFalse(isNewer)
    }

    @Test
    fun isNightlyNewer_newCommitLaterDate_returnsTrue() {
        val releaseTime = 1700000000000L
        val laterArtifactTime = releaseTime + 24 * 3600 * 1000L // 1 day later
        val isNewer = UpdateChecker.isNightlyNewer(
            artifactCreatedAtMs = laterArtifactTime,
            artifactHeadSha = "newsha456",
            localBuildTimeMs = releaseTime,
            localGitCommitSha = "oldsha123",
            latestStablePublishedAtMs = releaseTime,
            localVersionName = "0.0.6",
            latestStableTag = "v0.0.6",
        )
        assertTrue(isNewer)
    }

    @Test
    fun isStableNewer_jumpingFromNewerNightlyToOlderStable_returnsFalse() {
        // User is running a nightly build built on 2026-11-17
        val nightlyBuildTime = 1700000000000L + 24 * 3600 * 1000L
        // Stable release was published on 2026-11-16
        val stablePublishedAt = 1700000000000L

        val isNewer = UpdateChecker.isStableNewer(
            releaseTag = "v0.0.6",
            releasePublishedAtMs = stablePublishedAt,
            localVersionName = "0.0.6",
            localBuildTimeMs = nightlyBuildTime,
        )
        assertFalse(isNewer)
    }

    @Test
    fun isStableNewer_newReleasePublishedAfterNightly_returnsTrue() {
        // User is running a nightly build built on 2026-11-17
        val nightlyBuildTime = 1700000000000L
        // A new stable v0.0.7 is published on 2026-11-20
        val stablePublishedAt = 1700000000000L + 3 * 24 * 3600 * 1000L

        val isNewer = UpdateChecker.isStableNewer(
            releaseTag = "v0.0.7",
            releasePublishedAtMs = stablePublishedAt,
            localVersionName = "0.0.6",
            localBuildTimeMs = nightlyBuildTime,
        )
        assertTrue(isNewer)
    }
}
