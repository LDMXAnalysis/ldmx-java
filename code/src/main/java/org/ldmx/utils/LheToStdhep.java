package org.ldmx.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

import hep.io.stdhep.StdhepEvent;
import hep.io.stdhep.StdhepWriter;
import hep.physics.vec.BasicHep3Vector;
import hep.physics.vec.Hep3Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.DefaultParser; 

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

/**
 * A class to convert lhe events to Stdhep.
 * 
 * @author <a href="mailto:omoreno@slac.stanford.edu">Omar Moreno</a> 
 */
public class LheToStdhep {
    
    private static final int N_PARTICLE_INDEX = 0;
    private static final int EVENT_NUMBER_INDEX = 1;
    
    private static final int PDG_ID_INDEX = 1;
    private static final int STATUS_INDEX = 2; 
    private static final int FIRST_MOTHER_INDEX = 3; 
    private static final int SECOND_MOTHER_INDEX = 4; 
    private static final int FIRST_DAUGHTER_INDEX = 5; 
    private static final int SECOND_DAUGHTER_INDEX = 6;
    
    private static double targetThickness = 0.35; // mm
    private static double targetDeltaX = 10.0; // mm
    private static double targetDeltaY = 20.0; // mm
    private static double targetZPosition  = 0.00; // mm 
    
    private static boolean verbose = false;
    
    public static void main(String[] args) throws IOException {

        
        List<String> lheGzFiles = new ArrayList<String>(); 
        String stdhepFileName = "output.stdhep";

        CommandLineParser parser = new DefaultParser();

        // Create the Options
        // TODO: Add ability to parse list of files.
        Options options = new Options(); 
        options.addOption("i", "input",   true, "Path to lhe.gz file to process.");
        options.addOption("l", "list",    true, "Text file containing a list of lhe.gz files to process.");
        options.addOption("v", "verbose", true, "Turn on verbose mode.");
       
        try {
           
            // Parse the command line arguments
            CommandLine line = parser.parse(options, args);
            
            // If the file is not specified, notify the user and exit the 
            // application.
            if (!line.hasOption("i") && !line.hasOption("l")) {
                System.out.println("[ lheToStdhep ] : Please specify an lhe.gz file or a list of files to process.");
                System.exit(0);
            } else if (line.hasOption("i") && line.hasOption("l")) { 
                System.out.println("[ lheToStdhep ] : Cannot specify both an individual input file and a list of files.");
            }
          
            if (line.hasOption("v")) verbose = true;
            
            // Get the name of the input file
            if (line.hasOption("i")) {
                lheGzFiles.add(line.getOptionValue("i"));
            } else { 
                BufferedReader reader = new BufferedReader(new FileReader(line.getOptionValue("l"))); 
                String filePath = null;
                while((filePath = reader.readLine()) != null) {
                    lheGzFiles.add(filePath);
                }
                reader.close();
            }
        } catch(ParseException e){
            System.out.println("Unable to parse command line arguments: " + e.getMessage());
        }
       
        for (String lheGzFile : lheGzFiles) { 
            
            System.out.println("[ lheToStdhep ]: Processing " + lheGzFile);
            
            // Build the output file name
            stdhepFileName = lheGzFile.substring(lheGzFile.lastIndexOf("/") + 1, lheGzFile.indexOf(".lhe.gz"));
            stdhepFileName += ".stdhep";
            
            // Ungzip the file 
            GZIPInputStream lheGzStream = new GZIPInputStream(new FileInputStream(lheGzFile));
            
            // Get all of the lhe events 
            List<Element> events = getLheEvents(lheGzStream);
            System.out.println("[ lheToStdhep ] : A total of " + events.size() + " will be processed.");
            System.out.println("[ lheToStdhep ] : Events will be written to file: " + stdhepFileName);
            convertToStdHep(events, stdhepFileName);
        }
        
        
        
    }

    /**
     * 
     */
    static private void convertToStdHep(List<Element> events, String stdhepFileName) throws IOException{
       
        StdhepWriter writer = new StdhepWriter(stdhepFileName, "Import Stdhep Events", "Imported from LHE generated from MadGraph", events.size());
        writer.setCompatibilityMode(false);
        
        for(Element event : events){
            writeEvent(event, writer);
        }
        writer.close();
    }

    /**
     * 
     */
    private static List<Element> getLheEvents(GZIPInputStream lheGzStream) {
        
        // Instantiate the SAX parser used to build the JDOM document
        SAXBuilder builder = new SAXBuilder(); 
        
        // Parse the lhe file and build the JDOM document
        Document document = null;
        List<Element> eventNodes = null; 
        try {
            
            document = (Document) builder.build(lheGzStream);
            
            // Get the root node
            Element rootNode = document.getRootElement(); 
            
            // Get a list of all nodes of type event
            eventNodes = rootNode.getChildren("event");
        
        } catch (JDOMException e) {
            e.printStackTrace();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return eventNodes; 
    }
   
    /** 
     * 
     */
    private static Hep3Vector getVertexPosition() { 
       
       double x = ThreadLocalRandom.current().nextDouble(-targetDeltaX, targetDeltaX);
       double y = ThreadLocalRandom.current().nextDouble(-targetDeltaY, targetDeltaY);
       double z = ThreadLocalRandom.current().nextDouble(
               targetZPosition - targetThickness/2, 
               targetZPosition + targetThickness/2);
        
       return new BasicHep3Vector(x, y, z); 
    }
    
    private static void printMessage(String message) { 
        if (verbose) System.out.println("[ lheToStdhep ] : " + message); 
    }

    /**
     * 
     */
    private static void writeEvent(Element event, StdhepWriter writer) throws IOException{
        
        int eventNumber = 0;
        int numberOfParticles = 0; 
        int particleIndex = 0;
        int pdgID[] = null; 
        int particleStatus[] = null;
        int motherParticles[] = null; 
        int daughterParticles[] = null; 
        double particleMomentum[] = null;
        double particleVertex[] = null; 
        
        // Get the text within the event element node.  An element node contains
        // information describing the event and it's particles.  The PDG ID of
        // a particle along with it's kinematics are listed on it's own line.
        // In order to parse the information for each particle, the text is 
        // split using the newline character as a delimiter.  
        String[] eventData = event.getTextTrim().split("\n");   
     
        // Get the vertex position that will be used for this particle.
        Hep3Vector vertexPosition = getVertexPosition();
        
        for (int datumIndex = 0; datumIndex < eventData.length; datumIndex++) {
            
            // Split a line by whitespace
            String[] eventTokens = eventData[datumIndex].split("\\s+");
        
            if(datumIndex == 0) {
                
                eventNumber = Integer.valueOf(eventTokens[EVENT_NUMBER_INDEX]);
                printMessage("#================================================#\n#");
                printMessage("# Event: " + eventNumber);
                
                numberOfParticles = Integer.valueOf(eventTokens[N_PARTICLE_INDEX]);
                printMessage("# Number of particles: " + numberOfParticles + "\n#");
                printMessage("#================================================#");
        
                // Reset all arrays used to build the Stdhep event
                particleIndex = 0; 
                particleStatus = new int[numberOfParticles];
                pdgID = new int[numberOfParticles];
                motherParticles = new int[numberOfParticles*2];
                daughterParticles = new int[numberOfParticles*2];
                particleMomentum = new double[numberOfParticles*5];
                particleVertex = new double[numberOfParticles*4];
            
                continue;
            }
    
            // Get the PDG ID of the particle
            pdgID[particleIndex] = Integer.valueOf(eventTokens[PDG_ID_INDEX]);
            
            
            printMessage(">>> PDG ID: " + pdgID[particleIndex]);
            
            // Get the status of the particle (initial state = -1, final state = 1, resonance = 2)
            particleStatus[particleIndex] = Integer.valueOf(eventTokens[STATUS_INDEX]);
            if(particleStatus[particleIndex] == -1) particleStatus[particleIndex] = 3; 
            printMessage(">>>> Particle Status: " + particleStatus[particleIndex]);
            
            motherParticles[particleIndex*2] = Integer.valueOf(eventTokens[FIRST_MOTHER_INDEX]);
            motherParticles[particleIndex*2 + 1] = Integer.valueOf(eventTokens[SECOND_MOTHER_INDEX]);
            printMessage(">>>> Mothers: 1) " + motherParticles[particleIndex*2] + " 2) " + motherParticles[particleIndex*2 + 1]);
            
            // Get the daughter particles
            daughterParticles[particleIndex*2] = Integer.valueOf(eventTokens[FIRST_DAUGHTER_INDEX]);
            daughterParticles[particleIndex*2 + 1] = Integer.valueOf(eventTokens[SECOND_DAUGHTER_INDEX]);
            if (daughterParticles[particleIndex*2] != 0 || daughterParticles[particleIndex*2 + 1] != 0) throw new RuntimeException("wtf?");
            printMessage(">>>> Daughter: 1) " + daughterParticles[particleIndex*2] + " 2) " + daughterParticles[particleIndex*2 + 1]);
            
            // Get the kinematics of the particle
            particleMomentum[particleIndex*5] = Double.valueOf(eventTokens[7]);     // px
            particleMomentum[particleIndex*5 + 1] = Double.valueOf(eventTokens[8]); // py   
            particleMomentum[particleIndex*5 + 2] = Double.valueOf(eventTokens[9]); // pz
            particleMomentum[particleIndex*5 + 3] = Double.valueOf(eventTokens[10]); // Particle Energy
            particleMomentum[particleIndex*5 + 4] = Double.valueOf(eventTokens[11]); // Particle Mass
            printMessage(">>>> px: " + particleMomentum[particleIndex*5] 
                    + " py: " + particleMomentum[particleIndex*5 + 1]
                    + " pz: " + particleMomentum[particleIndex*5 + 2]
                    + " energy: " + particleMomentum[particleIndex*5 + 3]
                    + " mass: " + particleMomentum[particleIndex*5 + 4]
            );
            
            particleVertex[particleIndex*4] = vertexPosition.x();
            particleVertex[particleIndex*4+1] = vertexPosition.y();
            particleVertex[particleIndex*4+2] = vertexPosition.z();
            particleVertex[particleIndex*4+3] = 0; 
           
            printMessage(">>>> vertex: " + vertexPosition.toString());
            
            // Increment the particle number
            particleIndex++;
            
            printMessage(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        }
        
        // Create the Stdhep event and write it 
        StdhepEvent stdhepEvent = new StdhepEvent(eventNumber, numberOfParticles, particleStatus, 
                pdgID, motherParticles, daughterParticles, particleMomentum, particleVertex);
        writer.writeRecord(stdhepEvent);
    }
}
