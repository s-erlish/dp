#!/usr/bin/env bash
# One-shot environment setup for a FRESH container. Idempotent; safe to re-run.
#
# Installs the Android SDK and the .NET SDK, initialises the desktop submodule, and regenerates
# the gitignored libv2ray type-check stub. After this, docs/agents/verify-build.sh passes on both
# platforms. Nothing here touches repository sources.
set -euo pipefail

echo "== 1/4 Android SDK -> /opt/android-sdk"
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -fsSL -o /tmp/cmdline-tools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  unzip -q -o /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --licenses >/dev/null 2>&1 || true
# NOTE: app/build.gradle.kts says compileSdk = 37, but the SDK package is called
# "platforms;android-37.0" - "platforms;android-37" does not exist and sdkmanager fails on it.
"$SDKM" --install "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" >/dev/null
echo "   platforms: $(ls "$ANDROID_HOME/platforms")"

echo "== 2/4 .NET SDK -> /opt/dotnet"
if [ ! -x /opt/dotnet/dotnet ]; then
  curl -fsSL -o /tmp/dotnet-install.sh https://dot.net/v1/dotnet-install.sh
  bash /tmp/dotnet-install.sh --channel 10.0 --install-dir /opt/dotnet --no-path >/dev/null
fi
echo "   dotnet $(DOTNET_ROOT=/opt/dotnet /opt/dotnet/dotnet --version)"

echo "== 3/4 GlobalHotKeys submodule (desktop build fails CS0246 without it)"
git -C /home/user/v2rayN submodule update --init --recursive >/dev/null
echo "   ok"

echo "== 4/4 libv2ray type-check stub -> V2rayNG/app/libs/libv2ray-stub.jar"
# The real native libv2ray.aar is published on 2dust/AndroidLibXrayLite releases, which is not
# reachable from this environment. This stub carries ONLY the class surface the app compiles
# against, so Kotlin type-checks. It is gitignored, must never be committed, and must never be
# referenced from app code. If a build fails on a missing libv2ray member, add it HERE - do not
# change app code to fit the stub.
SB=$(mktemp -d)
mkdir -p "$SB/src/go" "$SB/src/libv2ray" "$SB/out"
cat > "$SB/src/go/Seq.java" <<'EOF'
package go;
public final class Seq {
    private Seq() {}
    public static void setContext(android.content.Context ctx) {}
}
EOF
cat > "$SB/src/libv2ray/CoreCallbackHandler.java" <<'EOF'
package libv2ray;
public interface CoreCallbackHandler {
    long startup();
    long shutdown();
    long onEmitStatus(long l, String s);
}
EOF
cat > "$SB/src/libv2ray/ProcessFinder.java" <<'EOF'
package libv2ray;
public interface ProcessFinder {
    long findProcessByConnection(String network, String srcIP, long srcPort, String destIP, long destPort);
}
EOF
cat > "$SB/src/libv2ray/CoreController.java" <<'EOF'
package libv2ray;
public class CoreController {
    public boolean getIsRunning() { return false; }
    public void setIsRunning(boolean v) {}
    public void registerProcessFinder(ProcessFinder finder) {}
    public void startLoop(String configContent, int fd) throws Exception {}
    public void stopLoop() throws Exception {}
    public long measureDelay(String url) throws Exception { return -1L; }
    public String queryAllOutboundTrafficStats() { return ""; }
}
EOF
cat > "$SB/src/libv2ray/Libv2ray.java" <<'EOF'
package libv2ray;
public final class Libv2ray {
    private Libv2ray() {}
    public static void initCoreEnv(String envPath, String key) {}
    public static void reconcileBrowserDialer(String addr) {}
    public static String checkVersionX() { return ""; }
    public static long measureOutboundDelay(String config, String url) throws Exception { return -1L; }
    public static CoreController newCoreController(CoreCallbackHandler handler) { return new CoreController(); }
}
EOF
AJ="$ANDROID_HOME/platforms/android-37.0/android.jar"
javac -nowarn --release 17 -cp "$AJ" -d "$SB/out" $(find "$SB/src" -name '*.java')
mkdir -p /home/user/dp/V2rayNG/app/libs
( cd "$SB/out" && jar cf /home/user/dp/V2rayNG/app/libs/libv2ray-stub.jar go libv2ray )
rm -rf "$SB"
echo "   $(ls -l /home/user/dp/V2rayNG/app/libs/libv2ray-stub.jar | awk '{print $5" bytes"}')"

cat <<'EOF'

Done. Export these in every shell that builds:
  export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
  export DOTNET_ROOT=/opt/dotnet PATH=/opt/dotnet:$PATH
Then:
  bash /home/user/dp/docs/agents/verify-build.sh both
EOF
