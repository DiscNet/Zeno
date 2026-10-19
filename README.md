# ZENO.

**Visual website builder for Android.**

ZENO is an independent, mobile-first editor for designing web pages visually and exporting a real HTML/CSS project. The codebase is native Kotlin and does not depend on a hosted backend.

## Current milestone — 0.4.0 (experimental)

- Visual page canvas with pinch-to-zoom, panning, selection, dragging, four resize handles, proportional image resizing and alignment snapping.
- Add text, shapes, buttons and imported images. Adjust position, size, radius, visibility, lock, color and opacity with a HSV/HEX picker.
- Organize layers with live thumbnails; change stacking, visibility and locking.
- Responsive artboard composition with prominent two-column hero layout on tablet/desktop, larger typography, fluid artwork and vertical mobile layout. Per-breakpoint editable geometry is stored without drift; generated HTML has breakpoint CSS.
- Native high-resolution glass lens with separable blur, smooth inward refraction near the rim and specular highlights. Web export uses CSS backdrop-filter and a translucent surface. Page backgrounds: solid color, grid, gradient, or imported image.
- Undo and redo with local automatic draft saving.
- Import TTF/OTF fonts. Open or save editable JSON projects with embedded images/fonts through Android's document picker.
- Preview the generated page in an embedded WebView (JavaScript disabled).
- Export a ZIP containing index.html and project.zeno.json.
- GitHub Actions runs layout regression unit tests and builds a debug APK on pushes and manual runs.

## Important limits

This is an experimental editor, **not yet a full Figma or production website builder**. Layouts still use absolute positioning with heuristic hero reflow, not a full Figma-like layout engine. Arbitrary compositions require manual breakpoint editing; true flex/grid responsive constraints, text wrapping on the canvas, advanced typography, design tokens, nested components, interactive navigation, JavaScript behavior, multi-page websites and React export are not yet implemented. Imported assets are embedded as base64 into JSON/HTML and large images/fonts can increase memory usage; imported images are compressed or resized. Buttons are visual only. No cloud synchronization or collaboration is included.

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

In GitHub, open the **Actions** tab, select **Android APK**, and download the **ZENO-v0.4.0-debug** artifact after a successful workflow. The debug APK is intended for development/testing, not Play Store distribution or permanent release signing.

## Roadmap

1. Constraint-based layouts using containers, Flexbox and CSS Grid.
2. Multi-page projects, components, assets and advanced layer management.
3. Styles, spacing, typography, responsive breakpoints and inspectors.
4. Prototype interactions, animations and code/export improvements.
5. Testing, performance, accessibility, release signing and publishing.

ZENO is separate from Noise! and will have its own build and release lifecycle.

## Reference and independence

Design architecture studied against [Figcraft](https://github.com/storytold/figcraft): auto layout sizing modes, responsive composition and a high-resolution optical glass pipeline. ZENO remains independently implemented in Kotlin for Android; it does not incorporate Rust code from Figcraft. The native pixel-processing glass effect may affect frame rates on slower devices and is visually different from CSS backdrop filters.
