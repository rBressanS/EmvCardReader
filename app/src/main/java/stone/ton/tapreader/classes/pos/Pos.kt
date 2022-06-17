package stone.ton.tapreader.classes.pos

import android.content.Context
import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.CaPublicKey
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData
import stone.ton.tapreader.classes.pos.interfaces.ICardPoller
import stone.ton.tapreader.classes.pos.interfaces.IUIProcessor
import stone.ton.tapreader.classes.utils.AssetsParser
import stone.ton.tapreader.classes.utils.DataSets

class Pos(readActivity: ReadActivity, cardPoller: ICardPoller, uiProcessor:IUIProcessor) {
    val terminalTags = getTerminalTags(readActivity)
    val caPublicKeys = getCaPublicKeys(readActivity)
    val kernels = getKernels(readActivity)


    private fun getTerminalTags(context: Context): List<TerminalTag> {
        DataSets.terminalTags = AssetsParser.parseAsset(context, "terminal_config/terminal_tags")
        return DataSets.terminalTags
    }

    private fun getCaPublicKeys(context: Context): List<CaPublicKey> {
        DataSets.caPublicKeys = AssetsParser.parseAsset(context, "terminal_config/ca_public_keys")
        return DataSets.caPublicKeys
    }

    private fun getKernels(context: Context): List<KernelData> {
        DataSets.kernels = AssetsParser.parseAsset(context, "terminal_config/kernels")
        return DataSets.kernels
    }

    val reader: Reader = Reader(readActivity, kernels, terminalTags, cardPoller, uiProcessor)

}