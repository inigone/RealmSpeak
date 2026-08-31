package com.robin.magic_realm.RealmBattle;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.robin.game.objects.GameObject;
import com.robin.general.graphics.GraphicsUtil;
import com.robin.general.swing.DieRoller;
import com.robin.general.util.StringUtilities;
import com.robin.magic_realm.components.BattleHorse;
import com.robin.magic_realm.components.ChitComponent;
import com.robin.magic_realm.components.SquareChitComponent;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;
import com.robin.magic_realm.components.wrapper.CombatWrapper;
import com.robin.magic_realm.components.wrapper.HostPrefWrapper;

public class CombatSummarySheet extends JPanel {
	private static final String[] COMBAT_STAGES  = {
		"Prebattle",
		"Luring",
		"Random Assignment",
		"Deploy/Charge",
		"Encounter",
		"Assign Targets",
		"Positioning",
		"Change Tactics (rare)",
		"Resolving",
		"Fatigue",
		"Disengage",
	};
	
	/** Where each coup marker was drawn this paint, so hovering one can redraw it larger. */
	private final ArrayList<CoupMarker> coupMarkers = new ArrayList<>();
	private CoupMarker hoveredCoup;
	private Point hoverPoint;

	/** One drawn coup marker - everything needed to paint it again at any size. */
	private static class CoupMarker {
		private final Rectangle bounds;
		private final String type;
		private final GameObject affected;
		private final int count;
		private CoupMarker(Rectangle bounds,String type,GameObject affected,int count) {
			this.bounds = bounds;
			this.type = type;
			this.affected = affected;
			this.count = count;
		}
	}
	/** Attack order for the whole battle, resolved once per paint; null when it does not apply. */
	private BattleModel.AttackOrder attackOrder;
	/** Outcome lines and attack classification for the battle, worked out once per paint. */
	private OutcomeLines.BattleOutcomes outcomes;
	private ArrayList<CharacterWrapper> characters;
	private BattleModel battleModel;
	private CombatFrame combatFrame;
	
	public CombatSummarySheet(CombatFrame combatFrame) {
		super();
		this.battleModel = combatFrame.getBattleModel();
		this.combatFrame = combatFrame;
		ArrayList<CharacterWrapper> characters = new ArrayList<>();
		for (RealmComponent rc : battleModel.getAllParticipatingCharacters()) {
			characters.add(new CharacterWrapper(rc.getGameObject()));
		}
		this.characters = characters;
		Collections.sort(characters,new Comparator<CharacterWrapper>() {
			public int compare(CharacterWrapper c1,CharacterWrapper c2) {
				int ret = 0;
				ret = c1.getCombatPlayOrder()-c2.getCombatPlayOrder();
				if (ret==0) {
					ret = c1.getGameObject().getName().compareTo(c2.getGameObject().getName());
				}
				return ret;
			}
		});
		this.setLayout(null);
		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseMoved(MouseEvent ev) {
				updateCoupHover(ev.getPoint());
			}
			public void mouseDragged(MouseEvent ev) {
				updateCoupHover(ev.getPoint());
			}
		});
	}
	private Font STAGE_FONT = new Font("Dialog",Font.BOLD,12);
	private Color STAGE_SECTION_COLOR = new Color(200,255,200,150);
	private Color NAME_SECTION_COLOR = new Color(200,200,255,150);
	private Color NUMBER_BOX_COLOR = new Color(0,0,0,100);
	private Stroke MARK_STROKE = new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
	private int buttonWidth = 70;
	private int buttonHeight = 20;
	public void paintComponent(Graphics g1) {
		super.paintComponent(g1);
		// The Sheet/Lure/Flip buttons below are created and added during the paint, so without this
		// every repaint leaks another set - thousands of them after a while, until the event thread
		// spends all its time painting button text
		removeAll();
		g1.setFont(STAGE_FONT);
		Graphics2D g = (Graphics2D)g1;
		AffineTransform normal = g.getTransform();
		
		int x,y,s;
		coupMarkers.clear();
		attackOrder = resolveAttackOrder();
		cacheOutcomeLines();
		
		// Draw name sections
		g.setColor(NAME_SECTION_COLOR);
		x = 5;
		y = 182;
		s = 145 + (COMBAT_STAGES.length*30) - 20;
		for (int i=0;i<characters.size();i++) {
			g.fillRect(x,y-20,s,25);
			y += 30;
		}
		
		// Draw names
		g.setColor(Color.black);
		x = 10;
		y = 180;
		for (CharacterWrapper character : characters) {
			String name = character.getGameObject().getName();
			g.drawString(name,x,y);
			y += 30;
		}
		int listBottom = y;
		
		// Draw stage sections
		g.setColor(STAGE_SECTION_COLOR);
		x = 150;
		y = 5;
		s = 150 + (characters.size()*30);
		for (int i=0;i<COMBAT_STAGES.length;i++) {
			g.fillRect(x-18,y,25,s);
			x+=30;
		}
		
		// Draw Combat Stage Titles
		AffineTransform rotated = new AffineTransform(normal);
		rotated.rotate(Math.toRadians(-90),150,150);
		g.setTransform(rotated);
		g.setColor(Color.black);
		x = 150;
		y = 150;
		for (int i=0;i<COMBAT_STAGES.length;i++) {
			g.drawString(COMBAT_STAGES[i],x,y);
			y += 30;
		}
		g.setTransform(normal);
		Stroke normalStroke = g.getStroke();
		
		for (int r=0;r<characters.size();r++) {
			CharacterWrapper character = characters.get(r);
			boolean active = true;
			int stage = character.getCombatStatus();
			if (stage>Constants.COMBAT_WAIT) {
				stage -= Constants.COMBAT_WAIT;
				active = false;
			}
			for (int c=0;c<COMBAT_STAGES.length;c++) {
				int n = ((c*characters.size())+r)+1;
				int stageCompare = c+1;
				Rectangle rect = getRectangleForPosition(r,c);
				
				g.setColor(NUMBER_BOX_COLOR);
				GraphicsUtil.drawCenteredString(g,rect.x,rect.y,rect.width,rect.height,String.valueOf(n));
				
				g.setColor(Color.black);
				g.draw(rect);
				
				g.setStroke(MARK_STROKE);				
				if (stage==stageCompare && active) {
					g.setColor(Color.red);
					rect.x += 2;
					rect.y += 2;
					rect.width -= 4;
					rect.height -= 4;
					g.draw(rect);
				}
				else if (stage>stageCompare) {
					rect.x += 2;
					rect.y += 2;
					rect.width -= 4;
					rect.height -= 4;
					
					g.drawLine(rect.x,rect.y,rect.x+rect.width,rect.y+rect.height);
				}
				g.setStroke(normalStroke);
			}
		}
		
		// List battling natives for each character
		x = 5;
		y = listBottom;
		g.setColor(Color.black);
		for (CharacterWrapper character : characters) {
			for (String groupName : character.getBattlingNativeGroups()) {
				StringBuffer sb = new StringBuffer();
				sb.append("The ");
				sb.append(StringUtilities.capitalize(groupName));
				sb.append(" are battling the ");
				sb.append(character.getGameObject().getName());
				sb.append(".");
				
				g.drawString(sb.toString(),x,y+20);
				y += 20;
			}
		}
		y += 40;
		
		// Battle overview
		g.drawString("DEFENDER",x+90,y);
		g.drawString("ATTACKERS",x+200,y);
		y -= 45;
		int row=0;
		for (RealmComponent battleParticipant : combatFrame.getAllParticipants()) {
			CombatWrapper cr = new CombatWrapper(battleParticipant.getGameObject());
			row+=1;
			y += 90;
			int rowTop = y-40;
			g.drawImage(battleParticipant.getImage(),x+80,y-40,80,80,null);
			drawAttackOrderStamp(g,battleParticipant,x+80,y-40);
			drawCombatRolls(g,battleParticipant,x+80,y-40);
			drawDeadHorseMark(g,battleParticipant,x+80,y-40);
			drawCoupMarkers(g,battleParticipant,x+80,y-40);		
			JButton chartButton = new JButton("Sheet");
			final int rcRow = row;
			chartButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ev) {
					combatFrame.participantTable.setRowSelectionInterval(rcRow,rcRow);
				}
			});
			chartButton.setBounds(x,y-10,buttonWidth,buttonHeight);
			add(chartButton);
			
			RealmComponent owner = battleParticipant.getOwner();
			boolean isOwnedByActive = (owner!=null && owner.equals(combatFrame.getActiveParticipant()));
			if (combatFrame.getActionState() == Constants.COMBAT_LURE && CombatFrame.isInteractiveFrame()) {
				if (combatFrame.areDenizensToLure() && (combatFrame.getActiveParticipant() ==  battleParticipant || isOwnedByActive) && !battleParticipant.isMistLike() ) {
					JButton lureButton = new JButton("Lure");
					lureButton.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent ev) {
							if (battleParticipant.isCharacter()) {
								combatFrame.lureDenizens(battleParticipant,0,0,true);
							} else {
								DenizenCombatSheet denizenSheet = new DenizenCombatSheet(combatFrame,combatFrame.getBattleModel(),battleParticipant,false,null);
								if (denizenSheet.canLureMoreDenizens()) {
									DenizenCombatSheet.lureDenizens(combatFrame,battleParticipant);
								}
								else {
									DenizenCombatSheet.showDialogOnlySingleDenizenCanBeLured(combatFrame);
								}
							}
						}
					});
					lureButton.setBounds(x,y-35,buttonWidth,buttonHeight);
					add(lureButton);
				}
				if (!battleParticipant.isCharacter() && DenizenCombatSheet.denizenCanFlip(battleParticipant)) {
					JButton flip = new JButton("Flip");
					flip.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent ev) {
							battleParticipant.flip();
							combatFrame.repaintCombatSheetPanel();
						}
					});
					flip.setBounds(x,y+12,buttonWidth,12);
					add(flip);	
				}
				if (!battleParticipant.isCharacter() && battleParticipant.hasHorse()) {
					JButton flipHorse = new JButton("FlipSteed");
					flipHorse.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent ev) {
							BattleHorse horse = battleParticipant.getHorse();
							horse.flip();
						}
					});
					flipHorse.setBounds(x-5,y+25,76,12);
					add(flipHorse);	
				}
			}
			
			int xAttacker = x+110;
			int attackerCount = 0;
			for (GameObject attacker : cr.getAttackers()) {
				if (attackerCount != 0 && attackerCount % 4 == 0) {
					y += 90;
					xAttacker = x+110;
				}
				xAttacker += 90;
				RealmComponent attackerRc = RealmComponent.getRealmComponent(attacker);
				g.drawImage(attackerRc.getImage(),xAttacker,y-40,80,80,null);
				drawAttackOrderStamp(g,attackerRc,xAttacker,y-40);
				drawCombatRolls(g,attackerRc,xAttacker,y-40);
				drawDeadHorseMark(g,attackerRc,xAttacker,y-40);
				drawCoupMarkers(g,attackerRc,xAttacker,y-40);
				attackerCount += 1;
			}

			// Outcome lines for this participant's sheet, listed down the right of the row
			int outcomeBottom = drawOutcomeLines(g,battleParticipant,x+OUTCOME_COLUMN_OFFSET,rowTop);
			if (outcomeBottom>y+40) {
				y = outcomeBottom-40; // let a long list grow the row rather than overlap the next
			}
			g.setFont(STAGE_FONT);
		}
		y += 80;
		
		/*
		int yUnassignedHeadline = y;
		y += 50;
		int xUnassigned = x;
		int unassignedCount = 0;
		for (RealmComponent battleParticipant : battleModel.getAllBattleParticipants(true)) {
			if (unassignedCount != 0 && unassignedCount % 6 == 0) {
				y += 90;
				xUnassigned = x;
			}
			CombatWrapper cr = new CombatWrapper(battleParticipant.getGameObject());
			if (!cr.isSheetOwner() && cr.getAttackerCount() == 0 && !battleParticipant.hasTarget()) {
				g.drawImage(battleParticipant.getImage(),xUnassigned,y-40,80,80,null);
				xUnassigned += 90;
				unassignedCount +=1;
			}
		}
		if (unassignedCount != 0) {
			g.drawString("UNASSIGNED",x+10,yUnassignedHeadline);
		}
		*/

		// Last, so an enlarged coup marker sits over everything else
		drawCoupHover(g);
	}
	private static final Font STAMP_FONT = new Font("Dialog",Font.BOLD,13);
	private static final Color STAMP_BACKING = new Color(255,255,255,225);
	private static final Color STAMP_TEXT = OutcomeLines.COLOR_INCOMING;
	/** An attack that will not land - no outcome line was produced for it. */
	private static final Color STAMP_MISSED_TEXT = OutcomeLines.COLOR_NOT_LANDING;

	/**
	 * Stamps a portrait with the attack order number of every attack it makes, so a chit can be
	 * matched to its outcome line.  A character attacks once per fight chit, so it can carry more
	 * than one number.  A number is greyed when that attack produced no outcome line anywhere -
	 * it neither intercepts nor undercuts, so it will not land.
	 */
	private void drawAttackOrderStamp(Graphics2D g,RealmComponent participant,int x,int y) {
		if (attackOrder==null) return;
		ArrayList<Integer> positions = attackOrder.positionsFor(participant.getGameObject());
		ArrayList<String> keys = attackOrder.attackKeysFor(participant.getGameObject());
		if (positions.isEmpty()) return;
		g.setFont(STAMP_FONT);
		int stampX = x;
		for (int i=0;i<positions.size();i++) {
			String text = String.valueOf(positions.get(i));
			String key = i<keys.size()?keys.get(i):"";
			int width = g.getFontMetrics().stringWidth(text)+8;
			g.setColor(STAMP_BACKING);
			g.fillRoundRect(stampX,y,width,16,6,6);
			g.setColor(stampColor(key));
			g.drawString(text,stampX+4,y+13);
			stampX += width+2;
		}
	}
	private static final int PORTRAIT_SIZE = 80;
	private static final int ROLL_DIE_SIZE = 14;
	private static final int ROLL_DOT_SIZE = 4;

	/**
	 * Paints the FUMBLE or MISSILE dice this counter threw across the bottom of its portrait, once
	 * the attacks have been resolved.  A counter that attacked more than once shows a roll each, in
	 * the order they were thrown.
	 */
	private void drawCombatRolls(Graphics2D g,RealmComponent participant,int x,int y) {
		if (attackOrder==null || combatFrame.getActionState()<Constants.COMBAT_RESOLVING) return;
		CombatWrapper combat = new CombatWrapper(participant.getGameObject());
		ArrayList<String> rolls = combat.getFumbleRolls();
		if (rolls==null) {
			rolls = combat.getMissileRolls();
		}
		if (rolls==null || rolls.isEmpty()) return;

		ArrayList<DieRoller> rollers = new ArrayList<>();
		int totalWidth = 0;
		for (String roll : rolls) {
			DieRoller roller = new DieRoller(roll,ROLL_DIE_SIZE,ROLL_DOT_SIZE);
			rollers.add(roller);
			totalWidth += roller.getPreferredSize().width+2;
		}
		int rollX = x+((PORTRAIT_SIZE-totalWidth)/2);
		for (DieRoller roller : rollers) {
			Dimension size = roller.getPreferredSize();
			int rollY = y+PORTRAIT_SIZE-size.height;
			roller.paintComponent(g.create(rollX,rollY,size.width,size.height));
			rollX += size.width+2;
		}
	}
	private static final Font COUP_FONT = new Font("Dialog",Font.BOLD,13);
	private static final int COUP_ICON_SIZE = 24;
	private static final int COUP_GAP = 2;
	private static final Color COUP_MARK_COLOR = new Color(210,0,0);

	/**
	 * Marks what this attacker achieved - counting coup - as a miniature of whatever it scored
	 * against: the target it wounded or killed, or the piece of armor it damaged or destroyed.
	 * <p>
	 * Every attacker involved in an outcome records its own coup during resolution, so simultaneous
	 * attacks that share a result all get marked rather than only whichever one was credited.
	 * Markers stack down the right edge, wrapping into another column, clear of the order stamp
	 * (top left) and the dice (bottom).
	 */
	private void drawCoupMarkers(Graphics2D g,RealmComponent participant,int x,int y) {
		if (!showsOutcomeExtras()) return;
		ArrayList<String> coups = new CombatWrapper(participant.getGameObject()).getCoups();
		if (coups==null || coups.isEmpty()) return;

		// Group identical coups so repeated wounds on one target become a single count
		ArrayList<String> order = new ArrayList<>();
		HashMap<String,Integer> counts = new HashMap<>();
		for (String coup : coups) {
			if (!counts.containsKey(coup)) order.add(coup);
			counts.put(coup,Integer.valueOf(counts.containsKey(coup)?counts.get(coup).intValue()+1:1));
		}

		int markerX = x+PORTRAIT_SIZE-COUP_ICON_SIZE;
		int markerY = y;
		for (String coup : order) {
			String[] parts = CombatWrapper.splitCoup(coup);
			if (parts==null) continue;
			if (markerY+COUP_ICON_SIZE>y+PORTRAIT_SIZE) {
				// Ran out of room down this column - start another to its left
				markerY = y;
				markerX -= COUP_ICON_SIZE+COUP_GAP;
			}
			drawCoupMarker(g,parts[0],parts[1],counts.get(coup).intValue(),markerX,markerY);
			markerY += COUP_ICON_SIZE+COUP_GAP;
		}
	}
	/**
	 * Enlarges whichever coup marker the mouse is over, since the markers themselves are too small
	 * to read.  Same idea as the hover on a combat sheet, at the component's natural size.
	 */
	private void updateCoupHover(Point point) {
		CoupMarker found = null;
		for (CoupMarker marker : coupMarkers) {
			if (marker.bounds.contains(point)) {
				found = marker;
				break;
			}
		}
		hoverPoint = point;
		if (hoveredCoup!=found) {
			hoveredCoup = found;
			repaint();
		}
	}
	private void drawCoupHover(Graphics2D g) {
		if (hoveredCoup==null || hoverPoint==null) return;
		RealmComponent rc = RealmComponent.getRealmComponent(hoveredCoup.affected);
		if (rc==null) return;
		int size = Math.max(rc.getComponentSize().width,rc.getComponentSize().height);
		// Keep it on the panel, and out from under the pointer
		int hx = Math.max(0,Math.min(hoverPoint.x+14,getWidth()-size));
		int hy = Math.max(0,Math.min(hoverPoint.y+14,getHeight()-size));
		paintCoup(g,hoveredCoup.type,hoveredCoup.affected,hoveredCoup.count,hx,hy,size);
	}
	/**
	 * The component's image without its damage overlay.  The portrait on the left of the row already
	 * shows final state; a coup marker should show only the effect it is reporting, so a target
	 * wounded here and killed later does not carry someone else's red X inside this marker.
	 */
	private static Image getPlainImage(RealmComponent rc) {
		if (!(rc instanceof ChitComponent)) return rc.getImage();
		ChitComponent chit = (ChitComponent)rc;
		boolean previous = chit.isIgnoreDamage();
		chit.setIgnoreDamage(true);
		try {
			return rc.getImage();
		}
		finally {
			// getRealmComponent hands out a shared instance, so this must not leak
			chit.setIgnoreDamage(previous);
		}
	}
	private void drawCoupMarker(Graphics2D g,String type,String affectedId,int count,int x,int y) {
		GameObject affected = battleModel.getGameData().getGameObject(Long.valueOf(affectedId));
		if (affected==null) return;
		paintCoup(g,type,affected,count,x,y,COUP_ICON_SIZE);
		coupMarkers.add(new CoupMarker(new Rectangle(x,y,COUP_ICON_SIZE,COUP_ICON_SIZE),type,affected,count));
	}
	/**
	 * Paints one coup at any size: the image of whatever it was scored against, marked with what
	 * happened to it.  Size driven, so the hover enlargement is the very same marker, bigger - a red
	 * X for a kill, the count for wounds, and nothing added for armor: a damaged chit is flipped to
	 * its DAMAGED face, and a destroyed one keeps the game's own X through it.
	 */
	private void paintCoup(Graphics2D g,String type,GameObject affected,int count,int x,int y,int size) {
		RealmComponent rc = RealmComponent.getRealmComponent(affected);
		if (rc==null) return;
		// Destroyed armor uses the game's own destroyed counter, X and all
		boolean destroyedArmor = CombatWrapper.COUP_ARMOR_DESTROYED.equals(type);
		g.drawImage(destroyedArmor?rc.getImage():getPlainImage(rc),x,y,size,size,null);

		// Armor coups carry no added markup - the armor chit already says what became of it
		g.setColor(COUP_MARK_COLOR);
		if (CombatWrapper.COUP_KILL.equals(type)) {
			Stroke old = g.getStroke();
			g.setStroke(new BasicStroke(Math.max(3,size/8),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
			g.drawLine(x,y,x+size,y+size);
			g.drawLine(x+size,y,x,y+size);
			g.setStroke(old);
		}
		else if (CombatWrapper.COUP_WOUND.equals(type)) {
			g.setFont(COUP_FONT.deriveFont((float)Math.max(11,size*13/COUP_ICON_SIZE)));
			GraphicsUtil.drawCenteredString(g,x,y,size,size,String.valueOf(count));
		}
		g.setColor(Color.black);
		g.drawRect(x,y,size,size);
	}
	/** Crosses out a native's or monster's steed symbol once it has been killed. */
	private void drawDeadHorseMark(Graphics2D g,RealmComponent participant,int x,int y) {
		if (!(participant instanceof SquareChitComponent)) return;
		if (!showsOutcomeExtras()) return;
		((SquareChitComponent)participant).paintDeadHorseMark(g,x,y,PORTRAIT_SIZE);
	}
	/** Matches the colour of the line this attack is listed on, or grey when it produced none. */
	private Color stampColor(String attackKey) {
		return outcomes==null?OutcomeLines.COLOR_NOT_LANDING:outcomes.colorFor(attackKey);
	}
	/** Left edge of the outcome line column, past four attacker portraits. */
	private static final int OUTCOME_COLUMN_OFFSET = 560;
	private static final int OUTCOME_LINE_HEIGHT = 13;
	/** Incoming attacks - yellow, matched by their order stamps. */
	private static final Color OUTCOME_TEXT = OutcomeLines.COLOR_INCOMING;
	private static final Color OUTCOME_OUTGOING_TEXT = OutcomeLines.COLOR_OUTGOING;
	/** Marks the sheet owner's own attack, so it does not read as another attack against it. */
	private static final String OUTGOING_MARK = "\u2192 ";

	/**
	 * Lists every outcome line this participant's combat sheet would show: its own attacks first,
	 * marked as outgoing, then the attacks landing on it.  Each side is in resolution order.
	 *
	 * @return		the y coordinate below the last line drawn
	 */
	private int drawOutcomeLines(Graphics2D g,RealmComponent participant,int x,int y) {
		ArrayList<AttackKillEstimate> outgoing = new ArrayList<>();
		ArrayList<AttackKillEstimate> incoming = new ArrayList<>();
		if (!collectOutcomeLines(participant,outgoing,incoming)) return y;

		g.setFont(CombatSheet.ESTIMATE_FONT);
		int lineY = y+OUTCOME_LINE_HEIGHT;
		for (AttackKillEstimate estimate : outgoing) {
			lineY = drawOutcomeEstimate(g,estimate,x,lineY,OUTCOME_OUTGOING_TEXT,OUTGOING_MARK);
		}
		for (AttackKillEstimate estimate : incoming) {
			lineY = drawOutcomeEstimate(g,estimate,x,lineY,OUTCOME_TEXT,"");
		}
		return lineY;
	}
	private int drawOutcomeEstimate(Graphics2D g,AttackKillEstimate estimate,int x,int lineY,Color color,String mark) {
		g.setColor(color);
		for (AttackKillEstimate.EstimateLine line : estimate.getLines()) {
			g.drawString(mark+line.text,x,lineY);
			lineY += OUTCOME_LINE_HEIGHT;
		}
		return lineY;
	}
	/** Resolved once per paint rather than per portrait. */
	private boolean showsOutcomeExtras() {
		return showOutcomeExtras;
	}
	private boolean showOutcomeExtras;
	/** Works out every participant's outcome lines once, shared with the round summary window. */
	private void cacheOutcomeLines() {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(battleModel.getGameData());
		showOutcomeExtras = hostPrefs.hasPref(Constants.OPT_COMBAT_OUTCOME_PROBABILITIES);
		outcomes = attackOrder==null?null:OutcomeLines.collectBattle(combatFrame,battleModel,hostPrefs);
	}
	/**
	 * Splits this participant's sheet lines by what each attack is aimed AT: an attack landing on
	 * the row's defender is incoming, and anything else placed on that sheet is outgoing.
	 * <p>
	 * Classifying by target rather than by attacker matters because a character can place an attack
	 * on another sheet - the Amazon attacking a denizen that sits on the Woods Girl's sheet is still
	 * an outgoing attack, even though neither the attacker nor the sheet owner is the other.
	 *
	 * @return		false when there is nothing to show
	 */
	private boolean collectOutcomeLines(RealmComponent participant,ArrayList<AttackKillEstimate> outgoing,ArrayList<AttackKillEstimate> incoming) {
		if (outcomes==null) return false;
		ArrayList<AttackKillEstimate> estimates = outcomes.byParticipant.get(participant.getGameObject().getStringId());
		if (estimates==null) return false;

		String defenderId = participant.getGameObject().getStringId();
		for (AttackKillEstimate estimate : estimates) {
			if (defenderId.equals(estimate.getTargetId())) {
				incoming.add(estimate);
			}
			else {
				outgoing.add(estimate);
			}
		}
		Comparator<AttackKillEstimate> byOrder = new Comparator<AttackKillEstimate>() {
			public int compare(AttackKillEstimate e1,AttackKillEstimate e2) {
				return e1.getAttackOrder()-e2.getAttackOrder();
			}
		};
		Collections.sort(outgoing,byOrder);
		Collections.sort(incoming,byOrder);
		return !outgoing.isEmpty() || !incoming.isEmpty();
	}
	/** Null unless the option is on and the combat boxes are final. */
	private BattleModel.AttackOrder resolveAttackOrder() {
		HostPrefWrapper hostPrefs = HostPrefWrapper.findHostPrefs(battleModel.getGameData());
		OutcomeLines lines = new OutcomeLines(combatFrame,battleModel,hostPrefs,null,false);
		if (!hostPrefs.hasPref(Constants.OPT_COMBAT_OUTCOME_PROBABILITIES) || !lines.outcomesSettled()) return null;
		return battleModel.getAttackOrder(combatFrame.getCurrentRound());
	}
	private static Rectangle getRectangleForPosition(int row,int col) {
		int x = (col * 30) + 132;
		int y = (row * 30) + 162;
		return new Rectangle(x,y,24,24);
	}
	
//	public static void main(String[] args) {
//		ArrayList list = new ArrayList();
//		list.add("White Knight");
//		list.add("Captain");
//		list.add("Swordsman");
//		JOptionPane.showMessageDialog(null,new CombatSummarySheet(list));
//	}
}