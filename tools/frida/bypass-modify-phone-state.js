/*
 * Imeichanger — dev-only MODIFY_PHONE_STATE bypass for on-device testing.
 *
 * WHY: the phoneEx vendor service (IMtkTelephonyEx, txn 43 = sendAtCmd) enforces
 * android.permission.MODIFY_PHONE_STATE on the CALLER. That permission is
 * signature|privileged and, on this firmware, "managed by role" — it cannot be
 * granted to an ordinary APK with `pm grant`. For local testing (userdebug +
 * `adb root`) we neutralise the enforcement in the process that hosts phoneEx
 * instead of granting anything. This is a TEST harness only; it is never shipped.
 *
 * Enforcement runs server-side, so attach to the host of phoneEx — on MediaTek
 * that is com.android.phone:
 *
 *     adb root
 *     adb push frida-server /data/local/tmp/frida-server
 *     adb shell chmod 755 /data/local/tmp/frida-server
 *     adb shell "/data/local/tmp/frida-server &"     # setenforce 0 first if SELinux blocks it
 *     frida -U -n com.android.phone -l tools/frida/bypass-modify-phone-state.js
 *
 * If logcat shows the denial coming from another process, attach to that one by
 * name instead (e.g. `-n system_server`). See tools/frida/README.md.
 */
'use strict';

var TARGET_PERM = 'android.permission.MODIFY_PHONE_STATE';
var GRANTED = 0;  // PackageManager.PERMISSION_GRANTED

// Restrict the bypass to one caller package (safer on a shared test device).
// Set to null to allow MODIFY_PHONE_STATE for ANY caller.
var ONLY_PACKAGE = 'com.engboost.imeichanger';

Java.perform(function () {
    var ContextImpl = Java.use('android.app.ContextImpl');
    var Binder = Java.use('android.os.Binder');
    var ActivityThread = Java.use('android.app.ActivityThread');

    function callerPackages(uid) {
        try {
            var app = ActivityThread.currentApplication();
            if (app === null) return null;
            var pkgs = app.getPackageManager().getPackagesForUid(uid);
            return pkgs; // String[] or null
        } catch (e) {
            return null;
        }
    }

    function callerAllowed() {
        if (ONLY_PACKAGE === null) return true;
        var uid = Binder.getCallingUid();
        var pkgs = callerPackages(uid);
        if (pkgs === null) return true; // can't resolve -> don't block the test
        for (var i = 0; i < pkgs.length; i++) {
            if (('' + pkgs[i]) === ONLY_PACKAGE) return true;
        }
        return false;
    }

    function tag() {
        return '[imei-bypass uid=' + Binder.getCallingUid() + ']';
    }

    // Hook every overload of a ContextImpl check* method -> return GRANTED.
    function hookCheck(name) {
        try {
            ContextImpl[name].overloads.forEach(function (ov) {
                ov.implementation = function () {
                    var args = Array.prototype.slice.call(arguments);
                    if (('' + args[0]) === TARGET_PERM && callerAllowed()) {
                        console.log(tag() + ' check ' + name + ' -> GRANTED');
                        return GRANTED;
                    }
                    return ov.call.apply(ov, [this].concat(args));
                };
            });
            console.log('[imei-bypass] hooked ContextImpl.' + name);
        } catch (e) {
            console.log('[imei-bypass] skip ContextImpl.' + name + ': ' + e);
        }
    }

    // Hook every overload of a ContextImpl enforce* method -> swallow for our perm.
    function hookEnforce(name) {
        try {
            ContextImpl[name].overloads.forEach(function (ov) {
                ov.implementation = function () {
                    var args = Array.prototype.slice.call(arguments);
                    if (('' + args[0]) === TARGET_PERM && callerAllowed()) {
                        console.log(tag() + ' enforce ' + name + ' -> allowed');
                        return;
                    }
                    return ov.call.apply(ov, [this].concat(args));
                };
            });
            console.log('[imei-bypass] hooked ContextImpl.' + name);
        } catch (e) {
            console.log('[imei-bypass] skip ContextImpl.' + name + ': ' + e);
        }
    }

    ['checkPermission',
     'checkCallingPermission',
     'checkCallingOrSelfPermission',
     'checkSelfPermission'].forEach(hookCheck);

    ['enforcePermission',
     'enforceCallingPermission',
     'enforceCallingOrSelfPermission'].forEach(hookEnforce);

    // Belt-and-suspenders: if the firmware funnels through these helpers, neutralise
    // them too. They are optional — absent classes/methods are ignored.
    function hookVoidIfPresent(className, methodName) {
        try {
            var c = Java.use(className);
            c[methodName].overloads.forEach(function (ov) {
                ov.implementation = function () {
                    console.log('[imei-bypass] ' + className + '.' + methodName + ' -> allowed');
                    return;
                };
            });
            console.log('[imei-bypass] hooked ' + className + '.' + methodName);
        } catch (e) {
            // not on this ROM — fine
        }
    }

    hookVoidIfPresent('com.android.phone.PhoneInterfaceManager', 'enforceModifyPermission');
    hookVoidIfPresent('com.android.internal.telephony.TelephonyPermissions',
                      'enforceCallingOrSelfModifyPermissionOrCarrierPrivilege');

    console.log('[imei-bypass] ready. Target perm: ' + TARGET_PERM +
                (ONLY_PACKAGE ? (' for ' + ONLY_PACKAGE) : ' for ANY caller'));
});
