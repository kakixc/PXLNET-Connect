pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "sing-box"
include(":app")
include(":terminal-emulator")
project(":terminal-emulator").projectDir = file("third_party/termux-app/terminal-emulator")
include(":terminal-view")
project(":terminal-view").projectDir = file("third_party/termux-app/terminal-view")
