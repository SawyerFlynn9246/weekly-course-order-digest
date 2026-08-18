#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/weekly-course-digest-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java') $(find src/test/java -name '*.java')
java -cp "$BUILD_DIR" learning.store.WeeklyDigestServiceTest
