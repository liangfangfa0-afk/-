pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MotionGuard"
include(":app", ":liquidglass")
project(":liquidglass").projectDir = file("libs/LiquidGlassBar/liquidglass")
