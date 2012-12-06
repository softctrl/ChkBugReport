/*
 * Copyright (C) 2011 Sony Ericsson Mobile Communications AB
 * Copyright (C) 2012 Sony Mobile Communications AB
 *
 * This file is part of ChkBugReport.
 *
 * ChkBugReport is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * ChkBugReport is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ChkBugReport.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sonyericsson.chkbugreport;

import com.sonyericsson.chkbugreport.settings.BoolSetting;
import com.sonyericsson.chkbugreport.settings.Setting;
import com.sonyericsson.chkbugreport.settings.Settings;

import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
/* package */ class Gui extends JFrame implements OutputListener, ActionListener {

    private JButton mBtnAdb;
    private JButton mBtnExec;
    private Main mMain;
    private JLabel mDropArea;
    private JLabel mStatus;
    private Plugin mAdbExt;
    private BugReportModule mMod;

    public Gui(Main main) {
        super("ChkBugReport - (C) 2012 Sony-Ericsson");

        // Change window/application icon
        try {
            setIconImage(ImageIO.read(getClass().getResourceAsStream("/app_icon.png")));
        } catch (IOException e) {
            // Ignore error
        }

        mMain = main;
        mMod = (BugReportModule) mMain.getModule();

        JTabbedPane tabs = new JTabbedPane();
        setContentPane(tabs);

        JPanel runPanel = new JPanel(new BorderLayout());
        tabs.addTab("Run", runPanel);
        JPanel runTB = new JPanel();
        runPanel.add(runTB, BorderLayout.NORTH);
        mBtnAdb = new JButton("Fetch from device");
        mBtnAdb.setEnabled(false);
        runTB.add(mBtnAdb);
        mBtnExec = new JButton("Process");
        mBtnExec.setEnabled(false);
        mBtnExec.addActionListener(this);
        runTB.add(mBtnExec);
        mDropArea = new JLabel("Drop a bugreport file here!", JLabel.CENTER);
        runPanel.add(mDropArea, BorderLayout.CENTER);
        mDropArea.setBorder(BorderFactory.createLoweredBevelBorder());
        mDropArea.setTransferHandler(new MyTransferHandler());
        mStatus = new JLabel("Ready.");
        runPanel.add(mStatus, BorderLayout.SOUTH);

        mAdbExt = mMod.getPlugin("AdbExtension");
        if (mAdbExt != null) {
            mBtnAdb.setEnabled(true);
            mBtnAdb.addActionListener(this);
        }

        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        tabs.addTab("Settings", settingsPanel);
        buildSettings(settingsPanel);

        setSize(640, 480);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void buildSettings(JPanel settingsPanel) {
        final Settings settings = mMain.getSettings();
        for (Setting setting : settings) {
            if (setting instanceof BoolSetting) {
                final BoolSetting bs = (BoolSetting) setting;
                final JCheckBox chk = new JCheckBox(setting.getDescription());
                settingsPanel.add(chk);
                chk.setSelected(bs.get());
                chk.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        bs.set(chk.isSelected());
                        settings.save();
                    }
                });
            }
        }

    }

    private void enableUI(boolean enable) {
        mDropArea.setEnabled(enable);
        if (mAdbExt != null) {
            mBtnAdb.setEnabled(enable);
        }
    }

    public void loadFile(final String path) {
        enableUI(false);
        new Thread() {

            @Override
            public void run() {
                mMod.addFile(path, null, false);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        enableUI(true);
                        mBtnExec.setEnabled(true);
                        mBtnAdb.setEnabled(false);
                    }
                });
            }

        }.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == mBtnAdb) {
            loadFile("adb://");
            return;
        }
        if (src == mBtnExec) {
            enableUI(false);
            new Thread() {

                @Override
                public void run() {
                    try {
                        mMod.generate();
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                enableUI(true);
                                mBtnExec.setEnabled(false);
                                mBtnAdb.setEnabled(false);
                            }
                        });
                    } catch (final IOException err) {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                JOptionPane.showMessageDialog(Gui.this,
                                        "Error processing file: " + err, "Error...",
                                        JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                }

            }.start();
            return;
        }

    }

    @Override
    public void onPrint(final int level, final int type, final String msg) {
        if (level <= 1) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mStatus.setText(msg);
                }
            });
        }
    }

    class MyTransferHandler extends TransferHandler {

        @Override
        public boolean importData(TransferSupport support) {
            DataFlavor selDf = null;
            for (DataFlavor df : support.getDataFlavors()) {
                if (df.isMimeTypeEqual("text/uri-list")) {
                    if ("java.lang.String".equals(df.getParameter("class"))) {
                        selDf = df;
                        break;
                    }
                }
            }
            if (selDf != null) {
                String path;
                try {
                    path = (String) support.getTransferable().getTransferData(selDf);
                    URL url = new URL(path);
                    if (url.getProtocol().equals("file")) {
                        loadFile(URLDecoder.decode(url.getPath(), "UTF-8"));
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
            for (DataFlavor df : transferFlavors) {
                if (df.isMimeTypeEqual("text/uri-list")) {
                    if ("java.lang.String".equals(df.getParameter("class"))) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
