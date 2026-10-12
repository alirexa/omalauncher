package dev.omalauncher.linux

import dev.omalauncher.upgrade.StepJournal
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsTrustTest {
    @get:Rule val temp = TemporaryFolder()
    private val record get() = File(temp.root, "steps.json")
    @Test fun freshInstallMayProceedToVerification() = requireVerifiedRootfs(StepJournal(record))
    @Test fun resumedUnverifiedImageIsBlockedAfterRestart() {
        StepJournal(record).mark("rootfs")
        assertThrows(Exception::class.java) { requireVerifiedRootfs(StepJournal(record)) }
    }
    @Test fun completeUnverifiedInstallIsBlockedEvenWithoutRootfsStep() {
        StepJournal(record).completeSetup()
        assertThrows(Exception::class.java) { requireVerifiedRootfs(StepJournal(record)) }
    }
    @Test fun verifiedInstallMayResumeAfterRestart() {
        StepJournal(record).apply { mark("rootfs-signature-1"); mark("rootfs"); completeSetup() }
        requireVerifiedRootfs(StepJournal(record))
    }
}
