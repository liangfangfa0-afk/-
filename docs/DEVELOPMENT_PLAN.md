# Development plan

## Implemented in 1.0.22
- [x] Live motion sampling using linear acceleration, with accelerometer fallback
- [x] Low-pass filtering and rolling RMS estimate
- [x] Foreground service and persistent stop notification
- [x] User-authorized full-screen overlay using TYPE_APPLICATION_OVERLAY
- [x] Perimeter bubble field: left/right columns plus top/bottom edge markers
- [x] Bubble positions respond continuously to filtered acceleration
- [x] Overlay is non-focusable and non-touchable
- [x] Main screen explains and requests overlay permission

## Architecture and limitations
The app uses Android's user-granted draw-over-other-apps permission. It does not require LSPosed hooks for the overlay. The project contains no Root, LSPosed, Xposed, system_server integration, or application/system hooks.

Motion-driven bubbles are a visual cue, not a vehicle detector or a clinically validated treatment. Sensor axes depend on device orientation and handling. Android and OEM background policies may still stop the foreground service.
