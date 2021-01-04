package jRAT.moduleAuxiliary;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jnativehook.DefaultLibraryLocator;

public class ModuleKeyloggerCustomLibraryLocator extends DefaultLibraryLocator {

    private static String libraryPath;
    
    public static void setLibraryPath(String path)
    {
        libraryPath = path;
    }
    
    public Iterator<File>getLibraries()
    {
        List<File> libraries = new ArrayList<File>(1);
        libraries.add(new File(libraryPath));
        return libraries.iterator();        
    }
}
