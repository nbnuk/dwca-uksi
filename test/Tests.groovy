import au.com.bytecode.opencsv.CSVReader
import org.junit.Assert;
import org.junit.Test;

public class TaxonTests {
    @Test
    public void firstTest() {
        Assert.assertTrue(true)
    }

    def baseDir = '/data/uk/dwca/'

    static def scientificNames(baseDir){

        def csvReader = new CSVReader(new FileReader("data/uk/dwca/taxa.csv"))
        def headers = csvReader.readNext()  //ignore header
        def nameLookUp = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonVersionKey = line[0]
            def scientificName = line[4]
            nameLookUp.put(taxonVersionKey, scientificName)
        }
        csvReader.close()
        nameLookUp
    }

    @Test
    public void scientificNameTests() {
        def name = scientificNames().get("BMSSYS0000051882")
        Assert.assertEquals(name, "Tolypocladium longisegmentum")

        name = scientificNames().get("NHMSYS0020475523")
        Assert.assertEquals(name, "Dreissena bugensis")

        name = scientificNames().get("NHMSYS0000530674")
        Assert.assertEquals(name, "Turdus merula")
    }



}