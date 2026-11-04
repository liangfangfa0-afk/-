pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MotionGuard"
include(":app", ":liquidglass", ":prismal")
project(":liquidglass").projectDir = file("libs/LiquidGlassBar/liquidglass")
project(":prismal").projectDir = file("prismal-adapter")
