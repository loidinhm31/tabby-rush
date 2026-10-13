# ForumFlash theme observation and todo adaptation

Date: 2026-10-13. Source: https://forumflash.vercel.app/ . Parent inspection, not a third delegated researcher.
Evidence: browser-rendered 1440x1050 desktop and 390x844 narrow viewport; inspected computed styles/CSS variables and actual screenshots.
Screenshots: [desktop](forumflash-desktop.png), [mobile](forumflash-mobile.png). These are reference evidence, not proposed app mockups.
CSS observed: https://forumflash.vercel.app/_next/static/immutable/chunks/1sd9jgfsefezf.css (hashed URL may change).

## Observed visual language
- Deep teal headings, panels and primary buttons; mint accents; warm cream hero; white cards and pale cool page surface.
- Large tightly spaced geometric display headings; small uppercase tracked eyebrow labels; generous whitespace and thin dividers.
- Dark teal desktop sidebar plus white workspace, rounded large focus card; horizontal topic cards at narrow widths.
- Coral active-tab underline and restrained coral destructive/review treatment; mint/coral/yellow/cool chips.
- Desktop title approximately 52px; section title 54px. Header action has 10px corner radius and 11px bold label. Do not copy these tiny labels into Android controls.
- Real site is IELTS vocabulary, not a forum. Reuse visual language only; do not import vocabulary features, brand logo, content or illustration assets.

## Measured tokens
| Role / CSS variable | Observed value |
|---|---|
| Primary / --green | #004646 |
| Deep primary / --green-deep | #003838 |
| Mint / --mint | #27C4B5 |
| Mint surface / --mint-soft | #E6F0F2 |
| Cream / --cream | #F7F4ED |
| Coral / --coral | #ED3838 |
| Coral surface / --coral-soft | #FFEBEB |
| Divider / --line | #D9E3E4 |
| Text / --ink | #0F172A |
| Muted / --muted | #667279 |
| White | #FFFFFF |
| Body background computed | #F8FBFA |
| Heading font computed | Cal Sans |
| Body font computed | Alexandria |

## Proposed Compose translation (decisions, not observations)
- Doggo identity, light theme first. Dark theme not requested: explicit later choice, not accidentally inferred from dark teal cards.
- Material 3 color scheme overridden with shared design tokens; disable dynamic colors by default to preserve reference.
- Primary teal + white content; secondary mint + dark teal content. Do not use mint for small text on white without a contrast check.
- Coral reserved for errors/destructive actions; selected filter can use teal/mint to avoid conflating selection and error.
- Bundle licensed Cal Sans/Alexandria font files as common Compose resources, including required license notices. Verify redistribution and glyph coverage before bundling; approved accessible system font alternative if unavailable.
- Proposed spacing scale 4/8/12/16/24/32 dp; rounded controls ~12dp and cards ~20dp; body >=16sp, metadata >=12sp, scalable type.
- Android compact: modest cream header with title and remaining count; All/Active/Completed filter row; white todo list; accessible add action; edit in dialog/sheet. No marketing hero consuming the viewport.
- Desktop: constrain readable list width; at >=840dp sidebar for filters/counts and main list/editor; narrower desktop falls back to stacked layout. Same app features, not a second desktop implementation.
- Explicit UI states: initial loading, empty list, empty filtered results, loaded, mutation pending, validation error, unauthorized, server/network error, retry, delete confirmation.
- Completion checkbox + text/semantics, not color alone; keyboard tab order, Enter submit, Escape cancel, visible focus; TalkBack and screen-reader labels.
- Touch targets >=48dp; large text at 200%; contrast >=4.5:1 normal text and >=3:1 large text/control indicators. Measure tokens in actual component pairs.
- Long titles wrap without hiding edit/delete controls; IME does not cover input or primary action.

## Visual acceptance later
- Run actual Android app on emulator/phone; compare palette/type/hierarchy against evidence, not pixel-match a website.
- Run Linux UI over Xpra and resize to compact/expanded widths; inspect keyboard/focus and font rendering.
- Windows same scenarios on the later Windows machine; do not mark verified from Linux builds.
- Capture screenshots of real empty, populated, completed, editor, error states. Preview/fake backend alone is not runtime proof.

## Unresolved questions
- User approval for light-only first release; default preserve observed light theme.
- Font licensing/glyph audit is implementation prerequisite, not yet verified.
