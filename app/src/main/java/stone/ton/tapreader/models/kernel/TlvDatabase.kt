package stone.ton.tapreader.models.kernel

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.utils.General.Companion.decodeHex

class TlvDatabase {

    private var entries = HashMap<BerTag, TlvDatabaseEntry>()

    fun isPresent(tag: ByteArray): Boolean {
        return entries.contains(BerTag(tag))
    }

    fun tagOf(name: String): TlvDatabaseEntry? {
        val entry = entries.filterValues { it.name == name }
        return if (entry.size != 1) {
            null
        } else {
            entry.values.first()
        }
    }

    fun getTlv(tag: BerTag): TlvDatabaseEntry? {
        return entries[tag]
    }

    fun getTlv(tag: ByteArray): TlvDatabaseEntry? {
        return getTlv(BerTag(tag))
    }

    fun getTlv(tag: String): TlvDatabaseEntry? {
        return getTlv(tag.decodeHex())
    }

    fun getLength(tag: BerTag): Int? {
        return entries[tag]?.fullTag?.bytesValue?.size
    }

    fun initialize(tag: BerTag) {
        entries[tag] = TlvDatabaseEntry("", createEmptyTlv(tag))
    }

    fun add(tlv: BerTlv) {
        entries[tlv.tag] = TlvDatabaseEntry("", tlv)
    }

    fun runIfExists(tag: ByteArray, cb: (TlvDatabaseEntry) -> Unit) {
        val t = getTlv(tag)
        if (t != null) {
            cb.invoke(t)
        }
    }


    fun parseAndStoreCardResponse(tlv: BerTlv) {
        if (tlv.isConstructed) {
            val tagList = tlv.values
            for (tag in tagList) {
                parseAndStoreCardResponse(tag)
            }
        } else {
            entries[tlv.tag] = TlvDatabaseEntry("", tlv)
        }
    }

    private fun createEmptyTlv(tag: BerTag): BerTlv {
        return BerTlvBuilder.template(tag).buildTlv()
    }


    data class TlvDatabaseEntry(
        var name: String,
        var fullTag: BerTlv
    )
}