# NEXORA VPN — v0.4 experimental SSH/Proxy

This branch is isolated from the working WireGuard v0.3.4.

## What works in this implementation
- SSH2 connection to a user-provided **authorized** SSH server using host, port, username, password.
- SHA256 host key pinning. If no fingerprint is provided, the server key can be observed, but authentication is **rejected** until it is independently verified.
- Local SOCKS5 TCP CONNECT server bound to \`127.0.0.1\` with an ephemeral port. A JSch direct-tcpip SSH channel carries each proxied connection; no cleartext proxy credentials or remote credentials are bundled.
- Test button performing an HTTPS request for the public IP via the local SOCKS5 endpoint.
- WireGuard flow, profile encryption, and diagnostics from v0.3.4 untouched.

## Still NOT implemented in v0.4
- Does **not** run an Android-wide SSH VPN (needs a separately licensed tun2socks Android VpnService integration with correct protected sockets and IPv4/IPv6 DNS handling).
- Does **not** import Rev Hunter's private BR02 or FLARE settings, and does not offer unauthorized access to another operator's infrastructure.
- Does **not** bypass Rev Hunter advertising, its time quota or any other third-party authorization.
- Does **not** magically provide mobile data on a chip without an accessible authorized endpoint.
- No SSH session persistence after Android kills the process (test engine, foreground operation only).
- No fake rewarded ad provider or fabricated rewards.

## Next steps
1. Test SSH login using an authorized account and server-published SHA256 host-key fingerprint (OpenSSH \`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256\` can be used by an administrator).
2. Confirm the SOCKS5 HTTPS IP test on cellular Internet and Wi-Fi.
3. Only then integrate a distinct full Android \`VpnService\` and a vetted tun2socks library, with proper VPN loop prevention, DNS, UDP policy, foreground service, and cleanup.
4. Real rewarded ads in **our app**, if desired, require an ad provider callback and our own entitlement service; a client-side timer alone is not sufficient for accountable rewards.

## References / third party licenses
- mwiede/JSch (SSH2, BSD-style license): https://github.com/mwiede/jsch
- Bouncy Castle (security provider, MIT-style): https://www.bouncycastle.org/
- Candidate future tun2socks: hev-socks5-tunnel (MIT): https://github.com/heiher/hev-socks5-tunnel

Do not publish a build asserting that the current SSH test routes WhatsApp/Telegram; it does not.
