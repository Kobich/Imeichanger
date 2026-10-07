# Production deployment (platform-signed privileged app)

For the released build the app is not bypassed — it genuinely holds
`MODIFY_PHONE_STATE`. On stock-signed firmware (`release-keys`) that requires
**two** things that are outside the APK and must be done by whoever owns the
platform signing key (the firmware vendor / client):

1. **Sign the APK with the platform key**, and
2. **Install it as a privileged app + add the permission allowlist** below.

## What is already correct in the app

The manifest declaration is complete and does not need to change:

```xml
<uses-permission android:name="android.permission.MODIFY_PHONE_STATE"
    tools:ignore="ProtectedPermissions" />
```

`tools:ignore="ProtectedPermissions"` only silences a lint warning; it has no
runtime effect. Nothing else in the manifest is required to *request* the
permission. Whether it is *granted* is decided entirely by the signature and the
allowlist below — that part cannot be fixed from inside the APK.

## What the vendor / client must do

1. **Sign with the platform key**
   ```
   apksigner sign --key platform.pk8 --cert platform.x509.pem \
       --out Imeichanger-signed.apk app-release-unsigned.apk
   ```

2. **Install as a privileged app** (not `/system/app` — it must be `priv-app`):
   ```
   /system/priv-app/Imeichanger/Imeichanger.apk        # mode 0644
   ```

3. **Add the privileged-permission allowlist** from this folder:
   ```
   /system/etc/permissions/privapp-permissions-com.engboost.imeichanger.xml
   ```
   (mode 0644, SELinux `u:object_r:system_file:s0`). This is mandatory — a
   privileged app does **not** get `MODIFY_PHONE_STATE` without it.

4. Reboot and verify:
   ```
   adb shell dumpsys package com.engboost.imeichanger | grep -E "codePath|PRIVILEGED|MODIFY_PHONE_STATE"
   ```
   Expect `codePath=/system/priv-app/Imeichanger`, the `PRIVILEGED` flag, and
   `android.permission.MODIFY_PHONE_STATE: granted=true`.

## If priv-app is not enough

The `phoneEx` vendor service may accept calls only from the **system UID**, not
merely from a privileged app holding the permission. If, after the steps above,
calls still fail with a `SecurityException`, the app additionally needs to run as
the system user:

```xml
<manifest ... android:sharedUserId="android.uid.system">
```

> ⚠️ Decide this **with the vendor before they sign**:
> - It only works when the APK is signed with the **platform key** (it is — see
>   step 1).
> - `sharedUserId` changes the app's UID; adding or removing it later makes the
>   app uninstall-only (data cannot migrate). Pick one and keep it.
> - Confirm first whether it is actually required — test the plain priv-app build
>   first, and only add `sharedUserId` if the service rejects a privileged caller.

## Responsibility split

- **App side (this repo):** a working APK and a correct manifest. ✅ done.
- **Vendor/client side:** platform signing, priv-app install, the allowlist XML
  above, and (if required) agreeing to `sharedUserId` before signing.

Local development does not need any of this — see `tools/frida/`.
