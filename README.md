# GT7 Telemetry (Android)

Live Gran Turismo 7 dashboard: shift lights, gear, speed, pedals, tyre temps, fuel and laps of fuel left,
current/last/best lap and live delta to your best lap. Own car only: GT7 sends no data about other drivers.

## Use
1. PS5 and phone on the same Wi-Fi (no guest network / AP isolation).
2. PS5: Settings > Network > View Connection Status > note the IP address.
3. App: enter the IP, tap Connect, then start a race or time trial in GT7.
4. Long-press the dashboard (or Back) to return. "How to set up" opens the in-app manual (shown on first launch). "Demo mode" shows fake data.

No console handy? Run `python3 tools/fake_ps5.py` on a computer on the same network and use its IP.

## Build the APK
- GitHub: create a repo, upload this folder, open Actions > Build APK > download `gt7-telemetry-apk`.
- Android Studio: File > Open this folder, Build > Build APK(s). Output: app/build/outputs/apk/debug/app-debug.apk
- Install: copy the APK to the phone and allow "install unknown apps".

## Notes
- Protocol: heartbeat "A" to PS5 UDP 33739; telemetry arrives on UDP 33740, 296 bytes, Salsa20 encrypted.
- Delta needs one completed best lap first; fuel estimate needs one completed lap.
