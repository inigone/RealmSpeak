package com.robin.magic_realm.RealmSpeak.update;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.*;

/**
 * Modal dialog for the full update flow:
 *   1. "Checking…" spinner
 *   2. Update-available details + buttons
 *   3. Download progress bar
 *   4. "Restarting…" label then System.exit
 *
 * All network/file I/O runs on a background thread; UI updates hop to the EDT.
 */
public class UpdateDialog extends JDialog {

	private final UpdateSource source;
	private final JLabel statusLabel = new JLabel("Checking for updates…", SwingConstants.CENTER);
	private final JTextArea releaseNotes = new JTextArea(8, 50);
	private final JScrollPane notesScroll = new JScrollPane(releaseNotes);
	private final JProgressBar progressBar = new JProgressBar(0, 100);
	private final JButton updateButton = new JButton("Update & Restart");
	private final JButton viewButton = new JButton("View on GitHub");
	private final JButton cancelButton = new JButton("Cancel");
	private final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
	private final JPanel centerPanel = new JPanel(new BorderLayout(0, 8));

	private UpdateInfo pendingUpdate;
	private String releasePageUrl;

	public UpdateDialog(Frame owner, UpdateSource source) {
		super(owner, "Check for Updates (" + source.getSourceName() + ")", true);
		this.source = source;
		buildUI();
		showChecking();
		startCheckThread();
	}

	private void buildUI() {
		releaseNotes.setEditable(false);
		releaseNotes.setLineWrap(true);
		releaseNotes.setWrapStyleWord(true);
		releaseNotes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

		progressBar.setStringPainted(true);
		progressBar.setVisible(false);

		centerPanel.setBorder(new EmptyBorder(8, 12, 8, 12));
		centerPanel.add(statusLabel, BorderLayout.NORTH);
		centerPanel.add(notesScroll, BorderLayout.CENTER);
		centerPanel.add(progressBar, BorderLayout.SOUTH);
		notesScroll.setVisible(false);

		updateButton.addActionListener(this::onUpdate);
		viewButton.addActionListener(e -> {
			if (releasePageUrl != null) {
				try {
					Desktop.getDesktop().browse(java.net.URI.create(releasePageUrl));
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "Could not open browser: " + ex.getMessage());
				}
			}
		});
		cancelButton.addActionListener(e -> dispose());

		buttonPanel.add(viewButton);
		buttonPanel.add(updateButton);
		buttonPanel.add(cancelButton);

		setLayout(new BorderLayout());
		add(centerPanel, BorderLayout.CENTER);
		add(buttonPanel, BorderLayout.SOUTH);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
		setLocationRelativeTo(getOwner());
	}

	private void showChecking() {
		statusLabel.setText("Checking for updates…");
		notesScroll.setVisible(false);
		progressBar.setVisible(false);
		updateButton.setVisible(false);
		viewButton.setVisible(false);
		cancelButton.setText("Cancel");
		pack();
	}

	private void startCheckThread() {
		String currentTag = AppUpdater.readCurrentTag();
		new Thread(() -> {
			try {
				java.util.Optional<UpdateInfo> result = source.checkForUpdate(currentTag);
				SwingUtilities.invokeLater(() -> {
					if (result.isPresent()) {
						showUpdateAvailable(currentTag, result.get());
					} else {
						showUpToDate(currentTag);
					}
				});
			} catch (IOException ex) {
				SwingUtilities.invokeLater(() -> showError(ex.getMessage()));
			}
		}, "rs-update-check").start();
	}

	private void showUpToDate(String currentTag) {
		statusLabel.setText("You are up to date. (version: " + currentTag + ")");
		notesScroll.setVisible(false);
		progressBar.setVisible(false);
		updateButton.setVisible(false);
		viewButton.setVisible(false);
		cancelButton.setText("Close");
		pack();
	}

	private void showUpdateAvailable(String currentTag, UpdateInfo info) {
		pendingUpdate = info;
		releasePageUrl = info.releasePageUrl;
		statusLabel.setText("<html>Update available: <b>" + info.tag + "</b> &nbsp; (current: " + currentTag + ")</html>");
		releaseNotes.setText(info.releaseNotes.isBlank() ? "(no release notes)" : info.releaseNotes);
		releaseNotes.setCaretPosition(0);
		notesScroll.setVisible(true);
		progressBar.setVisible(false);
		updateButton.setVisible(true);
		viewButton.setVisible(true);
		cancelButton.setText("Cancel");
		pack();
	}

	private void showError(String message) {
		statusLabel.setText("<html><font color='red'>Error: " + message + "</font></html>");
		notesScroll.setVisible(false);
		progressBar.setVisible(false);
		updateButton.setVisible(false);
		viewButton.setVisible(false);
		cancelButton.setText("Close");
		pack();
	}

	private void onUpdate(ActionEvent e) {
		if (pendingUpdate == null) return;
		updateButton.setEnabled(false);
		viewButton.setEnabled(false);
		cancelButton.setEnabled(false);
		statusLabel.setText("Downloading " + pendingUpdate.tag + "…");
		notesScroll.setVisible(false);
		progressBar.setValue(0);
		progressBar.setString("0%");
		progressBar.setVisible(true);
		pack();

		UpdateInfo info = pendingUpdate;
		new Thread(() -> {
			try {
				Path tempZip = Files.createTempFile("rs_update_", ".zip");
				AppUpdater.download(info.downloadUrl, tempZip, pct -> SwingUtilities.invokeLater(() -> {
					if (pct < 0) {
						progressBar.setIndeterminate(true);
						progressBar.setString("Downloading…");
					} else {
						progressBar.setIndeterminate(false);
						progressBar.setValue(pct);
						progressBar.setString(pct + "%");
					}
				}));
				SwingUtilities.invokeLater(() -> {
					progressBar.setIndeterminate(false);
					progressBar.setValue(100);
					progressBar.setString("Installing…");
					statusLabel.setText("Installing…");
				});
				Path installDir = AppUpdater.getInstallDir();
				SwingUtilities.invokeLater(() -> {
					statusLabel.setText("Restarting RealmSpeak…");
					progressBar.setVisible(false);
					pack();
				});
				try {
					AppUpdater.installAndRestart(tempZip, installDir);
				} catch (IOException ex2) {
					SwingUtilities.invokeLater(() -> {
						showError("Restart failed: " + ex2.getMessage());
						cancelButton.setEnabled(true);
					});
				}
			} catch (IOException ex) {
				SwingUtilities.invokeLater(() -> {
					showError("Update failed: " + ex.getMessage());
					cancelButton.setEnabled(true);
				});
			}
		}, "rs-update-install").start();
	}

	/** Convenience: create the inigone update source and show the dialog. */
	public static void showInigoneUpdateDialog(Frame owner) {
		UpdateSource src = new GitHubUpdateSource(
				"inigone", "RealmSpeak", "inigone", "RealmSpeak\\d.*\\.zip");
		UpdateDialog dlg = new UpdateDialog(owner, src);
		dlg.setVisible(true);
	}
}
