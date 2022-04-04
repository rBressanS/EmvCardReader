package stone.ton.tapreader.classes.emv

import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv

class CandidateApp(appTag: BerTlv) {
    val aid = appTag.find(BerTag(0x4f)).bytesValue

    val priority = appTag.find(BerTag(0x87)).bytesValue

    val label = appTag.find(BerTag(0x50)).bytesValue
}