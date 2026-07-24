# Playwright Test Coverage

Last updated: 2026-07-24 · 10 passed · 0 failed · 0 skipped · 10 total

`[x]` passed &nbsp; `[!]` failed &nbsp; `[-]` skipped

## e2e/02-marketplace-authentication-flow.spec.js

**› e2e/02-marketplace-authentication-flow.spec.js**
- [x] Authentication flow › adminEn signs up — first user auto-promoted to ADMIN, settings open, timeline and user audit created
- [x] Authentication flow › userEn signs up — USER role assigned, settings open, activity created
- [x] Authentication flow › userUk signs up — USER role assigned, settings open, activity created
- [x] Authentication flow › moderatorUk signs up — USER role assigned, activity created
- [x] Authentication flow › moderatorEn signs up — USER role assigned, activity created
- [x] Authentication flow › adminUk signs up — USER role assigned, activity created
- [x] Authentication flow › userEn logs in — cancel logout keeps session, confirm logout works
- [x] Authentication flow › userEn — locale persists across logout and re-login
- [x] Authentication flow › wrong password — login rejected, user stays logged out
- [x] Authentication flow › rateLimitUser exceeds login attempts — 5 wrong passwords rejected, 6th blocked with too-many-attempts message, correct password still blocked during lockout

