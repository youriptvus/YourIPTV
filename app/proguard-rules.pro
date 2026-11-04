# YourIPTV release ProGuard/R8 rules.
# The default proguard-android-optimize.txt (referenced from build.gradle.kts) already minifies and
# optimizes; these rules keep the integrity/branding layer intact and stable.

# Keep the signing and integrity gate whole. Tree-shaking or aggressive inlining must not turn the
# brand lock into a no-op, and the member names of the custom-tool exception seam must stay stable.
-keep class com.youriptv.app.OfficialSigning { *; }
-keep class com.youriptv.app.SigningStatus { *; }
-keep class com.youriptv.app.BrandIntegrity { *; }
-keep class com.youriptv.app.BrandIntegrity$* { *; }
-keep class com.youriptv.app.ui.BrandAssets { *; }
-keep class com.youriptv.app.ui.BrandAssets$* { *; }

# org.json is provided by the Android platform at runtime (only pulled in as a test dependency).
-dontwarn org.json.**
