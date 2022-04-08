package stone.ton.tapreader.classes.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class AssetsParser {
    companion object {
        inline fun <reified T> parseAsset(context: Context, assetFilePath: String): T {
            lateinit var jsonString: String
            try {
                jsonString = context.assets.open("$assetFilePath.json")
                    .bufferedReader()
                    .use { it.readText() }
            } catch (ioException: IOException) {
                //Log.d("", ioException)
                System.out.println(ioException)
            }
            val data = object : TypeToken<T>() {}.type
            return Gson().fromJson(jsonString, data)
        }
    }
}