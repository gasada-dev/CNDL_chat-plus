# CNDL_chat+ Design System

## 1. Atmosphere & Identity

A compact Minecraft-native control surface: dark translucent panels, crisp one-pixel borders,
and a single violet accent preserve the game's visual language while keeping dense chat tools clear.

## 2. Color

All UI colors come from `ThemeTokens`, backed by current `ThemeManager` theme: background for
screen wash; surfaces for depth; borders for outlines; accent for selection, focus, and headings;
text for hierarchy; success, warning, and danger for status. Default-theme values reproduce previous
palette, including high-contrast black, gray, white, and red chat-overlay controls.

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
- `ThemeScreen`: owo-ui theme selector on Minecraft 26.2 only; 1.21.11 does not open it.

## 6. Motion & Interaction

Use Minecraft's immediate native widget transitions only. Hover, focus, active, disabled, and selected
states are communicated by existing surface, border, text, and accent changes; no decorative motion.

## 7. Depth & Surface

Use mixed tonal shift and one-pixel borders. Background sits behind surfaces; widgets use secondary
and hover surfaces. owo-ui uses theme radius tokens; vanilla panels retain flat fills. Do not add
shadows, textures, gradients, or new materials.

## 8. Accessibility Constraints & Accepted Debt

Controls keep native keyboard focus and narration labels, status errors remain visible, destructive
actions are explicitly labeled, and compact layouts must not overlap. ALL chat remains reachable.

Accepted constraint: Minecraft's bitmap font and Unicode gear glyph limit icon fidelity; they are kept
to match the host game and avoid external assets or dependencies.
