package com.robin.magic_realm.RealmBattle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import com.robin.game.server.GameClient;
import com.robin.magic_realm.components.BattleChit;
import com.robin.magic_realm.components.CharacterChitComponent;
import com.robin.magic_realm.components.MonsterChitComponent;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;
import com.robin.magic_realm.components.wrapper.CombatWrapper;
import com.robin.magic_realm.components.wrapper.HostPrefWrapper;

/**
 * Works out which outcome lines belong to one combat sheet, without needing the sheet itself.
 * <p>
 * Kept separate from CombatSheet so the summary page can gather the lines for every participant at
 * once: building a CombatSheet runs updateLayout(), which can WRITE combat boxes through
 * placeInFirstCombatBox, and doing that from a paint would mutate game state.
 */
public class OutcomeLines {

	/** An attack listed as outgoing - aimed at something other than the row's defender. */
	public static final Color COLOR_OUTGOING = new Color(0,110,0);
	/** An attack listed as incoming - landing on the row's defender. */
	public static final Color COLOR_INCOMING = new Color(190,140,0);
	/** An attack that produced no outcome line anywhere, so it will not land. */
	public static final Color COLOR_NOT_LANDING = new Color(150,150,150);

	/**
	 * Every participant's outcome lines for one battle, plus how each attack is classified.  Shared
	 * so the summary pane and the round summary window colour the same attack the same way - the
	 * whole point of the numbers is being able to match the two views by eye.
	 */
	public static class BattleOutcomes {
		public final HashMap<String,ArrayList<AttackKillEstimate>> byParticipant = new HashMap<>();
		private final HashSet<String> listed = new HashSet<>();
		private final HashSet<String> outgoing = new HashSet<>();
		/** The colour this attack's order number gets in either view. */
		public Color colorFor(String attackKey) {
			if (!listed.contains(attackKey)) return COLOR_NOT_LANDING;
			return outgoing.contains(attackKey)?COLOR_OUTGOING:COLOR_INCOMING;
		}
	}
	/** Works out every participant's outcome lines once, and classifies every attack. */
	public static BattleOutcomes collectBattle(CombatFrame combatFrame,BattleModel model,HostPrefWrapper hostPrefs) {
		BattleOutcomes outcomes = new BattleOutcomes();
		if (combatFrame==null || model==null || hostPrefs==null) return outcomes;
		for (RealmComponent participant : combatFrame.getAllParticipants()) {
			OutcomeLines lines = new OutcomeLines(combatFrame,model,hostPrefs,participant,false);
			if (!lines.showOutcomeLines() || !lines.outcomesSettled()) continue;
			String defenderId = participant.getGameObject().getStringId();
			ArrayList<AttackKillEstimate> deduped = new ArrayList<>();
			HashSet<String> seen = new HashSet<>();
			for (AttackKillEstimate estimate : lines.getAll()) {
				// getAll concatenates three groups, so ONE attack can arrive twice - collapse on the
				// attack's own identity, which still keeps identical goblins' attacks apart
				if (!seen.add(estimate.getAttackKey())) continue;
				deduped.add(estimate);
				outcomes.listed.add(estimate.getAttackKey());
				if (!defenderId.equals(estimate.getTargetId())) {
					outcomes.outgoing.add(estimate.getAttackKey());
				}
			}
			outcomes.byParticipant.put(defenderId,deduped);
		}
		return outcomes;
	}

	private final CombatFrame combatFrame;
	private final BattleModel model;
	private final HostPrefWrapper hostPrefs;
	private final RealmComponent sheetOwner;
	private final boolean alwaysSecret;

	private BattleModel.AttackOrder attackOrderCache;

	public OutcomeLines(CombatFrame combatFrame,BattleModel model,HostPrefWrapper hostPrefs,RealmComponent sheetOwner,boolean alwaysSecret) {
		this.combatFrame = combatFrame;
		this.model = model;
		this.hostPrefs = hostPrefs;
		this.sheetOwner = sheetOwner;
		this.alwaysSecret = alwaysSecret;
	}
	/** Every outcome line this sheet owner's sheet would draw. */
	public ArrayList<AttackKillEstimate> getAll() {
		ArrayList<AttackKillEstimate> all = getAttackerEstimates(true,true);
		if (!sheetOwner.isCharacter()) {
			// Only a denizen sheet draws these as separate groups; on a character sheet the owner's
			// own attacks are already part of the attacker set
			all.addAll(getSheetOwnerEstimates());
			all.addAll(getSheetOwnerTargetEstimates());
		}
		return all;
	}
	/** Discards the cached attack order so a tactics change cannot leave it stale. */
	public void reset() {
		attackOrderCache = null;
	}
	/**
	 * Outcome lines only make sense once attacks and maneuvers are being placed, so nothing is
	 * reported before the POSITION step.
	 */
	public boolean showOutcomeLines() {
		return hostPrefs!=null && hostPrefs.hasPref(Constants.OPT_COMBAT_OUTCOME_PROBABILITIES)
				&& combatFrame!=null && combatFrame.getActionState()>=Constants.COMBAT_POSITIONING
				&& !alwaysSecret && model!=null && sheetOwner!=null
				// Before Tactics another player's sheet is their business; from Tactics on everything is public
				&& (outcomesSettled() || isLocallyControlled(sheetOwner));
	}
	/**
	 * Whether repositioning and change-of-tactics have already been applied.  From the TACTICS step
	 * on they have, so the combat boxes are final and only the outcome that will actually happen is
	 * worth showing - see AttackKillEstimate.
	 */
	public boolean outcomesSettled() {
		return combatFrame!=null && combatFrame.getActionState()>=Constants.COMBAT_TACTICS;
	}
	/** The roll titles are only tagged when the outcome probabilities option is on. */
	public boolean tagRollsWithAttackOrder() {
		return hostPrefs!=null && hostPrefs.hasPref(Constants.OPT_COMBAT_OUTCOME_PROBABILITIES)
				&& combatFrame!=null && model!=null;
	}
	/** Cached because it costs a full sort of every attack in the battle. */
	public BattleModel.AttackOrder attackOrder() {
		if (attackOrderCache==null) {
			attackOrderCache = model.getAttackOrder(combatFrame.getCurrentRound());
		}
		return attackOrderCache;
	}
	/** This attack's place in the resolution order, or 0 before the boxes are final. */
	private int attackOrderOf(BattleChit attacker) {
		if (!outcomesSettled()) return 0;
		return attackOrder().positionOf(attacker);
	}
	/**
	 * The player who commands this piece, or null when nobody does.  A character carries its own
	 * player name; a hired leader carries one too, but a rank-and-file hireling carries none and is
	 * commanded by whoever hired it.  Unhired denizens belong to no player at all.
	 */
	private static String controllingPlayer(RealmComponent rc) {
		if (rc==null) return null;
		String playerName = new CharacterWrapper(rc.getGameObject()).getPlayerName();
		if (playerName!=null) return playerName;
		RealmComponent owner = rc.getOwner();
		if (owner!=null) return new CharacterWrapper(owner.getGameObject()).getPlayerName();
		return null;
	}
	/**
	 * Whether this player commands the piece.  Placements are secret until the TACTICS step, and a
	 * hireling's placement is its OWNER'S secret - not just a character's - so this must be asked of
	 * hirelings as well as characters.  Unhired denizens command nobody's secret and stay visible.
	 */
	private boolean isLocallyControlled(RealmComponent rc) {
		GameClient client = GameClient.GetMostRecentClient();
		if (client==null) return true;
		String playerName = controllingPlayer(rc);
		return playerName==null || client.getClientName().equals(playerName);
	}
	private static BattleChit asBattleChit(RealmComponent rc) {
		return rc instanceof BattleChit?(BattleChit)rc:null;
	}
	/**
	 * Whether this participant belongs to the separate target group rather than the attacker group.
	 * <p>
	 * Only a denizen sheet has a target group: DenizenCombatSheet draws the sheet owner's target in
	 * its own row and keeps it out of the attacker boxes.  A character sheet has no such row - every
	 * denizen attacking the character sits in the target boxes, including one the character happens
	 * to be attacking back - so nothing is split off there.
	 */
	private boolean isSheetOwnersTarget(RealmComponent rc) {
		if (sheetOwner.isCharacter()) return false;
		return rc.equals(sheetOwner.getTarget()) || rc.equals(sheetOwner.get2ndTarget());
	}
	/**
	 * Attacks aimed at this sheet's owner - what sits in the attacker boxes.  Characters are limited
	 * to the local player's; denizen attacks are public.
	 * <p>
	 * Denizens that are the sheet owner or one of its targets are skipped, mirroring the excludeList
	 * DenizenCombatSheet hands to placeAllAttacks: those chits are drawn in the defender and target
	 * rows instead.  Characters are not skipped for being the sheet owner - several characters can
	 * place attacks on the same sheet, including the owner attacking from their own sheet.
	 */
	public ArrayList<AttackKillEstimate> getAttackerEstimates(boolean includeCharacters,boolean includeDenizens) {
		ArrayList<AttackKillEstimate> estimates = new ArrayList<>();
		if (!showOutcomeLines()) return estimates;
		HashSet<String> seen = new HashSet<>();
		for (RealmComponent rc : model.getAllBattleParticipants(true)) {
			// Pre-Tactics: another player's placement is their secret.  From Tactics on, all are public.
			if (!outcomesSettled() && !isLocallyControlled(rc)) continue;
			if (rc.isCharacter()) {
				if (!includeCharacters) continue;
				if (isSheetOwnersTarget(rc)) continue;
			}
			else {
				if (!includeDenizens) continue;
				if (rc.equals(sheetOwner)) continue;
				if (isSheetOwnersTarget(rc)) continue;
				if (!sheetOwner.equals(rc.getTarget()) && !sheetOwner.equals(rc.get2ndTarget())) continue;
			}
			addEstimatesGuarded(estimates,seen,rc);
		}
		Collections.sort(estimates,new Comparator<AttackKillEstimate>() {
			public int compare(AttackKillEstimate e1,AttackKillEstimate e2) {
				return e1.getAttackBox()-e2.getAttackBox();
			}
		});
		return estimates;
	}
	/** The sheet owner's own attack, against whatever it is aimed at. */
	public ArrayList<AttackKillEstimate> getSheetOwnerEstimates() {
		ArrayList<AttackKillEstimate> estimates = new ArrayList<>();
		if (!showOutcomeLines()) return estimates;
		if (!outcomesSettled() && !isLocallyControlled(sheetOwner)) return estimates;
		addEstimatesGuarded(estimates,new HashSet<String>(),sheetOwner);
		return estimates;
	}
	/** The attacks made by whoever the sheet owner is targeting. */
	public ArrayList<AttackKillEstimate> getSheetOwnerTargetEstimates() {
		ArrayList<AttackKillEstimate> estimates = new ArrayList<>();
		if (!showOutcomeLines()) return estimates;
		HashSet<String> seen = new HashSet<>();
		addEstimatesGuarded(estimates,seen,sheetOwner.getTarget());
		addEstimatesGuarded(estimates,seen,sheetOwner.get2ndTarget());
		return estimates;
	}
	/**
	 * Guarded: the outcome lines are a reading aid, so a fault while working them out must never
	 * take the combat sheet's painting down with it.
	 */
	private void addEstimatesGuarded(ArrayList<AttackKillEstimate> estimates,HashSet<String> seen,RealmComponent attackerRc) {
		try {
			addEstimates(estimates,seen,attackerRc);
		}
		catch (Exception ex) {
			OutcomeLineDebug.log("failed to work out outcome lines for "
					+(attackerRc==null?"null":attackerRc.getGameObject().getNameWithNumber())+": "+ex);
		}
	}
	/**
	 * Adds every attack one participant makes.  A character attacks with the fight chits it placed on
	 * THIS sheet; a denizen attacks with its own chit, and a monster's weapon attacks separately (see
	 * BattleModel.collectBattleChits).
	 */
	private void addEstimates(ArrayList<AttackKillEstimate> estimates,HashSet<String> seen,RealmComponent attackerRc) {
		if (attackerRc==null) return;
		if (attackerRc.isCharacter()) {
			ArrayList<CharacterChitComponent> attacks = getPlacedAttacks(attackerRc);
			if (attacks.isEmpty()) return;
			sortInResolutionOrder(attacks);
			boolean firstAttack = true;
			for (CharacterChitComponent attacker : attacks) {
				// No dedupe: two of a character's own attacks are two separate attacks, even when
				// their numbers come out identical, and each needs its own lines
				addEstimate(estimates,null,attacker,resolveAttackTarget(attackerRc,firstAttack));
				firstAttack = false;
			}
			return;
		}
		BattleChit target = asBattleChit(attackerRc.getTarget());
		addEstimate(estimates,seen,asBattleChit(attackerRc),target);
		if (attackerRc.isMonster()) {
			RealmComponent weapon = ((MonsterChitComponent)attackerRc).getWeapon();
			if (weapon!=null) {
				addEstimate(estimates,seen,asBattleChit(weapon),target);
			}
		}
	}
	/** The fight chits this character placed on THIS sheet. */
	private ArrayList<CharacterChitComponent> getPlacedAttacks(RealmComponent characterRc) {
		ArrayList<CharacterChitComponent> attacks = new ArrayList<>();
		String sheetOwnerId = sheetOwner.getGameObject().getStringId();
		CharacterWrapper character = new CharacterWrapper(characterRc.getGameObject());
		for (RealmComponent fightChit : character.getActiveFightChits()) {
			CombatWrapper combat = new CombatWrapper(fightChit.getGameObject());
			int box = combat.getCombatBoxAttack();
			if (box<1 || box>3) continue;
			if (!combat.getPlacedAsFight()) continue;
			if (!sheetOwnerId.equals(combat.getSheetOwnerId())) continue;
			CharacterChitComponent attacker = new CharacterChitComponent(characterRc.getGameObject());
			attacker.setAttackChit(fightChit);
			attacks.add(attacker);
		}
		OutcomeLineDebug.log(characterRc.getGameObject().getName()+" placed "+attacks.size()+" attack(s) on "
				+sheetOwner.getGameObject().getNameWithNumber()+"'s sheet");
		return attacks;
	}
	/**
	 * Estimates against another player's piece are dropped, since their maneuver may be secret.  That
	 * covers hirelings as well as characters - a hireling's placement belongs to whoever hired it.
	 *
	 * @param seen	collapses identical attacks into one entry - two goblins with the same chit
	 * 				produce the same numbers.  Pass null to keep every attack separate.
	 */
	private void addEstimate(ArrayList<AttackKillEstimate> estimates,HashSet<String> seen,BattleChit attacker,BattleChit target) {
		if (attacker==null) return;
		if (target==null) {
			OutcomeLineDebug.log("dropped: no target resolved for an attack");
			return;
		}
		String what = BattleModel.getAttackLabel(attacker)+" vs "+target.getGameObject().getNameWithNumber();
		if (!outcomesSettled() && !isLocallyControlled((RealmComponent)target)) {
			OutcomeLineDebug.log("dropped "+what+": target belongs to another player (pre-Tactics)");
			return;
		}
		AttackKillEstimate estimate = AttackKillEstimate.create(attacker,target,hostPrefs,outcomesSettled(),attackOrderOf(attacker));
		if (estimate==null || estimate.getLines().isEmpty()) {
			OutcomeLineDebug.log("dropped "+what+": no estimate (attack placed="+attacker.hasAnAttack()
					+", missile="+attacker.isMissile()+", fumbleOption="+hostPrefs.hasPref(Constants.OPT_FUMBLE)+")");
			return;
		}
		// Once the boxes are final every surviving attack lands and rolls its own dice, so identical
		// attacks are each worth a line.  Before that they are hypothetical and collapse into one.
		if (seen!=null && !outcomesSettled() && !seen.add(estimateKey(estimate))) {
			OutcomeLineDebug.log("dropped "+what+": identical to an attack already listed");
			return;
		}
		estimates.add(estimate);
	}
	/** Every line, not just the first - two attacks can share an intercept line but differ below it. */
	public static String estimateKey(AttackKillEstimate estimate) {
		StringBuffer sb = new StringBuffer();
		for (AttackKillEstimate.EstimateLine line : estimate.getLines()) {
			sb.append(line.text).append("|");
		}
		return sb.toString();
	}
	/**
	 * What a character's attack placed on THIS sheet is aimed at.  On a denizen's sheet that is the
	 * sheet owner; on a character's own sheet it is whatever that character targeted, which
	 * BattleModel.processHits sends at the 2nd target for every attack after the first.
	 */
	private BattleChit resolveAttackTarget(RealmComponent attackerRc,boolean firstAttack) {
		if (!sheetOwner.isCharacter()) {
			return asBattleChit(sheetOwner);
		}
		RealmComponent target2 = attackerRc.get2ndTarget();
		RealmComponent target = (!firstAttack && target2!=null)?target2:attackerRc.getTarget();
		return asBattleChit(target);
	}
	/**
	 * Puts a character's own attacks in the order combat resolution will run them, so that the 1st
	 * target / 2nd target split lands on the same attacks it will in play.
	 */
	private void sortInResolutionOrder(ArrayList<CharacterChitComponent> attacks) {
		if (attacks.size()<2) return;
		boolean firstRound = combatFrame!=null && combatFrame.getCurrentRound()==1;
		Collections.sort(attacks,firstRound?new BattleChitLengthComparator():new BattleChitSpeedComparator());
	}
}
