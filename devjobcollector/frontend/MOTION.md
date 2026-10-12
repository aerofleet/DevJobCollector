# ITSDEV motion v1.0.0

Source specification: https://app.notion.com/p/3f79cc8ccb4a81adac5cfca5888fabde
Pattern reference: https://transitions.dev/ and https://github.com/Jakubantalik/transitions.dev.
Original implementation; no third-party motion runtime or copied premium recipes.

## Integration

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
