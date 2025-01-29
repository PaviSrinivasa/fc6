package nl.mpi.tla.flat.deposit.fc6;
import java.io.BufferedReader;
import org.fcrepo.client.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import nl.mpi.tla.util.Saxon;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import org.json.JSONObject;

public class Fc6 {
    
    protected static FcrepoClient fedoraClient = FcrepoClient.client().build();
    //protected static FcrepoClient fedoraClient = FcrepoClient.client().credentials("fedoraAdmin", "password").build();
	
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
	
	public static void main(String args[]) throws Exception {
		
		XdmNode info = fcrepo(new URI("lat:12345_comic_1_pdf/OBJ/fcr:metadata"));
                //System.out.print("Info: "+ info.toString());
		String title = Saxon.xpath2string(info, "normalize-space(//ebucore:filename)",null,NAMESPACES);
                String mimetype = Saxon.xpath2string(info, "normalize-space(//ebucore:hasMimeType)",null,NAMESPACES);
                String type = "unknown";
            //    switch(mimetype) { 
            //        case "application/pdf": 
            //            type = "document";
            //            break;
            //    }
                
                File mimefile = new File ("./src/main/java/nl/mpi/tla/flat/deposit/fc6/mime-mapping.tsv");
                Map<String,String> mmap = tsvToMap(mimefile);
                
                for (Map.Entry<String,String> entry : mmap.entrySet()){
                    if (entry.getKey().equalsIgnoreCase(mimetype)){
                        type = entry.getValue();
                    }
                }
                
                //ArrayList<String []> filetypes = tsvr(mimefile);
                //filetypes.forEach(array -> System.out.println(Arrays.toString(array)));
		//System.out.print(title);
                
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
                    
                    System.out.print(jsonDataString);
                    
                    JSONObject js = new JSONObject();
                   // js.put("type","\"target_id\":\"document\"");
                    //js.put("filename","\"value\":\"Green%20Hornet%20001.pdf\"");
                    //js.put("uri","\"value\":\"fedora://lat:12345_comic_1_pdf\"");
                    
                    
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

    private static Map<String, String> tsvToMap(File mimefile) {
        
        Map<String,String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(mimefile))){
            String line;
            while ((line = br.readLine()) != null){
                String[] parts = line.split("\t");
                
                if(parts.length >= 2){
                    String key = parts[0];
                    String value = parts[1];
                    
                    map.put(key,value);
                }
            }
            
            for (Map.Entry<String,String> entry : map.entrySet()){
                System.out.println("Key: "+ entry.getKey()+ ", value: "+ entry.getValue());
                }
            
            }
        catch(IOException e){
            System.out.println("Something went wrong");
        }
        
        return map;
}
}
                
          