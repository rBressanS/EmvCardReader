package stone.ton.tapreader.pos.process.coprocess

import android.util.Log
import com.payneteasy.tlv.BerTag
import com.payneteasy.tlv.BerTlv
import com.payneteasy.tlv.BerTlvBuilder
import stone.ton.tapreader.interfaces.IProcess
import stone.ton.tapreader.interfaces.IProcessSignal
import stone.ton.tapreader.models.pos.ProcessSignal
import stone.ton.tapreader.pos.process.ProcessSignalQueue
import stone.ton.tapreader.pos.process.coprocess.CoProcessPCD.cardPoller
import stone.ton.tapreader.pos.process.process_d.UserInterfaceRequestData
import stone.ton.tapreader.utils.DataSets
import stone.ton.tapreader.utils.General.Companion.decodeHex
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object CoProcessMain : IProcess {
    //TODO implement dataset

    //TODO Stopped at EMV Kernel C8 - 2.2.5
    // 4 Step

    var amount = 0
    private var paymentType = ""
    private val languagePreference = UserInterfaceRequestData.Companion.LanguagePreference.PT_BR

    fun startTransaction(amount: Int, paymentType: String) {
        Log.i("ProcessMain", "startTransaction")
        this.amount = amount
        this.paymentType = paymentType

        ProcessSignalQueue.addToQueue(CoProcessPCD.buildSignalForPolling(this, cardPoller))

        ProcessSignalQueue.addToQueue(
            CoProcessDisplay.buildSignalForMsg(
                this, UserInterfaceRequestData(
                    UserInterfaceRequestData.Companion.MessageIdentifier.PRESENT_CARD,
                    UserInterfaceRequestData.Companion.Status.READY_TO_READ,
                    0,
                    languagePreference,
                    UserInterfaceRequestData.Companion.ValueQualifier.AMOUNT,
                    amount,
                    76
                )
            )
        )
    }

    fun initializeKernel() {}

    private fun getDateOnFormat(format: String): String {
        val dateFormatter: DateFormat = SimpleDateFormat(format)
        dateFormatter.isLenient = false
        val today = Date()
        return dateFormatter.format(today)
    }

    override fun processSignal(processFrom: IProcess, signal: String, params: Any?) {
        if (signal == "CardDetected") {

            val response = CoProcessSelection.getPPSE()
            if (response != null) {
                if (response.kenerlId == 2) {
                    val kernelData =
                        DataSets.kernels.find { kernelData -> kernelData.kernelId == 2 }
                    if (kernelData != null) {
                        val kernel2 = CoProcessKernelC2()
                        kernel2.kernelData = kernelData
                        val syncDataList = ArrayList<BerTlv>()
                        syncDataList.add(
                            BerTlvBuilder().addEmpty(BerTag("6F".decodeHex())).buildTlv()
                        )
                        syncDataList.add(
                            BerTlvBuilder().addAmount(
                                BerTag("9F02".decodeHex()),
                                this.amount.toBigDecimal()
                            ).buildTlv()
                        )

                        syncDataList.add(
                            BerTlvBuilder().addHex(
                                BerTag("9A".decodeHex()),
                                getDateOnFormat("yyMMdd")
                            ).buildTlv()
                        )
                        syncDataList.add(
                            BerTlvBuilder().addHex(
                                BerTag("9F21".decodeHex()),
                                getDateOnFormat("hhmmss")
                            ).buildTlv()
                        )
                        syncDataList.add(
                            BerTlvBuilder().addHex(
                                BerTag("9C".decodeHex()),
                                "00"
                            ).buildTlv()
                        )
                        for(terminalTag in DataSets.terminalTags){
                            syncDataList.add(
                                BerTlvBuilder().addHex(
                                    BerTag(terminalTag.tag.decodeHex()),
                                    terminalTag.value
                                ).buildTlv()
                            )
                        }
                        for (kernelTag in kernelData.kernelTags) {
                            syncDataList.add(
                                BerTlvBuilder().addHex(
                                    BerTag(kernelTag.tag.decodeHex()),
                                    kernelTag.value
                                ).buildTlv()
                            )
                        }
                        val startProcessPayload = CoProcessKernelC2.StartProcessPayload(
                            response.fciResponse,
                            syncDataList
                        )
                        val result = kernel2.start(startProcessPayload)
                        println(result)

                        //CoProcessKernelC2.kernelData = kernelData
                        //CoProcessKernelC2.start(response.fciResponse)
                    }
                }
            }

        }
    }

    fun buildSignalForCardDetected(processFrom: IProcess): IProcessSignal {
        return ProcessSignal("CardDetected", null, this, processFrom)
    }

}