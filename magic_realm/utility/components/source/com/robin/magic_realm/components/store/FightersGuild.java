package com.robin.magic_realm.components.store;

import java.util.ArrayList;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.swing.RealmComponentOptionChooser;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.utility.RealmLogging;
import com.robin.magic_realm.components.utility.RealmUtility;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class FightersGuild extends GuildStore {

	// Level 2+ REST service: 3 REST actions per REST phase at the guild (passive — see CharacterWrapper.getRestBonus)
	// Level 3+ repair: auto-triggered at day-end when member stays at the guild (see doGuildDayEndService)
	public static String JOIN_GUILD_LOG_MESSAGE = "Fulfilled the join requirement for the Fighters Guild.";

	public FightersGuild(GuildChitComponent guild, CharacterWrapper character) {
		super(guild, character);
	}
	protected void setupGuildSpecific() {
		if (character.hasCurse(Constants.ASHES)) {
			reasonStoreNotAvailable = "The "+getTraderName()+" does not like your ASHES curse!";
			return;
		}
		if (character.hasCurse(Constants.DISGUST)) {
			reasonStoreNotAvailable = "The "+getTraderName()+" does not like your DISGUST curse!";
			return;
		}
	}
	protected String doGuildService(JFrame frame,int level) {
		return null; // Level 2+ REST bonus is passive; Level 3+ armor repair triggers at day end
	}
	public String doGuildDayEndService(JFrame frame, CharacterWrapper character) {
		if (character.getCurrentGuildLevel() < 3) return null;
		ArrayList<ArmorChitComponent> damaged = new ArrayList<>();
		for (GameObject go : character.getActiveInventory()) {
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			if (rc.isArmor() && ((ArmorChitComponent)rc).isDamaged()) {
				damaged.add((ArmorChitComponent)rc);
			}
		}
		if (damaged.isEmpty()) return null;
		for (ArmorChitComponent armor : damaged) {
			armor.setIntact(true);
		}
		System.out.println("[GUILD_TEST:FG-REPAIR-01] "+character.getGameObject().getName()+" auto-repaired "+damaged.size()+" armor at Fighters Guild");
		return "Fighters Guild: repaired "+damaged.size()+" damaged armor piece"+(damaged.size()==1?"":"s")+".";
	}

	// Level 1+: extra ALERT phase per turn (permanent)
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			GuildPhaseRegistry.applyBenefit(character, FIGHTERS_GUILD, 1);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
			System.out.println("[GUILD_TEST:FG-BEN-01] "+character.getGameObject().getName()+" granted Level 1 Fighters Guild benefit (extra ALERT)");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			GuildPhaseRegistry.unapplyBenefit(character, FIGHTERS_GUILD, 1);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}

	// Level 2+: extra REST phase + native relationship improvement
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.applyBenefit(character, FIGHTERS_GUILD, 2);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:FG-BEN-02] "+character.getGameObject().getName()+" granted Level 2 Fighters Guild benefit (extra REST + native relationship)");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.unapplyBenefit(character, FIGHTERS_GUILD, 2);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}

	// Level 3+: sharpness stars unaffected by armor (armor piercing) + native relationship
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().setThisAttribute(Constants.ARMOR_PIERCING);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:FG-BEN-03] "+character.getGameObject().getName()+" granted Level 3 Fighters Guild benefit (armor piercing + native relationship)");
		}
	}
	public void unapplyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().removeThisAttribute(Constants.ARMOR_PIERCING);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_3");
		}
	}

	// Level 4 (Master, unique): reduce any chosen FIGHT chit effort by one asterisk + native relationship
	public void applyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_4")) return;
		for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
			CharacterWrapper lc = new CharacterWrapper(livingCharacter);
			if (FIGHTERS_GUILD.equals(lc.getCurrentGuild(false))) {
				if (livingCharacter.getId()!=character.getGameObject().getId() &&
						(livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_4") ||
						 livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
					return;
				}
			}
		}
		// Collect FIGHT chits the character can choose from
		ArrayList<CharacterActionChitComponent> fightChits = new ArrayList<>();
		for (CharacterActionChitComponent chit : character.getAllChits()) {
			if ("fight".equals(chit.getGameObject().getThisAttribute("action"))) {
				fightChits.add(chit);
			}
		}
		if (fightChits.isEmpty()) return;
		RealmComponentOptionChooser chooser = new RealmComponentOptionChooser(frame,
				"Fighters Guild Master: Choose a FIGHT chit to reduce effort by one asterisk:",false);
		for (CharacterActionChitComponent chit : fightChits) {
			chooser.addGameObject(chit.getGameObject(), chit.getGameObject().getName());
		}
		chooser.setVisible(true);
		if (chooser.getSelectedText()==null) return;
		GameObject chosen = chooser.getFirstSelectedComponent().getGameObject();
		int currentEffort = 0;
		try { currentEffort = Integer.parseInt(chosen.getThisAttribute("effort")); } catch (NumberFormatException ignored) {}
		chosen.setThisAttribute("effort", String.valueOf(Math.max(0, currentEffort-1)));
		character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_4");
		chooseFriendlinessGain(frame);
		RealmLogging.logMessage(character.getGameObject().getName(),"Fighters Guild Master: reduced effort of "+chosen.getName()+".");
		System.out.println("[GUILD_TEST:FG-BEN-04] "+character.getGameObject().getName()+" granted Level 4 Fighters Guild benefit (FIGHT chit effort reduced: "+chosen.getName()+")");
	}
	public void unapplyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		// Chit modification is permanent; just clear the flag
		character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_4");
	}

	// Training bonus: extra ALERT for pledge holders (Level 0) only — Level 1+ has it permanently via benefit1
	public void applyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.FIGHTERS_GUILD_TRAINING)) {
			if (character.getCurrentGuildLevel() == 0) {
				GuildPhaseRegistry.applyBenefit(character, FIGHTERS_GUILD, 1); // training previews the same ALERT as benefit1
				character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_ALERT);
				System.out.println("[GUILD_TEST:FG-TRAIN-ON] "+character.getGameObject().getName()+" received Fighters Guild training bonus (ALERT for 14 days)");
			}
			character.getGameObject().setThisAttribute(Constants.FIGHTERS_GUILD_TRAINING);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.FIGHTERS_GUILD_TRAINING)) {
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_ALERT)) {
				GuildPhaseRegistry.unapplyBenefit(character, FIGHTERS_GUILD, 1);
				character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_ALERT);
			}
			character.getGameObject().removeThisAttribute(Constants.FIGHTERS_GUILD_TRAINING);
			System.out.println("[GUILD_TEST:FG-TRAIN-OFF] "+character.getGameObject().getName()+" Fighters Guild training bonus expired");
		}
	}

	public boolean validateRequirementAndJoin(CharacterWrapper character, RealmComponent victim, boolean spellUsed) {
		if (spellUsed) return false;
		String nativeGroup = victim.getGameObject().getThisAttribute("native");
		boolean forbiddenGroup = "soldiers".equals(nativeGroup) || "guard".equals(nativeGroup);
		if (character.hasGuildJoinRequirement() && victim.isNativeLeader() && !forbiddenGroup) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(1);
			applyGuildBenefit(null, character, 1);
			System.out.println("[GUILD_TEST:FG-JOIN-01] "+character.getGameObject().getName()+" joined Fighters Guild by killing "+victim.getGameObject().getName()+" ("+nativeGroup+")");
			return true;
		}
		return false;
	}
}
