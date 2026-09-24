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
    // The classic Interpreter API and JNI are built from the same public tag.
    exclusiveContent {
      forRepository { maven { url = uri("dist/native-litert/maven") } }
      filter { includeVersion("com.unicornwhodev.thirdparty", "litert-interpreter", "2.2.0-vds16k2") }
    }
    exclusiveContent {
      forRepository { maven { url = uri("dist/native-graphics/maven") } }
      filter { includeVersion("androidx.graphics", "graphics-path", "1.0.1-vds16k1") }
    }
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
include(":release-qa")
