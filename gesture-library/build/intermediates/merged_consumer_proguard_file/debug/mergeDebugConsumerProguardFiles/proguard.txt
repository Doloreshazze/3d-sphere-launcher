# Consumer ProGuard rules for gesture-library.
# These rules are included in the consuming app's ProGuard configuration.

# Keep all public API classes and members of the gesture library.
-keep public class com.antigravity.gesture.** { public *; }

# Keep MediaPipe classes used via reflection.
-keep class com.google.mediapipe.** { *; }
