package stone.ton.tapreader.classes.pos.readercomponents

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import stone.ton.tapreader.ReadActivity
import stone.ton.tapreader.classes.dataclasses.TerminalTag
import stone.ton.tapreader.classes.dataclasses.kernel.KernelData


class EntryPoint(
    var readActivity: ReadActivity,
    var kernels: List<KernelData>,
    var terminalTags: List<TerminalTag>,
) {

    fun readCardData(amount: Int, paymentType: String) {
        val terminalCandidateList = buildCandidateList(amount, paymentType)
        val intent = Intent(readActivity, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val nfcintent =
            PendingIntent.getActivity(readActivity, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))

        val nfcAdapter = NfcAdapter.getDefaultAdapter(readActivity)
        val extra = Bundle()
        extra.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000)
        readActivity.onResumeCallback = {
            nfcAdapter.enableForegroundDispatch(
                readActivity,
                nfcintent,
                intentFiltersArray,
                arrayOf(arrayOf<String>(IsoDep::class.java.name))
            )

            nfcAdapter.enableReaderMode(
                readActivity,
                EmvReaderCallback(terminalCandidateList, readActivity, terminalTags),
                (NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B),
                extra
            )
        }
        readActivity.onPauseCallback = {
            nfcAdapter.disableForegroundDispatch(readActivity)
            nfcAdapter.disableReaderMode(readActivity)
        }

    }

    private fun buildCandidateList(
        amount: Int,
        additionalFilter: String = "",
        transactionType: String? = "00",
    ): ArrayList<String> {
        val candidateList = ArrayList<String>()
        for (kernel in kernels) {
            for (kernelApp in kernel.kernelApplications) {
                if (additionalFilter != "" && kernelApp.filteringSelector != additionalFilter) {
                    continue
                }
                for (transactionConfig in kernelApp.transactionConfig) {
                    if (transactionConfig.transactionType == transactionType) {
                        if (!transactionConfig.zeroAmountAllowedFlag && amount == 0) {
                            continue
                        }
                        if (transactionConfig.readerContactlessTransactionLimit != null) {
                            if (amount >= transactionConfig.readerContactlessTransactionLimit!!) {
                                continue
                            }
                        }
                    }
                }
                candidateList.addAll(kernelApp.aids)
            }
        }
        return candidateList
    }


    val readerCombinations: ArrayList<EntryPointCombination> = arrayListOf(
        EntryPointCombination("A0000000041010", 4),
        EntryPointCombination("A0000000043060", 4),
        EntryPointCombination("A0000000031010", 2),
        EntryPointCombination("A0000000032010", 2),
    )

    fun buildPreProcessIndicators(amountAuthorized: Int) {
        for (combinationEntry in readerCombinations) {

            val combinationData = combinationEntry.configurationData
            val combinationIndicators = combinationEntry.preProcessingIndicators

            combinationIndicators.isStatusCheckRequested =
                combinationData.isStatusCheckSupport && amountAuthorized > -1

            if (amountAuthorized == 0) {
                if (!combinationData.isZeroAmountAllowedForOffline) {
                    if (!combinationData.isZeroAmountAllowed) {
                        combinationIndicators.isContactlessApplicationNotAllowed = true
                    } else {
                        combinationIndicators.isZeroAmount = true
                    }
                }
            }
            if (amountAuthorized >= combinationData.readerContactlessTransactionLimit) {
                combinationIndicators.isContactlessApplicationNotAllowed = true
            }

            if (amountAuthorized >= combinationData.readerContactlessFloorLimit) {
                combinationIndicators.isReaderContactlessFloorLimitExceeded = true
            }

            if (amountAuthorized >= combinationData.readerCvmRequiredLimit) {
                combinationIndicators.isReaderCvmRequiredLimitExceeded = true
            }

            if (combinationData.TTQ.isNotEmpty()) {
                combinationIndicators.TTQ = combinationData.TTQ
                combinationIndicators.TTQ =
                    setValueForByteAndBit(combinationIndicators.TTQ, 1, 7, false)
                combinationIndicators.TTQ =
                    setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, false)

                if (combinationIndicators.isReaderContactlessFloorLimitExceeded || combinationIndicators.isStatusCheckRequested) {
                    combinationIndicators.TTQ =
                        setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, true)
                }

                if (combinationIndicators.isZeroAmount) {
                    if ((combinationIndicators.TTQ[0].toInt() and 0b1000) == 0) {
                        combinationIndicators.TTQ =
                            setValueForByteAndBit(combinationIndicators.TTQ, 1, 8, true)
                    } else {
                        combinationIndicators.isContactlessApplicationNotAllowed = true
                    }
                }

                if (combinationIndicators.isReaderCvmRequiredLimitExceeded) {
                    combinationIndicators.TTQ =
                        setValueForByteAndBit(combinationIndicators.TTQ, 1, 7, true)
                }


            }

        }

    }

    fun preProcess(amountAuthorized: Int): Outcome {
        buildPreProcessIndicators(amountAuthorized)
        val hasAnyContactlessAllowed =
            readerCombinations.any { !it.preProcessingIndicators.isContactlessApplicationNotAllowed }
        if (!hasAnyContactlessAllowed) {
            return Outcome.TRY_ANOTHER_INTERFACE
        }
        return activateProtocol()
    }

    fun activateProtocol(): Outcome {
        return selectCombination()
    }

    fun selectCombination(): Outcome {
        return activateKernel()
    }

    fun activateKernel(): Outcome {
        return processByKernel()
    }

    fun processByKernel(): Outcome {
        return Outcome.APPROVED
    }

    fun runForStartA(amountAuthorized: Int) {
        preProcess(amountAuthorized)
    }

    fun runForStartB() {
        activateProtocol()
    }

    fun runForStartC() {
        selectCombination()
    }

    fun runForStartD() {
        activateKernel()
    }

    fun setValueForByteAndBit(
        byteArray: ByteArray,
        byteNum: Int,
        bitNum: Int,
        value: Boolean,
    ): ByteArray {
        var myByteArray = byteArray.clone()
        if (value) {
            myByteArray[byteNum] = (myByteArray[byteNum].toInt() or (1 shl bitNum)).toByte()
        } else {
            myByteArray[byteNum] = (myByteArray[byteNum].toInt() and (0 shl bitNum)).toByte()
        }

        return myByteArray
    }

}

class EntryPointCombination(val AID: String, val kernelId: Int) {
    constructor(
        AID: String,
        kernelId: Int,
        configurationData: EPConfigurationData,
        preProcessingIndicators: EPPreProcessingIndicators,
    ) : this(AID, kernelId) {
        this.configurationData = configurationData
        this.preProcessingIndicators = preProcessingIndicators
    }

    var configurationData: EPConfigurationData = EPConfigurationData()
    var preProcessingIndicators: EPPreProcessingIndicators = EPPreProcessingIndicators()
}

class EPConfigurationData {
    val isStatusCheckSupport = false
    val isZeroAmountAllowed = false
    val isZeroAmountAllowedForOffline = false
    val readerContactlessTransactionLimit = 0
    val readerContactlessFloorLimit = 0
    val terminalFloorLimit = 0
    val readerCvmRequiredLimit = 0
    val TTQ = byteArrayOf(0x25, 0, 0x40, 0) //TerminalTransactionQualifier
    val extendedSelectionSupport = true
}

class EPPreProcessingIndicators {
    var isStatusCheckRequested = false
    var isContactlessApplicationNotAllowed = false
    var isZeroAmount = false
    val isZeroAmountAllowed = false
    var isReaderCvmRequiredLimitExceeded = false
    var isReaderContactlessFloorLimitExceeded = false
    var TTQ = ByteArray(0) //TerminalTransactionQualifier
}
