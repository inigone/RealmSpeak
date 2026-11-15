package com.robin.magic_realm.components.store;

import java.util.ArrayList;

import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

/**
 * Single source of truth for which extra phase action each guild level provides.
 * Adding a new benefit requires only a new row in ENTRIES; apply/unapply, display
 * queries, and save-migration all derive from this table automatically.
 *
 * Row format: { guild-name, benefit-level, action-stored-in-GUILD_EXTRA_ACTIONS }
 * "SP" is stored as-is; callers that need the display form replace it with "E".
 */
public class GuildPhaseRegistry {

	private static final Object[][] ENTRIES = {
		{ "fighters", 1, "A"  },  // Initiate:   extra ALERT
		{ "fighters", 2, "R"  },  // Apprentice:  extra REST
		{ "magic",    2, "SP" },  // Apprentice:  extra ENCHANT (stored "SP", displayed "E")
		{ "thieves",  2, "H"  },  // Apprentice:  extra HIDE
	};

	/** Returns the action stored for this guild+level, or null if that level has no phase benefit. */
	public static String getAction(String guild, int level) {
		for (Object[] e : ENTRIES) {
			if (e[0].equals(guild) && (Integer)e[1] == level) return (String)e[2];
		}
		return null;
	}

	/**
	 * Returns the benefit level that provides action for guild, or 0 if none.
	 * Used by PhaseManagerIcon to look up the correct badge and tooltip label.
	 */
	public static int getBenefitLevel(String action, String guild) {
		for (Object[] e : ENTRIES) {
			if (e[0].equals(guild) && e[2].equals(action)) return (Integer)e[1];
		}
		return 0;
	}

	/** Adds the phase action for guild+level to GUILD_EXTRA_ACTIONS (noop if that level has no phase benefit). */
	public static void applyBenefit(CharacterWrapper character, String guild, int level) {
		String action = getAction(guild, level);
		if (action != null) {
			character.getGameObject().addThisAttributeListItem(Constants.GUILD_EXTRA_ACTIONS, action);
		}
	}

	/** Removes the phase action for guild+level from GUILD_EXTRA_ACTIONS. */
	public static void unapplyBenefit(CharacterWrapper character, String guild, int level) {
		String action = getAction(guild, level);
		if (action != null) {
			character.getGameObject().removeThisAttributeListItem(Constants.GUILD_EXTRA_ACTIONS, action);
		}
	}

	/**
	 * One-time migration run on first load after the guild phase redesign.
	 * Restores innate level extra_actions that may have been wrongly removed, then
	 * ensures GUILD_EXTRA_ACTIONS reflects the character's current guild level benefits.
	 */
	public static void migrate(CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_EXTRAS_MIGRATED_V3)) return;

		// Restore innate level extra_actions in case they were wrongly removed by v1 migration.
		// updateLevelAttributes() only runs at game start; subsequent loads rely on the saved value.
		int charLevel = character.getCharacterLevel();
		for (int n = 1; n <= charLevel; n++) {
			ArrayList<String> levelExtra = character.getGameObject().getAttributeList("level_"+n, Constants.EXTRA_ACTIONS);
			if (levelExtra == null) continue;
			for (String a : levelExtra) {
				ArrayList<String> ea = character.getGameObject().getThisAttributeList(Constants.EXTRA_ACTIONS);
				if (ea == null || !ea.contains(a)) {
					character.getGameObject().addThisAttributeListItem(Constants.EXTRA_ACTIONS, a);
				}
			}
		}

		// Ensure GUILD_EXTRA_ACTIONS has the correct guild-benefit phase extras.
		String guild = character.getCurrentGuild(false);
		if (guild != null) {
			for (Object[] e : ENTRIES) {
				if (!e[0].equals(guild)) continue;
				int level = (Integer)e[1];
				String action = (String)e[2];
				if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_"+level)) continue;
				ArrayList<String> ga = character.getGameObject().getThisAttributeList(Constants.GUILD_EXTRA_ACTIONS);
				if (ga == null || !ga.contains(action)) {
					character.getGameObject().addThisAttributeListItem(Constants.GUILD_EXTRA_ACTIONS, action);
				}
			}
		}
		character.getGameObject().setThisAttribute(Constants.GUILD_EXTRAS_MIGRATED_V3);
	}
}
