# PHENOX RUSH 1.0.0

Native Android GPS walking tracker by KeMa Se’ Phenomenal Studios.

- Kotlin + Jetpack Compose dashboard
- Foreground GPS location service with persistent notification
- GPS point capture and local Room database
- Distance accumulation with accuracy and speed sanity filters
- Walk session history
- Minimum Android 8.0 (API 26), compile/target API 35

GitHub Actions builds a debug APK and uploads it as the workflow artifact named PHENOX-RUSH-DEBUG-APK. The APK is debug-signed for direct device testing, not Play Store release.

This is an initial test build. Route-map rendering, GPX export, robust pause/resume and crash recovery, full permission UX, measured dashboard metrics and device acceptance testing remain outstanding.
