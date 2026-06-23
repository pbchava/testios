import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SegmentedStringWriter;

public class JsonModifierStreamer {

    // Optimization 1: Reusing the factory enables Jackson's internal BufferRecycler.
    // This completely eliminates array allocations for internal buffers on subsequent runs.
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * Highly optimized in-memory JSON element replacer.
     * * @param jsonContent The source JSON data as a String
     * @param targetKey   The element name to search for
     * @param newValue    The new value to inject
     * @return SegmentedStringWriter containing the modified JSON payload
     */
    public static String modifyJsonElement(String jsonContent, String targetKey, String newValue) throws IOException {
        
        // Optimization 2: SegmentedStringWriter uses the factory's buffer pool, 
        // avoiding the synchronized overhead of standard java.io.StringWriter/StringBuffer.
        SegmentedStringWriter outputWriter = new SegmentedStringWriter(JSON_FACTORY._getBufferRecycler());

        // Optimization 3: Passing the String directly avoids StringReader wrapper overhead.
        try (JsonParser parser = JSON_FACTORY.createParser(jsonContent);
             JsonGenerator generator = JSON_FACTORY.createGenerator(outputWriter)) {

            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                generator.copyCurrentEvent(parser);

                if (token == JsonToken.FIELD_NAME) {
                    String currentName = parser.currentName();
                    parser.nextToken(); 

                    if (targetKey.equals(currentName)) {
                        generator.writeString(newValue);
                    } else {
                        generator.copyCurrentEvent(parser);
                    }
                }
            }
        }
        
        return outputWriter.getAndClear();
    }
    
    public static void main(String[] args) {
        String inputJson = "{\r\n"
        		+ "            \"SRVCNM\":\"BPM_OAltCteN\",\r\n"
        		+ "            \"STDS_DISP_NUMIP\":\"10.130.252.178\",\r\n"
        		+ "            \"STDL_OPE_ID\":\"91894344\",\r\n"
        		+ "            \"STDL_SES_ID\":\"1943377\",\r\n"
        		+ "            \"STDS_CTE_NOMBRE\":\"ANDRE,SALAS/LUNA\",\r\n"
        		+ "            \"CTEL_CTE_FECALTA\":\"20260622\",\r\n"
        		+ "            \"CTEL_EMPR_FECINGRESO\":\"201004\",\r\n"
        		+ "            \"CTES_DOM_NOMCALLE\":\"BENITO JAUREZ 23\",\r\n"
        		+ "            \"CTES_DOM_NOMCOLONIA\":\"TOLUCA DE LERDO CENTRO\",\r\n"
        		+ "            \"CTES_DOM_NOMCIUDAD\":\"TOLUCA\",\r\n"
        		+ "            \"CTEI_DOM_CVEESTADO\":\"15\",\r\n"
        		+ "            \"CTEL_DOM_CODPOSTAL\":\"50000\",\r\n"
        		+ "            \"CTEN_DOM_NUMTEL\":\"5523846287\",\r\n"
        		+ "            \"CTEL_PER_FECNACIM\":\"20001010\",\r\n"
        		+ "            \"CTES_PER_VALCURP\":\"SALA001010HMCLNNA3\",\r\n"
        		+ "            \"CTES_PER_VALINICRFC\":\"SALA\",\r\n"
        		+ "            \"CTEL_PER_FECRFC\":\"001010\",\r\n"
        		+ "            \"CTES_PER_CVEHOMORFC\":\"\",\r\n"
        		+ "            \"CTEI_PER_CVESECTOR\":\"32\",\r\n"
        		+ "            \"CTEI_PER_CVESEXO\":\"1\",\r\n"
        		+ "            \"CTEI_PER_TIPO\":\"1\",\r\n"
        		+ "            \"CTEI_PER_TIPOOCUP\":\"1\",\r\n"
        		+ "            \"CTEI_PER_CVEEDOCIV\":\"1\",\r\n"
        		+ "            \"CTEI_CTE_NUMSUCPROM\":\"100\",\r\n"
        		+ "            \"CTEN_PER_IMPINGMEN\":\"42000\",\r\n"
        		+ "            \"STDI_DISP_NUMCAJA\":\"0\",\r\n"
        		+ "            \"STDI_DISP_NUMSUC\":\"100\",\r\n"
        		+ "            \"CTEI_SERV_NUMPRODEJE\":\"66\",\r\n"
        		+ "            \"CTEI_SERV_CVEINSTEJE\":\"8\",\r\n"
        		+ "            \"VTCN_EMPP_NUMEXTEL\":\"0\",\r\n"
        		+ "            \"CTEI_DOM_STATUS\":\"1\",\r\n"
        		+ "            \"CTEI_DOM_TIPO\":\"1\",\r\n"
        		+ "            \"STDL_TRAN_FECSOL\":\"0\",\r\n"
        		+ "            \"CTEI_PER_CVEEMPBNMX\":\"1\",\r\n"
        		+ "            \"CTEI_PER_CVENIVESC\":\"0\",\r\n"
        		+ "            \"CTEI_CTE_CVEENVIO\":\"1\",\r\n"
        		+ "            \"CTEI_CTE_CVESEGM\":\"3\",\r\n"
        		+ "            \"CTEI_CTE_DIACORTE\":\"30\",\r\n"
        		+ "            \"CTEN_PER_CODAFIL\":\"\",\r\n"
        		+ "            \"CTEI_PER_CVENACION\":\"1\",\r\n"
        		+ "            \"CTEI_PER_CVEPAISNAC\":\"1\",\r\n"
        		+ "            \"CTEL_PER_CODCAEB\":\"99999\",\r\n"
        		+ "            \"CTEL_PER_CVEPAISRES\":\"1\",\r\n"
        		+ "            \"CTES_CTE_CVETEL\":\"1\",\r\n"
        		+ "            \"SSMI_OPRN_NUMVERSERV\":\"1\",\r\n"
        		+ "            \"SSML_DIAL_IDORIGN\": \"101699\",\r\n"
        		+ "            \"STDI_APCTE_TIPO\":\"1\",\r\n"
        		+ "            \"STDI_DISP_NUMCSI\":\"10\",\r\n"
        		+ "            \"STDI_DISP_TIPO\":\"1\",\r\n"
        		+ "            \"STDI_MD_TIPO\":\"20\",\r\n"
        		+ "            \"STDI_TRAN_COD\":\"0\",\r\n"
        		+ "            \"STDI_TRAN_CVE\":\"1\",\r\n"
        		+ "            \"STDL_SES_NUM\":\"0\",\r\n"
        		+ "            \"STDL_SOL_NUMVERS\":\"0\",\r\n"
        		+ "            \"STDL_TRAN_NUMID\":\"75\",\r\n"
        		+ "            \"STDL_TRAN_SCOD\":\"0\",\r\n"
        		+ "            \"STDS_DISP_NOMSTATION\":\"S0302111\",\r\n"
        		+ "            \"STDS_DISP_VALVERSOFT\":\"0\",\r\n"
        		+ "            \"STDS_SOL_CVEOCC\":\"A\"  ,\"CTEL_PER_CODACTBANX\":\"11\"}";
        String targetElement = "STDI_DISP_NUMCSI";
        String replacementValue = "04";

        try {
        	String result = modifyJsonElement(inputJson, targetElement, replacementValue);
            System.out.println(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
