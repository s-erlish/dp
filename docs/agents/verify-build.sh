#!/usr/bin/env bash
# Build-verify gate for both clients. Usage:
#   bash docs/agents/verify-build.sh android    # Kotlin + resources + APK link
#   bash docs/agents/verify-build.sh desktop    # C# + AXAML
#   bash docs/agents/verify-build.sh both
#
# Prints BUILD result, warning count, and any warning NOT present in the recorded baseline.
# The bar is: build succeeds AND "NEW WARNINGS: 0". Line:col are stripped before comparing,
# because line numbers shift when code above them changes and that is not a regression.
#
# The baselines are a SUPERSET, and they must be re-recorded from a FULL build:
#   ./gradlew :app:assembleFdroidDebug --rerun-tasks     (not an incremental run)
#   dotnet build ... -c Release --no-incremental
# An incremental run only recompiles what changed, so it emits only that subset's warnings. A
# baseline taken from one makes the gate lie: the next agent whose change happens to recompile an
# untouched file is told it introduced warnings that were always there. Warnings that appear only
# sometimes — the Gradle build-script deprecations, which surface only when the build script itself
# is recompiled — belong in the baseline too. A baseline entry that does not appear costs nothing,
# because only NEW warnings fail the gate.
set -uo pipefail

DP=/home/user/dp
PC=/home/user/v2rayN
BASE_A="$DP/docs/agents/.baseline-warnings.txt"
BASE_D="$DP/docs/agents/.baseline-warnings-desktop.txt"
OUT="${TMPDIR:-/tmp}/verify-build-$$"
mkdir -p "$OUT"

# Strip ANSI, absolute paths, line:col and the MSBuild "[project]" suffix so two runs compare.
normalise() {
  sed -e 's/\x1b\[[0-9;]*m//g' \
      -e 's#file:///home/user/[^ ]*/\([A-Za-z0-9_.]*\.\(kt\|java\|xml\)\)#\1#g' \
      -e 's#/home/user/[^ (]*/\([A-Za-z0-9_.]*\.\(cs\|axaml\|csproj\)\)#\1#g' \
      -e 's/:[0-9]\+:[0-9]\+/:L:C/g' \
      -e 's/([0-9]\+,[0-9]\+\(,[0-9]\+,[0-9]\+\)\?)/(L,C)/g' \
      -e 's/ \[[^]]*\.csproj\]$//' \
      -e 's/[[:space:]]*$//' \
  | sort -u | grep -v '^$'
}

status=0

build_android() {
  export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  echo "=== ANDROID: ./gradlew :app:assembleFdroidDebug ==="
  # Serialised: agents share this build tree, and two concurrent Gradle runs on one project
  # contend for the same locks. Waiting for a turn is slow; interleaving is broken.
  echo "(waiting for the android build lock if another agent holds it)"
  flock /tmp/dep-android-build.lock \
    bash -c 'cd "'"$DP"'/V2rayNG" && ./gradlew :app:assembleFdroidDebug --no-daemon' \
    > "$OUT/android.raw" 2>&1
  local rc=$?
  if grep -q "BUILD SUCCESSFUL" "$OUT/android.raw"; then
    echo "BUILD: SUCCESSFUL"
  else
    echo "BUILD: FAILED (rc=$rc)"
    grep -E "^e: |error:|FAILURE:|Caused by|> Task .* FAILED" "$OUT/android.raw" | head -40
    status=1
    return
  fi
  # An UP-TO-DATE compile emits no warnings at all, which would make the check pass for free.
  # Say so out loud: a green run whose compiler never executed proves nothing about new code.
  if grep -qE "^> Task :app:compileFdroidDebugKotlin$" "$OUT/android.raw"; then
    echo "COMPILER: ran (Kotlin recompiled)"
  else
    echo "COMPILER: UP-TO-DATE - nothing recompiled, so this run proves nothing."
    echo "          Touch the files you changed, or run with --rerun-tasks, and verify again."
  fi
  grep -E "^w: " "$OUT/android.raw" | normalise > "$OUT/android.now"
  normalise < "$BASE_A" > "$OUT/android.base"
  echo "WARNINGS THIS RUN: $(wc -l < "$OUT/android.now")  (distinct baseline entries: $(wc -l < "$OUT/android.base"))"
  comm -13 "$OUT/android.base" "$OUT/android.now" > "$OUT/android.new"
  echo "NEW WARNINGS: $(wc -l < "$OUT/android.new")"
  if [ -s "$OUT/android.new" ]; then cat "$OUT/android.new"; status=1; fi
}

build_desktop() {
  export DOTNET_ROOT=/opt/dotnet PATH=/opt/dotnet:$PATH
  export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1
  # MSBuild keeps worker nodes alive after the build to speed up the next one. Those nodes inherit
  # the flock file descriptor below and outlive the `flock` process, so the lock is never released
  # and every later run of this script blocks forever on a build that finished minutes ago. Disable
  # node reuse: a few seconds per run buys a lock that actually unlocks.
  export MSBUILDDISABLENODEREUSE=1
  echo "=== DESKTOP: dotnet build v2rayN.Desktop -c Release ==="
  echo "(waiting for the desktop build lock if another agent holds it)"
  flock /tmp/dep-desktop-build.lock \
    bash -c 'cd "'"$PC"'/v2rayN" && dotnet build v2rayN.Desktop/v2rayN.Desktop.csproj -c Release' \
    > "$OUT/desktop.raw" 2>&1
  local rc=$?
  if grep -qE "^ *0 Error\(s\)" "$OUT/desktop.raw"; then
    echo "BUILD: SUCCESSFUL"
  else
    echo "BUILD: FAILED (rc=$rc)"
    grep -E ": error |error [A-Z]+[0-9]+" "$OUT/desktop.raw" | head -40
    status=1
    return
  fi
  if grep -qE "CoreCompile|CompileAvaloniaXaml" "$OUT/desktop.raw"; then
    echo "COMPILER: ran"
  else
    echo "COMPILER: up-to-date - if you changed files, force a rebuild (dotnet build --no-incremental)"
  fi
  grep -E ": warning " "$OUT/desktop.raw" | normalise > "$OUT/desktop.now"
  normalise < "$BASE_D" > "$OUT/desktop.base"
  echo "WARNINGS THIS RUN: $(wc -l < "$OUT/desktop.now")  (distinct baseline entries: $(wc -l < "$OUT/desktop.base"))"
  comm -13 "$OUT/desktop.base" "$OUT/desktop.now" > "$OUT/desktop.new"
  echo "NEW WARNINGS: $(wc -l < "$OUT/desktop.new")"
  if [ -s "$OUT/desktop.new" ]; then cat "$OUT/desktop.new"; status=1; fi
}

case "${1:-both}" in
  android) build_android ;;
  desktop|pc) build_desktop ;;
  both) build_android; echo; build_desktop ;;
  *) echo "usage: $0 [android|desktop|both]"; exit 2 ;;
esac

echo
if [ $status -eq 0 ]; then echo "VERIFY: PASS"; else echo "VERIFY: FAIL"; fi
echo "(raw logs in $OUT)"
exit $status
