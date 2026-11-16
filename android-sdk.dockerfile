FROM ubuntu:22.04

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    libzip4 \
    python3 \
    python3-pip \
    openjdk-17-jdk \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_API_LEVEL=34
ENV ANDROID_BUILD_TOOLS=34.0.0

RUN mkdir -p $ANDROID_HOME/cmdline-tools
WORKDIR /tmp

RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d /tmp/cmdline-tools-extract && \
    cp -r /tmp/cmdline-tools-extract/cmdline-tools/* $ANDROID_HOME/cmdline-tools/ && \
    mkdir -p $ANDROID_HOME/cmdline-tools/latest && \
    mv $ANDROID_HOME/cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/

RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
RUN $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-$ANDROID_API_LEVEL" \
    "build-tools;$ANDROID_BUILD_TOOLS" \
    "platform-tools" \
    "ndk;26.1.10909125"

# Install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip -O gradle.zip && \
    unzip -q gradle.zip -d /opt && \
    rm gradle.zip
ENV PATH=$PATH:/opt/gradle-8.5/bin

# Set up working directory
WORKDIR /workspace

# Default command
CMD ["/bin/bash"]