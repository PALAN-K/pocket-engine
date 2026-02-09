FROM eclipse-temurin:17-jdk-focal

ENV ANDROID_SDK_ROOT /opt/android-sdk
ENV PATH ${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# 필수 라이브러리 설치
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    python3 \
    curl \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Android Command Line Tools 설치
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/tools.zip \
    && unzip /tmp/tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/tools.zip

# 라이선스 동의 및 필수 컴포넌트 설치 (SDK 34, NDK 26)
RUN yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses \
    && /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125"

# Gradle 설치
ENV GRADLE_VERSION 8.5
RUN wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip \
    && unzip /tmp/gradle.zip -d /opt \
    && mv /opt/gradle-${GRADLE_VERSION} /opt/gradle \
    && rm /tmp/gradle.zip
ENV PATH ${PATH}:/opt/gradle/bin

WORKDIR /app
