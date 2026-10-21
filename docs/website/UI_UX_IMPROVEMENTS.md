# Public website UI improvements

Implemented against `ff2ceef`; no CMS records, member roles, Scholar snapshots, backend configuration or production data were changed. The existing untracked `PROJECT_CONTEXT_FOR_CHATGPT.md` is preserved.

## Behavior

- `ExpandableContent` is shared by every public editorial section through `DynamicSections`, including Home, Research and Products. Titles, separate illustrations and section links remain outside the collapsed description/body. The collapsed height is five rendered text line heights; paragraph spacing, headings and inline media can affect the number of complete visible lines.
- CMS HTML continues through `RichTextBody` and the existing sanitizer. No substring operation, HTML rewrite or duplicate body is used. Expansion reveals the same DOM, including formatting, links, lists and inline images. Short content has no toggle.
- Each section has independent state; changing locale resets its state. `ResizeObserver`, image load and font readiness update measurements. Buttons provide localized labels, `aria-expanded`, `aria-controls`, a visible focus outline and a 44px target. Keyboard focus on a clipped link opens its body, including during a collapse transition. Collapsing from the end of a long article returns to its heading below the sticky header. Reduced motion disables the animation.
- Editorial illustrations and Research/Project/News card images use centered `contain` in their existing frames, with a theme-aware background. Card image zoom on hover is removed so it cannot crop the illustration. Hero/background and member portrait image rules retain their existing behavior. Inline rich-text images retain their existing responsive, natural proportions when expanded.
- `MSc student` already exists in shared contracts and the CMS role selector. Its filter now reads `ThS` / `Master's Students`; its tier follows PhD and remains visible with a count and localized empty state when there are no students. Searching within this filter has a distinct no-match state. Classification uses the stored role only. Existing roles, IDs, member order, other empty-tier rules and the legacy Other Members group are retained.
- The Guest filter label is `Khách mời` / `Guests`. The `Guest` ID, filtering behavior and Partner Members tier title remain unchanged.

## Files

- `client/src/modules/website/public/components/expandable-content.tsx` — reusable measured disclosure.
- `client/src/modules/website/public/components/dynamic-sections.tsx` — shared editorial integration and heading anchors.
- `client/src/modules/website/public/public.css` — contain images, disclosure controls/transitions and empty tier styling.
- `client/src/modules/website/member-roles.ts` — existing MSc/Guest display metadata, empty-tier policy and shared role matcher.
- `client/src/modules/website/public/components/views/explore.tsx` — role filters, tier visibility and localized empty states.
- `client/tests/website.test.ts` — regression tests for role order, persistent empty MSc tier, Guest labels, role-only classification and input preservation.
- `client/src/modules/website/README.md` — updated membership display documentation.
- `docs/website/UI_UX_IMPROVEMENTS.md` — this handover.

## Verification (15 October 2026, Vietnam time)

- `npm run test:website`: 9 passed, including two new role regression tests.
- `./node_modules/.bin/tsc --noEmit -p tsconfig.json`: passed.
- `npm run lint`: no errors; the same 31 existing warnings in unrelated modules.
- `VITE_API_BASE_URL=http://127.0.0.1:4181 npm run build`: passed. Existing bundle-size warning remains. This origin was used only for a local mock API and browser checks; no production environment file was changed.
- `git diff --check`: passed.
- Browser checks used the actual built public application and a disposable local mock API. Verified Home/Research/Products in VI and EN at desktop width 1280, and representative mobile routes at 390px; Products also checked at 320/768px. No page-level horizontal overflow was observed. Waited for mock API content before evaluating, avoiding the initial fallback content.
- Verified independent expand/collapse, short-body button absence, unchanged rich HTML across toggles, full expanded height, keyboard toggling and access to clipped links, and heading visibility after collapsing from the end. Verified light/dark appearance and fully contained wide (1200x300), portrait (300x900) and small (48x48) illustration fixtures. Browser console reported no errors during these checks.
- People VI/EN: verified the empty MSc tier and filter; Guest filtering returned the same three seeded Guest profiles. A disposable MSc profile in the local fixture verified correct card rendering/count and filtering; it was never added to CMS or repository content.

No new dependencies. Backend tests were not run because no backend code or contracts changed. Physical phones, Safari and a full screen-reader audit were not tested. No push, deploy, migration, publish or production writes were performed.


## Compact public spacing (21 October 2026)

Branch: `feature/compact-public-layout`. The implementation changes only
`client/src/modules/website/public/public.css`; CMS content, contracts, search,
Scholar, routing and backend behavior are unchanged.

- Shared tokens define 32px section padding (24px on mobile), 16px following-section
  padding, 24px content spacing and 24px/20px heading padding. Adjacent sections
  share their separation; content after search does not add another top padding.
- Page headings retain breadcrumb, labels, typography and the decorative plus.
  The plus is vertically centered, and heading height follows content. Homepage
  Hero HTML and its measured geometry are unchanged.
- Search panels use 16px padding; result counts/notes and editorial content follow
  without stacked 85px section padding. Search notes retain a 16px gap before
  in-panel results, and no extra trailing margin before the following section.
- People group/tier headings and connectors share spacing variables: 48px between
  tiers and 16px branches. The connector lengths match the new spacing. Flex card
  widths, last-row centering, role filters and the independent guest group remain.
- Editorial image/text columns align at the top, use a 32px gap, and featured
  headings use 28–40px. Article HTML, list markers, expand/collapse, gallery frames
  and lightbox code are untouched. Empty states and detail headings are also compact.

Measured VI content positions at 1366×768, using a read-only published snapshot:

| Page | Before: first body/image top | After |
| --- | ---: | ---: |
| Publications | 838px | 581px |
| Projects | 838px | 581px |
| People | 858px | 633px |
| Research | 936px | 631px |
| Products | 728px | 452px |
| About | 651px | 440px |
| Contact | 561px | 401px |

Standard heading height decreased from 259px to 160px; About is 196px because
its existing text is longer. Mobile headings grow naturally with wrapped text.
News has no published records in the snapshot and preserves its empty state.

Verification:

- 32/32 website tests pass. Typecheck + production build pass. ESLint reports
  0 errors and 31 pre-existing warnings; the existing bundle-size warning remains.
- Checked 128 loaded layouts: eight content pages, VI/EN, light/dark, and viewports
  1366×768, 1440×900, 768×1024 and 390×844. No page-level horizontal overflow or
  overlapping top-level sections; desktop first body/image or empty state is
  inside the initial viewport. Before/after screenshots were saved locally.
- Publications preserves 101 list items and unrestricted height/visible overflow.
  ProMESh search returns one result; its source target is approximately 16px below
  the sticky header. Products retains identical rich HTML across expand/collapse;
  lightbox ArrowRight advances 1/2 to 2/2 and ESC closes it.
- Verified homepage Hero HTML and geometry against the baseline at 1366px.
  No production writes, publish, migration, commit, push, merge or deployment.
- Checks use the local built app with published JSON; uploaded-image URLs redirect
  to their existing public assets. No physical-device/Safari or full screen-reader
  audit was performed. Backend tests were not needed for this CSS-only change.
