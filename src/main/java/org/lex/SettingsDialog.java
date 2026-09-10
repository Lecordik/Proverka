package org.lex;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;

public class SettingsDialog extends JDialog {

    private final DatabaseManager db;
    private final JTextField  localPathField;
    private final JTextField  remoteHostField;
    private final JTextField  remoteDbField;
    private final JTextField  remoteUserField;
    private final JPasswordField remotePassField;
    private final JComboBox<String> dbTypeCombo;
    private final JLabel      remoteStatusLabel;
    private final JLabel      localInfoLabel;

    public SettingsDialog(JFrame parent, DatabaseManager db) {
        super(parent, "Настройки базы данных", true);
        this.db = db;

        setUndecorated(true);
        setSize(420, 480);
        setLocationRelativeTo(parent);
        setBackground(new Color(0, 0, 0, 0));

        MainWindow.RoundedPanel root = new MainWindow.RoundedPanel(14);
        root.setLayout(new BorderLayout());
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(14, 18, 14, 18));
        JLabel title = MainWindow.makeLabel("Настройки базы данных", 13, new Color(255,255,255,180));
        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Helvetica Neue", Font.PLAIN, 14));
        closeBtn.setForeground(new Color(255,255,255,80));
        closeBtn.setOpaque(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(title, BorderLayout.WEST);
        header.add(closeBtn, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);


        JPanel content = new MainWindow.TransparentPanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 22, 16, 22));


        content.add(sectionLabel("ЛОКАЛЬНАЯ БД (SQLite)"));
        content.add(Box.createVerticalStrut(6));

        localPathField = styledField(db.getLocalPath());
        JPanel localRow = fieldRow("Папка:", localPathField);

        JButton browseBtn = smallButton("…");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(db.getLocalPath());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                localPathField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        localRow.add(browseBtn);
        content.add(localRow);

        localInfoLabel = MainWindow.makeLabel("", 10, new Color(255,255,255,60));
        content.add(localInfoLabel);
        content.add(Box.createVerticalStrut(12));

        content.add(separator());
        content.add(Box.createVerticalStrut(12));

        content.add(sectionLabel("УДАЛЁННАЯ БД (PostgreSQL / MySQL)"));
        content.add(Box.createVerticalStrut(6));

        remoteHostField = styledField(db.getRemoteHost());
        remoteHostField.putClientProperty("JTextField.placeholderText", "192.168.1.100:5432");
        content.add(fieldRow("Хост:", remoteHostField));

        remoteDbField = styledField(db.getRemoteDb());
        content.add(fieldRow("База:", remoteDbField));

        remoteUserField = styledField(db.getRemoteUser());
        content.add(fieldRow("Логин:", remoteUserField));

        remotePassField = new JPasswordField();
        styleField(remotePassField);
        content.add(fieldRow("Пароль:", remotePassField));

        dbTypeCombo = new JComboBox<>(new String[]{"PostgreSQL", "MySQL"});
        dbTypeCombo.setSelectedItem(db.getRemoteType() == DatabaseManager.DbType.MYSQL ? "MySQL" : "PostgreSQL");
        dbTypeCombo.setBackground(new Color(50, 50, 60));
        dbTypeCombo.setForeground(new Color(255,255,255,200));
        dbTypeCombo.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        content.add(fieldRow("Тип:", dbTypeCombo));
        content.add(Box.createVerticalStrut(8));

        remoteStatusLabel = MainWindow.makeLabel("", 10, new Color(255,255,255,80));
        content.add(remoteStatusLabel);
        content.add(Box.createVerticalStrut(12));

        JPanel btns = new MainWindow.TransparentPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton testBtn = actionButton("Проверить соединение", new Color(255,255,255,20), new Color(255,255,255,150));
        testBtn.addActionListener(e -> {
            remoteStatusLabel.setText("Проверяю…");
            remoteStatusLabel.setForeground(new Color(255,255,255,80));
            applyToDb();
            new Thread(() -> {
                String result = db.testRemoteConnection();
                SwingUtilities.invokeLater(() -> {
                    remoteStatusLabel.setText(result);
                    remoteStatusLabel.setForeground(result.startsWith("✓") ? MainWindow.COLOR_OK : MainWindow.COLOR_ERR);
                });
            }, "db-test").start();
        });

        JButton saveBtn = actionButton("Сохранить", new Color(74,222,128,45), MainWindow.COLOR_OK);
        saveBtn.addActionListener(e -> {
            db.setLocalPath(localPathField.getText().trim());
            applyToDb();
            db.openLocalDb();
            localInfoLabel.setText("Сохранено ✓");
            dispose();
        });

        btns.add(testBtn);
        btns.add(saveBtn);
        content.add(btns);

        root.add(content, BorderLayout.CENTER);
    }

    private void applyToDb() {
        DatabaseManager.DbType type = "MySQL".equals(dbTypeCombo.getSelectedItem())
                ? DatabaseManager.DbType.MYSQL : DatabaseManager.DbType.POSTGRESQL;
        db.saveRemoteSettings(
                remoteHostField.getText().trim(),
                remoteDbField.getText().trim(),
                remoteUserField.getText().trim(),
                new String(remotePassField.getPassword()),
                type
        );
    }

    private JLabel sectionLabel(String text) {
        return MainWindow.makeLabel(text, 10, new Color(255,255,255,60));
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(255,255,255,20));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private JTextField styledField(String text) {
        JTextField f = new JTextField(text);
        styleField(f);
        return f;
    }

    private void styleField(JTextComponent f) {
        f.setBackground(new Color(255,255,255,18));
        f.setForeground(new Color(255,255,255,200));
        f.setCaretColor(new Color(255,255,255,200));
        f.setFont(new Font("Menlo", Font.PLAIN, 11));
        f.setBorder(new CompoundBorder(
            new LineBorder(new Color(255,255,255,25), 1, true),
            new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private JPanel fieldRow(String labelText, JComponent field) {
        JPanel row = new MainWindow.TransparentPanel(new BorderLayout(8, 0));
        row.setBorder(new EmptyBorder(0, 0, 6, 0));
        JLabel lbl = MainWindow.makeLabel(labelText, 11, new Color(255,255,255,110));
        lbl.setPreferredSize(new Dimension(80, 24));
        row.add(lbl, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Helvetica Neue", Font.PLAIN, 12));
        b.setForeground(new Color(255,255,255,150));
        b.setBackground(new Color(255,255,255,25));
        b.setBorder(new EmptyBorder(4, 10, 4, 10));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton actionButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(new Font("Helvetica Neue", Font.PLAIN, 11));
        b.setForeground(fg);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorder(new CompoundBorder(
            new LineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 76), 1, true),
            new EmptyBorder(6, 14, 6, 14)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
