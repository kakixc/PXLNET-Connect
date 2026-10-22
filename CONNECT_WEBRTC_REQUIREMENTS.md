# PXLNET Connect: WebRTC Whitelist Mode handoff (requirements, 2026-10-09)

This is the handoff for a **future, separately approved Connect implementation**, not a claim that the transport works. The owner selected **Connect-native WebRTC first** and required backend-enforced subscription access. This file changes no Android source, subscription format, engine, release, server, or production setting. No WebRTC/HAPP interoperability or whitelist egress has been proven.

Owner clarification 2026-10-09: the HAPP product path must work **without installing another app**, and the project must not buy additional servers/domains/paid infrastructure. Therefore a WebRTC Joiner + HAPP SOCKS5 companion is **not** a viable HAPP deliverable. This file remains only a future Connect-native WebRTC handoff; HAPP needs an independently verified native profile route (for example XHTTP through a reachable free HTTPS front), not a claimed WebRTC import.

## Existing local boundaries to preserve

- The app's single Android VPN lifecycle is centred on `app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt`, `BoxService.kt` and `StartServiceCoordinator.kt`. A future engine must cooperate with that one lifecycle; a second simultaneous `VpnService` is not a valid integration strategy.
- `PxlSubscriptionConverter.kt` and `UpdateProfileWork.kt` adapt Smart/legacy subscription content. `PxlXhttpSidecar.kt` stores XHTTP-specific data. A WebRTC endpoint/session must be a separately versioned capability, not mislabelled as VLESS, Hysteria2, or XHTTP.
- Preserve Telegram login, legacy URL import, guest mode, saved profiles, Smart AUTO/routing, current VLESS/Hysteria2 behaviour and the unfinished XHTTP work. Never force a WebRTC migration or replace a working node merely because a room is advertised.

## Concrete Connect change map for the later implementation slice

| Area | Existing files / component | Required future change |
| --- | --- | --- |
| VPN lifecycle | `app/src/main/java/io/nekohasekai/sfa/bg/VPNService.kt`, `BoxService.kt`, `StartServiceCoordinator.kt` | Integrate a selectable transport into the **single** existing Android VPN lifecycle, including start/stop, reconnect, network handoff and teardown on entitlement loss. Do not start a second simultaneous `VpnService`. |
| WebRTC data plane | New `WebRTCEngine` module, exact source layout to be chosen after PoC | Adapt the upstream Joiner (likely Go/Pion and Android bridge) to Connect's existing TUN and routing. Review API, license, dependencies, target ABIs, packaging, security, CPU and battery before shipping. Do not transplant upstream's Android VPN service unchanged. |
| Subscription compatibility | `app/src/main/java/io/nekohasekai/sfa/utils/PxlSubscriptionConverter.kt`, `bg/UpdateProfileWork.kt` | Only after transport proof, parse a separately versioned **Connect-only capability/manifest**. Missing capability keeps present Smart/legacy behaviour. Never display a fake VLESS/Hysteria2/Xray node as WebRTC. |
| XHTTP separation | `app/src/main/java/io/nekohasekai/sfa/utils/PxlXhttpSidecar.kt` | Preserve XHTTP as its own path; do not reuse its session format or rename it to WebRTC. |
| UI and local identity | Existing connection UI, account/device storage; exact files to select when implementing | Offer optional Whitelist Mode with truthful states and errors, retain manual fallback to current nodes, keep device proof in protected storage, and never embed permanent platform credentials in imported URLs. |

This is an integration map, **not** a finalized API, implementation approval, or reason to modify Connect now.

## Proposed interface to investigate, not implement yet

1. Define a `WebRTCEngine`/transport adapter behind the existing start/stop/reconnect state machine, with explicit states `unsupported`, `provisioning`, `ready`, `connecting`, `connected`, `degraded`, `stopped`, `expired`. Its own tunnel/proxy joins the platform room but the existing app owns the only Android VPN/TUN and routing policy.
2. Keep session acquisition separate from static subscription import. The Creator must enforce backend authorization **before traffic relay** and periodically thereafter; a room link alone is not permission. Existing Connect device identity, active entitlement, freeze/ban/revocation, concurrent-session cap and abuse limits must all be checked. In a whitelist the phone may not reach the ordinary backend URL, so an in-band WebRTC challenge/response with server-side backend validation is the preferred design candidate; compare it with pre-issued short-lived tickets before implementation. Do not put platform account cookies, permanent join links, relays' admin tokens or reusable credentials in a shareable subscription URL, logs, crash reports or screenshots. Never send a long-lived app bearer token over a platform room merely to make the first PoC pass.
3. A discoverable `Whitelist Mode` should be an optional, truthful route only on devices with a compatible engine and provisioned session. The current engine must remain the fallback, and a transport failure must not silently claim VPN protection. Display the actual path, status and meaningful error rather than a fake ping.
4. Specify one-device-one-session lifecycle, room rotation, reconnect/backoff, background/foreground behaviour, stop/delete/revocation cleanup, process death, network handoff, DNS and UDP/TCP policy, IPv4/IPv6 leaks, metered data and battery impact. Verify upstream's one-Creator-per-Joiner limit against service capacity before product commitment. A connection attempted while the backend URL is blocked must still be able to reach a room/bootstrap path or report a truthful failure; it must never silently bypass authorization. After an entitlement expires or a device is revoked, an already-open tunnel must be terminated, not merely denied on next start.
5. Start with an isolated local/fake Creator/Joiner and restricted-network emulator; then test on a dedicated Android test device and a real, permitted platform account only after separate design and operational approval. Include interruption, offline, expired subscription, revoked device, session reuse, untrusted room data, and coexistence with current Smart/legacy profiles.

## Backend entitlement and anti-abuse contract to design

The existing PXLNET backend/database remain the source of truth. Local backend already contains `app_access_tokens`/`device_id`, `vpn_device_limit`, and `SubscriptionStateResolver`; these are reusable foundations, **not an existing WebRTC session API**. Do not create a second independent subscription database for the Creator.

The Creator is the data-plane egress gate. It must refuse **all user traffic before** a positive backend check of active entitlement, expiry, freeze/ban/revocation, device authorization, concurrent-session limit and abuse limits. A room/SFU link is only a route, never permission. Recheck during a session and terminate one that loses authorization. New sessions fail closed if backend verification is unavailable; the policy for an already-open session during a short outage needs explicit security design. Backend should issue/verify sessions and revocations, **not proxy every packet in Python**.

The phone may be unable to reach the ordinary backend URL under a whitelist. A plausible PoC is an in-band WebRTC challenge/response to the Creator, which checks with the backend on its own network. Compare this with pre-issued short-lived tickets, which may expire before a cold start. **Room discovery/bootstrap with no direct backend access is still unsolved**; if it cannot be established, the app must show a truthful failure. Never send a long-lived Connect bearer token over a platform room merely to make a PoC pass. Define device-bound proof, single-use/replay protection, ticket rotation, rate/session/traffic quotas, and redacted audit logging before implementation.

The platform may require one Creator per Joiner, and operating multiple sessions on an existing server may be expensive even without buying infrastructure. Measure this before promising scale. No hardcoded provider hostname, creator count or static room link should become app business logic.

## Later work order and acceptance checks

1. Isolated upstream Creator/Joiner PoC: controlled SFU room, whitelist emulation, actual egress IP, TCP/UDP/DNS, resource use, platform account rules; no Smart Gateway, 3x-ui or production changes.
2. Written Android VPN/TUN and backend-session security design: cold-start bootstrap, device proof, issuer/verifier, expiry, revocation, outage behaviour, limits, logging. Obtain approval for material product/security choices before writing implementation code.
3. Local Connect integration behind a feature flag/capability with fake Creator/backend tests, truthful UI, fallback and preserved existing profiles.
4. Backend/Creator integration and Connect-only metadata only after the contract and transport pass; HAPP/legacy output remains compatible.
5. Independent security/code review, unit/integration/build for target ABIs, physical-device restricted-network test, leak/reconnect/battery checks, and separately permission-gated production rollout.

Minimum test matrix: valid subscriber; no subscription; expiry mid-session; frozen, banned and revoked user/device; copied room link; replayed/stolen ticket; device/session limit exceeded; backend unavailable; SFU unavailable; Wi-Fi/mobile handoff; process death; IPv4/IPv6, DNS and UDP leaks; traffic without authorization; ordinary VLESS/Hysteria2, Smart/legacy URL and guest-mode regressions. None of these results are claimed as passed yet.

## External compatibility that still needs proof

- [`kulikov0/whitelist-bypass`](https://github.com/kulikov0/whitelist-bypass) provides a headless Go relay and an Android joiner using its own `VpnService`; lifting its VPN service directly into this app would conflict with the existing one. Reusing its Go/Pion data plane behind PXLNET's VPN service is a possible research direction, subject to API, license, build/ABI and security review.
- [HAPP's official import documentation](https://www.happ.su/main/ru/dev-docs/examples-of-links-and-parameters) lists SOCKS5, not native WebRTC room import. A companion local Joiner plus HAPP SOCKS5 may work in some environments, but Android VPN ownership, lifecycle and DNS/UDP egress are unproven. Connect's own WebRTC engine is the primary native-client path; do not claim HAPP parity until an actual HAPP device acceptance test passes.
- Other transport candidates (TURN relay, XHTTP/fronted HTTPS) are comparisons, not implementation commitments. Do not bake provider-specific VK/Telemost/WB hostnames or a fixed server count into app business logic.

## Completion gate for a later Connect task

An owner-approved design and backend session contract; isolated proof of transport; independent security and code review; unit/integration tests for lifecycle and entitlement; build for target ABIs; physical-device egress/leak/reconnect/battery checks; and a verified fallback to current VLESS/Hysteria2 when the new mode is unavailable. Only then consider beta rollout, with production updates separately permission-gated.
