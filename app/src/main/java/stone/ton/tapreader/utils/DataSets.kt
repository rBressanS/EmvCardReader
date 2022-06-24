package stone.ton.tapreader.utils

import stone.ton.tapreader.models.emv.CaPublicKey
import stone.ton.tapreader.models.emv.TerminalTag
import stone.ton.tapreader.models.kernel.KernelData

object DataSets {
    lateinit var terminalTags: List<TerminalTag>
    lateinit var caPublicKeys: List<CaPublicKey>
    lateinit var kernels: List<KernelData>
}