package com.robin.magic_realm.components.store;

import java.util.ArrayList;

import javax.swing.JFrame;

import com.robin.game.objects.GameObject;
import com.robin.general.swing.ButtonOptionDialog;
import com.robin.magic_realm.components.CharacterActionChitComponent;
import com.robin.magic_realm.components.GuildChitComponent;
import com.robin.magic_realm.components.swing.RealmComponentOptionChooser;
import com.robin.magic_realm.components.swing.RealmObjectChooser;
import com.robin.magic_realm.components.utility.*;
import com.robin.magic_realm.components.wrapper.*;

public class MagicGuild extends GuildStore {

	// Level 2+: cancel-spell service triggers at day-end (see doGuildDayEndService)
	// Level 3+: cancel-curse service triggers at day-end (see doGuildDayEndService)
	public static String JOIN_GUILD_TEXT = "You have fulfilled the join requirement for the Magic Guild. You are now a member of the Magic Guild.";
	public static String JOIN_GUILD_TITLE = "Joining Magic Guild";

	private ArrayList<GameObject> greatTreasures;

	public MagicGuild(GuildChitComponent guild, CharacterWrapper character) {
		super(guild, character);
	}
	protected void setupGuildSpecific() {
		boolean journeymanMage = character.isGuildMember(trader) && character.getCurrentGuildLevel()==3;
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
		ButtonOptionDialog chooser = new ButtonOptionDialog(frame,trader.getIcon(),"Which type of MAGIC chit?",getTraderName()+" Reward",false);
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
			System.out.println("[GUILD_TEST:MG-SPELL-01] "+character.getGameObject().getName()+" cancelled spell "+go.getName()+" via Magic Guild service");
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
			System.out.println("[GUILD_TEST:MG-CURSE-01] "+character.getGameObject().getName()+" cancelled curse "+selected+" via Magic Guild service");
			return "Magic Guild: removed "+selected+" curse.";
		}
		return null;
	}
	protected String doGuildService(JFrame frame, int level) {
		return null; // Services trigger at day-end (see doGuildDayEndService)
	}
	public String doGuildDayEndService(JFrame frame, CharacterWrapper character) {
		int level = character.getCurrentGuildLevel();
		if (level < 2) return null;
		String result = cancelOneSpell(frame, character);
		if (level >= 3) {
			String curseResult = cancelOneCurse(frame, character);
			if (result == null) result = curseResult;
			else if (curseResult != null) result = result + " " + curseResult;
		}
		return result;
	}

	// Level 1+: -1 Reading Runes Table DRM (permanent, max -1 — skip if character already has it)
	public void applyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			if (!hasDiemod(character, "-1:ReadRunes:")) {
				character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
				character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD);
			}
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_1");
			System.out.println("[GUILD_TEST:MG-BEN-01] "+character.getGameObject().getName()+" granted Level 1 Magic Guild benefit (-1 ReadRunes DRM)");
		}
	}
	public void unapplyGuildBenefit1(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_1")) {
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD)) {
				character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
				character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT_1_DIEMOD);
			}
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_1");
		}
	}

	// Level 2+: extra ENCHANT phase per turn + native relationship
	public void applyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.applyBenefit(character, MAGIC_GUILD, 2);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_2");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:MG-BEN-02] "+character.getGameObject().getName()+" granted Level 2 Magic Guild benefit (extra ENCHANT + native relationship)");
		}
	}
	public void unapplyGuildBenefit2(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_2")) {
			GuildPhaseRegistry.unapplyBenefit(character, MAGIC_GUILD, 2);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_2");
		}
	}

	// Level 3+: enchant one chit for free per day + native relationship
	public void applyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().setThisAttribute(Constants.FREE_ENCHANT_CHIT);
			character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_3");
			chooseFriendlinessGain(frame);
			System.out.println("[GUILD_TEST:MG-BEN-03] "+character.getGameObject().getName()+" granted Level 3 Magic Guild benefit (free enchant chit + native relationship)");
		}
	}
	public void unapplyGuildBenefit3(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_3")) {
			character.getGameObject().removeThisAttribute(Constants.FREE_ENCHANT_CHIT);
			character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_3");
		}
	}

	// Level 4 (Master, unique): change one MAGIC chit to any other type + native relationship
	public void applyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.GUILD_BENEFIT+"_4")) return;
		for (GameObject livingCharacter : RealmUtility.getLivingCharacters(character.getGameData())) {
			CharacterWrapper lc = new CharacterWrapper(livingCharacter);
			if (MAGIC_GUILD.equals(lc.getCurrentGuild(false))) {
				if (livingCharacter.getId()!=character.getGameObject().getId() &&
						(livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT+"_4") ||
						 livingCharacter.hasThisAttribute(Constants.GUILD_BENEFIT_SUCESSOR))) {
					return;
				}
			}
		}
		// Collect MAGIC chits the character can choose from
		ArrayList<CharacterActionChitComponent> magicChits = new ArrayList<>();
		for (CharacterActionChitComponent chit : character.getAllChits()) {
			if ("magic".equals(chit.getGameObject().getThisAttribute("action"))) {
				magicChits.add(chit);
			}
		}
		if (magicChits.isEmpty()) return;
		RealmComponentOptionChooser chooser = new RealmComponentOptionChooser(frame,
				"Magic Guild Master: Choose a MAGIC chit to change type:",false);
		for (CharacterActionChitComponent chit : magicChits) {
			chooser.addGameObject(chit.getGameObject(), chit.getGameObject().getName());
		}
		chooser.setVisible(true);
		if (chooser.getSelectedText()==null) return;
		String newType = chooseMagicType(frame);
		if (newType==null) return;
		GameObject chosen = chooser.getFirstSelectedComponent().getGameObject();
		chosen.setThisAttribute("magic", newType);
		character.getGameObject().setThisAttribute(Constants.GUILD_BENEFIT+"_4");
		chooseFriendlinessGain(frame);
		RealmLogging.logMessage(character.getGameObject().getName(),"Magic Guild Master: changed "+chosen.getName()+" to "+newType+" type.");
		System.out.println("[GUILD_TEST:MG-BEN-04] "+character.getGameObject().getName()+" granted Level 4 Magic Guild benefit (MAGIC chit changed to type "+newType+")");
	}
	public void unapplyGuildBenefit4(JFrame frame, CharacterWrapper character) {
		// Chit modification is permanent; just clear the flag
		character.getGameObject().removeThisAttribute(Constants.GUILD_BENEFIT+"_4");
	}

	// Training bonus: -1 ReadRunes DRM for pledge holders (Level 0) only, max -1 — skip if already present
	public void applyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (!character.getGameObject().hasThisAttribute(Constants.MAGIC_GUILD_TRAINING)) {
			if (character.getCurrentGuildLevel() == 0 && !hasDiemod(character, "-1:ReadRunes:")) {
				character.getGameObject().addThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
				character.getGameObject().setThisAttribute(Constants.GUILD_TRAINING_DIEMOD);
				System.out.println("[GUILD_TEST:MG-TRAIN-ON] "+character.getGameObject().getName()+" received Magic Guild training bonus (-1 ReadRunes for 14 days)");
			}
			character.getGameObject().setThisAttribute(Constants.MAGIC_GUILD_TRAINING);
		}
	}
	public void unapplyGuildTrainingBonus(JFrame frame, CharacterWrapper character) {
		if (character.getGameObject().hasThisAttribute(Constants.MAGIC_GUILD_TRAINING)) {
			if (character.getGameObject().hasThisAttribute(Constants.GUILD_TRAINING_DIEMOD)) {
				character.getGameObject().removeThisAttributeListItem(Constants.DIEMOD,"-1:ReadRunes:all");
				character.getGameObject().removeThisAttribute(Constants.GUILD_TRAINING_DIEMOD);
			}
			character.getGameObject().removeThisAttribute(Constants.MAGIC_GUILD_TRAINING);
			System.out.println("[GUILD_TEST:MG-TRAIN-OFF] "+character.getGameObject().getName()+" Magic Guild training bonus expired");
		}
	}
	public boolean validateRequirementAndJoin(JFrame frame, CharacterWrapper character, GameObject spell) {
		if (character.hasGuildJoinRequirement()) {
			character.setGuildJoinRequirement(false);
			character.setCurrentGuildLevel(1);
			applyGuildBenefit(frame, character, 1);
			System.out.println("[GUILD_TEST:MG-JOIN-01] "+character.getGameObject().getName()+" joined Magic Guild");
			return true;
		}
		return false;
	}
}
