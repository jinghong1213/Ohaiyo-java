package com.dailyresume.ui;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * A short-lived, undecorated greeting window that pops before the launcher.
 *
 * <p>By default we render an ASCII cat that blinks across four frames so it
 * works on every machine without any asset. If {@code assets/cat.gif} exists
 * relative to the working directory, we load it instead — Swing's
 * {@link ImageIcon} animates GIFs natively when displayed on a JLabel.
 *
 * <p>The window:
 * <ul>
 *   <li>has no titlebar ({@link JWindow})</li>
 *   <li>centers on the screen</li>
 *   <li>floats above other windows</li>
 *   <li>dismisses on click</li>
 *   <li>auto-closes after {@code durationMs}</li>
 * </ul>
 *
 * <p>When the splash closes for any reason (timer, click, dispose), it
 * invokes the {@code onClose} runnable — that's how Launcher chains into
 * building the main {@link LauncherFrame}.
 */
public class CatSplash extends JWindow {

    // ---- look & feel ----------------------------------------------------
    private static final Color CREAM   = new Color(0xFFF6E5);
    private static final Color BORDER  = new Color(0xF0CFA0);
    private static final Color BRAND   = new Color(0x3A2E2A);
    private static final Color SUBTLE  = new Color(0x7A6657);
    private static final Color CAT     = new Color(0x5A3E2E);

    /** Four pure-ASCII frames so rendering is identical everywhere. */
    private static final String[] CAT_FRAMES = {
            "( =^.^= )",
            "( =-.-= )",
            "( =^.^= )",
            "( =^o^= )"
    };

    private final Runnable onClose;
    private final JLabel catLabel = new JLabel("", SwingConstants.CENTER);

    /** Index into CAT_FRAMES. Only used when we're in ASCII mode. */
    private int frameIdx = 0;

    /** True iff we successfully loaded an animated GIF (skip text cycling then). */
    private final boolean usingGif;

    /** Drives the ASCII frame cycling; null when we have a GIF. */
    private final Timer animTimer;

    /** Auto-close timer — fires once after {@code durationMs}. */
    private final Timer autoCloseTimer;

    /** Set true once closeSplash() runs, so onClose can't fire twice. */
    private boolean closed = false;

    public CatSplash(Runnable onClose, int durationMs, File gifFile) {
        this.onClose = onClose;

        // ----- root panel: vertical stack, soft border + padding -----
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(CREAM);
        root.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 2),
                new EmptyBorder(26, 24, 18, 24)
        ));

        // ----- cat (GIF if available, else ASCII) -----
        ImageIcon icon = null;
        if (gifFile != null && gifFile.isFile()) {
            ImageIcon tryIcon = new ImageIcon(gifFile.getAbsolutePath());
            // getIconWidth() returns -1 if the image is broken / unreadable.
            if (tryIcon.getIconWidth() > 0) {
                icon = tryIcon;
            }
        }

        if (icon != null) {
            usingGif = true;
            catLabel.setIcon(icon);
            // Make the splash dismiss when the GIF area is clicked, too.
        } else {
            usingGif = false;
            catLabel.setText(CAT_FRAMES[0]);
            catLabel.setFont(new Font("Consolas", Font.BOLD, 28));
            catLabel.setForeground(CAT);
        }
        catLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(catLabel);
        root.add(Box.createRigidArea(new Dimension(0, 6)));

        // ----- brand: "Oh" + italic "ai" + "yo!" using HTML inside one label -----
        JLabel brand = new JLabel(
                "<html>Oh<i><font color='#5b8def'>ai</font></i>yo!</html>",
                SwingConstants.CENTER);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 22));
        brand.setForeground(BRAND);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(brand);
        root.add(Box.createRigidArea(new Dimension(0, 6)));

        // ----- tagline -----
        JLabel tag = new JLabel("Welcome back. Let's pick up where you left off.");
        tag.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tag.setForeground(SUBTLE);
        tag.setAlignmentX(Component.CENTER_ALIGNMENT);
        root.add(tag);

        setContentPane(root);
        pack();
        // Lock in a comfortable minimum size even when content is short.
        setSize(Math.max(getWidth(), 360), Math.max(getHeight(), 220));
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        // ----- click-to-dismiss on every visible widget -----
        MouseAdapter dismiss = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { closeSplash(); }
        };
        root.addMouseListener(dismiss);
        catLabel.addMouseListener(dismiss);
        brand.addMouseListener(dismiss);
        tag.addMouseListener(dismiss);

        // ----- timers -----
        if (!usingGif) {
            // 240ms gives a "blinky-then-excited" rhythm; tune to taste.
            animTimer = new Timer(240, e -> {
                frameIdx = (frameIdx + 1) % CAT_FRAMES.length;
                catLabel.setText(CAT_FRAMES[frameIdx]);
            });
            animTimer.start();
        } else {
            animTimer = null; // ImageIcon drives its own GIF animation
        }
        autoCloseTimer = new Timer(durationMs, e -> closeSplash());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    private void closeSplash() {
        if (closed) return;
        closed = true;
        if (animTimer != null) animTimer.stop();
        autoCloseTimer.stop();
        setVisible(false);
        dispose();
        if (onClose != null) onClose.run();
    }
}
