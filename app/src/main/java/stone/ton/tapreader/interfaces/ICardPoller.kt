package stone.ton.tapreader.interfaces

import android.nfc.NfcAdapter

interface ICardPoller {

    fun startCardPolling(readerCallback: NfcAdapter.ReaderCallback)

}