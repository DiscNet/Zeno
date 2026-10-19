# ZENO.

**Visual website builder for Android.**

ZENO is an independent, mobile-first editor for designing web pages visually and exporting a real HTML/CSS project. The codebase is native Kotlin and does not depend on a hosted backend.

## Current milestone — 0.1.0 (experimental)

- Visual page canvas with pinch-to-zoom, panning, selection and dragging.
- Add text, shapes and buttons; edit text, coordinates, dimensions, font size and preset colors.
- Reorder selected layers to the front, inspect layer list, delete elements.
- Canvas presets for phone, tablet and desktop.
- Undo and redo with local automatic draft saving.
- Open or save editable JSON projects through Android's document picker.
- Preview the generated page in an embedded WebView (JavaScript disabled).
- Export a ZIP containing index.html and project.zeno.json.
- GitHub Actions builds a debug APK on pushes and manual runs.

## Important limits

This is an initial editor, **not yet a full Figma or production website builder**. Layouts use absolute positioning within a design-size artboard and scale proportionally; true flex/grid responsive constraints, text wrapping on the canvas, advanced typography, images, design tokens, nested components, interactive navigation, JavaScript behavior, multi-page websites and React export are not yet implemented. Buttons are visual only. No cloud synchronization or collaboration is included.

## Android project

- **Language:** Kotlin
- **Rendering:** native Android Canvas
- **Preview:** Android WebView
- **Export:** HTML/CSS + JSON in ZIP
- **Minimum Android:** API 26 (Android 8.0)
- **Build:** JDK 17, Android Gradle Plugin 8.11.1, Gradle 8.13, Android SDK 36
- **Package:** com.zeno.studio

With JDK 17 and Android SDK 36 installed, run the Gradle build with:

    gradle :app:assembleDebug

The APK is produced at:

    app/build/outputs/apk/debug/app-debug.apk

In GitHub, open the **Actions** tab, select **Android APK**, and download the **ZENO-v0.1.0-debug** artifact after a successful workflow. The debug APK is intended for development/testing, not Play Store distribution or permanent release signing.

## Roadmap

1. Constraint-based layouts using containers, Flexbox and CSS Grid.
2. Multi-page projects, components, assets and advanced layer management.
3. Styles, spacing, typography, responsive breakpoints and inspectors.
4. Prototype interactions, animations and code/export improvements.
5. Testing, performance, accessibility, release signing and publishing.

ZENO is separate from Noise! and will have its own build and release lifecycle.
