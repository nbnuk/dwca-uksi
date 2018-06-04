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
            if(line[8]=="accepted") {
                def establishmentMeans = line[9]
                nameLookUp.put(taxonVersionKey, [scientificName: scientificName, establishmentMeans: establishmentMeans])
            }
        }
        csvReader.close()
        nameLookUp
    }

    @Test
    public void scientificNameTests() {
        def name
        name = scientificNames().get("BMSSYS0000597955")
        Assert.assertEquals(name["scientificName"], "Tolypocladium longisegmentatum")

        name = scientificNames().get("NHMSYS0020475523")
        Assert.assertEquals(name["scientificName"], "Dreissena bugensis")
        Assert.assertEquals(name["establishmentMeans"], "Non-native")

        name = scientificNames().get("NHMSYS0000530674")
        Assert.assertEquals(name["scientificName"], "Turdus merula")

        name = scientificNames().get("NHMSYS0000332764")
        Assert.assertEquals(name["scientificName"], "Sciurus carolinensis")
        Assert.assertEquals(name["establishmentMeans"], "Non-native")

        name = scientificNames().get("NBNSYS0000005108")
        Assert.assertEquals(name["scientificName"], "Sciurus vulgaris")
        Assert.assertEquals(name["establishmentMeans"], "Native")

        // native redundant has records
        name = scientificNames().get("NBNSYS0000003491")
        Assert.assertEquals(name["scientificName"], "Saxifraga hirculus")
        Assert.assertEquals(name["establishmentMeans"], "Native")

        // non-native redundant has records
        name = scientificNames().get("NBNSYS0000005185")
        Assert.assertEquals(name["scientificName"], "Ursus arctos")
        Assert.assertEquals(name["establishmentMeans"], "Non-native")

        // non-native redundant has records
        name = scientificNames().get("NBNSYS0000007574")
        Assert.assertEquals(name["scientificName"], "Potamonectes canariensis")
        Assert.assertEquals(name["establishmentMeans"], "Non-native")

        // non-native redundant no records
        name = scientificNames().get("NBNSYS0000041445")
        Assert.assertEquals(name, null)

        // non-native redundant no records
        name = scientificNames().get("NBNSYS0000163318")
        Assert.assertEquals(name, null)
    }



}