pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}


dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // Source-built Flex keeps Save/Restore and gradients on 16 KB devices.
    // Never fall back to the incompatible upstream 2.16.1 native binaries.
    exclusiveContent {
      forRepository { maven { url = uri("dist/native-flex/maven") } }
      filter { includeVersion("org.tensorflow", "tensorflow-lite-select-tf-ops", "2.16.1-vds16k1") }
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "Vision Dataset Studio"

include(":app")
