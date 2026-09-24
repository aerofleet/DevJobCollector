package kr.itsdev.devjobcollector.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin.bootstrap")
public class AdminBootstrapProperties {
    private boolean enabled;
    private String email;
    private String name;
    private String password;
    private String passwordFile;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordFile() { return passwordFile; }
    public void setPasswordFile(String passwordFile) { this.passwordFile = passwordFile; }
}
