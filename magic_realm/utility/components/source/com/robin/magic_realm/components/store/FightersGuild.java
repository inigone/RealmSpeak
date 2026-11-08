package com.robin.magic_realm.components.store;

import java.util.ArrayList;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.attribute.Strength;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.utility.RealmLogging;
import com.robin.magic_realm.components.utility.RealmUtility;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class FightersGuild extends GuildStore {

	// Level 1+ REST service is now passive (3 REST actions per phase at the guild — see CharacterWrapper.getRestBonus)
	// Level 2+ repair is auto-triggered at day-end when member stays at the guild (see RealmTurnPanel)
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
		return null; // Level 1+ REST bonus is passive; Level 2+ armor repair triggers at day end
	}
	public String doGuildDayEndService(JFrame frame, CharacterWrapper character) {
		if (character.getCurrentGuildLevel() < 2) return null;
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
		return "Fighters Guild: repaired "+damaged.size()+" damaged armor piece"+(damaged.size()==1?"":"s")+".";
	}
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().addThisAttributeListItem(Constants.EXTRA_ACTIONS,"R");
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().removeThisAttributeListItem(Constants.EXTRA_ACTIONS,"R");
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().setThisAttribute(Constants.ARMOR_PIERCING);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().removeThisAttribute(Constants.ARMOR_PIERCING);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
				String guildLivingCharacter = new CharacterWrapper(livingCharacter).getCurrentGuild();
				if (guildLivingCharacter!=null && guildLivingCharacter.matches(FIGHTERS_GUILD)) {
					if (livingCharacter.getId()!=character.getGameObject().getId() && (livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_3") || livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
						return;
					}
				}
			}
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			GameObject go = getNewCharacterChit();
			Strength vul = new Strength(character.getGameObject().getThisAttribute("vulnerability"));
			if (!vul.isTremendous()) {
				vul = vul.addStrength(1);
			}
			go.setThisAttribute("action","fight");
			go.setThisAttribute("speed","3");
			go.setThisAttribute("strength",vul.toString());
			go.setThisAttribute("effort","2");
			go.setName(character.getCharacterLevelName(4)+" FIGHT "+vul.toString()+"3**");
			go.setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			RealmLogging.logMessage(character.getGameObject().getName(),"Gained a "+go.getName()+" chit.");
		}
	}
	public void unapplyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_3");
		for (CharacterActionChitComponent chit : character.getAllChits()) {
			if (chit.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
				character.getGameObject().remove(chit.getGameObject());
				chit.getGameObject().clearAllAttributes();
			}
		}
	}
	
	public void applyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_BONUS)) {
			character.getGameObject().addThisAttributeListItem(Constants.EXTRA_ACTIONS,"A");
			character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_BONUS)) {
			character.getGameObject().removeThisAttributeListItem(Constants.EXTRA_ACTIONS,"A");
			character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public boolean validateRequirementAndJoin(CharacterWrapper character, RealmComponent victim, boolean spellUsed) {
		if (spellUsed) return false;
		String nativeGroup = victim.getGameObject().getThisAttribute("native");
		boolean forbiddenGroup = "soldiers".equals(nativeGroup) || "guard".equals(nativeGroup);
		if (character.hasGuildJoinRequirement() && victim.isNativeLeader() && !forbiddenGroup) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(0);
			return true;
		}
		return false;
	}
}