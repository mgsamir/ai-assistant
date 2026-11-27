package com.example.assistant

/** JVM-friendly policy checks; invoke from a local test harness without Android APIs. */
object SecurityManagerTest {
    @JvmStatic fun main(args: Array<String>) {
        check(SecurityManager.decide("rm -rf /").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("sudo id").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("mkfs.ext4 /dev/sda").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("dd if=/dev/zero of=/dev/sda").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("nmap 8.8.8.8").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("cat /etc/shadow").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("ls; rm x").level == SecurityManager.Level.BLOCKED)
        check(SecurityManager.decide("ss -tln").level == SecurityManager.Level.SAFE)
        check(SecurityManager.decide("uptime").level == SecurityManager.Level.SAFE)
    }
}
