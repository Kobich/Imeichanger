package com.engboost.imeichanger.data

import android.util.AtomicFile
import android.util.Log
import com.engboost.imeichanger.domain.ImeiChangeRecord
import com.engboost.imeichanger.domain.SimSlot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant

/** What survives an app restart: the first IMEI seen per slot and the change history. */
data class StoredImeiState(
    val originalImeis: Map<SimSlot, String> = emptyMap(),
    val history: List<ImeiChangeRecord> = emptyList(),
)

/**
 * Keeps [StoredImeiState] in one JSON file. [AtomicFile] guarantees a crash mid-write
 * leaves the previous version intact. Blocking: call from a background thread.
 *
 * ```
 * {"originalImeis": {"SIM1": "…", "SIM2": "…"},
 *  "history": [{"slot": "SIM1", "timestamp": 1760000000000, "oldImei": "…", "newImei": "…"}]}
 * ```
 */
class ImeiStorage(file: File) {
    private val file = AtomicFile(file)

    fun load(): StoredImeiState = try {
        val json = JSONObject(file.readFully().decodeToString())
        StoredImeiState(
            originalImeis = json.getJSONObject(KEY_ORIGINAL_IMEIS).toImeiMap(),
            history = json.getJSONArray(KEY_HISTORY).toHistory(),
        )
    } catch (e: FileNotFoundException) {
        StoredImeiState()
    } catch (e: IOException) {
        Log.e(TAG, "Failed to read stored IMEI state", e)
        StoredImeiState()
    } catch (e: JSONException) {
        Log.e(TAG, "Stored IMEI state is corrupted, starting fresh", e)
        StoredImeiState()
    }

    fun save(state: StoredImeiState) {
        val json = JSONObject()
            .put(KEY_ORIGINAL_IMEIS, state.originalImeis.toJson())
            .put(KEY_HISTORY, state.history.toJson())
        val stream = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save IMEI state", e)
            return
        }
        try {
            stream.write(json.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save IMEI state", e)
            file.failWrite(stream)
        }
    }

    private fun Map<SimSlot, String>.toJson() = JSONObject().apply {
        forEach { (slot, imei) -> put(slot.name, imei) }
    }

    private fun JSONObject.toImeiMap(): Map<SimSlot, String> =
        SimSlot.entries.mapNotNull { slot -> optString(slot.name).takeIf { it.isNotEmpty() }?.let { slot to it } }
            .toMap()

    private fun List<ImeiChangeRecord>.toJson() = JSONArray().apply {
        forEach { record ->
            put(
                JSONObject()
                    .put(KEY_SLOT, record.slot.name)
                    .put(KEY_TIMESTAMP, record.timestamp.toEpochMilli())
                    .put(KEY_OLD_IMEI, record.oldImei)
                    .put(KEY_NEW_IMEI, record.newImei),
            )
        }
    }

    private fun JSONArray.toHistory(): List<ImeiChangeRecord> = (0 until length()).mapNotNull { index ->
        val item = getJSONObject(index)
        val slot = SimSlot.entries.firstOrNull { it.name == item.getString(KEY_SLOT) } ?: return@mapNotNull null
        ImeiChangeRecord(
            slot = slot,
            timestamp = Instant.ofEpochMilli(item.getLong(KEY_TIMESTAMP)),
            oldImei = item.getString(KEY_OLD_IMEI),
            newImei = item.getString(KEY_NEW_IMEI),
        )
    }

    private companion object {
        const val TAG = "ImeiStorage"
        const val KEY_ORIGINAL_IMEIS = "originalImeis"
        const val KEY_HISTORY = "history"
        const val KEY_SLOT = "slot"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_OLD_IMEI = "oldImei"
        const val KEY_NEW_IMEI = "newImei"
    }
}
