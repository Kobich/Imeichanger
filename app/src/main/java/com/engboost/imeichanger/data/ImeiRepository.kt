package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import kotlinx.coroutines.flow.Flow

interface ImeiRepository {
    val currentImeis: Flow<Map<SimSlot, String>>

    val originalImeis: Flow<Map<SimSlot, String>>
    val history: Flow<List<ImeiChangeRecord>>

    /** True while the modem is being read or written (including queued operations). */
    val isBusy: Flow<Boolean>

    /**
     * Writes [newImei] and records the change only once the modem reports it back.
     * @throws ImeiChangeException if the IMEI was not (or not verifiably) changed;
     * nothing is added to the history then.
     */
    suspend fun changeImei(slot: SimSlot, newImei: String)

    /** [changeImei] back to the original IMEI; no-op if none is known. */
    suspend fun resetImei(slot: SimSlot)
}

class ImeiChangeException(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        /** phoneEx rejected the command or is unreachable, e.g. no MODIFY_PHONE_STATE. */
        SEND_FAILED,

        /** The modem did not answer the read-back, so whether the write applied is unknown. */
        NOT_VERIFIED,

        /** The modem still reports a different IMEI after the write. */
        NOT_APPLIED,
    }
}
