# ITSDEV motion v1.0.0

Source specification: https://app.notion.com/p/3f79cc8ccb4a81adac5cfca5888fabde
Pattern reference: https://transitions.dev/ and https://github.com/Jakubantalik/transitions.dev.
Original implementation; no third-party motion runtime or copied premium recipes.

## Integration

### Readable, slower entrances — 2026-10-12 follow-up

User reported rushed motion and slow loading. Entrance duration is now 800ms (was 450ms), stagger 40ms capped at 120ms (was 70/210ms). Initially visible content starts at opacity 1; later scroll reveals start at .35. Results no longer have a second parent fade. Spinner rotation is 1.6s instead of 1s. Mutation handling scans only newly added subtrees, rather than the entire document for every DOM change.

Loading work: replace the blocking remote Font Awesome kit with existing Lucide icons; use Pretendard variable Unicode subsets instead of whole per-weight fonts; cache fingerprinted `/assets/*` for one year with immutable, leaving HTML revalidation intact. OKR: immediate readable job discovery while preserving perceptible movement. Pass criteria/evaluation: 4 viewport fixtures assert first-screen opacity 1, 800ms duration, delay <=120ms, and existing focus/off/scroll cases; 22 passed, 2 desktop modal skips; lint/build pass.

Diagnostic baseline: fresh 390x844 Chromium, no throttling, alternating motion on/reduced, two homepage visits per mode. LCP on 9444/16024ms, reduced 5404/17536ms; JS/CSS requests 2062–13105ms. The following jobs navigation hit a 60s timeout. This incomplete, highly variable sample cannot establish animation causality or a speedup. A separate asset request was a CDN HIT but timed out during transfer. Network latency and field Core Web Vitals remain unverified; do not describe them as fixed solely by the animation changes.

### Entrance and scroll correction — 2026-10-12

User clarification supersedes the earlier interaction-only scope: first-viewport content and below-fold content must animate on entry. `src/motion/reveal.js` is shared verbatim with the portal. Homepage hero items, section headings, theme cards, CTA and asynchronously loaded job cards reveal once per mounted element. Duration 450ms, batch stagger 70ms capped at 210ms; travel 20px (`standard`) or 12px (`subtle`). Individual `translate` avoids overriding hover `transform`. Headlines remain opaque from frame one. No CSS hides content before JS; focus, printing, runtime off and OS reduced motion cancel animations.

OKR: make the initial hierarchy and newly discovered content perceptible while preserving job-search access; conversion improvement is not measured. KPI/pass criteria: all 8 entrance/scroll cases at 360/768/1024/1440px pass, no replay on reverse scroll, focused content opacity 1, no reveal under reduced motion. Before: no viewport-triggered reveals; after: 8/8 pass. Combined motion regression: 22 passed, 2 desktop-only modal skips. Commands: `npm run lint`, `npm run build`, `npx playwright test e2e/entrance-scroll.spec.js e2e/motion.spec.js`. JS gzip 122.92KB; +1.47KB against original 121.45KB baseline, below 5KB budget. Field performance targets below remain unverified.

`src/motion/motion.css` is the shared, vendored v1.0.0 source. The portal keeps an identical copy at `src/assets/motion.css`. Change both together and compare SHA-256 before releasing. Site adapters are separate. No package registry or new runtime dependency is required.

- Cards: semantic router links, keyboard focus, 4px hover only for fine pointers; stable title height.
- Buttons: press .98, 80ms press/140ms feedback; disabled actions excluded.
- Job filters: native pressed buttons, measured sliding indicator; ResizeObserver handles wrapped rows and mobile panel opening. They are filters, not ARIA tab panels.
- Results: 12 placeholders matching page size, 355px/365px minimum card space, 1200ms opacity pulse and 320ms content fade. Variable content and result counts can still cause layout shifts; no zero-CLS claim.
- Saved/application feedback: persistent polite live region, dismissible status, no forced focus or timeout; application message distinguishes recorded activity from completed external application.
- Mobile menu: native modal dialog provides focus containment, Escape, background inertness and focus restoration. Backdrop click closes. Native close is immediate on browsers without discrete transition support.

## Rollout and rollback

Default DJC profile is `standard`; `VITE_MOTION_MODE=off|subtle|standard` selects a build profile. GitHub Actions reads repository variable `VITE_MOTION_MODE`. Set it to `off` and rerun the frontend workflow to persist a rollback. In a current page, `document.documentElement.dataset.motion='off'` disables motion immediately for inspection only. CSS respects live OS reduced-motion changes, including removal of card lift. Functional focus rings and native controls remain.

## Evaluation contract

OKR connection: preserve keyboard access and completion of job discovery/save/application flows while introducing consistent interaction feedback. This change does not claim conversion uplift.

Targets: added JS gzip <=5KB; zero new page exceptions in the motion fixture; all applicable keyboard/reduced-motion/rollback cases pass at 360/768/1024/1440px. Field targets remain LCP <=2.5s, INP <=200ms, CLS <=0.1 at real-user p75; lab samples cannot certify those targets.

Before/after initial production JS gzip: 121.45KB -> 122.03KB (+0.58KB, before final CSS-only close transition). Initial CSS gzip: 47.14KB -> 47.97KB. No JS dependency added.

Commands: `npm run lint`, `npm run build`, `npx playwright test --workers=2`. Motion fixture covers query changes, indicator geometry, keyboard detail navigation, reduced motion, loading, off switch, mobile focus/Escape and bookmark status. Desktop modal cases are intentionally skipped. Existing detail fixture was corrected to match the pre-existing cache-busting query parameter.

Production lab script: `.codex/measure-motion.mjs before|after`, fresh Chromium contexts, 390x844 touch, 3 visits/site, no network/CPU throttling. LCP/CLS and sampled event durations are diagnostic only; not field INP or a 60fps guarantee.
