package stone.ton.tapreader

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.utils.General.Companion.decodeHex

object MockedTerminals {
    fun getMockedTerminalOne(): List<BerTlv> {
        return getAsBerTlvList(cardOneApdus)
    }

    private val cardOneApdus = mapOf(
        "6F" to "",
        "DF8124" to "000000000100",
        "DF8134" to "0012",
        "DF8135" to "0018",
        "DF8132" to "0014",
        "DF8133" to "0032",
        "DF8136" to "012c",
        "DF8137" to "32",
        "DF811F" to "08",
    )

    private fun getAsBerTlvList(map: Map<String, String>): List<BerTlv> {
        val syncDataList = ArrayList<BerTlv>()
        for (entry in map) {
            syncDataList.add(
                BerTlvBuilder().addHex(BerTag(entry.key.decodeHex()), entry.value)
                    .buildTlv()
            )
        }
        return syncDataList
    }

}