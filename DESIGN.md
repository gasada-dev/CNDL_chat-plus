# CNDL_chat+ Design System

## 1. Atmosphere & Identity

A compact Minecraft-native control surface: dark translucent panels, crisp one-pixel borders,
and a single violet accent preserve the game's visual language while keeping dense chat tools clear.

## 2. Color

All UI colors come from `UiConstants`: `BACKGROUND` for screen wash; `SURFACE`, `SURFACE_ALT`,
and `SURFACE_HOVER` for depth; `BORDER` for outlines; `ACCENT` and `ACCENT_SOFT` for selection,
focus, and headings; `TEXT` and `MUTED` for hierarchy; `SUCCESS`, `WARNING`, and `ERROR` for status.
Chat-overlay controls retain their established high-contrast black, gray, white, and red palette.

## 3. Typography

Use Minecraft's bundled bitmap font and `Component` styles. Default text is the native 8-9px line;
headers use the same face with an accent underline. Strong icon controls may use bold `Component`
styling when the glyph otherwise lacks enough visual weight.

## 4. Spacing & Layout

The base unit is 4px. Standard fields and buttons are 20px high; list rows use 22-28px rhythm;
panel insets are 16-18px; column gaps are 8-16px. Panels center within the viewport and cap their
width. Compact mode begins near 520px and replaces side-by-side content with paging or stacked rows.

## 5. Components

- `ScreenChrome`: translucent background, bordered panel, centered title, accent underline.
- `StyledButton`: alternate surface, border, hover accent, muted disabled text, native focus behavior.
- `StyledCycleButton`: `StyledButton` states with a visible label/value pair.
- `StyledEditBox`: native edit behavior with border and accent focus outline.
- Pagination: adjacent `<` and `>` buttons with disabled boundary states; content remains reachable.
- Status text: centered success/warning/error feedback; failed mutations stay on the current screen.

## 6. Motion & Interaction

Use Minecraft's immediate native widget transitions only. Hover, focus, active, disabled, and selected
states are communicated by existing surface, border, text, and accent changes; no decorative motion.

## 7. Depth & Surface

Use mixed tonal shift and one-pixel borders. `BACKGROUND` sits behind `SURFACE`; widgets use
`SURFACE_ALT` and `SURFACE_HOVER`. Do not add shadows, textures, gradients, or new materials.

## 8. Accessibility Constraints & Accepted Debt

Controls keep native keyboard focus and narration labels, status errors remain visible, destructive
actions are explicitly labeled, and compact layouts must not overlap. ALL chat remains reachable.

Accepted constraint: Minecraft's bitmap font and Unicode gear glyph limit icon fidelity; they are kept
to match the host game and avoid external assets or dependencies.
