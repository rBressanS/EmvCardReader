package stone.ton.tapreader.classes.pos

import android.content.Context
import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.CaPublicKey
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.utils.AssetsParser

class Pos(readActivity: ReadActivity) {
    val terminalTags = getTerminalTags(readActivity)
    val caPublicKeys = getCaPublicKeys(readActivity)
    val kernels = getKernels(readActivity)

    private fun getTerminalTags(context: Context): List<TerminalTag> {
        return AssetsParser.parseAsset(context, "terminal_config/terminal_tags")
    }

    private fun getCaPublicKeys(context: Context): List<CaPublicKey> {
        return AssetsParser.parseAsset(context, "terminal_config/ca_public_keys")
    }

    private fun getKernels(context: Context): List<KernelData> {
        return AssetsParser.parseAsset(context, "terminal_config/kernels")
    }

    val reader: Reader = Reader(readActivity, kernels, terminalTags)

}