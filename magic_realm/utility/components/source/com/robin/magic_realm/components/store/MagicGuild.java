package com.robin.magic_realm.components.store;

import java.util.ArrayList;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.general.swing.ButtonOptionDialog;
import com.robin.magic_realm.components.CharacterActionChitComponent;
import com.robin.magic_realm.components.GuildChitComponent;
import com.robin.magic_realm.components.attribute.Strength;
import com.robin.magic_realm.components.swing.RealmComponentOptionChooser;
import com.robin.magic_realm.components.swing.RealmObjectChooser;
import com.robin.magic_realm.components.utility.*;
import com.robin.magic_realm.components.wrapper.*;

public class MagicGuild extends GuildStore {

	// Level 1+ cancel-spell and Level 2+ cancel-curse services now trigger at day-end (see doGuildDayEndService)
	public static String JOIN_GUILD_TEXT = "You have fulfilled the join requirement for the Magic Guild. You are now a member of the Magic Guild.";
	public static String JOIN_GUILD_TITLE = "Joining Magic Guild";

	private ArrayList<GameObject> greatTreasures;
	
	public MagicGuild(GuildChitComponent guild, CharacterWrapper character) {
		super(guild, character);
	}
	protected void setupGuildSpecific() {
		boolean journeymanMage = character.isGuildMember(trader) && character.getCurrentGuildLevel()==2;
		if (!journeymanMage && character.hasCurse(Constants.ASHES)) {
			reasonStoreNotAvailable = "The "+getTraderName()+" does not like your ASHES curse!";
			return;
		}

		greatTreasures = new ArrayList<GameObject>();
		for (GameObject go:character.getInventory()) {
			if (go.hasThisAttribute("treasure") && go.hasThisAttribute("great")) {
				greatTreasures.add(go);
			}
		}
	}
	private boolean madePayment() {
		RealmObjectChooser chooser = new RealmObjectChooser("Choose two Great Treasures to give to the Magic Guild.",character.getGameData(),false);
		chooser.setValidCount(2);
		chooser.addObjectsToChoose(greatTreasures);
		chooser.setVisible(true);
		if (chooser.pressedOkay()) {
			ArrayList<GameObject> toGive = chooser.getChosenObjects();
			for(GameObject go:toGive) {
				TradeUtility.loseItem(character,go,trader.getGameObject(),false);
				go.removeThisAttribute("great");
			}
			trader.getGameObject().addAll(toGive);
			return true;
		}
		return false;
	}
	private String chooseMagicType(JFrame frame) {
		ButtonOptionDialog chooser = new ButtonOptionDialog(frame,trader.getIcon(),"Which type of speed 2 MAGIC chit will you take?",getTraderName()+" Reward",false);
		chooser.addSelectionObject("I");
		chooser.addSelectionObject("II");
		chooser.addSelectionObject("III");
		chooser.addSelectionObject("IV");
		chooser.addSelectionObject("V");
		chooser.addSelectionObject("VI");
		chooser.addSelectionObject("VII");
		chooser.addSelectionObject("VIII");
		chooser.setVisible(true);
		return (String)chooser.getSelectedObject();
	}
	private String cancelOneSpell(JFrame frame, CharacterWrapper character) {
		SpellMasterWrapper smw = SpellMasterWrapper.getSpellMaster(character.getGameData());
		ArrayList<SpellWrapper> spells = smw.getAffectingSpells(character.getGameObject());
		if (spells.isEmpty()) return null;
		RealmComponentOptionChooser chooser = new RealmComponentOptionChooser(frame,"Cancel which spell? (optional)",true);
		for (SpellWrapper spell : spells) {
			String optionKey = chooser.generateOption();
			chooser.addGameObjectToOption(optionKey, spell.getGameObject());
			for (GameObject hgo : spell.getGameObject().getHold()) {
				chooser.addGameObjectToOption(optionKey, hgo);
			}
		}
		chooser.setVisible(true);
		if (chooser.getSelectedText() != null) {
			GameObject go = chooser.getFirstSelectedComponent().getGameObject();
			new SpellWrapper(go).expireSpell();
			return "Magic Guild: cancelled "+go.getName()+" spell.";
		}
		return null;
	}
	private String cancelOneCurse(JFrame frame, CharacterWrapper character) {
		ArrayList<String> curses = character.getAllCurses();
		if (curses.isEmpty()) return null;
		ButtonOptionDialog chooser = new ButtonOptionDialog(frame, trader.getIcon(), "Cancel which curse? (optional)", getTraderName()+" Service", true);
		for (String curse : curses) {
			chooser.addSelectionObject(curse, true);
		}
		chooser.setVisible(true);
		String selected = (String)chooser.getSelectedObject();
		if (selected != null) {
			character.removeCurse(selected);
			return "Magic Guild: removed "+selected+" curse.";
		}
		return null;
	}
	protected String doGuildService(JFrame frame, int level) {
		return null; // Services now trigger at day-end (see doGuildDayEndService)
	}
	public String doGuildDayEndService(JFrame frame, CharacterWrapper character) {
		int level = character.getCurrentGuildLevel();
		if (level < 1) return null;
		String result = cancelOneSpell(frame, character);
		if (level >= 2) {
			String curseResult = cancelOneCurse(frame, character);
			if (result == null) result = curseResult;
			else if (curseResult != null) result = result + " " + curseResult;
		}
		return result;
	}
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().addThisAttributeListItem(Constants.EXTRA_ACTIONS,"SP");
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			character.getGameObject().removeThisAttributeListItem(Constants.EXTRA_ACTIONS,"SP");
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().setThisAttribute(Constants.FREE_ENCHANT_CHIT);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			character.getGameObject().removeThisAttribute(Constants.FREE_ENCHANT_CHIT);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
				String guildLivingCharacter = new CharacterWrapper(livingCharacter).getCurrentGuild();
				if (guildLivingCharacter!=null && guildLivingCharacter.matches(MAGIC_GUILD)) {
					if (livingCharacter.getId()!=character.getGameObject().getId() && (livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_3") || livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
						return;
					}
				}
			}
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			String chosenMagicType = chooseMagicType(frame);
			GameObject go = getNewCharacterChit();
			Strength vul = new Strength(character.getGameObject().getThisAttribute("vulnerability"));
			if (!vul.isTremendous()) {
				vul = vul.addStrength(1);
			}
			go.setThisAttribute("action","magic");
			go.setThisAttribute("speed","2");
			go.setThisAttribute("magic",chosenMagicType);
			go.setThisAttribute("effort","2");
			go.setName(character.getCharacterLevelName(4)+" MAGIC "+chosenMagicType+"2**");
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
			character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
			character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_BONUS)) {
			character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
			character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_BONUS);
		}
	}
	public boolean validateRequirementAndJoin(JFrame frame, CharacterWrapper character, GameObject spell) {
		if (character.hasGuildJoinRequirement()) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(0);
			return true;
		}
		return false;
	}
}