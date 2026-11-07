package com.robin.magic_realm.components.quest.reward;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.magic_realm.components.quest.QuestConstants;
import com.robin.magic_realm.components.store.GuildStore;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class QuestRewardGuild extends QuestReward {
	public static final String GUILD = "_guild";
	public static final String GUILD_CHANGE = "_guild_change";
	public static final String GUILD_LEVEL = "_guild_lvl";
	public static final String GUILD_RESET = "_guild_reset";
	
	public enum GuildGainType {
		Increase,
		Decrease,
		Set,
		NoChange
	}
	
	public QuestRewardGuild(GameObject go) {
		super(go);
	}

	public void processReward(JFrame frame,CharacterWrapper character) {
		if (getGuildName().matches(QuestConstants.REMOVE)) {
			character.clearGuild();
			character.discardNonEligibleGuildQuests();
			GuildStore guild = character.getCurrentGuildStore();
			guild.unapplyAllGuildBenefits(frame, character);
			return;
		}
		if (!getGuildName().matches(QuestConstants.CURRENT)) {
			if (resetGuildLevelForGuildChange() && !getGuildName().matches(character.getCurrentGuild())) {
				character.setCurrentGuildLevel(0);
				GuildStore guild = character.getCurrentGuildStore();
				guild.unapplyAllGuildBenefits(frame, character);
			}
			character.setCurrentGuild(getGuildName());
			character.discardNonEligibleGuildQuests();
		}
		
		switch (getGuildChange()) {
			case Increase:
				if (character.getCurrentGuildLevel()>=3) {
					return;
				}
				character.setCurrentGuildLevel(character.getCurrentGuildLevel()+1);
				GuildStore guildStore = character.getCurrentGuildStore();
				guildStore.applyGuildBenefit(frame,character,character.getCurrentGuildLevel());
				break;
			case Decrease:
				if (character.getCurrentGuildLevel()<=0) {
					return;
				}
				character.setCurrentGuildLevel(character.getCurrentGuildLevel()-1);
				guildStore = character.getCurrentGuildStore();
				guildStore.unapplyGuildBenefit(frame,character,character.getCurrentGuildLevel()+1);
				break;
			case Set:
				int lvlBefore = character.getCurrentGuildLevel();
				if (getGuildLevel()>=3) {
					character.setCurrentGuildLevel(3);
				} else if (getGuildLevel()<=0) {
					character.setCurrentGuildLevel(0);
				} else {
					character.setCurrentGuildLevel(getGuildLevel());
				}
				if (lvlBefore>character.getCurrentGuildLevel()) {
					GuildStore guildStore = character.getCurrentGuildStore();
					while (lvlBefore>character.getCurrentGuildLevel()) {
						guildStore.unapplyGuildBenefit(frame,character,lvlBefore);
						lvlBefore--;
					}
				}
				if (lvlBefore<character.getCurrentGuildLevel()) {
					GuildStore guildStore = character.getCurrentGuildStore();
					while (lvlBefore<character.getCurrentGuildLevel()) {
						guildStore.applyGuildBenefit(frame,character,lvlBefore+1);
						lvlBefore++;
					}
				}
			case NoChange:
				break;
		}
	}	
		
	public String getDescription() {
		StringBuffer sb = new StringBuffer();
		if (getGuildName().matches(QuestConstants.REMOVE)) {
			return "Removes the characters guild membership.";
		}
		switch (getGuildChange()) {
			case Increase:
				sb.append("Increase ");
				break;
			case Decrease:
				sb.append("Deacrease ");
				break;
			case Set:
				sb.append("Set ");
			case NoChange:
				return "Set characters guild to "+getGuildName();
		}
		if (getGuildName().matches(QuestConstants.CURRENT)) {
			sb.append("characters current guild level");
		}
		else {
			sb.append("characters guild level of "+getGuildName());
		}
		if (getGuildChange() == GuildGainType.Set) {
			sb.append(" to "+getGuildLevel());
		}
		
		sb.append(".");
		return sb.toString();
	}
	public RewardType getRewardType() {
		return RewardType.Guild;
	}
	private GuildGainType getGuildChange() {
		return GuildGainType.valueOf(getString(GUILD_CHANGE));
	}
	private int getGuildLevel() {
		return getInt(GUILD_LEVEL);
	}
	private String getGuildName() {
		return getString(GUILD);
	}
	private boolean resetGuildLevelForGuildChange() {
		return getBoolean(GUILD_RESET);
	}
}