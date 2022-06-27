package stone.ton.tapreader

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.utils.AssetsParser
import stone.ton.tapreader.utils.DataSets
import stone.ton.tapreader.utils.General.Companion.decodeHex

object MockedTerminals {
    fun getMockedTerminalOne(): List<BerTlv> {

        return getAsBerTlvList(terminalOneApdus)
    }

    private val terminalOneApdus = mapOf(
        "6F" to "",
        "DF8124" to "000000000100",
        "DF8134" to "0012",
        "DF8135" to "0018",
        "DF8132" to "0014",
        "DF8133" to "0032",
        "DF8136" to "012c",
        "DF8137" to "32",
        "DF811F" to "08",
        "9F02" to "000000000100",
        "DF8126" to "000000020000",
        "DF8118" to "40",
        "DF8119" to "08",
        "9A" to "220727",
        "9F35" to "21",
        "9f40" to "4000802000",
        "9f1a" to "0076",
        "5F2A" to "0986",
        "9c" to "00",
"DF8123" to "000000000000",
        "DF8120" to "F45084800C",
        "DF8121" to "0000000000",
        "DF8122" to "F45084800C",
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