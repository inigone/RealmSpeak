package com.robin.magic_realm.components.store;

import javax.swing.JFrame;

import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.attribute.RelationshipType;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

/**
 * Pure-logic operations on guild membership state.
 *
 * Call sites keep their JFrame dialogs and broadcast calls — this class owns
 * only the state transitions and predicates that were previously duplicated or
 * scattered, so there is exactly one place to look when guild rules change.
 *
 * Relationship formula:  level 1-2 = Neutral, 3 = Friendly, 4 = Ally
 *                        (max(NEUTRAL, level - 2)); non-members = Unfriendly.
 */
public class GuildOperationsManager {

	// ----- Relationship -------------------------------------------------------

	/**
	 * Returns the guild's relationship value toward this character.
	 * Use for both trading (RealmUtility) and the character-sheet grid (RelationshipTable).
	 */
	public static int getGuildRelationship(CharacterWrapper character, String guildName) {
		return character.isGuildMember(guildName)
			? Math.max(RelationshipType.NEUTRAL, character.getCurrentGuildLevel() - 2)
			: RelationshipType.UNFRIENDLY;
	}

	/** Convenience overload for call sites that have a RealmComponent trader. */
	public static int getGuildRelationship(CharacterWrapper character, RealmComponent guildTrader) {
		String name = guildTrader.getGameObject().getThisAttribute(com.robin.magic_realm.components.utility.Constants.GUILD);
		return getGuildRelationship(character, name);
	}

	// ----- Membership predicates ----------------------------------------------

	/** True if the character should gain hidden status at their guild at start of evening. */
	public static boolean gainsHiddenAtGuild(CharacterWrapper character) {
		return character.getCurrentGuildLevel() >= 2;
	}

	// ----- State transitions --------------------------------------------------

	/**
	 * Accept the guild's join requirement (become a Pledge).
	 * Does NOT apply any benefits — those come when the requirement is fulfilled.
	 */
	public static void acceptPledge(CharacterWrapper character, String guildName) {
		character.setCurrentGuild(guildName);
		character.setGuildJoinRequirement(true);
	}

	/**
	 * Remove the character from their current guild entirely.
	 * Strips all guild benefits, discards guild quests, then clears membership.
	 * Caller is responsible for any broadcast or UI feedback.
	 */
	public static void removeFromGuild(JFrame frame, CharacterWrapper character) {
		GuildStore guild = character.getCurrentGuildStore();
		if (guild != null) guild.unapplyAllGuildBenefits(frame, character);
		character.discardNonEligibleGuildQuests(); // discard before clearing so getCurrentGuild() still resolves
		character.clearGuild();
	}

	/**
	 * Switch the character to a different guild, resetting their level to 0 and
	 * stripping existing benefits if the guild actually changes.
	 * Caller sets the new level and applies new benefits afterward.
	 */
	public static void changeGuild(JFrame frame, CharacterWrapper character, String newGuildName, boolean resetLevel) {
		if (resetLevel && !newGuildName.equalsIgnoreCase(character.getCurrentGuild())) {
			GuildStore existing = character.getCurrentGuildStore();
			if (existing != null) existing.unapplyAllGuildBenefits(frame, character);
			character.setCurrentGuildLevel(0);
		}
		character.setCurrentGuild(newGuildName);
		character.discardNonEligibleGuildQuests();
	}
}
