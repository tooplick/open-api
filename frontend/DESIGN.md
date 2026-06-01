---
name: Lumina Nexus
colors:
  surface: '#fbf9f8'
  surface-dim: '#dcd9d9'
  surface-bright: '#fbf9f8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3f3'
  surface-container: '#f0eded'
  surface-container-high: '#eae7e7'
  surface-container-highest: '#e4e2e2'
  on-surface: '#1b1c1c'
  on-surface-variant: '#45474a'
  inverse-surface: '#303030'
  inverse-on-surface: '#f3f0f0'
  outline: '#76777b'
  outline-variant: '#c6c6cb'
  surface-tint: '#5c5e63'
  primary: '#5a5c61'
  on-primary: '#ffffff'
  primary-container: '#727479'
  on-primary-container: '#fdfcff'
  inverse-primary: '#c5c6cc'
  secondary: '#5e5e60'
  on-secondary: '#ffffff'
  secondary-container: '#e3e2e4'
  on-secondary-container: '#646466'
  tertiary: '#605c51'
  on-tertiary: '#ffffff'
  tertiary-container: '#797469'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1e2e8'
  primary-fixed-dim: '#c5c6cc'
  on-primary-fixed: '#191c20'
  on-primary-fixed-variant: '#44474b'
  secondary-fixed: '#e3e2e4'
  secondary-fixed-dim: '#c7c6c8'
  on-secondary-fixed: '#1b1c1d'
  on-secondary-fixed-variant: '#464749'
  tertiary-fixed: '#e8e2d4'
  tertiary-fixed-dim: '#ccc6b9'
  on-tertiary-fixed: '#1e1b13'
  on-tertiary-fixed-variant: '#4a473d'
  background: '#fbf9f8'
  on-background: '#1b1c1c'
  surface-variant: '#e4e2e2'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.04em
  display-lg-mobile:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  body-base:
    fontFamily: Geist
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-sm:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  code-snippet:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  label-caps:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.08em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 48px
  xl: 80px
  gutter: 24px
  margin: 32px
---

## Brand & Style

The design system is engineered for developers who value precision, speed, and clarity. It adopts a **Refined Architectural** aesthetic, moving away from high-contrast tech-noir toward a "Paper-Technical" interface that emphasizes focus and durability. 

The visual narrative centers on the concept of a "Daylight Workspace"—professional, open, and frictionless. By utilizing a light, monochromatic-leaning palette and soft, pill-shaped geometry, the system creates a high-legibility environment. This reduces visual fatigue during long sessions of API management and data analysis. The aesthetic is "quiet" but "structural," prioritizing content over decoration.

## Colors

The palette is rooted in a "Studio Light" environment. The foundation is a clean, near-white base that provides a neutral canvas for technical data.

- **Primary (Steel Grey):** Reserved for primary actions, active states, and critical navigation. It provides a grounded, stable focal point without the aggression of high-saturation colors.
- **Secondary (Cool Slate):** Used for supporting UI elements, icons, and non-interactive components to maintain a cohesive, low-vibrancy aesthetic.
- **Neutral:** A range of balanced greys defines the structural borders and secondary text. Borders use crisp, light-grey lines to maintain structure against the white surfaces.
- **Semantic Colors:** Success (Emerald), Warning (Amber), and Error (Rose) are used with higher saturation to remain clearly visible against the light background.

## Typography

This design system utilizes **Geist** for its systematic, technical, and neutral character. It provides the high-tech, precise feel required for a developer-centric tool. **JetBrains Mono** is introduced as a supporting font for code snippets, API keys, and technical logs to ensure maximum legibility of character-specific strings.

Hierarchy is established through weight and scale. In this light-themed environment, typography relies on varying shades of grey to denote importance rather than pure black. Use `label-caps` for section headers in sidebars or small metadata descriptors. Ensure all code-related content uses the monospaced variable to distinguish "system data" from "platform interface."

## Layout & Spacing

The layout follows a **Fixed Grid** model for dashboard views to ensure consistency in data visualization, while content-heavy pages (like documentation) utilize a centered fluid column for readability.

- **Grid:** 12-column system with a 24px gutter.
- **Vertical Rhythm:** An 8px baseline grid ensures spacious alignment, creating an open and airy feel that balances the technical density of the data.
- **Desktop:** Side navigation is fixed (240px), with the main content area utilizing the remaining space up to a 1440px max-width.
- **Mobile:** Elements reflow into a single column. Horizontal margins reduce to 16px. Side navigation collapses into a top-bar burger menu.

## Elevation & Depth

Depth is conveyed through **Subtle Tonal Layering** and soft ambient shadows, creating a sense of physical objects resting on a surface.

- **Surface Levels:** 
    - Level 0: Primary Background (#F6F7FD) - The base canvas.
    - Level 1: Elevated Surface (#FFFFFF) - Main Cards and active containers.
    - Level 2: Interactive Surface - Used for hover states or dropdown menus.
- **Shadows:** Use very soft, highly diffused shadows (e.g., 10% opacity) to lift cards from the background without creating harsh edges.
- **Outlines:** Use 1px borders in a light neutral grey for all containers. This provides necessary structure in a light environment where surfaces might otherwise bleed together.

## Shapes

The shape language is "Organic-Technical." Elements use a generous **Pill-Shaped** (16px / 1rem) corner radius. This provides an approachable, modern appearance that softens the density of technical data and distinguishes the platform as a next-generation developer tool.

- **Small elements (Checkboxes, Tags):** Fully rounded (pill) radius.
- **Standard elements (Buttons, Inputs, Cards):** 16px (1rem) radius.
- **Large containers (Modals):** 32px (2rem) radius.

## Components

### Buttons
Primary buttons use the Steel Grey background with white text and a pill-shaped radius. Secondary buttons are "Ghost" style: 1px light grey border with no background, transitioning to a very light grey fill on hover.

### Inputs & Search
Input fields use a white background with a 1px neutral border and 16px corner radius. On focus, the border deepens to the Primary Steel Grey with a very soft ambient outer glow.

### Cards & Data Visualization
Cards use the elevated white surface (#FFFFFF) and a subtle 1px border. Data visualizations (line charts for API usage) use thin 1.5pt lines in Steel Grey, with the Warm Cream (Tertiary) used as a background fill for area charts.

### Chips & Badges
Badges for status use a "Filled-Dim" style: a very low-opacity background of the semantic color with high-contrast text of the same color to ensure legibility against the light theme.

### Code Blocks
Code blocks use a secondary container background with the JetBrains Mono font. Syntax highlighting should follow a "Light-Studio" or "Solarized-Light" inspired theme to remain consistent with the neutral palette.