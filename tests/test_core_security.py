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

    def test_command_allowlist_rejects_network_and_mutating_tools(self):
        self.assertFalse(security.is_safe("curl https://example.com")[0])
        self.assertFalse(security.is_safe("systemctl restart ssh")[0])
        self.assertFalse(security.is_safe("find /tmp -delete")[0])
        self.assertTrue(security.is_safe("uptime")[0])


if __name__ == "__main__":
    unittest.main()
