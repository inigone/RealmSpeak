package com.robin.magic_realm.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;

import javax.swing.ImageIcon;

import com.robin.game.objects.GameObject;
import com.robin.general.swing.ImageCache;
import com.robin.magic_realm.components.wrapper.CombatWrapper;

public abstract class SquareChitComponent extends ChitComponent {
	
	protected SquareChitComponent(GameObject obj) {
		super(obj);
	}
	
	public Shape getShape(int x,int y,int size) {
		return new Rectangle(x,y,size-1,size-1);
	}
	/** The little steed symbol natives and monsters carry in their lower right corner. */
	protected static ImageIcon getHorseIcon(NativeSteedChitComponent horse) {
		String[] ret = horse.getFolderAndType();
		int horseSize = ret[2]==null?20:40*Integer.valueOf(ret[2]);
		return ImageCache.getIcon(ret[0]+"/"+ret[1],horseSize);
	}
	/** Where drawHorse puts that symbol, in chit coordinates. */
	private Rectangle getHorseBounds(ImageIcon icon) {
		int size = getChitSize();
		return new Rectangle(size-icon.getIconWidth()-2,size>>1,icon.getIconWidth(),icon.getIconHeight());
	}
	/**
	 * Redraws the steed symbol struck through in red, for a steed that has been killed.
	 * <p>
	 * drawHorse works from getHorse(false), which drops a steed once its permanent DEAD attribute is
	 * set, so after combat a killed steed simply vanishes from the chit.  During combat it is still
	 * drawn, because only the CombatWrapper knows it died yet.  This covers both: it redraws the
	 * symbol crossed out, over a chit already drawn at some other size.
	 *
	 * @param drawnSize		the size the chit was drawn at, so the symbol can be scaled to match
	 */
	public void paintDeadHorseMark(Graphics g,int x,int y,int drawnSize) {
		BattleHorse dead = getHorseIncludeDead();
		if (dead==null || !(dead instanceof NativeSteedChitComponent)) return;
		// isDead() reads the permanent DEAD attribute, which is not applied until combat is over -
		// during the battle a killed steed is only marked on its CombatWrapper
		if (!new CombatWrapper(dead.getGameObject()).isDead() && !dead.isDead()) return;

		ImageIcon icon = getHorseIcon((NativeSteedChitComponent)dead);
		Rectangle bounds = getHorseBounds(icon);
		double scale = (double)drawnSize/(double)getChitSize();
		int hx = x+(int)(bounds.x*scale);
		int hy = y+(int)(bounds.y*scale);
		int hw = (int)(bounds.width*scale);
		int hh = (int)(bounds.height*scale);
		g.drawImage(icon.getImage(),hx,hy,hw,hh,null);

		Graphics2D g2 = (Graphics2D)g;
		Stroke old = g2.getStroke();
		g2.setStroke(new BasicStroke(2));
		g2.setColor(Color.red);
		g2.drawLine(hx,hy,hx+hw,hy+hh);
		g2.drawLine(hx+hw,hy,hx,hy+hh);
		g2.setStroke(old);
	}
}