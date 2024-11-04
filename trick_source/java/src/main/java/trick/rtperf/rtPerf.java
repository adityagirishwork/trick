package trick.rtperf;

// Imports
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.Math;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.event.*;
// import javax.xml.ws.Action;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledEditorKit;

import trick.common.utils.VariableServerConnection;
import trick.common.TrickApplication;
import trick.common.ui.UIUtils;
import trick.common.ui.components.FontChooser;
import trick.common.ui.panels.AnimationPlayer;
import trick.common.ui.panels.FindBar;
import trick.simcontrol.SimControlApplication;
import trick.simcontrol.utils.SimControlActionController;
import trick.simcontrol.utils.SimState;

import org.jdesktop.application.Action;
import org.jdesktop.application.Application;
import org.jdesktop.application.Task;
import org.jdesktop.application.View;
import org.jdesktop.swingx.JXEditorPane;
import org.jdesktop.swingx.JXLabel;
import org.jdesktop.swingx.JXPanel;
import org.jdesktop.swingx.JXStatusBar;
import org.jdesktop.swingx.JXTitledPanel;
import org.jdesktop.swingx.JXTitledSeparator;

public class rtPerf extends TrickApplication implements PropertyChangeListener {
    
    //========================================
    //    Public data
    //========================================


    //========================================
    //    Protected data
    //========================================


    //========================================
    //    Private Data
    //========================================
    private int modeIndex = -1;
    private int debug_flag ;
    private int debug_present ;
    private int overrun_present ;
    private int message_present ;
    private int message_port ;
    
    /** whether automatically exit when sim is done/killed. */
    private static boolean isAutoExitOn;

    // The panel that displays the current sim state description as well as progress.
    private JXTitledPanel runtimeStatePanel;
    private String currentSimStatusDesc = "None";
    private JProgressBar progressBar;
    // Always enable progress bar unless the sim termination time is not defined
    private boolean enableProgressBar = true;

    private JTextField recTime;
    //private JTextField realtimeTime;
    //private JTextField metTime;
    //private JTextField gmtTime;
    private JTextField simRealtimeRatio;

    private JXTitledPanel simOverrunPanel;
    private JTextField[] simRunDirField;
    private JTextField[] overrunField;
    private int slaveCount;
    private double simStartTime;
    private double simStopTime;
    private double execTimeTicValue;

    private JToggleButton dataRecButton;
    private JToggleButton realtimeButton;
    private JToggleButton dumpChkpntASCIIButton;
    private JToggleButton loadChkpntButton;
    private JToggleButton liteButton;

    private JXEditorPane statusMsgPane;

    private JXLabel statusLabel;

    /*
     *  The action controller that performs actions for such as clicking button, selection a menu item and etc.
     */
    private SimControlActionController actionController;

    // The animation image player panel
    private AnimationPlayer logoImagePanel;

    // VariableServerConnection for sending/receiving Variable Server commands.
    private VariableServerConnection commandSimcom;
    // VariableServerConnection for receiving Sim state from Variable Server.
    private VariableServerConnection statusSimcom;

    // Socket for receiving health and status messages
    private SocketChannel healthStatusSocketChannel ;
   
    private JComboBox runningSimList;
    private static String host;
    private static int port = -1;
    private static boolean isRestartOptionOn;
    //True if an error was encountered during the attempt to connect to Variable Server during intialize()
    private boolean errOnInitConnect = false;
    //Time out when attempting to establish connection with Variable Server in milliseconds
    private int varServerTimeout = 5000;
    
    // The object of SimState that has Sim state data.
    private SimState simState;
    private String customizedCheckpointObjects;

    private static Charset charset;
    

    final private static String LOCALHOST = "localhost";

	final private Dimension FULL_SIZE = new Dimension(680, 640);
	final private Dimension LITE_SIZE = new Dimension(340, 360);


    @Action
    public void startRT() {
        launchTrickApplication("rtperf", "--host " + host + " --port " + port);
    }

    /**
     * Connects to the variable server if {@link VariableServerConnection} is able to be created successfully and
     * starts the communication server for sim health status messages.
     */
    @Action
    public void connect() {
        // get host and port for selected sim  	
        if (runningSimList != null && runningSimList.getSelectedItem() != null) {
        	String selectedStr = runningSimList.getSelectedItem().toString();
        	// remove the run info if it is shown
        	int leftPre = selectedStr.indexOf("(");
        	if (leftPre != -1) {
        		selectedStr = selectedStr.substring(0, leftPre);
        	}
        	// can be separated either by : or whitespace
        	String[] elements = selectedStr.split(":");
        	
        	if (elements == null || elements.length < 2) {
        		elements = selectedStr.split("\\s+");
        	}
        	
        	if (elements == null || elements.length < 2) {       
				String errMsg = "Can't connect! Please provide valid host name and port number separated by : or whitespace!";		
                printErrorMessage(errMsg);
        		return;
        	}
        	host = elements[0].trim();
        	try {
        	port = Integer.parseInt(elements[1].trim());
        	} catch (NumberFormatException nfe) {
				String errMsg = elements[1] + " is not a valid port number!";
        		printErrorMessage(errMsg);
        		return;
        	}
        }
        
        getInitializationPacket();
        
        if (commandSimcom == null) {
			String errMsg = "Sorry, can't connect. Please make sure the availability of both server and port!";
			printErrorMessage(errMsg);
            return;
        } else {            
            Object[] keys = actionMap.allKeys();
            // If there is a server connection established, enable all actions.
            for (int i = 0; i < keys.length; i++) {
                String theKey = (String)keys[i];
                getAction(theKey).setEnabled(true);
            }
        }

        scheduleGetSimState();

        startStatusMonitors();
    }

    /**
     * Helper method for setting style attribute.
     */
    private void setColorStyleAttr(Style st, Color foreground, Color background) {
        st.addAttribute(StyleConstants.Foreground, foreground);
        st.addAttribute(StyleConstants.Background, background);
        st.addAttribute(StyleConstants.Alignment, StyleConstants.ALIGN_LEFT);
    }

    /**
	 * Prints an error message to the status message pane. In the event there is an error with it, a JOptionPane will pop up.
	 * @param err
	 */
	protected void printErrorMessage(String err) {
		try {
			// Get the document attached to the Status Message Pane 
			Document doc = statusMsgPane.getDocument();

			// Set the font color to red and the background to black
			StyleContext sc = new StyleContext();               
			Style redStyle = sc.addStyle("Red", null);
			setColorStyleAttr(redStyle, Color.red, Color.black);

			// Add the error message to the bottom of the message pane
			doc.insertString(doc.getLength(), err + "\n", redStyle);

			// If Lite mode is engaged, or the window is small enough 
			// to obscure the message pane, create a popup for the error as well.
			if (liteButton.isSelected() || getMainFrame().getSize().height <= LITE_SIZE.height + 50) {
				JOptionPane.showMessageDialog(getMainFrame(), err, "Sim Control Panel Error", JOptionPane.ERROR_MESSAGE);
			}
		} catch (BadLocationException ble) {
			JOptionPane.showMessageDialog(getMainFrame(), 
										  "Status Message Pane had an issue when printing: " + err, 
										  "Status Message Pane Error", 
										  JOptionPane.ERROR_MESSAGE);
		} catch (NullPointerException npe) {
			System.err.println( "Sim Control Error at Initialization: \n" + err);
		}
	}

    /**
     * Creates the panel for displaying the sim health status messages.
     */
    private JPanel createStatusMsgPanel() {
        statusMsgPane = new JXEditorPane();
        statusMsgPane.setDocument(new DefaultStyledDocument());
        statusMsgPane.setEditorKit(new StyledEditorKit());    
        statusMsgPane.putClientProperty(JXEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        statusMsgPane.setBackground(Color.black);

        int curr_font_size = statusMsgPane.getFont().getSize() ;
        Font font = new Font("Monospaced", Font.PLAIN, curr_font_size);
        statusMsgPane.setFont(font);

        JPanel statusMsgPanel = UIUtils.createSearchableTitledPanel("Status Messages", statusMsgPane, new FindBar(statusMsgPane.getSearchable()));
        return statusMsgPanel;
    }

    /**
     * Helper method for starting monitors for sim status as well as health status.
     */
	private void startStatusMonitors() {
		MonitorSimStatusTask monitorSimStatusTask = new MonitorSimStatusTask(this);
        monitorSimStatusTask.addPropertyChangeListener(this);
        getContext().getTaskService().execute(monitorSimStatusTask);

        // For receiving hs messages.
        getContext().getTaskService().execute(new MonitorHealthStatusTask(this));
	}

    //========================================
    //    Set/Get methods
    //========================================
    /**
     * Gets the initialization packet from Variable Server if it is up.
     */
    public void getInitializationPacket() {    	
        String simRunDir = null;
        String[] results = null;      
        boolean masterslave_enabled;
        try {
			String errMsg = "Error: SimControlApplication:getInitializationPacket()";
            try {
            	if (host != null && port != -1) {
            		commandSimcom = new VariableServerConnection(host, port, varServerTimeout);
            	} else {
            		commandSimcom = null;
            	}
            } catch (UnknownHostException host_exception) {
                /** The IP address of the host could not be determined. */
                errMsg += "\n Unknown host \""+host+"\"";
                errMsg += "\n Please use a valid host name (e.g. localhost)";
                errOnInitConnect = true;   
		printErrorMessage(errMsg); 
            } catch (SocketTimeoutException ste) {
                /** Connection attempt timed out. */
                errMsg += "\n Connection Timeout \""+host+"\"";
                errMsg += "\n Please try a different host name (e.g. localhost)";
                errOnInitConnect = true;
                printErrorMessage(errMsg);           
            } catch (IOException ioe) {
                /** Port number is unavailable, or there is no connection, etc. */
                errMsg += "\n Invalid TCP/IP port number \""+port+"\"";
                errMsg += "\n Please check the server and enter a proper port number!";
                errMsg += "\n IOException ..." + ioe;
                errMsg += "\n If there is no connection, please make sure SIM is up running properly!";
                errOnInitConnect = true;
		printErrorMessage(errMsg);
            } 
            
            if (commandSimcom == null) {
            	(new RetrieveHostPortTask()).execute();
            	return;
            }
                       
            actionController.setVariableServerConnection(commandSimcom);

            simState = new SimState();

            commandSimcom.put("trick.var_exists(\"trick_master_slave.master.num_slaves\")");
            results = commandSimcom.get().split("\t");
            masterslave_enabled = results[1].equals("1");

            commandSimcom.put("trick.var_set_client_tag(\"SimControl\")\n");
            commandSimcom.put("trick.var_add(\"trick_sys.sched.sim_start\") \n" +
            		          "trick.var_add(\"trick_sys.sched.terminate_time\") \n" +
                              "trick.var_add(\"trick_sys.sched.time_tic_value\") \n" +
                              "trick.var_add(\"trick_cmd_args.cmd_args.default_dir\") \n" +
                              "trick.var_add(\"trick_cmd_args.cmd_args.cmdline_name\") \n" +
                              "trick.var_add(\"trick_cmd_args.cmd_args.input_file\") \n" +
                              "trick.var_add(\"trick_cmd_args.cmd_args.run_dir\") \n");
            
            if (masterslave_enabled) {
                commandSimcom.put("trick.var_add(\"trick_master_slave.master.num_slaves\") \n");
            }

            commandSimcom.put("trick.var_send() \n" +
                              "trick.var_clear() \n");

            results = commandSimcom.get().split("\t");
            if (results != null && results.length > 0) {
                execTimeTicValue = Double.parseDouble(results[3]);
                simStartTime = Double.parseDouble(results[1]);
                long terminateTime = Long.parseLong(results[2]);                
                if (terminateTime >= Long.MAX_VALUE - 1) {
                	enableProgressBar = false;
                }
                
                // need to minus the sim start time as it could be a number other than 0.0
                simStopTime = terminateTime/execTimeTicValue - simStartTime;
            }

            slaveCount = masterslave_enabled ? Integer.parseInt(results[8]) : 0;

            simRunDirField = new JTextField[slaveCount+1];
            overrunField = new JTextField[slaveCount+1];

            for (int i = 0; i < simRunDirField.length; i++) {
                if (i==0) {
                    simRunDirField[i] = new JTextField(results[4] + java.io.File.separator + results[5] + " " + results[6]);
                } else {
                    simRunDirField[i] = new JTextField();
                }
                overrunField[i] = new JTextField("    ");
                overrunField[i].setPreferredSize( new Dimension(60, overrunField[i].getHeight()) );
            }
            simRunDir = results[7];
            simRunDir = results[4] + java.io.File.separator + simRunDir;

            simState.setRunPath(simRunDir);

            // MODIFY ALL THE STUFF BELOW TO GET THE RELEVANT INFORMATION FOR THE USE CASE. This is also where I need to change the GUI display.
            
            for (int i = 1; i < simRunDirField.length; i++) {
            	/**
            	 * Commented out the following code as slaves is a vector and can't be accessed at this point.
                 * Uncomment the following code if we can in the future.
            	 */
                /*commandSimcom.put("trick.sim_services.var_add(\"master_slave.master.slaves[" + i + "].sim_path\") \n" +
                                 "trick.sim_services.var_add(\"master_slave.master.slaves[" + i + "].S_main_name\") \n ");
                                  "trick.sim_services.var_add(\"master_slave.master.slaves[" + i + "].run_input_file\") \n" +
                                  "trick.sim_serives.var_send( ) \n" +
                                  "trick.sim_services.var_clear( ) \n");
                results = commandSimcom.get().split("\t");
                simRunDirField[i].setText(results[1] + java.io.File.separator + results[2] + " " + results[2]);*/
            	simRunDirField[i].setText("Slave " + i);
            }
            
            commandSimcom.put("trick.var_exists(\"trick_instruments.debug_pause.debug_pause_flag\")\n") ;
            results = commandSimcom.get().split("\t");
            debug_present = Integer.parseInt(results[1]);

            commandSimcom.put("trick.var_exists(\"trick_real_time.rt_sync.total_overrun\")\n") ;
            results = commandSimcom.get().split("\t");
            overrun_present = Integer.parseInt(results[1]);

            commandSimcom.put("trick.var_exists(\"trick_message.mdevice.port\")\n") ;
            results = commandSimcom.get().split("\t");
            message_present = Integer.parseInt(results[1]);

            if ( message_present == 1 ) {
                commandSimcom.put("trick.var_add(\"trick_message.mdevice.port\") \n" +
                                  "trick.var_send() \n" +
                                  "trick.var_clear() \n");
                results = commandSimcom.get().split("\t");
                message_port = Integer.parseInt(results[1]) ;

            }

            // Modify the GUI here.

            // If simOverrunPanel is already created, meaning the GUI was setup without connecting to the server.
            // Now, the user hits the Connect button to connect. Therefore, we need to update this panel once
            // it gets connected.
            if (simOverrunPanel != null) {
                (new RebuildSimOverrunPanelTask()).execute();
            }
        }
        catch (NumberFormatException nfe) {

        }
        catch (IOException e) {

        }
        catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    /**
     * Adds all the variables to variable server for getting SIM states.
     */
    private void scheduleGetSimState() {
        try {
            statusSimcom = new VariableServerConnection(host, port);
            statusSimcom.put("trick.var_set_client_tag(\"SimControl2\")\n");

            // whenever there is data in statusSimcom socket, do something
            String status_vars ;

            status_vars = "trick.var_add(\"trick_sys.sched.time_tics\") \n" +
                          "trick.var_add(\"trick_sys.sched.mode\") \n" +
                          "trick.var_add(\"trick_real_time.rt_sync.actual_run_ratio\") \n" +
                          "trick.var_add(\"trick_real_time.rt_sync.active\") \n";

            if ( debug_present != 0 ) {
                status_vars += "trick.var_add(\"trick_instruments.debug_pause.debug_pause_flag\")\n" ;
            }
            if ( overrun_present != 0 ) {
                status_vars += "trick.var_add(\"trick_real_time.rt_sync.total_overrun\")\n" ;
            }
            statusSimcom.put(status_vars) ;

            statusSimcom.put("trick.var_cycle(0.25)\n");

            getAction("connect").setEnabled(false);         
            runningSimList.setEnabled(false);
        }
        catch (NumberFormatException nfe) {

        }
        catch (IOException e) {
            statusLabel.setText("Not Ready");
            statusLabel.setEnabled(false);
            statusSimcom = null;
            getAction("connect").setEnabled(true);           
            runningSimList.setEnabled(true);
            getAction("startSim").setEnabled(false);
        }
    }

    public static void main(String[] args) {
        Application.launch(rtPerf.class, args);
    }
}














































































/* class JobExecutionEvent {
    public String id;
    public boolean isEOF;
    public boolean isTOF;
    public double start;
    public double stop;

    public JobExecutionEvent(String identifier, boolean isTopOfFrame, boolean isEndOfFrame, double start_time, double stop_time) {
        id = identifier;
        isEOF = isEndOfFrame;
        isTOF = isTopOfFrame;
        start = start_time;
        stop = stop_time;
    }

    public String toString() {
        return ( "JobExecutionEvent: " + id + "," + start + "," + stop );
    }
}

class KeyedColorMap {
    private Map<String, Color> colorMap;
    int minColorIntensity;

    public KeyedColorMap() {
        colorMap = new HashMap<String, Color>();
        minColorIntensity = 100;
    }

    private Color generateColor () {
        Random rand = new Random();
        boolean found = false;
        int R = 0;
        int G = 0;
        int B = 0;

        while (!found) {
            R = rand.nextInt(256);
            G = rand.nextInt(256);
            B = rand.nextInt(256);
            found = true;
            if ((R < minColorIntensity) && (G < minColorIntensity) && (B < minColorIntensity)) {
                found = false;
            }
        }
        return new Color( R,G,B);
    }

    public void addKey( String identifier ) {
        if (!colorMap.containsKey(identifier)) {
            colorMap.put(identifier, generateColor());
        }
    }

    // Given the key, return the color.
    public Color getColor(String identifier) {
        return colorMap.get(identifier);
    }

    // Given the color, return the key.
    public String getKeyOfColor(Color search_color) {
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            String id = entry.getKey();
            Color color = entry.getValue();
            if (color.getRGB() == search_color.getRGB()) {
                return id;
            }
        }
        return null;
    }

    public void readFile(String fileName) throws IOException {
        try {
            BufferedReader in = new BufferedReader( new FileReader(fileName) );
            String line;
            String field[];

            while( (line = in.readLine()) !=null) {
                field   = line.split(",");
                String id    = field[0];
                int R = Integer.parseInt( field[1]);
                int G = Integer.parseInt( field[2]);
                int B = Integer.parseInt( field[3]);
                colorMap.put(id, new Color(R,G,B));
            }
            in.close();
        } catch ( java.io.FileNotFoundException e ) {
           System.out.println("File \"" + fileName + "\" not found.\n");
        }
    }

    public void writeFile(String fileName) throws IOException {
        BufferedWriter out = new BufferedWriter( new FileWriter(fileName) );
        for (Map.Entry<String, Color> entry : colorMap.entrySet()) {
            String id = entry.getKey();
            Color color = entry.getValue();
            String line = String.format(id + "," + color.getRed() +
                                             "," + color.getGreen() +
                                             "," + color.getBlue() + "\n");
            out.write(line, 0, line.length());
        }
        out.flush();
        out.close();
    }
} // KeyedColorMap

class TraceViewCanvas extends JPanel {

    public static final int MIN_TRACE_WIDTH = 4;
    public static final int DEFAULT_TRACE_WIDTH = 10;
    public static final int MAX_TRACE_WIDTH = 30;
    public static final int LEFT_MARGIN = 100;
    public static final int RIGHT_MARGIN = 100;
    public static final int TOP_MARGIN = 20;
    public static final int BOTTOM_MARGIN = 20;

    private int traceWidth;
    private double frameDuration;
    private List<JobExecutionEvent> jobExecList;
    private KeyedColorMap idToColorMap;
    private BufferedImage image;
    private TraceViewOutputToolBar sToolBar;
    private Cursor crossHairCursor;
    private Cursor defaultCursor;

    public TraceViewCanvas( ArrayList<JobExecutionEvent> jobExecEvtList, TraceViewOutputToolBar outputToolBar ) {

        traceWidth = DEFAULT_TRACE_WIDTH;
        frameDuration = 1.0;
        image = null;
        sToolBar = outputToolBar;
        jobExecList = jobExecEvtList;
        crossHairCursor = new Cursor( Cursor.CROSSHAIR_CURSOR );
        defaultCursor = new Cursor( Cursor.DEFAULT_CURSOR );
        double smallestStart =  Double.MAX_VALUE;
        double largestStop   = -Double.MAX_VALUE;

        try {
           idToColorMap = new KeyedColorMap();
           idToColorMap.readFile("IdToColors.txt");

           boolean wasTOF = false;
           double startOfFrame = 0.0;
           double lastStartOfFrame = 0.0;
           double frameSizeSum = 0.0;
           int frameNumber = 0;
           int frameSizeCount = 0;

           for (JobExecutionEvent jobExec : jobExecList ) {
                if (jobExec.start < smallestStart) smallestStart = jobExec.start;
                if (jobExec.stop  > largestStop)    largestStop  = jobExec.stop;
                // Calculate the average frame size.
                if (!wasTOF && jobExec.isTOF) {
                    startOfFrame = jobExec.start;
                    if (frameNumber > 0) {
                        double frameSize = (startOfFrame - lastStartOfFrame);
                        frameSizeSum += frameSize;
                        frameSizeCount ++;
                    }
                    lastStartOfFrame = startOfFrame;
                    frameNumber++;
                }
                wasTOF = jobExec.isTOF;
                idToColorMap.addKey(jobExec.id);
            }

            // Calculate the average frame size.
            frameDuration = frameSizeSum / frameSizeCount;
            idToColorMap.writeFile("IdToColors.txt");

           System.out.println("File loaded.\n");
        } catch ( java.io.FileNotFoundException e ) {
           System.out.println("File not found.\n");
           System.exit(0);
        } catch ( java.io.IOException e ) {
           System.out.println("IO Exception.\n");
           System.exit(0);
        }

        int preferredHeight = traceWidth * (int)((largestStop - smallestStart) / frameDuration) + TOP_MARGIN;
        setPreferredSize(new Dimension(500, preferredHeight));

        ViewListener viewListener = new ViewListener();
         addMouseListener(viewListener);
         addMouseMotionListener(viewListener);
    }

    public double getFrameDuration() {
        return frameDuration;
    }

    public void setFrameDuration(double duration) {
        frameDuration = duration;
        repaint();
    }

    public void increaseTraceWidth() {
        if (traceWidth < MAX_TRACE_WIDTH) {
            traceWidth ++;
            repaint();
        }
    }

    public void decreaseTraceWidth() {
        if (traceWidth > MIN_TRACE_WIDTH) {
            traceWidth --;
            repaint();
        }
    }

    private boolean traceRectContains(int x, int y) {
        int traceRectXMax = getWidth() - RIGHT_MARGIN;
        if ( x < (LEFT_MARGIN)) return false;
        if ( x > (traceRectXMax)) return false;
        if ( y < TOP_MARGIN) return false;
        return true;
    }

    private boolean timeRectContains(int x, int y) {
        int timeRectXMin = 30;
        int timeRectXMax = LEFT_MARGIN;
        if ( x < 30 ) return false;
        if ( x > LEFT_MARGIN) return false;
        if ( y < TOP_MARGIN) return false;
        return true;
    }

    private class ViewListener extends MouseInputAdapter {
        public void mouseReleased(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            Color color = new Color ( image.getRGB(x,y) );

            String id = idToColorMap.getKeyOfColor( color );
            sToolBar.setJobID(id);

            if ( y > TOP_MARGIN) {
                int frameNumber = (y - TOP_MARGIN) / traceWidth;
                sToolBar.setFrameNumber(frameNumber);
            }
            if ( traceRectContains(x, y)) {
                double pixelsPerSecond = (double)calcTraceRectWidth() / frameDuration;
                double subFrameTime = (x - LEFT_MARGIN) / pixelsPerSecond;
                sToolBar.setSubFrameTime(subFrameTime);
            }
        }

        public void mouseMoved(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            if ( traceRectContains(x, y)) {
                setCursor(crossHairCursor);
            } else {
                setCursor(defaultCursor);
            }
        }
    }

    private int calcTraceRectHeight() {
        return ( getHeight() - TOP_MARGIN - BOTTOM_MARGIN);
    }

    private int calcTraceRectWidth() {
        return ( getWidth() - LEFT_MARGIN - RIGHT_MARGIN);
    }

    private void doDrawing(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        rh.put(RenderingHints.KEY_RENDERING,
               RenderingHints.VALUE_RENDER_QUALITY);

        int traceRectHeight = calcTraceRectHeight();
        int traceRectWidth = calcTraceRectWidth();
        double pixelsPerSecond = (double)traceRectWidth / frameDuration;

        // Panel Background Color Fill
        g2d.setPaint(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // Frame Trace Rectangle Fill
        g2d.setPaint(Color.BLACK);
        g2d.fillRect(LEFT_MARGIN, TOP_MARGIN, traceRectWidth, traceRectHeight);

        boolean wasEOF = false;
        boolean wasTOF = false;
        double startOfFrame = 0.0;
        int frameNumber = 0;

        for (JobExecutionEvent jobExec : jobExecList ) {

            if (!wasTOF && jobExec.isTOF) {
                startOfFrame = jobExec.start;
                frameNumber ++;
            }

            wasTOF = jobExec.isTOF;
            wasEOF = jobExec.isEOF;

            int jobY = TOP_MARGIN + frameNumber * traceWidth;
            int jobStartX = LEFT_MARGIN + (int)((jobExec.start - startOfFrame) * pixelsPerSecond);
            int jobWidth  = (int)( (jobExec.stop - jobExec.start) * pixelsPerSecond);

            g2d.setPaint(Color.BLACK);
            g2d.drawString ( String.format("%8.3f", startOfFrame), 30, jobY + traceWidth/2);
            g2d.setPaint( idToColorMap.getColor( jobExec.id ) );
            g2d.fillRect(jobStartX, jobY, jobWidth, traceWidth-2);

        } // for
    } // doDrawing

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        doDrawing(g2);
        g.drawImage(image, 0, 0, this);
        g2.dispose();
    }
} // class TraceViewCanvas

class TraceViewInputToolBar extends JToolBar implements ActionListener {

    private TraceViewCanvas traceView;
    private JTextField frameDurationField;

    public TraceViewInputToolBar (TraceViewCanvas tv) {
        traceView = tv;
        add( new JLabel(" Frame Size: "));
        frameDurationField = new JTextField(15);
        frameDurationField.setText( String.format("%8.4f", traceView.getFrameDuration()) );
        add(frameDurationField);

        JButton setButton = new JButton("Set");
        setButton.addActionListener(this);
        setButton.setActionCommand("setFrameSize");
        setButton.setToolTipText("Set frame size in seconds.");
        add(setButton);
    }
    public void actionPerformed(ActionEvent event) {
        String s = event.getActionCommand();
        switch (s) {
            case "setFrameSize":
                double newFrameSize = 0.0;
                try {
                    newFrameSize = Double.parseDouble( frameDurationField.getText() );
                } catch ( NumberFormatException e) {
                    frameDurationField.setText( String.format("%8.4f", traceView.getFrameDuration()) );
                }
                if ( newFrameSize > 0.0) {
                    traceView.setFrameDuration( newFrameSize );
                }
            break;
            default:
                System.out.println("Unknown Action Command:" + s);
            break;
        }
    }
} // class TraceViewInputToolBar

class TraceViewOutputToolBar extends JToolBar {
    private JTextField IDField;
    private JTextField frameNumberField;
    private JTextField subFrameTimeField;

    public TraceViewOutputToolBar () {

        add( new JLabel(" Job ID: "));
        IDField = new JTextField(15);
        IDField.setEditable(false);
        IDField.setText( "");
        add(IDField);

        add( new JLabel(" Frame Number: "));
        frameNumberField = new JTextField(15);
        frameNumberField.setEditable(false);
        frameNumberField.setText( "0");
        add(frameNumberField);

        add( new JLabel(" Subframe Time: "));
        subFrameTimeField = new JTextField(15);
        subFrameTimeField.setEditable(false);
        subFrameTimeField.setText( "0.00");
        add(subFrameTimeField);
    }
    public void setJobID(String id) {
        IDField.setText( id );
    }
    public void setFrameNumber(int fn) {
        frameNumberField.setText( String.format("%d", fn));
    }
    public void setSubFrameTime(double time) {
        subFrameTimeField.setText( String.format("%8.4f", time));
    }
} // class TraceViewOutputToolBar

class TraceViewMenuBar extends JMenuBar implements ActionListener {

    private TraceViewCanvas traceView;

    public TraceViewMenuBar(TraceViewCanvas tv) {
        traceView = tv;

        JMenu fileMenu = new JMenu("File");
        JMenuItem fileMenuExit = new JMenuItem("Exit");
        fileMenuExit.setActionCommand("exit");
        fileMenuExit.addActionListener(this);
        fileMenu.add(fileMenuExit);
        add(fileMenu);

        JMenu optionsMenu = new JMenu("Options");
        JMenu traceSizeMenu = new JMenu("TraceSize");
        JMenuItem traceSizeMenuIncrease = new JMenuItem("Increase Trace Width");
        traceSizeMenuIncrease.setActionCommand("increase-trace_width");
        KeyStroke ctrlPlus  = KeyStroke.getKeyStroke('P', InputEvent.CTRL_MASK );
        traceSizeMenuIncrease.setAccelerator(ctrlPlus);
        traceSizeMenuIncrease.addActionListener(this);
        traceSizeMenu.add(traceSizeMenuIncrease);
        JMenuItem traceSizeMenuDecrease = new JMenuItem("Decrease Trace Width");
        traceSizeMenuDecrease.setActionCommand("decrease-trace_width");
        KeyStroke ctrlMinus = KeyStroke.getKeyStroke('-', InputEvent.CTRL_MASK);
        traceSizeMenuDecrease.setAccelerator(ctrlMinus);
        traceSizeMenuDecrease.addActionListener(this);
        traceSizeMenu.add(traceSizeMenuDecrease);
        optionsMenu.add(traceSizeMenu);
        add(optionsMenu);

    }
    public void actionPerformed(ActionEvent e) {
        String s = e.getActionCommand();
        switch (s) {
            case "increase-trace_width":
                traceView.increaseTraceWidth();
            break;
            case "decrease-trace_width":
                traceView.decreaseTraceWidth();
            break;
            case "exit":
                System.exit(0);
            default:
                System.out.println("Unknown Action Command:" + s);
            break;
        }
    }
} // class TraceViewMenuBar

class TraceViewWindow extends JFrame {

    public TraceViewWindow( ArrayList<JobExecutionEvent> jobExecList ) {
        TraceViewOutputToolBar outputToolBar = new TraceViewOutputToolBar();
        TraceViewCanvas traceView = new TraceViewCanvas( jobExecList, outputToolBar);

        TraceViewMenuBar menuBar = new TraceViewMenuBar(traceView);
        setJMenuBar(menuBar);

        TraceViewInputToolBar nToolBar = new TraceViewInputToolBar( traceView );
        add(nToolBar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane( traceView );
        scrollPane.setPreferredSize(new Dimension(800, 400));

        JPanel tracePanel = new JPanel();
        tracePanel.setPreferredSize(new Dimension(800, 400));
        tracePanel.add(scrollPane);
        tracePanel.setLayout(new BoxLayout(tracePanel, BoxLayout.X_AXIS));

        JPanel mainPanel  = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(tracePanel);

        add(outputToolBar, BorderLayout.SOUTH);

        setTitle("rtPerf");
        setSize(800, 500);
        add(mainPanel);
        pack();
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setFocusable(true);
        setVisible(true);

        traceView.repaint();
    }
} // class TraceViewWindow

public class rtPerf extends JFrame {
    private ArrayList<JobExecutionEvent> jobExecList;
    private TraceViewCanvas traceViewCanvas;

    public rtPerf(String [] args) {
        jobExecList = new ArrayList<>();
        TraceViewOutputToolBar outputToolBar = new TraceViewOutputToolBar();
        traceViewCanvas = new TraceViewCanvas(jobExecList, outputToolBar);

        // Sets up the main frame
        setTitle("Real-Time Jobs");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);

        getContentPane().add(traceViewCanvas);

        // Starts a seperate thread for RT data retreival.
        new Thread(() -> {
            try {
                Socket socket = new Socket(trick.var_server_get_hostname(), trick.var_server_get_port());
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        
                while (true) {
                    String data = reader.readLine();
                    if (data == null) {
                        break; // Handle connection termination
                    }
                    updateJobExecutionList(data);
                }
        
                reader.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        setVisible(true);
    }

    private void updateJobExecutionList(String data) {
        synchronized (jobExecList) {
            String[] parts = data.split(","); // Need to check to see if the data is comma seperated or tab seperated.
            if (parts.length >= 5) {
                String id = parts[0];
                boolean isEOF = Boolean.parseBoolean(parts[1]);
                boolean isTOF = Boolean.parseBoolean(parts[2]);
                double start = Double.parseDouble(parts[3]);
                double stop = Double.parseDouble(parts[4]);
                JobExecutionEvent event = new JobExecutionEvent(id, isEOF, isTOF, start, stop);
                jobExecList.add(event);
            }
        }
    }

    public static void main(String[] args) {
        rtPerf rtperf = new rtPerf( args );
    } // main
} // class rtPerf */