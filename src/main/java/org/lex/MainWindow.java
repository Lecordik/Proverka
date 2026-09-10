package org.lex;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainWindow extends JFrame {


    static final Color BG          = new Color(18, 18, 24, 235);
    static final Color BG_HEADER   = new Color(255, 255, 255, 10);
    static final Color BORDER_CLR  = new Color(255, 255, 255, 20);
    static final Color TEXT_DIM    = new Color(255, 255, 255, 100);
    static final Color TEXT_BRIGHT = new Color(255, 255, 255, 210);
    static final Color COLOR_OK    = new Color(74, 222, 128);
    static final Color COLOR_ERR   = new Color(248, 113, 113);
    static final Color COLOR_IDLE  = new Color(255, 255, 255, 33);

    static Font unicodeFont(int style, int size) {
        String[] candidates = {
                "Apple Color Emoji", "Noto Sans", "DejaVu Sans",
                "Arial Unicode MS", "Segoe UI Symbol", "Dialog"
        };
        for (String name : candidates) {
            Font f = new Font(name, style, size);
            if (f.canDisplay('✓')) return f;
        }
        return new Font(Font.SANS_SERIF, style, size);
    }


    static Font textFont(int size) {
        String[] candidates = { "Helvetica Neue", "Segoe UI", "Ubuntu", "Dialog" };
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, size);
            if (!f.getFamily().equals("Dialog") || name.equals("Dialog")) return f;
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    private final IconPanel    iconPanel;
    private final JLabel       resultLabel;
    private final JLabel       saveStatusLabel;
    private final DetailsPanel detailsPanel;


    private final DatabaseManager db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-thread");
        t.setDaemon(true);
        return t;
    });


    private final StringBuilder scanBuffer = new StringBuilder();


    private Point dragStart;

    public MainWindow() {
        db = new DatabaseManager();

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(720, 500);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        RoundedPanel root = new RoundedPanel(16);
        root.setLayout(new BorderLayout());
        setContentPane(root);

        JPanel header = new HeaderPanel();
        root.add(header, BorderLayout.NORTH);

        JPanel center = new TransparentPanel(new BorderLayout());

        iconPanel = new IconPanel();
        iconPanel.setPreferredSize(new Dimension(720, 240));
        center.add(iconPanel, BorderLayout.CENTER);

        resultLabel = makeLabel("Отсканируйте КИЗ", 18, TEXT_DIM);
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
        resultLabel.setBorder(new EmptyBorder(0, 20, 6, 20));

        saveStatusLabel = makeLabel("", 10, new Color(255, 255, 255, 60));
        saveStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        saveStatusLabel.setBorder(new EmptyBorder(0, 20, 10, 20));

        JPanel bottomLabels = new TransparentPanel(new BorderLayout());
        bottomLabels.add(resultLabel, BorderLayout.NORTH);
        bottomLabels.add(saveStatusLabel, BorderLayout.SOUTH);
        center.add(bottomLabels, BorderLayout.SOUTH);

        root.add(center, BorderLayout.CENTER);

        detailsPanel = new DetailsPanel();
        detailsPanel.setVisible(false);
        root.add(detailsPanel, BorderLayout.SOUTH);

        header.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (!isMaximized()) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragStart.x,
                            loc.y + e.getY() - dragStart.y);
                }
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    if (!isActive()) return false;

                    if (e.getID() == KeyEvent.KEY_TYPED) {
                        char c = e.getKeyChar();
                        if (c == '\n' || c == '\r') {
                            String raw = scanBuffer.toString().trim();
                            scanBuffer.setLength(0);
                            if (!raw.isEmpty()) processBarcode(raw);
                        } else if (c != KeyEvent.CHAR_UNDEFINED) {
                            scanBuffer.append(c);
                        }
                        return true;
                    }

                    if (e.getID() == KeyEvent.KEY_PRESSED &&
                            e.getKeyCode() == KeyEvent.VK_ENTER) {
                        String raw = scanBuffer.toString().trim();
                        scanBuffer.setLength(0);
                        if (!raw.isEmpty()) processBarcode(raw);
                        return true;
                    }

                    return false;
                });
    }

    private boolean isMaximized() {
        return (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
    }

    private void processBarcode(String raw) {
        KizParser.ParseResult result = KizParser.parse(raw);

        SwingUtilities.invokeLater(() -> {
            if (result.valid) {
                iconPanel.setStatus(true);
                resultLabel.setText(result.productName);
                resultLabel.setForeground(COLOR_OK);
                detailsPanel.update(result);
                detailsPanel.setVisible(true);
            } else {
                iconPanel.setStatus(false);
                resultLabel.setText(result.errorMsg);
                resultLabel.setForeground(COLOR_ERR);
                detailsPanel.setVisible(false);
            }
            revalidate();
            repaint();
        });

        executor.submit(() -> {
            DatabaseManager.SaveResult sr = db.saveScan(raw, result);
            SwingUtilities.invokeLater(() -> {
                saveStatusLabel.setText(sr.toStatusString());
                Timer t = new Timer(2500, ev -> saveStatusLabel.setText(""));
                t.setRepeats(false);
                t.start();
            });
        });
    }

    static JLabel makeLabel(String text, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(textFont(size));
        l.setForeground(color);
        l.setOpaque(false);
        return l;
    }

    class HeaderPanel extends JPanel {
        HeaderPanel() {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.LEFT, 8, 14));
            setPreferredSize(new Dimension(720, 42));

            add(makeDotButton(new Color(255, 95, 87), () -> {
                db.close(); executor.shutdown(); dispose(); System.exit(0);
            }));
            add(makeDotButton(new Color(254, 188, 46), () ->
                    setExtendedState(getExtendedState() | ICONIFIED)));
            add(makeDotButton(new Color(40, 200, 64), () -> {
                if (isMaximized()) setExtendedState(NORMAL);
                else setExtendedState(MAXIMIZED_BOTH);
            }));

            JLabel title = makeLabel("Проверка КИЗ / Честный Знак", 11, TEXT_DIM);
            title.setBorder(new EmptyBorder(0, 10, 0, 0));
            add(title);

            JButton settings = new JButton("⚙");
            settings.setFont(textFont(15));
            settings.setForeground(TEXT_DIM);
            settings.setOpaque(false);
            settings.setContentAreaFilled(false);
            settings.setBorderPainted(false);
            settings.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            settings.addActionListener(e ->
                    new SettingsDialog(MainWindow.this, db).setVisible(true));

            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            spacer.setPreferredSize(new Dimension(720 - 220, 1));
            add(spacer);
            add(settings);
        }

        private JButton makeDotButton(Color color, Runnable action) {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillOval(0, 0, 14, 14);
                    g2.dispose();
                }
            };
            b.setPreferredSize(new Dimension(14, 14));
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> action.run());
            return b;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BG_HEADER);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() + 16, 16, 16);
            g2.dispose();
        }
    }

    static class IconPanel extends JPanel {
        private boolean valid     = false;
        private boolean hasResult = false;
        private float   animScale = 1f;
        private Timer   animTimer;

        IconPanel() { setOpaque(false); }

        void setStatus(boolean valid) {
            this.valid     = valid;
            this.hasResult = true;
            animScale = 0.6f;
            if (animTimer != null) animTimer.stop();
            animTimer = new Timer(16, null);
            animTimer.addActionListener(e -> {
                animScale = Math.min(1f, animScale + 0.05f);
                repaint();
                if (animScale >= 1f) animTimer.stop();
            });
            animTimer.start();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            int cx = getWidth()  / 2;
            int cy = getHeight() / 2;

            Color iconColor = !hasResult ? COLOR_IDLE
                    :  valid     ? COLOR_OK : COLOR_ERR;

            // Круг-свечение
            int r = (int)(Math.min(getWidth(), getHeight()) * 0.38 * animScale);
            g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(),
                    iconColor.getBlue(), 18));
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);
            g2.setColor(new Color(iconColor.getRed(), iconColor.getGreen(),
                    iconColor.getBlue(), 50));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            if (!hasResult) {
                g2.setColor(iconColor);
                g2.setStroke(new BasicStroke(2f));
                int ir = (int)(r * 0.55);
                g2.drawOval(cx - ir, cy - ir, ir * 2, ir * 2);
            } else {
                String symbol = valid ? "✓" : "✗";
                int fontSize  = (int)(Math.min(getWidth(), getHeight()) * 0.48 * animScale);
                fontSize = Math.max(fontSize, 24);
                Font f = unicodeFont(Font.PLAIN, fontSize);
                g2.setFont(f);
                g2.setColor(iconColor);

                FontMetrics fm = g2.getFontMetrics();
                int tx = cx - fm.stringWidth(symbol) / 2;
                int ty = cy + (fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(symbol, tx, ty);
            }

            g2.dispose();
        }
    }

    static class DetailsPanel extends JPanel {
        private final JLabel gtinVal, serialVal, verifyVal;

        DetailsPanel() {
            setOpaque(false);
            setBorder(new CompoundBorder(
                    new MatteBorder(1, 0, 0, 0, BORDER_CLR),
                    new EmptyBorder(16, 52, 20, 52)
            ));
            setLayout(new GridLayout(3, 2, 8, 8));

            Font labelFont = textFont(11);
            Font monoFont  = new Font(Font.MONOSPACED, Font.PLAIN, 13);

            JLabel l1 = new JLabel("GTIN");          l1.setFont(labelFont); l1.setForeground(TEXT_DIM); l1.setOpaque(false);
            JLabel l2 = new JLabel("Серийный");      l2.setFont(labelFont); l2.setForeground(TEXT_DIM); l2.setOpaque(false);
            JLabel l3 = new JLabel("Код верификации"); l3.setFont(labelFont); l3.setForeground(TEXT_DIM); l3.setOpaque(false);

            gtinVal   = new JLabel("—"); gtinVal.setFont(monoFont);   gtinVal.setForeground(TEXT_BRIGHT);   gtinVal.setOpaque(false);
            serialVal = new JLabel("—"); serialVal.setFont(monoFont); serialVal.setForeground(TEXT_BRIGHT); serialVal.setOpaque(false);
            verifyVal = new JLabel("—"); verifyVal.setFont(monoFont); verifyVal.setForeground(TEXT_BRIGHT); verifyVal.setOpaque(false);

            add(l1); add(gtinVal);
            add(l2); add(serialVal);
            add(l3); add(verifyVal);
        }

        void update(KizParser.ParseResult r) {
            gtinVal.setText(r.gtin != null ? r.gtin : "—");
            serialVal.setText(r.serial != null ? r.serial : "—");
            String v = "";
            if (r.verifyKey  != null) v += r.verifyKey;
            if (r.verifyCode != null) v += (v.isEmpty() ? "" : "  /  ")
                    + r.verifyCode.substring(0, Math.min(r.verifyCode.length(), 16)) + "…";
            verifyVal.setText(v.isEmpty() ? "—" : v);
        }
    }

    static class TransparentPanel extends JPanel {
        TransparentPanel(LayoutManager lm) { super(lm); setOpaque(false); }
    }

    static class RoundedPanel extends JPanel {
        private final int radius;
        RoundedPanel(int radius) { this.radius = radius; setOpaque(false); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
            g2.setColor(BORDER_CLR);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    radius * 2, radius * 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
