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
    }

}