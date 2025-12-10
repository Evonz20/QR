#!/bin/bash
sudo apt update
sudo apt install -y openjdk-17-jdk android-sdk apktool apksigner git python3 python3-pip ngrok qrencode

git clone https://github.com/zxing/zxing.git /opt/zxing

if [ ! -f ~/.android/debug.keystore ]; then
    keytool -genkey -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android,C=US"
fi

echo "Setup complete."
echo "Build APK → apksigner sign --ks ~/.android/debug.keystore app.apk"
echo "Host: python3 -m http.server 8080 & ngrok http 8080"
echo "QR: qrencode -o rat.png -s 12 \"http://$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[0].public_url')/app.apk\""
