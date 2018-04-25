rem ndk-build
rem ant -q clean release
java -jar signapk.jar certificate.pem key.pk8 "./bin/hu-release-unsigned.apk" test.apk
adb install -r test.apk
 