// Copyright 2010 - UDS/CNRS
// The Aladin program is distributed under the terms
// of the GNU General Public License version 3.
//
//This file is part of Aladin.
//
//    Aladin is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, version 3 of the License.
//
//    Aladin is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    The GNU General Public License is available in COPYING file
//    along with Aladin.
//


package cds.aladin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import cds.aladin.Aladin;
import cds.tools.Util;

/**
 * Slider avec bouton "plus", "moins" et titre
 * @author Pierre Fernique [CDS]
 * @version 1.0 Jan 2012 - cr�ation
 */
public abstract class SliderPlusMoins extends JPanel {
   Aladin aladin;
   
   JLabel label;
   JSlider slider;
   JButton plus,moins;
   
   /**
    * Cr�ation d'un slider
    * @param aladin r�f�rence
    * @param title - titre du slider (apparait sur la gauche)
    * @param min,max - valeurs du slider
    * @param incr - valeur de l'incr�ment lors de l'usage du bouton + ou -
    */
   public SliderPlusMoins(Aladin aladin,String title, int min, int max, final int incr) {
      this.aladin = aladin;
      
      label = new JLabel(title);
      label.setFont(Aladin.SSPLAIN);

      slider = new JSlider(JSlider.HORIZONTAL,min,max,min);
      slider.setPaintLabels(false);
      slider.setPaintTicks(false);
      slider.addChangeListener( new ChangeListener() {
         public void stateChanged(ChangeEvent e) { submit(0); }
      });
      
      JButton b;
      moins=b = new Bouton("-");
      b.setMargin(new Insets(0,0,0,0) );
      b.addActionListener( new ActionListener() {
         public void actionPerformed(ActionEvent e) { submit(-incr); }
      });

      plus=b = new Bouton("+");
      b.setMargin(new Insets(0,0,0,0) );
      b.addActionListener( new ActionListener() {
         public void actionPerformed(ActionEvent e) { submit(incr); }
      });
      
      setLayout( new BorderLayout(0,0));
      JPanel p = new JPanel(new BorderLayout(0,0));
      p.add(moins,BorderLayout.WEST);
      p.add(slider,BorderLayout.CENTER);
      p.add(plus,BorderLayout.EAST);
      
      add(label,BorderLayout.WEST);
      add(p,BorderLayout.CENTER);

      
      setEnabled(false);
      
   }
   
   /** R�cup�re la valeur courant du slider */
   public int getValue() { return slider.getValue(); }
   
   /** Positionne la valeur courante du slider */
   public void setValue(int v) { slider.setValue(v); }
   
   /** Action appel�e lors de la modification du slider par l'utilisateur */
   abstract void submit(int inc);
   
   boolean enable=true;
   
   /** Active ou d�sactive le slider */
   public void setEnabled(boolean m) {
      if( m==enable ) return;       // d�j� fait
      enable=m;
      super.setEnabled(m);
      slider.setEnabled(m);
      label.setForeground( m ? Color.black : Aladin.MYGRAY );
      plus.setEnabled(m);
      moins.setEnabled(m);
   }
   
   /** Positionne le tip */
   void setTooltip(String tip) {
      Util.toolTip(label, tip);
      Util.toolTip(moins, tip);
      Util.toolTip(plus, tip);
      Util.toolTip(slider, tip);
   }

   class Bouton extends JButton implements MouseMotionListener {
      static final int SIZE=10;
      Bouton(String s) {
         super.setText(s);
         setFont(Aladin.LBOLD);
         addMouseMotionListener(this);
      }
      public Dimension getPreferredSize() { return new Dimension(SIZE,SIZE); }
      public Dimension getSize() { return new Dimension(SIZE,SIZE); }
   
      public void paintComponent(Graphics g) {
         int H = getHeight();
         int W = getWidth();
         g.setColor( slider.getBackground());
         g.fillRect(0, 0, W, H);
         g.setColor( enable ? Color.black : Aladin.MYGRAY );
         String s = getText();
         g.drawString(s,W/2-g.getFontMetrics().stringWidth(s)/2,H/2+4);
      }
      public void mouseDragged(MouseEvent e) { }
      public void mouseMoved(MouseEvent e) {
         if( !enable ) return;
         setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      
   }
   
}
