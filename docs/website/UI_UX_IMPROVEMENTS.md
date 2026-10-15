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
