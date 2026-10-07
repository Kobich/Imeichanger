# Frida bypass for on-device testing (dev only)

`MODIFY_PHONE_STATE` is `signature|privileged` and, on the test firmware, "managed
by role" — it **cannot** be granted to an ordinary APK (`pm grant` fails with
`SecurityException: ... is managed by role`). For local testing on a
**userdebug** device with `adb root`, this script neutralises the permission
check in the process that hosts the `phoneEx` service, so the app runs as a
normal (non-system) APK. It is a test harness only and is never part of a build.

For production the app must be signed with the platform key and deployed as a
privileged app — see `system-integration/`.

## One-time setup

1. Install Frida tools on the PC:
   ```
   pip install frida-tools
   ```
2. Find the device CPU ABI and the installed Frida version:
   ```
   adb shell getprop ro.product.cpu.abi      # usually arm64-v8a
   frida --version
   ```
3. Download the matching `frida-server` from
   https://github.com/frida/frida/releases (file
   `frida-server-<version>-android-<arch>.xz`), unpack it, and push it:
   ```
   adb root
   adb push frida-server-<version>-android-arm64 /data/local/tmp/frida-server
   adb shell chmod 755 /data/local/tmp/frida-server
   ```

## Each test session

```
adb root
adb shell setenforce 0            # only if SELinux blocks frida-server (userdebug)
adb shell "/data/local/tmp/frida-server &"

# host of the phoneEx service on MediaTek is com.android.phone:
frida -U -n com.android.phone -l tools/frida/bypass-modify-phone-state.js
```

Then launch the Imeichanger app and trigger the AT command. The Frida console
prints a line every time it allows a `MODIFY_PHONE_STATE` check.

## Confirmed target on this firmware

The original author verified that on this ROM the check is
`com.mediatek.phone.MtkPhoneInterfaceManagerEx.enforceModifyPermission`, running
in the `com.android.phone` process. The script hooks that exact method (plus the
generic `ContextImpl` funnel as a fallback), so attaching to `com.android.phone`
is enough.

## If it still gets denied

The denial is enforced in a different process. Find it:

```
adb logcat | findstr /i "MODIFY_PHONE_STATE SecurityException phoneEx"
```

Look at the top stack frame / process name in the `SecurityException`, then
attach to that process instead, e.g. `frida -U -n system_server -l ...`.
Note the exact enforcing method name from the stack trace; if it is not one of
the methods already hooked in the script, add it to the `hookVoidIfPresent`
calls.

## Scope

By default the bypass applies only to `com.engboost.imeichanger` (see
`ONLY_PACKAGE` at the top of the script). Set it to `null` to allow any caller.
