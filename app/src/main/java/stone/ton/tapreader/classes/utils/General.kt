package stone.ton.tapreader.classes.utils

class General {

    companion object{
        fun isPureAscii(s: ByteArray?): Boolean {
            var result = true
            if (s != null) {
                for (i in s.indices) {
                    val c = s[i].toInt()
                    if (c < 31 || c > 127) {
                        result = false
                        break
                    }
                }
            }
            return result
        }

        fun ByteArray.toHex(): String {
            /*!
            This method converts bytes to strings of hex
            */
            val result = StringBuilder()
            for (inputbyte in this) {
                result.append(String.format("%02X" + " ", inputbyte))
            }
            return result.toString().trim()
        }

        fun String.decodeHex(): ByteArray {
            check((this.replace(" ", "").length % 2) == 0) { "Must have an even length" }

            return replace(" ", "").chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }
    }


}