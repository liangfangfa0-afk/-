# Sensor prototype — Stage 1

## Implemented

- User-controlled start/stop while the activity is visible.
- Prefer the platform linear-acceleration sensor.
- Fall back to accelerometer with a low-pass gravity estimate subtracted.
- Smooth the resulting acceleration vector and display its magnitude.
- Drive an in-app visual cue prototype from the filtered motion vector.
- Unregister sensor listeners when the activity is paused or destroyed.

## Explicit limitations

- This prototype does not identify a vehicle. It only reports motion changes.
- The threshold is provisional and is not a validated classifier.
- Sampling is foreground-only; no background service or system overlay is included.
- Sensor readings and UI animation need real-device validation before further tuning.
- The visual cue is an in-app prototype, not a system-wide overlay.

## Test checklist

1. Open the app and confirm monitoring is stopped.
2. Tap Start and confirm values update.
3. Leave the activity and confirm monitoring pauses.
4. Return and start again.
5. Compare stationary readings with gentle ordinary movement.
6. Check that Stop immediately unregisters the listener.
