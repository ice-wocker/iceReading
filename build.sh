#!/bin/bash
# 冰读 iceReading 构建脚本
# 使用 android-tools(自带的 aapt/dx)
set -e
cd "$(dirname "$0")"

ANDROID_JAR=/data/data/com.termux/files/home/apkbuild/android.jar
AAPT=/data/data/com.termux/files/usr/bin/aapt
DX=/data/data/com.termux/files/usr/bin/dx
APKSIGNER=/data/data/com.termux/files/usr/bin/apksigner
ZIPALIGN=/data/data/com.termux/files/usr/bin/zipalign
KEYTOOL=/data/data/com.termux/files/usr/bin/keytool
ECJ=/data/data/com.termux/files/usr/share/dex/ecj.jar

[ -f "$ANDROID_JAR" ] || { echo "❌ android.jar 不存在"; exit 1; }
[ -x "$AAPT" ] || { echo "❌ aapt 不存在"; exit 1; }
[ -x "$DX" ] || { echo "❌ dx 不存在"; exit 1; }
[ -x "$APKSIGNER" ] || { echo "❌ apksigner 不存在"; exit 1; }
[ -x "$ZIPALIGN" ] || { echo "❌ zipalign 不存在"; exit 1; }
[ -x "$KEYTOOL" ] || { echo "❌ keytool 不存在"; exit 1; }

WORK=build
rm -rf $WORK
mkdir -p $WORK/gen $WORK/classes $WORK/dex

echo "==== 1) aapt 打包 ===="
$AAPT package -f -m -J $WORK/gen -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F $WORK/res.zip 2>&1 | tail -10
[ -f $WORK/res.zip ] || { echo "❌ aapt 失败"; exit 1; }

echo "==== 2) ecj 编译 ===="
SRC_FILES=$(find src -name '*.java')
GEN_FILES=$(find $WORK/gen -name '*.java' 2>/dev/null)
ALL_JAVA="$SRC_FILES $GEN_FILES"
dalvikvm -Xmx512m -cp $ECJ org.eclipse.jdt.internal.compiler.batch.Main \
  -proc:none -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" \
  -d $WORK/classes $ALL_JAVA 2>&1 | tail -30
[ ${PIPESTATUS[0]} -eq 0 ] || { echo "❌ ecj 失败"; exit 1; }

echo "==== 3) dx 转 dex ===="
$DX --dex --output=$WORK/dex/classes.dex $WORK/classes 2>&1 | tail -5
[ -f $WORK/dex/classes.dex ] || { echo "❌ dx 失败"; exit 1; }

echo "==== 4) 合并 apk ===="
cp $WORK/res.zip icereading-unsigned.apk
(cd $WORK/dex && zip -q -j ../../icereading-unsigned.apk classes.dex)
# assets 必须在根目录
(cd assets && zip -q -r ../icereading-unsigned.apk .)

echo "==== 5) zipalign ===="
$ZIPALIGN -f 4 icereading-unsigned.apk icereading-aligned.apk

echo "==== 6) 签名 ===="
KS=keystore.jks
if [ ! -f "$KS" ]; then
  $KEYTOOL -genkey -v -keystore $KS -alias icereading -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass icereading123 -keypass icereading123 \
    -dname "CN=iceReading, OU=App, O=iceReading, L=Shanghai, ST=Shanghai, C=CN" 2>&1 | tail -3
fi
$APKSIGNER sign --ks $KS --ks-pass pass:icereading123 --key-pass pass:icereading123 --out icereading.apk icereading-aligned.apk 2>&1 | tail -3
$APKSIGNER verify icereading.apk && echo "✅ 签名验证通过"

rm -f icereading-unsigned.apk icereading-aligned.apk

SIZE=$(stat -c%s icereading.apk 2>/dev/null || stat -f%z icereading.apk)
echo "==== ✅ 成品: icereading.apk ($SIZE bytes / $((SIZE/1024))KB) ===="
