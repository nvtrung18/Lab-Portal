# ALELA: Scholar sync, sorting and Products

Implementation notes for the changes after commit `eb46600`. No production data was written and no deployment/migration was run during implementation.

## 1. Backend configuration

The provider is [SerpApi Google Scholar Author API](https://serpapi.com/google-scholar-author-api). Its documented `author_id`, pagination offsets and `num=100` are used. The implementation only calls `https://serpapi.com/search.json`; it never fetches CMS URLs, follows provider `next` URLs, solves CAPTCHA, uses proxies or crawls Google directly.

Set these variables in the **backend** environment (root environment file when using Compose):

```dotenv
SCHOLAR_SYNC_ENABLED=true
SCHOLAR_SERPAPI_API_KEY=<set privately on the backend>
SCHOLAR_SYNC_MAX_PAGES=100
SCHOLAR_SYNC_TIMEOUT_SECONDS=30
SCHOLAR_SYNC_MAX_SNAPSHOT_BYTES=8000000
```

Examples contain an empty key and disable automatic synchronization. Never use a `VITE_` prefix for the key. Both development and production Compose pass these variables only to the backend. Direct Spring runs require environment variables to be exported by the operator; Spring does not automatically read arbitrary `.env` files.

A restart/recreation of the backend is needed after configuring its environment. This document does **not** authorize performing a production restart or deploy.

### Scheduling and manual synchronization

- The scheduler checks saved draft **and** published members one minute after startup, then hourly. Each Scholar author ID is fetched once per successful **7-day** interval, persisted across restarts. Several members referring to the same author share one snapshot.
- Automatic sync requires both `SCHOLAR_SYNC_ENABLED=true` and a nonempty key. Manual sync needs the key but can run with automatic sync disabled.
- Failure preserves the last successful snapshot and schedules a retry after one day. Manual retries are coalesced while queued/running and throttled for 60 seconds after the last attempt.
- The worker has a dedicated single-thread executor and bounded queue. A database lease is acquired only when a worker starts; CMS row locking is brief and no provider request runs inside that transaction. A stale lease can be reclaimed after two hours; the provider collection loop has a one-hour deadline, with a bounded request timeout per page.
- CMS → **Thành viên → Google Scholar** shows configuration state, queued/running/success/failure state, article count, last attempt/success, next attempt, provider processing time when available and the last ten safe error codes. **Đồng bộ ngay** requests an asynchronous run.
- Save a new member/changed Scholar URL as a draft first. A draft-only member is eligible for synchronization, but is not published by synchronization.

### API

Both endpoints require ADMIN and the existing authenticated portal checks:

- `GET /api/admin/website/scholar-sync`
- `POST /api/admin/website/scholar-sync/{authorId}` → 202, then poll the status endpoint.

Only an author ID present in saved CMS content can be requested. Status responses never contain provider keys, provider request URLs, raw response bodies or draft contents. Errors are fixed safe codes; HTTP errors identify a status such as `PROVIDER_HTTP_429` without logging provider body text or request query strings.

## 2. Storage and preservation

`system_configs` already exists. Separate keys `WEBSITE_SCHOLAR_<authorId>` store sync state, error history and the latest complete successful Scholar snapshot. **No new database migration is necessary.** The existing V74 CMS storage prerequisite still applies.

The synchronizer never writes `PUBLIC_WEBSITE_CONTENT`, its revision, draft, published content or publishedAt. Public website reads overlay **only** Scholar publications, metrics when supplied, and retrieval date onto already-published members. Names, roles, photos, affiliations, interests, descriptions, account mappings and other content stay in the CMS. A changed Scholar author URL uses a different snapshot; an old author's data is not attached to the new URL.

The CMS editor continues to display the manually entered snapshot. This is intentional: save/publish cannot copy a large synced bibliography into the editor and overwrite other CMS fields. The sync panel reports the independent snapshot count and date. The current draft preview also uses the edited/manual member snapshot; the public published view uses the successful independent snapshot. Products previews use the ordinary CMS draft data as before.

### Pagination, limits and incomplete responses

- Articles are deduplicated by `citation_id` (including its equivalent `citation_for_view` in a Scholar article URL), or by the source URL if no article identifier is available. No deduplication by title alone, since different works may share a title.
- Fetch all returned pages using the provider's `start`/`cstart` offset. Only the offset is read from a `next` URL; its host, API key and other query parameters are never followed/copied.
- A full 100-row page without `next` causes another page request, avoiding silently stopping at 100.
- Missing citations/year remain null; missing text remains empty. A year of 0 or outside the valid range is unknown. Values are not inferred from article titles or summed to invent h-index/citation metrics.
- Provider response fields supply total/recent metrics and the recent starting year. Provider processing time is recorded only when parseable; fetchedAt is the actual local retrieval time.
- API errors, malformed/missing articles, empty results, stalled pagination, the configured page limit, total run deadline or snapshot size limit **do not replace** the previous snapshot. A partial page collection is never committed as complete.
- Default maximum: 100 pages of up to 100 articles, plus an 8MB serialized snapshot guard. `max-pages` accepts 1–1000. If more pages are advertised/needed when the limit is reached, status is `PAGE_LIMIT_REACHED_RETAINED`; increase the configured limit deliberately after checking provider quota. These are operational safeguards, not a silent top-N list.
- The CMS manual editor/DTO retains its existing limit of **100 manual articles/profile**. Independent synchronized snapshots can exceed 100 and are not sent through CMS write validation. Tests cover a public snapshot with 101 articles. A payload/limit error keeps old data, it never trims articles to satisfy the manual limit.
- Provider coverage and plan limits still apply; shortened author/venue strings from the provider remain shortened. There is no claim that the provider exposes full citation-detail metadata for every article.

No real provider request was made as part of the implementation. The API key, subscribed provider quota and a real end-to-end synchronization remain to be configured/verified by the operator.

## 3. Scholar table sorting

`contracts/scholar-sort.ts` implements pure, stable sorting; the existing Scholar component adds keyboard-accessible header buttons.

- Default: publication year descending.
- Click **Trích dẫn / Citations** or **Năm / Year**: first selection descending; click the same column again to toggle ascending/descending.
- Unknown/null/nonfinite values stay last in both directions; 0 citations is a known value.
- Equal values retain their source order. The input/CMS array is never mutated.
- Active header uses `aria-sort=ascending|descending`, inactive header `none`; arrow icons show the active direction. Buttons retain a 44px minimum target and the existing horizontally scrolling mobile table.

## 4. Products and moving the two News sections

New routes: `/vi/products`, `/en/products`. Both use the shared public router (not `app/routes.tsx` legacy), fixed page normalization, PageHeading and DynamicSections. Header desktop/mobile and footer now include **Projects → Products → News**. An empty Products page has an honest empty state and no invented product records.

The new `products` PageTemplate is accepted by TypeScript and Java DTO validation. No new `data.products` collection is introduced: products are the rich bilingual sections already supported by Content Library, including HTML, images and links.

### Safe operator workflow — no automatic production rewrite

1. After deploying the code through the normal authorized process, open CMS → **Nội dung các tab → Sản phẩm**. Older CMS JSON is normalized to include this tab without changing or moving existing sections.
2. Choose **Chuyển nội dung từ Tin & Sự kiện**. The helper recognizes the two requested Vietnamese titles among sections currently selected in News:
   - Hệ thống IoT quan trắc khí gây mùi trong trang trại chăn nuôi
   - Hệ thống mô phỏng trang trại chăn nuôi lợn digital twin
3. Review the checkbox list and confirmation text. Cancel leaves the draft unchanged. Confirm changes **only the working draft's section selections**: unselects these IDs in News, appends them once in Products. The library entries, original IDs, HTML, images, links and both language fields are preserved; unrelated News sections and selections on other pages stay intact.
4. Preview Products **and** News in VI/EN and desktop/mobile. Use the same editor to add/edit/reorder/hide/remove sections as other tabs. If the titles were changed and no candidate is detected, manually select the existing library entries in Products and unselect them in News; do not recreate the HTML or delete the library entries.
5. **Lưu nháp** if review is not complete. Published data is unchanged.
6. Only after approval, **Xuất bản** using the existing confirmation. This publishes the complete reviewed draft through the existing revision workflow. If 409 occurs, reload the latest draft and review again instead of force-overwriting.
7. Verify public Products contains the two sections and public News no longer selects them.

No script, migration or startup hook silently moves production data. Reusing selected sections preserves their contents; deleting a library entry would affect every page using it, so the transfer never deletes library content.

## 5. Files and checks

Main implementation:

- Backend: `admin/website/scholar/`, `ScholarSyncController`, `WebsiteContentServiceImpl`, Java WebsiteContent DTO and the repository's batch key lookup.
- Contracts: `website-content.ts`, `website-pages.ts`, `website-product-transfer.ts`, `scholar-sort.ts`.
- Frontend: `ScholarSyncPanel`, `ProductSectionsTransfer`, `PageContentEditor`, public Scholar component, `views/products.tsx`, active `PublicWebsitePage` switch and shared navigation/link resolution.
- Configuration: application.yml, backend Compose environment, environment examples. No key added to frontend.
- Tests: `client/tests/website.test.ts`, Java `admin/website/` test classes. Frontend CI now runs `npm run test:website`.

Commands:

```sh
cd client
npm run test:website
npm run lint
./node_modules/.bin/tsc --noEmit -p tsconfig.json
# Configure an appropriate API origin for the environment before building:
npm run build

# From server:
sh mvnw verify
```

Verified locally on 15 October 2026:

- `npm run test:website`: 7 tests passed, including URL parsing, stable/null-safe sorting, legacy normalization, explicit transfer preservation and preview/public round-trips.
- TypeScript no-emit check and Vite production build: passed. The final build used the same non-production API origin as CI (`https://api.ci.invalid`), so it is a verification artifact, not a production deployment package.
- ESLint: 0 errors, 31 pre-existing warnings; no new website warning. Vite still reports the existing large-bundle warning. Node's native TypeScript test runner emits a non-fatal module-type detection warning for shared contracts; no dependency/lockfile changes were needed.
- New backend coverage: 28 passing tests across provider pagination/parsing/failures, isolated storage/leases/preservation, scheduling/queue/retry, ADMIN authorization, Products DTO/JSON, public projection, draft/publish and stale revisions.
- Browser: Products VI/EN, images/rich text, desktop and 390px mobile layouts; English header at 1001px; Products order in desktop/mobile/footer; actual citation/year toggle and aria-sort on the Nguyễn Hoài Sơn profile. Public mobile table retains its own 420px horizontal scroll region without widening the document.
- Mutation workflows were covered by local tests, not by logging into production CMS. Production sections were not transferred. No real SerpApi request was made.

**Final `sh mvnw verify`: passed, 1,765 tests reported, 1,733 executed successfully, 32 skipped, 0 failures/errors.** The skipped cases are environment-gated integration tests. The browser checks use a local in-memory API fixture, not production CMS or real Scholar data. Existing MySQL opt-in tests are skipped unless explicitly configured; successful H2/unit checks do not certify a production MySQL instance or provider plan.

## Changed file inventory

Paths are relative to the repository root. The pre-existing untracked `PROJECT_CONTEXT_FOR_CHATGPT.md` was preserved without edits.

- `.env.example`
- `.env.production.example`
- `.github/workflows/ci.yml`
- `client/package.json`
- `client/src/modules/website/api.ts`
- `client/src/modules/website/components/PageContentEditor.tsx`
- `client/src/modules/website/components/ProductSectionsTransfer.tsx`
- `client/src/modules/website/components/ScholarSyncPanel.tsx`
- `client/src/modules/website/components/TemplateCopyEditor.tsx`
- `client/src/modules/website/content.ts`
- `client/src/modules/website/pages/AdminWebsitePage.tsx`
- `client/src/modules/website/pages/PublicWebsitePage.tsx`
- `client/src/modules/website/public/components/scholar-profile-details.tsx`
- `client/src/modules/website/public/components/site-link.tsx`
- `client/src/modules/website/public/components/site-shell.tsx`
- `client/src/modules/website/public/components/views/products.tsx`
- `client/src/modules/website/public/public.css`
- `client/tests/website.test.ts`
- `contracts/scholar-author-id.ts`
- `contracts/scholar-sort.ts`
- `contracts/website-content.ts`
- `contracts/website-pages.ts`
- `contracts/website-product-transfer.ts`
- `docker-compose.prod.yml`
- `docker-compose.yml`
- `docs/website/SCHOLAR_AND_PRODUCTS.md`
- `server/.env.example`
- `server/src/main/java/com/web/labportalbackend/admin/systemconfig/repository/SystemConfigRepository.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/controller/ScholarSyncController.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/dto/WebsiteContent.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarAuthorId.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarProvider.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSnapshot.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSnapshotStore.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncConfiguration.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncException.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncProperties.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncService.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncState.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/scholar/SerpApiScholarClient.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/service/impl/WebsiteContentServiceImpl.java`
- `server/src/main/resources/application.yml`
- `server/src/test/java/com/web/labportalbackend/admin/website/ScholarSyncControllerTest.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteProductsTest.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/scholar/ScholarSnapshotStoreTest.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/scholar/ScholarSyncServiceTest.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/scholar/SerpApiScholarClientTest.java`
