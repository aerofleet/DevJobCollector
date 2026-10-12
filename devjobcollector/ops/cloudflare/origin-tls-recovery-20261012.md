# Public job API TLS recovery — 2026-10-12

## Incident and confirmed causes

Public search and health returned HTTP 526 while the backend's loopback health returned 200. The origin certificate expired on 2026-10-01 at 09:25:32 UTC. Certbot's timer was active, but renewal failed for two independent configuration defects:

1. Four files under `/etc/letsencrypt/live/<API_DOMAIN>/` were hard links to archive generation 3 rather than the symbolic links Certbot requires. `certbot certificates` rejected the lineage.
2. The renewal configuration referenced an ACME account whose registration and private-key files were absent on this server.

How these files became hard links and why the account was absent were not established. A migration/copy mistake is a hypothesis, not a confirmed cause. No database record loss was observed or inferred from the 526 response.

The frontend initialized the result count to zero and kept displaying it after a failed initial request. This made an unknown count look like a successful empty result. The motion release changed neither the API endpoint nor certificate settings; the inspected certificate had expired before that release.

## Recovery

- Verified the four current files refer to the same inodes as the audited archive generation before mutation.
- Backed up the complete Let's Encrypt and Nginx directories on the origin under a root-only, timestamped `/root/djc-tls-backups/` directory. Private keys stayed on that host and were not logged.
- Atomically replaced only the four hard-link directory entries with relative symbolic links. Existing certificate/key bytes remained unchanged during lineage repair.
- Registered a new production ACME account because no complete registration was present, then renewed the existing named certificate through the Nginx authenticator/installer.
- Ran `nginx -t` and reloaded Nginx. TLS verification was not weakened; DNS, OCI routing/security rules, Cloudflare SSL mode and backend application data were unchanged.
- New certificate expiration: **2027-01-10 07:10:47 UTC**. The live links now point to archive generation 4.
- Confirmed `certbot.timer` enabled and active. A subsequent renewal dry-run without overriding the saved account succeeded.

Frontend commit `abf5817` distinguishes unknown/loading/error counts from successful zero results and adds a retry button. Additional-page failure preserves the previously known count; retry restarts the query. Deployment: https://github.com/aerofleet/DevJobCollector/actions/runs/38221395252 (success).

## KPI / OKR / evaluation set

OKR: restore public job discovery and ensure API failures cannot be mistaken for an empty collection. No conversion uplift is claimed.

Targets / pass criteria:

- Public health + unfiltered search + backend-filter search + Java-filter search: 4/4 successful responses.
- Public frontend query-to-detail navigation: 1/1 successful without page exceptions.
- Renewal rehearsal: 1/1 success, timer enabled/active, valid origin certificate.
- Error UI: loading and failed requests never display a fabricated numeric count; successful empty responses still show zero; retry recovers the real count across four viewports.

Before/after:

| Measurement | Before | After |
|---|---|---|
| Public health/search | 526 / 526 | 200 / 200 |
| Origin certificate | Expired 2026-10-01 | Valid through 2027-01-10 |
| Renewal configuration | Invalid lineage + missing account | Dry-run passed |
| Unfiltered public count | Unknown, UI displayed 0 | 919 |
| Backend-filter public count | Unavailable | 40 |
| Java-filter public count | Unavailable | 0, successful API response |
| Browser detail navigation | Blocked by API | Passed with real public job |

Counts are point-in-time observations, not fixed test fixtures.

`npm run lint` and `npm run build` passed. `npx playwright test e2e/jobs-results-state.spec.js e2e/jobs-infinite-scroll.spec.js e2e/motion.spec.js --workers=2`: **34 passed, 2 intentional desktop-only modal skips** at 360/768/1024/1440px. Live browser verification completed with **0 page exceptions**.

## Reproduce and verify

```sh
curl --fail --silent https://<API_DOMAIN>/actuator/health
curl --fail --silent 'https://<API_DOMAIN>/api/v1/jobs/search?page=0&size=1'
curl --fail --silent 'https://<API_DOMAIN>/api/v1/jobs/search?page=0&size=1&jobCategory=backend'
nc -z -w 3 127.0.0.1 8080
sudo certbot certificates
sudo openssl x509 -in /etc/letsencrypt/live/<API_DOMAIN>/fullchain.pem -noout -dates
sudo certbot renew --cert-name <API_DOMAIN> --dry-run --non-interactive --no-random-sleep-on-renew
systemctl is-enabled certbot.timer
systemctl is-active certbot.timer nginx
sudo nginx -t
```

For future migrations, preserve symbolic links, file ownership and private-key permissions and transfer the complete Certbot account/renewal state securely. Verify `certbot certificates` and a renewal dry-run before cutover. Do not replace a lineage with whichever archive filename sorts last; validate the certificate/key generation together first. Do not roll back to this incident's expired certificate as a service-restoration action.

References: [Cloudflare 526](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-5xx-errors/error-526/), [Certbot certificate storage and renewal](https://eff-certbot.readthedocs.io/en/stable/using.html#where-are-my-certificates).
