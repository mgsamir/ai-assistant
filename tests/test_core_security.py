import os
import tempfile
import unittest
from unittest.mock import patch

from core import security


class UrlSafetyTests(unittest.TestCase):
    def test_rejects_non_http_schemes_and_userinfo(self):
        self.assertFalse(security.is_safe_url("file:///etc/passwd")[0])
        self.assertFalse(security.is_safe_url("https://user:pass@example.com")[0])

    def test_rejects_private_literal_addresses(self):
        for host in ("127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.169.254", "[::1]"):
            with self.subTest(host=host):
                self.assertFalse(security.is_safe_url(f"http://{host}/")[0])

    @patch("core.security.socket.getaddrinfo")
    def test_rejects_domain_resolving_to_private_ip(self, getaddrinfo):
        getaddrinfo.return_value = [(2, 1, 6, "", ("10.1.2.3", 443))]
        self.assertFalse(security.is_safe_url("https://example.test")[0])

    @patch("core.security.socket.getaddrinfo")
    def test_allows_domain_resolving_to_public_ip(self, getaddrinfo):
        getaddrinfo.return_value = [(2, 1, 6, "", ("93.184.216.34", 443))]
        self.assertTrue(security.is_safe_url("https://example.test")[0])


class CommandSafetyTests(unittest.TestCase):
    def test_safe_options_with_spaces_are_allowed(self):
        for command in ("ls -la", "df -h", "ps aux"):
            with self.subTest(command=command):
                self.assertTrue(security.is_safe(command)[0], security.is_safe(command)[1])

    def test_shell_metacharacters_still_blocked(self):
        for command in ("ls; whoami", "cat /tmp/file | head", "ls > /tmp/out", "ls < /tmp/file"):
            with self.subTest(command=command):
                self.assertFalse(security.is_safe(command)[0])

    def test_non_path_command_allowlist_and_find_mutations(self):
        self.assertFalse(security.is_safe("curl https://example.com")[0])
        self.assertFalse(security.is_safe("systemctl restart ssh")[0])
        self.assertFalse(security.is_safe("find /tmp -delete")[0])
        self.assertTrue(security.is_safe("uptime")[0])

    def test_approved_roots_are_allowed_for_all_path_commands(self):
        commands = (
            "cat /tmp/file", "head -n 2 /var/log/syslog", "tail -n 2 /tmp/file",
            "grep needle /tmp/file", "ls -la /tmp", "du -h /tmp", "stat /tmp/file",
            "file /tmp/file", "wc -l /tmp/file", "find /tmp -name '*.log'",
        )
        for command in commands:
            with self.subTest(command=command):
                self.assertTrue(security.is_safe(command)[0], security.is_safe(command)[1])

    def test_sensitive_or_out_of_root_paths_are_blocked(self):
        forbidden = (
            "cat /etc/shadow", "cat ~/.ssh/id_rsa", "cat " + os.path.join(security.PROJECT_ROOT, ".env"),
            "head " + os.path.join(security.PROJECT_ROOT, "server.pem"),
            "grep token /home/user/private.key", "ls /etc",
        )
        for command in forbidden:
            with self.subTest(command=command):
                self.assertFalse(security.is_safe(command)[0])

    def test_grep_pattern_is_not_mistaken_for_a_path(self):
        self.assertTrue(security.is_safe("grep -n needle /tmp/file")[0])
        self.assertTrue(security.is_safe("grep -e needle -f /tmp/patterns /var/log/syslog")[0])
        self.assertFalse(security.is_safe("grep -f /etc/shadow needle /tmp/file")[0])

    def test_symlink_cannot_escape_an_allowed_root(self):
        with tempfile.TemporaryDirectory(dir="/tmp") as temp_dir:
            link = os.path.join(temp_dir, "outside")
            try:
                os.symlink("/etc/shadow", link)
            except (OSError, NotImplementedError):
                self.skipTest("Symlinks are unavailable")
            self.assertFalse(security.is_safe(f"cat {link}")[0])


if __name__ == "__main__":
    unittest.main()
