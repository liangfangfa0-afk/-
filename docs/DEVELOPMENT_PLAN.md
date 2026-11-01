# Development plan

## Stage 0 — Reproducible project bootstrap

- [x] Android application module
- [x] libxposed API 102 compile-only dependency
- [x] Modern Xposed entry registration
- [x] Fixed initial scope: `system`
- [x] GitHub Actions debug build workflow
- [ ] Confirm first CI build succeeds
- [x] Add foreground sensor sampling prototype
- [x] Add in-app motion cue visualization prototype
- [x] Document foreground-only behavior and limitations

## Stage 1 — Runtime and sensor feasibility

- [ ] Confirm API 102 module is recognized by the user's installed LSPosed build
- [ ] Verify the system-server callback lifecycle without installing hooks
- [ ] Decide whether sensor collection belongs in system_server or a normal app service
- [ ] Measure accelerometer and gyroscope availability, sampling rate, and power impact
- [ ] Define motion classification and false-positive tests

## Stage 2 — User-facing experience

- [ ] Main switch and explicit state reporting
- [ ] Permission and overlay onboarding, if overlay is selected
- [ ] Motion cue prototype and user-adjustable behavior
- [ ] Runtime enable/disable without device reboot after initial activation

## Stage 3 — Device validation

- [ ] Test on OPPO K13 Turbo (PLM110), Android 16
- [ ] Test stationary, walking, handheld movement, and vehicle-like motion
- [ ] Verify crash safety and clean shutdown
- [ ] Package signed release only after debug validation

## Safety and reliability constraints

- Sensor motion is not proof that the user is in a vehicle.
- The feature is an aid, not a medical treatment or a guarantee against motion sickness.
- No system-server hooks are added until a concrete, tested hook point is justified.
- Keep the LSPosed scope limited to the special `system` target unless a documented requirement proves otherwise.
