package com.robin.magic_realm.RealmSpeak;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import javax.swing.*;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.attribute.*;
import com.robin.magic_realm.components.utility.*;
import com.robin.magic_realm.components.wrapper.*;

public class CharacterTabViewWarningsManager {

	static final Color CHIT_CRITICAL_TAB_COLOR = new Color(190, 55, 55);
	static final Color CHIT_WARNING_TAB_COLOR = new Color(210, 150, 40);
	static final Color RECORD_TAB_ALERT_COLOR = new Color(255, 215, 45);

	private final CharacterFrame frame;
	private javax.swing.Timer recordTabBlinkTimer;
	private boolean recordTabBlinkOn;
	private javax.swing.Timer yourTurnTabBlinkTimer;
	private boolean yourTurnTabBlinkOn;

	public CharacterTabViewWarningsManager(CharacterFrame frame) {
		this.frame = frame;
	}

	public void updateAll() {
		updateChitTab();
		updateInventoryTab();
		updateSpellsTab();
		updateDiscoveriesTab();
		updateExpansionTab();
		updateRelationshipTab();
		updateHirelingTab();
		updateNotesTab();
		updateQuestTab();
		updateChatTab();
		updateYourTurnTab();
		updateRecordTab();
	}

	public void stopRecordBlink() {
		if (recordTabBlinkTimer != null) {
			recordTabBlinkTimer.stop();
			recordTabBlinkTimer = null;
		}
	}

	public void stopYourTurnBlink() {
		if (yourTurnTabBlinkTimer != null) {
			yourTurnTabBlinkTimer.stop();
			yourTurnTabBlinkTimer = null;
		}
		yourTurnTabBlinkOn = false;
	}

	void updateYourTurnTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null) return;

		if (frame.turnPanel == null) {
			stopYourTurnBlink();
			return;
		}

		int yourTurnTab = tabs.indexOfComponent(frame.turnPanel);
		if (yourTurnTab < 0) {
			stopYourTurnBlink();
			return;
		}

		if (tabs.getSelectedIndex() == yourTurnTab) {
			if (yourTurnTabBlinkTimer != null) yourTurnTabBlinkTimer.stop();
			yourTurnTabBlinkOn = false;
			tabs.setBackgroundAt(yourTurnTab, CHIT_CRITICAL_TAB_COLOR);
			return;
		}

		if (yourTurnTabBlinkTimer == null) {
			yourTurnTabBlinkTimer = new javax.swing.Timer(600, event -> {
				if (frame.turnPanel == null) { stopYourTurnBlink(); return; }
				int t = tabs.indexOfComponent(frame.turnPanel);
				if (t < 0 || tabs.getSelectedIndex() == t) { updateYourTurnTab(); return; }
				yourTurnTabBlinkOn = !yourTurnTabBlinkOn;
				tabs.setBackgroundAt(t, yourTurnTabBlinkOn ? CHIT_CRITICAL_TAB_COLOR : null);
			});
		}
		if (!yourTurnTabBlinkTimer.isRunning()) yourTurnTabBlinkTimer.start();
	}

	public void updateRecordTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int recordTab = tabs.indexOfComponent(frame.getActionPanel());
		boolean awaitingActionInput = frame.gameHandler.getGame().isRecording()
			&& frame.character.isDoRecord()
			&& frame.actionPanel.getActionControlManager().getCurrentlyRecordingAction() == null
			&& frame.gameHandler.getGame().getGameStarted()
			&& !frame.gameHandler.getGame().isGameOver()
			&& !frame.gameHandler.getGame().getGameEnded()
			&& !frame.singleButtonManager.hasMandatoryShowing();
		if (!awaitingActionInput) {
			if (recordTabBlinkTimer != null) recordTabBlinkTimer.stop();
			recordTabBlinkOn = false;
			if (recordTab >= 0) {
				tabs.setBackgroundAt(recordTab, null);
				tabs.setToolTipTextAt(recordTab, "Record Actions");
			}
			return;
		}
		if (recordTab < 0) return;

		tabs.setToolTipTextAt(recordTab, "Awaiting action input - open Record Actions tab");
		if (tabs.getSelectedIndex() == recordTab) {
			if (recordTabBlinkTimer != null) recordTabBlinkTimer.stop();
			recordTabBlinkOn = false;
			tabs.setBackgroundAt(recordTab, RECORD_TAB_ALERT_COLOR);
			return;
		}
		if (recordTabBlinkTimer == null) {
			recordTabBlinkTimer = new javax.swing.Timer(600, event -> {
				int currentRecordTab = tabs.indexOfComponent(frame.getActionPanel());
				if (currentRecordTab < 0 || tabs.getSelectedIndex() == currentRecordTab
					|| !frame.gameHandler.getGame().isRecording() || !frame.character.isDoRecord()
					|| frame.singleButtonManager.hasMandatoryShowing()) {
					updateRecordTab();
					return;
				}
				recordTabBlinkOn = !recordTabBlinkOn;
				tabs.setBackgroundAt(currentRecordTab, recordTabBlinkOn ? RECORD_TAB_ALERT_COLOR : null);
			});
		}
		if (!recordTabBlinkTimer.isRunning()) recordTabBlinkTimer.start();
	}

	private void updateChitTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || !frame.character.isCharacter()) return;

		int chitTab = tabs.indexOfComponent(frame.getChitPanel());
		if (chitTab < 0) return;

		int woundedCount = frame.character.getWoundedChits().size();
		int fatiguedCount = frame.character.getFatiguedChits().size();
		boolean pendingWound = frame.character.getExtraWounds() > 0 || frame.character.hasDreadWound();
		if (woundedCount > 0 || pendingWound) {
			tabs.setBackgroundAt(chitTab, CHIT_CRITICAL_TAB_COLOR);
			String detail = pendingWound ? "Wound assignment required" : woundedCount + " wounded chit" + (woundedCount == 1 ? "" : "s");
			tabs.setToolTipTextAt(chitTab, detail + " - open Chits tab");
		}
		else if (fatiguedCount > 0) {
			tabs.setBackgroundAt(chitTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(chitTab, fatiguedCount + " fatigued chit" + (fatiguedCount == 1 ? "" : "s") + " - open Chits tab");
		}
		else {
			tabs.setBackgroundAt(chitTab, null);
			tabs.setToolTipTextAt(chitTab, "Toggle Chits View");
		}
	}

	private void updateInventoryTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null) return;

		int inventoryTab = tabs.indexOfComponent(frame.getInventoryObjectPanel());
		if (inventoryTab < 0) return;

		boolean[] active = new boolean[6];
		boolean[] available = new boolean[6];
		for (GameObject item : frame.character.getInventory()) {
			if (item.hasThisAttribute(Constants.DEAD) || item.hasThisAttribute(Constants.DESTROYED)) continue;
			RealmComponent rc = RealmComponent.getRealmComponent(item);
			if (rc == null) continue;

			int type = inventoryEquipmentType(rc);
			if (type < 0) continue;
			available[type] = true;
			active[type] = active[type] || rc.isActivated();
		}

		String[] names = {"weapon", "helmet", "breastplate", "shield", "helmet", "suit of armor"};
		StringBuilder missing = new StringBuilder();
		boolean missingWeapon = available[0] && !active[0];
		for (int i = 0; i < active.length; i++) {
			if (available[i] && !active[i]) {
				if (missing.length() > 0) missing.append(", ");
				missing.append(names[i]);
			}
		}

		if (missing.length() > 0) {
			tabs.setBackgroundAt(inventoryTab, missingWeapon ? CHIT_CRITICAL_TAB_COLOR : CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(inventoryTab, "Activate available " + missing + " - open Inventory tab");
		}
		else {
			tabs.setBackgroundAt(inventoryTab, null);
			tabs.setToolTipTextAt(inventoryTab, "Toggle Inventory View");
		}
	}

	private int inventoryEquipmentType(RealmComponent rc) {
		if (rc.isWeapon()) return 0;
		if (rc.getGameObject().hasThisAttribute(Constants.HELMET)) return 1;
		if (rc.getGameObject().hasThisAttribute(Constants.BREASTPLATE)) return 2;
		if (rc.getGameObject().hasThisAttribute(Constants.SHIELD)) return 3;
		if (rc.getGameObject().hasThisAttribute(Constants.SUIT_OF_ARMOR)) return 5;
		return -1;
	}

	private void updateSpellsTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int spellsTab = tabs.indexOfComponent(frame.getSpellsPanel());
		if (spellsTab < 0) return;

		boolean redTriggered = false;
		boolean yellowTriggered = false;
		for (GameObject spellObj : frame.character.getAllSpells()) {
			if (new SpellWrapper(spellObj).isAlive()) continue;
			String targetType = spellObj.getThisAttribute("spell");
			ColorMagic targetColor = ColorMagic.makeColorMagic(spellObj.getThisAttribute("magic_color"), true);
			boolean magicAvailable = spellsTabMagicTypeAvailable(targetType);
			boolean colorAvailable = spellsTabColorAvailable(targetColor);
			if (!magicAvailable && !colorAvailable
					&& spellsTabHasProspectiveMagicChit(targetType)
					&& spellsTabHasProspectiveColorChit(targetColor)) {
				redTriggered = true;
				break;
			}
			if (!yellowTriggered) {
				boolean yellowCase1 = magicAvailable && !colorAvailable && spellsTabHasProspectiveColorChit(targetColor);
				boolean yellowCase2 = colorAvailable && !magicAvailable && spellsTabHasProspectiveMagicChit(targetType);
				yellowTriggered = yellowCase1 || yellowCase2;
			}
		}

		if (redTriggered) {
			tabs.setBackgroundAt(spellsTab, CHIT_CRITICAL_TAB_COLOR);
			tabs.setToolTipTextAt(spellsTab, "Rest/heal chits to cast a spell - open Spells tab");
		} else if (yellowTriggered) {
			tabs.setBackgroundAt(spellsTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(spellsTab, "Rest/heal chits to cast a spell - open Spells tab");
		} else {
			tabs.setBackgroundAt(spellsTab, null);
			tabs.setToolTipTextAt(spellsTab, "Toggle Spells View");
		}
	}

	private boolean spellsTabMagicTypeAvailable(String targetType) {
		for (CharacterActionChitComponent chit : frame.character.getActiveMagicChits()) {
			if (!chit.getGameObject().hasThisAttribute(SpellWrapper.INCANTATION_TIE)
					&& targetType.equals(chit.getMagicType())) return true;
		}
		String dayKey = frame.character.getCurrentDayKey();
		for (GameObject treasure : frame.character.getActivatedTreasureObjects()) {
			if (treasure.hasThisAttribute(SpellWrapper.INCANTATION_TIE)) continue;
			if (TreasureCardComponent.readAvailableMagicTypes(dayKey, treasure).contains(targetType)) return true;
		}
		return false;
	}

	private boolean spellsTabColorAvailable(ColorMagic targetColor) {
		for (MagicChit chit : frame.character.getColorMagicChits()) {
			if (targetColor == null || chit.getColorMagic().sameColorAs(targetColor)) return true;
		}
		for (ColorMagic src : frame.character.getInfiniteColorSources()) {
			if (targetColor == null || src.sameColorAs(targetColor)) return true;
		}
		return false;
	}

	private boolean spellsTabHasProspectiveMagicChit(String targetType) {
		for (CharacterActionChitComponent chit : frame.character.getFatiguedChits()) {
			if ("MAGIC".equals(chit.getAction()) && targetType.equals(chit.getMagicType())
					&& !chit.getGameObject().hasThisAttribute(SpellWrapper.INCANTATION_TIE)) return true;
		}
		for (CharacterActionChitComponent chit : frame.character.getWoundedChits()) {
			if ("MAGIC".equals(chit.getAction()) && targetType.equals(chit.getMagicType())
					&& !chit.getGameObject().hasThisAttribute(SpellWrapper.INCANTATION_TIE)) return true;
		}
		return false;
	}

	private boolean spellsTabHasProspectiveColorChit(ColorMagic targetColor) {
		for (CharacterActionChitComponent chit : frame.character.getAllMagicChits()) {
			if (chit.getGameObject().hasThisAttribute(SpellWrapper.INCANTATION_TIE)) continue;
			if (chit.getMagicNumber() >= 6) continue;
			if (targetColor == null || chit.compatibleWith(targetColor)) return true;
		}
		return false;
	}

	private void updateDiscoveriesTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int discTab = tabs.indexOfComponent(frame.getDiscoveriesPanel());
		if (discTab < 0) return;

		TileLocation loc = frame.character.getCurrentLocation();
		if (loc == null || loc.clearing == null || loc.tile == null) {
			tabs.setBackgroundAt(discTab, null);
			tabs.setToolTipTextAt(discTab, "Toggle Discoveries View");
			return;
		}

		boolean othersPresent = false;
		for (RealmComponent rc : loc.clearing.getClearingComponents()) {
			if (rc.isCharacter() && rc.getGameObject().getId() != frame.character.getGameObject().getId()) {
				othersPresent = true;
				break;
			}
		}

		if (othersPresent && discoveriesTabHasLocalDiscovery(loc)) {
			tabs.setBackgroundAt(discTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(discTab, "You have discoveries which others in this clearing may not know - open Discoveries tab");
		} else {
			tabs.setBackgroundAt(discTab, null);
			tabs.setToolTipTextAt(discTab, "Toggle Discoveries View");
		}
	}

	private boolean discoveriesTabHasLocalDiscovery(TileLocation loc) {
		for (PathDetail path : loc.tile.getHiddenPaths(true)) {
			if (path.getFrom() == loc.clearing || path.getTo() == loc.clearing) {
				if (frame.character.hasHiddenPathDiscovery(path.getFullPathKey())) return true;
			}
		}
		for (PathDetail path : loc.tile.getSecretPassages(true)) {
			if (path.getFrom() == loc.clearing || path.getTo() == loc.clearing) {
				if (frame.character.hasSecretPassageDiscovery(path.getFullPathKey())) return true;
			}
		}
		for (RealmComponent rc : loc.clearing.getClearingComponents()) {
			if (rc.isCacheChit()) continue;
			if (rc.isRedSpecial() && frame.character.hasOtherChitDiscovery(rc.getGameObject().getName())) return true;
			if (rc.isTreasureLocation() && rc.getGameObject().hasThisAttribute("discovery")
					&& frame.character.hasTreasureLocationDiscovery(rc.getGameObject().getName())) return true;
		}
		return false;
	}

	private void updateRelationshipTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int relTab = tabs.indexOfComponent(frame.getRelationshipPanel());
		if (relTab < 0) return;

		TileLocation loc = frame.character.getCurrentLocation();
		if (loc == null || loc.clearing == null) {
			tabs.setBackgroundAt(relTab, null);
			tabs.setToolTipTextAt(relTab, "Toggle Native Relationships View");
			return;
		}

		int nsLeft = frame.character.getBasicPhases() + frame.character.getShelteredPhases();
		int sLeft = loc.clearing.isCave() ? 0 : frame.character.getSunlightPhases();
		boolean ponyDoubles = false;
		GameObject steedObj = frame.character.getPonyGameObject();
		if (steedObj != null) {
			SteedChitComponent steed = (SteedChitComponent) RealmComponent.getRealmComponent(steedObj);
			if (steed.extraMove()) nsLeft++;
			else if (steed.doublesMove()) ponyDoubles = true;
		}
		int mountainCost = frame.character.getMountainMoveCost();

		record BfsState(ClearingDetail clearing, int ns, int s, boolean ponyFree) {}
		Set<BfsState> visited = new HashSet<>();
		Queue<BfsState> queue = new LinkedList<>();
		BfsState start = new BfsState(loc.clearing, nsLeft, sLeft, false);
		visited.add(start);
		queue.add(start);

		boolean enemyReachable = false;
		boolean unfriendlyReachable = false;

		outer:
		while (!queue.isEmpty()) {
			BfsState state = queue.poll();

			for (RealmComponent rc : state.clearing().getClearingComponents()) {
				if (!rc.isDenizen() || !rc.isNative()) continue;
				int rel = frame.character.getRelationship(rc.getGameObject());
				if (rel <= RelationshipType.ENEMY) { enemyReachable = true; break outer; }
				if (rel <= RelationshipType.UNFRIENDLY) unfriendlyReachable = true;
			}

			for (PathDetail path : state.clearing().getConnectedPaths()) {
				if (path.isHidden() && !frame.character.hasHiddenPathDiscovery(path.getFullPathKey())) continue;
				if (path.isSecret() && !frame.character.hasSecretPassageDiscovery(path.getFullPathKey())) continue;

				ClearingDetail dest = path.findConnection(state.clearing());
				if (dest == null) continue;

				boolean destIsCave = dest.isCave();
				boolean destIsWater = dest.isWater();
				int cost = dest.isMountain() ? mountainCost : 1;

				if (state.ponyFree()) {
					BfsState next = new BfsState(dest, state.ns(), state.s(), false);
					if (visited.add(next)) queue.add(next);
					continue;
				}

				if (state.ns() >= cost) {
					boolean newPonyFree = ponyDoubles && !state.clearing().isCave() && !destIsCave && !destIsWater;
					BfsState next = new BfsState(dest, state.ns() - cost, state.s(), newPonyFree);
					if (visited.add(next)) queue.add(next);
				}
				if (!destIsCave && state.s() >= cost) {
					boolean newPonyFree = ponyDoubles && !state.clearing().isCave() && !destIsWater;
					BfsState next = new BfsState(dest, state.ns(), state.s() - cost, newPonyFree);
					if (visited.add(next)) queue.add(next);
				}
			}
		}

		if (enemyReachable) {
			tabs.setBackgroundAt(relTab, CHIT_CRITICAL_TAB_COLOR);
			tabs.setToolTipTextAt(relTab, "Enemy natives within a day's move - open Native Relationships tab");
		} else if (unfriendlyReachable) {
			tabs.setBackgroundAt(relTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(relTab, "Unfriendly natives within a day's move - open Native Relationships tab");
		} else {
			tabs.setBackgroundAt(relTab, null);
			tabs.setToolTipTextAt(relTab, "Toggle Native Relationships View");
		}
	}

	private void updateChatTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		CharacterChatPanel chatPanel = frame.getChatPanel();
		int chatTab = tabs.indexOfComponent(chatPanel);
		if (chatTab < 0) return;

		int lastLineDayKey = chatPanel.lastLineDayKey;
		if (lastLineDayKey < 0) {
			tabs.setBackgroundAt(chatTab, null);
			tabs.setToolTipTextAt(chatTab, "Toggle Chat View");
			return;
		}

		GameWrapper game = GameWrapper.findGame(frame.character.getGameObject().getGameData());
		int currentDayKey = (game.getMonth() - 1) * RealmCalendar.DAYS_IN_A_MONTH + game.getDay();
		int diff = currentDayKey - lastLineDayKey;

		if (diff == 0) {
			tabs.setBackgroundAt(chatTab, CHIT_CRITICAL_TAB_COLOR);
			tabs.setToolTipTextAt(chatTab, "Chat posted today - open Chat tab");
		} else if (diff <= 2) {
			tabs.setBackgroundAt(chatTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(chatTab, "Recent chat activity - open Chat tab");
		} else {
			tabs.setBackgroundAt(chatTab, null);
			tabs.setToolTipTextAt(chatTab, "Toggle Chat View");
		}
	}

	private void updateQuestTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int questTab = tabs.indexOfComponent(frame.getQuestPanel());
		if (questTab < 0) return;

		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(frame.character.getGameObject().getGameData());
		if (!hostPrefs.isUsingQuestCards() && !hostPrefs.isUsingGuildQuests()) {
			tabs.setBackgroundAt(questTab, null);
			tabs.setToolTipTextAt(questTab, "Toggle Quest View");
			return;
		}

		int emptySlots = frame.character.getQuestSlotCount(hostPrefs) - frame.character.getUnfinishedNotAllPlayQuestCount();
		if (emptySlots > 0) {
			tabs.setBackgroundAt(questTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(questTab, emptySlots + " empty quest slot" + (emptySlots == 1 ? "" : "s") + " - open Quest tab");
		} else {
			tabs.setBackgroundAt(questTab, null);
			tabs.setToolTipTextAt(questTab, "Toggle Quest View");
		}
	}

	private void updateNotesTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int notesTab = tabs.indexOfComponent(frame.getNotesPanel());
		if (notesTab < 0) return;

		GameWrapper game = GameWrapper.findGame(frame.character.getGameObject().getGameData());
		int currentMonth = game.getMonth();
		int currentDay = game.getDay();

		boolean hasNewNote = false;
		for (Note note : frame.character.getNotes()) {
			if (note.getMonth() == currentMonth && note.getDay() == currentDay) {
				hasNewNote = true;
				break;
			}
		}

		if (hasNewNote) {
			tabs.setBackgroundAt(notesTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(notesTab, "New notes added today - open Character Notes tab");
		} else {
			tabs.setBackgroundAt(notesTab, null);
			tabs.setToolTipTextAt(notesTab, "Toggle Character Notes View");
		}
	}

	private void updateHirelingTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int hireTab = tabs.indexOfComponent(frame.getHirelingPanel());
		if (hireTab < 0) return;

		ArrayList<RealmComponent> hirelings = frame.character.getAllHirelings();
		if (hirelings.isEmpty()) {
			tabs.setBackgroundAt(hireTab, null);
			tabs.setToolTipTextAt(hireTab, "Toggle Hirelings View");
			return;
		}

		TileLocation loc = frame.character.getCurrentLocation();
		ClearingDetail clearing = (loc != null) ? loc.clearing : null;

		boolean hasUnassigned = false;
		boolean hasOneDayLeft = false;
		boolean hasThreeDaysLeft = false;

		for (RealmComponent rc : hirelings) {
			if (rc.getGameObject().hasThisAttribute(Constants.COMPANION)) continue;
			int term = rc.getTermOfHire();
			if (term == 1) hasOneDayLeft = true;
			else if (term <= 3) hasThreeDaysLeft = true;

			if (!hasUnassigned && clearing != null
					&& rc.getGameObject().getHeldBy() == null
					&& clearing.getClearingComponents(false).contains(rc)) {
				hasUnassigned = true;
			}
		}

		if (hasOneDayLeft || hasUnassigned) {
			tabs.setBackgroundAt(hireTab, CHIT_CRITICAL_TAB_COLOR);
			String tip;
			if (hasUnassigned && hasOneDayLeft) tip = "Unassigned hireling in clearing; contract expiring tomorrow - open Hirelings tab";
			else if (hasUnassigned) tip = "Unassigned hireling in current clearing - open Hirelings tab";
			else tip = "Hireling contract expiring tomorrow - open Hirelings tab";
			tabs.setToolTipTextAt(hireTab, tip);
		} else if (hasThreeDaysLeft) {
			tabs.setBackgroundAt(hireTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(hireTab, "Hireling contract expiring soon - open Hirelings tab");
		} else {
			tabs.setBackgroundAt(hireTab, null);
			tabs.setToolTipTextAt(hireTab, "Toggle Hirelings View");
		}
	}

	private void updateExpansionTab() {
		JTabbedPane tabs = frame.tabs;
		if (tabs == null || frame.gameHandler.getGame() == null) return;

		int expTab = tabs.indexOfComponent(frame.getExpansionOnePanel());
		if (expTab < 0) return;

		TileLocation loc = frame.character.getCurrentLocation();
		if (loc == null || loc.clearing == null) {
			tabs.setBackgroundAt(expTab, null);
			tabs.setToolTipTextAt(expTab, "Toggle Expansion View");
			return;
		}

		boolean othersPresent = false;
		for (RealmComponent rc : loc.clearing.getClearingComponents()) {
			if (rc.isCharacter() && rc.getGameObject().getId() != frame.character.getGameObject().getId()) {
				othersPresent = true;
				break;
			}
		}

		boolean hasGateDiscovery = false;
		if (othersPresent) {
			for (RealmComponent rc : loc.clearing.getClearingComponents()) {
				if (rc.isGate() && frame.character.hasOtherChitDiscovery(rc.getGameObject().getName())) {
					hasGateDiscovery = true;
					break;
				}
			}
		}

		if (hasGateDiscovery) {
			tabs.setBackgroundAt(expTab, CHIT_WARNING_TAB_COLOR);
			tabs.setToolTipTextAt(expTab, "You have discoveries which others in this clearing may not know - open Expansion tab");
		} else {
			tabs.setBackgroundAt(expTab, null);
			tabs.setToolTipTextAt(expTab, "Toggle Expansion View");
		}
	}
}
