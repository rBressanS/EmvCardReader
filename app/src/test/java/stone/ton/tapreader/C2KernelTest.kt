package stone.ton.tapreader

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.payneteasy.tlv.BerTlv
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import stone.ton.tapreader.MockedTerminals.getMockedTerminalOne
import stone.ton.tapreader.models.apdu.APDUResponse
import stone.ton.tapreader.models.emv.CaPublicKey
import stone.ton.tapreader.models.emv.TerminalTag
import stone.ton.tapreader.pos.process.coprocess.CoProcessKernelC2
import stone.ton.tapreader.pos.process.coprocess.CoProcessPCD
import stone.ton.tapreader.utils.AssetsParser
import stone.ton.tapreader.utils.DataSets

@RunWith(MockitoJUnitRunner::class)
class C2KernelTest {
    @Test
    fun doTransaction() {
        val kernel = CoProcessKernelC2()
        val mockedCard = MockedCards.getMockCardBtt2()
        CoProcessPCD.cardConnection = mockedCard
        DataSets.caPublicKeys = getCaPks()
        DataSets.terminalTags = getTerminalTags()


        val outcome = kernel.start(buildStartPayload(mockedCard.fciResponse, getMockedTerminalOne()))
        println(outcome)
        assertTrue(outcome.outcomeParameter.status == CoProcessKernelC2.OutcomeParameter.Status.ONLINE_REQUEST)
    }

    private fun loadDataSets(context:Context){
        DataSets.terminalTags = AssetsParser.parseAsset(context, "terminal_config/terminal_tags")
        DataSets.caPublicKeys = getCaPks()
        DataSets.kernels = AssetsParser.parseAsset(context, "terminal_config/kernels")
    }

    private fun getCaPks(): List<CaPublicKey>{
        val data = object : TypeToken<List<CaPublicKey>>() {}.type
        val jsonString = "[\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"B8048ABC30C90D976336543E3FD7091C8FE4800DF820ED55E7E94813ED00555B573FECA3D84AF6131A651D66CFF4284FB13B635EDD0EE40176D8BF04B7FD1C7BACF9AC7327DFAA8AA72D10DB3B8E70B2DDD811CB4196525EA386ACC33C0D9D4575916469C4E4F53E8E1C912CC618CB22DDE7C3568E90022E6BBA770202E4522A2DD623D180E215BD1D1507FE3DC90CA310D27B3EFCCD8F83DE3052CAD1E48938C68D095AAC91B5F37E28BB49EC7ED597\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1224\",\n" +
                "    \"index\": \"05\",\n" +
                "    \"checksum\": \"EBFA0D5D06D8CE702DA3EAE890701D45E274C845\",\n" +
                "    \"rId\": \"A000000004\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"A0DCF4BDE19C3546B4B6F0414D174DDE294AABBB828C5A834D73AAE27C99B0B053A90278007239B6459FF0BBCD7B4B9C6C50AC02CE91368DA1BD21AAEADBC65347337D89B68F5C99A09D05BE02DD1F8C5BA20E2F13FB2A27C41D3F85CAD5CF6668E75851EC66EDBF98851FD4E42C44C1D59F5984703B27D5B9F21B8FA0D93279FBBF69E090642909C9EA27F898959541AA6757F5F624104F6E1D3A9532F2A6E51515AEAD1B43B3D7835088A2FAFA7BE7\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1224\",\n" +
                "    \"index\": \"F1\",\n" +
                "    \"checksum\": \"D8E68DA167AB5A85D8C3D55ECB9B0517A1A5B4BB\",\n" +
                "    \"rId\": \"A000000004\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"CB26FC830B43785B2BCE37C81ED334622F9622F4C89AAE641046B2353433883F307FB7C974162DA72F7A4EC75D9D657336865B8D3023D3D645667625C9A07A6B7A137CF0C64198AE38FC238006FB2603F41F4F3BB9DA1347270F2F5D8C606E420958C5F7D50A71DE30142F70DE468889B5E3A08695B938A50FC980393A9CBCE44AD2D64F630BB33AD3F5F5FD495D31F37818C1D94071342E07F1BEC2194F6035BA5DED3936500EB82DFDA6E8AFB655B1EF3D0D7EBF86B66DD9F29F6B1D324FE8B26CE38AB2013DD13F611E7A594D675C4432350EA244CC34F3873CBA06592987A1D7E852ADC22EF5A2EE28132031E48F74037E3B34AB747F\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1230\",\n" +
                "    \"index\": \"06\",\n" +
                "    \"checksum\": \"F910A1504D5FFB793D94F3B500765E1ABCAD72D9\",\n" +
                "    \"rId\": \"A000000004\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24954C2FCA3074825DDD4C0C8F186CB020F683E02F2DEAD3969133F06F7845166ACEB57CA0FC2603445469811D293BFEFBAFAB57631B3DD91E796BF850A25012F1AE38F05AA5C4D6D03B1DC2E568612785938BBC9B3CD3A910C1DA55A5A9218ACE0F7A21287752682F15832A678D6E1ED0B\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1224\",\n" +
                "    \"index\": \"08\",\n" +
                "    \"checksum\": \"20D213126955DE205ADC2FD2822BD22DE21CF9A8\",\n" +
                "    \"rId\": \"A000000003\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"9D912248DE0A4E39C1A7DDE3F6D2588992C1A4095AFBD1824D1BA74847F2BC4926D2EFD904B4B54954CD189A54C5D1179654F8F9B0D2AB5F0357EB642FEDA95D3912C6576945FAB897E7062CAA44A4AA06B8FE6E3DBA18AF6AE3738E30429EE9BE03427C9D64F695FA8CAB4BFE376853EA34AD1D76BFCAD15908C077FFE6DC5521ECEF5D278A96E26F57359FFAEDA19434B937F1AD999DC5C41EB11935B44C18100E857F431A4A5A6BB65114F174C2D7B59FDF237D6BB1DD0916E644D709DED56481477C75D95CDD68254615F7740EC07F330AC5D67BCD75BF23D28A140826C026DBDE971A37CD3EF9B8DF644AC385010501EFC6509D7A41\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1228\",\n" +
                "    \"index\": \"09\",\n" +
                "    \"checksum\": \"1FF80A40173F52D7D27E0F26A146A1C8CCB29046\",\n" +
                "    \"rId\": \"A000000003\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"AA94A8C6DAD24F9BA56A27C09B01020819568B81A026BE9FD0A3416CA9A71166ED5084ED91CED47DD457DB7E6CBCD53E560BC5DF48ABC380993B6D549F5196CFA77DFB20A0296188E969A2772E8C4141665F8BB2516BA2C7B5FC91F8DA04E8D512EB0F6411516FB86FC021CE7E969DA94D33937909A53A57F907C40C22009DA7532CB3BE509AE173B39AD6A01BA5BB85\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1231\",\n" +
                "    \"index\": \"0E\",\n" +
                "    \"checksum\": \"A7266ABAE64B42A3668851191D49856E17F8FBCD\",\n" +
                "    \"rId\": \"A000000025\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"C8D5AC27A5E1FB89978C7C6479AF993AB3800EB243996FBB2AE26B67B23AC482C4B746005A51AFA7D2D83E894F591A2357B30F85B85627FF15DA12290F70F05766552BA11AD34B7109FA49DE29DCB0109670875A17EA95549E92347B948AA1F045756DE56B707E3863E59A6CBE99C1272EF65FB66CBB4CFF070F36029DD76218B21242645B51CA752AF37E70BE1A84FF31079DC0048E928883EC4FADD497A719385C2BBBEBC5A66AA5E5655D18034EC5\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1231\",\n" +
                "    \"index\": \"0F\",\n" +
                "    \"checksum\": \"A73472B3AB557493A9BC2179CC8014053B12BAB4\",\n" +
                "    \"rId\": \"A000000025\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"hashAlgorithmIndicator\": 1,\n" +
                "    \"publicKeyAlgorithmIndicator\": 1,\n" +
                "    \"modulus\": \"CF98DFEDB3D3727965EE7797723355E0751C81D2D3DF4D18EBAB9FB9D49F38C8C4A826B99DC9DEA3F01043D4BF22AC3550E2962A59639B1332156422F788B9C16D40135EFD1BA94147750575E636B6EBC618734C91C1D1BF3EDC2A46A43901668E0FFC136774080E888044F6A1E65DC9AAA8928DACBEB0DB55EA3514686C6A732CEF55EE27CF877F110652694A0E3484C855D882AE191674E25C296205BBB599455176FDD7BBC549F27BA5FE35336F7E29E68D783973199436633C67EE5A680F05160ED12D1665EC83D1997F10FD05BBDBF9433E8F797AEE3E9F02A34228ACE927ABE62B8B9281AD08D3DF5C7379685045D7BA5FCDE58637\",\n" +
                "    \"exponent\": \"03\",\n" +
                "    \"expiryDate\": \"1231\",\n" +
                "    \"index\": \"10\",\n" +
                "    \"checksum\": \"C729CF2FD262394ABC4CC173506502446AA9B9FD\",\n" +
                "    \"rId\": \"A000000025\"\n" +
                "  }\n" +
                "]"
        return Gson().fromJson(jsonString, data)
    }

    private fun getTerminalTags(): List<TerminalTag>{
        val data = object : TypeToken<List<TerminalTag>>() {}.type
        val jsonString = "[\n" +
                "  {\n" +
                "    \"tag\": \"9F1E\",\n" +
                "    \"value\": \"3132333435363738\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F1A\",\n" +
                "    \"value\": \"0076\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F39\",\n" +
                "    \"value\": \"07\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F1B\",\n" +
                "    \"value\": \"00000000\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"5F2A\",\n" +
                "    \"value\": \"0986\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"5F36\",\n" +
                "    \"value\": \"02\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F01\",\n" +
                "    \"value\": \"000000000000\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F16\",\n" +
                "    \"value\": \"000000000000000000000000000000\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F1C\",\n" +
                "    \"value\": \"3132333435363738\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F4E\",\n" +
                "    \"value\": \"534F4654504F53\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F7A\",\n" +
                "    \"value\": \"00\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"tag\": \"9F15\",\n" +
                "    \"value\": \"0000\"\n" +
                "  }\n" +
                "]"
        return Gson().fromJson(jsonString, data)
    }
    private fun buildStartPayload(fciResponse:APDUResponse, syncDataList:List<BerTlv>): CoProcessKernelC2.StartProcessPayload {
        return CoProcessKernelC2.StartProcessPayload(fciResponse, syncDataList)
    }
}
