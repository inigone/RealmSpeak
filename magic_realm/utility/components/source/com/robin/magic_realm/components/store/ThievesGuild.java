package com.robin.magic_realm.components.store;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.attribute.Strength;
import com.robin.magic_realm.components.attribute.TileLocation;
import com.robin.magic_realm.components.utility.*;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class ThievesGuild extends GuildStore {

	// Services are now passive/day-end: Level 1+ reveals hidden paths, Level 2+ secret passages,
	// Level 3 opens one locked treasure — all within a 2-tile radius, no gold cost.
	public static String JOIN_GUILD_TEXT = "You have fulfilled the join requirement for the Thieves Guild. You are now a member of the Thieves Guild.";
	public static String JOIN_GUILD_TITLE = "Joining Thieves Guild";

	public ThievesGuild(GuildChitComponent guild, CharacterWrapper character) {
		super(guild, character);
	}
	protected void setupGuildSpecific() {
		if (character.hasCurse(Constants.ASHES)) {
			reasonStoreNotAvailable = "The "+getTraderName()+" does not like your ASHES curse!";
		}
	}
	protected String doGuildService(JFrame frame, int level) {
		return null; // All services trigger at day-end (see doGuildDayEndService)
	}
	public String doGuildDayEndService(JFrame frame, CharacterWrapper character) {
		int level = character.getCurrentGuildLevel();
		if (level < 1) return null;

		TileLocation loc = character.getCurrentLocation();
		if (loc == null || loc.tile == null) return null;

		// Collect tiles within 2-tile radius
		Set<TileComponent> radius = new LinkedHashSet<>();
		radius.add(loc.tile);
		for (TileComponent t1 : loc.tile.getAllAdjacentTiles()) {
			if (t1 != null) {
				radius.add(t1);
				for (TileComponent t2 : t1.getAllAdjacentTiles()) {
					if (t2 != null) radius.add(t2);
				}
			}
		}

		int hiddenFound = 0, secretFound = 0;
		for (TileComponent tile : radius) {
			if (tile.isEnchanted()) continue;
			for (PathDetail path : tile.getHiddenPaths(false)) {
				String key = path.getFullPathKey();
				if (path.isHidden() && !character.hasHiddenPathDiscovery(key)) {
					character.addHiddenPathDiscovery(key);
					hiddenFound++;
				}
			}
			if (level >= 2) {
				for (PathDetail path : tile.getSecretPassages(false)) {
					String key = path.getFullPathKey();
					if (path.isSecret() && !character.hasSecretPassageDiscovery(key)) {
						character.addSecretPassageDiscovery(key);
						secretFound++;
					}
				}
			}
		}

		StringBuilder result = new StringBuilder();
		if (hiddenFound > 0 || secretFound > 0) {
			result.append("Thieves Guild: discovered");
			if (hiddenFound > 0) result.append(" ").append(hiddenFound).append(" hidden path(s)");
			if (hiddenFound > 0 && secretFound > 0) result.append(",");
			if (secretFound > 0) result.append(" ").append(secretFound).append(" secret passage(s)");
			result.append(" within 2 tiles.");
		}

		if (level >= 3) {
			ArrayList<GameObject> openable = new ArrayList<>();
			for (GameObject go : character.getInventory()) {
				if (go.hasThisAttribute(Constants.NEEDS_OPEN)) {
					openable.add(go);
				}
			}
			if (!openable.isEmpty()) {
				GameObject opened = TreasureUtility.openOneObject(frame, character, openable, null, true);
				if (opened != null) {
					if (result.length() > 0) result.append(" ");
					result.append("Thieves Guild: opened the ").append(opened.getName()).append(".");
				}
			}
		}

		return result.length() > 0 ? result.toString() : null;
	}
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().addThisAttributeListItem(Constants.EXTRA_ACTIONS,"H");
			character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().removeThisAttributeListItem(Constants.EXTRA_ACTIONS,"H");
			character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().setThisAttribute(Constants.THIEVES_GUILD_PVP_STEAL);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().removeThisAttribute(Constants.THIEVES_GUILD_PVP_STEAL);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
				String guildLivingCharacter = new CharacterWrapper(livingCharacter).getCurrentGuild();
				if (guildLivingCharacter!=null && guildLivingCharacter.matches(THIEVES_GUILD)) {
					if (livingCharacter.getId()!=character.getGameObject().getId() &&  (livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_3") || livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
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
			go.setThisAttribute("action","move");
			go.setThisAttribute("speed","2");
			go.setThisAttribute("strength",vul.toString());
			go.setThisAttribute("effort","2");
			go.setName(character.getCharacterLevelName(4)+" MOVE "+vul.toString()+"2**");
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
			// Level 0 only - Level 1+ already has permanent -1:Stealing:all from benefit1
			if (character.getCurrentGuildLevel() == 0) {
				character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_BONUS+"_diemod");
			}
			character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_BONUS)) {
			// Only remove DIEMOD if we actually added it (tracked by _diemod flag)
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_BONUS+"_diemod")) {
				character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_BONUS+"_diemod");
			}
			character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public boolean validateRequirementAndJoin(CharacterWrapper character, GameObject loot) {
		if (character.hasGuildJoinRequirement() && RealmComponent.getRealmComponent(loot).isTreasure()) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(0);
			return true;
		}
		return false;
	}
}