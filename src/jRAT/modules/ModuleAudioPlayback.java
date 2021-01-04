package jRAT.modules;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

public class ModuleAudioPlayback implements LineListener {

    private final static AtomicBoolean PLAYING       = new AtomicBoolean(false);
    private final static AtomicBoolean playCompleted = new AtomicBoolean(false);

    private static String HOST                       = null;
    private static Integer PORT                      = null;

    private static Socket socket                     = null;
    private static DataInputStream socketInput       = null;
    private static DataOutputStream socketOutput     = null;

    /* -------------------------------------------------------------------------------------------------------- */

    public static void init(Class stage) throws Exception
    {
        HOST = (String)  stage.getMethod("getHost", new Class[0]).invoke(null, new Object[0]);
        PORT = (Integer) stage.getMethod("getPort", new Class[0]).invoke(null, new Object[0]);
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: AudioPlayback ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "playfile", "path"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "playurl",  "url"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "stop",     ""));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "status",   ""));
        return moduleInfo.toString().getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        switch (methodName)
        {
            case "playfile": return true;
            case "playurl":  return true;
            default:         return false;
        }
    }

    /* -------------------------------------------------------------------------------------------------------- */

    public static byte[] stop() throws UnsupportedEncodingException
    {
        PLAYING.set(false);
        return new String("Stopped!").getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */
    
    public static byte[] status() throws UnsupportedEncodingException
    {
        if (PLAYING.get())
            return new String("Audio playback in progress!").getBytes("UTF-8");
        else
            return new String("No audio playing!").getBytes("UTF-8");
    }

    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */
    /*                                      AUDIO PLAYBACK METHODS                                              */
    /* -------------------------------------------------------------------------------------------------------- */
    /* -------------------------------------------------------------------------------------------------------- */

    public static void playfile(String audioPath)
    {
        /* If an audio clip is already playing
         * stop it and play this clip instead
         */
        if (PLAYING.get())
        {
            PLAYING.set(false);
            try { Thread.sleep(1500); }
            catch (InterruptedException ex) {}
        }

        PLAYING.set(true);
        try
        {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(audioPath));
            AudioFormat format = audioInputStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip = (Clip) AudioSystem.getLine(info);

            clip.addLineListener(new ModuleAudioPlayback());
            clip.open(audioInputStream);             
            clip.start();

            /* wait for the playback to finish */            
            while (!playCompleted.get() && PLAYING.get())
            {
                try { Thread.sleep(1000); }
                catch (InterruptedException ex) {}
            }

            clip.close();
            audioInputStream.close();
        }
        catch (Exception ex) {}
        PLAYING.set(false);
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */

    public static void playurl(String url)
    {
        /* If an audio clip is already playing
         * stop it and play this clip instead
         */
        if (PLAYING.get())
        {
            PLAYING.set(false);
            try { Thread.sleep(1500); }
            catch (InterruptedException ex) {}
        }       

        PLAYING.set(true);
        AudioInputStream audioInputStream = null;
        Clip clip = null;
        try
        {
            audioInputStream = AudioSystem.getAudioInputStream(new URL(url));
            AudioFormat format = audioInputStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            clip = (Clip) AudioSystem.getLine(info);

            clip.addLineListener(new ModuleAudioPlayback());
            clip.open(audioInputStream);
            clip.start();

            /* wait for the playback to finish */            
            while (!playCompleted.get() && PLAYING.get())
            {
                try { Thread.sleep(1000); }
                catch (InterruptedException ex) {}
            }
            clip.close();
        }
        catch (Exception ex) {}
        finally
        {
            try { if (audioInputStream != null) audioInputStream.close(); }
            catch (Exception ex) {}
        }
        PLAYING.set(false);
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

    @Override
    public void update(LineEvent event)
    {
        LineEvent.Type type = event.getType();
        if (type == LineEvent.Type.START)
            playCompleted.set(false);
        else if (type == LineEvent.Type.STOP)
            playCompleted.set(true);
    }
    
    /* -------------------------------------------------------------------------------------------------------- */

}
