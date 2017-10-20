package au.org.ala.uk.speciesinventory

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import org.apache.commons.io.FileUtils

/**
 * Script for creating a DwCA from data exported from the UK species inventory.
 */
public class CreateDwcA {

    public static void main(String[] args){

        def requiredFiles = [
                "TAXON_LIST_VERSION.csv",
                 "TAXON_LIST.csv",
                 "ORGANISM_MASTER.csv",
                 "NAMESERVER.csv",
                 "TAXON_VERSION.csv",
                 "TAXON.csv",
                 "TAXON_LIST_ITEM.csv",
                 "TAXON_RANK.csv",
                 "TAXON_GROUP_NAME.csv"
        ]

        if(args.length != 2){
            println ("Supply a base directory containing UK species inventory export files, and an output directory. e.g. /data/uk  /data/uk/dwca")
            println ("Required files include: \n\n\t" + requiredFiles.join("\n\t"))
            return
        }

        if(args.length == 2){

            def viable = true
            //check for files
            requiredFiles.each {
                if(!new File(args[0] + File.separatorChar + it).exists()){
                    println("Missing file: " + args[0] + File.separatorChar + it)
                    viable = false
                }
            }

            if(!new File(args[1]).exists()){
                println("Output directory does not exist " + args[1])
                FileUtils.forceMkdir(new File(args[1]))
            }

            if(!viable){
                return;
            }
        }

        def baseDir = args[0] + File.separatorChar

        //read the taxon list version keys into map
        def groupIDMap = {

            def reader = new CSVReader(new FileReader(baseDir + "TAXON_GROUP_NAME.csv"))
            reader.readNext()  //ignore header
            def map = [:]
            def line = null
            while ((line = reader.readNext()) != null) {
                def key = line[0]
                def name = line[1]
                map.put(key, name)
            }
            map
        }.call()

        def taxonGroupMap = {

            def reader = new CSVReader(new FileReader(baseDir + "TAXON_VERSION.csv"))
            def headers = reader.readNext()  //ignore header
            def map = [:]
            def line = null
            while ((line = reader.readNext()) != null) {
                def groupKey = line[11]
                def groupName = groupIDMap.get(groupKey)
                def tvKey = line[0]
                map.put(tvKey, groupName)
            }
            map
        }.call()

        //read the taxon list version keys into map
        def versionListMap = {
            def taxonListVersionReader = new CSVReader(new FileReader(baseDir + "TAXON_LIST_VERSION.csv"))
            def headers = taxonListVersionReader.readNext()  //ignore header
            def versionListMap = [:]
            def line = null
            while ((line = taxonListVersionReader.readNext()) != null) {
                def versionKey = line[0]
                def listKey = line[1]
                versionListMap.put(versionKey, listKey)
            }
            versionListMap
        }.call()

        //read taxon list into map
        def taxonListMap = {
            def taxonListReader = new CSVReader(new FileReader(baseDir + "TAXON_LIST.csv"))
            def headers = taxonListReader.readNext()  //ignore header
            def taxonListMap = [:]
            def line = null
            while ((line = taxonListReader.readNext()) != null) {
                def listKey = line[0]
                taxonListMap.put(listKey, [list: line[1], authority: line[4], description: line[2]])
            }
            taxonListMap
        }.call()

        //organism key -> taxon version key map
        def organismTaxonVersionKeyMap = {
            def map = [:]
            def orgMasterReader = new CSVReader(new FileReader(baseDir + "ORGANISM_MASTER.csv"))
            def line = ""
            while((line = orgMasterReader.readNext()) != null) {
                map.put(line[0], line[2])
            }
            orgMasterReader.close()
            map
        }.call()

        def taxonVersionRankLookup = CreateDwcA.taxonVersionRankLookup(baseDir)
        def taxonVersionLookup = CreateDwcA.taxonVersionLookup(baseDir)
        def scientificNameLookup = CreateDwcA.readScientificNames(baseDir)
        def nameserverLookup = CreateDwcA.readNameServer(baseDir)
        def recommendedKeys = nameserverLookup.values().toSet()

        //required column headers - taxonID, datasetID, acceptedNameUsageID, parentNameUsageID, taxonomicStatus, taxonRank, scientificName, scientificNameAuthorship

        //iterate over ORGANISM MASTER
        // output taxonConceptID=ORGANISM_KEY, taxonID=TAXON_VERSION_KEY, parentNameUsageID=PARENT_TVK
        // retrieve scientificName=ITEM_NAME, scientificNameAuthorship=AUTHORITY  from TAXON.csv
        // retrieve taxonRank=TAXON_RANK_KEY, datasetID=TAXON_LIST_VERSION_KEY    from TAXON_LIST_ITEM.csv

        // output MARINE_FLAG,TERRESTRIAL_FRESHWATER_FLAG,REDUNDANT_FLAG,NON_NATIVE_FLAG -> speciesProfile.txt
        // ignore if VERNACULAR="Y", ONLY_IN_NOT_FIT_FOR_WEB="Y", REDUNDANT_FLAG="Y"
        def orgMasterReader = new CSVReader(new FileReader(baseDir + "ORGANISM_MASTER.csv"))

        def taxaWriter = new CSVWriter(new FileWriter(new File("data/uk/dwca/taxa.csv")))
        taxaWriter.writeNext(["taxonID", "parentNameUsageID", "acceptedNameUsageID", "datasetID", "scientificName", "scientificNameAuthorship", "taxonRank", "taxonConceptID", "taxonomicStatus", "establishmentMeans", "taxonGroup"] as String[])

        def commonNameWriter = new CSVWriter(new FileWriter(new File("data/uk/dwca/vernacular.csv")))
        commonNameWriter.writeNext(["taxonID", "nameID", "datasetID", "vernacularName", "language", "status"] as String[])

        def speciesProfile = new CSVWriter(new FileWriter(new File("data/uk/dwca/speciesProfile.csv")))
        speciesProfile.writeNext(["taxonID", "habitat"] as String[])

        def headers = orgMasterReader.readNext()  //ignore header
        def line = null

        def taxonVersionKeys = [] as HashSet

        while((line = orgMasterReader.readNext()) != null){

            def terrestrial = line[8] == "Y"
            def marine = line[7] == "Y"
            def freshwater = line[9] == "Y"
            def establishmentMeans = line[11] == "Y" ? "introduced" : "native"
            def redundant = line[10]
            def deletedDate = line[19] //straight duplicate

            def ignore = deletedDate || redundant == "Y"

            if(!ignore){

                def taxonVersionKey = line[2]
                def taxonVersionRank = taxonVersionRankLookup.get(taxonVersionKey)
                if(!taxonVersionRank){
                    println("Unable to get rank for version key: " + taxonVersionKey)
                }


                def taxonKey = taxonVersionLookup.get(taxonVersionKey)
                def nameLookup = scientificNameLookup.get(taxonKey)

                taxonVersionKeys <<  taxonVersionKey

                if(nameLookup) {
                    def taxonConceptID = line[0]    //ORGANISM_KEY
                    def taxonID = line[2]           //TAXON_VERSION_KEY
                    def parentNameUsageID = organismTaxonVersionKeyMap.get(line[1]) //PARENT_TVK
                    def acceptedNameUsageID = ""    //blank if not a synonym
                    def datasetID = "" //taxonListVersionKey
                    def scientificName = nameLookup["scientificName"]
                    def scientificNameAuthorship = nameLookup["scientificNameAuthorship"]
                    def taxonRank = taxonVersionRank["taxonRank"]
                    def taxonomicStatus = "accepted"
                    def taxonGroup = taxonGroupMap.get(taxonID) // this needs to be changed to get the

                    // taxaWriter
                    String[] taxon = [taxonID, parentNameUsageID, acceptedNameUsageID, datasetID, scientificName, scientificNameAuthorship, taxonRank, taxonConceptID, taxonomicStatus, establishmentMeans, taxonGroup]
                    taxaWriter.writeNext(taxon)

                    if(marine){
                        speciesProfile.writeNext([taxonID, "marine"] as String[])
                    }

                    if(terrestrial){
                        speciesProfile.writeNext([taxonID, "terrestrial"] as String[])
                    }

                    if(freshwater){
                        speciesProfile.writeNext([taxonID, "freshwater"] as String[])
                    }

                } else {
                    println("name lookup fails for " + taxonVersionKey)
                }
            }
        }
        orgMasterReader.close()

        //get the synonyms
        def csvReader = new CSVReader(new FileReader(baseDir + "NAMESERVER.csv"))
        csvReader.readNext()  //ignore header

        def nsline

        while((nsline = csvReader.readNext()) != null){

            def deletedDate = nsline[10]
            //def redundant = nsline[11]

            if(!deletedDate) {

                def taxonVersionKey = nsline[1]      //the INPUT TAXON VERSION KEY
                def taxonVersionRank = taxonVersionRankLookup.get(taxonVersionKey)
                def taxonKey = taxonVersionLookup.get(taxonVersionKey)
                def nameLookup = scientificNameLookup.get(taxonKey)
                def acceptedNameUsageID = nsline[5]  //RECOMMENDED_KEY

                def taxonID = taxonVersionKey        //TAXON_VERSION_KEY
                def taxonConceptID = ""
                def parentNameUsageID = ""
                def datasetID = ""
                def scientificName = nameLookup["scientificName"]
                def scientificNameAuthorship = nameLookup["scientificNameAuthorship"]
                def taxonRank = taxonVersionRank["taxonRank"]
                def taxonomicStatus = "synonym"
                def language = nameLookup["lang"]

                def isRecommended = nsline[3] == "R"   //is recommended
                def isWellformed = nsline[2] == "W" //is well formed

                //get the well formed-ness, recommended-ness of the name
                if (taxonID != acceptedNameUsageID && !nameLookup["isVernacular"]) {
                    String[] taxon = [taxonID, parentNameUsageID, acceptedNameUsageID, datasetID, scientificName, scientificNameAuthorship, taxonRank, taxonConceptID, taxonomicStatus]
                    taxaWriter.writeNext(taxon)
                } else if (taxonID != acceptedNameUsageID && nameLookup["isVernacular"]) {
                    //set a priority based on language
                    def status = "standard"
                    if (language == "en") {
                        if (isRecommended && isWellformed) {
                            status = "preferred"
                        } else if (isRecommended) {
                            status = "recommended"
                        } else if (isWellformed) {
                            status = "well formed"
                        } else {
                            status = "not well formed"
                        }
                    } else {
                        status = "local"
                    }
                    String[] common = [acceptedNameUsageID, taxonID, datasetID, scientificName, language, status]
                    commonNameWriter.writeNext(common)
                }
            }
        }

        def datasetWriter = new CSVWriter(new FileWriter(new File("data/uk/dwca/dataset.csv")))
        datasetWriter.writeNext(["datasetID", "name", "dataProviderID", "dataProvider", "description"] as String[])

        //output attribution
//        taxonVersionKeys.each {
//            //get the dataset details
//            def taxonListKey = versionListMap.get(it)
//            def listDetails = taxonListMap.get(taxonListKey)
//            String[] dataset = [it, listDetails['list'], "", listDetails['authority'], listDetails['description']]
//            datasetWriter.writeNext(dataset)
//        }

        datasetWriter.flush()
        datasetWriter.close()

        taxaWriter.flush()
        taxaWriter.close()

        commonNameWriter.flush()
        commonNameWriter.close()

        speciesProfile.flush()
        speciesProfile.close()

        println "Archive created."
    }

    static def taxonVersionLookup(baseDir){

        def csvReader = new CSVReader(new FileReader(baseDir + "TAXON_VERSION.csv"))
        def headers = csvReader.readNext()  //ignore header
        def taxonVersionLookup = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonVersionKey = line[0]
            def taxonKey = line[1]
            taxonVersionLookup.put(taxonVersionKey, taxonKey)
        }
        csvReader.close()
        taxonVersionLookup
    }

    /**
     * Returns a taxonVersionKey -> name, authorship map
     * @return
     */
    static def readScientificNames(baseDir){

        def csvReader = new CSVReader(new FileReader(baseDir + "TAXON.csv"))
        def headers = csvReader.readNext()  //ignore header
        def scientificNames = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonKey = line[0]
            def normalisedKey = line[1]
            def name = line[2]
            def author = line[3]
            def nameTypeKey = line[8]
            def language = line[7]
            def wellformed = line[7]

            scientificNames.put(taxonKey, [normalisedKey: normalisedKey, scientificName: name, scientificNameAuthorship: author, isVernacular: nameTypeKey == "NBNSYS0000000002", lang:language, wellformed: wellformed])
        }
        csvReader.close()
        scientificNames
    }

    /**
     * Returns a taxonVersionKey -> name, authorship map
     * @return
     */
    static def readNameServer(baseDir){

        def csvReader = new CSVReader(new FileReader(baseDir + "NAMESERVER.csv"))
        csvReader.readNext()  //ignore header
        def recommendedKeyLookup = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonKey = line[1]
            def recommendedKey = line[5]
            recommendedKeyLookup.put(taxonKey, recommendedKey)
        }
        csvReader.close()
        recommendedKeyLookup
    }


    static def taxonVersionRankLookup(baseDir){

        //get the taxon ranks
        def taxonRankLookup = {
            def csvReader = new CSVReader(new FileReader(baseDir + "TAXON_RANK.csv"))
            def headers = csvReader.readNext() //ignore headers
            def line = ""
            def rankLookup = [:]
            while((line =  csvReader.readNext()) != null){
                rankLookup.put(line[0], line[3])
            }
            rankLookup
        }.call()

        println(taxonRankLookup)

        println("Loading taxon list")
        def csvReader = new CSVReader(new FileReader(baseDir + "TAXON_VERSION.csv"))
        def headers = csvReader.readNext()  //ignore header

        def line = null
        def taxonVersionKeyRankLookup = [:]

        while((line =  csvReader.readNext()) != null){
            def taxonVersionKey = line[0]
            def taxonRankKey = line[12]
            taxonVersionKeyRankLookup.put(taxonVersionKey, [taxonVersionKey: taxonVersionKey, taxonRank: taxonRankLookup.get(taxonRankKey)])
        }

        println("List items: " + taxonVersionKeyRankLookup.size())
        taxonVersionKeyRankLookup
    }
}