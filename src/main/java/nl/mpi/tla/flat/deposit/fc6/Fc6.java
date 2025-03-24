package nl.mpi.tla.flat.deposit.fc6;
import java.io.BufferedReader;
import org.fcrepo.client.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;

import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.XdmNode;
import nl.mpi.tla.util.Saxon;

import com.fasterxml.jackson.databind.*;
import java.net.URISyntaxException;

public class Fc6 {
    
        protected static FcrepoClient fedoraClient = FcrepoClient.client().build();
	
	final static public Map<String,String> NAMESPACES = new LinkedHashMap<>();
	    
	    static {
	        NAMESPACES.put("cmd", "http://www.clarin.eu/cmd/");
	        NAMESPACES.put("dc", "http://purl.org/dc/elements/1.1/");
	        NAMESPACES.put("fedora", "http://fedora.info/definitions/v4/repository#");
	        NAMESPACES.put("fits", "http://hul.harvard.edu/ois/xml/ns/fits/fits_output");
	        NAMESPACES.put("flat", "java:nl.mpi.tla.flat");
	        NAMESPACES.put("foxml", "info:fedora/fedora-system:def/foxml#");
	        NAMESPACES.put("lat", "http://lat.mpi.nl/");
	        NAMESPACES.put("ldp", "http://www.w3.org/ns/ldp#");
	        NAMESPACES.put("model", "info:fedora/fedora-system:def/model#");
	        NAMESPACES.put("oai", "http://www.openarchives.org/OAI/2.0/");
	        NAMESPACES.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
	        NAMESPACES.put("relsext", "info:fedora/fedora-system:def/relations-external#");
	        NAMESPACES.put("onto-relsext", "http://islandora.ca/ontology/relsext#");
	        NAMESPACES.put("sx", "java:nl.mpi.tla.saxon");
	        NAMESPACES.put("srx", "http://www.w3.org/2005/sparql-results#");
	        NAMESPACES.put("view", "info:fedora/fedora-system:def/view#");        
	        NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
	        NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
	        NAMESPACES.put("ldp", "http://www.w3.org/ns/ldp#");
                NAMESPACES.put("ebucore", "http://www.ebu.ch/metadata/ontologies/ebucore/ebucore#");
	    };

        public record CsvRecord(String filetype, String islandora_model, String mediatype, String media_use, String media_bundle_target_id, String relation) {}	

	public static void main(String args[]) throws Exception {
            XdmNode info;
            try {
                 info = fcrepo(new URI("lat_12345_comic_1_pdf/OBJ/fcr:metadata"));
            }
            catch (Exception e) {
                throw new Exception("Unexpected status while querying! Error msg:"+e.getMessage());
            }
            if (!info.isEmpty()) {
                String title = Saxon.xpath2string(info, "normalize-space(//ebucore:filename)",null,NAMESPACES);
                String mimetype = Saxon.xpath2string(info, "normalize-space(//ebucore:hasMimeType)",null,NAMESPACES);

                //Verify the below lines
                XdmNode nodeInfo = fcrepo(new URI("lat_12345_comic_1"));
                //XdmNode nodeInfo = fcrepo(new URI ("info:fedora/fedora-system:def/model#label"));
                System.out.println("XdmNode: "+nodeInfo.toString());
                String bundlename = Saxon.xpath2string(nodeInfo, "normalize-space(//dc:title)",null,NAMESPACES);
                System.out.println("Bundlename:"+ bundlename);
                // until here
                String type = "unknown";
                String field_islandora_model = "unknown";
                String mediatype = "unknown";
                String field_media_use = "Unknown";
                String relation = "unknown";
                String searchString = " ";

                File mimefile = new File ("./src/main/java/nl/mpi/tla/flat/deposit/fc6/mime-mapping.csv");

                Map<String,List<CsvRecord>> mmap = csvToMap(mimefile);

                List<CsvRecord> matchingMimetype = mmap.getOrDefault(mimetype, Collections.emptyList()); 
                System.out.println("matchingMimetype----------> "+matchingMimetype);
                for( CsvRecord csvR : matchingMimetype){
                    type = csvR.filetype();
                    System.out.println("type--> "+type);
                    field_islandora_model = csvR.islandora_model();
                    System.out.println("field_islandora_model---->"+field_islandora_model);
                    mediatype = csvR.mediatype();
                    System.out.println("mediatype---->"+mediatype);
                    field_media_use = csvR.media_use();
                    System.out.println("field_media_use---->"+field_media_use);
                    relation = csvR.relation();
                    System.out.println("relation---->"+relation);
                }


                //node enitity
                String jsonNodeString = "{"
                                        + " \"type\": ["
                                        + "   {"
                                        + "      \"target_id\":\"" + "islandora_object" + "\","
                                        + "      \"target_type\":\"" + "node_type" + "\""
                                        + "   } "
                                        + " ],"
                                        + " \"title\": ["
                                        + "   {"
                                        + "       \"value\":\"" + bundlename + "\""
                                        + "   }"
                                        + " ],"
                                        + " \"field_model\": ["
                                        + "   {"
                                        + "       \"target_id\": \""+ field_islandora_model +"\","
                                        + "       \"target_type\": \""+ "taxonomy_term" +"\""
                                        + "   }"
                                        + " ],"
                                        + " \"uid\": ["
                                        + "   {"
                                        + "       \"target_id\": 1 "
                                        + "   }"
                                        + " ]"
                                        + "}";    

                String endpointNode = "https://islandora.dev/node?_format=json";
                searchString = "nid";

                String nodeid = callHttpResponse(jsonNodeString, endpointNode, searchString);

                //file entity

                String jsonFileString = "{"
                                        + " \"type\": ["
                                        + "   {"
                                        + "      \"target_id\":\"" + type + "\""
                                        + "   } "
                                        + " ],"
                                        + " \"filename\": ["
                                        + "   {"
                                        + "       \"value\":\"" + title + "\""
                                        + "   }"
                                        + " ],"
                                        + " \"uri\": ["
                                        + "   {"
                                        + "       \"value\": \"fedora://lat_12345_comic_1_pdf/OBJ\" "
                                        + "   }"
                                        + " ],"
                                        + " \"uid\": ["
                                        + "   {"
                                        + "       \"target_id\": 1 "
                                        + "   }"
                                        + " ]"
                                        + "}";  

                String endpointFile = "https://islandora.dev/entity/file?_format=json";
                searchString = "fid";

                String fileid = callHttpResponse(jsonFileString, endpointFile, searchString);

                //media entity   
                String jsonMediaString = "{"
                                        + " \"bundle\": ["
                                        + "   {"
                                        + "      \"target_id\":\"" + mediatype + "\","
                                        + "      \"target_type\":\"" + "media_type" + "\""
                                        + "   } "
                                        + " ],"
                                        + " \"name\": ["
                                        + "   {"
                                        + "       \"value\":\"" + title + "\""
                                        + "   }"
                                        + " ],"
                                        + " \"field_media_use\": ["
                                        + "   {"
                                        + "       \"target_id\": \""+ field_media_use +"\","
                                        + "       \"target_type\": \""+ "taxonomy_term" +"\""
                                        + "   }"
                                        + " ],"
                                        + " \""+ relation+ "\": ["
                                        + "   {"
                                        + "       \"target_id\":\"" + fileid + "\""
                                        + "   }"
                                        + " ],"
                                        + " \"field_media_of\": ["
                                        + "   {"
                                        + "       \"target_id\": \""+ nodeid +"\""
                                        + "   }"
                                        + " ],"
                                        + " \"uid\": ["
                                        + "   {"
                                        + "       \"target_id\": 1 "
                                        + "   }"
                                        + " ]"
                                        + "}";    

                String endpointMedia = "https://islandora.dev/entity/media?_format=json";

                HttpClient httpClientMedia = HttpClient.newBuilder().build();
                HttpRequest requestMedia = HttpRequest.newBuilder()
                        .uri(URI.create(endpointMedia))
                        .header("content-type", "application/json")
                        .POST(BodyPublishers.ofString(jsonMediaString))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:admin10"
                                + "").getBytes()))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

                HttpResponse responseMedia = httpClientMedia.send(requestMedia, BodyHandlers.ofString());
                System.out.print(responseMedia.body());

                if (responseMedia.statusCode()==200 || responseMedia.statusCode()==201 ) {
                    System.out.println("Goed gedaan");
                }
                else
                    throw new Exception("Unexpected status["+responseMedia.statusCode()+"] while querying! Error msg:");
            }
            else {
                System.out.println("The XdmNode is null");
            }
        }
                

        public static XdmNode fcrepo(URI fid) throws Exception {
           XdmNode res = null;
            URI uri = null;
            try {
                uri = new URI("https://fcrepo.islandora.dev/fcrepo/rest"+"/"+fid.toString());
            } catch (URISyntaxException e) {
                throw new Exception(e);   
            }

            try (FcrepoResponse response = new GetBuilder(uri, fedoraClient)
                .accept("application/rdf+xml")
                .perform()) {
                    res = Saxon.buildDocument(new StreamSource(response.getBody()));
            } catch (Exception e) {
                 throw e;   
            }
            return res;
        }

        public static Map<String, List<CsvRecord>> csvToMap(File mimefile) {
            Map<String,List<CsvRecord>> map = new HashMap<>();
            try (BufferedReader br = new BufferedReader(new FileReader(mimefile))){
                String line;
                while ((line = br.readLine()) != null){
                    String[] parts = line.split(";");

                    if(parts.length >= 7){
                        String mimetype = parts[0].trim();
                        String filetype = parts[1].trim();
                        String islandora_model = parts[2].trim(); 
                        String mediatype = parts[3].trim();
                        String media_use = parts[4].trim();
                        String media_bundle_target_id = parts[5].trim();
                        String relation = parts[6].trim();

                        CsvRecord csvRecord = new CsvRecord(filetype, islandora_model, mediatype, media_use, media_bundle_target_id, relation);
                        map.putIfAbsent(mimetype, new ArrayList<>());
                        map.get(mimetype).add(csvRecord);
                    }
                }

                map.forEach((mimetype,items) -> {
                   System.out.println (mimetype + " -> " + items );
                });
            }
            catch(IOException e){
                System.out.println("Something went wrong");
            }
            return map;
        }
        
        private static String callHttpResponse(String jsonNodeString, String endpointNode, String searchString) throws Exception {
            String foundString = "0";
            try {
                HttpClient httpClientNode = HttpClient.newBuilder().build();
                HttpRequest requestNode = HttpRequest.newBuilder()
                        .uri(URI.create(endpointNode))
                        .header("content-type", "application/json")
                        .POST(BodyPublishers.ofString(jsonNodeString))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:admin10"
                                + "").getBytes()))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

                HttpResponse response = httpClientNode.send(requestNode, BodyHandlers.ofString());
                System.out.print(response.body());

                if (response.statusCode()==200 || response.statusCode()==201 ) {
                    String jsonResponse = response.body().toString();
                    ObjectMapper objMap = new ObjectMapper();
                    Map<String, Object> mapResponse = objMap.readValue(jsonResponse, Map.class);
                    System.out.println("mapNodeResponse: "+ mapResponse.toString());
                    if (mapResponse.containsKey(searchString)){
                        List<Map<String, Object>> mapfound = (List<Map<String, Object>>) mapResponse.get(searchString);
                        if (mapfound.get(0).containsKey("value")){
                            foundString = mapfound.get(0).get("value").toString();
                            System.out.println("foundString----> "+foundString);
                        }
                    }
                }
                else
                    throw new Exception("Unexpected status["+response.statusCode()+"] while querying! Error msg:");
            }
            catch (Exception e) {
                    System.out.print("Error msg:"+e.getMessage());
                    throw e;
            }
            return foundString;
        }
}
                
          