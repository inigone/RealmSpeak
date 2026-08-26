package com.robin.magic_realm.components.utility;

import java.util.*;

import com.robin.game.objects.*;
import com.robin.game.server.GameClient;
import com.robin.general.swing.DieRoller;
import com.robin.general.util.*;
import com.robin.magic_realm.components.*;
import com.robin.magic_realm.components.attribute.Strength;
import com.robin.magic_realm.components.attribute.TileLocation;
import com.robin.magic_realm.components.table.MonsterGrow;
import com.robin.magic_realm.components.table.RaiseDead;
import com.robin.magic_realm.components.wrapper.*;

public class SetupCardUtility {
	
	private static int generatedMonsterCount = 0;
	
	public static void reset() {
		generatedMonsterCount = 0;
	}
	public static void updateGeneratedMonsterInt(GameObject go) {
		go.setThisAttribute(Constants.NUMBER,++generatedMonsterCount);
	}

	/**
	 * This method will summon monsters from the TreasureSetupCard based on warnings/sounds/etc
	 * 
	 * It will also relocate monsters that are prowling to the specified clearing.
	 */
	public static void summonMonsters(ArrayList<GameObject> summoned,TileLocation tl,GameData data,boolean includeWarningSounds,boolean includeSiteChits,int monsterDie,String boardNumber,int nativeDie) {
		summonMonsters(summoned,tl,data,includeWarningSounds,includeSiteChits,monsterDie,boardNumber,nativeDie,true);
	}
	public static void summonMonsters(ArrayList<GameObject> summoned,TileLocation tl,GameData data,boolean includeWarningSounds,boolean includeSiteChits,int monsterDie,String boardNumber,int nativeDie,boolean prowling) {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(data);
		if (hostPrefs.getDisableSummoning() || DebugUtility.isNoSummon()) {
			return;
		}
		if (!tl.isInClearing()) { // Must be a clearing - can't summon monsters on non-clearings!
			return;
		}
		int clearingNum = tl.clearing.getNum();
		
		// Use a pool to locate all the possible summoning objects for the given monsterDie
		GamePool pool = new GamePool(data.getGameObjects());
		ArrayList<String> keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("!monster"); // no monsters (just their summon boxes)
		keyVals.add("!rank"); // no natives (just their summon boxes)
		ArrayList<GameObject> summons = pool.find(keyVals);
		
		// Break out the objects into three groups
		ArrayList<GameObject> goldSpecials = new ArrayList<>(); // Visitor/Mission chit boxes
		ArrayList<GameObject> dwellingSpecific = new ArrayList<>(); // Native groups
		ArrayList<GameObject> treasureLocations = new ArrayList<>(); // Specific Monsters
		ArrayList<GameObject> otherLocations = new ArrayList<>(); // summoned in a specific order
		for (GameObject go:summons) {
			if (go.getThisInt("monster_die")!=monsterDie && go.getThisInt("monster_die2")!=monsterDie && (nativeDie==-1 || (nativeDie!=-1 && go.getThisInt("native_die")!=nativeDie && go.getThisInt("native_die2")!=nativeDie))) continue;
			if(!GameObjectMatchesBoardNumber(go,boardNumber)) continue;
			
			if (go.hasKey("gold_special_target")) {
				goldSpecials.add(go);
			}
			else if (go.hasKey("dwelling") && !go.hasThisAttribute(Constants.ROVING_NATIVE)) {
				// the native dwellings are dependant on the presence of another regular dwelling (campfires, house, etc.)
				dwellingSpecific.add(go);
			}
			else if (go.hasKey("treasure_location")) {
				// make sure this location is actually in this tile
				if (go.getHeldBy()!=null && go.getHeldBy().equals(tl.tile.getGameObject())) {
					// and that it has anything to summon...
					if (go.getHoldCount()>0) {
						// and that it is face down (rule 12.5/3) - this is probably unnecessary (all tls only have 1 box of monsters anyway)
						TreasureLocationChitComponent tlChit = (TreasureLocationChitComponent)RealmComponent.getRealmComponent(go);
						if (!tlChit.hasSummonedToday(monsterDie) || hostPrefs.hasPref(Constants.HOUSE2_MULTIPLE_SUMMONING)) {
							treasureLocations.add(go);
						}
					}
				}
			}
			else {
				otherLocations.add(go);
			}
		}
		// Sort otherLocations by box_num attribute (those without it, will be sorted to the top)
		Collections.sort(otherLocations,new Comparator<GameObject>() {
			public int compare(GameObject go1,GameObject go2) {
				int n1 = go1.getInt("this","box_num");
				int n2 = go2.getInt("this","box_num");
				return n1-n2;
			}
		});
		
		// Find and sort the warning/sound chits
		/*
		 * Warning chits summon monsters FIRST
		 * THEN Sound chits (low numbers summon before higher numbers)
		 */
		ArrayList<GameObject> warningChits = SetupCardUtility.getWarnings(tl.tile.getGameObject().getHold(),monsterDie,includeWarningSounds,hostPrefs); // this is done separately to capture treasures...
		ArrayList<GameObject> soundChits = new ArrayList<>();
		ArrayList<GameObject> prowlingMonsters = new ArrayList<>();
		for (GameObject go : tl.tile.getGameObject().getHold()) {
			if(!GameObjectMatchesBoardNumber(go,boardNumber)) continue;
			
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			if (rc instanceof SoundChitComponent) {
				SoundChitComponent sound = (SoundChitComponent)rc;
				if (!sound.hasSummonedToday(monsterDie)) { // only summon once per day (rule 12.5/3)
					soundChits.add(go);
				}
			}
			else if (rc.isMonster() && !rc.isPlayerControlledLeader()) {
				int die = go.getThisInt("monster_die");
				int die2 = go.getThisInt("monster_die2");
				int die3 = go.getThisInt("native_die");
				int die4 = go.getThisInt("native_die2");
				if (die==monsterDie || die2==monsterDie || die==99 || die3==nativeDie || die4==nativeDie) { // ghosts are ALWAYS prowling
					MonsterChitComponent monster = (MonsterChitComponent)rc;
					if (!monster.isBlocked() && ((!monster.isMaximumWeight() && !monster.hasMaximumWeightItem()) || monster.isMistLike())) { // Exclude blocked and (except mist like) X monsters and monsters with item with weight X (e.g. Minotaur Shield with altered weight X)
						// Finally, make sure there isn't a monster lure in the monster's clearing.
						if (prowling && ClearingUtility.getItemInClearingWithKey(rc.getCurrentLocation(),Constants.NO_PROWLING)==null) {
							prowlingMonsters.add(go);
						}
					}
				}
			}
		}
		Collections.sort(soundChits,new Comparator<GameObject>() { // sort sound (no need to sort warnings)
			public int compare(GameObject go1,GameObject go2) {
				int n1 = go1.getInt("this","clearing");
				int n2 = go2.getInt("this","clearing");
				return n1-n2;
			}
		});
		
		// Generated monsters and visible travelers used to propagate from here, once per end of
		// character turn.  They now move exactly once a day, in runDailyGeneratorPhase(), so nothing
		// happens here - summoning below is unaffected.  The queries that collected them went with it.

		// Now the process can begin

		// Before anything can be summoned, all prowling monsters on the tile need to be moved to the clearing,
		for (GameObject prowler : prowlingMonsters) {
			// Verify that the clearing changes, if not, then NO BLOCKING OCCURS!!
			int fromClearing = prowler.getThisInt("clearing");
			if (fromClearing==clearingNum) continue;
			
			// We're good!  Move that bitch! (Sheesh, watch the language!)
			prowler.setThisAttribute("clearing",String.valueOf(clearingNum));
			if (!prowler.hasThisAttribute("blocked")) {
				GameClient.broadcastClient("host",prowler.getName()+" is prowling.");
			}
			
			// If new location contains an unhidden character, then the monster stops prowling
			MonsterChitComponent monster = (MonsterChitComponent)RealmComponent.getRealmComponent(prowler);
			updateMonsterBlock(monster);
		}
		
		// Cycle through native dwellings and summon natives if needed
		GameObject dwellingInClearing = ClearingUtility.findDwellingInClearing(tl.tile.getGameObject(),clearingNum);
		if (dwellingInClearing!=null) {
			String dwellingType = dwellingInClearing.getThisAttribute("dwelling").toLowerCase();
			String bn = dwellingInClearing.getThisAttribute(Constants.BOARD_NUMBER);
			for (GameObject nativeDwelling : dwellingSpecific) {
				// need to test the clearing to see if any dwellings are in it that match the attribute "dwelling" in this object
				String dwelling = nativeDwelling.getThisAttribute("dwelling").toLowerCase();
				if (dwelling.indexOf(dwellingType)>=0) {
					// Make sure boardNumber compares (Board B Company only goes to Board B L Fire)
					String bnNative = nativeDwelling.getThisAttribute(Constants.BOARD_NUMBER);
					if (bn==null?bnNative==null:bn.equals(bnNative)) {
						summoned.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),nativeDwelling,clearingNum,"rank"));
					}
				}
			}
		}
		
		// Cycle through gold specials, and summon chits if needed
		ArrayList<RealmComponent> clearingComponents = tl.clearing.getClearingComponents();
		for (GameObject gs : goldSpecials) {
			String bn = gs.getThisAttribute(Constants.BOARD_NUMBER);
			
			// This does not need the bn, because it will be compared to the native attribute
			String summon_n = gs.getThisAttribute("summon_n");
			
			// These two are compared to the game object name, and thus require the bn
			String summon_t = gs.getThisAttribute("summon_t");
			String summon_tl = gs.getThisAttribute("summon_tl");
			
			if (bn!=null) {
				if (summon_t!=null) {
					summon_t = summon_t+" "+bn;
				}
				if (summon_tl!=null) {
					summon_tl = summon_tl+" "+bn;
				}
			}
			
			// Iterate through clearing components
			for (RealmComponent rc : clearingComponents) {
				String rcBn = rc.getGameObject().getThisAttribute(Constants.BOARD_NUMBER);
				if (bn==null?rcBn==null:bn.equals(rcBn)) { // Make sure the goldSpecialTarget matches the boardNumber of the component
					// Must be an unhired native leader!
					if (summon_n!=null && rc.isNativeLeader() && rc.getOwner()==null && rc.getGameObject().getThisAttribute("native").equalsIgnoreCase(summon_n)) {
						summoned.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),gs,clearingNum));
						break;
					}
					if (summon_t!=null && rc.isTreasure() && rc.getGameObject().getName().equalsIgnoreCase(summon_t)) {
						summoned.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),gs,clearingNum));
						break;
					}
					if (summon_tl!=null && rc.isTreasureLocation() && rc.getGameObject().getName().equalsIgnoreCase(summon_tl)) {
						summoned.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),gs,clearingNum));
						break;
					}
				}
			}
		}
		
		ArrayList<GameObject> newMonsters = new ArrayList<>();
		
		// Expansion: generators used to spawn from here, on the first end of character turn whose
		// monster die matched.  They now spawn in runDailyGeneratorPhase(), AFTER the day's propagation,
		// so a monster created today stays at its generator instead of wandering off the moment it
		// appears.  Leaving this loop in place as well would spawn every generator twice a day - the
		// hasSummonedToday() guard here and the daily phase know nothing about each other.

		if (includeSiteChits) {
			String tileType = tl.tile.getTileType();
			// Cycle through treasure locations and summon their guardians (if any)
			for (GameObject trLoc : treasureLocations) {
				StateChitComponent chit = (StateChitComponent)RealmComponent.getRealmComponent(trLoc);
				chit.addSummonedToday(monsterDie);
				
				int tlClearing = trLoc.getThisInt("clearing");
				ClearingDetail clearing = tl.tile.getClearing(tlClearing);
				
				ArrayList<GameObject> hold = new ArrayList<>(trLoc.getHold());
				for (GameObject go : hold) {
					if (go.hasThisAttribute("monster")) {
						// Guardian might have a tilereq, if playing Pruitt's monsters!
						String tileReq = go.getThisAttribute(Constants.SETUP_START_TILE_REQ); // this is optional, and necessary for Pruitt's monsters
						if (tileReq==null || tileReq.equals(tileType)) {
							go.setThisAttribute("clearing",String.valueOf(tlClearing));
							clearing.add(go,null);
							newMonsters.add(go);
						}
					}
				}
			}
		}
		
		ArrayList<String> redChitNames = new ArrayList<>();
		ArrayList<GameObject> tileHold = new ArrayList<>();
		tileHold.addAll(tl.tile.getHold());
		for (GameObject go : tileHold) {
			if (!go.hasKey("red_special")) continue;
			redChitNames.add(go.getName());
			String boardNum = go.getThisAttribute(Constants.BOARD_NUMBER);
			int redChitClearing = go.getThisInt("clearing");
			GameObject denizenDwelling = SetupCardUtility.getFirstLocationWithSummonName(otherLocations,go.getName(),null,boardNum,null);
			if (denizenDwelling!=null) {
				newMonsters.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),denizenDwelling,redChitClearing));
			}
		}
		// Cycle through warning chits and summon anything possible (warning chits are already filtered when getWarnings is called)
		for (GameObject warning : warningChits) {
			if(!GameObjectMatchesBoardNumber(warning,boardNumber)) continue;
			
			RealmComponent rc = RealmComponent.getRealmComponent(warning);
			if (rc.isStateChit()) { // Might be a treasure, like Dragon Essence (bug 453)
				StateChitComponent chit = (StateChitComponent)rc;
				chit.addSummonedToday(monsterDie);
			}
			
			String warningName = warning.getThisAttribute(RealmComponent.WARNING); // ie., bones
			String tileType = warning.getThisAttribute("tile_type");  // ie., C
			if (tileType==null) {
				if (warning.hasThisAttribute("tile_type_clearing_watercave") && tl.clearing!=null) {
					if (tl.clearing.isWater() || tl.clearing.isCave()) {
						tileType = "C";
					}
				}
				if (warning.hasThisAttribute("tile_type_clearing") && tl.clearing!=null) {
					for (String type : warning.getThisAttributeList("tile_type_clearing")) {
						if (type.matches("V") && tl.clearing.isNormal()) {
							tileType = "V";
						} else if (type.matches("N") && tl.clearing.isNormal()) {
							tileType = "N";
						} else if (type.matches("C") && tl.clearing.isCave()) {
							tileType = "C";
						} else if (type.matches("M") && tl.clearing.isMountain()) {
							tileType = "M";
						} else if (type.matches("W") && tl.clearing.isWoods()) {
							tileType = "W";
						} else if (type.matches("WATER") && tl.clearing.isWater()) {
							tileType = "WATER";
						} else if (type.matches("FROZEN_WATER") && tl.clearing.isFrozenWater()) {
							tileType = "FROZEN_WATER";
						}
						if (tileType!=null) {
							break;
						}
					}
				}
				if (warning.hasThisAttribute("tile_type_tile")) {
					String currentTile = tl.tile.getTileType();
					for (String type : warning.getThisAttributeList("tile_type_tile")) {
						if (type.matches(currentTile)) {
							tileType = type;
							break;
						}
					}
				}
				if (tileType==null) {
					if (warning.hasThisAttribute("tile_type_fallback")) {
						tileType = warning.getThisAttribute("tile_type_fallback").toUpperCase();
					}
					else {
						tileType = tl.tile.getTileType();
					}
				}
			}
			
			String boardNum = warning.getThisAttribute(Constants.BOARD_NUMBER);
			GameObject loc = SetupCardUtility.getFirstLocationWithSummonName(otherLocations,warningName,tileType,boardNum,redChitNames);
			if (loc!=null) {
				// found one!  do summon by dumping hold to tile
				
				// first, make sure there aren't any reasons why they can't be summoned...  (Holy Relic)
				GameObject go = ClearingUtility.getItemInClearingWithKey(tl,Constants.NO_UNDEAD);
				GameObject firstMonster = loc.getHoldCount()>0?(GameObject)loc.getHold().get(0):null;
				boolean stopSummon = go!=null && firstMonster!=null && firstMonster.hasThisAttribute(Constants.UNDEAD);

				if (!stopSummon) newMonsters.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),loc,clearingNum));
			}
		}
		if (includeWarningSounds) {
			// Cycle through sound chits and summon anything possible
			String tileType = tl.tile.getGameObject().getThisAttribute("tile_type");
			for (GameObject sound : soundChits) {
				StateChitComponent chit = (StateChitComponent)RealmComponent.getRealmComponent(sound);
				chit.addSummonedToday(monsterDie);
				
				String soundName = sound.getThisAttribute("sound"); // ie., roar
				
				int soundClearing = sound.getThisInt("clearing");
				String boardNum = sound.getThisAttribute(Constants.BOARD_NUMBER);
				GameObject loc = SetupCardUtility.getFirstLocationWithSummonName(otherLocations,soundName,tileType,boardNum,redChitNames);
				if (loc!=null) {
					// found one!  do summon by dumping hold to tile
					newMonsters.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),loc,soundClearing));
				}
			}
		}
		if (hostPrefs.hasPref(Constants.FE_DEADLY_REALM)) {
			ArrayList<ClearingDetail> clearings = tl.tile.getClearings();
			for (ClearingDetail clearing : clearings) {
				ArrayList<RealmComponent> reds = clearing.getRedSpecials();
				for (RealmComponent redSpecial : reds) {
					GameObject redSpecialGo = redSpecial.getGameObject();
					if (!redSpecialGo.hasThisAttribute("seen")) continue;
					
					String name = null;
					if (redSpecialGo.getThisAttribute(RealmComponent.RED_SPECIAL).matches("lost_castle")) {
						name = "castle";
					}
					else if (redSpecial.getGameObject().getThisAttribute(RealmComponent.RED_SPECIAL).matches("lost_city")) {
						name = "city";
					}
					else if (redSpecial.getGameObject().getThisAttribute(RealmComponent.RED_SPECIAL).matches("lost_fortress")) {
						name = "fortress";
					}
					else if (redSpecial.getGameObject().getThisAttribute(RealmComponent.RED_SPECIAL).matches("lost_palace")) {
						name = "palace";
					}
					else {
						continue;
					}
					
					String boardNum = redSpecialGo.getThisAttribute(Constants.BOARD_NUMBER);
					GameObject loc = SetupCardUtility.getFirstLocationWithTsSection(otherLocations,name,boardNum);
					if (loc!=null) {
						// found one!  do summon by dumping hold to tile
						newMonsters.addAll(ClearingUtility.dumpHoldToTile(tl.tile.getGameObject(),loc,clearing.getNum()));
					}
				}
			}
		}
		
		summoned.addAll(newMonsters);
		for (GameObject added : newMonsters) {
			RealmComponent rc = RealmComponent.getRealmComponent(added);
			if (rc.isMonster()) {
				SetupCardUtility.updateMonsterBlock((MonsterChitComponent)rc);
			}
		}
	}
	public static GameObject createWasp(MonsterCreator mc,GameData data) {
		GameObject go = mc.createOrReuseMonster(data);
		mc.setupGameObject(go,"Wasp","wasp","M",false,true);
		MonsterCreator.setupSide(go,"light","L",1,2,0,3,"yellow");
		MonsterCreator.setupSide(go,"dark","L",1,2,0,3,"yellow");
		go.setThisAttribute(Constants.ICON_TYPE+Constants.ALTERNATIVE,"wasp");
		go.setThisAttribute(Constants.ICON_FOLDER+Constants.ALTERNATIVE,"wesnoth/units");
		go.setThisAttribute(Constants.ICON_SIZE+Constants.ALTERNATIVE,"0.9");
		return go;
	}
	public static GameObject createBlob(MonsterCreator mc,GameData data) {
		GameObject go = mc.createOrReuseMonster(data);
		mc.setupGameObject(go,"Blob","blob","L",false);
		MonsterCreator.setupSide(go,"light","L",0,3,0,5,"lightblue");
		MonsterCreator.setupSide(go,"dark","L",0,3,0,5,"lightblue");
		go.setThisAttribute(Constants.ICON_TYPE+Constants.ALTERNATIVE,"mudcrawler");
		go.setThisAttribute(Constants.ICON_FOLDER+Constants.ALTERNATIVE,"wesnoth/units/monsters");
		go.setThisAttribute(Constants.ICON_SIZE+Constants.ALTERNATIVE,"1.1");
		go.setThisAttribute(Constants.ICON_Y_OFFSET+Constants.ALTERNATIVE,"1");
		go.setThisAttribute(Constants.GM_GROW);
		return go;
	}
	public static boolean GameObjectMatchesBoardNumber(GameObject go,String boardNumber) {
		if (boardNumber == null) return true;
		String bn = go.getThisAttribute(Constants.BOARD_NUMBER);	
		return (bn==null && boardNumber == "") || (bn != null && boardNumber.matches(bn));
	}
	private static ArrayList<GameObject> generateMonsters(GameObject generator,ClearingDetail clearing) {
		ArrayList<GameObject> list = new ArrayList<>();
		GameData data = generator.getGameData();
		String dieString = generator.getThisAttribute(Constants.GENERATOR);
		if (dieString==null) return list;
		String iconType = generator.getThisAttribute("icon_type");
		int monsters = getDieRollForString(dieString);
		MonsterCreator mc = new MonsterCreator("gen"+iconType);
		boolean noUndeadAllowed = ClearingUtility.getItemInClearingWithKey(clearing.getTileLocation(),Constants.NO_UNDEAD)!=null;
		String podId = null; // minted from the first monster actually created - see below
		for (int i=0;i<monsters;i++) {
			GameObject go = null;
			if ("wasp".equals(iconType)) {
				go = createWasp(mc,data);
			}
			else if ("blob".equals(iconType)) {
				go = createBlob(mc,data);
			}
			else if ("zombie1".equals(iconType)) {
				if (noUndeadAllowed) break;
				go = RaiseDead.createUndead(mc,data);
				// color these undead a little differently to distinguish them from others
				go.setAttribute("light","chit_color","gray");
				go.setAttribute("dark","chit_color","darkgray");
				go.setThisAttribute(Constants.ICON_TYPE+Constants.ALTERNATIVE,"zombie");
				go.setThisAttribute(Constants.ICON_FOLDER+Constants.ALTERNATIVE,"wesnoth/units/undead");
				go.setThisAttribute(Constants.ICON_SIZE+Constants.ALTERNATIVE,"0.9");
			}
			if (go!=null) {
				go.setThisAttribute(Constants.GENERATED);
				// One spawn batch = one new pod, never joining whatever already stands here. The id is
				// taken from the first monster created in this batch: object ids are unique and never
				// reused, so no counter needs persisting, and the label survives that monster's death
				// since it is only ever compared, never dereferenced.
				if (podId==null) podId = "p"+go.getStringId();
				go.setThisAttribute(Constants.GM_POD_ID,podId);
				go.setThisAttribute("clearing",String.valueOf(clearing));
				clearing.add(go,null);
				go.setThisAttribute("monster_die",generator.getThisAttribute("monster_die"));
				if (generator.hasThisAttribute("monster_die2")) {
					go.setThisAttribute("monster_die2",generator.getThisAttribute("monster_die2"));
				}
				go.setThisAttribute(Constants.GENERATOR_ID,generator.getStringId());
				if (generator.getThisAttribute(Constants.BOARD_NUMBER) != null) {
					go.setThisAttribute((Constants.BOARD_NUMBER), generator.getThisAttribute(Constants.BOARD_NUMBER));
				}
			}
		}
		list.addAll(mc.getMonstersCreated());
		return list;
	}
	private static int calculateIncentive(ArrayList<RealmComponent> components,int monsterIncentive,int characterIncentive) {
		int count = 0;
		for (RealmComponent rc:components) {
			if (rc.isCharacter()) count+=characterIncentive;
			if (rc.getGameObject().hasThisAttribute(Constants.GENERATED)) count+=monsterIncentive;
		}
		return count;
	}
	private static int countCharacters(ArrayList<RealmComponent> components) {
		int count = 0;
		for (RealmComponent rc:components) {
			if (rc.isCharacter()) count++;
		}
		return count;
	}
	/**
	 * Generated monsters standing here, NOT counting the ones doing the deciding.
	 * <p>
	 * A monster must not crowd itself out of its own clearing.  Its own location is scored alongside
	 * the places it could move to, and the crowding term is what makes a place with monsters already
	 * in it less attractive - so counting itself, and its pod-mates when they move as one body, taxes
	 * holding position for company the monster IS.  A pod of five was penalised as heavily for staying
	 * put as for walking into a clearing already holding five strangers.
	 * <p>
	 * Only the deciding body is excluded.  Generated monsters of another flavour standing in the same
	 * clearing are strangers and still crowd normally.
	 */
	private static int countGeneratedMonsters(ArrayList<RealmComponent> components,Collection<GameObject> deciding) {
		int count = 0;
		for (RealmComponent rc:components) {
			if (!rc.getGameObject().hasThisAttribute(Constants.GENERATED)) continue;
			if (deciding!=null && deciding.contains(rc.getGameObject())) continue;
			count++;
		}
		return count;
	}
	/**
	 * One place a monster could go, with everything needed to score it, pick it and explain it.
	 * Exactly one of tile/clearing is set, matching the branch that built it.
	 */
	private static class MoveOption {
		TileComponent tile;
		ClearingDetail clearing;
		int dfh;          // distance from the generator - clearings walking, tiles flying
		int characters;   // characters present, plus roadway characters attributable here
		int generated;    // generated monsters already present
		boolean hold;     // this is where the monster already stands
		boolean farther;  // strictly further from the generator than the monster already is
		boolean same;     // exactly as far from the generator - a lateral move, or holding
		int ruleScore;    // written-rules score
		double weight;    // by-chance weight
		double odds;      // by-chance odds as a percentage, filled once the total is known
	}
	/** Written rules: dfh + 40c - 2g, unchanged from upstream.  Highest wins. */
	private static int ruleScore(int dfh,int characters,int generated) {
		return dfh + 2*(20*characters - generated);
	}
	/**
	 * By chance: build the odds the host asked for, rather than a score.
	 * <p>
	 * Every candidate gets a weight of its own, and the odds are those weights normalised.  Two things
	 * decide it:
	 * <ol>
	 * <li><b>What the move does to the distance</b> from the generator - <b>closer 1, no further 5,
	 *     further 25</b>.  Holding position is not special: it leaves the monster exactly as far out as
	 *     it was, so it scores 5 like any lateral step.</li>
	 * <li><b>Whether a character is there</b> - <b>+200</b>, flat, for one character or a dozen.  A
	 *     roadway character counts as present at each place its road touches, so it adds 200 at both
	 *     ends (see the roadway rules on chooseGeneratedMonsterMove()).</li>
	 * </ol>
	 * So a character outranks distance outright: any place with one is worth at least 201 against 25 for
	 * the best empty option, and a monster that can see a character goes for it wherever it lies.  With
	 * nobody about, a monster is 5x likelier to push outward than to stay level and 25x likelier than to
	 * turn back.
	 * <p>
	 * Generated monsters already present still divide the weight by (1 + g/4), so a crowded place is
	 * less attractive without being ruled out - not counting the monster or pod doing the deciding,
	 * which would otherwise tax staying put for company it IS.  See countGeneratedMonsters().
	 * <p>
	 * This replaced a scheme that split fixed shares (85% further / 10% no further / 5% closer) among
	 * the members of each category.  It guaranteed those totals however the map branched, but the cost
	 * was that a candidate's weight depended on how many siblings it happened to have: holding fell from
	 * 10 to 3.3 merely because two lateral moves existed, a character multiplying that 3.3 still lost to
	 * an empty outward tile starting from 42.5, and a category with no members at all left its share
	 * unclaimed so a lone retreat could outrank three level moves.  Per-candidate weights have none of
	 * those interactions - what a place is worth no longer depends on what else is on offer.
	 * <p>
	 * The trade accepted knowingly: the CHANCE OF MOVING OUTWARD AT ALL now grows with the number of
	 * outward options, since five ways further are five weights of 25 against one 5 for holding.  A
	 * monster in open country therefore wanders more than one hemmed in - which reads as reasonable, and
	 * is the price of every candidate being scored on its own merits.
	 */
	private static void applyChanceWeights(ArrayList<MoveOption> options) {
		for (MoveOption option:options) {
			double weight = option.farther?25.0:(option.same?5.0:1.0);
			if (option.characters>0) weight += 200.0;
			weight /= (1.0 + option.generated/4.0);
			option.weight = weight;
		}
	}
	/**
	 * Pick one option and return its index.  Written rules take the best score, ties broken at random.
	 * By chance draws in proportion to the weights built by applyChanceWeights().
	 */
	private static int choose(ArrayList<MoveOption> options,boolean byChance) {
		if (options.isEmpty()) return -1;
		if (!byChance) {
			int best = Integer.MIN_VALUE;
			ArrayList<Integer> tied = new ArrayList<>();
			for (int i=0;i<options.size();i++) {
				int score = options.get(i).ruleScore;
				if (score>best) {
					best = score;
					tied.clear();
					tied.add(i);
				}
				else if (score==best) {
					tied.add(i);
				}
			}
			return tied.get(RandomNumber.getRandom(tied.size()));
		}
		applyChanceWeights(options);
		double total = 0.0;
		for (MoveOption option:options) {
			total += option.weight;
		}
		for (MoveOption option:options) {
			option.odds = total>0.0?100.0*option.weight/total:0.0;
		}
		if (total<=0.0) return RandomNumber.getRandom(options.size());
		// getRandom(n) yields 0..n-1; scale that into the weight total.
		double roll = RandomNumber.getRandom(1000000)/1000000.0*total;
		double running = 0.0;
		for (int i=0;i<options.size();i++) {
			running += options.get(i).weight;
			if (roll<running) return i;
		}
		return options.size()-1;
	}
	/**
	 * The odds every option was given, one per line.  Only meaningful once choose() has run with
	 * byChance - that is what fills in the weights and odds.
	 * <p>
	 * What drove those odds is NOT spelled out per line: the report would be unreadable, and the same
	 * handful of rules is behind every line anyway.  They are explained once, in prose, behind the
	 * report's About button - see getPropagationAboutText().
	 */
	private static String describeOdds(ArrayList<MoveOption> options,MonsterChitComponent monster) {
		StringBuilder sb = new StringBuilder();
		for (MoveOption option:options) {
			String where = option.hold?"stay put"
				:(option.clearing!=null
					?(option.clearing.isEdge()?"off the board":String.valueOf(option.clearing.getTileLocation()))
					:option.tile.getTileName());
			// A live option must never print as 0%: weights now span 1 to 200+, so genuine long shots
			// round to zero and would read as impossible.
			String odds = option.odds>0.0 && option.odds<0.5?"<1%":String.format("%3.0f%%",option.odds);
			sb.append(String.format("    %-24s %4s%n",where,odds));
		}
		DebugUtility.diag("[GMP]   odds "+monster.getGameObject().getName()+"\n"+sb);
		return sb.toString();
	}
	/**
	 * Plain-English explanation of how a generated monster picks where to go, for the About button on
	 * the Daily Propagation report.  Written for a player mid-game, so it describes the pull of each
	 * consideration and never the arithmetic - anyone wanting the actual weights has the odds column
	 * in front of them, and the formulas live in applyChanceWeights() and ruleScore().
	 * <p>
	 * It reads the host's settings and describes only what is actually switched on, so a player is
	 * never told about a rule this game is not using.  It is composed on the HOST and shipped to the
	 * clients with the report, which is why it takes hostPrefs rather than reading them itself -
	 * clients cannot see the host's preferences.
	 */
	public static String getPropagationAboutText(HostPrefWrapper hostPrefs) {
		boolean byChance = hostPrefs!=null && hostPrefs.hasPref(Constants.HOUSE3_GM_MOVE_BY_CHANCE);
		boolean individually = hostPrefs!=null && hostPrefs.hasPref(Constants.HOUSE3_GM_MOVE_INDIVIDUALLY);
		boolean noRevenge = hostPrefs!=null && hostPrefs.hasPref(Constants.HOUSE3_GM_NO_REVENGE);
		StringBuilder sb = new StringBuilder();
		sb.append("Once each day, every generated monster already on the board considers where to go.\n");
		sb.append("It weighs the place it is standing against each neighbouring place it could reach -\n");
		sb.append("clearings if it walks, whole tiles if it flies.\n\n");

		sb.append("WHAT PULLS A MONSTER\n\n");
		sb.append("  Characters.  A character outweighs everything else put together.  Any place one\n");
		sb.append("  stands in becomes the monster's overwhelming preference, wherever that place lies\n");
		sb.append("  - it will happily turn back toward its own generator to reach one.  What matters\n");
		sb.append("  is that a character is there at all, not how many: a lone adventurer draws as\n");
		sb.append("  hard as a whole party.  A character caught partway along a roadway is not safely\n");
		sb.append("  between places - it counts as present at both ends of that road, so either end\n");
		sb.append("  may come for it.\n\n");
		sb.append("  Distance from its generator.  With nobody about, a monster wanders outward: a\n");
		sb.append("  move further out is worth several times one that keeps its distance, and many\n");
		sb.append("  times one that heads back toward the lair that spawned it.  What counts is where\n");
		sb.append("  the move leaves it, not whether it moved - a sideways step that keeps its\n");
		sb.append("  distance is worth exactly what standing still is worth.\n\n");
		sb.append("  Crowding.  Generated monsters already standing somewhere make it less appealing.\n");
		sb.append("  This thins a swarm out across the map; it never rules a place out entirely.\n\n");
		sb.append("  The board edge.  For a walking monster, leaving the realm is simply another way of\n");
		sb.append("  moving outward.  One that takes it is gone from the game for good.\n\n");

		sb.append("HOW THE CHOICE IS MADE\n\n");
		if (byChance) {
			sb.append("  This game has GENERATED MONSTERS MOVE BY CHANCE switched on, so the pulls above\n");
			sb.append("  become odds rather than a verdict, and the report lists them.  The most tempting\n");
			sb.append("  destination is the most likely one, not a certainty - you can work out where a\n");
			sb.append("  monster will probably go, but never where it must go.\n\n");
			sb.append("  Where several places are equally worth moving to, they share the chance of moving\n");
			sb.append("  that way between them.  Having many ways outward does not make a monster more\n");
			sb.append("  likely to head outward; it only spreads that likelihood among them.\n\n");
		}
		else {
			sb.append("  This game follows the written rules: the most tempting destination simply wins,\n");
			sb.append("  and a tie is broken at random.  Given the board, you can work out exactly where a\n");
			sb.append("  monster will go.  (The host can switch on GENERATED MONSTERS MOVE BY CHANCE to\n");
			sb.append("  turn that verdict into odds instead, and this report will then list them.)\n\n");
		}
		sb.append("  Staying put is always one of the choices, and a monster that stays put still grows.\n\n");

		if (!individually) {
			sb.append("  Monsters spawned together move together, as a pod: they decide once and the whole\n");
			sb.append("  group goes the same way.  (The host can switch on GENERATED MONSTERS MOVE\n");
			sb.append("  INDIVIDUALLY to have each one choose for itself.)\n\n");
			sb.append("  A pod keeps its identity for life.  Two pods that end up in the same clearing do\n");
			sb.append("  NOT become one - they stay separate bodies, each deciding for itself, and each\n");
			sb.append("  counts the other as crowding just as it would any other monster standing there.\n");
			sb.append("  So a shared clearing pushes both of them to move on, rather than settling into a\n");
			sb.append("  single ever-growing crowd.  Each fresh batch a generator spawns is a new pod too,\n");
			sb.append("  even when it appears on top of one already sitting at the lair.\n\n");
		}
		else {
			sb.append("  Each monster chooses for itself, so a stack can split up.\n\n");
		}
		if (!noRevenge) {
			sb.append("  A monster whose generator has been destroyed hunts the character responsible, and\n");
			sb.append("  ignores everything above while it does.  It will not take any move that puts it\n");
			sb.append("  further from its quarry, and it will not leave the board while the hunt is on.\n\n");
		}

		sb.append("Once every monster has moved, and only then, the generators spawn.  Anything created\n");
		sb.append("today stays at its generator until tomorrow.");
		return sb.toString();
	}
	/*
	 * ROADWAY CHARACTERS
	 *
	 * A character partway along a roadway is invisible to the ordinary incentive scoring:
	 * TileComponent.getRealmComponentsAt() skips anything carrying "otherClearing", so such a character
	 * is counted in NEITHER clearing at the ends of its road, not merely one of them.  Left alone that
	 * lets a character on a road predict exactly where a monster at one end will go, since nothing is
	 * pulling it either way.
	 *
	 * The rule is the same at both scales: a roadway character counts ONCE for each container its road
	 * touches.  A walking monster sees it in both clearings at the ends.  A flying monster sees it in
	 * the one tile a within-tile road lies in, or in both tiles a road spanning a tile boundary
	 * touches.  Fixed here rather than in getRealmComponentsAt(), which 44 other files rely on for
	 * blocking, painting and combat.
	 */
	private static ArrayList<GameObject> getRoadwayCharacters(GameData data) {
		ArrayList<GameObject> roadway = new ArrayList<>();
		for (GameObject go:RealmObjectMaster.getRealmObjectMaster(data).getPlayerCharacterObjects()) {
			if (!go.hasThisAttribute("otherClearing")) continue;
			if (go.hasThisAttribute(Constants.DEAD)) continue;
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			if (rc!=null && rc.isCharacter()) roadway.add(go);
		}
		return roadway;
	}
	/** Roadway characters whose road has one of its ends in this clearing.  Rule 2. */
	private static int roadwayCharactersAtClearing(ArrayList<GameObject> roadwayCharacters,ClearingDetail clearing) {
		if (clearing==null) return 0;
		int count = 0;
		for (GameObject go:roadwayCharacters) {
			// isBetweenClearings() guarantees clearing, other and other.clearing are all present.
			TileLocation tl = ClearingUtility.getTileLocation(go);
			if (tl==null || !tl.isBetweenClearings()) continue;
			if (clearing.equals(tl.clearing) || clearing.equals(tl.getOther().clearing)) count++;
		}
		return count;
	}
	/**
	 * Roadway characters whose road touches this tile, counted ONCE however many of its ends are in it -
	 * so a road within one tile counts its character once there (rule 3), and a road across a tile
	 * boundary counts its character once in each of the two tiles (rule 4).
	 */
	private static int roadwayCharactersAtTile(ArrayList<GameObject> roadwayCharacters,TileComponent tile) {
		if (tile==null) return 0;
		GameObject tileGo = tile.getGameObject();
		int count = 0;
		for (GameObject go:roadwayCharacters) {
			TileLocation tl = ClearingUtility.getTileLocation(go);
			if (tl==null || !tl.isBetweenClearings()) continue;
			// TileComponent does not override equals(), so these compare the underlying GameObjects.
			if (tileGo.equals(tl.tile.getGameObject()) || tileGo.equals(tl.getOther().tile.getGameObject())) count++;
		}
		return count;
	}
	/**
	 * A generated monster's chosen move, kept separate from carrying it out.  Choosing and applying are
	 * split so that a pod - a body of monsters spawned together and stamped with one
	 * {@link Constants#GM_POD_ID} - can be scored ONCE and then all moved to the
	 * same destination, instead of each fanning out on its own roll - see propagatePod().  Nothing else
	 * about the scoring changes: a monster moved on its own goes through exactly the same two steps.
	 */
	public static class GeneratedMonsterMove {
		public final TileLocation destination; // null when the monster leaves the board
		public final boolean leavesBoard;
		public final boolean holds;            // destination is where the monster already stands
		public String odds;                    // per-option odds breakdown, null unless MOVE BY CHANCE
		private GeneratedMonsterMove(TileLocation destination,boolean leavesBoard,boolean holds) {
			this.destination = destination;
			this.leavesBoard = leavesBoard;
			this.holds = holds;
		}
		static GeneratedMonsterMove to(TileLocation destination) {
			return new GeneratedMonsterMove(destination,false,false);
		}
		static GeneratedMonsterMove offBoard() {
			return new GeneratedMonsterMove(null,true,false);
		}
		/**
		 * The monster chose its own location.  Distinct from a null result - a monster that holds is
		 * still present in its clearing and still grows; it simply is not relocated or reported.
		 */
		static GeneratedMonsterMove hold(TileLocation here) {
			return new GeneratedMonsterMove(here,false,true);
		}
		GeneratedMonsterMove explain(String oddsText) {
			this.odds = oddsText;
			return this;
		}
	}
	public static void moveGeneratedMonster(MonsterChitComponent monster, HostPrefWrapper hostPrefs) {
		if (monster.isBlocked() || monster.getCurrentLocation()==null) return;
		GeneratedMonsterMove move = chooseGeneratedMonsterMove(monster,hostPrefs);
		if (move==null) return;
		applyGeneratedMonsterMove(monster,move,hostPrefs);
	}
	/**
	 * Score the monster's options and pick one, without changing any game state.  Returns null when
	 * there is nowhere to go, or when the monster chooses to hold position.
	 * <p>
	 * <b>Scoring.</b>  Every candidate scores distance from the monster's generator plus twice its
	 * interest - characters draw it in (+20 each), generated monsters already there push it away
	 * (-1 each), and distance from home is what makes a monster with nothing better to do wander.
	 * <p>
	 * <b>Selection.</b>  By default the best score wins outright, ties broken at random - pickHighest(),
	 * which is what the written rules say and so is the default.  With HOUSE3_GM_MOVE_BY_CHANCE on, the
	 * scores instead become weights and one candidate is drawn in proportion - pickWeighted().  A
	 * destination twice as attractive is then twice as likely rather than certain, so a character can
	 * work out the odds on a monster's next move but never the answer.  That option is a deliberate
	 * departure from the rules, which is why it is opt-in where the other generated-monster options are
	 * opt-out.
	 * <p>
	 * On top of that base:
	 * <ol>
	 * <li><b>Holding position is always an option.</b>  The monster's current clearing (walking) or
	 *     current tile (flying) is scored alongside the places it could move to, so a character
	 *     standing where the monster already is counts for exactly as much as one in an adjacent
	 *     clearing or tile.  Choosing it yields a holding move, NOT null: the monster is not relocated
	 *     and nothing is reported, but it is still in its clearing and still grows.
	 *     <p>
	 *     The monster does not count itself as crowding, nor its pod-mates when the pod moves as one
	 *     body; only strangers count.  Both selections are affected - a monster should no more be
	 *     driven out of its own clearing by its own presence under the written rules than by
	 *     chance.
	 *     <p>
	 *     Under MOVE BY CHANCE holding gets no rule of its own: it is simply the option that leaves the
	 *     monster as far out as it was, and is weighted 5 like any lateral step - see
	 *     applyChanceWeights().</li>
	 * <li><b>A walking monster counts a roadway character in BOTH clearings</b> at the ends of that
	 *     road, so a monster at either end is drawn to the road rather than ignoring it.
	 *     <p>
	 *     Under the written rules this does NOT make holding and walking the road equally likely: the
	 *     character's bonus lands on both ends and cancels, so distance from the generator decides and a
	 *     character can read the answer off the board.
	 *     <p>
	 *     By chance both ends take the same flat +200, so what separates them is only the distance term:
	 *     205 for the end that keeps the monster level against 225 for one that carries it further out.
	 *     A road running crosswise leaves the two genuinely even at 205 apiece - the character cannot
	 *     tell which end it will be met at, which is the point of the rule.</li>
	 * <li><b>A flying monster counts a within-tile roadway character once, in that tile.</b></li>
	 * <li><b>A flying monster counts a roadway character whose road crosses a tile boundary once in
	 *     each of the two tiles</b> the road touches.</li>
	 * </ol>
	 * Rules 2 to 4 are one rule at two scales: a roadway character counts once for each container its
	 * road touches.  Without them such a character is invisible to scoring entirely - see the note on
	 * getRoadwayCharacters().  All of this only bites when nothing else competes; an ordinary character
	 * standing in a clearing contributes through the normal incentive and breaks the tie.
	 * <p>
	 * The eligibility guards live in the CALLER, not here - moveGeneratedMonster() checks isBlocked()
	 * and a null location before asking, and propagatePod() checks every pod member.  Anything else
	 * that calls this directly has to do the same; scoring a blocked monster will happily return a
	 * destination it is not allowed to use.
	 */
	public static GeneratedMonsterMove chooseGeneratedMonsterMove(MonsterChitComponent monster,HostPrefWrapper hostPrefs) {
		ArrayList<GameObject> alone = new ArrayList<>();
		alone.add(monster.getGameObject());
		return chooseGeneratedMonsterMove(monster,hostPrefs,alone);
	}
	/**
	 * As above, for a pod: every member of `deciding` is excluded from the crowding count, because the
	 * pod moves as one body and must not count its own members as company - see
	 * countGeneratedMonsters().  The pod's leader is the monster that does the scoring.
	 */
	public static GeneratedMonsterMove chooseGeneratedMonsterMove(MonsterChitComponent monster,HostPrefWrapper hostPrefs,Collection<GameObject> deciding) {
		GameObject generator = monster.getGameObject().getGameData().getGameObject(monster.getGameObject().getThisInt(Constants.GENERATOR_ID));
		TileLocation home = ClearingUtility.getTileLocation(generator);
		TileLocation current = monster.getCurrentLocation();
		TileLocation revengeLocation = null;
		int distanceFromTarget = 0;
		if (monster.getGameObject().hasThisAttribute(Constants.REVENGE)) {
			GameObject target = monster.getGameObject().getGameData().getGameObject(new Long(monster.getGameObject().getThisAttribute(Constants.REVENGE)));
			revengeLocation = ClearingUtility.getTileLocation(target);
		}
		ArrayList<GameObject> roadwayCharacters = getRoadwayCharacters(monster.getGameObject().getGameData());
		boolean byChance = hostPrefs!=null && hostPrefs.hasPref(Constants.HOUSE3_GM_MOVE_BY_CHANCE);
		if (monster.flies()) {
			if (revengeLocation!=null) {
				distanceFromTarget = ClearingUtility.getDistanceBetweenTiles(current.tile,revengeLocation.tile);
			}

			// Find tiles to move to.  The tile it is already in is one of them - a monster with nothing
			// worth moving towards is entitled to hold position, and a character standing where it is
			// should weigh the same as one in an adjacent tile.
			int currentDfh = ClearingUtility.getDistanceBetweenTiles(current.tile,home.tile);
			ArrayList<MoveOption> options = new ArrayList<>();
			ArrayList<TileComponent> tileOptions = new ArrayList<>();
			tileOptions.add(current.tile);
			tileOptions.addAll(current.tile.getAllAdjacentTiles());
			for (TileComponent adj:tileOptions) {
				int distanceFromHome = ClearingUtility.getDistanceBetweenTiles(adj,home.tile);
				if (revengeLocation!=null) {
					int newDistanceFromTarget = ClearingUtility.getDistanceBetweenTiles(adj,revengeLocation.tile);
					if (newDistanceFromTarget>distanceFromTarget) continue;
				}

				ArrayList<RealmComponent> present = adj.getAllClearingComponents();
				MoveOption option = new MoveOption();
				option.tile = adj;
				option.dfh = distanceFromHome;
				option.characters = countCharacters(present)+roadwayCharactersAtTile(roadwayCharacters,adj);
				option.generated = countGeneratedMonsters(present,deciding);
				option.hold = adj.getGameObject().equals(current.tile.getGameObject());
				option.farther = distanceFromHome>currentDfh;
				option.same = distanceFromHome==currentDfh;
				option.ruleScore = ruleScore(distanceFromHome,option.characters,option.generated);
				options.add(option);
			}

			if (options.isEmpty()) {
				return null;
			}

			int pick = choose(options,byChance);
			String oddsText = byChance?describeOdds(options,monster):null;
			TileComponent finalTile = options.get(pick).tile;
			// Compared by GameObject: TileComponent does not override equals(), so .equals() here would
			// silently be an identity test.
			if (finalTile.getGameObject().equals(current.tile.getGameObject())) {
				return GeneratedMonsterMove.hold(current).explain(oddsText);
			}
			return GeneratedMonsterMove.to(new TileLocation(finalTile,true)).explain(oddsText);
		}
		else {
			if (revengeLocation!=null) {
				if (revengeLocation.clearing==null) {
					distanceFromTarget = ClearingUtility.getDistanceBetweenTiles(current.tile,revengeLocation.tile);
				} else {
					distanceFromTarget = ClearingUtility.calculateClearingCount(current,revengeLocation);
				}
			}
			// Find clearing to move to.  The clearing it is already in is one of them - a monster with
			// nothing worth moving towards is entitled to hold position, and a character standing where
			// it is should weigh the same as one in an adjacent clearing.
			int currentDfh = ClearingUtility.calculateClearingCount(home,current);
			ArrayList<MoveOption> options = new ArrayList<>();
			ArrayList<ClearingDetail> clearingOptions = new ArrayList<>();
			clearingOptions.add(current.clearing);
			ArrayList<PathDetail> connected = current.clearing.getConnectedPaths();
			if (connected!=null) {
				for (PathDetail path:connected) {
					ClearingDetail other = path.findConnection(current.clearing);
					if (other!=null && !clearingOptions.contains(other)) clearingOptions.add(other);
				}
			}
			/*
			 * Walking off the board is an ordinary option, scored like any other clearing - the monster
			 * simply leaves the game.  It has to be collected separately: findConnections() resolves a
			 * tile-edge path through to the adjacent tile's clearing and returns null when there is no
			 * tile beyond, so a board edge is dropped from getConnectedPaths() entirely.  Map edges live
			 * only in getConnectedMapEdges().  Without this a walking generated monster could never
			 * leave, and the isEdge() test further down was unreachable.
			 *
			 * getEdgeAsClearing(), NOT getEdgeClearing() - the two names are a trap.  A path holds two
			 * ClearingDetails, the edge itself and the ordinary clearing it touches; getEdgeClearing()
			 * returns the ORDINARY one, which for these paths is the clearing the monster is standing
			 * in.  Adding that is a silent no-op - contains() rejects it as a duplicate and no edge
			 * option ever reaches the scorer.  getEdgeAsClearing() is the one whose isEdge() is true.
			 */
			ArrayList<PathDetail> mapEdges = current.clearing.getConnectedMapEdges();
			if (mapEdges!=null) {
				for (PathDetail path:mapEdges) {
					ClearingDetail edge = path.getEdgeAsClearing();
					if (edge!=null && !clearingOptions.contains(edge)) clearingOptions.add(edge);
				}
			}
			for (ClearingDetail other:clearingOptions) {
				/*
				 * A map edge is not a real place: it holds nothing, and asking for its distance from the
				 * generator is meaningless.  Treat it as one step further out than wherever the monster
				 * stands, so it lands in the "farther" group and competes as an ordinary way of moving
				 * away from home.  A monster hunting a revenge target never takes it - walking off the
				 * board cannot bring it closer to anyone.
				 */
				if (other.isEdge()) {
					if (revengeLocation!=null) continue;
					MoveOption edgeOption = new MoveOption();
					edgeOption.clearing = other;
					edgeOption.dfh = currentDfh+1;
					edgeOption.farther = true;
					edgeOption.ruleScore = ruleScore(currentDfh+1,0,0);
					options.add(edgeOption);
					continue;
				}
				int distanceFromHome = ClearingUtility.calculateClearingCount(home,other.getTileLocation()); // is this going to kill performance?
				if (revengeLocation!=null) {
					if (revengeLocation.clearing==null) {
						int newDistanceFromTarget = ClearingUtility.getDistanceBetweenTiles(other.getTileLocation().tile,revengeLocation.tile);
						if (newDistanceFromTarget>distanceFromTarget) continue;
					} else {
						int newDistanceFromTarget = ClearingUtility.calculateClearingCount(other.getTileLocation(),revengeLocation);
						if (newDistanceFromTarget>distanceFromTarget) continue;
					}
				}

				ArrayList<RealmComponent> present = other.getClearingComponents();
				MoveOption option = new MoveOption();
				option.clearing = other;
				option.dfh = distanceFromHome;
				option.characters = countCharacters(present)+roadwayCharactersAtClearing(roadwayCharacters,other);
				option.generated = countGeneratedMonsters(present,deciding);
				option.hold = other.equals(current.clearing);
				option.farther = distanceFromHome>currentDfh;
				option.same = distanceFromHome==currentDfh;
				option.ruleScore = ruleScore(distanceFromHome,option.characters,option.generated);
				options.add(option);
			}
			
			// A revenge monster that has caught up with its target can filter out every candidate here -
			// each neighbour is further from the target than where it already stands - leaving nothing
			// to choose from.
			if (options.isEmpty()) {
				return null;
			}

			int pick = choose(options,byChance);
			String oddsText = byChance?describeOdds(options,monster):null;
			ClearingDetail finalClearing = options.get(pick).clearing;
			// Checked BEFORE isEdge(), or a monster standing in an edge clearing would walk off the
			// board for choosing to stay put.
			if (finalClearing.equals(current.clearing)) {
				return GeneratedMonsterMove.hold(current).explain(oddsText);
			}
			if (finalClearing.isEdge()) {
				return GeneratedMonsterMove.offBoard().explain(oddsText);
			}
			return GeneratedMonsterMove.to(finalClearing.getTileLocation()).explain(oddsText);
		}
	}
	/**
	 * Carry out a move chosen by chooseGeneratedMonsterMove().  Applied per monster, so every member of
	 * a pod gets its own MOVED stamp and its own MonsterGrow roll while sharing one destination.
	 */
	public static void applyGeneratedMonsterMove(MonsterChitComponent monster,GeneratedMonsterMove move,HostPrefWrapper hostPrefs) {
		if (move.leavesBoard) {
			RealmUtility.makeDead(monster); // the monster leaves the board
			return;
		}
		// A monster that holds is already where it belongs; relocating it would emit a pointless change
		// to every client.  It still falls through to MonsterGrow below - staying put does not stop a
		// monster growing.
		if (!move.holds) {
			ClearingUtility.moveToLocation(monster.getGameObject(),move.destination);
		}
		// MonsterGrow was reached only from the walking branch before the split.  A flying monster's
		// destination is a tile-only TileLocation, so testing for a clearing keeps that exactly.
		if (move.destination.isInClearing() && monster.getGameObject().hasThisAttribute(Constants.GM_GROW)) {
			MonsterGrow table = new MonsterGrow(null,null,monster);
			DieRollBuilder builder = new DieRollBuilder(null,null,0);
			DieRoller roller = builder.createRoller(table.getTableKey(),move.destination);
			RealmLogging.logMessage("host",table.apply(null,roller));
		}
	}
	/**
	 * A day's generator summary: the text to show, and a title naming the generators behind it.
	 * The two travel together because only the phase itself knows which generators actually did
	 * anything - the report text does not name them in a form worth parsing back out.
	 */
	public static class DailyGeneratorReport {
		public final String title;
		public final String text;
		private DailyGeneratorReport(String title,String text) {
			this.title = title;
			this.text = text;
		}
	}
	/**
	 * Title the day's report after whatever generators contributed to it - "Hive Monster Propagation".
	 * Several can act on one day (two boards, or two generator types sharing a monster die), so the
	 * names are joined; past three the tail is counted rather than listed, to keep it a window title.
	 */
	private static String buildReportTitle(LinkedHashSet<String> generatorNames) {
		if (generatorNames.isEmpty()) return "Monster Propagation";
		ArrayList<String> names = new ArrayList<>(generatorNames);
		String subject;
		if (names.size()==1) subject = names.get(0);
		else if (names.size()<=3) subject = String.join(" & ",names);
		else subject = String.join(" & ",names.subList(0,2))+" and "+(names.size()-2)+" more";
		return subject+" Monster Propagation";
	}
	/**
	 * The whole daylight-start generator phase: every generated monster and visible traveler already on
	 * the board propagates ONCE, and only then do the generators spawn - so a monster created today
	 * sits at its generator for the rest of the day instead of immediately wandering off.
	 * <p>
	 * Returns the summary to broadcast and show, or null when nothing happened.
	 */
	public static DailyGeneratorReport runDailyGeneratorPhase(HostPrefWrapper hostPrefs,GameData data,DieRoller monsterDieRoller,DieRoller nativeDieRoller) {
		if (hostPrefs.getDisableSummoning() || DebugUtility.isNoSummon()) return null;
		if (monsterDieRoller==null || monsterDieRoller.getNumberOfDice()==0) return null;
		if (nativeDieRoller!=null && nativeDieRoller.getNumberOfDice()==0) {
			nativeDieRoller = null; // not a Super Realm game - the caller's roller was never rolled
		}

		/*
		 * Resolve the (monsterDie, nativeDie, boardNumber) set ONCE, so propagation can run to
		 * completion across every die before any generator spawns.  Interleaving them per-die would
		 * let a monster spawned for die A propagate on die B in the same daylight.
		 */
		ArrayList<int[]> dice = new ArrayList<>();
		ArrayList<String> boards = new ArrayList<>();
		if (!hostPrefs.getMultiBoardEnabled() || !hostPrefs.hasPref(Constants.EXP_MONSTER_DIE_PER_SET)) {
			dice.add(new int[]{monsterDieRoller.getValue(0),nativeDieRoller==null?-1:nativeDieRoller.getValue(0)});
			boards.add(null);
			if (hostPrefs.hasPref(Constants.EXP_DOUBLE_MONSTER_DIE) && monsterDieRoller.getNumberOfDice()>1) {
				dice.add(new int[]{monsterDieRoller.getValue(1),nativeDieRoller==null||nativeDieRoller.getNumberOfDice()<2?-1:nativeDieRoller.getValue(1)});
				boards.add(null);
			}
		}
		else {
			int diceRolled = monsterDieRoller.getNumberOfDice();
			int dicePerBoard = hostPrefs.hasPref(Constants.EXP_DOUBLE_MONSTER_DIE)?2:1;
			for (int i=0;i<diceRolled/dicePerBoard;i++) {
				String boardNumber = i>0?Constants.MULTI_BOARD_APPENDS.substring(i-1,i):"";
				for (int n=0;n<dicePerBoard;n++) {
					int die = dicePerBoard*i+n;
					dice.add(new int[]{monsterDieRoller.getValue(die),nativeDieRoller==null||nativeDieRoller.getNumberOfDice()<=die?-1:nativeDieRoller.getValue(die)});
					boards.add(boardNumber);
				}
			}
		}

		// PASS 1 - propagate everything already on the board.  A chit matching two rolled die values
		// still moves only once: alreadyMoved is carried across every die/board pass.
		ArrayList<GameObject> alreadyMoved = new ArrayList<>();
		ArrayList<String> propagated = new ArrayList<>();
		LinkedHashSet<String> generatorNames = new LinkedHashSet<>(); // insertion-ordered, so the title is stable
		for (int i=0;i<dice.size();i++) {
			propagateGeneratedMonsters(hostPrefs,data,alreadyMoved,propagated,generatorNames,dice.get(i)[0],boards.get(i),dice.get(i)[1]);
		}

		// PASS 2 - THEN the generators spawn.  Everything created here appears after propagation is
		// finished, so a newly spawned monster sits at its generator for the rest of the day.
		ArrayList<GameObject> alreadyGenerated = new ArrayList<>();
		ArrayList<String> spawned = new ArrayList<>();
		for (int i=0;i<dice.size();i++) {
			generateFromGenerators(data,alreadyGenerated,spawned,generatorNames,dice.get(i)[0],boards.get(i),dice.get(i)[1]);
		}

		String text = buildDailyGeneratorReport(propagated,spawned);
		return text==null?null:new DailyGeneratorReport(buildReportTitle(generatorNames),text);
	}
	private static boolean matchesPropagationDie(GameObject go,int monsterDie,int nativeDie) {
		if (go.getThisInt("monster_die")==monsterDie || go.getThisInt("monster_die2")==monsterDie) return true;
		if (nativeDie==-1) return false;
		return go.getThisInt("native_die")==nativeDie || go.getThisInt("native_die2")==nativeDie;
	}
	private static void propagateGeneratedMonsters(HostPrefWrapper hostPrefs,GameData data,ArrayList<GameObject> alreadyMoved,ArrayList<String> report,LinkedHashSet<String> generatorNames,int monsterDie,String boardNumber,int nativeDie) {
		GamePool pool = new GamePool(data.getGameObjects());

		ArrayList<String> generatedQuery = new ArrayList<>();
		generatedQuery.add(Constants.GENERATED);
		generatedQuery.add("!"+Constants.DEAD);
		ArrayList<GameObject> candidates = new ArrayList<>();
		for (GameObject go:pool.find(generatedQuery)) {
			if (!matchesPropagationDie(go,monsterDie,nativeDie)) continue;
			if (!GameObjectMatchesBoardNumber(go,boardNumber)) continue;
			if (alreadyMoved.contains(go)) continue;
			alreadyMoved.add(go);
			candidates.add(go);
			GameObject generator = data.getGameObject(go.getThisInt(Constants.GENERATOR_ID));
			if (generator!=null) generatorNames.add(generator.getName());
		}
		for (ArrayList<GameObject> pod:buildPropagationPods(hostPrefs,candidates)) {
			/*
			 * This pass runs on the HOST, inside GameServer.processNextRequest -> GameHost.applyChanges.
			 * An exception escaping here kills the GameServer thread outright: every client then sees
			 * EOFException, their GameClient thread dies, and the whole game hangs with no error shown.
			 * One misbehaving chit must not be able to do that, so failures are contained per pod.
			 */
			try {
				propagatePod(pod,report,hostPrefs);
			}
			catch(Exception ex) {
				String podName = getPodName(pod);
				RealmLogging.logMessage("host","Daily Propagation: could not move "+podName
					+" ("+ex+") - "+(pod.size()==1?"it stays":"they stay")+" put.");
				DebugUtility.diag("[GMP]   ERROR moving "+podName+": "+ex);
				ex.printStackTrace();
				continue;
			}
		}

		ArrayList<String> travelerQuery = new ArrayList<>();
		travelerQuery.add(RealmComponent.TRAVELER);
		travelerQuery.add(Constants.SPAWNED);
		travelerQuery.add("!"+RealmComponent.OWNER_ID);
		for (GameObject go:pool.find(travelerQuery)) {
			if (!matchesPropagationDie(go,monsterDie,nativeDie)) continue;
			if (!GameObjectMatchesBoardNumber(go,boardNumber)) continue;
			if (alreadyMoved.contains(go)) continue;
			alreadyMoved.add(go);

			TravelerChitComponent traveler = (TravelerChitComponent)RealmComponent.getRealmComponent(go);
			TileLocation before = traveler.getCurrentLocation();
			moveTraveler(traveler,hostPrefs);
			TileLocation current = traveler.getCurrentLocation();
			// Travelers still propagate, but deliberately stay OUT of the player-facing report -
			// that dialog is about the generated monsters.  Traced here instead.
			if (before!=null && current!=null && !before.equals(current)) {
				DebugUtility.diag("[GMP]   MOVE(traveler) "+go.getName()+"#"+go.getStringId()+" "+before+" -> "+current);
			}
		}
	}
	/**
	 * The generator flavour a monster belongs to. Now used only when breaking a pre-pod-id save's
	 * monsters into starting pods - live membership is the persistent {@link Constants#GM_POD_ID}
	 * stamp, not flavour-plus-location, so that two same-flavour pods sharing a clearing stay two pods.
	 */
	private static String getPodType(GameObject go) {
		GameObject generator = go.getGameData().getGameObject(go.getThisInt(Constants.GENERATOR_ID));
		String iconType = generator==null?null:generator.getThisAttribute("icon_type");
		return iconType!=null?("gen:"+iconType):("name:"+go.getName());
	}
	/** Unstamped monsters from a pre-pod-id save are broken into pods of at most this many. */
	private static final int LEGACY_POD_SIZE = 6;
	/**
	 * Groups the day's movers into pods by their PERSISTENT {@link Constants#GM_POD_ID} stamp.
	 * <p>
	 * Pods deliberately never combine. An earlier version rebuilt them from (location,flavour) every
	 * day, which meant two pods that happened to move into the same clearing were silently fused into
	 * one body the next morning - and, worse, thereafter excluded each other from the crowding count
	 * (see countGeneratedMonsters(), which exempts only the deciding body), so they stopped repelling
	 * one another entirely. Keying on a stamp assigned once, at spawn, keeps them distinct for life:
	 * each is scored on its own and counts the other as company it is NOT, which is the standard
	 * repellant effect applied afresh at the moment each pod decides - pods are propagated in sequence
	 * and every option is scored against the clearing as it stands right then, so a pod that has
	 * already moved today is counted where it now is.
	 */
	private static ArrayList<ArrayList<GameObject>> buildPropagationPods(HostPrefWrapper hostPrefs,ArrayList<GameObject> candidates) {
		ArrayList<ArrayList<GameObject>> pods = new ArrayList<>();
		// Opting out of pods just means every pod holds exactly one monster, so the loop below stays the
		// per-monster loop it would otherwise be and nothing downstream needs to know the difference.
		boolean podsEnabled = hostPrefs==null || !hostPrefs.hasPref(Constants.HOUSE3_GM_MOVE_INDIVIDUALLY);
		if (!podsEnabled) {
			for (GameObject go:candidates) {
				ArrayList<GameObject> pod = new ArrayList<>();
				pod.add(go);
				pods.add(pod);
			}
			return pods;
		}

		assignLegacyPodIds(candidates);

		// Keyed on the stamp AND current location. The stamp is what stops two pods merging; the location
		// is what keeps the "a pod is one body standing in one place" invariant that propagatePod()
		// depends on - it scores the leader's position and then moves every member there, so a pod whose
		// members had drifted apart would teleport the stragglers. Members can be separated by things
		// outside propagation (one blocked while the rest moved, killed, relocated by a spell), which the
		// old location-derived grouping made impossible by construction. A split subgroup is re-stamped
		// with a fresh id below, so the halves stay separate for good rather than snapping back together
		// if they ever reunite.
		ArrayList<String> podKeys = new ArrayList<>();            // parallel to pods
		ArrayList<TileLocation> podLocations = new ArrayList<>(); // parallel to pods
		for (GameObject go:candidates) {
			String podId = go.getThisAttribute(Constants.GM_POD_ID);
			TileLocation tl = ClearingUtility.getTileLocation(go);
			int found = -1;
			for (int i=0;i<pods.size();i++) {
				if (!podKeys.get(i).equals(podId==null?"":podId)) continue;
				TileLocation other = podLocations.get(i);
				// Deliberately matched with TileLocation.equals() rather than a string key: tile names
				// are not unique once multiple boards are in play.
				if (other==null?tl==null:other.equals(tl)) {
					found = i;
					break;
				}
			}
			if (found>=0) {
				pods.get(found).add(go);
			}
			else {
				ArrayList<GameObject> pod = new ArrayList<>();
				pod.add(go);
				pods.add(pod);
				podKeys.add(podId==null?"":podId);
				podLocations.add(tl); // null for a monster with no location - it can never pod up, and
									  // propagatePod() drops it exactly as a lone monster always was
			}
		}
		// Re-stamp any pod that turned out to be a split remnant, so the pieces never re-merge.
		ArrayList<String> seenIds = new ArrayList<>();
		for (ArrayList<GameObject> pod:pods) {
			String podId = pod.get(0).getThisAttribute(Constants.GM_POD_ID);
			if (podId!=null && !seenIds.contains(podId)) {
				seenIds.add(podId);
				continue;
			}
			String freshId = "p"+pod.get(0).getStringId();
			for (GameObject go:pod) go.setThisAttribute(Constants.GM_POD_ID,freshId);
			seenIds.add(freshId);
			DebugUtility.diag("[GMP] pod split: "+pod.size()+" member(s) of "+podId
				+" separated at "+ClearingUtility.getTileLocation(pod.get(0))+" -> new pod "+freshId);
		}
		return pods;
	}
	/**
	 * Stamps any monster that predates {@link Constants#GM_POD_ID} - i.e. everything already standing on
	 * the board in a game saved before pods became persistent. Co-located monsters of one flavour are
	 * split into pods of {@link #LEGACY_POD_SIZE}, plus a remainder pod for whatever is left over, so a
	 * long-accumulated stack disperses as several bodies that repel each other rather than as one
	 * immovable crowd. Runs once per monster: after this, the stamp is what identifies the pod, and a
	 * pod that later loses members simply shrinks.
	 */
	private static void assignLegacyPodIds(ArrayList<GameObject> candidates) {
		ArrayList<GameObject> unstamped = new ArrayList<>();
		for (GameObject go:candidates) {
			if (go.getThisAttribute(Constants.GM_POD_ID)==null) unstamped.add(go);
		}
		if (unstamped.isEmpty()) return;

		ArrayList<TileLocation> groupLocations = new ArrayList<>();
		ArrayList<String> groupTypes = new ArrayList<>();
		ArrayList<ArrayList<GameObject>> groups = new ArrayList<>();
		for (GameObject go:unstamped) {
			TileLocation tl = ClearingUtility.getTileLocation(go);
			String type = getPodType(go);
			int found = -1;
			if (tl!=null) {
				for (int i=0;i<groups.size();i++) {
					TileLocation other = groupLocations.get(i);
					// Deliberately matched with TileLocation.equals() rather than a string key: tile names
					// are not unique once multiple boards are in play.
					if (other!=null && other.equals(tl) && type.equals(groupTypes.get(i))) {
						found = i;
						break;
					}
				}
			}
			if (found>=0) {
				groups.get(found).add(go);
			}
			else {
				ArrayList<GameObject> group = new ArrayList<>();
				group.add(go);
				groups.add(group);
				groupLocations.add(tl);
				groupTypes.add(type);
			}
		}

		for (ArrayList<GameObject> group:groups) {
			String podId = null;
			int inPod = 0;
			for (GameObject go:group) {
				if (podId==null || inPod==LEGACY_POD_SIZE) {
					podId = "p"+go.getStringId();
					inPod = 0;
				}
				go.setThisAttribute(Constants.GM_POD_ID,podId);
				inPod++;
			}
			DebugUtility.diag("[GMP] assignLegacyPodIds: "+group.size()+" unstamped "+getPodType(group.get(0))
				+" at "+ClearingUtility.getTileLocation(group.get(0))+" -> "
				+((group.size()+LEGACY_POD_SIZE-1)/LEGACY_POD_SIZE)+" pod(s)");
		}
	}
	private static String getPodName(ArrayList<GameObject> pod) {
		GameObject first = pod.get(0);
		if (pod.size()==1) return first.getName(); // reads exactly as a lone monster always did
		boolean uniform = true;
		for (GameObject go:pod) {
			if (!go.getName().equals(first.getName())) {
				uniform = false;
				break;
			}
		}
		if (uniform) return pod.size()+" "+first.getName()+"s";
		GameObject generator = first.getGameData().getGameObject(first.getThisInt(Constants.GENERATOR_ID));
		return pod.size()+" "+(generator!=null?generator.getName()+" monsters":"generated monsters");
	}
	private static String getRevengeSuffix(GameObject monster) {
		if (!monster.hasThisAttribute(Constants.REVENGE)) return "";
		GameObject target;
		try {
			target = monster.getGameData().getGameObject(Long.valueOf(monster.getThisAttribute(Constants.REVENGE)));
		}
		catch(NumberFormatException ex) { // a malformed stamp must not break the report
			return "";
		}
		return target==null?"":(" (hunting "+target.getName()+")");
	}
	private static void propagatePod(ArrayList<GameObject> pod,ArrayList<String> report,HostPrefWrapper hostPrefs) {
		MonsterChitComponent leader = (MonsterChitComponent)RealmComponent.getRealmComponent(pod.get(0));
		String podName = getPodName(pod);
		/*
		 * chooseGeneratedMonsterMove() screens nothing itself - see its javadoc - so the eligibility
		 * guards moveGeneratedMonster() applies have to be applied here as well.  A pod holds ENTIRELY
		 * if ANY member is blocked; letting the rest walk off would defeat the point of moving as one.
		 */
		for (GameObject go:pod) {
			MonsterChitComponent member = (MonsterChitComponent)RealmComponent.getRealmComponent(go);
			if (member.isBlocked()) {
				DebugUtility.diag("[GMP] pod HOLDS "+podName+" at "+leader.getCurrentLocation()
					+" - "+go.getName()+"#"+go.getStringId()+" is blocked (cleared only at day end)");
				return;
			}
		}
		TileLocation before = leader.getCurrentLocation();
		if (before==null) return;
		GeneratedMonsterMove move = chooseGeneratedMonsterMove(leader,hostPrefs,pod);
		if (move==null) return;
		for (GameObject go:pod) {
			MonsterChitComponent member = (MonsterChitComponent)RealmComponent.getRealmComponent(go);
			applyGeneratedMonsterMove(member,move,hostPrefs);
			TileLocation current = member.getCurrentLocation();
			if (current!=null && current.isInClearing()) { // null once the monster has walked off a map edge
				updateMonsterBlock(member);
			}
		}
		if (report!=null) {
			// Only report pods that actually went somewhere - held and blocked ones stay silent.
			// Every outcome is reported when the odds are on show, holds included - the point of the
			// breakdown is seeing what the monster COULD have done, which a silent hold hides.
			String line = null;
			if (move.leavesBoard) {
				line = podName+" left the map from "+before;
			}
			else if (move.holds) {
				if (move.odds!=null) line = podName+getRevengeSuffix(pod.get(0))+" stayed at "+before;
			}
			else if (move.destination!=null && !before.equals(move.destination)) {
				line = podName+getRevengeSuffix(pod.get(0))+": "+before+" -> "+move.destination;
			}
			if (line!=null) {
				report.add(move.odds==null?line:(line+"\n"+move.odds.replaceAll("\\s+$","")));
			}
		}
	}
	private static void generateFromGenerators(GameData data,ArrayList<GameObject> alreadyGenerated,ArrayList<String> report,LinkedHashSet<String> generatorNames,int monsterDie,String boardNumber,int nativeDie) {
		GamePool pool = new GamePool(data.getGameObjects());
		ArrayList<GameObject> newMonsters = new ArrayList<>();
		for (GameObject go:pool.find("seen,generator,!destroyed")) {
			if (!matchesPropagationDie(go,monsterDie,nativeDie)) continue;
			if (!GameObjectMatchesBoardNumber(go,boardNumber)) continue;
			if (alreadyGenerated.contains(go)) continue;
			alreadyGenerated.add(go);

			TileLocation genTl = ClearingUtility.getTileLocation(go);
			if (genTl!=null) {
				// Keep the once-a-day marker truthful even though the daily phase is now the only thing
				// that spawns generators - it is what every other summoning path tests, and leaving it
				// unset would let any future EOCTMS path spawn the same generator again the same day.
				StateChitComponent stateChit = (StateChitComponent)RealmComponent.getRealmComponent(go);
				if (stateChit.hasSummonedToday(monsterDie)) continue;
				stateChit.addSummonedToday(monsterDie);
				ArrayList<GameObject> spawned = generateMonsters(go,genTl.clearing);
				DebugUtility.diag("[GMP] generator "+go.getName()+"#"+go.getStringId()+" at "+genTl
					+" spawned "+spawned.size()+" at start of Daylight (these do NOT propagate today)");
				if (!spawned.isEmpty()) {
					report.add(go.getName()+" at "+genTl+" spawned "+spawned.size()+" "+spawned.get(0).getName()
						+(spawned.size()==1?"":"s"));
					generatorNames.add(go.getName());
				}
				newMonsters.addAll(spawned);
			}
		}
		for (GameObject added:newMonsters) {
			RealmComponent rc = RealmComponent.getRealmComponent(added);
			if (rc.isMonster()) {
				updateMonsterBlock((MonsterChitComponent)rc);
			}
		}
	}
	private static String buildDailyGeneratorReport(ArrayList<String> propagated,ArrayList<String> spawned) {
		if (propagated.isEmpty() && spawned.isEmpty()) return null;
		StringBuilder sb = new StringBuilder();
		if (propagated.isEmpty()) {
			sb.append("No generated monsters moved.\n");
		}
		else {
			sb.append(propagated.size()).append(propagated.size()==1?" generated monster propagated:\n":" generated monsters propagated:\n");
			appendCapped(sb,propagated);
		}
		if (!spawned.isEmpty()) {
			sb.append("\nGenerators spawned:\n");
			appendCapped(sb,spawned);
			sb.append("\nNewly spawned monsters stay at their generator today.");
		}
		return sb.toString();
	}
	private static void appendCapped(StringBuilder sb,ArrayList<String> lines) {
		int max = 15;
		for (int i=0;i<lines.size() && i<max;i++) {
			sb.append("    ").append(lines.get(i)).append("\n");
		}
		if (lines.size()>max) {
			sb.append("    ...and ").append(lines.size()-max).append(" more\n");
		}
	}
	public static void moveTraveler(TravelerChitComponent traveler, HostPrefWrapper hostPrefs) {
		TileLocation current = traveler.getCurrentLocation();
		if (current == null) return;	// not sure why this can happen, but at least this wont throw an error anymore
		
		// Find clearing to move to
		int mostInterest = Integer.MIN_VALUE;
		HashLists<Integer,ClearingDetail> choices = new HashLists<>();
		// Include current clearing when deciding (though with one less incentive)
		choices.put(calculateIncentive(current.clearing.getClearingComponents(),-2,-1)-1,current.clearing);
		ArrayList<PathDetail> paths = current.clearing.getConnectedPaths();
		if (paths == null) return;
		for (PathDetail path:paths) {
			ClearingDetail other = path.findConnection(current.clearing);
			if (other.isEdge()) continue; // travelers don't leave the map

			// if clearing has characters in it, make it MORE interesting
			int interest = calculateIncentive(other.getClearingComponents(),-2,-1);
			
			mostInterest = Math.max(interest,mostInterest);
			choices.put(interest,other);
		}
		
		// Randomly choose from furthest locations
		ArrayList<ClearingDetail> finalChoices = choices.getList(mostInterest);
		int r = RandomNumber.getRandom(finalChoices.size());
		ClearingDetail finalClearing = finalChoices.get(r);
		
		if (!current.clearing.equals(finalClearing)) {
			TileLocation tl = finalClearing.getTileLocation();
			ClearingUtility.moveToLocation(traveler.getGameObject(),tl);
		}
	}
	private static int getDieRollForString(String dieString) {
		String[] s = dieString.toLowerCase().split("d");
		int count = Integer.parseInt(s[0]);
		int sides = Integer.parseInt(s[1]);
		int total = 0;
		for (int i=0;i<count;i++) {
			total += RandomNumber.getDieRoll(sides);
		}
		return total;
	}

	private static ArrayList<GameObject> getWarnings(Collection<GameObject> gameObjects,int monsterDie,boolean includeWarningSounds,HostPrefWrapper hostPrefs) {
		ArrayList<GameObject> gos = new ArrayList<>(gameObjects);
		
		// Find all "seen" treasures
		ArrayList<RealmComponent> seen = new ArrayList<>();
		for (GameObject go : gameObjects) {
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			seen.addAll(ClearingUtility.dissolveIntoSeenStuff(rc));
		}
		for (RealmComponent rc : seen) {
			if (!gos.contains(rc.getGameObject())) {
				gos.add(rc.getGameObject());
			}
		}
		
		// Now process warnings
		ArrayList<GameObject> warnings = new ArrayList<>();
		for (GameObject go : gos) {
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			if (!rc.isDwelling() && go.hasThisAttribute(RealmComponent.WARNING)) {
				if (rc instanceof WarningChitComponent) {
					if (includeWarningSounds) { // only add warning chits if allowed
						WarningChitComponent warning = (WarningChitComponent)rc;
						if (!warning.hasSummonedToday(monsterDie) || hostPrefs.hasPref(Constants.HOUSE2_MULTIPLE_SUMMONING)) { // only summon once per day (rule 12.5/3)
							warnings.add(go);
						}
					}
				}
				else {
					// Non-warning chits (like Dragon Essence) are always added
					warnings.add(go);
				}
			}
		}
		return warnings;
	}

	/**
	 * Cycles through the otherLocations list (sorted previously by box_num) and returns the first
	 * location that matches up with the summon name.  This method is used by the summonMonsters(...) method
	 * to determine which monsters/natives are summoned for a given warning or sound chit name.
	 */
	public static GameObject getFirstLocationWithSummonName(ArrayList<GameObject> otherLocations,String name,String tileType,String boardNum,Collection<String> redChitNames) {
		if (tileType==null) tileType = "";
		String nameWithType = (name+" "+tileType).toLowerCase(); // ie., roar M
		for (GameObject loc : otherLocations) {
			String locBoardNum = loc.getThisAttribute(Constants.BOARD_NUMBER);
			if ((boardNum==null && locBoardNum==null) || (boardNum!=null && boardNum.equals(locBoardNum))) {
				if (loc.getHoldCount()>0) {
					String summon = loc.getThisAttribute("summon"); // Maybe this should be an AttributeList...
					if (summon!=null) {
						StringTokenizer tokens = new StringTokenizer(summon,",");
						while(tokens.hasMoreTokens()) {
							String test = tokens.nextToken().toLowerCase().trim();
							if (nameWithType.indexOf(test)>=0) {
								return loc;
							}
							if (redChitNames!=null) {
								for (String redChit : redChitNames) {
									String nameWithRedChit = (redChit+" "+name).toLowerCase(); // ie., lost city patter
									if (nameWithRedChit.indexOf(test)>=0) {
										return loc;
									}
								}
							}
						}
					} // else ---- This is probably a non-monster (Native, Treasure, Item, etc.)
				}
			}
		}
		return null;
	}
	
	private static GameObject getFirstLocationWithTsSection(ArrayList<GameObject> otherLocations,String name,String boardNum) {
		name = name.toLowerCase();
		for (GameObject loc : otherLocations) {
			String locBoardNum = loc.getThisAttribute(Constants.BOARD_NUMBER);
			if ((boardNum==null && locBoardNum==null) || (boardNum!=null && boardNum.equals(locBoardNum))) {
				if (loc.getHoldCount()>0) {
					String section = loc.getThisAttribute("ts_section");
					if (section!=null && section.matches(name)) {
							return loc;
					}
				}
			}
		}
		return null;
	}

	public static void summonMonsters(HostPrefWrapper hostPrefs,ArrayList<GameObject> summoned,CharacterWrapper character,DieRoller monsterDieRoller,DieRoller nativeDieRoller) {
		if (!hostPrefs.getMultiBoardEnabled() || !hostPrefs.hasPref(Constants.EXP_MONSTER_DIE_PER_SET)) {
			SetupCardUtility.summonMonsters(hostPrefs,summoned,character,monsterDieRoller.getValue(0),null,nativeDieRoller==null?-1:nativeDieRoller.getValue(0));
			if (hostPrefs.hasPref(Constants.EXP_DOUBLE_MONSTER_DIE) && (monsterDieRoller.getValue(0)!=monsterDieRoller.getValue(1) || (nativeDieRoller!=null && nativeDieRoller.getValue(0)!=nativeDieRoller.getValue(1)))) {
				SetupCardUtility.summonMonsters(hostPrefs,summoned,character,monsterDieRoller.getValue(1),null,nativeDieRoller==null?-1:monsterDieRoller.getValue(1));
			}
		}
		else {
			int diceRolled = monsterDieRoller.getNumberOfDice();
			if (hostPrefs.hasPref(Constants.EXP_DOUBLE_MONSTER_DIE)) {
				for (int i=0; i<diceRolled/2; i++) {
					String boardNumber = "";
					if (i>0) {
						boardNumber = Constants.MULTI_BOARD_APPENDS.substring(i-1, i);
					}
					SetupCardUtility.summonMonsters(hostPrefs,summoned,character,monsterDieRoller.getValue(2*i),boardNumber,nativeDieRoller==null?-1:nativeDieRoller.getValue(2*i));
					if (monsterDieRoller.getValue(2*i)!=monsterDieRoller.getValue(2*i+1) || (nativeDieRoller!=null && nativeDieRoller.getValue(2*i)!=nativeDieRoller.getValue(2*i+1))) {
						SetupCardUtility.summonMonsters(hostPrefs,summoned,character,monsterDieRoller.getValue(2*i+1),boardNumber,nativeDieRoller==null?-1:nativeDieRoller.getValue(2*i+1));
					}
				}
			}
			else {
				for (int i=0; i<diceRolled; i++) {
					String boardNumber = "";
					if (i>0) {
						boardNumber = Constants.MULTI_BOARD_APPENDS.substring(i-1, i);
					}
					SetupCardUtility.summonMonsters(hostPrefs,summoned,character,monsterDieRoller.getValue(i),boardNumber,nativeDieRoller==null?-1:nativeDieRoller.getValue(i));
				}
			}
		}
	}
	
	public static void summonMonsters(HostPrefWrapper hostPrefs,ArrayList<GameObject> summoned,CharacterWrapper character,int monsterDie, int nativeDie) {
		summonMonsters(hostPrefs, summoned, character, monsterDie, null, nativeDie);
	}
	
	public static void summonMonsters(HostPrefWrapper hostPrefs,ArrayList<GameObject> summoned,CharacterWrapper character,int monsterDie, String boardNumber,int nativeDie) {
		if (!character.isMinion() && !character.isSleep()) { // Minions and sleeping characters do not summon monsters or prowling denizens
			TileLocation current = character.getCurrentLocation();
			if ((!character.getNoSummon() || hostPrefs.hasPref(Constants.SR_NO_SUMMONING_FOR_FOLLOWERS)) && !character.getGameObject().hasThisAttribute(Constants.NO_SUMMONING)) { // Only the "first" follower in the "group" summons monsters!				
				boolean peacefulDay = false;
				if (current.tile!=null & current.tile.getGameObject().hasThisAttribute(Constants.EVENT_PEACEFUL_DAY)) {
					peacefulDay = true;
				}
				
				boolean atPeaceWithNature = character.affectedByKey(Constants.PEACE_WITH_NATURE);
				boolean warningSounds = !atPeaceWithNature && !peacefulDay;
				boolean prowling = true;
				
				boolean lull = character.getGameObject().hasAttribute(Constants.OPTIONAL_BLOCK,Constants.DRUID_LULL) || character.getGameObject().hasThisAttribute(Constants.DRUID_LULL);
				boolean siteChits = !lull && !peacefulDay;
				
				if (atPeaceWithNature && hostPrefs.hasPref(Constants.HOUSE2_PEACE_WITH_NATURE_SITES)) {
					siteChits = false;
				}
				if (character.isHidden() && hostPrefs.hasPref(Constants.OPT_QUIET_MONSTERS)) {
					warningSounds = false;
					siteChits = false;
				}
				if (character.getGameObject().hasThisAttribute(Constants.NO_PROWLING)) {
					prowling = false;
				}
				
				summonMonsters(summoned,current,character.getGameObject().getGameData(),warningSounds,siteChits,monsterDie,boardNumber,nativeDie,prowling);
			}
		}
	}

	public static boolean resetDenizen(GameObject denizen) {
		if (denizen.hasThisAttribute(Constants.DEAD_PERMANENT)) {
			return false;
		}
		
		denizen.removeThisAttribute("needs_init");
		
		// Remove tile clearing definition
		denizen.removeThisAttribute("clearing");
		
		// Remove Player controlling character
		(new CharacterWrapper(denizen)).removePlayerName();
		
		// Remove all DEAD designations and leftover killedBy info - make sure light side up too!
		CombatWrapper.clearAllCombatInfo(denizen);
		denizen.removeThisAttribute(Constants.DEAD);
		denizen.removeThisAttribute(Constants.SERIOUS_WOUND);
		ChitComponent rc = (ChitComponent)RealmComponent.getRealmComponent(denizen);
		rc.setLightSideUp();
		if (rc.isNative()) {
			NativeSteedChitComponent horse = (NativeSteedChitComponent)rc.getHorseIncludeDead();
			if (horse!=null) {
				if (!horse.getGameObject().hasThisAttribute(Constants.DEAD_PERMANENT)) {
					horse.setLightSideUp();
					CombatWrapper.clearAllCombatInfo(horse.getGameObject());
					horse.getGameObject().removeThisAttribute(Constants.DEAD);
				}
			}
		}
		
		if (rc.isMonster()) {
			MonsterChitComponent monster = (MonsterChitComponent)rc;
			MonsterPartChitComponent shield = monster.getShield();
			if (shield != null) {
				shield.setDamaged(false);
				shield.setDestroyed(false);
			}
			NativeSteedChitComponent horse = (NativeSteedChitComponent)rc.getHorseIncludeDead();
			if (horse!=null) {
				if (!horse.getGameObject().hasThisAttribute(Constants.DEAD_PERMANENT)) {
					horse.setLightSideUp();
					CombatWrapper.clearAllCombatInfo(horse.getGameObject());
					horse.getGameObject().removeThisAttribute(Constants.DEAD);
				}
			}
		}
		
		GameObject denizenHolder = SetupCardUtility.getDenizenHolder(denizen);
		if (denizenHolder!=null&&denizen.hasThisAttribute("garrison")) {
			// Garrison natives return to the board immediately
			TileLocation tl = ClearingUtility.getTileLocation(denizenHolder);
			tl.clearing.add(denizen,null);
		}
		else if (denizenHolder!=null) {
			// Make sure to cancel any bewitching spells when returning to setup card!
			SpellMasterWrapper smw = SpellMasterWrapper.getSpellMaster(denizen.getGameData());
			smw.expireBewitchingSpells(rc.getGameObject(),null);
			
			// Return denizen to their holding box
			denizenHolder.add(denizen);
		}
		
		if (denizenHolder!=null) {
			RealmComponent dh = RealmComponent.getRealmComponent(denizenHolder);
			if (dh instanceof WarningChitComponent) {
				// Special case for Ghosts trapped in a warning chit - they always pop back out to the tile
				WarningChitComponent warning = (WarningChitComponent)dh;
				warning.setFaceUp();
			}
			
			if (denizen.hasThisAttribute(Constants.NATIVE_CACHE)) {
				GameObject cache = denizen.getGameData().getGameObject(new Long(denizen.getThisAttribute(Constants.NATIVE_CACHE)));
				ArrayList<GameObject> allItems = new ArrayList<>();
				for (GameObject item : cache.getHold()) {
					allItems.add(item);
				}
				for (GameObject item : allItems) {
					denizenHolder.add(item);
				}
				cache.removeThisAttribute("clearing");
				GameObject holder = cache.getHeldBy();
				if (holder!=null) {
					holder.remove(cache);
				}
				denizen.removeThisAttribute(Constants.NATIVE_CACHE);
				RealmUtility.sortGameObjectsHold(denizenHolder, false);
			}
		}
		
		if (rc.isHorse() && !rc.isNative() && !rc.getGameObject().hasThisAttribute(RealmComponent.MONSTER_STEED)) {
			SpellMasterWrapper smw = SpellMasterWrapper.getSpellMaster(denizen.getGameData());
			smw.expireBewitchingSpells(denizen,null);
			SetupCardUtility.getHorseHolder(denizen).add(denizen);
		}
		return true;
	}

	public static void updateMonsterBlock(MonsterChitComponent monster) {
		if (!monster.isMistLike()) { // Misty monsters don't block
			TileLocation prowlerLocation = ClearingUtility.getTileLocation(monster);
			String magicImmunity = monster.getGameObject().getThisAttribute(Constants.MAGIC_IMMUNITY);
			for (RealmComponent rc : prowlerLocation.clearing.getClearingComponents()) {
				if (rc.isPlayerControlledLeader() && !rc.isHidden() && (!rc.isMistLike() || monster.getGameObject().hasThisAttribute(Constants.IGNORE_MIST_LIKE)) && !rc.isImmuneTo(monster) 
					&& (!rc.getGameObject().hasThisAttribute(Constants.BLINDING_LIGHT) || (magicImmunity!=null && (magicImmunity.matches("prism") || magicImmunity.matches("purple"))))) {
					CharacterWrapper character = new CharacterWrapper(rc.getGameObject());
					if (!character.isSleep()) {
						monster.setBlocked(true);
						HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(monster.getGameObject().getGameData());
						if (!monster.isSmall() || !hostPrefs.hasPref(Constants.HOUSE3_SMALL_MONSTERS)) {
							character.setBlocked(true);
							GameClient.broadcastClient("host",monster.getGameObject().getName()+" blocks the "+character.getGameObject().getName());
						}
					}
				}
			}
		}
	}

	public static void resetGeneralDwellings(GameData data) {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(data);
		GamePool pool = new GamePool(data.getGameObjects());
		ArrayList<String> keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add(Constants.GENERAL_DWELLING);
		ArrayList<GameObject> dwellings = pool.find(keyVals);
		for (GameObject dwelling : dwellings) {
			if (!dwelling.hasThisAttribute(RealmComponent.TILE_TYPE) || !dwelling.hasThisAttribute(RealmComponent.WARNING)) continue;
			String type = dwelling.getThisAttribute(RealmComponent.TILE_TYPE);
			String warning = dwelling.getThisAttribute(RealmComponent.WARNING);
			ArrayList<String> keyValsChit = new ArrayList<>();
			keyValsChit.add(hostPrefs.getGameKeyVals());
			keyValsChit.add(RealmComponent.WARNING+"="+warning);
			keyValsChit.add("!"+RealmComponent.DWELLING);
			keyValsChit.add(RealmComponent.TILE_TYPE+"="+type);
			ArrayList<GameObject> chits = pool.find(keyValsChit);
			if (chits.size()!=1) continue;
			GameObject chit = chits.get(0);
			TileLocation chitLocation = RealmComponent.getRealmComponent(chit).getCurrentLocation();
			TileLocation dwellingLocation = RealmComponent.getRealmComponent(dwelling).getCurrentLocation();
			if (dwelling.getHeldBy()!=chit && chitLocation!=null && chitLocation.tile!=null && !chitLocation.tile.getGameObject().equals(dwelling.getHeldBy()) && dwellingLocation.tile!=null && chitLocation.tile!=dwellingLocation.tile) {
				ClearingDetail clearing5 = chitLocation.tile.getClearing(5);
				if (clearing5!=null && clearing5.isConnectsToBorderland()) {
					clearing5.add(dwelling, null);
					continue;
				}
				ClearingDetail clearing4 = chitLocation.tile.getClearing(4);
				if (clearing4!=null && clearing4.isConnectsToBorderland()) {
					clearing4.add(dwelling, null);
					continue;
				}
				ClearingDetail clearing2 = chitLocation.tile.getClearing(2);
				if (clearing2!=null && clearing2.isConnectsToBorderland()) {
					clearing2.add(dwelling, null);
					continue;
				}
				ArrayList<ClearingDetail> clearings = chitLocation.tile.getClearings();
				ClearingDetail clearing = clearings.get(RandomNumber.getRandom(clearings.size()));
				clearing.add(dwelling, null);
			}
		}
	}
	
	/**
	 * All monsters and natives for the given monster die are returned to the treasure setup card
	 */
	public static void resetDenizens(GameData data,int monsterDie, boolean regenerateHorses) {
		resetDenizens(data,monsterDie,regenerateHorses,true);
	}
	public static void resetDenizens(GameData data,int monsterDie, boolean regenerateHorses, boolean seventhDay) {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(data);
		GameWrapper game = GameWrapper.findGame(data);
		GamePool pool = new GamePool(data.getGameObjects());
		
		ArrayList<String> keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die="+monsterDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add("clearing"); // this identifies those that are on tiles
		keyVals.add("!"+RealmComponent.OWNER_ID); // this identifies unhired natives
		Collection<GameObject> returning = pool.extract(keyVals);
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die2="+monsterDie);
		keyVals.add("setup_start");
		keyVals.add("clearing");
		keyVals.add("!"+RealmComponent.OWNER_ID);
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die="+monsterDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add("needs_init"); // this identifies those that need to initialized (start of game)
		returning.addAll(pool.extract(keyVals));
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die2="+monsterDie);
		keyVals.add("setup_start");
		keyVals.add("needs_init");
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die="+monsterDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add(Constants.DEAD); // this identifies those that are DEAD
		returning.addAll(pool.extract(keyVals));
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die2="+monsterDie);
		keyVals.add("setup_start");
		keyVals.add(Constants.DEAD);
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("monster_die=99"); // the ghosts
		keyVals.add("setup_start");
		keyVals.add("monster");
		keyVals.add(Constants.DEAD); // but only if the ghosts are dead!
		returning.addAll(pool.extract(keyVals));
		
		if (!returning.isEmpty()) {
			if (seventhDay) {
				GameClient.broadcastClient("host","7th day - denizens return to setup card:");
			}
			else {
				GameClient.broadcastClient("host","Denizens return to setup card:");
			}
		}
		
		for (GameObject denizen : returning) {
			GameClient.broadcastClient("host"," - "+denizen.getName());
			if (resetDenizen(denizen)) {
				game.addRegeneratedDenizen(denizen);
			}
		}
		
		if (regenerateHorses) {
			keyVals = new ArrayList<>();
			keyVals.add(hostPrefs.getGameKeyVals());
			keyVals.add("horse");
			keyVals.add("!native");
			keyVals.add("!monster_steed");
			keyVals.add(Constants.DEAD);
			for (GameObject horse : pool.extract(keyVals)) {
				resetDenizen(horse);
			}
		}
		
		// Flip all visitor/mission chits
		if (!hostPrefs.hasPref(Constants.HOUSE2_NO_MISSION_VISITOR_FLIPSIDE)&&!hostPrefs.usesSuperRealm()) {
			flipGoldSpecialChits(hostPrefs,pool,monsterDie);
		}
	}
	
	/**
	 * All natives for the given native die are returned to the Chart of Clans
	 */
	public static void resetNatives(GameData data,int nativeDie) {
		resetNatives(data,nativeDie,true);
	}
	public static void resetNatives(GameData data,int nativeDie,boolean seventhDay) {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(data);
		GameWrapper game = GameWrapper.findGame(data);
		GamePool pool = new GamePool(data.getGameObjects());
		
		ArrayList<String> keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die="+nativeDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add("clearing"); // this identifies those that are on tiles
		keyVals.add("!"+RealmComponent.OWNER_ID); // this identifies unhired natives
		Collection<GameObject> returning = pool.extract(keyVals);
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die2="+nativeDie);
		keyVals.add("setup_start");
		keyVals.add("clearing");
		keyVals.add("!"+RealmComponent.OWNER_ID);
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die="+nativeDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add("needs_init"); // this identifies those that need to initialized (start of game)
		returning.addAll(pool.extract(keyVals));
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die2="+nativeDie);
		keyVals.add("setup_start");
		keyVals.add("needs_init");
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die="+nativeDie);
		keyVals.add("setup_start"); // this should get all monsters and natives
		keyVals.add(Constants.DEAD); // this identifies those that are DEAD
		returning.addAll(pool.extract(keyVals));
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("native_die2="+nativeDie);
		keyVals.add("setup_start");
		keyVals.add(Constants.DEAD);
		returning.addAll(pool.extract(keyVals));
		
		keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("setup_start");
		keyVals.add(Constants.DEAD);
		keyVals.add(Constants.SERIOUS_WOUND);
		for (GameObject woundedDenizen : pool.extract(keyVals)) {
			if (!returning.contains(woundedDenizen)) {
				returning.add(woundedDenizen);
			}
		}
		
				
		if (!returning.isEmpty()) {
			if (seventhDay) {
				GameClient.broadcastClient("host","7th day - natives return to the Chart of Clans:");
			}
			else {
				GameClient.broadcastClient("host","Natives return to the Chart of Clans:");
			}
		}
		
		for (GameObject denizen : returning) {
			GameClient.broadcastClient("host"," - "+denizen.getName());
			game.addRegeneratedDenizen(denizen);
			resetDenizen(denizen);
		}
	}
	
	private static void flipGoldSpecialChits(HostPrefWrapper hostPrefs,GamePool pool,int monsterDie) {
		ArrayList<String> keyVals = new ArrayList<>();
		keyVals.add(hostPrefs.getGameKeyVals());
		keyVals.add("gold_special");
		ArrayList<GameObject> allGoldSpecial = pool.extract(keyVals);
		ArrayList<GameObject> toFlip = new ArrayList<>();
		for (GameObject side1 : allGoldSpecial) {
			if (side1.getThisInt("monster_die")!=monsterDie && side1.getThisInt("monster_die2")!=monsterDie) continue;
			GameObject holder = side1.getHeldBy();
			if (holder!=null) {
				RealmComponent rc = RealmComponent.getRealmComponent(holder);
				if (rc==null || !rc.isCharacter()) { // can't flip chit if held by a character!
					toFlip.add(side1);
				}
			}
		}
		for (GameObject side1 : toFlip) {
			GameObject side2 = side1.getGameObjectFromThisAttribute("pairid");
			GameObject holder = side1.getHeldBy();
			if (side1.hasThisAttribute("clearing")) {
				side2.setThisAttribute("clearing",side1.getThisInt("clearing"));
				side1.removeThisAttribute("clearing");
			}
			holder.add(side2);
			holder.remove(side1);
		}
	}

	public static void setupDwellingNatives(GameObject dwelling) {
		if (dwelling.getHoldCount()>0) {
			GameObject tile = dwelling.getHeldBy();
			if (tile!=null) {
				String clearing = dwelling.getThisAttribute("clearing");
				if (clearing!=null) {
					GamePool subpool = new GamePool(dwelling.getHold());
					ArrayList<GameObject> natives = subpool.find(GamePool.makeKeyVals("rank"));
					for (GameObject aNative : natives) {
						aNative.setThisAttribute("clearing",clearing);
						tile.add(aNative);
					}
				}
			}
		}
	}

	public static void setupDwellingsAndGhosts(HostPrefWrapper hostPrefs,GameData data) {
		if (!hostPrefs.hasPref(Constants.EXP_NO_DWELLING_START)) { // Make sure option is enabled before revealing dwellings
			// Dwellings and ghosts should be remapped to the appropriate tiles
			// Simply flip those chits face up, and the rest will work
			ArrayList<String> keyVals = new ArrayList<>();
			keyVals.add(hostPrefs.getGameKeyVals());
			keyVals.add(RealmComponent.WARNING);
			keyVals.add(RealmComponent.TILE_TYPE+"=V");
			keyVals.add("chit");
			GamePool pool = new GamePool(data.getGameObjects());
			Collection<GameObject> warningChits = pool.find(keyVals);
			keyVals.clear();
			keyVals.add(RealmComponent.WARNING);
			keyVals.add(RealmComponent.TILE_TYPE+"=H");
			keyVals.add("chit");
			warningChits.addAll(pool.find(keyVals));
			for (GameObject warningChit : warningChits) {
				WarningChitComponent wc = (WarningChitComponent)RealmComponent.getRealmComponent(warningChit);
				wc.setFaceUp();
			}
			
			// Bring in native groups for each of the dwellings
			keyVals = new ArrayList<>();
			keyVals.add(hostPrefs.getGameKeyVals());
			keyVals.add("dwelling");
			pool = new GamePool(data.getGameObjects());
			Collection<GameObject> dwellings = pool.find(keyVals);
			for (GameObject dwelling : dwellings) {
				setupDwellingNatives(dwelling);
			}
		}
	}

	/**
	 * Do this at the start of the game to guarantee all treasure locations
	 * have their monsters.  Note: does NOT reset row 6 of the setup card!!
	 */
	public static void resetAllTreasureLocationDenizens(GameData data) {
		for (int i=1;i<=6;i++) {
			resetDenizens(data,i,false);
		}
		for (int i=1;i<=6;i++) {
			resetNatives(data,i);
		}
	}

	/**
	 * This returns the GameObject that holds all the denizens on the setup card
	 */
	public static GameObject getDenizenHolder(GameObject denizen) {
		GameData data = denizen.getGameData();
		String block = denizen.hasAttributeBlock("this_h")?"this_h":"this";
		String holderName = denizen.getAttribute(block,"setup_start");
		if (holderName!=null) {
			ArrayList<String> keys = new ArrayList<>();
			String boardNum = denizen.getThisAttribute(Constants.BOARD_NUMBER);
			if (boardNum!=null) {
				holderName = holderName + " " + boardNum;
				keys.add(Constants.BOARD_NUMBER+"="+boardNum);
			}
			else {
				keys.add("!"+Constants.BOARD_NUMBER);
			}
			keys.add("name="+holderName);
			keys.add("!character");
			keys.add("ts_section");
			GamePool pool = new GamePool(data.getGameObjects());
			ArrayList<GameObject> holders = pool.find(keys);
			
			GameObject denizenHolder = null;
			if (holders.size()==1) {
				// only 1?  Then its obvious
				denizenHolder = holders.iterator().next();
			}
			else {
				// more than 1?  Better crossreference with box_num
				String boxNum = denizen.getAttribute(block,"box_num");
				for (GameObject holder : holders) {
					if (holder==null || holder.getThisAttribute("box_num")==null) {
						return null;
					}
					if (holder.getThisAttribute("box_num").equals(boxNum)) {
						denizenHolder = holder;
						break;
					}
				}
			}
			return denizenHolder;
		}
		return null;
	}
	
	public static GameObject getHorseHolder(GameObject horse) {
		GameData data = horse.getGameData();
		String holderName = horse.getThisAttribute("horse_holder");
		if (holderName!=null) {
			ArrayList<String> keys = new ArrayList<>();
			String boardNum = horse.getThisAttribute(Constants.BOARD_NUMBER);
			if (boardNum!=null) {
				holderName = holderName + " " + boardNum;
				keys.add(Constants.BOARD_NUMBER+"="+boardNum);
			}
			else {
				keys.add("!"+Constants.BOARD_NUMBER);
			}
			keys.add("name="+holderName);
			keys.add("!character");
			keys.add("ts_section");
			GamePool pool = new GamePool(data.getGameObjects());
			return pool.findFirst(keys);
		}
		return null;
	}

	public static GameObject getDwellingLeader(GameObject dwelling) {
		String setupStart = StringUtilities.capitalize(dwelling.getThisAttribute("dwelling"));
		String boardNumber = dwelling.getThisAttribute(Constants.BOARD_NUMBER);
		ArrayList<String> query = new ArrayList<>();
		query.add("rank=HQ");
		query.add("setup_start="+setupStart);
		if (boardNumber!=null) {
			query.add(Constants.BOARD_NUMBER+"="+boardNumber);
		}
		GamePool pool = new GamePool(dwelling.getGameData().getGameObjects());
		return pool.findFirst(query);
	}
	public static boolean stillChitsToPlace(HostPrefWrapper hostPrefs) {
		RealmObjectMaster rom = RealmObjectMaster.getRealmObjectMaster(hostPrefs.getGameData());
		if (hostPrefs.usesSuperRealm()) {
			ArrayList<GameObject> gs = new ArrayList<>(rom.findObjects("gold_special,!"+Constants.GOLD_SPECIAL_PLACED)); 
			return !gs.isEmpty();
		}
		int boards = hostPrefs.getMultiBoardEnabled() ? hostPrefs.getMultiBoardCount() : 1;
		int totalChitsToPlace = boards * 6;
		ArrayList<GameObject> gs = new ArrayList<>(rom.findObjects("gold_special,"+Constants.GOLD_SPECIAL_PLACED));
		int placedChits = gs.size();
		if (!hostPrefs.hasPref(Constants.HOUSE2_NO_MISSION_VISITOR_FLIPSIDE)&&!hostPrefs.usesSuperRealm()) {
			placedChits >>= 1; // divide by 2
		}
		return placedChits<totalChitsToPlace;
	}
	
	public static void turnMonstersAndNativesDarkSideUp(GameData data) {
		GamePool pool = new GamePool(data.getGameObjects());
		ArrayList<String> query = new ArrayList<>();
		query.add("denizen");
		query.add("monster");
		Collection<GameObject> monsters = pool.find(query);
		query.clear();
		query.add("denizen");
		query.add("native");
		Collection<GameObject> natives = pool.find(query);
		
		ArrayList<GameObject> denizens = new ArrayList<>();
		denizens.addAll(monsters);
		denizens.addAll(natives);
		
		for (GameObject denizen : denizens) {
			RealmComponent denizenRc = RealmComponent.getRealmComponent(denizen);
			if (denizenRc instanceof MonsterChitComponent) {
				MonsterChitComponent monsterChit = (MonsterChitComponent) denizenRc;
				if (monsterChit.getVulnerability().weakerOrEqualTo(Strength.valueOf("H")) && monsterChit.isLightSideUp()) {
					monsterChit.setDarkSideUp();
				}
			}
			else if (denizenRc.isNative() && !denizenRc.isHiredOrControlled()) {
				NativeChitComponent nativeChit = (NativeChitComponent) denizenRc;
				nativeChit.setDarkSideUp();
			}
		}
	}
	
	public static void regroupNative(RealmComponent rc, GameData data) {
		GamePool pool = new GamePool(data.getGameObjects());
		String boardNum = rc.getGameObject().getThisAttribute(Constants.BOARD_NUMBER);
		if (rc.getGameObject().hasThisAttribute("garrison")) {
			GameObject denizenHolder = SetupCardUtility.getDenizenHolder(rc.getGameObject());
			if (denizenHolder!=null && rc.getGameObject().hasThisAttribute("garrison")) {
				TileLocation tl = ClearingUtility.getTileLocation(denizenHolder);
				tl.clearing.add(rc.getGameObject(),null);
			}
			return;
		}
		
		String groupName = rc.getGameObject().getThisAttribute(RealmComponent.NATIVE);
		ArrayList<String> query = new ArrayList<>();
		query.add("denizen");
		query.add(RealmComponent.NATIVE+"="+groupName);
		if (boardNum!=null && !boardNum.isEmpty()) {
			query.add(Constants.BOARD_NUMBER+"="+boardNum);
		}
		ArrayList<GameObject> denizens = pool.find(query);
		ArrayList<RealmComponent> groupMembers = new ArrayList<>();
		for (GameObject denizen : denizens) {
			RealmComponent denizenRc = RealmComponent.getRealmComponent(denizen);
			if (denizenRc.isNative() && !denizenRc.isHireling()&& !denizenRc.isCompanion() && !denizenRc.isControlledNative()) {
				TileLocation loc = denizenRc.getCurrentLocation();
				if (loc!=null && loc.hasClearing()) {
					for (RealmComponent clearingComponent : loc.clearing.getClearingComponents()) {
						if (clearingComponent.isDwelling()) {
							groupMembers.add(denizenRc);
							break;
						}
					}
				}				
			}
		}
		if (!groupMembers.isEmpty()) {
			Collections.sort(groupMembers, new Comparator<RealmComponent>() {
				public int compare(RealmComponent rc1, RealmComponent rc2) {
					String rank1 = rc1.getGameObject().getThisAttribute("rank");
					String rank2 = rc2.getGameObject().getThisAttribute("rank");
					if (rank1.matches("HQ")) return -1;
					if (rank2.matches("HQ")) return +1;
					return Integer.valueOf(rank1).compareTo(Integer.valueOf(rank2));
				}
			});
			TileLocation loc = groupMembers.get(0).getCurrentLocation();
			loc.clearing.add(rc.getGameObject(), null);
		}
		else {
			GameObject denizenHolder = SetupCardUtility.getDenizenHolder(rc.getGameObject());
			denizenHolder.add(rc.getGameObject());
		}		
	}
}