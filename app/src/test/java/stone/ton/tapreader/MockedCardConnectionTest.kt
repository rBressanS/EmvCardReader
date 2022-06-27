package stone.ton.tapreader

import com.payneteasy.tlv.BerTlv
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import stone.ton.tapreader.MockedTerminals.getMockedTerminalOne
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.pos.process.coprocess.CoProcessKernelC2
import stone.ton.tapreader.pos.process.coprocess.CoProcessPCD

@RunWith(MockitoJUnitRunner::class)
class MockedCardConnectionTest {
    @Test
    fun doTransaction() {
        val kernel = CoProcessKernelC2()
        val mockedCard = MockedCards.getMockCardBTT()
        CoProcessPCD.cardConnection = mockedCard

        val outcome = kernel.start(buildStartPayload(mockedCard.fciResponse, getMockedTerminalOne()))
        println(outcome)
        assertTrue(outcome.outcomeParameter.status == CoProcessKernelC2.OutcomeParameter.Status.APPROVED)
    }

    private fun buildStartPayload(fciResponse:APDUResponse, syncDataList:List<BerTlv>): CoProcessKernelC2.StartProcessPayload {
        return CoProcessKernelC2.StartProcessPayload(fciResponse, syncDataList)
    }
}
