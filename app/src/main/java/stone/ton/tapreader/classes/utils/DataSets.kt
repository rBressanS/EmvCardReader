package stone.ton.tapreader.classes.utils

import stone.ton.tapreader.classes.dataclasses.CaPublicKey
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData

object DataSets {
    lateinit var terminalTags: List<TerminalTag>
    lateinit var caPublicKeys: List<CaPublicKey>
    lateinit var kernels: List<KernelData>
}