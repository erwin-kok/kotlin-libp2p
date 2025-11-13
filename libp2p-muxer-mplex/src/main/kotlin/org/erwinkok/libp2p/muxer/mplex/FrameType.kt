package org.erwinkok.libp2p.muxer.mplex

public enum class FrameType(public val opcode: Int) {
    NEW_STREAM(0),
    MESSAGE_RECEIVER(1),
    MESSAGE_INITIATOR(2),
    CLOSE_RECEIVER(3),
    CLOSE_INITIATOR(4),
    RESET_RECEIVER(5),
    RESET_INITIATOR(6),
    ;

    companion object {
        private val maxOpcode = entries.maxByOrNull { it.opcode }!!.opcode
        private val byOpcodeArray = Array(maxOpcode + 1) { op -> entries.singleOrNull { it.opcode == op } }
        public operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}
