package stone.ton.tapreader.classes.pos.interfaces

import android.nfc.NfcAdapter

interface CardPoller {

    fun startCardPolling(readerCallback: NfcAdapter.ReaderCallback)

}