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

import org.json.JSONObject;

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
		
            XdmNode info = fcrepo(new URI("lat:12345_comic_1_pdf/OBJ/fcr:metadata"));
            //System.out.print("Info: "+ info.toString());
            String title = Saxon.xpath2string(info, "normalize-space(//ebucore:filename)",null,NAMESPACES);
            String mimetype = Saxon.xpath2string(info, "normalize-space(//ebucore:hasMimeType)",null,NAMESPACES);
            String type = "unknown";

            File mimefile = new File ("./src/main/java/nl/mpi/tla/flat/deposit/fc6/mime-mapping.csv");

            Map<String,List<CsvRecord>> mmap = csvToMap(mimefile);

            List<CsvRecord> matchingMimetype = mmap.getOrDefault(mimetype, Collections.emptyList()); 
            System.out.println("matchingMimetype----------> "+matchingMimetype);
            for( CsvRecord csvR : matchingMimetype){
                type = csvR.filetype();
                System.out.println("type--> "+type);
            }

            try {
                String jsonDataString = "{"
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
                                        + "       \"value\": \"fedora://test123/testfile\" "
                                        + "   }"
                                        + " ]"
                                        + "}";                    
            JSONObject js = new JSONObject();
            System.out.print(js.toString());

            String endpoint = "https://islandora.traefik.me/entity/file?_format=json";

            HttpClient httpClient = HttpClient.newBuilder().build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("content-type", "application/json")
                    .POST(BodyPublishers.ofString(jsonDataString))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:admin10"
                            + "").getBytes()))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpResponse response = httpClient.send(request, BodyHandlers.ofString());

            if (response.statusCode()==200 || response.statusCode()==201 ) {
                System.out.print(response.headers());
                System.out.print(response.body());
            }
            else
                throw new Exception("Unexpected status["+response.statusCode()+"] while querying! Error msg:");
        }
        catch (Exception e) {
            System.out.print("Error msg:"+e.getMessage());
         throw e;   
        }
    }
                

    public static XdmNode fcrepo(URI fid) throws Exception {
       XdmNode res = null;
        URI uri = null;
        try {
            uri = new URI("http://islandora.traefik.me:8081/fcrepo/rest"+"/"+fid.toString());
        } catch (Exception e) {
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
}
                
          