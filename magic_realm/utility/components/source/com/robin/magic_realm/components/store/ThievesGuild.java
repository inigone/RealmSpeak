package com.robin.magic_realm.components.store;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.attribute.TileLocation;
import com.robin.magic_realm.components.swing.RealmComponentOptionChooser;
import com.robin.magic_realm.components.utility.*;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class ThievesGuild extends GuildStore {

	// Services trigger at day-end when member spends a day at the guild:
	//   Level 2+: reveal hidden paths in 2-tile radius
	//   Level 3+: reveal secret passages in 2-tile radius
	//   Level 4:  learn to unlock any locked treasure or treasure site
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
		if (level < 2) return null;

		TileLocation loc = character.getCurrentLocation();
		if (loc == null || loc.tile == null) return null;

		// Collect tiles within 2-tile radius of guild
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
			if (level >= 3) {
				for (PathDetail path : tile.getSecretPassages(false)) {
					String key = path.getFullPathKey();
					if (path.isSecret() && !character.hasSecretPassageDiscovery(key)) {
						character.addSecretPassageDiscovery(key);
						secretFound++;
					}
				}
			}
		}
		if (hiddenFound > 0) System.out.println("[GUILD_TEST:TG-PATH-01] "+character.getGameObject().getName()+" discovered "+hiddenFound+" hidden path(s) via Thieves Guild");
		if (secretFound > 0) System.out.println("[GUILD_TEST:TG-SECRET-01] "+character.getGameObject().getName()+" discovered "+secretFound+" secret passage(s) via Thieves Guild");

		StringBuilder result = new StringBuilder();
		if (hiddenFound > 0 || secretFound > 0) {
			result.append("Thieves Guild: discovered");
			if (hiddenFound > 0) result.append(" ").append(hiddenFound).append(" hidden path(s)");
			if (hiddenFound > 0 && secretFound > 0) result.append(",");
			if (secretFound > 0) result.append(" ").append(secretFound).append(" secret passage(s)");
			result.append(" within 2 tiles.");
		}

		if (level >= 4) {
			if (!character.getGameObject().hasThisAttribute(Constants.GUILD_UNLOCK_LEARNED)) {
				character.getGameObject().setThisAttribute(Constants.GUILD_UNLOCK_LEARNED);
				// Open all NEEDS_OPEN inventory items now that they know how to unlock
				ArrayList<GameObject> opened = new ArrayList<>();
				for (GameObject go : character.getInventory()) {
					if (go.hasThisAttribute(Constants.NEEDS_OPEN)) {
						GameObject item = TreasureUtility.openOneObject(frame, character, java.util.Collections.singletonList(go), null, false);
						if (item != null) opened.add(item);
					}
				}
				if (result.length() > 0) result.append(" ");
				result.append("Thieves Guild Master: learned to unlock any locked treasure or site.");
				if (!opened.isEmpty()) {
					result.append(" Unlocked: ");
					for (int i=0; i<opened.size(); i++) {
						if (i>0) result.append(", ");
						result.append(opened.get(i).getName());
					}
					result.append(".");
				}
				System.out.println("[GUILD_TEST:TG-UNLOCK-01] "+character.getGameObject().getName()+" learned to unlock treasures via Thieves Guild Master service");
			}
		}

		return result.length() > 0 ? result.toString() : null;
	}

	// Level 1+: -1 Steal Attempt Table DRM (permanent, max -1 — skip if character already has it)
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			if (!hasDiemod(character, "-1:stealing:")) {
				character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD);
			}
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
			System.out.println("[GUILD_TEST:TG-BEN-01] "+character.getGameObject().getName()+" granted Level 1 Thieves Guild benefit (-1 Stealing DRM)");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD)) {
				character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD);
			}
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}

	// Level 2+: extra HIDE phase per turn + native relationship
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.applyBenefit(character, THIEVES_GUILD, 2);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:TG-BEN-02] "+character.getGameObject().getName()+" granted Level 2 Thieves Guild benefit (extra HIDE + native relationship)");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.unapplyBenefit(character, THIEVES_GUILD, 2);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}

	// Level 3+: may target other characters with STEAL actions + native relationship
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().setThisAttribute(Constants.THIEVES_GUILD_PVP_STEAL);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:TG-BEN-03] "+character.getGameObject().getName()+" granted Level 3 Thieves Guild benefit (PvP steal + native relationship)");
		}
	}
	public void unapplyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().removeThisAttribute(Constants.THIEVES_GUILD_PVP_STEAL);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_3");
		}
	}

	// Level 4 (Master, unique): reduce any chosen MOVE chit effort by one asterisk + native relationship
	public void applyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_4")) return;
		for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
			CharacterWrapper lc = new CharacterWrapper(livingCharacter);
			if (THIEVES_GUILD.equals(lc.getCurrentGuild(false))) {
				if (livingCharacter.getId()!=character.getGameObject().getId() &&
						(livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_4") ||
						 livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
					return;
				}
			}
		}
		// Collect MOVE chits the character can choose from
		ArrayList<CharacterActionChitComponent> moveChits = new ArrayList<>();
		for (CharacterActionChitComponent chit : character.getAllChits()) {
			if ("move".equals(chit.getGameObject().getThisAttribute("action"))) {
				moveChits.add(chit);
			}
		}
		if (moveChits.isEmpty()) return;
		RealmComponentOptionChooser chooser = new RealmComponentOptionChooser(frame,
				"Thieves Guild Master: Choose a MOVE chit to reduce effort by one asterisk:",false);
		for (CharacterActionChitComponent chit : moveChits) {
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
		RealmLogging.logMessage(character.getGameObject().getName(),"Thieves Guild Master: reduced effort of "+chosen.getName()+".");
		System.out.println("[GUILD_TEST:TG-BEN-04] "+character.getGameObject().getName()+" granted Level 4 Thieves Guild benefit (MOVE chit effort reduced: "+chosen.getName()+")");
	}
	public void unapplyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		// Chit modification is permanent; just clear the flag
		character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_4");
	}

	// Training bonus: -1 Stealing DRM for pledge holders (Level 0) only, max -1 — skip if already present
	public void applyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.THIEVES_GUILD_TRAINING)) {
			if (character.getCurrentGuildLevel() == 0 && !hasDiemod(character, "-1:stealing:")) {
				character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_DIEMOD);
				System.out.println("[GUILD_TEST:TG-TRAIN-00] "+character.getGameObject().getName()+" received Thieves Guild training bonus (-1 Stealing for 14 days)");
			}
			character.getGameObject().setThisAttribute(Constants.THIEVES_GUILD_TRAINING);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.THIEVES_GUILD_TRAINING)) {
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_DIEMOD)) {
				character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:Stealing:all");
				character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_DIEMOD);
			}
			character.getGameObject().removeThisAttribute(Constants.THIEVES_GUILD_TRAINING);
			System.out.println("[GUILD_TEST:TG-TRAIN-EXP] "+character.getGameObject().getName()+" Thieves Guild training bonus expired");
		}
	}
	public boolean validateRequirementAndJoin(CharacterWrapper character, RealmComponent victim, GameObject loot) {
		if (character.hasGuildJoinRequirement()
				&& victim.isNativeLeader()
				&& RealmComponent.getRealmComponent(loot).isTreasure()) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(1);
			applyGuildBenefit(null, character, 1);
			System.out.println("[GUILD_TEST:TG-JOIN-01] "+character.getGameObject().getName()+" joined Thieves Guild by stealing "+loot.getName()+" from native HQ "+victim.getGameObject().getName());
			return true;
		}
		return false;
	}
}
