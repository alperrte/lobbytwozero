package com.ltz.hardware.infrastructure.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** No ENV contract is introduced. Operators must approve and provision secure configuration. */
@Component
@ConfigurationProperties(prefix = "ltz.hardware.icecat")
public class IcecatProperties {
    private boolean enabled;
    private boolean accountVerified;
    private boolean mediaRightsVerified;
    private String apiToken = "";
    private String contentToken = "";
    private boolean localMediaEnabled;
    private Path mediaDirectory = Path.of("var", "hardware-media");
    private int batchSize = 30;

    public boolean ready() { return enabled && accountVerified && !apiToken.isBlank() && !contentToken.isBlank(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isAccountVerified() { return accountVerified; }
    public void setAccountVerified(boolean value) { accountVerified = value; }
    public boolean isMediaRightsVerified() { return mediaRightsVerified; }
    public void setMediaRightsVerified(boolean value) { mediaRightsVerified = value; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String value) { apiToken = value == null ? "" : value; }
    public String getContentToken() { return contentToken; }
    public void setContentToken(String value) { contentToken = value == null ? "" : value; }
    public boolean isLocalMediaEnabled() { return localMediaEnabled; }
    public void setLocalMediaEnabled(boolean value) { localMediaEnabled = value; }
    public Path getMediaDirectory() { return mediaDirectory; }
    public void setMediaDirectory(Path value) { mediaDirectory = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) {
        if (value < 1 || value > 100) throw new IllegalArgumentException("Invalid Icecat batch size");
        batchSize = value;
    }
    @Override public String toString() { return "IcecatProperties[credentials=REDACTED, enabled=" + enabled + "]"; }
}
