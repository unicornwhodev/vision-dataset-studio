FROM eclipse-temurin:21-jdk-noble

ARG ANDROID_CLI_VERSION=15859902
ARG ANDROID_CLI_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ARG GRADLE_VERSION=9.3.1
ARG GRADLE_SHA256=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    VDS_GRADLE_BIN=gradle \
    PATH=/opt/gradle/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl git python3 unzip zip libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*

# SDK licences are accepted by the operator when launching the publication workflow.
RUN curl -fL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLI_VERSION}_latest.zip" -o /tmp/android-cli.zip \
    && echo "${ANDROID_CLI_SHA256}  /tmp/android-cli.zip" | sha256sum -c - \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" /tmp/android-cli \
    && unzip -q /tmp/android-cli.zip -d /tmp/android-cli \
    && mv /tmp/android-cli/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
    && rm -rf /tmp/android-cli /tmp/android-cli.zip
RUN yes | sdkmanager --licenses >/dev/null
RUN sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'

RUN curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && echo "${GRADLE_SHA256}  /tmp/gradle.zip" | sha256sum -c - \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
    && rm /tmp/gradle.zip \
    && git config --system --add safe.directory /source

LABEL org.opencontainers.image.title="Vision Dataset Studio Android build tools" \
      org.opencontainers.image.source="https://github.com/unicornwhodev/vision-dataset-studio" \
      org.opencontainers.image.description="JDK 21, Android SDK 36 and Gradle 9.3.1; no application data, credentials or model weights."
WORKDIR /source
CMD ["bash"]
