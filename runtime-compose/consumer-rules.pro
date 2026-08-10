# The adapter deliberately avoids a core dependency on Compose by discovering the
# AndroidComposeView semantics owner through its public, zero-argument getter.
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    androidx.compose.ui.semantics.SemanticsOwner getSemanticsOwner();
}
