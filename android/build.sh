#!/usr/bin/env bash
# Builds out/app.apk. Works in Git Bash on Windows and on Linux/macOS (CI).
# Override JDK / SDK / KEYSTORE / KEYSTORE_PASS; set REQUIRE_KEYSTORE=1 to never generate a new key.
set -euo pipefail
JDK="${JDK:-/b/Program Files/Java/jdk-17}"
SDK="${SDK:-/b/Program Files/Android/Sdk}"
BT="$SDK/build-tools/37.0.0"
JAR="$SDK/platforms/android-36/android.jar"
APP_KS="${KEYSTORE:-release.keystore}"
KS_PASS="${KEYSTORE_PASS:-android}"
if command -v cygpath >/dev/null; then win() { cygpath -w "$1"; }; EXE=.exe; else win() { echo "$1"; }; EXE=; fi
PY=$(command -v python || command -v python3)
cd "$(dirname "$0")"
rm -rf out && mkdir -p out/classes out/dex
mkdir -p out/res && "$BT/aapt2$EXE" compile --dir res -o out/res/res.zip
"$BT/aapt2$EXE" link -o out/base.apk -I "$(win "$JAR")" --manifest AndroidManifest.xml out/res/res.zip
"$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -classpath "$(win "$JAR")" -d out/classes $(find src -name '*.java') 2>&1 | grep -v "warning: \[options\]" || true
mapfile -t CLS < <(find out/classes -name "*.class")
[ ${#CLS[@]} -gt 0 ] || { echo "javac produced no classes" >&2; exit 1; }
"$JDK/bin/java" -cp "$(win "$BT/lib/d8.jar")" com.android.tools.r8.D8 --release --min-api 33 --lib "$(win "$JAR")" --output out/dex "${CLS[@]}"
cp out/base.apk out/unaligned.apk
"$PY" -c "import zipfile;z=zipfile.ZipFile('out/unaligned.apk','a',zipfile.ZIP_DEFLATED);z.write('out/dex/classes.dex','classes.dex');z.close()"
"$BT/zipalign$EXE" -f 4 out/unaligned.apk out/aligned.apk
KS="$APP_KS"
if [ ! -f "$KS" ]; then
  [ "${REQUIRE_KEYSTORE:-}" != 1 ] || { echo "keystore $KS not found" >&2; exit 1; }
  "$JDK/bin/keytool" -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias clockbridge -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Clock Bridge" >/dev/null 2>&1
fi
"$JDK/bin/java" -jar "$(win "$BT/lib/apksigner.jar")" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --out out/app.apk out/aligned.apk
ls -la out/app.apk
