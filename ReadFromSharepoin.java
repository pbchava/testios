<dependency>
    <groupId>com.github.markusbernhardt</groupId>
    <artifactId>proxy-vole</artifactId>
    <version>1.1.5</version>
</dependency>

import com.github.markusbernhardt.proxy.ProxySearch;
import java.io.*;
import java.net.*;
import java.util.Base64;

public class SharePointPacDownloader {

    public static void main(String[] args) {
        // --- CONFIGURATION ---
        String fileUrl = "https://yourtenant.sharepoint.com/sites/SiteName/Shared%20Documents/YourFile.pdf";
        String spUser = "yourname@company.com";
        String spPass = "YourSharePointPassword";
        String outputPath = "downloaded_file.pdf";

        // Optional: If your PROXY itself requires a login
        String proxyUser = "proxyUser";
        String proxyPass = "proxyPass";

        try {
            // 1. INITIALIZE PROXY-VOLE (PAC DETECTION)
            // This searches for IE/Chrome/Firefox settings and PAC scripts
            ProxySearch proxySearch = ProxySearch.getDefaultProxySearch();
            ProxySelector proxySelector = proxySearch.getProxySelector();
            
            if (proxySelector != null) {
                ProxySelector.setDefault(proxySelector);
                System.out.println("Successfully loaded PAC/System proxy settings.");
            }

            // 2. HANDLE AUTHENTICATION (Proxy & SharePoint)
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    // If the request is for the Proxy
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                    } 
                    // If the request is for SharePoint (Basic Auth)
                    return new PasswordAuthentication(spUser, spPass.toCharArray());
                }
            });

            // 3. EXECUTE DOWNLOAD
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // SharePoint often requires this header for non-browser clients
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            connection.setRequestProperty("X-FORMS_BASED_AUTH_ACCEPTED", "f");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(outputPath)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    System.out.println("Download complete: " + outputPath);
                }
            } else {
                System.err.println("Error: Server returned HTTP " + responseCode);
                // Tip: 401/403 often means Legacy Auth is disabled by your IT.
            }

        } catch (Exception e) {
            System.err.println("Failed to download file.");
            e.printStackTrace();
        }
    }
}