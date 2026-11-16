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
}
