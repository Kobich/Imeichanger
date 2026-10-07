package com.engboost.imeichanger.reference

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.engboost.imeichanger.ui.theme.ImeichangerTheme

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import android.os.Binder
import android.os.IInterface


private const val TAG = "my_app"
const val DESCRIPTOR = "com.mediatek.telephony.IOemHookCallback"
const val TRANSACTION_onAtCmdResp = IBinder.FIRST_CALL_TRANSACTION + 0
const val TRANSACTION_onAtUrcInd = IBinder.FIRST_CALL_TRANSACTION + 1
const val TRANSACTION_onError = IBinder.FIRST_CALL_TRANSACTION + 2

interface IOemHookCallback : IInterface {

    @Throws(RemoteException::class)
    fun onAtCmdResp(slotId: Int, token: Long, atCmd: String)

    @Throws(RemoteException::class)
    fun onAtUrcInd(slotId: Int, urc: String)

    @Throws(RemoteException::class)
    fun onError(e: String)

    /** Must return the binder that the remote side will invoke. */
    override fun asBinder(): IBinder
}

class OemHookCallback(
    private val onAtCmdResp: (slotId: Int, token: Long, atCmd: String) -> Unit = { _, _, _ -> },
    private val onAtUrcInd: (slotId: Int, urc: String) -> Unit = { _, _ -> },
    private val onError: (error: String) -> Unit = { }
) : IOemHookCallback {

    // -----------------------------------------------------------------
    // 1  The Binder object that the remote side will actually talk to.
    // -----------------------------------------------------------------
    private val binder = object : Binder() {
        init {
            // The generated AIDL stub attaches the interface descriptor to the binder.
            this.attachInterface(this@OemHookCallback, DESCRIPTOR)
        }

        @Throws(RemoteException::class)
        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int
        ): Boolean {
            // The first step a normal stub does is enforce the interface token.
            // If the token is wrong we simply return false.
            if (code != IBinder.INTERFACE_TRANSACTION) {
                data.enforceInterface(DESCRIPTOR)
            }

            when (code) {
                // -----------------------------------------------------
                // onAtCmdResp(int slotId, long token, String atCmd)
                // -----------------------------------------------------
                TRANSACTION_onAtCmdResp -> {
                    val slotId = data.readInt()
                    val token = data.readLong()
                    val atCmd = data.readString() ?: ""
                    onAtCmdResp(slotId, token, atCmd)
                    // onAtCmdResp is declared "oneway" in the AIDL, so no reply is needed.
                    return true
                }

                // -----------------------------------------------------
                // onAtUrcInd(int slotId, String urc)
                // -----------------------------------------------------
                TRANSACTION_onAtUrcInd -> {
                    val slotId = data.readInt()
                    val urc = data.readString() ?: ""
                    onAtUrcInd(slotId, urc)
                    return true
                }

                // -----------------------------------------------------
                // onError(String err)
                // -----------------------------------------------------
                TRANSACTION_onError -> {
                    val err = data.readString() ?: ""
                    onError(err)
                    return true
                }

                // -----------------------------------------------------
                // The framework asks for the descriptor (binder plumbing)
                // -----------------------------------------------------
                IBinder.INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }

                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }

    // -----------------------------------------------------------------
    // 2  IInterface implementation - just forward to the private binder.
    // -----------------------------------------------------------------
    override fun asBinder(): IBinder = binder

    // -----------------------------------------------------------------
    // 3  The three callbacks required by the interface.
    // -----------------------------------------------------------------
    @Throws(RemoteException::class)
    override fun onAtCmdResp(slotId: Int, token: Long, atCmd: String) {
        // When a *local* caller (your own code) wants to invoke the callback
        // directly, we forward to the lambda as well.
        onAtCmdResp(slotId, token, atCmd)
    }

    @Throws(RemoteException::class)
    override fun onAtUrcInd(slotId: Int, urc: String) {
        onAtUrcInd(slotId, urc)
    }

    @Throws(RemoteException::class)
    override fun onError(e: String) {
        onError(e)
    }
}

object PhoneExClient {

    // -----------------------------------------------------------------
    // 1  Retrieve the hidden service's raw binder (exactly the same as before)
    // -----------------------------------------------------------------
    @Throws(
        ClassNotFoundException::class,
        NoSuchMethodException::class,
        IllegalAccessException::class,
        java.lang.reflect.InvocationTargetException::class
    )
    private fun getPhoneExBinder(): IBinder {
        // ServiceManager.getService("phoneEx")
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getDeclaredMethod("getService", String::class.java)
        return getService.invoke(null, "phoneEx") as IBinder
    }

    // -----------------------------------------------------------------
    // 2  Public entry-point - same payload as before, but we now accept a
    //    full-featured IOemHookCallback instead of a single-int lambda.
    // -----------------------------------------------------------------
    /**
     * Calls the hidden "phoneEx" service (transaction code) with the
     * exact argument layout you described:
     *
     *   i32      -> `intParam`
     *   i64      -> `longParam`
     *   s16      -> `stringParam`
     *   callback -> an `IOemHookCallback` binder
     *
     * @param intParam    32-bit integer argument
     * @param longParam   64-bit integer argument
     * @param stringParam 16-bit UTF-16 string argument
     * @param callback    an implementation of `IOemHookCallback`
     *
     * @throws RemoteException if the binder transaction fails.
     */
    @Throws(RemoteException::class)
    fun callPhoneEx(
        intParam: Int,
        longParam: Long,
        stringParam: String,
        callback: IOemHookCallback      // <-- new argument
    ) {
        // Resolve the hidden service
        val remote: IBinder = getPhoneExBinder()

        // Prepare parcels
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            // ---- 2a. Interface token for the **service** (not for the callback)
            data.writeInterfaceToken("com.mediatek.internal.telephony.IMtkTelephonyEx") // <-- change if you know a different token

            // ---- 2b. Service-specific arguments
            data.writeInt(intParam)         // i32
            data.writeLong(longParam)       // i64
            data.writeString(stringParam)   // s16  (Parcel uses UTF-16)

            // ---- 2c. The callback binder (our OemHookCallback implementation)
            data.writeStrongBinder(callback.asBinder())

            // ---- 3. Perform the IPC
            val TRANSACTION_CODE = 43
            remote.transact(TRANSACTION_CODE, data, reply, 0)

            // ---- 4. (Optional) read any reply the service might send
            // Example: maybe the service returns an int status code.
            // Adjust according to the real protocol.
            val status = reply.readInt()
            Log.d(TAG, "phoneEx replied with status = $status")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImeichangerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val myCallback = OemHookCallback(
            onAtCmdResp = { slotId, token, atCmd ->
                Log.i(TAG, "onAtCmdResp - slot=$slotId token=$token cmd=$atCmd")
            },
            onAtUrcInd = { slotId, urc ->
                Log.i(TAG, "onAtUrcInd - slot=$slotId urc=$urc")
            },
            onError = { errMsg ->
                Log.e(TAG, "Remote side reported error: $errMsg")
            }
        )

        // Run the transaction on a background thread because `transact` blocks.
        Thread {
            try {
                PhoneExClient.callPhoneEx(
                    intParam = 0,
                    longParam = 0,
                    stringParam = "AT+EGMR=1,7,\"353332990071343\"",
                    callback = myCallback   // <-- pass the binder implementation
                )
                Log.i(TAG, "phoneEx call completed (sync part)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to invoke phoneEx service", e)
            }

            try {
                PhoneExClient.callPhoneEx(
                    intParam = 0,
                    longParam = 0,
                    stringParam = "AT+CFUN=1,1",
                    callback = myCallback   // <-- pass the binder implementation
                )
                Log.i(TAG, "phoneEx call completed (sync part)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to invoke phoneEx service", e)
            }
        }.start()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ImeichangerTheme {
        Greeting("Android")
    }
}
