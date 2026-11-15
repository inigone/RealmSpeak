package com.robin.magic_realm.components.swing;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.*;

import com.robin.game.objects.GameObject;
import com.robin.general.graphics.GraphicsUtil;
import com.robin.general.swing.ImageCache;
import com.robin.magic_realm.components.BattleHorse;
import com.robin.magic_realm.components.RealmComponent;
import com.robin.magic_realm.components.attribute.PhaseManager;
import com.robin.magic_realm.components.store.GuildPhaseRegistry;
import com.robin.magic_realm.components.utility.Constants;
import com.robin.magic_realm.components.wrapper.CharacterWrapper;

public class PhaseManagerIcon extends ImageIcon {
	private static final Font NUMBER_FONT = new Font("Dialog",Font.BOLD,12);
	private static final Font ACTION_FONT = new Font("Dialog",Font.BOLD,14);
	
	private PhaseManager pm;
	private CharacterWrapper character;
	private List extraActions;
	private List<Boolean> isGuildActionList; // parallel to extraActions; true = guild-benefit phase
	private Rectangle ponyRect;

	public PhaseManagerIcon(PhaseManager pm) {
		this.pm = pm;
		this.character = pm != null ? pm.getCharacter() : null;
		updateImage();
	}
	public void handleClick(CharacterWrapper character,Point p) {
		if (ponyRect!=null && ponyRect.contains(p)) {
			boolean ponyLock = !character.isPonyLock(); // toggle
			character.setPonyLock(ponyLock);
			pm.setPonyLock(ponyLock);
			updateImage();
		}
	}
	public String getText(int x) {
		if (x<32) {
			return pm.getBasic()+" Basic";
		}
		else if (x<64) {
			return pm.getSunlight()+" Sunlight";
		}
		else if (x<96) {
			return pm.getSheltered()+" Sheltered";
		}
		else {
			int n = (x-96)>>5;
			if (n<extraActions.size()) {
				Object[] obj = (Object[])extraActions.get(n);
				String action = (String)obj[0];
				GameObject go = (GameObject)obj[1];
				boolean isGuild = isGuildActionList != null && n < isGuildActionList.size() && isGuildActionList.get(n);
				String source = isGuild ? getGuildActionSource(action) : null;
				if (source == null) source = go.getName();
				return "Extra "+action+" phase from "+source;
			}
		}
		return null;
	}
	private static String getNonNegString(int val) {
		if (val>0) {
			return String.valueOf(val);
		}
		return "0";
	}
	private void updateImage() {
		int width = 96;
		extraActions = pm.getExtraActionsList();
		isGuildActionList = buildGuildActionList(extraActions);
		width += (extraActions.size()*32);
		
		ponyRect = null;
		
		ImageIcon basic = ImageCache.getIcon("phases/basic");
		ImageIcon sunlight = ImageCache.getIcon("phases/sunshine");
		ImageIcon sheltered = ImageCache.getIcon("phases/sheltered");
		BufferedImage image = new BufferedImage(width,32,BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D g = (Graphics2D)image.getGraphics();
		g.drawImage(basic.getImage(),0,0,null);
		g.drawImage(sunlight.getImage(),32,0,null);
		g.drawImage(sheltered.getImage(),64,0,null);
		g.setFont(NUMBER_FONT);
		g.setColor(Color.black);
		GraphicsUtil.drawCenteredString(g,0,0,32,30,getNonNegString(pm.getBasic()));
		GraphicsUtil.drawCenteredString(g,32,0,32,30,getNonNegString(pm.getSunlight()));
		GraphicsUtil.drawCenteredString(g,64,0,32,30,getNonNegString(pm.getSheltered()));
		
		int pos = 96;
		g.setFont(ACTION_FONT);
		int idx = 0;
		for (Iterator i=extraActions.iterator();i.hasNext();idx++) {
			Object[] obj = (Object[])i.next();
			String action = (String)obj[0];
			GameObject go = (GameObject)obj[1]; // could draw this as a RealmComponent small image...
			boolean isGuild = isGuildActionList != null && idx < isGuildActionList.size() && isGuildActionList.get(idx);
			RealmComponent rc = RealmComponent.getRealmComponent(go);
			if (rc!=null) {
				int dx = pos+(rc.isCard()?3:0);
				int dy = 0;
				java.awt.Image baseImage = isGuild ? getGuildBadgeImage(action) : null;
				if (baseImage == null) baseImage = rc.getPhaseImage();
				g.drawImage(baseImage,dx,dy,32,32,null);
				if (rc.isHorse() || rc.isNativeHorse()) {
					BattleHorse horse = (BattleHorse)rc;
					if (horse.doublesMove()) {
						ponyRect = new Rectangle(dx,dy,32,32);
						if (pm.isPonyLock()) {
							Stroke old = g.getStroke();
							g.setStroke(Constants.THICK_STROKE);
							g.setColor(Color.blue);
							g.drawLine(dx,dy,dx+ponyRect.width,dy+ponyRect.height);
							g.drawLine(dx,dy+ponyRect.height,dx+ponyRect.width,dy);
							g.setStroke(old);
						}
					}
				}
			}
			else { // testing
				g.setColor(Color.white);
				g.fillRect(pos,0,32,32);
				g.setColor(Color.black);
				g.drawRect(pos,0,32,32);
			}
			g.setColor(Color.black);
			GraphicsUtil.drawCenteredString(g,pos,0,32,30,action);
			g.setColor(Color.red);
			GraphicsUtil.drawCenteredString(g,pos-1,-1,32,30,action);
			pos += 32;
		}
		
		setImage(image);
	}
	private static final String[] GUILD_LEVEL_NAMES = { "Pledge", "Initiate", "Apprentice", "Journeyman", "Master" };

	/**
	 * Builds a parallel boolean list for extraActions: true = guild-benefit phase, false = innate.
	 * Detection works by counting how many times each action appears in EXTRA_ACTIONS (innate);
	 * once those are "consumed" in order, the remaining occurrences are guild-sourced.
	 * This avoids using the guild GO as the phase's required item (which would cause
	 * "Missing Required Item" errors when the character is away from the guild).
	 */
	private List<Boolean> buildGuildActionList(List actions) {
		List<Boolean> result = new ArrayList<>();
		if (character == null) {
			for (int i = 0; i < actions.size(); i++) result.add(false);
			return result;
		}
		Map<String, Integer> innateCount = new HashMap<>();
		ArrayList<String> ea = character.getGameObject().getThisAttributeList(Constants.EXTRA_ACTIONS);
		if (ea != null) {
			for (String a : ea) innateCount.merge(a.replace("SP","E"), 1, Integer::sum);
		}
		if (character.getGameObject().hasAttribute(Constants.OPTIONAL_BLOCK, Constants.EXTRA_ACTIONS)) {
			ArrayList<String> oa = character.getGameObject().getAttributeList(Constants.OPTIONAL_BLOCK, Constants.EXTRA_ACTIONS);
			if (oa != null) {
				for (String a : oa) innateCount.merge(a.replace("SP","E"), 1, Integer::sum);
			}
		}
		for (Object o : actions) {
			String action = (String)((Object[])o)[0];
			int remaining = innateCount.getOrDefault(action, 0);
			if (remaining > 0) {
				innateCount.put(action, remaining - 1);
				result.add(false); // innate
			} else {
				result.add(true); // guild-sourced
			}
		}
		return result;
	}

	private String getGuildActionSource(String action) {
		if (character == null) return null;
		String guild = character.getCurrentGuild(false);
		if (guild == null || guild.isEmpty()) return null;
		int level = GuildPhaseRegistry.getBenefitLevel(action, guild);
		if (level == 0) return null;
		String guildLabel = Character.toUpperCase(guild.charAt(0)) + guild.substring(1) + " Guild";
		String levelName = level < GUILD_LEVEL_NAMES.length ? GUILD_LEVEL_NAMES[level] : "Master";
		return guildLabel + " " + levelName;
	}
	private java.awt.Image getGuildBadgeImage(String action) {
		if (character == null) return null;
		String guild = character.getCurrentGuild(false);
		if (guild == null || guild.isEmpty()) return null;
		int level = GuildPhaseRegistry.getBenefitLevel(action, guild);
		if (level == 0) return null;
		ImageIcon icon = ImageCache.getIcon("badges/"+guild+level);
		return icon != null ? icon.getImage() : null;
	}
	public static void main(String[] args) {
		PhaseManager pm = new PhaseManager(null,null,2,2,0);
		GameObject go = new GameObject(null);
		pm.addFreeAction("M",go);
		pm.addFreeAction("TR",go);
		pm.addFreeAction("S",go);
		pm.addFreeAction("FLY",go);
		PhaseManagerIcon pmv = new PhaseManagerIcon(pm);
		JLabel label = new JLabel(pmv);
		JFrame frame = new JFrame();
		frame.setSize(300,100);
		frame.setLocationRelativeTo(null);
		frame.getContentPane().add(label);
		frame.setVisible(true);
	}
}