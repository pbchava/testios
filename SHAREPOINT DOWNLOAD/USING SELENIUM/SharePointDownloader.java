package sharepoint.util;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SharePointDownloader {
    public static void main(String[] args) {
        // 1. Set up Edge Options to use your Windows Session
        EdgeOptions options = new EdgeOptions();
        
        // This tells Edge to use your actual Windows profile
        // Replace 'YourUser' with your Windows username
        options.addArguments("user-data-dir=C:\\Users\\YourUser\\AppData\\Local\\Microsoft\\Edge\\User Data");
        options.addArguments("profile-directory=Default"); 
        
        // 2. Configure Download Behavior
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", "C:\\Downloads\\SharePointFiles");
        prefs.put("download.prompt_for_download", false);
        options.setExperimentalOption("prefs", prefs);

        WebDriver driver = new EdgeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        try {
            // 3. Navigate to the SharePoint File
            String sharePointUrl = "https://yourcompany.sharepoint.com/:x:/s/YourSite/YourFileID";
            driver.get(sharePointUrl);

            // 4. Handle DSSO / Redirects
            // Because of user-data-dir, it should bypass the login screen automatically.
            
            // 5. Trigger the Download
            // We look for the "Download" button in the top command bar
            By downloadBtn = By.xpath("//button[@name='Download'] | //button[contains(.,'Download')]");
            wait.until(ExpectedConditions.elementToBeClickable(downloadBtn)).click();

            // Give it a moment to finalize the download stream
            Thread.sleep(5000); 
            
            System.out.println("Download triggered successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}