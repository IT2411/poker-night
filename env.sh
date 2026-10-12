#!/usr/bin/env bash

# 1. Detect or set JAVA_HOME (JDK 17 recommended)
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
        export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
    elif [ -d "$HOME/.sdkman/candidates/java/current" ]; then
        export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
    fi
fi

# 2. Detect or set ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "/usr/lib/android-sdk" ]; then
        export ANDROID_HOME="/usr/lib/android-sdk"
    fi
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"

# 3. Add tools to PATH
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -n "$ANDROID_HOME" ]; then
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
fi

# 4. Write local.properties for Gradle if ANDROID_HOME is set
if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
    echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

echo "=== Environment Loaded ==="
echo "JAVA_HOME:    ${JAVA_HOME:-'Not found (install openjdk-17)'}"
echo "ANDROID_HOME: ${ANDROID_HOME:-'Not found (set path to Android SDK)'}"
java -version 2>&1 | head -n 1
echo "=========================="
