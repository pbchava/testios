import java.io.*;
import java.net.*;
import java.util.Base64;

public class SharePointLegacyDownload {

    public static void main(String[] args) {
        String fileUrl = "https://yourtenant.sharepoint.com/sites/YourSite/Shared%20Documents/file.pdf";
        String username = "yourname@yourtenant.onmicrosoft.com";
        String password = "YourPassword123!";
        String outputPath = "downloaded_file.pdf";

        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // 1. Basic Authentication (Works if Legacy Auth is enabled)
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            
            // 2. Set User-Agent to avoid being blocked as a bot
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(outputPath)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    System.out.println("File downloaded successfully!");
                }
            } else {
                System.out.println("Server returned HTTP code: " + responseCode);
                // Note: 403 usually means MFA is required or Legacy Auth is blocked.
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}