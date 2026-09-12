package com.robin.magic_realm.RealmBattle;

import java.util.ArrayList;

import com.robin.magic_realm.components.BattleChit;
import com.robin.magic_realm.components.CharacterChitComponent;
import com.robin.magic_realm.components.MonsterChitComponent;
import com.robin.magic_realm.components.BattleHorse;
import com.robin.magic_realm.components.MonsterPartChitComponent;
import com.robin.magic_realm.components.NativeSteedChitComponent;
import com.robin.magic_realm.components.SteedChitComponent;
import com.robin.magic_realm.components.NativeChitComponent;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.WeaponChitComponent;
import com.robin.magic_realm.components.attribute.ColorMagic;
import com.robin.magic_realm.components.attribute.Harm;
import com.robin.magic_realm.components.attribute.Strength;
import com.robin.magic_realm.components.attribute.TileLocation;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.utility.DieRollBuilder;
import com.robin.magic_realm.components.utility.DieRollBuilder.DieRollParameters;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;
import com.robin.magic_realm.components.wrapper.CombatWrapper;
import com.robin.magic_realm.components.wrapper.HostPrefWrapper;

/**
 * Works out, for one character attack that has been placed on a combat sheet, how the attack will
 * land and what has to be rolled on the FUMBLE or MISSILE table to kill the target.  The combat
 * sheet writes this next to the attack chit so the player can see the arithmetic before committing.
 * <p>
 * The hit type, the die modifier and the tables themselves are the same code combat resolution
 * uses (BattleModel.calculateHitType, FumbleModifier, CombatTables), so the preview cannot drift
 * away from the real result.  What is mirrored rather than shared is the defensive half of
 * BattleChit.applyHit - armor, shields, barkskin and immunities - because that method applies its
 * effects as it goes and cannot be run speculatively.  Only monster and native targets are
 * supported; character targets have far more involved armor and wound handling.
 */
public class AttackKillEstimate {

	/** Lowest and highest modified table result worth testing - well past what any modifier reaches. */
	private static final int LOWEST_RESULT = -20;
	private static final int HIGHEST_RESULT = 24;

	/** The highest natural roll a single die can show. */
	private static final int MAX_NATURAL_ROLL = 6;

	public enum Emphasis {
		/** An outcome that can actually come up. */
		LIVE,
		/** An outcome that is ruled out however the target repositions. */
		UNAVAILABLE
	}

	public static class EstimateLine {
		public final String text;
		public final Emphasis emphasis;
		private EstimateLine(String text, Emphasis emphasis) {
			this.text = text;
			this.emphasis = emphasis;
		}
	}

	private final ArrayList<EstimateLine> lines = new ArrayList<>();
	private String label = "";
	private int attackBox;
	private int attackOrder;
	private String targetId = "";
	private String attackKey = "";
	/** Total die modifier (clearing bonus + fumble modifier) for the first outcome, used for portrait badges. */
	private int totalModifier;
	/** Compact kill outcome text ("sure kill", "no kill", "kill 1-4") for portrait badges. */
	private String killSummary = "";

	private AttackKillEstimate() {
	}

	/** The attack box this estimate's attack sits in, 1-3. */
	public int getAttackBox() {
		return attackBox;
	}
	/** Where this attack falls in the battle's resolution order, or 0 before the boxes are final. */
	public int getAttackOrder() {
		return attackOrder;
	}
	/**
	 * Game object id of what this attack is aimed at.  The summary uses it to tell an attack landing
	 * on a row's defender from one merely placed on that defender's sheet.
	 */
	public String getTargetId() {
		return targetId;
	}
	/**
	 * Identifies the individual attack - the fight chit for a character, the chit itself otherwise.
	 * Two identical goblins have different keys, so lists can tell their attacks apart.
	 */
	public String getAttackKey() {
		return attackKey;
	}

	/**
	 * Up to POSITION both the intercept and undercut lines are present: the target repositions at
	 * random before the attack resolves, so whichever box the attack sits in, either outcome can
	 * still come up.  From TACTICS on the boxes are final, so only the line that will actually
	 * happen is kept - and an attack that neither intercepts nor undercuts keeps no lines at all.
	 */
	public ArrayList<EstimateLine> getLines() {
		return lines;
	}
	/** Total die modifier (clearing + fumble) applied to the first outcome. Zero when none applies. */
	public int getTotalModifier() {
		return totalModifier;
	}
	/** Compact kill outcome: "sure kill", "no kill", "kill 1-4", or harm string for char targets. */
	public String getKillSummary() {
		return killSummary;
	}

	/**
	 * @param attacker		a character chit with its attack chit already set
	 * @param target		the denizen the attack was placed against
	 * @return				null when this attack cannot be previewed - no attack placed, a target
	 * 						other than a monster or native, or a battle using neither table
	 */
	public static AttackKillEstimate create(BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs, boolean settled, int attackOrder) {
		if (attacker == null || target == null) return null;
		if (!attacker.hasAnAttack()) return null;
		if (!hostPrefs.hasPref(Constants.OPT_FUMBLE) && !attacker.isMissile()) return null;
		if (target.isCharacter()) {
			// Killing a character runs through armor layers and wounds, so report the harm instead
			return createForCharacterTarget(attacker, target, hostPrefs, settled, attackOrder);
		}
		if (!target.isMonster() && !target.isNative()) return null;

		// Which table is rolled, and the roll needed to kill, do not depend on how the attack lands -
		// only the fumble modifier does.  So work the table out once and let each outcome shift it
		// by its own modifier.
		Harm baseHarm = attacker.getHarm();
		boolean rollsTable = !baseHarm.isNegligible() && baseHarm.isAdjustable();
		String tableName = null;
		if (rollsTable && attacker.isMissile()) {
			tableName = "missile";
		}
		else if (rollsTable && hostPrefs.hasPref(Constants.OPT_FUMBLE) && !isMagicAttack(attacker) && !baseHarm.getStrength().isRed()) {
			tableName = "fumble";
		}

		AttackKillEstimate estimate = new AttackKillEstimate();
		estimate.label = orderPrefix(attackOrder)+BattleModel.getAttackLabel(attacker);
		estimate.attackOrder = attackOrder;
		estimate.targetId = target.getGameObject().getStringId();
		estimate.attackKey = BattleModel.getAttackOrderKey(attacker);
		estimate.attackBox = attacker.getAttackCombatBox();
		String settledKillText = null;
		Integer bestKillingResult = null;
		int dieModifier = 0;
		if (tableName == null) {
			// Nothing is rolled, so the result is already settled either way
			settledKillText = wouldKill(baseHarm, attacker, target, hostPrefs, attackOrder) ? "sure kill" : "no kill";
		}
		else {
			DieRollParameters params = getDieRollParameters(attacker);
			dieModifier = params == null ? 0 : params.modifier;
			bestKillingResult = findBestKillingResult(baseHarm, tableName, attacker, target, hostPrefs, attackOrder);
		}

		int actualHitType = settled?BattleModel.calculateHitType(attacker, target, new ArrayList<String>()):0;
		if (!settled || actualHitType == BattleModel.INTERCEPT) {
			estimate.addOutcome("Intercept", BattleModel.INTERCEPT, bestKillingResult, settledKillText, attacker, target, hostPrefs, dieModifier);
		}
		if (canEverUndercut(attacker, target)) {
			if (!settled || actualHitType == BattleModel.UNDERCUT) {
				estimate.addOutcome("Undercut", BattleModel.UNDERCUT, bestKillingResult, settledKillText, attacker, target, hostPrefs, dieModifier);
			}
		}
		else if (!settled) {
			estimate.lines.add(new EstimateLine(estimate.label + " Undercut: n/a", Emphasis.UNAVAILABLE));
		}
		return estimate;
	}

	/**
	 * The highest table result that still kills, or null if none does.  attackOrderPos matters only
	 * for picking which body takes the hit - see wouldKill.  Harm only ever gets worse as
	 * the result climbs, so the results that kill are the run of lowest ones.  This stops at the
	 * first result that does not kill rather than taking the highest killing result outright - the
	 * optional missile table leaves harm untouched above 9, which would otherwise read as another
	 * kill far up the table.
	 */
	private static Integer findBestKillingResult(Harm baseHarm, String tableName, BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs, int attackOrderPos) {
		Integer best = null;
		for (int result = LOWEST_RESULT; result <= HIGHEST_RESULT; result++) {
			Harm harm = new Harm(baseHarm);
			applyTable(harm, result, tableName, hostPrefs);
			if (!wouldKill(harm, attacker, target, hostPrefs, attackOrderPos)) {
				break;
			}
			best = Integer.valueOf(result);
		}
		return best;
	}

	private void addOutcome(String outcomeName, int hitType, Integer bestKillingResult, String settledKillText, BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs, int dieModifier) {
		StringBuilder sb = new StringBuilder(label).append(" ").append(outcomeName).append(":");
		int fumbleModifier = 0;
		if (hostPrefs.hasPref(Constants.OPT_FUMBLE)) {
			FumbleModifier fumble = FumbleModifier.calculate(attacker, target, hitType, hostPrefs);
			fumbleModifier = fumble.getValue();
			sb.append(" ").append(fumble.getEquation());
		}
		String kt = settledKillText != null ? settledKillText : killText(bestKillingResult, fumbleModifier + dieModifier);
		sb.append("  ").append(kt);
		lines.add(new EstimateLine(sb.toString(), Emphasis.LIVE));
		if (lines.size() == 1) {
			totalModifier = fumbleModifier + dieModifier;
			killSummary = kt;
		}
	}

	private static String killText(Integer bestKillingResult, int totalModifier) {
		if (bestKillingResult == null) return "no kill";
		int needed = bestKillingResult.intValue() - totalModifier;
		if (needed >= MAX_NATURAL_ROLL) return "sure kill";
		if (needed < 1) return "no kill";
		return "kill " + (needed == 1 ? "1" : ("1-" + needed));
	}

	/**
	 * Whether an undercut is reachable at all, regardless of where the attack sits.  A target with
	 * no maneuver is intercepted from every box, so an undercut can never come up.
	 */
	private static boolean canEverUndercut(BattleChit attacker, BattleChit target) {
		if (attacker.getGameObject().hasThisAttribute(Constants.NO_UNDERCUT)) return false;
		if (target.getManeuverCombatBox() == 0) return false;
		if (((RealmComponent) target).affectedByKey(Constants.STOP_UNDERCUT)) return false;
		if (attacker.getAttackSpeed().fasterThan(target.getMoveSpeed())) return true;
		return attacker.getAttackSpeed().equalTo(target.getMoveSpeed()) && attacker.hitsOnTie();
	}

	/**
	 * The mirror of create() for an attack coming AT a character: rather than a kill test, which
	 * would need all of CharacterChitComponent.applyHit's armor and wound handling, this reports the
	 * harm each die result inflicts and leaves the player to weigh it against their armor.
	 * <p>
	 * Only the attacker's attack speed and the character's maneuver speed matter here - the
	 * character's own attack plays no part.
	 *
	 * @param attacker	the denizen chit (or a monster's weapon part) attacking the character
	 * @param target	the character being attacked
	 */
	private static AttackKillEstimate createForCharacterTarget(BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs, boolean settled, int attackOrder) {
		Harm baseHarm = attacker.getHarm();
		boolean rollsTable = !baseHarm.isNegligible() && baseHarm.isAdjustable();
		String tableName = null;
		if (rollsTable && attacker.isMissile()) {
			tableName = "missile";
		}
		else if (rollsTable && hostPrefs.hasPref(Constants.OPT_FUMBLE) && !isMagicAttack(attacker) && !baseHarm.getStrength().isRed()) {
			tableName = "fumble";
		}

		AttackKillEstimate estimate = new AttackKillEstimate();
		estimate.label = orderPrefix(attackOrder)+BattleModel.getAttackLabel(attacker);
		estimate.attackOrder = attackOrder;
		estimate.targetId = target.getGameObject().getStringId();
		estimate.attackKey = BattleModel.getAttackOrderKey(attacker);
		estimate.attackBox = attacker.getAttackCombatBox();
		int actualHitType = settled?BattleModel.calculateHitType(attacker, target, new ArrayList<String>()):0;
		if (!settled || actualHitType == BattleModel.INTERCEPT) {
			estimate.addHarmOutcome("Intercept", BattleModel.INTERCEPT, baseHarm, tableName, attacker, target, hostPrefs);
		}
		if (canEverUndercut(attacker, target)) {
			if (!settled || actualHitType == BattleModel.UNDERCUT) {
				estimate.addHarmOutcome("Undercut", BattleModel.UNDERCUT, baseHarm, tableName, attacker, target, hostPrefs);
			}
		}
		else if (!settled) {
			estimate.lines.add(new EstimateLine(estimate.label + " Undercut: n/a", Emphasis.UNAVAILABLE));
		}
		return estimate;
	}

	private void addHarmOutcome(String outcomeName, int hitType, Harm baseHarm, String tableName, BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs) {
		StringBuilder sb = new StringBuilder(label).append(" ").append(outcomeName).append(":");
		int fumbleModifier = 0;
		if (hostPrefs.hasPref(Constants.OPT_FUMBLE)) {
			FumbleModifier fumble = FumbleModifier.calculate(attacker, target, hitType, hostPrefs);
			fumbleModifier = fumble.getValue();
			sb.append(" ").append(fumble.getEquation());
		}
		sb.append("  ");
		String harmText;
		if (tableName == null) {
			harmText = harmString(new Harm(baseHarm));
		}
		else {
			harmText = harmOutcomes(baseHarm, tableName, fumbleModifier, hostPrefs);
		}
		sb.append(harmText);
		lines.add(new EstimateLine(sb.toString(), Emphasis.LIVE));
		if (lines.size() == 1) {
			totalModifier = fumbleModifier;
			killSummary = harmText;
		}
	}

	/**
	 * The harm each natural roll inflicts, weakest first, with equal results merged into one range -
	 * eg. <code>4-6: L* , 2-3: M* , 1: H*</code>.  The high die is 1-6 whether the attacker rolls
	 * one die or two, so the mapping is the same either way.
	 */
	private static String harmOutcomes(Harm baseHarm, String tableName, int totalModifier, HostPrefWrapper hostPrefs) {
		String[] byRoll = new String[MAX_NATURAL_ROLL + 1];
		for (int natural = 1; natural <= MAX_NATURAL_ROLL; natural++) {
			Harm harm = new Harm(baseHarm);
			applyTable(harm, natural + totalModifier, tableName, hostPrefs);
			byRoll[natural] = harmString(harm);
		}
		StringBuilder sb = new StringBuilder();
		int high = MAX_NATURAL_ROLL;
		while (high >= 1) {
			int low = high;
			while (low > 1 && byRoll[low - 1].equals(byRoll[high])) {
				low--;
			}
			if (sb.length() > 0) sb.append(CombatSheet.ESTIMATE_GROUP_SEPARATOR);
			sb.append(low == high ? String.valueOf(low) : (low + "-" + high)).append(": ").append(byRoll[high]);
			high = low - 1;
		}
		return sb.toString();
	}

	/** Harm written the way it reads on a chit - strength plus one star per point of sharpness. */
	private static String harmString(Harm harm) {
		if (harm.isWound()) return "wound";
		StringBuilder sb = new StringBuilder(harm.getStrength().toString());
		for (int i = 0; i < harm.getSharpness(); i++) {
			sb.append("*");
		}
		return sb.toString();
	}

	private static void applyTable(Harm harm, int result, String tableName, HostPrefWrapper hostPrefs) {
		if ("missile".equals(tableName)) {
			CombatTables.applyMissileTable(harm, CombatTables.normalizeMissileResult(result, hostPrefs), hostPrefs);
		}
		else {
			CombatTables.applyFumbleTable(harm, result);
		}
	}

	/**
	 * Where this attack falls in the resolution order, shown only once the boxes are final.  Attacks
	 * that resolve simultaneously share a number - see BattleModel.getAttackOrder.
	 */
	private static String orderPrefix(int attackOrder) {
		return attackOrder>0?("["+attackOrder+"] "):"";
	}

	private static boolean isMagicAttack(BattleChit attacker) {
		String magicType = attacker.getMagicType();
		return magicType != null && magicType.length() > 0;
	}

	/** @return null when the character has no clearing to read die modifiers from. */
	private static DieRollParameters getDieRollParameters(BattleChit attacker) {
		if (!attacker.isCharacter()) {
			// Denizens roll through BattleModel.createClearingRoller, which applies no modifier at
			// all - two dice, or one for an archer's missile
			return null;
		}
		CharacterWrapper character = new CharacterWrapper(attacker.getGameObject());
		TileLocation location = character.getCurrentLocation();
		if (location == null || location.tile == null) return null;
		String key = "fumble";
		if (attacker.isMissile()) {
			// Same key BattleModel.getAdjustedHarm rolls under, so the modifier matches
			if (new CombatWrapper(attacker.getGameObject()).getCastSpell() != null) {
				key = "magicmissil"; // the 'e' is left off intentionally - see BattleModel
			}
			else {
				key = attacker.getMissileType();
				if (key == null || key.trim().length() == 0) {
					key = "missile";
				}
			}
		}
		// Built directly rather than through getDieRollBuilder: that caches per character and would
		// overwrite the parent frame and any red die lock the real roll is relying on.
		DieRollBuilder builder = new DieRollBuilder(null, character, 0);
		return builder.getDieRollParameters(key, location, 2);
	}

	/**
	 * Mirrors the defensive half of applyHit: every defense there does one of two things - block the
	 * attack outright, or dampen sharpness - so the kill test reduces to comparing applied strength
	 * against vulnerability.  It also has to pick the right body: a steed intercepts the hit for its
	 * rider, and steed and rider often have different vulnerabilities.
	 */
	private static boolean wouldKill(Harm attackerHarm, BattleChit attacker, BattleChit target, HostPrefWrapper hostPrefs, int attackOrderPos) {
		RealmComponent targetRc = (RealmComponent) target;
		if (targetRc.affectedByKey(Constants.HOLY_SHIELD)) {
			return false;
		}

		// A mounted target's steed takes the hit and applyHit returns there, so the kill test has to
		// be against the steed - unless the attack is aimed at the rider, or the harm is RED and
		// carries on through
		RealmComponent hitRc = targetRc;
		if (!attackerHarm.getStrength().isRed()
				&& !new CombatWrapper(target.getGameObject()).isTargetingRider(attacker.getGameObject())) {
			// Same order position combat resolution will use, so a steed killed simultaneously this
			// round still takes the hit here, exactly as applyHit has it
			BattleHorse horse = targetRc.getHorse(attackOrderPos>0?attackOrderPos:-1);
			if (horse instanceof RealmComponent) {
				hitRc = (RealmComponent) horse;
			}
		}

		Harm harm = new Harm(attackerHarm);
		Strength vulnerability;
		boolean armored;
		boolean barkskin;

		if (hitRc instanceof MonsterChitComponent) {
			MonsterChitComponent monster = (MonsterChitComponent) hitRc;
			vulnerability = monster.getVulnerability();
			armored = monster.isArmored();
			barkskin = monster.hasBarkskin();
			dampenForMagicImmunity(harm, attacker, monster);
			dampenForPoisonImmunity(harm, attacker, monster.getGameObject().hasThisAttribute(Constants.POISON_IMMUNITY));
			if (!harm.getIgnoresArmor() && monster.hasActiveShield() && hitsShield(monster, attacker)) {
				// An attack that hits the shield never harms the monster
				return false;
			}
		}
		else if (hitRc instanceof NativeChitComponent) {
			NativeChitComponent nativeChit = (NativeChitComponent) hitRc;
			vulnerability = nativeChit.getVulnerability();
			armored = nativeChit.isArmored();
			barkskin = nativeChit.hasBarkskin();
			dampenForPoisonImmunity(harm, attacker, nativeChit.getGameObject().hasThisAttribute(Constants.POISON_IMMUNITY));
		}
		else if (hitRc instanceof NativeSteedChitComponent) {
			NativeSteedChitComponent steed = (NativeSteedChitComponent) hitRc;
			vulnerability = steed.getVulnerability();
			armored = steed.isArmored();
			barkskin = steed.hasBarkskin();
			dampenForPoisonImmunity(harm, attacker, steed.getGameObject().hasThisAttribute(Constants.POISON_IMMUNITY));
		}
		else if (hitRc instanceof SteedChitComponent) {
			SteedChitComponent steed = (SteedChitComponent) hitRc;
			vulnerability = steed.getVulnerability();
			armored = steed.isArmored();
			barkskin = steed.hasBarkskin();
			dampenForPoisonImmunity(harm, attacker, steed.getGameObject().hasThisAttribute(Constants.POISON_IMMUNITY));
		}
		else {
			return false;
		}

		boolean armorPiercing = attacker.getGameObject().hasThisAttribute(Constants.ARMOR_PIERCING)
				|| (attacker.isCharacter() && new CharacterWrapper(attacker.getGameObject()).affectedByKey(Constants.ARMOR_PIERCING));
		if (!harm.getIgnoresArmor() && !armorPiercing && armored) {
			harm.dampenSharpness();
		}
		else if (!armored && !armorPiercing && barkskin && !ignoresBarkskin(attacker)) {
			harm.dampenSharpness();
		}

		Strength applied = harm.getAppliedStrength();
		if (hostPrefs.hasPref(Constants.HOUSE2_DENIZENS_WOUNDS) && applied.equalTo(vulnerability)) {
			// Wounded rather than killed
			return false;
		}
		return applied.strongerOrEqualTo(vulnerability);
	}

	private static boolean hitsShield(MonsterChitComponent monster, BattleChit attacker) {
		MonsterPartChitComponent shield = monster.getShield();
		if (shield == null) return false;
		return new CombatWrapper(shield.getGameObject()).getCombatBoxDefense() == attacker.getAttackCombatBox();
	}

	private static void dampenForMagicImmunity(Harm harm, BattleChit attacker, MonsterChitComponent monster) {
		if (!monster.getGameObject().hasThisAttribute(Constants.MAGIC_IMMUNITY)) return;
		if (!attacker.isCharacter()) return;
		WeaponChitComponent weapon = ((CharacterChitComponent) attacker).getAttackingWeapon();
		if (weapon == null) return;
		if (!weapon.getGameObject().hasThisAttribute(Constants.MAGIC_COLOR_BONUS_ACTIVE)) return;
		int weaponBonus = weapon.getFaceAttributeInt(Constants.MAGIC_COLOR_BONUS_SHARPNESS);
		if (weaponBonus == 0) return;

		String immunity = monster.getGameObject().getThisAttribute(Constants.MAGIC_IMMUNITY);
		ColorMagic monsterImmunityColor = ColorMagic.makeColorMagic(immunity, true);
		ColorMagic weaponMagicColorBonus = ColorMagic.makeColorMagic(weapon.getGameObject().getThisAttribute(Constants.MAGIC_COLOR_BONUS), true);
		if (immunity.matches("prism") || monsterImmunityColor.sameColorAs(weaponMagicColorBonus)) {
			for (int i = 0; i < weaponBonus; i++) {
				harm.dampenSharpness();
			}
		}
	}

	private static void dampenForPoisonImmunity(Harm harm, BattleChit attacker, boolean targetIsImmune) {
		if (!targetIsImmune) return;
		if (attacker.isCharacter()) {
			WeaponChitComponent weapon = ((CharacterChitComponent) attacker).getAttackingWeapon();
			if (weapon != null && weapon.getGameObject().hasThisAttribute(Constants.POISON)) {
				harm.dampenSharpness();
			}
		}
		if (attacker.getGameObject().hasThisAttribute(Constants.POISON)) {
			harm.dampenSharpness();
		}
	}

	private static boolean ignoresBarkskin(BattleChit attacker) {
		ColorMagic attackerImmunityColor = ColorMagic.makeColorMagic(attacker.getGameObject().getThisAttribute(Constants.MAGIC_IMMUNITY), true);
		return attackerImmunityColor != null && (attackerImmunityColor.isPrismColor() || attackerImmunityColor.getColorNumber() == ColorMagic.GRAY);
	}
}
