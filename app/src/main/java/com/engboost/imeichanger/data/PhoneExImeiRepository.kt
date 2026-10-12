package com.engboost.imeichanger.data

import android.util.Log
import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import com.engboost.imeichanger.phoneex.CallbackLayout
import com.engboost.imeichanger.phoneex.OemHookCallback
import com.engboost.imeichanger.phoneex.PhoneExClient
import com.engboost.imeichanger.phoneex.PhoneExIntrospection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Real [ImeiRepository] backed by the hidden MediaTek `phoneEx` service.
 *
 * The EGMR *type* field selects which IMEI (7 = IMEI1, 10 = IMEI2); the binder
 * slot arg is ignored by the modem, so everything runs on slot 0:
 *   read  = AT+EGMR=0,<type>
 *   write = AT+CFUN=0 -> AT+EGMR=1,<type>,"<imei>" -> AT+CFUN=1,1 -> read back
 *
 * [PhoneExClient.sendAtCommand] is fire-and-forget: the modem answers later on a
 * binder thread via [OemHookCallback.onAtCmdResp]. [sendAndAwait] bridges that
 * back to a blocking call with a [CountDownLatch] so the suspend API can return
 * a parsed result. Every public operation runs as one [modemSession]: on
 * [Dispatchers.IO] under [commandMutex], so sequences never interleave.
 *
 * Original IMEIs and history are persisted in [storage] and restored on start.
 */
class PhoneExImeiRepository(
    private val storage: ImeiStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ImeiRepository {

    private val _currentImeis = MutableStateFlow<Map<SimSlot, String>>(emptyMap())
    override val currentImeis: StateFlow<Map<SimSlot, String>> = _currentImeis.asStateFlow()

    // The modem keeps no factory IMEI: remember (and persist) the first one observed
    // per slot, so reset has something to write back to even after a restart.
    private val _originalImeis = MutableStateFlow<Map<SimSlot, String>>(emptyMap())
    override val originalImeis: StateFlow<Map<SimSlot, String>> = _originalImeis.asStateFlow()

    private val _history = MutableStateFlow<List<ImeiChangeRecord>>(emptyList())
    override val history: StateFlow<List<ImeiChangeRecord>> = _history.asStateFlow()

    private val commandMutex = Mutex()

    // Discovered once via reflection probe, then reused for every callback.
    @Volatile
    private var layout: CallbackLayout = CallbackLayout.FALLBACK
    @Volatile
    private var probed = false

    init {
        // Restore saved state first so the stored originals win over what the modem
        // reports now, then read both slots so the UI shows real values.
        scope.launch { modemSession { restore(); refreshAll() } }
    }

    override suspend fun changeImei(slot: SimSlot, newImei: String): Unit = modemSession {
        val oldImei = _currentImeis.value[slot]
        if (oldImei == newImei) return@modemSession
        writeImei(slot, newImei)
        val readBack = readImei(slot) ?: newImei
        rememberOriginal(slot, oldImei ?: readBack)
        _currentImeis.update { it + (slot to readBack) }
        _history.update {
            it + ImeiChangeRecord(
                slot = slot,
                timestamp = Instant.now(),
                oldImei = oldImei.orEmpty(),
                newImei = readBack,
            )
        }
        persist()
    }

    override suspend fun resetImei(slot: SimSlot) {
        val original = _originalImeis.value[slot] ?: return
        changeImei(slot, original)
    }

    private fun restore() {
        val stored = storage.load()
        _originalImeis.value = stored.originalImeis
        _history.value = stored.history
    }

    private fun refreshAll() {
        var originalsChanged = false
        for (slot in SimSlot.entries) {
            val imei = readImei(slot) ?: continue
            originalsChanged = rememberOriginal(slot, imei) || originalsChanged
            _currentImeis.update { it + (slot to imei) }
        }
        if (originalsChanged) persist()
    }

    /** Returns true if [imei] became the slot's original (i.e. none was known yet). */
    private fun rememberOriginal(slot: SimSlot, imei: String): Boolean {
        if (_originalImeis.value.containsKey(slot)) return false
        _originalImeis.update { it + (slot to imei) }
        return true
    }

    private fun persist() {
        storage.save(StoredImeiState(originalImeis = _originalImeis.value, history = _history.value))
    }

    /**
     * Runs [block] on [Dispatchers.IO] holding [commandMutex], so a whole sequence
     * (e.g. write + read back) never interleaves with another one. The mutex is not
     * reentrant: the helpers below must only be called from inside a session.
     */
    private suspend fun <T> modemSession(block: suspend () -> T): T =
        commandMutex.withLock { withContext(Dispatchers.IO) { block() } }

    /**
     * Reads one IMEI via AT+EGMR=0,<type>. The EGMR *type* selects which IMEI
     * (7 = IMEI1, 10 = IMEI2) — the binder slot arg is ignored by the modem, so
     * every command goes on PHONE_SLOT (0), which answers for both types.
     */
    private fun readImei(slot: SimSlot): String? {
        val resp = sendAndAwait(PHONE_SLOT, "AT+EGMR=0,${slot.egmrType}")
        val imei = resp?.let(::parseImei)
        Log.i(TAG, "readImei $slot (type=${slot.egmrType}) raw='$resp' parsed=$imei")
        return imei
    }

    /**
     * Proven write sequence: radio off -> write -> reboot modem. Caller reads back.
     * NonCancellable: aborting after CFUN=0 would leave the radio switched off.
     */
    private suspend fun writeImei(slot: SimSlot, imei: String) = withContext(NonCancellable) {
        val type = slot.egmrType
        sendRaw(PHONE_SLOT, "AT+CFUN=0")
        delay(WRITE_STEP_DELAY_MS)
        sendRaw(PHONE_SLOT, "AT+EGMR=1,$type,\"$imei\"")
        delay(WRITE_STEP_DELAY_MS)
        sendRaw(PHONE_SLOT, "AT+CFUN=1,1")
        delay(MODEM_REBOOT_DELAY_MS)
    }

    /** Blocking send that waits for the modem's onAtCmdResp (or times out). */
    private fun sendAndAwait(slot: Int, cmd: String): String? {
        ensureProbed()
        val latch = CountDownLatch(1)
        var response: String? = null
        // Strong local ref keeps the callback alive until the modem replies.
        val callback = OemHookCallback(
            layout = layout,
            onAtCmdResp = { _, _, resp -> response = resp; latch.countDown() },
            onError = { err -> Log.w(TAG, "phoneEx onError: $err"); latch.countDown() },
        )
        return try {
            PhoneExClient.sendAtCommand(slot = slot, token = 0L, atCmd = cmd, callback = callback)
            if (!latch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "phoneEx timeout waiting for '$cmd'")
            }
            response
        } catch (e: Exception) {
            Log.e(TAG, "phoneEx send '$cmd' failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Fire-and-forget send used inside the write sequence (reply only logged). */
    private fun sendRaw(slot: Int, cmd: String) {
        ensureProbed()
        val callback = OemHookCallback(layout = layout)
        try {
            PhoneExClient.sendAtCommand(slot = slot, token = 0L, atCmd = cmd, callback = callback)
        } catch (e: Exception) {
            Log.e(TAG, "phoneEx send '$cmd' failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun ensureProbed() {
        if (probed) return
        layout = PhoneExIntrospection.probe()
        probed = true
    }

    // EGMR field index per IMEI: 7 = IMEI1 (SIM1), 10 = IMEI2 (SIM2).
    private val SimSlot.egmrType: Int
        get() = when (this) {
            SimSlot.SIM1 -> 7
            SimSlot.SIM2 -> 10
        }

    private companion object {
        const val TAG = "imei_phoneex"

        // EGMR ignores the binder slot; slot 0 answers for both IMEI types.
        const val PHONE_SLOT = 0
        const val RESPONSE_TIMEOUT_MS = 5_000L
        const val WRITE_STEP_DELAY_MS = 1_000L
        const val MODEM_REBOOT_DELAY_MS = 3_000L

        // Modem echoes e.g. +EGMR: "353332990071343" (quoted) or a bare run of
        // digits; grab the first 15-digit group either way.
        val IMEI_REGEX = Regex("\\d{15}")

        fun parseImei(response: String): String? = IMEI_REGEX.find(response)?.value
    }
}
