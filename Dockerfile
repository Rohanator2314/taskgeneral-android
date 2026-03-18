FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/opt/java/openjdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin

# Install required packages
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    openjdk-17-jdk \
    curl \
    git \
    python3 \
    && rm -rf /var/lib/apt/lists/*

# Download and setup Android SDK
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

# Accept licenses and install required SDK components
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses || true
RUN $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-35" \
    "platforms;android-34" \
    "platforms;android-36" \
    "build-tools;35.0.0" \
    "platform-tools"

# Install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip -O /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    rm /tmp/gradle.zip && \
    ln -s /opt/gradle-8.9/bin/gradle /usr/local/bin/gradle

# Install Rust and Cargo
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable
ENV PATH="$HOME/.cargo/bin:$PATH"

# Set working directory
WORKDIR /workspace

# Copy project files
COPY . /workspace/

# Build the Android app
RUN ./gradlew assembleDebug --no-daemon

CMD ["echo", "Build complete"]
