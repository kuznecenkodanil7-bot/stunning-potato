#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle not found. Install JDK 21 and Gradle 8.x."
  exit 1
fi
gradle clean build
