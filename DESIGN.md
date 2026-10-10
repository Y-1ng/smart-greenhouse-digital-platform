---
name: 智慧温室大棚农产品全链条数字化管理平台
description: 面向寒旱区设施农业示范基地的全链条生产、质量、合作与治理工作台
colors:
  primary: "#0e7c6b"
  primary-strong: "#0a6558"
  primary-ink: "#084f45"
  primary-soft: "#e2f0ec"
  surface: "#ffffff"
  surface-glass: "rgb(255 255 255 / 72%)"
  surface-muted: "#f4f9f8"
  background: "#071d17"
  workbench: "#f1f3f4"
  cover-background: "#071d17"
  action: "#c25a2b"
  action-strong: "#a94a20"
  action-soft: "#f8eae2"
  text-strong: "#12241e"
  text-body: "#3d544b"
  text-muted: "#6b837a"
  text-faint: "#9bafa7"
  border: "#e2efe9"
  border-soft: "#eff6f2"
  success: "#14876b"
  success-soft: "#e2f0ec"
  warning: "#c98a16"
  warning-soft: "#fdf3e2"
  danger: "#d9534f"
  danger-soft: "#fdecea"
  info: "#4e93d6"
  info-soft: "#e9f2fb"
  production: "#0e7c6b"
  quality: "#2e8b57"
  cooperation: "#b07a22"
  governance: "#3a6e9e"
typography:
  display:
    fontFamily: "Noto Sans SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "22px"
    fontWeight: 650
    lineHeight: 1.3
  headline:
    fontFamily: "Noto Sans SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "17px"
    fontWeight: 650
    lineHeight: 1.35
  title:
    fontFamily: "Noto Sans SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "15px"
    fontWeight: 650
    lineHeight: 1.4
  body:
    fontFamily: "Noto Sans SC, Microsoft YaHei UI, Microsoft YaHei, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "DIN Next LT Pro, Bahnschrift, Segoe UI, sans-serif"
    fontSize: "9px"
    fontWeight: 600
    lineHeight: 1.45
  data:
    fontFamily: "DIN Next LT Pro, Bahnschrift, Segoe UI, sans-serif"
    fontSize: "21px"
    fontWeight: 650
    lineHeight: 1.08
rounded:
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  pill: "999px"
spacing:
  xxs: "4px"
  xs: "8px"
  sm: "10px"
  md: "14px"
  lg: "18px"
  xl: "24px"
  xxl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    padding: "0 14px"
    height: "40px"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-body}"
    rounded: "{rounded.md}"
    padding: "0 12px"
    height: "38px"
  navigation-item-active:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.text-strong}"
    rounded: "{rounded.md}"
    padding: "8px 10px"
  data-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: "12px"
  field-input:
    backgroundColor: "{colors.surface-muted}"
    textColor: "{colors.text-strong}"
    rounded: "{rounded.sm}"
    padding: "0 10px"
    height: "38px"
  status-tag:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success}"
    rounded: "{rounded.sm}"
    padding: "0 9px"
    height: "25px"
  scene-mode-active:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    padding: "0 10px"
    height: "30px"
---

# Design System: 智慧温室大棚农产品全链条数字化管理平台

## Overview

**Creative North Star: "日光玻璃工作台" (The Daylight Glass Workbench)**

The interface is a working instrument for a cold-arid agricultural base, not a marketing surface. It reads as a glass workbench floating in front of a sunlit greenhouse: a cool glass-cyan field, translucent white panels, frost-green identity, and white data surfaces that stay crisp and legible.

The visual language has three materials and nothing else, split across one dividing line. To the left of that line sits **the deep-green field** (`#071d17`, fine grain plus soft mint light pools) carrying the navigation as low-alpha **glass** — the rail, the secondary panel, and their light-on-dark labels. To the right sits **the workbench**: a light-grey textured surface (`#f1f3f4`, same grain, softer) holding the top bar and the page canvas. Data always lands on **white card stock** — every metric, table, chart, and form is a solid white surface with a 1px hairline boundary, a top inner highlight, and a soft diffusion shadow. **Frost green** is the identity and selection voice (brand, active navigation, selected modes, success). A single warm orange is reserved for execution actions.

The interior Three.js viewer runs in daylight, matching the physical subject: light sky backdrop, aluminium structure, glass panels with real transparency, green crop beds, amber equipment, and white glass HUD chips. The **home cover is the same world after dark**: a deep-green field (`#071d17`), a lit greenhouse floating in it, mint identity accents, and dark-green glass panels with light text. Interior and cover share geometry, materials, and motion; only the light rig and the surface polarity differ.

The system deliberately avoids neon sci-fi spectacle, purple gradients, pure black, decorative blur on data surfaces, and generic equal-card page structures. Density is used where it helps a control room scan, but every card must answer a user decision: what is happening, what is at risk, what changed, or what can be done next.

**Key Characteristics:**

- Deep-green navigation field, light-grey workbench, glass chrome, white data.
- One warm orange execution voice; module colors carry context.
- Deep-green textured field (`#071d17`) with translucent glass chrome at 7–14% white on the navigation side; light-grey grain (`#f1f3f4`) on the workbench side.
- Data-first typography: Noto Sans SC for language, DIN Next Pro for numbers and labels.
- Daylight interior scene and a deep-green night cover: aluminium frame, real glass transparency, green crop beds, amber equipment.

## Colors

The palette is a cool glass-cyan field carrying frost-green identity and white data surfaces, with module colors reserved for orientation and semantic colors reserved for state. Warm orange appears only where the user is dispatching work.

### Primary

- **Frost Green** (`#0e7c6b`): The identity and selection voice. Brand mark, active navigation, selected mode groups, progress emphasis, and standard primary buttons.
- **Deep Frost** (`#0a6558`): Hover, pressed, and gradient endpoint for the primary action.
- **Frost Ink** (`#084f45`): Small accent text and iconography that needs a darker legible green on white.
- **Frost Wash** (`#e2f0ec`): Active navigation fills, selected rows, and low-emphasis accent surfaces.

### Action

- **Dispatch Orange** (`#c25a2b`): The execution voice. Used only for immediate, consequential actions: dispatching a device command, executing an irrigation plan, opening the AI entry. Never as decoration, never as a general link color, and never more than one per screen.
- **Deep Dispatch** (`#a94a20`): Hover and pressed state for dispatch actions.
- **Dispatch Wash** (`#f8eae2`): Warning-adjacent surfaces and low-emphasis execution context.

### Secondary

- **Production Frost** (`#0e7c6b`): Production command identity and environmental action context.
- **Quality Green** (`#2e8b57`): Quality, evidence, verification, and certificate context.
- **Cooperation Amber** (`#b07a22`): Cooperative operations, equity, dividends, and contract context.
- **Governance Steel Blue** (`#3a6e9e`): Governance, regulation, chain evidence, and external coordination context.

### Tertiary

- **Success Frost** (`#14876b`): Successful device state, valid evidence, confirmed chain state, and healthy progress.
- **Harvest Warning** (`#c98a16`): Watch states, pending evidence, threshold deviation, and environmental warnings.
- **Mineral Danger** (`#d9534f`): Offline devices, failed commands, invalid evidence, and risk values.
- **Information Blue** (`#4e93d6`): Hash pointers, trace relationships, links, and informational telemetry.

### Neutral

- **Deep Field** (`#071d17`): The navigation-side field behind the rail and secondary panel, carrying a fine grain and soft mint light pools. Never used as a card fill.
- **Workbench Grey** (`#f1f3f4`): The work-surface side behind the top bar and page canvas, carrying the same grain at lower contrast. The line between this and the deep field is the product's spatial anchor: navigation is the site, the workbench is the desk.
- **Glass Window** (`rgb(9 32 25 / 62%)`): The floating application frame, over a blurred and saturated backdrop.
- **Glass Panel** (`rgb(255 255 255 / 7–14%)`): Navigation rail, secondary navigation, top bar, drawers, modals, and scene HUD — low enough that the deep green reads through.
- **Chrome Ink** (`#cfe3da` / `#a8c9bb` / `#5fdcb0`): Text and icons on glass chrome. Chrome is always light-on-dark.
- **Surface White** (`#ffffff`): Data cards, forms, charts, and tables — solid, never frosted.
- **Strong Ink** (`#12241e`): Headings, metric values, and primary labels.
- **Body Ink** (`#3d544b`): Data labels and secondary information.
- **Muted Ink** (`#6b837a`): Descriptions, helper copy, and table headers.
- **Faint Ink** (`#9bafa7`): Placeholders, axes, and tertiary metadata.

### Named Rules

**The Glass Scoping Rule.** Glass is only allowed where something genuinely floats above something else: the application window, navigation chrome, the top bar, drawers, modals, and the 3D scene HUD. Data cards are solid white. Putting a blur on a metric card is decorative glass and is forbidden.

**The Two-Polarity Rule.** The interface has exactly two polarities and they never mix on one surface. Navigation chrome — the rail, the secondary panel, their labels — is light text on the deep-green field. The workbench — top bar, page headers, drawers — is dark text on light grey or white glass. Data — every card, table, form, and chart — is dark text on solid white. A light-on-light label or a dark-on-dark label is a defect, not a style choice.

**The One Dispatch Voice Rule.** Dispatch Orange marks execution. It never becomes a decorative wash, never colors a static label, and appears at most once per screen.

**The Identity Rule.** Frost Green carries brand, selection, and success. Everything else is neutral or module-scoped.

**The Module Context Rule.** Production, quality, cooperation, and governance each have one accent used for orientation; semantic green, amber, red, and blue remain reserved for state.

**The Evidence Color Rule.** A traceability or chain claim may never rely on color alone. Pair state color with a label, icon, receipt, or explicit status.

## Typography

**Display Font:** Noto Sans SC (with Microsoft YaHei UI fallback)  
**Body Font:** Noto Sans SC (with Microsoft YaHei UI fallback)  
**Data/Label Font:** DIN Next LT Pro (with Bahnschrift and Segoe UI fallback)  
**Code Font:** Consolas (with Courier New fallback)

**Character:** The pairing is functional and instrument-like. Noto Sans SC keeps Chinese operational text calm and readable; DIN Next LT Pro gives metrics, dates, latency, and labels a precise technical voice. Data uses tabular numerals to prevent value jitter.

### Hierarchy

- **Display** (650, 22px, 1.3): Page-level greenhouse or module title only.
- **Headline** (650, 17px, 1.35): Navigation panel title and major overlay title.
- **Title** (650, 15px, 1.4): Card headings and section titles.
- **Body** (400, 12px, 1.55): Operational copy, descriptions, and labels.
- **Label** (600, 9px, 1.45): Timestamps, axis labels, measurements, and compact status labels.
- **Data** (650, 21px, 1.08): Metric values and numeric emphasis.

### Named Rules

**The Data Face Rule.** Numbers, timestamps, latency, hashes, and measurements use the data or code face, never the display face.

**The No Whisper Rule.** Metadata, table cells, and small labels live at 11–11.5px; field labels sit at 12px; buttons, inputs, and selects at 13px; chart axis ticks may stop at 11px. Nothing operational drops back to the 8–10px band the previous theme used.

## Layout

The application is a floating glass window over a cool glass-cyan field. Inside it, a two-level navigation sits on the left as two detached glass panels, and the work surface is a third rounded surface holding a glass top bar and a scrolling content area. Desktop shell columns are `84px / 236px / minmax(0, 1fr)` with the window inset 14px from the viewport; the rail and secondary panel each carry their own 22px radius and a 10–12px gap. Main content is padded `16px 18px 28px`, separated by 14px vertical rhythm, and scrolls independently of the persistent navigation.

Pages use a 12-column conceptual grid through asymmetric CSS Grid compositions. Dashboard cards are grouped by operational meaning, not by equal-width card counts. On laptop widths, secondary content stacks below primary content. At `1180px`, the secondary navigation becomes an overlay. At `900px`, multi-column operational layouts collapse to one column. At `760px`, the shell uses a 64px icon rail, 44px touch targets, hidden dashboard-editing tools, and an icon-only AI entry. At `640px`, environmental metrics collapse to a compact 2×2 summary and trend charts switch to one column so the first plot arrives earlier.

### Named Rules

**The Working Canvas Rule.** Navigation can be dark and immersive; the work area stays light, calmer, and highly scannable.

**The Dense-But-Decidable Rule.** A screen may show many metrics, but each metric must support a decision or a traceable fact.

**The Canvas Grid Rule.** Grid or line-field backgrounds belong only to actual canvas, scene, map, or measurement surfaces; page shells use plain tonal surfaces.

**The Hug Rule.** Content cards size to their content. A card never stretches to fill the viewport just to avoid whitespace, because the leftover height lands inside the card as a blank band. Only two surfaces are allowed to fill: the 3D scene stage and the sampling trend wall. Whitespace belongs on the canvas, not inside a panel.

## Elevation & Depth

The system uses two depth mechanisms and keeps them apart. Glass chrome earns depth from a blurred backdrop, a 1px inner light edge, and a top inset highlight — real refraction, not a glow. White data surfaces earn depth from a 1px hairline boundary, a top inner highlight, and a soft diffusion shadow with no blur of what is behind them. The 3D greenhouse scene runs in daylight: a light sky backdrop, a soft edge vignette, a subtle measurement grid, and white glass HUD chips.

### Shadow Vocabulary

- **Operational card** (`0 1px 2px rgb(9 62 54 / 4%), 0 10px 28px rgb(9 62 54 / 7%), inset 0 1px 0 rgb(255 255 255 / 92%)`): White data surfaces at rest.
- **Glass window** (`0 26px 70px rgb(9 62 54 / 16%), inset 0 1px rgb(255 255 255 / 88%)`): The floating application frame.
- **Glass panel** (`0 12px 30px rgb(9 62 54 / 8%), inset 0 1px 0 rgb(255 255 255 / 82%)`): Navigation chrome, top bar, drawers, scene HUD.
- **Dispatch action** (`0 10px 22px rgb(194 90 43 / 24%)`): Orange execution buttons.
- **Primary action** (`0 10px 22px rgb(14 124 107 / 20%)`): Frost-green buttons and active selection.

### Named Rules

**The No Neon Rule.** Glows are not a default depth system. Depth comes from offset, blur, boundary, and tonal separation.

**The One Surface Rule.** A surface declares either glass or card stock, never both. A white data card carries a hairline boundary and an inset highlight; it never carries `backdrop-filter`.

**The Refraction Rule.** Glass surfaces must show a 1px inner light edge plus a top inset highlight, so the material reads as an edge rather than a haze.

## Shapes

The form language is softly engineered and deliberately two-tiered. Glass chrome uses the largest radii: 28px for the floating application window, 22px for the navigation rail and panel, and 20–22px for the work surface. Data surfaces use 18px for cards and chart containers, 12–14px for inner controls and list rows, and 999px for selectable pills and status tags. Borders stay at 1px: white at 72% opacity on glass, `#e2efe9` hairline on white card stock. Nested cards are avoided; grouping uses section boundaries, spacing, and surface tone.

## Components

### Buttons

- **Shape:** Primary actions use a pill or 12px rounded control depending on context; secondary actions use 8–12px rounded controls.
- **Primary:** Frost-green gradient from `#1e957d` to `#0e7c6b`, white text, 14px radius, 40–44px height. Execution actions (dispatch, run, AI entry) use the dispatch-orange gradient from `#d4703e` to `#b24e22` instead, and only one appears per screen.
- **Hover / Focus:** The fill deepens, the shadow tightens, and focus uses a 2px frost-green outline with a 2px offset.
- **Secondary:** White surface, hairline border, body-ink label; hover shifts to a frost wash.
- **Destructive:** Danger soft background with red text and a visible destructive label; never use color alone.

### Chips

- **Style:** Status tags use semantic soft backgrounds with a darker semantic text and a 1px semantic border.
- **State:** Selected module filters use the current module accent; state tags use success, warning, danger, or information semantics rather than module color.

### Cards / Containers

- **Corner Style:** 16px radius for operational cards and 20px for shell-level containers.
- **Background:** White surfaces on a cool-gray canvas; dark surfaces only for navigation, scene HUD, and overlays.
- **Shadow Strategy:** Use the operational card shadow at rest; elevate only on hover, focus, or active interaction.
- **Border:** 1px cool-gray border for separation; no heavy card outlines.
- **Internal Padding:** 10–16px for operational cards; 18–24px for major modules.

### Inputs / Fields

- **Style:** 8px radius, `#f9fafb` surface, 1px `#e4e7ec` border, strong ink value.
- **Focus:** Module accent border plus a 3px translucent accent ring.
- **Error / Disabled:** Danger text and border for errors; reduced opacity, disabled semantics, and no action for disabled fields.

### Navigation

Two levels, both detached glass. The **icon rail** is 72px wide with a 34px radius and carries icons only — no labels, no brand block, no green fill. Its icons are slate-blue (`#7b93a6`, 24px, 1.6 stroke) and the current module is marked with a soft white capsule and a darker `#46637a` icon, never a color fill; the module name stays available through `title` and `aria-label`.

The **secondary panel** is 300px wide with a 28px radius and reads as a list, not a card grid: 64px rows of 23px slate-blue icon + 16px/500 label + light chevron, separated by whitespace rather than dividers, with the row description kept in the DOM for assistive tech and hidden visually. The top of the panel carries one quiet status line (`数字孪生底座 · 4/4 节点在线`) closed by a hairline rule, and the foot carries the flat `系统协同指数` block under a matching rule.

**The active row is a rounded solid green block** (`#2f9a72`, 16px radius, inset 10px from the panel edges) carrying a soft green shadow, a white icon, and a white 600-weight label. Hover rows take the same radius and a white-wash fill. Glass surfaces use `rgb(255 255 255 / 56%)` and `rgb(255 255 255 / 72%)` with a 1px inner light edge and a top inset highlight. Mobile navigation becomes a 60px icon rail with a 300px rounded glass overlay drawer that slides fully off-canvas.

### Modals

Modals are one of the few surfaces that legitimately use glass, because they float above the workbench: `rgb(255 255 255 / 92%)`, 28px radius, a 1px white border, a 24px backdrop blur, and a `0 32px 80px rgb(9 62 54 / 22%)` shadow with a top inset highlight. The scrim is `rgb(18 36 30 / 28%)` with a 6px blur.

Inside a modal the surface rules go back to card stock: list rows are 16px white cards with a hairline border, option tiles are 14px, inputs and selects are 12px, and the selected state is a mint wash (`#e2f0ec`) with a `rgb(14 124 107 / 30%)` border — never a dark fill. Modal headers and footers are transparent and closed by hairline rules rather than painted bands.

This applies to 温室与作物档案 (`GreenhouseManagerDialog`), 环境阈值设置 (`threshold-dialog`), and the AI drawer sheet. Native scrollbars and date pickers are themed by declaring `color-scheme: light` on the root; a leftover `color-scheme: dark` from the previous world renders them black.

### Charts

Charts are custom SVG for bounded datasets. Temperature uses red-orange, humidity uses information blue, light uses amber, and soil moisture uses success green. Grid lines stay low-contrast, current values and min/max labels are visible, and chart cards keep the metric name, unit, and timestamp close to the plot. Charts must expose a textual summary and never rely on hue alone.

### Three.js Greenhouse

The digital twin is an immersive full-width scene rendered in daylight, matching the physical subject: a light sky backdrop, an aluminium structural frame, glass panels with real transparency, green crop beds, amber equipment markers, a faint measurement grid, and white glass HUD chips. The home cover runs the same world full-viewport with a light glass top bar, chapter rail, and hotspot labels. Scene interactions must support drag, wheel and touch zoom, hotspot selection, mode switching, reset, and reduced motion; the cover adds a very light pointer parallax (dial 4–5) that must disable itself entirely under `prefers-reduced-motion`.

### AI Summary

AI 总结 is a first-class module next to 生产指挥 and 质量溯源, with its own nav rail entry, secondary rows (`生成总结` / `AI 问答` / `分析框架` / `历史记录`), and a dedicated route per section. The workbench shares the data-card language of the other modules: a hero band whose title follows the current section, framework selector rows, an instruction field, numbered result rows on a mint-tinted surface, and a history list bounded to 1080px. Cards hug their content and never stretch to fill the viewport.

The AI 问答 section is a two-column surface: a quiet settings card on the left (会话号, 当前模型, 已问条数, 可这样问, 清空对话) and the thread plus composer on the right. User messages are mint-tinted and right-aligned; assistant messages sit on the same tinted surface used by result rows, left-aligned, with the source badge and timestamp on the header line. The composer is a single field row with one mint send button, and `Enter` sends while `Shift+Enter` wraps.

Because the platform now calls a real model, every AI output carries its provenance. `POST /api/ai/summary` and `POST /api/ai/chat` return `source` (`deepseek` or `local`), the resolved `model`, the latency, the data sections that were actually read, and a `notice` when the call degraded. The interface must show all of it: a mint source badge for model output, a neutral one for local fallback, and an orange-tinted notice bar stating the reason. Never present a local fallback as model output, and never show a conclusion without its data scope.

The drawer remains the in-place shortcut from every other module: a white, right-side work surface with an orange entry action, framework rows, an instruction field, thinking state, result surface, and history list. It must not cover the primary control or trap focus, and its entry button is hidden inside the AI module itself so the module never offers a duplicate of the page it is already on. Drawer and page read the same `useAiSummary` state, so a summary generated in either place shows up in both, and the session is mirrored to `sessionStorage` (`smart-greenhouse-ai-summary`) so the history survives a refresh. The drawer footer carries the single action that promotes the quick view into the full page.

## Do's and Don'ts

### Do:

- **Do** keep glass on chrome, drawers, modals, and the scene HUD; keep data on solid white card stock.
- **Do** reserve Dispatch Orange for immediate execution actions, at most one per screen.
- **Do** use Frost Green for identity, selection, and success; module accents only for orientation.
- **Do** give every glass surface a 1px inner light edge plus a top inset highlight.
- **Do** use semantic green, amber, red, and blue for state; always pair color with text or icon.
- **Do** use DIN Next LT Pro for numbers, timestamps, hashes, latency, and metrics.
- **Do** keep operational cards at 18px radius and shell-level glass surfaces at 20–28px radius.
- **Do** provide loading, empty, error, disabled, focus, and success states for interactive workflows.
- **Do** verify every dashboard at desktop, laptop, 390px mobile, and with reduced motion.

### Don't:

- **Don't** use purple-blue gradients, neon outer glows, or pure black surfaces.
- **Don't** put `backdrop-filter` on a data card, table, or metric tile; glass on data is decorative blur.
- **Don't** use Dispatch Orange for navigation, links, headings, or decoration.
- **Don't** use emoji as structural icons or status symbols.
- **Don't** use color alone to communicate risk, chain, certificate, or device state.
- **Don't** draw decorative page-wide grid or line-field backgrounds; reserve them for actual canvas, scene, map, or measurement surfaces.
- **Don't** create nested cards or equal-card layouts when a section boundary, row, or table communicates the hierarchy.
- **Don't** shrink operational text below 9px or control text below 11px.
- **Don't** place a shadow, 1px border, and glass blur on the same surface without a functional reason.
- **Don't** let hover-only interactions carry primary controls; provide click, tap, and keyboard alternatives.
