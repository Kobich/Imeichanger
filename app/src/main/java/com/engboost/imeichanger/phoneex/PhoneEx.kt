package com.engboost.imeichanger.phoneex

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.RemoteException
import android.util.Log

const val TAG = "imei_phoneex"

/** Interface token of the vendor service that owns sendAtCmd (transaction 43). */
const val SERVICE_DESCRIPTOR_DEFAULT = "com.mediatek.internal.telephony.IMtkTelephonyEx"

/** sendAtCmd transaction code (from the docs). */
const val TRANSACTION_sendAtCmd = 43

/** Candidate descriptors for the result callback, most-likely first. */
private val CALLBACK_DESCRIPTOR_CANDIDATES = listOf(
    "com.mediatek.internal.telephony.IOemHookCallback",
    "com.mediatek.telephony.IOemHookCallback",
)

/**
 * Resolved layout of the IOemHookCallback AIDL.
 * Either discovered from the on-device framework (preferred) or the fallback guess.
 */
data class CallbackLayout(
    val descriptor: String,
    val txnOnAtCmdResp: Int,
    val txnOnAtUrcInd: Int,
    val txnOnError: Int,
    val discovered: Boolean,
) {
    companion object {
        val FALLBACK = CallbackLayout(
            descriptor = CALLBACK_DESCRIPTOR_CANDIDATES.first(),
            txnOnAtCmdResp = IBinder.FIRST_CALL_TRANSACTION + 0,
            txnOnAtUrcInd = IBinder.FIRST_CALL_TRANSACTION + 1,
            txnOnError = IBinder.FIRST_CALL_TRANSACTION + 2,
            discovered = false,
        )
    }
}

/**
 * Reflects over the on-device framework classes to discover the *real*
 * IOemHookCallback descriptor and transaction codes. Everything is logged so we
 * can read it back from logcat / the on-screen log.
 */
object PhoneExIntrospection {

    fun probe(log: (String) -> Unit = { Log.i(TAG, it) }): CallbackLayout {
        log("=== phoneEx introspection start ===")

        // 1. Is the service even registered, and what descriptor does it report?
        try {
            val binder = getServiceBinder("phoneEx")
            if (binder == null) {
                log("service 'phoneEx' -> NOT registered (getService returned null)")
            } else {
                val iface = try {
                    binder.interfaceDescriptor
                } catch (e: Exception) {
                    "<error: ${e.message}>"
                }
                log("service 'phoneEx' -> OK, interfaceDescriptor = $iface")
            }
        } catch (e: Throwable) {
            log("service 'phoneEx' probe FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 2. Walk candidate callback classes looking for DESCRIPTOR + TRANSACTION_* fields.
        for (name in CALLBACK_DESCRIPTOR_CANDIDATES) {
            val layout = tryReadLayout(name, log)
            if (layout != null) {
                log("RESOLVED callback layout from '$name': $layout")
                log("=== phoneEx introspection end (discovered) ===")
                return layout
            }
        }

        log("could not read any callback class via reflection; using FALLBACK = ${CallbackLayout.FALLBACK}")
        log("hint: on the test device run -> adb shell settings put global hidden_api_policy 1")
        log("=== phoneEx introspection end (fallback) ===")
        return CallbackLayout.FALLBACK
    }

    private fun tryReadLayout(ifaceName: String, log: (String) -> Unit): CallbackLayout? {
        for (cn in listOf(ifaceName, "$ifaceName\$Stub")) {
            try {
                val c = Class.forName(cn)
                log("found class: $cn")

                val descriptor = readStringField(c, "DESCRIPTOR") ?: ifaceName
                val methods = c.declaredMethods.map { it.name }.distinct()
                log("  methods: $methods")

                val txns = c.declaredFields
                    .filter { it.name.startsWith("TRANSACTION_") }
                    .mapNotNull { f ->
                        f.isAccessible = true
                        (f.get(null) as? Int)?.let { f.name to it }
                    }
                txns.forEach { (n, v) -> log("  $n = $v") }
                log("  DESCRIPTOR = $descriptor")

                val cmd = txns.firstOrNull { it.first.contains("AtCmdResp", true) }?.second
                val urc = txns.firstOrNull { it.first.contains("AtUrcInd", true) }?.second
                val err = txns.firstOrNull { it.first.contains("Error", true) }?.second

                // Only treat as a real find if we actually read the descriptor or codes.
                val anything = cmd != null || urc != null || err != null ||
                    descriptor != ifaceName
                if (!anything) {
                    log("  (class present but no useful fields; keep looking)")
                    continue
                }

                return CallbackLayout(
                    descriptor = descriptor,
                    txnOnAtCmdResp = cmd ?: CallbackLayout.FALLBACK.txnOnAtCmdResp,
                    txnOnAtUrcInd = urc ?: CallbackLayout.FALLBACK.txnOnAtUrcInd,
                    txnOnError = err ?: CallbackLayout.FALLBACK.txnOnError,
                    discovered = true,
                )
            } catch (e: Throwable) {
                log("  class '$cn' not usable: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }

    private fun readStringField(c: Class<*>, field: String): String? = try {
        val f = c.getDeclaredField(field)
        f.isAccessible = true
        f.get(null) as? String
    } catch (e: Throwable) {
        null
    }

    fun getServiceBinder(name: String): IBinder? {
        val sm = Class.forName("android.os.ServiceManager")
        val getService = sm.getDeclaredMethod("getService", String::class.java)
        return getService.invoke(null, name) as? IBinder
    }
}

/**
 * Our implementation of the vendor IOemHookCallback. The remote side writes the
 * command result into a Binder transaction that lands in [onTransact]; we decode
 * it and forward to the lambdas. Transaction codes + descriptor come from
 * [layout] so we can feed in whatever the firmware probe discovered.
 */
class OemHookCallback(
    private val layout: CallbackLayout = CallbackLayout.FALLBACK,
    private val onAtCmdResp: (slotId: Int, token: Long, atCmd: String) -> Unit = { _, _, _ -> },
    private val onAtUrcInd: (slotId: Int, urc: String) -> Unit = { _, _ -> },
    private val onError: (error: String) -> Unit = { },
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : IInterface {

    private val binder = object : Binder() {
        init {
            attachInterface(this@OemHookCallback, layout.descriptor)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.INTERFACE_TRANSACTION) {
                // Be tolerant instead of throwing, so a descriptor mismatch is visible
                // in the log rather than silently failing.
                try {
                    data.enforceInterface(layout.descriptor)
                } catch (e: SecurityException) {
                    log("INCOMING txn code=$code: descriptor mismatch (expected '${layout.descriptor}') -> ${e.message}")
                    return false
                }
            }
            return when (code) {
                layout.txnOnAtCmdResp -> {
                    val slotId = data.readInt()
                    val token = data.readLong()
                    val atCmd = data.readString() ?: ""
                    log("onAtCmdResp slot=$slotId token=$token cmd=$atCmd")
                    onAtCmdResp(slotId, token, atCmd)
                    true
                }
                layout.txnOnAtUrcInd -> {
                    val slotId = data.readInt()
                    val urc = data.readString() ?: ""
                    log("onAtUrcInd slot=$slotId urc=$urc")
                    onAtUrcInd(slotId, urc)
                    true
                }
                layout.txnOnError -> {
                    val err = data.readString() ?: ""
                    log("onError: $err")
                    onError(err)
                    true
                }
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(layout.descriptor)
                    true
                }
                else -> {
                    log("INCOMING unknown txn code=$code")
                    super.onTransact(code, data, reply, flags)
                }
            }
        }
    }

    override fun asBinder(): IBinder = binder
}

object PhoneExClient {

    /**
     * Sends a single AT command through the hidden phoneEx service.
     * Blocks on the binder transaction, so call it off the main thread.
     */
    @Throws(RemoteException::class)
    fun callPhoneEx(
        slot: Int,
        token: Long,
        atCmd: String,
        callback: OemHookCallback,
        serviceDescriptor: String = SERVICE_DESCRIPTOR_DEFAULT,
        log: (String) -> Unit = { Log.i(TAG, it) },
    ) {
        val remote = PhoneExIntrospection.getServiceBinder("phoneEx")
            ?: throw RemoteException("service 'phoneEx' not found")

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(serviceDescriptor)
            data.writeInt(slot)
            data.writeLong(token)
            data.writeString(atCmd)
            data.writeStrongBinder(callback.asBinder())

            log("-> transact($TRANSACTION_sendAtCmd) slot=$slot token=$token cmd=$atCmd")
            remote.transact(TRANSACTION_sendAtCmd, data, reply, 0)

            // Server does reply.writeNoException(); mirror it with readException().
            reply.readException()
            log("<- transact accepted (no exception from service)")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}
