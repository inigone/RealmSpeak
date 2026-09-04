package com.robin.magic_realm.RealmBattle;

import com.robin.magic_realm.components.attribute.Harm;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.utility.RealmUtility;
import com.robin.magic_realm.components.wrapper.HostPrefWrapper;

/**
 * The MISSILE and FUMBLE tables, pulled out of BattleModel.getAdjustedHarm so that they can also
 * be applied speculatively - see AttackKillEstimate, which walks every possible die result to work
 * out what a character needs to roll to kill a target.  Nothing here rolls dice or logs.
 */
public class CombatTables {

	private static final int NEGLIGIBLE_HARM = -10000;

	/**
	 * Clamps a raw missile roll into the range the table in play actually covers.  Combat resolution
	 * reports this value (not the raw roll) in the battle log, so it is a separate step.
	 */
	public static int normalizeMissileResult(int result, HostPrefWrapper hostPrefs) {
		if (hostPrefs.hasPref(Constants.OPT_MISSILE)) {
			return result < 1 ? 1 : result;
		}
		if (hostPrefs.hasPref(Constants.REV_MISSILE)) {
			return result;
		}
		// Standard missile table
		if (result > 6) return 6;
		if (result < 1) return 1;
		return result;
	}

	/** The suffix combat resolution uses to name the missile table in play. */
	public static String getMissileTableLabel(HostPrefWrapper hostPrefs) {
		if (hostPrefs.hasPref(Constants.OPT_MISSILE)) return " (using optional)";
		if (hostPrefs.hasPref(Constants.REV_MISSILE)) return " (using revised)";
		return "";
	}

	/**
	 * Applies the missile table in play to totalHarm.
	 *
	 * @param result	a roll already run through normalizeMissileResult
	 * @return			the roller subtitle, or null when the roll falls off the table entirely and
	 * 					the harm is left untouched (only reachable on the optional table)
	 */
	public static String applyMissileTable(Harm totalHarm, int result, HostPrefWrapper hostPrefs) {
		if (hostPrefs.hasPref(Constants.OPT_MISSILE)) {
			// Optional missile table
			if (result < 10) {
				if (result < 8) {
					totalHarm.changeLevels(4 - result);
					return RealmUtility.getLevelChangeString(4 - result);
				}
				totalHarm.setWound(true);
				return "wound";
			}
			return null;
		}
		if (hostPrefs.hasPref(Constants.REV_MISSILE)) {
			// Revised optional missile table
			int change = RealmUtility.revisedMissileTable(result);
			totalHarm.changeLevels(change);
			return RealmUtility.getLevelChangeString(change);
		}
		// Standard missile table
		totalHarm.changeLevels(3 - result);
		return RealmUtility.getLevelChangeString(3 - result);
	}

	/**
	 * Applies the fumble table to totalHarm.
	 *
	 * @return		the roller subtitle
	 */
	public static String applyFumbleTable(Harm totalHarm, int result) {
		int change;
		if (result < 2) {
			change = 2;
		}
		else if (result < 4) {
			change = 1;
		}
		else if (result < 7) {
			change = 0;
		}
		else if (result < 9) {
			change = -1;
		}
		else if (result < 10) {
			change = -2;
		}
		else {
			change = NEGLIGIBLE_HARM; // NEG
		}
		String changeString = change == NEGLIGIBLE_HARM ? "Negligible Harm" : RealmUtility.getLevelChangeString(change);
		totalHarm.changeLevels(change);
		return changeString;
	}
}
