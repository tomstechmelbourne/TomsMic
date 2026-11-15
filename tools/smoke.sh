#!/bin/bash
# Emulator smoke test for Tom's Mic: install, open each page, record a few seconds, take screenshots, check it did not crash.
set -x
API=$1
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
P=com.tomstechsolutions.mic
M=$P/.ui.MainActivity
mkdir -p shots
adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 5; done
sleep 45
ok=0
for i in 1 2 3; do
  adb wait-for-device
  until adb shell pm path android >/dev/null 2>&1; do sleep 5; done
  if adb install -r -g "$APK" 2>&1 | grep -q Success; then ok=1; break; fi
  echo "install try $i failed, waiting"; sleep 40
done
[ $ok = 1 ] || { echo "INSTALL FAILED (emulator)"; exit 1; }
for perm in RECORD_AUDIO POST_NOTIFICATIONS READ_PHONE_STATE READ_CALL_LOG READ_CONTACTS READ_MEDIA_AUDIO; do adb shell pm grant $P android.permission.$perm 2>/dev/null; done
adb logcat -c
adb shell am start -W -n $M
sleep 3; adb exec-out screencap -p > shots/api${API}_0_splash.png
sleep 5; adb exec-out screencap -p > shots/api${API}_1_record.png
# record ~6 seconds
adb shell am start -n $M -a com.tomstechsolutions.mic.RECORD
sleep 7; adb exec-out screencap -p > shots/api${API}_2_recording.png
adb shell am start -n $M --ei open_tab 2; sleep 3; adb exec-out screencap -p > shots/api${API}_3_strip_on_calls.png
adb shell am start -n $M --ez stop_rec true; sleep 8
adb shell am start -n $M --ei open_tab 1; sleep 3; adb exec-out screencap -p > shots/api${API}_4_library.png
adb shell am start -n $M --ez open_latest true; sleep 4; adb exec-out screencap -p > shots/api${API}_5_detail.png
adb shell am start -n $M --ei open_tab 3; sleep 3; adb exec-out screencap -p > shots/api${API}_6_more.png
adb shell input swipe 500 1600 500 300 300; sleep 2; adb exec-out screencap -p > shots/api${API}_7_more2.png
adb shell input swipe 500 1600 500 300 300; sleep 2; adb exec-out screencap -p > shots/api${API}_8_more3.png
adb shell am start -n $M --ei open_tab 0 --es set_theme dark; sleep 3; adb exec-out screencap -p > shots/api${API}_9_dark.png
adb shell am start -n $M --ei open_tab 0 --es set_theme soft; sleep 3; adb exec-out screencap -p > shots/api${API}_10_soft.png
adb shell am start -n $M --ei open_tab 0 --es set_theme frosted; sleep 3; adb exec-out screencap -p > shots/api${API}_11_frosted.png
adb shell am start -n $M --ei open_tab 0 --es set_theme toms; sleep 2
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 4
adb exec-out screencap -p > shots/api${API}_12_landscape.png
adb shell settings put system user_rotation 0; sleep 3
adb shell ls -la /sdcard/Music/Tom\'s\ Mic/Recordings/* > shots/api${API}_files.txt 2>&1
PID=$(adb shell pidof $P)
adb logcat -d > shots/api${API}_logcat.txt
grep -A 30 "FATAL EXCEPTION" shots/api${API}_logcat.txt | grep -B2 -A 28 "Process: $P" > shots/api${API}_crash.txt || true
if [ -z "$PID" ] || [ -s shots/api${API}_crash.txt ]; then echo "APP CRASHED on API $API"; cat shots/api${API}_crash.txt; exit 1; fi
echo "API $API OK (pid $PID)"
