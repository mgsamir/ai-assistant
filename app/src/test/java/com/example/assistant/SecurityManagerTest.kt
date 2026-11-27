package com.example.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityManagerTest {
    @Test fun destructiveAndShellInputsAreBlocked() {
        listOf("rm -rf /", "sudo id", "mkfs.ext4 /dev/sda", "dd if=/dev/zero of=/dev/sda", "nmap 8.8.8.8", "cat /etc/shadow", "ls; rm x").forEach {
            assertEquals("$it must be blocked", SecurityManager.Level.BLOCKED, SecurityManager.decide(it).level)
        }
    }
    @Test fun safeReadOnlyCommandsAreAllowed() {
        assertEquals(SecurityManager.Level.SAFE, SecurityManager.decide("ss -tln").level)
        assertEquals(SecurityManager.Level.SAFE, SecurityManager.decide("uptime").level)
        assertEquals(SecurityManager.Level.SAFE, SecurityManager.decide("battery").level)
    }
    @Test fun localDownloadPathAllowedAndSensitivePathsRejected() {
        assertTrue(SecurityManager.isAllowedPath("/sdcard/Download", listOf("/sdcard/Download")))
        assertFalse(SecurityManager.isAllowedPath("/etc/shadow", listOf("/sdcard/Download")))
        assertFalse(SecurityManager.isAllowedPath("/home/user/.ssh/id_rsa", listOf("/home/user")))
    }
}
