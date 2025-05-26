/* 
 * Copyright (C) 2015-2017 The Language Archive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.mpi.tla.flat.deposit.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.XdmNode;
import nl.mpi.tla.flat.deposit.Context;
import nl.mpi.tla.flat.deposit.DepositException;
import nl.mpi.tla.flat.deposit.sip.Resource;
import nl.mpi.tla.flat.deposit.sip.SIPInterface;
import nl.mpi.tla.util.Saxon;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoResponse;
import org.fcrepo.client.GetBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pavi
 */

public class DrupalSync extends FedoraAction {
    
    private static final Logger logger = LoggerFactory.getLogger(DrupalSync.class.getName());

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

    /**
     *
     * @param filetype
     * @param islandora_model
     * @param mediatype
     * @param media_use
     * @param media_bundle_target_id
     * @param relation
     * @return
     */
    public record CsvRecord(String filetype, String islandora_model, String mediatype, String media_use, String media_bundle_target_id, String relation) {
    }

    @Override
    public boolean perform(Context context) throws DepositException {
        SIPInterface sip;
        try {
            
            connect(context);
            
            //if (!hasParameter("prefix"))
            //    throw new DepositException("Handle prefix has not been specified!");
            
                    
            sip = context.getSIP();
            XdmNode info= fcrepo(sip.getFID());
            
            String title = Saxon.xpath2string(info, "normalize-space(//dc:title)",null,NAMESPACES);
            String mimetype = null ;

            System.out.println("XdmNode: "+info.toString());
            String bundlename = title;
            System.out.println("Bundlename:"+ bundlename);
            // until here
            String type = "unknown";
            String field_islandora_model = "unknown";
            String mediatype = "unknown";
            String field_media_use = "Unknown";
            String relation = "unknown";
            String searchString = "";
            
            File mimefile;

            String fileparam = getParameter("path", null);
            if (!fileparam.isEmpty()) {
                mimefile = new File (fileparam);
            } else {
                throw new DepositException("Mime-mapping.csv file not found!!");
            }

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
            
            if(sip.isInsert()){
                //everything needs to be bundle and the indivitual files into drupal
                // store fid for updating.
                // loop over resources for insert res.isInsert()
                //perform the written code of node, file & media
                
                URI pid = (sip.hasPID()?sip.getPID():null);
                sip.setPID(new URI(getOverwriteProperties(context).getProperty("sip.PID","hdl:"+getParameter("prefix")+"/"+UUID.randomUUID())));
                if (pid==null) {
                    logger.info("Assigned new PID["+sip.getPID()+"] to the SIP");
                } else {
                    logger.info("Assigned new PID["+sip.getPID()+"] to the SIP to update AIP["+pid+"]");
                }
                
                //node
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
                                        + " \"field_pid\": ["
                                        + "   {"
                                        + "       \"value\": \""+ sip.getPID() + "\""
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
                
                String fileid = "";
                
                for (Resource res:sip.getResources()) {
                    if (res.isInsert() || res.isUpdate()) {
                        if (res.isInsert() && res.hasPID()) {
                            //file
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
                                        + " \"field_pid\": ["
                                        + "   {"
                                        + "       \"value\": \""+ res.getPID() + "\""
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

                        fileid = callHttpResponse(jsonFileString, endpointFile, searchString);

                            
                        }
                    }
                }
                
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
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMediaString))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:admin10"
                                + "").getBytes()))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();

                HttpResponse responseMedia = httpClientMedia.send(requestMedia, HttpResponse.BodyHandlers.ofString());
                System.out.print(responseMedia.body());

                if (responseMedia.statusCode()==200 || responseMedia.statusCode()==201 ) {
                    System.out.println("Goed gedaan");
                }
                else
                    throw new Exception("Unexpected status["+responseMedia.statusCode()+"] while querying! Error msg:");
                
            }
            else if (sip.isUpdate()){
                //fetch the node id from Drupal which matches the fid from Fedora. we also have to store fid when inserting
                
            }
            
            
        } catch (Exception ex) {
            throw new DepositException("Couldn't assign PIDs!",ex);
        }   
        return true;
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
                    .POST(HttpRequest.BodyPublishers.ofString(jsonNodeString))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:admin10"
                            + "").getBytes()))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            HttpResponse response = httpClientNode.send(requestNode, HttpResponse.BodyHandlers.ofString());
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
