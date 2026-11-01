package com.motionguard.xposed

import io.github.libxposed.api.XposedModule

/**
 * Minimal API 102 entry point.
 *
 * No framework hooks are installed in this bootstrap commit. System-server
 * integration will be added only after the sensor and process-boundary design
 * has been verified on the target device.
 */
class MotionGuardModule : XposedModule()
