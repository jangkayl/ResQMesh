package edu.cit.resqmesh.core.network.bluetooth.gatt

import edu.cit.resqmesh.core.logging.AppLogger
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.bluetooth.state.BleLink
import edu.cit.resqmesh.core.network.bluetooth.state.MeshFrameCodec
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * A lost or duplicated GATT chunk splices the next frame into the current one, leaving a correctly sized but
 * undecodable frame and a buffered tail that would swallow the following frames too. The MeshPayload decode is
 * the only resync signal: errors thrown later by handlers say nothing about framing and never clear the link.
 */
internal object GattFrameResync {
    fun deliver(link: BleLink, frame: ByteArray, process: (ByteArray) -> Unit) = deliver(
        link.receiveBuffer, frame, process,
        decodes = ::decodes,
        log = { dropped ->
            AppLogger.d("BLE_MESH", "GATT_RESYNC link=${link.generation} endpoint=${link.endpoint} dropped=$dropped")
        }
    )

    /** The frame is still handed to [process], which keeps its own parse-failure logging. */
    fun deliver(
        receiveBuffer: MeshFrameCodec.Reassembler,
        frame: ByteArray,
        process: (ByteArray) -> Unit,
        decodes: (ByteArray) -> Boolean,
        log: (Int) -> Unit
    ) {
        if (!decodes(frame)) {
            val dropped = receiveBuffer.size
            receiveBuffer.clear()
            log(dropped)
        }
        process(frame)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodes(frame: ByteArray): Boolean =
        runCatching { ProtoBuf.decodeFromByteArray(MeshPayload.serializer(), frame) }.isSuccess
}
