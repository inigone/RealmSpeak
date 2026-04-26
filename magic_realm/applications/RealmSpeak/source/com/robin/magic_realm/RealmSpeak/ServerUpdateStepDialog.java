package com.robin.magic_realm.RealmSpeak;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;

import com.robin.game.objects.GameObjectChange;
import com.robin.game.server.GameClient;

public class ServerUpdateStepDialog extends JDialog {

	private final GameClient client;
	private final Runnable onStep;
	private final JLabel batchCountLabel;
	private final JTextArea changeLog;

	public ServerUpdateStepDialog(Frame owner, GameClient client, Runnable onStep) {
		super(owner, "Server Updates Pending", true);
		this.client = client;
		this.onStep = onStep;

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setAlwaysOnTop(true);

		batchCountLabel = new JLabel();
		batchCountLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		changeLog = new JTextArea(10, 50);
		changeLog.setEditable(false);
		changeLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

		JButton stepButton = new JButton("Step");
		stepButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				ArrayList<GameObjectChange> batch = client.stepNextBatch();
				showBatch(batch);
				updateCount();
				if (onStep != null) onStep.run();
				if (!client.hasPendingBatches()) {
					dispose();
				}
			}
		});

		JButton flushButton = new JButton("Flush All");
		flushButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				client.flushAllBatches();
				if (onStep != null) onStep.run();
				dispose();
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(stepButton);
		buttons.add(flushButton);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(batchCountLabel, BorderLayout.NORTH);
		getContentPane().add(new JScrollPane(changeLog), BorderLayout.CENTER);
		getContentPane().add(buttons, BorderLayout.SOUTH);

		updateCount();
		pack();
		setLocationRelativeTo(owner);
	}

	private void updateCount() {
		int count = client.getPendingBatchCount();
		batchCountLabel.setText(count + " update batch" + (count == 1 ? "" : "es") + " remaining");
	}

	private void showBatch(ArrayList<GameObjectChange> batch) {
		if (batch == null) return;
		StringBuilder sb = new StringBuilder();
		for (GameObjectChange c : batch) {
			sb.append(c.toString()).append('\n');
		}
		changeLog.setText(sb.toString());
		changeLog.setCaretPosition(0);
	}

	public static void show(Frame owner, GameClient client, Runnable onStep) {
		if (!client.hasPendingBatches()) return;
		new ServerUpdateStepDialog(owner, client, onStep).setVisible(true);
	}
}
