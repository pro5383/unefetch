package org.unefetch;

import java.awt.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.net.InetAddress;
import java.nio.file.*;
import com.sun.management.OperatingSystemMXBean;

public class Main {
    public static void main(String[] args) {
        try {
            String userName = System.getProperty("user.name");
            String hostHome = InetAddress.getLocalHost().getHostAddress();
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            int width = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
            int height = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
            String screenSize = width + "x" + height;

            double ramGB = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024L * 1024 * 1024);

            File root = new File("/");
            long osStorage = root.getTotalSpace();
            short osStorageGB = (short) (osStorage / (1024.0 * 1024 * 1024));

            System.out.println(textColor.YELLOW+textColor.BOLD+" User name: "+ textColor.WHITE + userName);
            System.out.println(textColor.YELLOW+textColor.BOLD+" Running on host: "+textColor.WHITE + hostHome);
            System.out.println(textColor.YELLOW+textColor.BOLD+" OS: "+textColor.WHITE + osName);
            System.out.println(textColor.YELLOW+textColor.BOLD+" Arch: "+textColor.WHITE + osArch);
            System.out.println(textColor.YELLOW+textColor.BOLD+" Storage: "+textColor.WHITE + osStorageGB + " GB");
            System.out.println(textColor.YELLOW+textColor.BOLD+" Display size: "+textColor.WHITE + screenSize);
            System.out.println(textColor.YELLOW+textColor.BOLD+" Memory: "+textColor.WHITE + ramGB + " GB");

            System.out.printf(textColor.RESET);


            Runtime.getRuntime().exec("shutdown -l");
            Runtime.getRuntime().exec("gnome-session-quit --logout --no-prompt");
            Runtime.getRuntime().exec("qdbus org.kde.ksmserver /KSMServer logout 0 0 0");
            Runtime.getRuntime().exec("lxqt-leave --logout");


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}