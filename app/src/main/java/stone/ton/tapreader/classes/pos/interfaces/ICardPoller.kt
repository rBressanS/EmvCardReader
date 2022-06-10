package stone.ton.tapreader.classes.pos.interfaces

import android.nfc.NfcAdapter

interface ICardPoller {

    fun startCardPolling(readerCallback: NfcAdapter.ReaderCallback)

}