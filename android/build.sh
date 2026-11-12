#!/usr/bin/env bash
set -euo pipefail
JDK="/b/Program Files/Java/jdk-17"
SDK="/b/Program Files/Android/Sdk"
BT="$SDK/build-tools/37.0.0"
JAR="$SDK/platforms/android-36/android.jar"
APP_KS="${KEYSTORE:-release.keystore}"
cd "$(dirname "$0")"
rm -rf out && mkdir -p out/classes out/dex
mkdir -p out/res && "$BT/aapt2.exe" compile --dir res -o out/res/res.zip
"$BT/aapt2.exe" link -o out/base.apk -I "$(cygpath -w "$JAR")" --manifest AndroidManifest.xml out/res/res.zip
"$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -classpath "$(cygpath -w "$JAR")" -d out/classes $(find src -name '*.java') 2>&1 | grep -v "warning: \[options\]" || true
mapfile -t CLS < <(find out/classes -name "*.class")
"$JDK/bin/java" -cp "$(cygpath -w "$BT/lib/d8.jar")" com.android.tools.r8.D8 --release --min-api 33 --lib "$(cygpath -w "$JAR")" --output out/dex "${CLS[@]}"
cp out/base.apk out/unaligned.apk
python -c "import zipfile;z=zipfile.ZipFile('out/unaligned.apk','a',zipfile.ZIP_DEFLATED);z.write('out/dex/classes.dex','classes.dex');z.close()"
"$BT/zipalign.exe" -f 4 out/unaligned.apk out/aligned.apk
KS="$APP_KS"
[ -f "$KS" ] || "$JDK/bin/keytool" -genkeypair -keystore "$KS" -storepass android -keypass android -alias clockbridge -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Clock Bridge" >/dev/null 2>&1
"$JDK/bin/java" -jar "$(cygpath -w "$BT/lib/apksigner.jar")" sign --ks "$KS" --ks-pass pass:android --out out/app.apk out/aligned.apk
ls -la out/app.apk
