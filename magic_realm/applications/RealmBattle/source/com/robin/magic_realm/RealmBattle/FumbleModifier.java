package com.robin.magic_realm.RealmBattle;

import java.util.ArrayList;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.BattleChit;
import com.robin.magic_realm.components.Horsebackable;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.attribute.Speed;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;
import com.robin.magic_realm.components.wrapper.CombatWrapper;
import com.robin.magic_realm.components.wrapper.HostPrefWrapper;

/**
 * The die modifier applied to the FUMBLE and MISSILE tables, pulled out of BattleModel so that the
 * combat sheet can show the same number - and the same arithmetic - before anything is rolled.
 * <p>
 * Nothing here logs directly: the log lines combat resolution would have written are collected in
 * getLogEntries() for the caller to emit, which keeps a speculative calculation silent.
 */
public class FumbleModifier {

	private int value = 0;
	private final ArrayList<String> logEntries = new ArrayList<>();
	private final StringBuilder equation = new StringBuilder();

	private FumbleModifier() {
	}

	public int getValue() {
		return value;
	}

	/** The log lines combat resolution writes while working the modifier out, in order. */
	public ArrayList<String> getLogEntries() {
		return logEntries;
	}

	/**
	 * The same arithmetic written out for the combat sheet, eg. <code>1 - 3 + 4 = +2</code>.
	 */
	public String getEquation() {
		return equation.toString() + " = " + (value < 0 ? "" : "+") + value;
	}

	private void add(int amount, String equationTerm) {
		value += amount;
		equation.append(equationTerm);
	}

	/**
	 * @param hitType	one of the BattleModel hit type constants, so that an undercut can be charged
	 * 					its +4
	 */
	public static FumbleModifier calculate(BattleChit attacker, BattleChit target, int hitType, HostPrefWrapper hostPrefs) {
		FumbleModifier fumble = new FumbleModifier();

		int attackSpeed = attacker.getAttackSpeed().getNum();
		int targetSpeed = target.getMoveSpeed().getNum();
		fumble.value = attackSpeed - targetSpeed;
		fumble.equation.append(attackSpeed).append(" - ").append(targetSpeed);

		CombatWrapper attackerCombat = new CombatWrapper(attacker.getGameObject());
		if (hostPrefs.hasPref(Constants.OPT_TWO_HANDED_WEAPONS) && RealmComponent.getRealmComponent(attackerCombat.getGameObject()).isCharacter()) {
			CharacterWrapper attackerCharacter = new CharacterWrapper(attackerCombat.getGameObject());
			ArrayList<GameObject> activeInventory = attackerCharacter.getActiveInventory();
			boolean shield = false;
			boolean twoHandedWeapon = false;
			if (!attackerCharacter.affectedByKey(Constants.STRONG)) {
				for (GameObject item : activeInventory) {
					if (item.hasThisAttribute(Constants.SHIELD) && item.getThisAttribute(Constants.WEIGHT) != "L") shield = true;
					if (item.hasThisAttribute(Constants.TWO_HANDED)) twoHandedWeapon = true;
				}
			}
			if (twoHandedWeapon && shield) {
				fumble.add(2, " + 2");
				fumble.logEntries.add("fumble = " + attackSpeed + " - " + targetSpeed + " = " + fumble.value + " (base speed difference and two-handed weapon malus)");
			}
		}
		else {
			fumble.logEntries.add("fumble = " + attackSpeed + " - " + targetSpeed + " = " + fumble.value + " (base speed difference)");
		}

		if (hitType == BattleModel.UNDERCUT) {
			fumble.add(4, " + 4");
			fumble.logEntries.add("fumble + 4 = " + fumble.value + " (for undercut)");
		}

		fumble.applyRiderAdjustment(attacker, target, hostPrefs);
		return fumble;
	}

	/*
	 * Possibilities:
	 * 		No OPT_SEPARATE_RIDER
	 * 			Targeting non-horseback rider - DONE
	 * 			Targeting horseback rider - DONE
	 * 		OPT_SEPARATE_RIDER
	 * 			Targeting non-horseback rider - DONE
	 * 			Targeting horseback rider
	 * 			Targeting rider's horse - DONE
	 */
	private void applyRiderAdjustment(BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs) {
		if (!hostPrefs.hasPref(Constants.OPT_RIDING_HORSES)) return;

		RealmComponent targetRc = (RealmComponent) target;
		CombatWrapper targetCombat = new CombatWrapper(target.getGameObject());
		if (targetRc.getHorse() == null || !targetCombat.isTargetingRider(attacker.getGameObject())) return;
		if (!(targetRc instanceof Horsebackable)) return;

		// Get the rider's maneuver (if any) separate from the horse - need the box, and the speed
		Horsebackable hb = (Horsebackable) targetRc;
		int mBox = hb.getManeuverCombatBox(false);
		Speed mSpeed = hb.getMoveSpeed(false);
		if (mBox <= 0 || mSpeed == null) return;

		logEntries.add("Applying special horse/rider maneuver rules:");

		int attackSpeed = attacker.getAttackSpeed().getNum();
		add(attackSpeed - mSpeed.getNum(), " + " + attackSpeed + " - " + mSpeed.getNum());
		logEntries.add("fumble + " + attackSpeed + " - " + mSpeed.getNum() + " = " + value + " (base attack speed versus rider)");

		if (attacker.getAttackCombatBox() == mBox) {
			logEntries.add("Intercepted Rider! (box " + attacker.getAttackCombatBox() + " matches box " + mBox + ")");
		}
		else {
			logEntries.add("Did not Intercept Rider! (box " + attacker.getAttackCombatBox() + " does not match box " + mBox + ")");
			add(4, " + 4");
			logEntries.add("fumble + 4 = " + value + " (for failing to intercept rider)");
		}
	}
}
