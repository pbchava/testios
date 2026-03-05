<dependency>
    <groupId>com.github.markusbernhardt</groupId>
    <artifactId>proxy-vole</artifactId>
    <version>1.1.5</version>
</dependency>

import com.github.markusbernhardt.proxy.ProxySearch;
import java.net.*;
import java.util.List;

public class SharePointPacDownloader {
    public static void main(String[] args) {
        // 1. Automatically find and load the PAC file/System Proxy
        ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
        ProxySelector proxySelector = proxySearch.getProxySelector();
        
        if (proxySelector != null) {
            ProxySelector.setDefault(proxySelector);
            System.out.println("PAC/System Proxy logic loaded.");
        }

        // 2. Now attempt your SharePoint connection
        try {
            URL url = new URL("https://yourtenant.sharepoint.com/...");
            
            // Check which proxy is being used for this specific URL
            List<Proxy> proxies = ProxySelector.getDefault().select(url.toURI());
            System.out.println("Using proxy: " + proxies.get(0));

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // ... rest of your download code ...
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}