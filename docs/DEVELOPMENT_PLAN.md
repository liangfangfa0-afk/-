# Development plan

## Implemented in this build
- [x] Android app and LSPosed API 102 bootstrap entry
- [x] Linear acceleration sampling with accelerometer fallback and gravity separation
- [x] Low-pass filtering and rolling RMS estimate
- [x] Heuristic motion states: relative stillness, light motion, sustained motion, stronger jolts
- [x] Foreground service continues sampling while activity is not visible
- [x] Persistent notification with stop action
- [x] In-app motion cue and live diagnostics

## Not implemented / requires separate device work
- [ ] System-wide overlay or Android System UI integration
- [ ] Actual system_server hooks; LSPosed entry remains bootstrap only
- [ ] Vehicle classifier validated against walking, handheld movement and real trips
- [ ] OPPO K13 Turbo (PLM110), Android 16 battery-policy and long-duration validation
- [ ] Release signing and distribution

## Runtime notes
The foreground service uses Android's special-use foreground-service category. Android/OEM battery policies may still stop it. Android 13+ notification permission affects notification visibility. The cue remains inside this app and does not draw over other apps.

Thresholds are initial heuristics, not a validated vehicle classifier. Motion sensing cannot prove that the user is in a vehicle or guarantee relief from motion sickness.

## LSPosed
Keep configured scope limited to `system`. This build does not install system hooks. Enabling LSPosed does not change the app's sensor sampling.
