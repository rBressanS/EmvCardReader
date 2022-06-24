package stone.ton.tapreader.models.emv

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv

class CandidateApp(appTag: BerTlv) {
    val aid: ByteArray = appTag.find(BerTag(0x4f)).bytesValue

    var kernelId: Int = 0

    val priority: ByteArray = appTag.find(BerTag(0x87)).bytesValue

    val label = appTag.find(BerTag(0x50))?.bytesValue

    val kernelIdentifier = appTag.find(BerTag(0x9f, 0x2a))?.bytesValue
    val extendedSelection = appTag.find(BerTag(0x9f, 0x29))?.bytesValue
    val asrpd = appTag.find(BerTag(0x9f, 0x0a))?.bytesValue
}