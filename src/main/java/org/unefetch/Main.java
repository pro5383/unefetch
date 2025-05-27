package org.unefetch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.sun.management.OperatingSystemMXBean;
import java.awt.RadialGradientPaint;

public class Main extends JFrame {
    private static final Color PRIMARY_DARK = new Color(15, 23, 42);
    private static final Color SECONDARY_DARK = new Color(30, 41, 59);
    private static final Color ACCENT_BLUE = new Color(59, 130, 246);
    private static final Color ACCENT_PURPLE = new Color(139, 92, 246);
    private static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    private static final Color TEXT_SECONDARY = new Color(203, 213, 225);
    private static final Color SUCCESS_GREEN = new Color(34, 197, 94);
    private static final Color WARNING_ORANGE = new Color(251, 146, 60);
    private static final Color ERROR_RED = new Color(239, 68, 68);

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 800;
    private static final int ANIMATION_DELAY = 16;

    private JPanel mainPanel;
    private ModernButton refreshButton;
    private SystemInfoPanel infoPanel;
    private StatusPanel statusPanel;
    private Timer animationTimer;
    private Timer particleTimer;

    private float gradientOffset = 0f;
    private List<Particle> particles = new ArrayList<>();
    private volatile boolean isLoading = false;

    public Main() {
        initializeFrame();
        createComponents();
        setupLayout();
        startAnimations();
        loadSystemInfoAsync();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void cleanup() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (particleTimer != null) {
            particleTimer.stop();
        }
    }

    private void initializeFrame() {
        setTitle("System Information | Unefetch v2.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(800, 600));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
            System.setProperty("sun.java2d.opengl", "true");
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ex) {}
        }

        try {
            setIconImage(createAppIcon());
        } catch (Exception e) {}
    }

    private Image createAppIcon() {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        GradientPaint gradient = new GradientPaint(0, 0, ACCENT_BLUE, 64, 64, ACCENT_PURPLE);
        g2d.setPaint(gradient);
        g2d.fillRoundRect(8, 8, 48, 48, 16, 16);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Segoe UI Emoji", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        String text = "i";
        int x = (64 - fm.stringWidth(text)) / 2;
        int y = (64 + fm.getAscent()) / 2;
        g2d.drawString(text, x, y);

        g2d.dispose();
        return icon;
    }

    private void createComponents() {
        mainPanel = new ModernBackgroundPanel();
        mainPanel.setLayout(new BorderLayout(20, 20));
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JPanel headerPanel = createHeaderPanel();
        infoPanel = new SystemInfoPanel();
        JScrollPane scrollPane = new JScrollPane(infoPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);

        JPanel bottomPanel = createBottomPanel();

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel titleLabel = new JLabel("System Information", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI Emoji", Font.BOLD, 36));
        titleLabel.setForeground(TEXT_PRIMARY);

        JLabel subtitleLabel = new JLabel("Updated: " + getCurrentTime(), JLabel.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        subtitleLabel.setForeground(TEXT_SECONDARY);

        JPanel titleContainer = new JPanel(new BorderLayout(0, 5));
        titleContainer.setOpaque(false);
        titleContainer.add(titleLabel, BorderLayout.CENTER);
        titleContainer.add(subtitleLabel, BorderLayout.SOUTH);

        headerPanel.add(titleContainer, BorderLayout.CENTER);
        return headerPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(15, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        statusPanel = new StatusPanel();

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        buttonPanel.setOpaque(false);

        refreshButton = new ModernButton("🔄 Refresh Data", ACCENT_BLUE);
        ModernButton exitButton = new ModernButton("❌ Exit", ERROR_RED);

        refreshButton.addActionListener(this::handleRefresh);
        exitButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(refreshButton);
        buttonPanel.add(exitButton);

        bottomPanel.add(statusPanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        return bottomPanel;
    }

    private void setupLayout() {
        setContentPane(mainPanel);
    }

    private void handleRefresh(ActionEvent e) {
        if (!isLoading) {
            loadSystemInfoAsync();
        }
    }

    private void loadSystemInfoAsync() {
        isLoading = true;
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳ Updating...");
        statusPanel.setStatus("Loading data...", StatusPanel.StatusType.LOADING);

        CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(800);
                        return collectSystemInfo();
                    } catch (Exception e) {
                        throw new RuntimeException("Error collecting system information", e);
                    }
                }).orTimeout(30, TimeUnit.SECONDS)
                .whenComplete((systemInfo, throwable) -> {
                    SwingUtilities.invokeLater(() -> {
                        isLoading = false;
                        refreshButton.setEnabled(true);
                        refreshButton.setText("🔄 Refresh Data");

                        if (throwable != null) {
                            handleError(throwable);
                        } else {
                            infoPanel.updateInfo(systemInfo);
                            statusPanel.setStatus("Data updated: " + getCurrentTime(),
                                    StatusPanel.StatusType.SUCCESS);
                        }
                    });
                });
    }

    private SystemInfo collectSystemInfo() {
        SystemInfo info = new SystemInfo();

        try {
            info.userName = System.getProperty("user.name", "Unknown");
            info.computerName = InetAddress.getLocalHost().getHostName();
            info.osName = System.getProperty("os.name", "Unknown");
            info.osVersion = System.getProperty("os.version", "Unknown");
            info.osArch = System.getProperty("os.arch", "Unknown");
            info.javaVersion = System.getProperty("java.version", "Unknown");
            info.javaVendor = System.getProperty("java.vendor", "Unknown");

            info.localIp = InetAddress.getLocalHost().getHostAddress();
            info.publicIp = getPublicIP();

            Runtime runtime = Runtime.getRuntime();
            info.processorCount = runtime.availableProcessors();

            // Get CPU model
            info.cpuModel = getCpuModel();

            // Get GPU info
            String[] gpuInfo = getGpuInfo();
            info.gpuModel = gpuInfo[0];
            info.videoMemory = gpuInfo[1];

            OperatingSystemMXBean osBean;
            try {
                osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                info.totalMemory = osBean.getTotalMemorySize();
                info.freeMemory = osBean.getFreeMemorySize();
                info.usedMemory = info.totalMemory - info.freeMemory;
                double cpuLoad = osBean.getCpuLoad() * 100;
                info.cpuLoad = Double.isNaN(cpuLoad) || cpuLoad < 0 ? 0.0 : cpuLoad;
            } catch (Exception e) {
                info.totalMemory = 0;
                info.freeMemory = 0;
                info.usedMemory = 0;
                info.cpuLoad = 0.0;
            }

            File[] roots = File.listRoots();
            long totalStorage = 0, freeStorage = 0;
            for (File root : roots) {
                totalStorage = Math.addExact(totalStorage, root.getTotalSpace());
                freeStorage = Math.addExact(freeStorage, root.getFreeSpace());
            }
            info.totalStorage = totalStorage;
            info.freeStorage = freeStorage;
            info.usedStorage = totalStorage - freeStorage;

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();
            info.displayCount = devices.length;
            if (devices.length > 0) {
                DisplayMode dm = devices[0].getDisplayMode();
                info.screenWidth = dm.getWidth();
                info.screenHeight = dm.getHeight();
                info.refreshRate = dm.getRefreshRate();
            }

        } catch (Exception e) {
            throw new RuntimeException("Error collecting system information", e);
        }

        return info;
    }

    private String getPublicIP() {
        String[] ipServices = {
                "https://checkip.amazonaws.com/",
                "https://api.ipify.org/",
                "https://ifconfig.me/ip"
        };

        for (String urlStr : ipServices) {
            try {
                URL url = new URL(urlStr);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String ip = reader.readLine();
                    if (ip != null && !ip.trim().isEmpty()) {
                        return ip.trim();
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch public IP from " + urlStr + ": " + e.getMessage());
            }
        }
        return "Unavailable";
    }

    private String getCpuModel() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                // Try wmic first
                Process process = Runtime.getRuntime().exec("wmic cpu get name");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.contains("Name")) {
                            return line;
                        }
                    }
                }
                // Fallback to systeminfo
                process = Runtime.getRuntime().exec("systeminfo");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Processor(s):")) {
                            String[] parts = line.split(":");
                            if (parts.length > 1) {
                                String processorInfo = parts[1].trim();
                                return processorInfo.split("@")[0].trim();
                            }
                        }
                    }
                }
            } else if (os.contains("nix") || os.contains("nux")) {
                // Try lscpu first
                Process process = Runtime.getRuntime().exec("lscpu");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("Model name")) {
                            return line.split(":", 2)[1].trim();
                        }
                    }
                }
                // Fallback to /proc/cpuinfo
                process = Runtime.getRuntime().exec("cat /proc/cpuinfo");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("model name")) {
                            return line.split(":", 2)[1].trim();
                        }
                    }
                }
            } else if (os.contains("mac")) {
                Process process = Runtime.getRuntime().exec("sysctl -n machdep.cpu.brand_string");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch CPU model: " + e.getMessage());
        }
        return "Unknown";
    }

    private String[] getGpuInfo() {
        String os = System.getProperty("os.name").toLowerCase();
        String gpuModel = "Unknown";
        String videoMemory = "Unknown";
        try {
            if (os.contains("win")) {
                Process process = Runtime.getRuntime().exec("wmic path Win32_VideoController get Name, AdapterRAM");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    boolean headerSkipped = false;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!headerSkipped) {
                            headerSkipped = true;
                            continue;
                        }
                        if (!line.isEmpty()) {
                            String[] parts = line.split("\\s+", 2);
                            if (parts.length >= 2) {
                                gpuModel = parts[0].trim();
                                try {
                                    long ramBytes = Long.parseLong(parts[1].trim());
                                    videoMemory = SystemInfoPanel.formatBytes(ramBytes);
                                } catch (NumberFormatException e) {
                                    videoMemory = "Unknown";
                                    System.err.println("Failed to parse video memory: " + e.getMessage());
                                }
                            }
                            break; // Take first GPU
                        }
                    }
                }
                // Fallback to PowerShell
                if (gpuModel.equals("Unknown")) {
                    process = Runtime.getRuntime().exec("powershell -Command \"Get-CimInstance Win32_VideoController | Select-Object Name, AdapterRAM\"");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("Name")) {
                                String nextLine = reader.readLine();
                                if (nextLine != null && !nextLine.trim().isEmpty()) {
                                    gpuModel = nextLine.trim();
                                }
                            } else if (line.contains("AdapterRAM")) {
                                String nextLine = reader.readLine();
                                if (nextLine != null && !nextLine.trim().isEmpty()) {
                                    try {
                                        long ramBytes = Long.parseLong(nextLine.trim());
                                        videoMemory = SystemInfoPanel.formatBytes(ramBytes);
                                    } catch (NumberFormatException e) {
                                        videoMemory = "Unknown";
                                        System.err.println("Failed to parse PowerShell video memory: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (os.contains("nix") || os.contains("nux")) {
                // Try lspci for GPU model
                Process process = Runtime.getRuntime().exec("lspci | grep -E 'VGA|3D'");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        String[] parts = line.split(":", 2);
                        if (parts.length > 1) {
                            gpuModel = parts[1].trim();
                        }
                    }
                }
                // Try nvidia-smi for video memory (NVIDIA GPUs)
                try {
                    process = Runtime.getRuntime().exec("nvidia-smi --query-gpu=memory.total --format=csv,noheader");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line = reader.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            videoMemory = line.trim();
                        }
                    }
                } catch (Exception e) {
                    // Fallback to glxinfo if available
                    try {
                        process = Runtime.getRuntime().exec("glxinfo | grep 'Video memory'");
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line = reader.readLine();
                            if (line != null && !line.trim().isEmpty()) {
                                videoMemory = line.split(":", 2)[1].trim();
                            }
                        }
                    } catch (Exception ex) {
                        videoMemory = "Unknown (requires nvidia-smi or glxinfo)";
                        System.err.println("Failed to fetch GPU video memory: " + ex.getMessage());
                    }
                }
            } else if (os.contains("mac")) {
                Process process = Runtime.getRuntime().exec("system_profiler SPDisplaysDataType");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("Chipset Model")) {
                            gpuModel = line.split(":", 2)[1].trim();
                        } else if (line.startsWith("VRAM")) {
                            videoMemory = line.split(":", 2)[1].trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch GPU info: " + e.getMessage());
        }
        return new String[]{gpuModel, videoMemory};
    }

    private void handleError(Throwable error) {
        String message = "Error loading data: " + error.getMessage();
        infoPanel.showError(message);
        statusPanel.setStatus("Load error", StatusPanel.StatusType.ERROR);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    private void startAnimations() {
        animationTimer = new Timer(ANIMATION_DELAY, e -> {
            gradientOffset += 0.008f;
            if (gradientOffset > 1.0f) gradientOffset = 0f;
            mainPanel.repaint();
        });
        animationTimer.start();

        particleTimer = new Timer(500, e -> {
            if (particles.size() < 20) {
                particles.add(new Particle());
            }
            particles.removeIf(particle -> {
                particle.update();
                return particle.isDead();
            });
            mainPanel.repaint();
        });
        particleTimer.start();
    }

    private static class SystemInfo {
        String userName, computerName, osName, osVersion, osArch;
        String javaVersion, javaVendor, localIp, publicIp;
        String cpuModel, gpuModel, videoMemory;
        int processorCount, displayCount, screenWidth, screenHeight, refreshRate;
        long totalMemory, freeMemory, usedMemory;
        long totalStorage, freeStorage, usedStorage;
        double cpuLoad;
    }

    private class Particle {
        float x, y, vx, vy, size, alpha, life;
        Color color;

        public Particle() {
            x = (float)(Math.random() * getWidth());
            y = (float)(Math.random() * getHeight());
            vx = (float)(Math.random() * 2 - 1);
            vy = (float)(Math.random() * 2 - 1);
            size = (float)(Math.random() * 3 + 1);
            alpha = (float)(Math.random() * 0.3 + 0.1);
            life = 1.0f;

            Color[] colors = {ACCENT_BLUE, ACCENT_PURPLE, SUCCESS_GREEN};
            color = colors[(int)(Math.random() * colors.length)];
        }

        public void update() {
            x += vx;
            y += vy;
            life -= 0.01f;

            if (x < 0 || x > getWidth()) vx *= -1;
            if (y < 0 || y > getHeight()) vy *= -1;
        }

        public boolean isDead() {
            return life <= 0;
        }

        public void draw(Graphics2D g2d) {
            if (life > 0) {
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                        (int)(alpha * life * 255)));
                g2d.fillOval((int)x, (int)y, (int)size, (int)size);
            }
        }
    }

    private class ModernBackgroundPanel extends JPanel {
        public ModernBackgroundPanel() {
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

            int width = getWidth();
            int height = getHeight();

            GradientPaint mainGradient = new GradientPaint(
                    0, 0, PRIMARY_DARK,
                    width, height, SECONDARY_DARK
            );
            g2d.setPaint(mainGradient);
            g2d.fillRect(0, 0, width, height);

            float offset = gradientOffset * 2 * (float)Math.PI;
            RadialGradientPaint light1 = new RadialGradientPaint(
                    (float)(width * 0.3 + 200 * Math.cos(offset)),
                    (float)(height * 0.3 + 150 * Math.sin(offset)),
                    300f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 30),
                            new Color(0, 0, 0, 0)}
            );
            g2d.setPaint(light1);
            g2d.fillRect(0, 0, width, height);

            RadialGradientPaint light2 = new RadialGradientPaint(
                    (float)(width * 0.7 + 250 * Math.cos(offset + Math.PI)),
                    (float)(height * 0.7 + 200 * Math.sin(offset + Math.PI)),
                    350f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(ACCENT_PURPLE.getRed(), ACCENT_PURPLE.getGreen(), ACCENT_PURPLE.getBlue(), 25),
                            new Color(0, 0, 0, 0)}
            );
            g2d.setPaint(light2);
            g2d.fillRect(0, 0, width, height);

            for (Particle particle : particles) {
                particle.draw(g2d);
            }

            g2d.dispose();
        }
    }

    private class SystemInfoPanel extends JPanel {
        private final List<InfoCard> cards = new ArrayList<>();
        private String errorMessage;

        public SystemInfoPanel() {
            setOpaque(false);
            setLayout(new GridBagLayout());
            setDoubleBuffered(true);
            initializeCards();
        }

        private void initializeCards() {
            String[] categories = {
                    "👤 User", "💻 System", "🔧 Java", "🌐 Network",
                    "⚡ Processor", "🎮 Graphics", "💾 Memory", "💿 Storage", "🖥️ Display"
            };

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;

            for (int i = 0; i < categories.length; i++) {
                InfoCard card = new InfoCard(categories[i]);
                cards.add(card);

                gbc.gridx = i % 2;
                gbc.gridy = i / 2;
                add(card, gbc);
            }
        }

        public void updateInfo(SystemInfo info) {
            errorMessage = null;

            cards.get(0).setContent(
                    "Username: " + info.userName,
                    "Computer Name: " + info.computerName
            );

            cards.get(1).setContent(
                    "OS: " + info.osName,
                    "Version: " + info.osVersion,
                    "Architecture: " + info.osArch
            );

            cards.get(2).setContent(
                    "Java Version: " + info.javaVersion,
                    "Vendor: " + info.javaVendor
            );

            cards.get(3).setContent(
                    "Local IP: " + info.localIp,
                    "Public IP: " + info.publicIp
            );

            cards.get(4).setContent(
                    "Model: " + info.cpuModel,
                    "Cores: " + info.processorCount,
                    "CPU Load: " + String.format("%.1f%%", info.cpuLoad)
            );

            cards.get(5).setContent(
                    "GPU Model: " + info.gpuModel,
                    "Video Memory: " + info.videoMemory
            );

            cards.get(6).setContent(
                    "Total Memory: " + formatBytes(info.totalMemory),
                    "Used: " + formatBytes(info.usedMemory),
                    "Free: " + formatBytes(info.freeMemory)
            );

            cards.get(7).setContent(
                    "Total Storage: " + formatBytes(info.totalStorage),
                    "Used: " + formatBytes(info.usedStorage),
                    "Free: " + formatBytes(info.freeStorage)
            );

            cards.get(8).setContent(
                    "Display Count: " + info.displayCount,
                    "Resolution: " + info.screenWidth + "×" + info.screenHeight,
                    "Refresh Rate: " + (info.refreshRate == -1 ? "Unknown" : info.refreshRate + " Hz")
            );

            repaint();
        }

        public void showError(String message) {
            this.errorMessage = message;
            for (InfoCard card : cards) {
                card.showError();
            }
            repaint();
        }

        private static String formatBytes(long bytes) {
            if (bytes < 0) return "Unknown";
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private class InfoCard extends JPanel {
        private String title;
        private List<String> content = new ArrayList<>();
        private boolean isError = false;
        private float hoverAlpha = 0f;
        private Timer hoverTimer;

        public InfoCard(String title) {
            this.title = title;
            setOpaque(false);
            setPreferredSize(new Dimension(400, 150));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setDoubleBuffered(true);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    startHoverAnimation(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    startHoverAnimation(false);
                }
            });
        }

        private void startHoverAnimation(boolean entering) {
            if (hoverTimer != null) hoverTimer.stop();

            hoverTimer = new Timer(20, e -> {
                if (entering && hoverAlpha < 1f) {
                    hoverAlpha += 0.1f;
                    if (hoverAlpha > 1f) hoverAlpha = 1f;
                } else if (!entering && hoverAlpha > 0f) {
                    hoverAlpha -= 0.1f;
                    if (hoverAlpha < 0f) hoverAlpha = 0f;
                } else {
                    hoverTimer.stop();
                    return;
                }
                repaint();
            });
            hoverTimer.start();
        }

        public void setContent(String... lines) {
            this.content.clear();
            for (String line : lines) {
                this.content.add(line);
            }
            this.isError = false;
            repaint();
        }

        public void showError() {
            this.content.clear();
            this.content.add("Error loading data");
            this.isError = true;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            Color bgColor = isError ?
                    new Color(ERROR_RED.getRed(), ERROR_RED.getGreen(), ERROR_RED.getBlue(), 20) :
                    new Color(255, 255, 255, (int)(15 + 10 * hoverAlpha));

            g2d.setColor(bgColor);
            g2d.fillRoundRect(0, 0, width, height, 20, 20);

            Color borderColor = isError ? ERROR_RED :
                    new Color(255, 255, 255, (int)(50 + 30 * hoverAlpha));
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(1, 1, width-2, height-2, 20, 20);

            g2d.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
            g2d.setColor(isError ? ERROR_RED : TEXT_PRIMARY);
            g2d.drawString(title, 20, 30);

            g2d.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            g2d.setColor(isError ? new Color(ERROR_RED.getRed(), ERROR_RED.getGreen(), ERROR_RED.getBlue(), 200) : TEXT_SECONDARY);

            int y = 55;
            for (String line : content) {
                if (y > height - 20) break;
                g2d.drawString(line, 20, y);
                y += 20;
            }

            g2d.dispose();
        }
    }

    private class StatusPanel extends JPanel {
        enum StatusType { SUCCESS, ERROR, WARNING, LOADING }

        private String statusText = "Ready";
        private StatusType statusType = StatusType.SUCCESS;
        private Timer blinkTimer;
        private boolean isBlinking = false;

        public StatusPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 30));
            setDoubleBuffered(true);
        }

        public void setStatus(String text, StatusType type) {
            this.statusText = text;
            this.statusType = type;

            if (type == StatusType.LOADING) {
                startBlinking();
            } else {
                stopBlinking();
            }

            repaint();
        }

        private void startBlinking() {
            stopBlinking();
            blinkTimer = new Timer(500, e -> {
                isBlinking = !isBlinking;
                repaint();
            });
            blinkTimer.start();
        }

        private void stopBlinking() {
            if (blinkTimer != null) {
                blinkTimer.stop();
                isBlinking = false;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color statusColor;
            String statusIcon;

            switch (statusType) {
                case SUCCESS:
                    statusColor = SUCCESS_GREEN;
                    statusIcon = "✓";
                    break;
                case ERROR:
                    statusColor = ERROR_RED;
                    statusIcon = "✗";
                    break;
                case WARNING:
                    statusColor = WARNING_ORANGE;
                    statusIcon = "⚠";
                    break;
                case LOADING:
                    statusColor = ACCENT_BLUE;
                    statusIcon = "⏳";
                    break;
                default:
                    statusColor = TEXT_SECONDARY;
                    statusIcon = "ℹ";
            }

            g2d.setColor(new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(),
                    isBlinking ? 100 : 180));
            g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

            g2d.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
            g2d.setColor(TEXT_PRIMARY);

            int iconWidth = g2d.getFontMetrics().stringWidth(statusIcon);
            g2d.drawString(statusIcon, 10, getHeight() - 10);
            g2d.drawString(statusText, 30, getHeight() - 10);

            g2d.dispose();
        }
    }

    private class ModernButton extends JButton {
        private Color baseColor;
        private float hoverAlpha = 0f;
        private Timer hoverTimer;

        public ModernButton(String text, Color baseColor) {
            super(text);
            this.baseColor = baseColor;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setForeground(TEXT_PRIMARY);
            setFont(new Font("Segoe UI Emoji", Font.BOLD, 14));
            setPreferredSize(new Dimension(150, 40));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    startHoverAnimation(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    startHoverAnimation(false);
                }
            });
        }

        private void startHoverAnimation(boolean entering) {
            if (hoverTimer != null) hoverTimer.stop();

            hoverTimer = new Timer(20, e -> {
                if (entering && hoverAlpha < 1f) {
                    hoverAlpha += 0.1f;
                    if (hoverAlpha > 1f) hoverAlpha = 1f;
                } else if (!entering && hoverAlpha > 0f) {
                    hoverAlpha -= 0.1f;
                    if (hoverAlpha < 0f) hoverAlpha = 0f;
                } else {
                    hoverTimer.stop();
                    return;
                }
                repaint();
            });
            hoverTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            Color bgColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                    (int)(180 + 50 * hoverAlpha));
            g2d.setColor(bgColor);
            g2d.fillRoundRect(0, 0, width, height, 15, 15);

            Color borderColor = new Color(255, 255, 255, (int)(100 + 50 * hoverAlpha));
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(1, 1, width-2, height-2, 15, 15);

            g2d.setFont(getFont());
            g2d.setColor(getForeground());
            FontMetrics fm = g2d.getFontMetrics();
            String text = getText();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent() - fm.getDescent();
            int x = (width - textWidth) / 2;
            int y = (height + textHeight) / 2 + 2;
            g2d.drawString(text, x, y);

            g2d.dispose();
        }
    }

    private class CustomScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = ACCENT_BLUE;
            thumbHighlightColor = new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 200);
            thumbDarkShadowColor = new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 100);
            trackColor = PRIMARY_DARK;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(trackColor);
            g2d.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            g2d.dispose();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(thumbColor);
            g2d.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width - 2, thumbBounds.height - 2, 10, 10);
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width - 2, thumbBounds.height - 2, 10, 10);
            g2d.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main gui = new Main();
            gui.setVisible(true);
        });
    }
}