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
        // Pleos Connect SDK (공식 문서: NaviHelper > importing-sdk)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroup("ai.pleos.playground") }
        }
    }
}
rootProject.name = "BabyRelay"
include(":app")
