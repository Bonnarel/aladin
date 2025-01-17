// Copyright 1999-2022 - Universite de Strasbourg/CNRS
// The Aladin Desktop program is developped by the Centre de Donnees
// astronomiques de Strasbourgs (CDS).
// The Aladin Desktop program is distributed under the terms
// of the GNU General Public License version 3.
//
//This file is part of Aladin Desktop.
//
//    Aladin Desktop is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, version 3 of the License.
//
//    Aladin Desktop is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    The GNU General Public License is available in COPYING file
//    along with Aladin Desktop.
//

package cds.aladin;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

import cds.tools.Astrodate;
import cds.tools.Util;
import cds.xml.Field;

/**
 * Canvas d'affichage des mesures des objets selectionnees
 *
 * @author Pierre Fernique [CDS]
 * @version 2.2 : dec 2016 Couleurs alternées
 * @version 2.1 : mars 2006 blink de la source courante
 * @version 2   : (21 janvier 2004) Changement de mode de mémorisation
 * @version 1.2 : 28 mars 00  Toilettage du code
 * @version 1.1 : 3 juin 99   Gestion du bloc-note
 * @version 1.0 : (10 mai 99) Toilettage du code
 * @version 0.9 : (??) creation
 */
public final class MCanvas extends JComponent
implements MouseListener,MouseMotionListener,
AdjustmentListener,
KeyListener,ActionListener,
MouseWheelListener, Widget
{
   private int NBLIGNE = 5;        // Nombre de lignes visibles par defaut
   private Font FONT;
   private int HF;          // Hauteur des lettres
   private int HL;          // Hauteur d'une ligne
   private int MH;          // Hauteur de la marge du haut
   private int MB;          // Hauteur de la marge du bas

   // Triangles-reperes de la ligne courante (position sur 1ere ligne)
   private int [] tX;    // Exterieur
   private int [] tY;

   Aladin aladin;        // Reference
   MyScrollbar scrollV;  // Reference
   MyScrollbar scrollH;  // Reference

   int W=500,H;              // Taille courante du MCanvas
   Image img;            // Double buffer
   double wblanc=-1;        // Taille d'un ``blanc'' dans le texte
   int firstsee;         // Premiere ligne affichee
   int lastsee;          // Derniere ligne affichee
   int currentsee=-1;    // Ligne courante (sous la souris)
   int nbligne;          // Nombre de lignes visibles actuellement
   Source oo;            // Precedente Source montree
   Vector mouseLigne;      // La dernière ligne sous la souris
   Vector showLigne;     // La ligne montrée (surlignée en bleue)
   Words wButton;	     // Dernier bouton appuye
   Obj oButton;	         // Objet associe au dernier bouton appuye
   int yrep=-1;          // l'ordonnee du dernier repere courant
   int absX=0;		         // Abscisse d'origine
   int oy=-1;     	     // L'ordonnee avant un drag
   int ox=-1;     	     // L'abscisse avant un drag
   boolean flagDrag=false;
   boolean firstUpdate=true; // Pour positionner le premier curseur
   int currentselect=-2;
   protected int triTag = Field.UNSORT;  //Indique un tri sur les tags

   protected Legende oleg=null;           // La légende courante
   protected Vector ligneHead=null;     // L'entéte courante
   protected Source objSelect=null;       // Source sélectionné (pour pouvoir bloquer son entête), sinon null
   protected Source objShow=null;         // Source pointée sous la souris dans l'image (le temps de faire le paintComponent=
   private boolean flagDrawHead=false;  // Juste pour réafficher rapidement le cas échéant
   private int onBordField=-1;          // Champ en cours de redimensionnement, -1 si aucun
   private int onBordX;                 // Début du drag lors d'un redimensionnement

   int indiceCourant=-1,sortField=-1;
   Source sCourante = null;

   private static String TIPHEAD,/*TIPREP,*/TIPGLU,TIPARCH,TIPFOV,TIPTAG;

   /** Creation.
    * @param aladin    Reference
    * @param text      Le vecteur contenant chaque ligne de mesures
    * @param scrollV La reference a la barre de defilement verticale
    * @param scrollH La reference a la barre de defilement horizontale
    */
   protected MCanvas(Aladin aladin,MyScrollbar scrollV,MyScrollbar scrollH) {
      this.aladin = aladin;
      this.scrollV = scrollV;
      this.scrollH = scrollH;
      
      FONT = Aladin.COURIER;
      HF = FONT.getSize(); // Hauteur des lettres
      HL = HF+3;          // Hauteur d'une ligne
      MH = HL;            // Hauteur de la marge du haut
      MB = 1;             // Hauteur de la marge du bas

      tX = new int[]{         5,         5,     14 };    // Exterieur
      tY = new int[]{ HL/2+1 -5, HL/2+1 +5, HL/2+1 };


      // Determination des tailles des lettres
//      wblanc = getToolkit().getFontMetrics(FONT).stringWidth("M");

      TIPHEAD = aladin.chaine.getString("TIPHEAD");
      TIPGLU  = aladin.chaine.getString("TIPGLU");
      TIPARCH = aladin.chaine.getString("TIPARCH");
      TIPFOV  = aladin.chaine.getString("TIPFOV");
      TIPTAG  = aladin.chaine.getString("TIPTAG");

      createPopupMenu();
      createPinPopupMenu();

      setOpaque(true);
      setDoubleBuffered(false); 
      addMouseListener(this);
      addMouseMotionListener(this);
      addKeyListener(this);
      addMouseWheelListener(this);
   }

   public Dimension getPreferredSize() {
      return new Dimension(2, HL*NBLIGNE + MH+MB);
   }

   JPopupMenu popMenu,popMenuTag;
   JMenuItem menuTriA,menuTriD,menuCopyVal,menuCopyURL,menuCopyCoord,menuCopyMeasurement,menuCopyMeasurement1,
   menuCopyAll,menuCopyAllAscii,
   /* menuTag,menuUntag,*/menuTag1,menuUntag1,menuHelpTag,
   /*menuKeepTag,menuKeepUntag,*/menuCreateMulti,menuCreateUniq,
   /* menuLoadImg,menuLoadImgs,*/menuUnselect,
   /* menuLauch utile ? */
   menuAddColumn,menuGoto,menuDel,menuTableInfo,menuEdit,menuCooToolbox,menuInfo,menuLaunch;

   // Cree le popup menu associe au View
   private void createPopupMenu() {
      Chaine c = aladin.chaine;
      popMenu = new JPopupMenu();
      popMenu.setLightWeightPopupEnabled(false);
      JMenuItem j;
      JMenu m;
      popMenu.add( menuUnselect=j=new JMenuItem(c.getString("MFUNSELECT")));
      j.addActionListener(this);
      popMenu.add( menuInfo=j=new JMenuItem(c.getString("MFDISPLAY"))); //c.getString("MFEDIT")));
      j.addActionListener(this);
      popMenu.add( menuEdit=j=new JMenuItem(c.getString("MFEDIT")));
      j.addActionListener(this);
      popMenu.add( menuDel=j=new JMenuItem(c.getString("MFDEL")));
      j.addActionListener(this);
      popMenu.add( menuGoto=j=new JMenuItem(c.getString("MFGOTOIMG")));
      j.addActionListener(this);
      //      popMenu.add( menuLoadImg=j=new JMenuItem(c.getString("MFLOADIMG")));
      //      j.addActionListener(this);
      //      popMenu.add( menuLoadImgs=j=new JMenuItem(c.getString("MFLOADIMGS")));
      //      j.addActionListener(this);
      //      popMenu.addSeparator();
      popMenu.add( menuTriA=j=new JMenuItem(c.getString("MFASCSORT")));
      j.addActionListener(this);
      popMenu.add( menuTriD=j=new JMenuItem(c.getString("MFADESCSORT")));
      j.addActionListener(this);
      popMenu.addSeparator();
      popMenu.add( m=new JMenu(c.getString("MFCOPYGEN")));
      m.add( menuCopyVal=j=new JMenuItem(c.getString("MFCOPYVAL")));
      j.addActionListener(this);
      m.add( menuCopyURL=j=new JMenuItem(c.getString("MFCOPYURL")));
      j.addActionListener(this);
      m.add( menuCopyCoord=j=new JMenuItem(c.getString("MFCOPYCOORD")));
      j.addActionListener(this);
      m.add( menuCopyMeasurement=j=new JMenuItem(c.getString("MFCOPYMEASUREMENT")));
      j.addActionListener(this);
      m.add( menuCopyMeasurement1=j=new JMenuItem(c.getString("MFCOPYMEASUREMENT1")));
      j.addActionListener(this);
      m.add( menuCopyAllAscii=j=new JMenuItem(c.getString("MFCOPYASCII")));
      j.addActionListener(this);
      m.add( menuCopyAll=j=new JMenuItem(c.getString("MFCOPY")));
      j.addActionListener(this);
      //      popMenu.addSeparator();
      //      popMenu.add( menuTag=j=new JMenuItem(c.getString("MFTAG")));
      //      j.addActionListener(this);
      //      popMenu.add( menuUntag=j=new JMenuItem(c.getString("MFUNTAG")));
      //      j.addActionListener(this);
      //      popMenu.addSeparator();
      //      popMenu.add( menuKeepTag=j=new JMenuItem(c.getString("MFKEEPTAG")));
      //      j.addActionListener(this);
      //      popMenu.add( menuKeepUntag=j=new JMenuItem(c.getString("MFKEEPUNTAG")));
      //      j.addActionListener(this);
      popMenu.addSeparator();
      popMenu.add( m=new JMenu(c.getString("MFCREATE")));
      m.add( menuCreateMulti=j=new JMenuItem(c.getString("VWCPLANEUNIQ")));
      j.addActionListener(this);
      m.add( menuCreateUniq=j=new JMenuItem(c.getString("VWCPLANEMULTI")));
      j.addActionListener(this);
      popMenu.add( menuAddColumn=j=new JMenuItem(aladin.ADDCOL));
      j.addActionListener(this);
      popMenu.add( menuTableInfo=j=new JMenuItem(aladin.TABLEINFO));
      j.addActionListener(this);
      popMenu.add( menuCooToolbox=j=new JMenuItem(aladin.COOTOOL));
      j.addActionListener(this);
      /* menu Launch utile */
      popMenu.add( menuLaunch=j=new JMenuItem("Launch service"));
      j.addActionListener(this);


      add(popMenu);
   }

   /** Reactions aux differents boutons du menu */
   public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if( src instanceof Timer ) { endTimerHist(); return; }

      //      if( src instanceof JMenuItem ) System.out.println("ActionCommand = "+((JMenuItem)src).getActionCommand());

      if( src==menuTriA || src==menuTriD ) aladin.mesure.tri(src==menuTriA);
      else if( /* src==menuTag || */ src==menuTag1 ) aladin.mesure.tag();
      else if( /* src==menuUntag || */ src==menuUntag1 ) aladin.mesure.untag();
      else if( src==menuHelpTag ) aladin.info(aladin.chaine.getString("MTAGINFO"));
      //      else if( src==menuKeepTag ) aladin.mesure.keepTag();
      //      else if( src==menuKeepUntag ) aladin.mesure.keepUntag();
      else if( src==menuCreateMulti ) aladin.cloneObj(false);
      else if( src==menuCreateUniq ) aladin.cloneObj(true);
      else if( src==menuGoto ) {
         aladin.view.gotoThere(objSelect);
         aladin.view.zoomOnSource(objSelect);
      }
      //      else if( src==menuLoadImg ) loadImg();
      //      else if( src==menuLoadImgs ) loadImgs();
      else if( src==menuCopyAll ) aladin.copyToClipBoard(aladin.mesure.getText());
      else if( src==menuCopyAllAscii ) aladin.copyToClipBoard(aladin.mesure.getText(true));
      else if( src==menuCopyCoord ) aladin.copyToClipBoard(aladin.mesure.getCurObjCoord());
      else if( src==menuCopyVal ) aladin.copyToClipBoard(aladin.mesure.getCurObjVal());
      else if( src==menuCopyURL ) aladin.copyToClipBoard(aladin.mesure.getCurObjURL());
      else if( src==menuCopyMeasurement ) aladin.copyToClipBoard(aladin.mesure.getCurObjMeasurement(false));
      else if( src==menuCopyMeasurement1 ) aladin.copyToClipBoard(aladin.mesure.getCurObjMeasurement(true));
      else if( src==menuUnselect ) deselect(objSelect);
      else if( src==menuDel ) delete(objSelect);
      else if( src==menuEdit ) edit(objSelect);
      else if( src==menuInfo ) info(objSelect);
      else if( src==menuTableInfo ) aladin.tableInfo(objSelect.plan);
      else if( src==menuAddColumn ) aladin.addCol(objSelect.plan);
      else if( src==menuCooToolbox ) openCooToolbox(objSelect);
      /* pourquoi menuLaunch */
      else if( src==menuLaunch ) aladin.mesure.getCurObjLaunch();

      // envoi via SAMP
      else if( src instanceof JMenuItem && ((JMenuItem)src).getActionCommand().equals(MBROADCASTSPECTRUM) ) {
         String o = ((JMenuItem)src).getText();
         if( urlSamp==null ) return;
         sendBySAMP(urlSamp,o);
         urlSamp=null;
         
      // Envoi directement dan Aladin
      } else if( src==menuDirectLoad ) {
         String o = ((JMenuItem)src).getText();
         if( urlSamp==null ) return;
         
         aladin.calque.newPlan(urlSamp, "Data", "provided by the original archive server", null);
         urlSamp=null;
      }

      if( aladin.view.zoomview.flagSED ) aladin.view.zoomview.repaint();
   }

   /**
    * Envoie aux Appli SAMP indiquées, ou à toutes (null), le spectre pointé par l'url */
   protected void sendBySAMP(String url, String plasticApp) {
      AppMessagingInterface mMgr = aladin.getMessagingMgr();
      if( ! Aladin.PLASTIC_SUPPORT || ! mMgr.isRegistered() ) return;

      ArrayList recipientsList = null;
      if( plasticApp!=null ) {
         recipientsList = new ArrayList();
         recipientsList.add(mMgr.getAppWithName(plasticApp));
      }

      aladin.trace(4,"MCanvas.sendBySAMP spectrum ["+url+"] to "+(plasticApp==null?"all":plasticApp));

      // une petite animation pour informer l'utilisateur que qqch se passe
      try {
         mMgr.getPlasticWidget().animateWidgetSend();
         if( plasticApp.equals("topcat") ) mMgr.sendMessageLoadVOTable(url, url, "Data", new Hashtable(), recipientsList);
         else mMgr.sendMessageLoadSpectrum(url, url, "Spectrum", new Hashtable(), recipientsList);

         aladin.glu.log(mMgr.getProtocolName(), "sending data or spectrum URL");
      } catch( Exception e ) {
         if( aladin.levelTrace>=3 ) e.printStackTrace();
         aladin.error("SAMP error: "+e.getMessage());
      }
   }
   
   private String urlSamp;

   /** Mémorise l'url d'un spectre qui va être envoyé via SAMP lorsque l'utilisateur aura indiqué
    * l'application SAMP cibles via le popupmenu qui va apparaître */
   protected void toSamp(String url,int x,int y) {
      urlSamp=url;
      aladin.view.infoCassis(null);
      showSAMPPopMenu(x,y);
   }


   //   /** Chargement d'une image centrée sur la source sélectionnée dans le tableau des mesures */
   //   private void loadImg() {
   //      Source s = objSelect;
   //      if( s==null ) return;
   //      String target = aladin.localisation.J2000ToString(s.raj, s.dej);
   //      aladin.execAsyncCommand("\""+s.id+"\"=get "+aladin.configuration.getLoadImgCmd()+" "+target);
   //   }
   //
   //   /** Chargement d'une image centrée pour chaque source dans la table des mesures
   //    * S'il y a au-moins une source taguée, ne concerne que les sources taguées*/
   //   private void loadImgs() {
   //      int n=0;
   //
   //      // Décompte du nombre de sources concernées
   //      for( int i=0; i<aladin.mesure.nbSrc; i++ ) {
   //         Source s = aladin.mesure.src[i];
   //         if( !s.isTagged() ) continue;
   //         n++;
   //      }
   //
   //      // Demande de confirmation s'il y en a trop
   //      if( !aladin.testNbImgLoad(n==0 ? aladin.mesure.nbSrc : n) ) return;
   //
   //      // C'est parti
   //      for( int i=0; i<aladin.mesure.nbSrc; i++ ) {
   //         Source s = aladin.mesure.src[i];
   //         if( n>0 && !s.isTagged() ) continue;
   //         String target = aladin.localisation.J2000ToString(s.raj, s.dej);
   //         aladin.execAsyncCommand("\""+s.id+"\"=get "+aladin.configuration.getLoadImgCmd()+" "+target);
   //      }
   //   }

   // Retourne l'état des tags : 0-aucune sources, 1-au-moins une, 2-toutes
   private int getTagFlag() {
      int n=0;
      for( int i=0; i<aladin.mesure.nbSrc; i++ ) {
         Source s = aladin.mesure.src[i];
         if( s.isTagged() ) n++;
      }
      return n==0 ? 0 : n==aladin.mesure.nbSrc ? 2 : 1;
   }

   /** Affichage du popupPin avec (dés)activation des menus concernés */
   private void popPinShow(int x,int y) {
      int tagMode=getTagFlag();
      menuTag1.setEnabled(tagMode<2);
      menuUntag1.setEnabled(tagMode>0);
      popMenuTag.show(this,x,y);
   }

   /** Affichage du popup avec (dés)activation des menus concernés */
   private void popupShow(int x,int y) {

      // Détermine s'il y a au-moins une source taguée
      boolean tag=getTagFlag()==1;
      boolean flag = objSelect!=null;
      menuUnselect.setEnabled(flag);
      menuDel.setEnabled(objSelect!=null && objSelect.plan.isSourceRemovable());
      menuEdit.setEnabled(objSelect!=null && objSelect instanceof SourceTag);
      menuCopyCoord.setEnabled(flag);
      //      menuLoadImg.setEnabled(flag);
      //      menuLoadImgs.setEnabled(aladin.mesure.nbSrc>1);
      //      menuLoadImgs.setText( aladin.chaine.getString(tag ? "MFLOADIMGS1" : "MFLOADIMGS"));
      menuGoto.setEnabled(flag);
      menuCopyMeasurement.setEnabled(flag);
      menuCopyURL.setEnabled( aladin.mesure.getCurObjURL().length()>0 );
      popMenu.show(this,x,y);
   }


   private JPopupMenu popMenuSAMP = null;

   private JMenu menuBroadcastSpectrum;
   private JMenuItem menuDirectLoad;
   
   //   private JMenuItem menuBroadcast;
   private String MALLAPPS;
   //   private String MBROADCASTALL;
   private String MBROADCASTSPECTRUM;

   // Cree le popup menu associe au select
   private void createSAMPPopupMenu() {

      popMenuSAMP = new JPopupMenu("SAMP");

      String appMsgProtocolName = aladin.getMessagingMgr().getProtocolName();
      //      MALLAPPS = aladin.chaine.getString("SLMALLAPPS").replaceAll("SAMP", appMsgProtocolName);
      //      MBROADCASTALL = aladin.chaine.getString("SLMBDCASTPLANES").replaceAll("SAMP", appMsgProtocolName);
      MBROADCASTSPECTRUM = aladin.chaine.getString("SLMBDCASTSPECTRUM");

      JMenuItem j;
      if( Aladin.PLASTIC_SUPPORT ) {
         //         popMenuSAMP.add( menuBroadcast = j= new JMenuItem(MBROADCASTALL));
         //         menuBroadcast.setActionCommand(MBROADCASTSPECTRUM);
         //         j.addActionListener(this);
         popMenuSAMP.add( menuBroadcastSpectrum = new JMenu(MBROADCASTSPECTRUM) );
      }
      popMenuSAMP.add( menuDirectLoad = new JMenuItem("try to load it directly in Aladin") );
      menuDirectLoad.addActionListener( this );

   }


   // Affiche le popup SAMP
   private void showSAMPPopMenu(int x,int y) {

      if( popMenuSAMP==null ) createSAMPPopupMenu();

      // Activation des items relatifs à PLASTIC
      if( Aladin.PLASTIC_SUPPORT ) {
         AppMessagingInterface mMgr = aladin.getMessagingMgr();

         ArrayList<String> spectrumApps = mMgr.getAppsSupporting(AppMessagingInterface.ABSTRACT_MSG_LOAD_SPECTRUM_FROM_URL);
         
         // On ajoute Topcat manu-militari si présent (ajustement pour Petr Skoda)
         for( String s :mMgr.getAppsSupportingTables() ) {
            if( s.equals("topcat") ) { spectrumApps.add(s); break; }
         }

         menuBroadcastSpectrum.setEnabled(spectrumApps.size()>0);

         JMenuItem item;
         menuBroadcastSpectrum.removeAll();
         
         HashSet<String> set = new HashSet<>();
         if( spectrumApps!=null && spectrumApps.size()>0) {
            for (String appName: spectrumApps) {
               if( set.contains(appName) ) continue;
               set.add(appName);
               menuBroadcastSpectrum.add(item = new JMenuItem(appName));
               item.setActionCommand(MBROADCASTSPECTRUM);
               item.addActionListener(this);
            }
         }

      }
      popMenuSAMP.show(this,x-15,y);
   }


   /** Recale l'absisse par defaut */
   protected void initX() { absX=0; }

   /** Dessine le bord gauche
    * @param x,y l'emplacement du mot dans la case
    * @param width la taille de la case
    */
   private void drawBordG(Graphics g,int x,int y,int width) {
      g.setColor( Aladin.COLOR_MEASUREMENT_LINE );
      g.drawLine(x+width+2,y,x+width+2,y+HF+2);
   }

   //   static private int cY[] = new int[3];
   //   static private int cX[] = new int[3];
   //
   //  /** Dessin d'un repere (petit triangle) a la position (x,y).
   //   * @param g        Le contexte graphique
   //   * @param  x,y     Les coordonnees ou le repere doit etre dessine
   //   * @param  o       L'objet (au sens aladin) associe au repere ou null si aucun
   //   *                 (l'objet (p) n'est utilise que pour determiner la
   //   *                  couleur du repere)
   //   * @param  flagRep true si on remplit le repere
   //   */
   //   private void drawRepere1(Graphics g,int x,int y,Source o,boolean flagRep) {
   //      int dy = y-HF-2;                // Decallage en ordonnee a partir de la ligne de base
   //
   //      // Decallage en ordonnee
   //      for( int i=0; i<3; i++ ) { cY[i] = tY[i]+dy; cX[i] = tX[i]+x; }
   //
   //      // Effacement
   //      g.setColor( flagRep ? C5 :BG );
   //      g.fillRect(x+1,dy+2,x+17,HL);
   //
   //      // Tracage
   //      Color c = o==null?Color.black:o.plan.c;
   //      g.setColor(c);
   //      if( flagRep ) g.fillPolygon(cX,cY,3);
   //      else g.drawPolygon(cX,cY,3);
   //
   //      g.setColor(COLORBORD);
   //      g.drawLine(1,y+2,1+16,y+2);
   //
   //   }

   /** Dessine le petit triangle indiquant  l'ordre du tri */
   private void drawSort(Graphics g,int x,int y,int sort,Color background) {
      int w=8;
      Color c = background==Color.white ? Color.lightGray : Color.white;

      g.setColor(background);
      g.fillRect(x-2, y-HF+1, w+5, HF);

      y-=w/2+1;
      g.setColor(Color.black);
      if( sort==Field.SORT_DESCENDING ) {
         g.drawLine(x+w/2,y+w/2, x,y-w/2);
         g.drawLine(x,y-w/2, x+w,y-w/2);
         g.setColor(c);
         g.drawLine(x+w/2,y+w/2, x+w,y-w/2);

      } else if( sort==Field.SORT_ASCENDING ) {
         g.drawLine(x+w/2,y-w/2, x,y+w/2);
         g.setColor(c);
         g.drawLine(x+w/2,y-w/2, x+w,y+w/2);
         g.drawLine(x,y+w/2, x+w,y+w/2);
      }
   }

   /** Dessin d'un bouton
    * @param g        Le contexte graphique
    * @param  x,y     Les coordonnees ou le logo doit etre dessine
    * @param  s      Le texte du mini-bouton
    * @param  w	    le mots associe au bouton
    * @return la taille du bouton
    */
   private int drawButton(Graphics g,int x,int y,int h,int width,String s,Words w) {
      int l; 		// =m.stringWidth(s)+6;
      y=y-HF+1;

      if( x+width>=1 ) {
         l=width-3;

         g.setColor( w.samp ? Color.yellow : Aladin.COLOR_BUTTON_BACKGROUND);
         g.fillRect(x,y,l,h);

         g.setColor(w.pushed?Color.black:Color.white);
         g.drawLine(x,y,x+l,y);
         g.drawLine(x,y,x,y+h);
         g.setColor(w.pushed?Color.white:Color.black);
         g.drawLine(x+l,y+h,x+l,y);
         g.drawLine(x+l,y+h,x,y+h);

         if( w.pushed ) g.setColor( Color.red );
         else if( w.haspushed ) g.setColor(  Color.green.darker().darker() );
         else g.setColor( Color.black );
         g.drawString(s,x+3,y+h-1);
      }
      return width;
   }

   /** Tracer d'un mot dans la fenetre des mesures.
    * <BR>
    * <B>Rq :</B> Met a jour la boite englobante du mot w.setPosition(...)
    *
    * @param g       Le contexte du graphique
    * @param w       Le mot
    * @param flagClear true s'il faut au prealable effacer
    * @param background La couleur du fond (par défaut)
    * @return        La taille calculee
    */
   protected int drawWords(Graphics g, Words w, boolean flagClear) {
      return drawWords(g,w,flagClear, w.y<=HF ?  Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND : Aladin.COLOR_MEASUREMENT_BACKGROUND );
   }
   private int drawWords(Graphics g, Words w, boolean flagClear, Color background) {
      int y = w.y+HF;        // Ligne de base
      int x = w.x;
      int xtext;	     // Ligne du texte
      int width;	     // Taille totale (en fonction du nbre de carac. indiques)
      int widthw;	     // Taille du mot
      String text =  w.text;
      
      // Prise en compte de la precision pour aligner le point décimal
      if( w.precision>=0 ) {
         int j = text.indexOf(' ');
         if( j<0 ) {
            
            try {
               text = Util.myRound(w.text,w.precision, false);
               int i = text.lastIndexOf('.');
               if( i>0 ) {
                  int k;
                  int n = text.length();
                  for( k=n-1; k>i && text.charAt(k)=='0'; k--);
                  if( k!=n-1) {
                     if( text.charAt(k)=='.') k--;
                     StringBuilder trail = new StringBuilder(16);
                     for( j=k;j<n-1;j++) trail.append(' ');
                     text = text.substring(0,k+1)+trail.toString();
                  }
               } else {
                  for( j=0; j<w.precision+1; j++ ) text+=' ';
               }
            } catch( Exception e) {}
         }
      }

      // Determination de la taille
      boolean flagCut=false;  // true si le mot est tronqué
      FontMetrics fm = g.getFontMetrics();
      widthw = fm.stringWidth(text) + (w.sort!=Field.UNSORT ? 12 : 0);
//      widthw = (int)( text.length()*wblanc + (w.sort!=Field.UNSORT ? 12 : 0) );
      if( w.onMouse ) {
         if( widthw<(int)(w.width*wblanc) ) width = (int)(w.width*wblanc);
         else {
            width=widthw;
            if( w.archive || w.footprint ) width+=3;
         }
      } else {
         width = (int)(w.width*wblanc);
         flagCut = widthw>(int)(w.width*wblanc) ;
      }

      // Remplissage prealable si necessaire
      if( flagClear || w.onMouse || w.show ) {
         Color bg = background;

         // Cellule sous la souris
         if( w.onMouse ) bg = Aladin.COLOR_MEASUREMENT_BORDERS_MOUSE_CELL;
         
         // Ligne sous la souris
         else if( w.show ) {
            
            // Ligne non "sélectionnée" (changement pour simple mouseMove)
            if( objSelect==null ) bg = Aladin.COLOR_MEASUREMENT_BACKGROUND_SELECTED_LINE;
            
            // Ligne sélectionnée (il faut recliquer pour la désélectionner)
            else  bg = Aladin.COLOR_STACK_SELECT;
         }

         g.setColor( bg );
         g.fillRect(x-6,y-HF,width+9,HL-1);
         if( w.onMouse && !(w.archive || w.footprint)  ) {
            int M=2;
            g.setColor( Aladin.COLOR_MEASUREMENT_BACKGROUND_MOUSE_CELL );
            g.fillRect(x-6+M,y-HF+M-1,width+9-2*M,HL-1-2*M+2);
         }
      }

      Color fg = Aladin.COLOR_MEASUREMENT_FOREGROUND;
      
      // ça dépasse à gauche ou à droite
      if( x+width<1  || x>W ) return width;

      // Une marque GLU (ancre)
      else if( w.glu) { // && !(w.archive || w.footprint) ) {
         
         // Le lien vient d'être cliqué
         if( w.pushed ) fg = Color.red ;
         
         // Le lien a été cliqué au préalable
         else if( w.haspushed ) {
            fg =  Color.green;
         }
         
         // Le lien n'a jamais été cliqué jusqu'à présent
         else fg = Aladin.COLOR_FOREGROUND_ANCHOR;
      } 
      
      // Sous la souris ?
      else if( w.onMouse ) fg = Aladin.COLOR_MEASUREMENT_FOREGROUND_SELECTED_LINE;
      
      // Dans l'entête ?
      else if( y<=2*HF ) fg = Aladin.COLOR_MEASUREMENT_HEADER_FOREGROUND;
      
      // Une valeur issue d'une colonne "calculée"
      else if( w.computed ) fg = Aladin.COLOR_MEASUREMENT_FOREGROUND_COMPUTED;
      
      // Ligne sélectionnée => Adaptation de la couleur pour que cela tranche sur le fond
      if( w.show ) {
         if( w.onMouse ) fg = fg.darker().darker();
         else fg = fg.brighter().brighter();
      }
      
      g.setColor( fg );

      // Mot trop long ?
      if( flagCut ) {
         int c = (int)((widthw - (width - (w.archive?6:0)) )/wblanc +2);
         int offset=text.length()-c;
         if( offset>0 ) text = text.substring(0,offset);
      }

      // Affichage du texte en fonction de l'alignement
      xtext=x;
      if( !flagCut ) {
         if( w.align==Words.RIGHT ) xtext=x+width-widthw;
         else if( w.align==Words.CENTER ) xtext=x+width/2 - widthw/2;
      }

      if( w.archive || w.footprint || w.servdef ) drawButton(g,xtext,y,HF,width,text,w);
      else g.drawString(text,xtext,y);

      // On ajoute des points à la fin du mot s'il est coupé
      if( flagCut ) {
         Font f = g.getFont();
         int xp = g.getFontMetrics().stringWidth(text.trim())+xtext;
         g.setFont(Aladin.PLAIN);
         g.drawString("...",xp,y);
         g.setFont(f);
      }

      // On dessine le triangle du mode de tri (ne concerne que l'entête)
      if( w.sort!=Field.UNSORT ) {
         Color c = w.onMouse ? Color.white :  background;
         drawSort(g,x+width-10,y,w.sort,c );
      }

      // On souligne s'il s'agit d'une ancre
//      if( w.glu ) g.drawLine(xtext,y+1,xtext+fm.stringWidth(text),y+1);

      // Tracé de l'épinglette
      if( w.pin && !aladin.isFullScreen() )  {
         if( pin==null ) pin = aladin.getImagette("Pin.png");
         g.drawImage(pin,x-16,y-HF-1,aladin);
      }

      drawBordG(g,x,y-HF,width);

      return (int)Math.ceil(w.width*wblanc)+1;
   }


   /** Mise a jour d'une ligne.
    * Ecrit dans le contexte graphique les infos d'une ligne de mesures contenues dans
    * le vecteur word a l'ordonnee y
    * Rq: Le repere qui commence generalement la ligne sera trace a la fin (memorisation)
    *     afin d'effacer un eventuel mot qui serait trace en dessous suite a un decallage
    *     a gauche des valeurs (par drag souris)
    *
    * @param g          Le contexte graphique
    * @param y          La position de la ligne de base (ordonnee)
    * @param ligne      Le vecteur contenant les differents mots de la ligne de mesures
    * @param flagClear  true si efface la surface au prealable
    * @return le nombre de pixels necessaires a la ligne
    */
   protected int drawLigne(Graphics g,int y,Vector ligne,boolean flagClear) {
      return drawLigne(g,0,y,ligne,flagClear,W);
   }
   protected int drawLigne(Graphics g,int X,int y,Vector ligne,boolean flagClear,int W) {
      Words w;       // liste courante de mots
      int x=X+absX;    // Abscisse courante dans la ligne
      int width;     // Largeur du mot (ou du repere) courant
      Enumeration e = ligne.elements();        // Pour faciliter la manip
      Source o = (Source)e.nextElement(); // L'objet lui-meme est en 1er place du vecteur
      Color bg = Aladin.COLOR_MEASUREMENT_BACKGROUND;

      // Pour l'extraction du repere de debut de ligne
      Words rep = null;
      boolean flagFirst=true;
      boolean show=false;

      // Affichage du repere et de chaque mot
      for( int i=0; e.hasMoreElements(); i++ ) {
         w = (Words) e.nextElement();
         
         if( i==0 ) {
            // bg = aladin.getBackground(); //(w.num%2==0) ? Color.white : BG ;
            
            // Effacement de la ligne si necessaire
            if( flagClear && W!=-1 ) {
               g.setColor( bg );
               g.fillRect(X+1,y-HF,X+W,HF+3);
            }
         }
         
         if( w.show ) show=true;

         // Dans le cas d'un repere
         if( i==0 ) {
            if( flagFirst ) rep = w;
            width=tX[2];

            // Dans le cas d'un mot
         } else {
            w.setPosition(x,y-HF,0,0);    // Memo des coord
            width = drawWords(g,w,flagClear);
         }

         w.setPosition(x,y-HF,width,HF);  // Memo de la boite recouvrant le mot, le repere
         x += (int)(width+wblanc);               // Calcul de la prochaine abscisse
         flagFirst=false;
      }

      if( W!=-1 ) {

         // Affichage du repere de debut de ligne si necessaire
         if( rep!=null && y>HF ) Util.drawCheckbox(g,X+3,y-HF/2-3,o.plan.c,null,null,o.isTagged());

         // On finit le fond de la ligne si nécessaire
         if( x<X+W ) {
            g.setColor(show ? Aladin.COLOR_MEASUREMENT_BACKGROUND_SELECTED_LINE : bg);
            g.fillRect(x-4, y-HF, X+W-(x-4), HF+2);
         }

         g.setColor( Aladin.COLOR_MEASUREMENT_LINE );
         g.drawLine(X+1,y+2,X+1+W,y+2);
      }

      return x-absX;

   }

   /** Retourne true s'il s'agit de la source et de l'indice du champ
    * de l'histogramme courant */
   //   private boolean isHist(Source o,int nField) {
   //      Hist hist = aladin.calque.zoom.zoomView.hist;
   //      return hist!=null && aladin.calque.zoom.zoomView.flagHist
   //           && hist.o==o && hist.nField==nField;
   //   }

   /** Redessine l'entête qui si c'est nécessaire (sur un mouseMoved par exemple) */
   private void quickDrawHead(Graphics g,Source o) {
      if( o!=null && o.getLeg()==oleg  ) return;      // déjà fait
      drawHead(g,o);
   }

   private void clearHead(Graphics g,int X,int W) {
      oleg=null;
      if( W<=0 ) return;
      g.setColor( Aladin.COLOR_MEASUREMENT_BACKGROUND  );
      g.fillRect(X+1,1,W,HF+3);
      //      Aladin.trace(4,"MCanvas.clearHead()");
   }

   private Image pin=null;  // L'image de l'épinglette

   /** Dessine la ligne d'entête, ou l'efface si null */
   protected void drawHead(Graphics g,Source o) { drawHead(g,0,0,o,W); }
   protected void drawHead(Graphics g,int X,int y,Source o,int W) {
      Vector head;
      if( o==null && oleg==null || o!=null && oleg==o.getLeg() ) head=ligneHead;
      else {
         head= o==null ? null : aladin.mesure.getHeadLine(o);
         oleg= o==null ? null : o.getLeg();
         ligneHead=head;
      }

      // Effacement de la ligne
      if( head==null && W!=-1 ) {
         clearHead(g,X,W);
         return;
      }

      Words w;       // liste courante de mots
      int x= X+absX;       // Abscisse courante dans la ligne
      y+=HF+1;
      int width;     // Largeur du mot (ou du repere) courant
      Enumeration e = head.elements();        // Pour faciliter la manip
      o = (Source)e.nextElement(); // L'objet lui-meme est en 1er place du vecteur

      // Affichage de chaque mot
      for( int i=0; e.hasMoreElements(); i++ ) {
         w = (Words) e.nextElement();

         // Dans le cas de la position du repère
         if( i==0 ) {
            width=tX[2];
            if( W!=-1 ) {
               g.setColor(Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND);
               g.fillRect(x+1,y-HF,width+2,HF+2);
               //               if( triTag!=Field.UNSORT ) drawSort(g,5,y,triTag,Aladin.BKGD);
            }

         } else {
            w.setPosition(x,y-HF,0,0);    // Positionnement
            width = drawWords(g,w,true,Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND);
         }

         w.setPosition(x,y-HF,width,HF);  // Memo de la boite recouvrant le mot, le repere
         x += (int)(width+wblanc);               // Calcul de la prochaine abscisse
      }

      if( W!=-1 ) {

         // On finit le fond de la ligne si nécessaire
         if( x<X+W ) {
            g.setColor(Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND);
            g.fillRect(x-4, y-HF, W-(x-4), HF+2);
         }

         g.setColor(o.plan.c );
         g.drawLine(X+1,y+2,X+1+W ,y+2);
      }

   }

   /** Remonte un eventuel bouton et chargement d'une image d'archive */
   //   public boolean mouseUp(Event ev, int x, int y) {
   public void mouseReleased(MouseEvent ev) {
      if( aladin.inHelp ) { aladin.setHelp(false); return; }
      if( flagDrag ) { return; }
      int x = ev.getX();
      int y = ev.getY();
       System.out.println("x y "+x+" "+y); 
      requestFocusInWindow();

      //Menu Popup
      if( (ev.getModifiers() & java.awt.event.InputEvent.BUTTON3_MASK) !=0
            /* && currentsee<aladin.mesure.getNbSrc() */) {
         popupShow(x,y);
         return;
      }

      // on avertit les FilterProperties qu'il y a eu un click dans les mesures
      if( FilterProperties.clickInMesure(sCourante, indiceCourant) ) return;

      if( wButton!=null ) {
         wButton.pushed=false;
         wButton.haspushed=true;
         
         aladin.mesure.datalinkshowX = x;
		 aladin.mesure.datalinkshowY = y;
         wButton.callArchive(aladin, oButton, oo);
         
         wButton=null; oButton=null;
         repaint();
      }

      flagDragX=-1;
      flagDragY=-1;
   }

   int memFirstsee=0;
   private int flagDragX=-1;
   private int flagDragY=-1;

   //   private double [] xHist = null;
   //   private String [] sHist = null;

   /** Tri des sources de même type que celle passée en paramètre. La légende
    * concernée permet de déterminer le précédent tri effectué pour éventuellement
    * switcher entre un tri ascendant et descendant */
   private void tri(Source o,int nField) {
      boolean ascending;
      //      int realNField = o.leg.getRealFieldNumber(nField);
      //      if( nField!=realNField ) System.out.println("nField="+nField+" realdNField="+realNField);
      //      nField = realNField;
      if( nField==-1 ) {
         o.getLeg().clearSort();
         triTag = Field.SORT_ASCENDING;
         ascending = true;
      } else {
         triTag = Field.UNSORT;
         ascending = o.getLeg().switchSort(nField);
      }
      ligneHead = aladin.mesure.getHeadLine(o);
      aladin.mesure.tri(o,nField,ascending);
   }

   //   /** Génération de l'histogramme de répartition des valeurs du champ nField
   //    * de toutes les sources de même légende que la source étalon o
   //    */
   //   protected void histo(Source o,int nField) {
   //      if( o.leg.isNumField(nField) ) {
   //         xHist=aladin.mesure.getFieldValues(xHist, o, nField);
   //         aladin.calque.zoom.zoomView.setHist(xHist,o,nField);
   //      } else {
   //         sHist=aladin.mesure.getFieldValues(sHist, o, nField);
   //         aladin.calque.zoom.zoomView.setHist(sHist,o,nField);
   //      }
   //   }

   /** Tri des sources de même type que celle passée en paramètre. */
   protected void tri(Source o,int nField,boolean ascending) {
      //      nField=o.leg.getRealFieldNumber(nField);
      o.getLeg().setSort(nField, ascending?Field.SORT_ASCENDING:Field.SORT_DESCENDING);
      ligneHead = aladin.mesure.getHeadLine(o);
      aladin.mesure.tri(o,nField,ascending);
   }

   // Cree le popup menu associe au View
   private void createPinPopupMenu() {
      JMenuItem j;
      Chaine c = aladin.chaine;
      popMenuTag = new JPopupMenu();
      popMenuTag.setLightWeightPopupEnabled(false);
      popMenuTag.add( menuTag1=j=new JMenuItem(c.getString("MFTAG")));
      j.addActionListener(this);
      popMenuTag.add( menuUntag1=j=new JMenuItem(c.getString("MFUNTAG")));
      j.addActionListener(this);
      popMenuTag.add( menuHelpTag=j=new JMenuItem("Help..."));
      j.addActionListener(this);
   }


   /** Clic sur une marque HTML
    * Appel au Glu resolver si on se trouve sur une ancre GLU
    */
   //   public boolean mouseDown(Event ev, int x, int y) {
   public void mousePressed(MouseEvent ev) {
      if( aladin.inHelp ) return;
      int x = ev.getX();
      int y = ev.getY();
      boolean flagPopup=false;

      // On va ouvrir le Popup sur le mouseUp()
      if( (ev.getModifiers() & java.awt.event.InputEvent.BUTTON3_MASK) !=0 ) {
         sortField = indiceCourant;
         flagPopup=true;
      }
      
      // externalisation d'un panel dans une fenêtre indépendante
      if( rectOut!=null && rectOut.contains(ev.getPoint()) ) {
         aladin.mesure.split();
         return;
      }

      // Clic dans l'entête
      if( !flagPopup && y<MH && ligneHead!=null) {

         // On va redimensionner
         if( onBordField!=-1 ) {
            onBordX = x;
            return;
         }

         // Info sur l'épinglette et les boites à cocher
         if( indiceCourant==-1 ) {
            popPinShow( ev.getX(), ev.getY());
            return;
         }

         // On va faire l'histogramme et si déjà fait trier
         timer.stop();
         Source o = (Source) ligneHead.elementAt(0);
         aladin.calque.zoom.zoomView.setHist(o,indiceCourant);
         tri(o,indiceCourant);
         return;
      }

      aladin.calque.zoom.zoomView.stopHist();

      // Il n'y a pas de mesure dans la fenetre des mesures
      if( currentsee<0 || aladin.mesure.getNbSrc()<=currentsee ) return;

      // En cas d'un drag
      ox=x; oy=y;
      memFirstsee=firstsee;

      // Recuperation des mots de la ligne courante
      Vector ligne = aladin.mesure.getWordLine(currentsee);
      Enumeration e = ligne.elements();      // Pour faciliter la manip.
      Source o = (Source)e.nextElement();    // L'objet lui-meme
      
      // Pour déselectionner la source correspondant à la ligne sous
      // la souris (shift appuyé). Il faut mettre ow=null pour éviter
      // des ghosts par la suite
      if( !flagPopup && ev.isShiftDown() ) {
         deselect(o);
         return;
      }

      // Recalibration dynamique en cours ?
      if( !flagPopup && aladin.frameNewCalib!=null && aladin.frameNewCalib.isShowing() ) {
         aladin.frameNewCalib.mouse(0,0,null,o);
      }

      // repérage de la source
      boolean rep;

      // Synchronization de toutes les vues sur la source
      if( !flagPopup ) {
         if( ev.getClickCount()==2 || aladin.calque.getPlanBase() instanceof PlanBG ) {
            rep=aladin.view.setRepere(o);
            
            // Centrage de la vue courante uniquement sans changement de zoom
         } else  rep=aladin.view.syncSimple(o);

         // Affichage de la coordonnees de la source
         aladin.search.setColorAndStatus(rep?1:-1);
         aladin.mesure.search.setColorAndStatus(rep?1:-1);
         aladin.localisation.seeCoord(o,1);
         if( aladin.frameCooTool!=null ) aladin.frameCooTool.setSource(o);
         aladin.sendObserver();

         // Je clique dans le tag
         if( x<15 ) {
            o.setTag( !o.isTagged() );
            triTag=Field.UNSORT;
            repaint();
            return;
         }

         for( int numField=0; e.hasMoreElements(); numField++ ) {
            Words w = (Words) e.nextElement(); // Analyse mot par mot

            if( w.inside(x,y) ) {              // C'est gagne
               
               // On fait un copier de la valeur sous la souris dans le presse papier
               if( ev.getClickCount()==2 ) aladin.copyToClipBoard(w.text);
               
               if( !w.glu && !w.footprint ) continue;             // Inutile si ce n'est pas une marque GLU

               // Mémorisation du lien qui aura été cliqué
               aladin.mesure.setHaspushed(o,numField);
               
              // Indication d'appel direct a l'archive
               if( w.archive ) {
                  wButton=w;	// Memo en vue de l'utilisation dans mouseUp
                  oButton=o;	// Idem
                  w.pushed=true;
                  Graphics g = getGraphics();
                  g.setFont(FONT);
                  drawWords(g,w,true);
                  return;
               }

               // Show/hide associated FoV (thomas VOTech)
               if( w.footprint ) {
                  PlanField pf = o.getFootprint().getFootprint();
                  if (pf != null) {
                     pf.setActivated(true);
                     pf.flagOk = true;

                  }
                  o.switchFootprint();
                  return;
               }

               // Appel a l'ancre
               aladin.glu.newWindow(ev.isControlDown());// Pour pouvoir creer une nouvelle fenetre
               w.callGlu(aladin.glu,this);
               Graphics g = getGraphics();
               g.setFont(FONT);
               drawWords(g,w,true);
               return;
            }
         }
      }

      // Sélection temporaire de l'objet cliqué pour pouvoir voir le menu associé
      if( objSelect==null || flagPopup ) { objSelect=o; setShow(ligne,true); }
      else objSelect=null;

      // Pour le déplacement horizontal et vertical par souris
      flagDragX=x;
      flagDragY=y;
      flagDrag=false;

      currentselect=(currentselect==currentsee)?-2:currentsee;
      repaint();
   }
   
   /** Déselection de la source indiquée */
   void deselect(Source o) {
      if( o==null ) return;
      if( aladin.view.deSelect(o) ) {
         mouseLigne=null;
         repaint();
      }
   }
   
   /** Edition d'une source (uniquement s'il s'agit d'une source editable (SourceTag) */
   void edit(Source o) {
      if( o==null || !(o instanceof SourceTag) ) return;
      aladin.view.editPropObj(o);
   }

   /** Edition d'une source (uniquement s'il s'agit d'une source editable (SourceTag) */
   void info(Source o) {
      try {
         new FrameRecord("Source record ("+o.id+") from "+o.plan.id,o.getRecord());
      } catch( Exception e ) { }
   }

   /** Suppression d'une source (uniquement pour les plan isSourceRemovable() */
   void delete(Source o) {
      if( o==null || !o.plan.isSourceRemovable() ) return;
      if( aladin.view.deSelect(o) ) mouseLigne=null;
      aladin.calque.delObjet(o);
      aladin.calque.repaintAll();
   }

   public void mouseWheelMoved(MouseWheelEvent e) {
      int mode = e.getWheelRotation();
      int n = scrollV.getValue();
      if( n+mode>=scrollV.getMaximum() ) return;
      if( n+mode<0 ) return;
      scrollV.setValue(n+mode);
   }


   public void mouseDragged(MouseEvent e) {
      if( aladin.inHelp ) return;
      int x = e.getX();
      int y = e.getY();

      // On redimensionne la colonne
      if( onBordField!=-1 ) {
         Field f = oleg.field[onBordField];
         int offset = (int)((onBordX-x)/wblanc);
         if( offset==0 ) return;
         int colSize = f.columnSize;
         colSize-=offset;
         if( colSize<3 ) return;
         f.columnSize=colSize;
         onBordX=x;
         reloadHead();
         repaint();
         return;
      }

      if( flagDragX!=-1 ) {
         int offset = flagDragX-x;
         if( offset!=0 ) {
            int scroll = scrollH.getValue();
            scrollH.setValue(scroll+offset);
            flagDragX=x;
            flagDrag=true;
         }
      }
      if( flagDragY!=-1  ) {
         int offset = (flagDragY-y)/HL;
         if( offset!=0 ) {
            int scroll = scrollV.getValue();
            scrollV.setValue(scroll+offset);
            flagDragY=y;
            flagDrag=true;
         }
      }

      if( flagDrag ) paintComponent(getGraphics());
   }

   /** Suppression des drapeaux show et OnMouse de la ligne sous la souris en vue
    * de son effacement */
   private void clearMouseLigne(Graphics g) {
      clearLigne1(g,mouseLigne,false);
      mouseLigne=null;
   }

   /** Suppression des drapeaux show et OnMouse de la ligne sélectionnée en vue
    * de son effacement */
   private void clearShowLigne(Graphics g) {
      clearLigne1(g,showLigne,true);
      showLigne=null;
   }

   /** Suppression des drapeaux show et onMouse de la ligne passée en paramètre
    * + réaffichage (voir clearMouse() et clearShow()) */
   private void clearLigne1(Graphics g,Vector ligne,boolean flagShow) {
      if( ligne==null ) return;
      Enumeration e1 = ligne.elements();
      Source o = (Source)e1.nextElement(); // On passe l'objet
      Words rep=null;

      // On remet le fond
      Words w=((Words)ligne.elementAt(1));
      int y = w.y;
      Color bg = Aladin.COLOR_MEASUREMENT_BACKGROUND; // aladin.getBackground(); // (w.num%2==0) ? Color.white :BG ;
      g.setColor(y<=HF ? Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND : bg);
      g.fillRect(0,y, W, HF+2);

      int size=0;
      while( e1.hasMoreElements() ) {
         w = (Words)e1.nextElement();
         w.onMouse=false;
         if( flagShow ) w.show = false;
         if( w.repere ) rep=w;
         else size=drawWords(g,w,true);
      }

      // On finit le fond de la ligne si nécessaire
      int x=w.x+size+3;
      if( x<W ) {
         g.setColor(w.show ? Aladin.COLOR_MEASUREMENT_BACKGROUND_SELECTED_LINE : w.y<HF ? Aladin.COLOR_MEASUREMENT_HEADER_BACKGROUND : bg);
         g.fillRect(x, y, W-x, HF+2);
      }

      // Affichage du repere de debut de ligne si necessaire
      if( rep!=null && y>HF) Util.drawCheckbox(g,3,y+HF/2-3,o.plan.c,null,null,o.isTagged());
   }

   /** Initialisation de la ligne à montrer  + affichage */
   private int initShow(Graphics g,int y,Vector ligne) {
      setShow(ligne,true);
      showLigne=ligne;
      return drawLigne(g,y,ligne,true);
   }

   /** Initialisation de la ligne à montrer (drapeau show à positionner sur tous les mots */
   private void setShow(Vector ligne,boolean flag) {
      if( ligne==null ) return;
      Enumeration e1 = ligne.elements();
      e1.nextElement(); // On passe l'objet
      while( e1.hasMoreElements() ) {
         Words w = (Words)e1.nextElement();
         w.show = flag;
      }
   }

   /** Force la reconstruction de la ligne d'entête */
   protected void reloadHead() {
      if( ligneHead==null ) return;
      Source o = (Source)ligneHead.elementAt(0);
      ligneHead=aladin.mesure.getHeadLine(o);
   }

   /** Désélection de la ligne en cours de sélection */
   protected void unselect() { objSelect=null; }

   /** Construit la description du champ i de l'objet o */
   private String getDescription(Source o,int i,boolean flagShort) {
      StringBuilder res = new StringBuilder();
      if( o.getLeg()!=null && o.getLeg().field!=null && i>=0 && i<o.getLeg().field.length ) {
         Field f = o.getLeg().field[i];
         A(res,o.getLeg().name);
         if( !flagShort ) {
            A(res," - ",f.name);
            A(res,": ",adjustVizier(f.description));
            if( f.unit!=null ) A(res," ("+f.unit+")");
         }
      }
      return res.toString();
   }

   /** Construit la description longue (info) du champ i de l'objet o */
   private String getInfo(Source o,int i) {
      Legende leg = o.getLeg();
      StringBuilder res = new StringBuilder();
      if( leg!=null && leg.field!=null && i>=0 && i<leg.field.length ) {
         Field f = leg.field[i];
         if( f.description==null && f.unit==null && f.ucd==null && f.utype==null ) return "";
         
         // Récupération du titre de la légende et du petit nom du obs_collection
         String s = leg.name!=null ? leg.name : "";
         if( o.plan!=null ) {
            MyProperties prop = aladin.directory.getProperties( o.plan.id );
            if( prop!=null ) {
               if( s.equals("CDSCatFileQuery") ) s=o.plan.id;   // Cochonnerie de FX
               String s1 = prop.getFirst("obs_collection_label");
               if( s1!=null ) {
                  if( s.length()>0 ) s= s1+" - "+s;
                  else s = s1;
               }
            }
         }
         A(res,s);
         A(res,"\n \n* Field: ",f.name);
         String v = s=o.getValue(i);
         if( s.length()>50 ) s=s.substring(0,47)+"...";
         A(res,"\n* Value: ", s );
         A(res,"\n* Unit: ",f.unit);
         if( f.isTime() ) A(res,"\n => "+Astrodate.JDToDate( Astrodate.parseTime(s, f.coo)));
         else if( (s = convert( v, f.unit))!=null ) A(res,"\n => ",s);
         A(res,"\n* UCD: ",f.ucd);
         A(res,"\n* Utype: ",f.utype);
         A(res,"\n \n",adjustVizier(f.description));
      }
      return res.toString();
   }
   
   // Conversion éventuelle, sinon null
   private String convert( String val, String unit) {
      if( unit==null || val==null ) return null;

      // Une date en JD ou MJD => ISO
      boolean mjd=false;
      try {
         if( unit.equalsIgnoreCase("jd") || (mjd=unit.equalsIgnoreCase("mjd")) )  {
            double x = Double.parseDouble(val);
            if( mjd ) x = Astrodate.MJDToJD( x );
            return Astrodate.JDToDate( x );
         }
      } catch( NumberFormatException e ) { }
      return null;
   }
   
   // Enlève cette cochonnerie de "? " en préfixe de certaines descriptions VizieR
   private String adjustVizier( String description ) {
      if( description==null || !description.startsWith("? ") ) return description;
      return description.substring(2);
   }
   
   // Ajoute à un String builder un mot, avec un éventuel suffixe
   private void A(StringBuilder res, String s) { A(res," ",s); }
   private void A(StringBuilder res, String sep, String s) {
      if( s==null || s.length()==0 ) return;
      if( res.length()==0 ) res.append(s);
      else { res.append(sep); res.append(s); }
   }

   //   /** Retourne le numéro du champ oé se trouve la souris */
   //   private int getFieldIndex(Vector v,int x,int y) {
   //      Enumeration e = vhead.elements();
   //      e.nextElement();
   //      for( int i=0; e.hasMoreElements(); i++ ) {
   //         Words w = (Words) e.nextElement();        // Les mots
   //         if( w.inside(x,y) ) return i-1;
   //      }
   //      return -1;
   //   }

   Timer timer=null;
   Source oTimer=null;
   int onField=-1;

   private void startTimerHist(Source o,int nField) {
      if( oTimer==o && onField==nField ) {
         //         aladin.trace(4, "MCanvas.startTimer("+o+","+nField+") nothing to do !");
         return;
      }
      oTimer=o;
      onField=nField;
      if( timer==null ) {
         timer = new Timer(500,this);
         timer.setRepeats(false);
         timer.start();
      } else timer.restart();
      //      aladin.trace(4, "MCanvas.startTimer("+o+","+nField+") restarted !");

   }

   private void endTimerHist() {
      if( oTimer==null || onField==-1 ) {
         //         aladin.trace(4, "endTimerHist() nothing to do !");
         return;
      }

      if( oTimer.getLeg().isSED() ) {
         aladin.view.zoomview.setSED(oTimer);
      } else  {
         aladin.calque.zoom.zoomView.setHist(oTimer,onField);
      }
      oTimer=null;
   }

   private Words ow=null; // Dernier mot sous la souris (pour éviter de refaire la méme chose)

   // Calcule le véritable indice du champ dans la légende associée é la source
   // en fonction de l'indice dans la table affichée car certains champs peuvent étre cachée
   // et il y a un décalage d'une valeur pour tenir compte de la case é cocher en début de ligne
   private int getRealIndice(Source s,int indice) {
      indice--;
      if( s.getLeg()==null ) return indice;
      
      // Comme si on affichait à nouveau
      int pos=-1;
      for( int i=0; i<s.getLeg().field.length; i++ ) {
         int nField = s.getLeg().fieldAt[ i ];
         if( s.getLeg().isVisible(nField) ) {
            pos++;
            if( pos==indice ) return nField;
         }
      }
      
      // Bizarre !!
      return indice;
   }

   /** Deplacement de la souris sur les mesures.
    * Reperage du mot courant et de l'objet associe dans la vue
    */
   //   public boolean mouseMove(Event evt, int x, int y) {
   public void mouseMoved(MouseEvent evt) {
      if( aladin.inHelp ) return;
      if( popMenu.isVisible() ) return;
      int x = evt.getX();
      int y = evt.getY();
      Graphics g = getGraphics();
      if( g==null ) return;
      g.setFont(FONT);
      Words w=null;        // Mot courant (sous la souris)
      Enumeration e;
      Source o;
      String tip="";
      int indice;


      // Est-on sur l'entête ?
      if( y<=MH ) {
         if( ligneHead==null ) return;
         Util.toolTip(this, TIPHEAD);
         onBordField=-1;
         o = (Source) ligneHead.elementAt(0);
         e = ligneHead.elements();
         e.nextElement();
         for( int i=0; e.hasMoreElements(); i++ ) {
            w = (Words)e.nextElement();
            indice=getRealIndice(o,i);
            if( w.onBord(x,y) ) onBordField=indice;
            if( x<15  || w.inside(x,y)  ) {
               if( ow==w ) break;
               //               indiceCourant=i-1;
               indiceCourant=indice;
               sCourante = o;

               // Démarrage du timer pour l'histogramme
               startTimerHist(sCourante,indice);

               if( objSelect==null && showLigne!=null ) clearShowLigne(g);
               if( mouseLigne!=null ) clearMouseLigne(g);
               w.onMouse=true;
               drawHead(g,o);
               drawWords(g,w,true);
               flagDrawHead=true;
               mouseLigne=ligneHead;
               ow=w;
               Util.drawEdge(g,W,H);
               aladin.mesure.showStatus( getDescription(o,indice, !aladin.mesure.flagSplit ) );
               aladin.mesure.showInfo( getInfo(o,indice), o.plan );
               break;
            }
         }

         if( onBordField!=-1 ) setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
         else aladin.makeCursor(this,Aladin.DEFAULTCURSOR);

        if( oo!=null ) {
           aladin.view.hideSource();
           oo.setShowFootprintTransient(false,true);
           oo=null;
        }
		
		drawIconOut(g);
        return;
      }

      aladin.makeCursor(this,Aladin.DEFAULTCURSOR);

      // Determination de la ligne courante
      currentsee = firstsee+ (y-MH)/HL;

      // Si on se trouve en dehors de toute ligne, on gere l'evenement
      // de la meme maniere que si l'on sort de la fenetre
      if ( currentsee>=aladin.mesure.getNbSrc() ) {
         indiceCourant=-1;
         if( oo!=null ) {
            aladin.view.hideSource();
            oo.setShowFootprintTransient(false,true);
            oo=null;
         }
         if( objSelect==null && showLigne!=null ) clearShowLigne(g);
         if( mouseLigne!=null ) clearMouseLigne(g);
         ow=null;
         Util.drawEdge(g,W,H);
         aladin.urlStatus.setText("");
         aladin.mesure.showStatus("");
         aladin.mesure.showInfo(null,null);
         Util.toolTip(this, tip);
         
         drawIconOut(g);
         return;
      }

      Vector ligne = aladin.mesure.getWordLine(currentsee);
      o = (Source)ligne.elementAt(0);

      // La ligne à montrer est-elle simplement sous la souris et s'agit-il
      // d'une ligne préalablement cliquée ?
      Source oshow = objSelect!=null ? objSelect : o;

      // Visualisation dans la vue de la source associee a la mesure courante
      if( oo!=oshow ) {
         aladin.view.showSource(oshow);

         if( oshow.isSetFootprint() ) oshow.setShowFootprintTransient(true,true);
         if( oo!=null && o.isSetFootprint() ) oo.setShowFootprintTransient(false,true);

         oo=oshow;
      }

      // Affichage de la coordonnees de la source
      aladin.localisation.seeCoord(oshow);

      // Pour mettre à jour le point d'un SED en cours de tracé
      showSEDPoint(oshow);

      // Affichage de l'entête correspondant à l'objet
      if( flagDrawHead ) drawHead(g,oshow);
      else quickDrawHead(g,oshow);
      flagDrawHead=false;

      // Affichage du texte (legende ou texte) associee a la mesure
      e = ligne.elements();
      
      o=(Source)e.nextElement();   // Je saute l'objet lui-meme
      boolean trouve=false;        // true si on trouve un mot a selectionner
      for( int i=0; e.hasMoreElements() && x>15 ; i++ ) {
         w = (Words) e.nextElement();        // Les mots
         indice=getRealIndice(o,i);
         if( w.inside(x,y)) {
            trouve=true;
            if( w==ow ) return;
            indiceCourant=indice;
            sCourante = o;

            // Démarrage du timer pour l'histogramme
            startTimerHist(sCourante,indice);

            if( objSelect==null && showLigne!=null ) clearShowLigne(g);
            if( mouseLigne!=null ) clearMouseLigne(g);

            if( objSelect==null ) initShow(g,w.y+HF,ligne);
            if( w.x>15 ) {
               if( !w.repere ) w.onMouse=true;
               drawWords(g,w,true);
            }
            
//            String sDesc = getDescription(o,indice);
//            String sUCD = o.leg.getUCD(indice);
//            String sHref = w.glu ? w.getHref() : "";
//            StringBuilder sTip = new StringBuilder();
//            if( sDesc.length()>0 ) add(sTip,sDesc);
//            if( sUCD.length()>0 ) add(sTip,"UCD: "+sUCD);
//            if( sHref.length()>0 ) add(sTip,sHref);
//            tip = sTip.length()>0 ? "<html>"+sTip.toString()+"</html>" : null;
//            Util.drawEdge(g,W,H);

            // Recuperation de la description du champ et d'un éventuel tooltip
            if( w.archive )  tip=TIPARCH;
            else if( w.glu )      tip=TIPGLU;
            else if( w.footprint)  tip=TIPFOV;
            try {
               if( w.glu ) w.urlStatus(aladin.urlStatus);
               else aladin.urlStatus.setText("");
            } catch(Exception ecurs) {}
            Util.drawEdge(g,W,H);
            
            // Les affichages de status et d'info
            aladin.mesure.showStatus( getDescription(o,indice, !aladin.mesure.flagSplit) );   // Mise a jour du status
            aladin.mesure.showInfo( getInfo(o,indice), o.plan );   // Mise a jour des infos au dessus du stack
            
            mouseLigne=ligne;
            ow=w;

            break;
         }
      }
      
      // Un tooltip sur la coche ?
      if(x<15 ) tip=TIPTAG;

      // thomas
      if(!trouve) {
         if( objSelect==null && showLigne!=null ) clearShowLigne(g);
         if( mouseLigne!=null ) clearMouseLigne(g);
         Util.drawEdge(g,W,H);
         ow=null;
         indiceCourant=-1;
      }

      drawIconOut(g);
      Util.toolTip(this, tip);
   }
   
   private void add(StringBuilder tip, String s) {
      if( s.length()==0 ) return;
      if( tip.length()>0 ) tip.append("<br>"+s);
      else tip.append(s);
   }


   protected void showSEDPoint(Source s) {
      if( s.getLeg()!=null && s.getLeg().isSED() && aladin.view.zoomview.flagSED )  aladin.view.zoomview.setSED(s);
   }

   //   public boolean mouseEnter(Event e, int x, int y) {
   public void mouseEntered(MouseEvent e) {
      ow=null;
      if( aladin.inHelp ) { aladin.help.setText(Help()); return; }

      // Juste pour repasser un sélection
      if( aladin.mesure.getNbSrc()>0 ) aladin.toolBox.mouseEnter(null,0,0);

      //      if( aladin.mesure.nbSrc>0 ) requestFocusInWindow();  // pour pouvoir taper une chaine
      Aladin.makeCursor(this,Aladin.HANDCURSOR);
   }

   /** Sortie de la souris.
    * Fin de reperage du mot courant et de l'objet associe dans la vue
    */
   public void mouseExited(MouseEvent e) {// Clear du status
      if( aladin.inHelp ) return;
      if( timer!=null ) timer.stop();
      aladin.mesure.showInfo(null,null);
      aladin.urlStatus.setText(aladin.COPYRIGHT);   // Remet le copyright
      currentsee=-1;                                // Plus aucune ligne courante
      Aladin.makeCursor(this,Aladin.DEFAULTCURSOR);

      // Traitement de l'objet dans la vue
      if( oo!=null ) { 
         
         oo.setShowFootprintTransient(false,true);
         
         aladin.view.hideSource();
         oo=null; 
      }

      // Petit nettoyage
      if( ow!=null ) ow.onMouse=false;
      repaint();
   }

   /** Ouverture de l'outil de manipulation des coordonnées pour la source indiquée */
   protected void openCooToolbox(Source o) {
      if( aladin.frameCooTool==null ) aladin.frameCooTool = new FrameCooToolbox(aladin);
      aladin.frameCooTool.setSource(o);
   }

   /** Désignation d'une ligne de mesure.
    * Recherche d'une ligne de mesures dans la fenetre des mesures
    * associees a un objet
    * @param o L'objet a trouver dans la liste des mesures
    * @param mode 0-montre qq soit l'état, 1-montre si non bloqué,
    *             2-montre et bloque, 3-montre et bloque si non bloqué avant, sinon débloque
    * @return <I>true</I> si la recherche aboutie, sinon <I>false</I>
    */
   protected boolean show(Source o,int mode) {
      int n;                  // Numero de ligne
      int ntext;              // Nombre de ligne de mesures
      boolean retour=false;   // Le code de retour par defaut

      if( mode==1 && objSelect!=null ) return false;

      if( mode==3 ) {
         if( objSelect!=null ) unselect();
         else objSelect=o;
      } else {
         unselect();
         if( mode==2 ) objSelect=o;
      }

      if( o==null )  currentsee=-1;        // Logique !
      else {
         for( n=0, ntext=aladin.mesure.getNbSrc(); n<ntext; n++ ) {
            Vector ligne = aladin.mesure.getWordLine(n);
            if( (Source)ligne.elementAt(0)!=o ) continue; // L'objet se trouve en 1ere position

            // Est-il dans la fenetre visible ?
            if( n<firstsee || n>lastsee ) {
               if( n<firstsee ) scrollV.setValue(n);
               else scrollV.setValue(n-nbligne+1);
               aladin.mesure.validate();  // pour la scrollV
            }
            currentsee=n;             // Nouvelle position courante
            objShow=o;                  // Ligne qu'il faut montrer (surligné en bleu)
            retour=true;
            break;
         }
      }

      if( retour ) showSEDPoint(o);

      repaint();
      return retour;
   }

   private int omax=-1;
   private int oW=-1;
   private boolean showScrollH=true;

   /** Ajustement du scrollbar horizontal si necessaire
    * @param max le nombre de pixels du texte
    * @return true si on a dû ajouter la barre horizontale
    */
   private void adjustScrollH(int max) {
      boolean ok;

      if( max==omax && oW==W ) return;
      omax=max;
      scrollH.setMaximum(max+20);
      
      // Juste en cas d'ouverture du panel
      if( W!=oW ) {
         scrollH.setVisibleAmount(W);
         scrollH.setBlockIncrement( (int)(W-10*wblanc) );
         oW=W;
      }

      if( ok=(max<W && showScrollH) ) { aladin.mesure.remove(scrollH); showScrollH=false; }
      else if( ok=(max>=W && !showScrollH) ) {
         aladin.mesure.add(scrollH,"South");
         showScrollH=true;
         scrollH.setVisibleAmount(W);
         scrollH.setBlockIncrement( (int)(W-10*wblanc));
         nbligne--;
      }
      if( !ok ) return;

      int deltaY = scrollH.getSize().height;
      if( showScrollH ) deltaY=-deltaY;
      scrollV.setSize( scrollV.getSize().width, scrollV.getSize().height+deltaY);

      aladin.mesure.validate();
   }

   private boolean showScrollV=true;
   
   /** Ajuste le block et l'extend du scrollbar vertical en fonction du nombre
    * de lignes visibles
    * @param nl Nombre de lignes affichables
    * @param needScrollBar la scrollbar est requise.
    */
   protected void adjustScrollV(int nl, boolean needScrollBar) {
      if( !needScrollBar  ) {
         if( showScrollV ) { aladin.mesure.remove(scrollV); showScrollV=false; }
      } else {
         if( !showScrollV ) { aladin.mesure.add(scrollV,"East"); showScrollV=true; }
      }
      aladin.mesure.validate();

      scrollV.setVisibleAmount(nl);
      scrollV.setBlockIncrement(nl-1);
   }

   /** Surcharge juste pour en profiter pour mettre à jour
    * le nombre de sources sélectionnées dans la fenétre des mesures */
   public void repaint() {
      aladin.adjustNbSel();
      super.repaint();
   }
   
   private Image iconOut=null;
   private Rectangle rectOut = null;
   

   // Regeneration du plan image pour la fenetre des mesures
   public void paintComponent(Graphics gr) {
      paintComponent1(gr);
      drawIconOut(gr);
   }
   
   // tracé de l'icone permettant l'extraction du panel dans une fenêtre indépendante
   private void drawIconOut(Graphics gr) {
      if( aladin.isFullScreen() ) return;
      if( aladin.mesure.isSplitted() ) return;
      if( iconOut==null ) iconOut = aladin.getImagette("Preview.gif");
      if( iconOut!=null ) {
         int w=16;
         int x = getWidth()-w-8;
         int y = getHeight()-w-8;
         gr.drawImage(iconOut, x,y,aladin);
         rectOut = new Rectangle(x,y,w,w);
      }
   }
   
   private void paintComponent1(Graphics gr) {
      
      super.paintComponent(gr);
      
      // Initialisation de la taille d'une lettre (la fonte est nécessaire monoscaped)
      if( wblanc==-1 ) {
         StringBuilder s = new StringBuilder();
         for( int i=0; i<100; i++ ) s.append('M');
         wblanc = gr.getFontMetrics(FONT).stringWidth(s.toString())/(double)s.length();
      }
      
      boolean flagDoubleBuffering = Aladin.useDoubleBuffering(gr);
      
      int j;

      // Positionnement du curseur apres le demarrage d'Aladin
      if( firstUpdate ) {
         firstUpdate=false;
         scrollV.setValue(0);
      }

      mouseLigne=null;
      showLigne=null;
      ow=null;
      
      if( img==null || img.getWidth(this)!=getWidth() || img.getHeight(this)!=getHeight() 
          ){
         W=getWidth();
         H=getHeight();
         img = aladin.createImage(W,H);
      }
      Graphics g = flagDoubleBuffering ? img.getGraphics() : gr;
      
      // AntiAliasing
      aladin.setAliasing(g);

      // Affichage des lignes visibles
      nbligne = (H- (MH+MB))/HL+1;             // Nbre de lignes
      int y = MH+HF+1;                    // Ordonnee courante
      int ts = aladin.mesure.getNbSrc();
      int max=0;			  // Taille max
      absX = -scrollH.getValue();
      lastsee=firstsee = scrollV.getValue();
      if( firstsee<0 ) lastsee=firstsee = 0;
      g.setFont(FONT);
      aladin.mesure.memoWordLineClear();

      Source oleg= objShow!=null ? objShow : objSelect;     // Quelle source é montrer ?

      for( j=0; lastsee<ts && j<nbligne; lastsee++, j++, y+=HL ) {
         Vector word = aladin.mesure.getWordLine(lastsee);
         if( word==null ) continue;

         // Mémorisation de la premiére source pour afficher l'entête correspondante
         if( oleg==null ) oleg = (Source)word.elementAt(0);

         int m = drawLigne(g,y,word,true);
         
         // Mémorisation de la WordLine afin de ne pas perdre les paramètres du tracé
         aladin.mesure.memoWordLine(word,lastsee);

         if( m>max ) max=m;
      }
      lastsee--;
      objShow=null;

      // On affiche l'entête de la source concernée (soit sous la souris, soit cliquée (oSelect))
      if( j>0 ) drawHead(g,oleg);
      else {
         ligneHead=null;
         clearHead(g,0,W);
      }

      // Si j'ai une scrollbar horizontale mais quelle ne serait pas nécessaire
      // il faut tout de même regarder si par hasard la ligne suivante n'en nécessiterait
      // pas une. Sinon, il va y avoir un effet de clignotement (ajout/retrait de
      // la scrollBar)
      if( showScrollH && max<W && lastsee+1<ts ) {
         Vector word = aladin.mesure.getWordLine(lastsee+1);
         int m = drawLigne(g,-30,word,true);
         if( m>max ) max=W;
      }
      
      // Ajustement des scrollbars si necessaire
      adjustScrollH(max);
      adjustScrollV((H- (MH+MB))/HL - (showScrollH?1:0), j<ts);

      // Nettoyage de la fin de la fenetre si necessaire
      y-=HF;
      int ry=H-y-1;                // La plage restante
      if( ry>0 ) {
         g.setColor( Aladin.COLOR_MEASUREMENT_BACKGROUND );
         g.fillRect(1,y,W-1,ry);
      }

      // Les bordures du cadre
      Util.drawEdge(g,W,H);
      
      if( flagDoubleBuffering ) gr.drawImage(img,0,0,this);
      
      if( aladin.view.zoomview.flagSED ) aladin.view.zoomview.repaint();
      
      /*if (aladin.mesure.isEnabledDatalinkPopUp) {
    	  List<String[]> datalinkInfos = aladin.mesure.getDataLinks();
    	  aladin.mesure.isEnabledDatalinkPopUp = false;
    	  
			if (datalinkInfos != null && !datalinkInfos.isEmpty()) {
				createAdditionalServiceMenu(datalinkInfos);
				datalinkPopupShow(datalinkshowX, datalinkshowY);
				datalinkshowX = -1;
				datalinkshowY = -1;
			}
      }*/
      
   }

   /** Gestion du Help */
   protected String Help() { return aladin.chaine.getString("MCanvas.HELP"); }

   //   // Gestion du Help
   //   public boolean handleEvent(Event e) {
   //      if( aladin.mesure.isSorting() ) {
   //         aladin.mesure.setStatus("...sorting...");
   //         return true;
   //      }
   //
   //      if( aladin.inHelp ) {
   //         if( e.id==Event.MOUSE_ENTER ) aladin.help.setText(Help());
   //         else if( e.id==Event.MOUSE_UP ) aladin.helpOff();
   //         return true;
   //      }
   //      return super.handleEvent(e);
   //   }

   public void mouseClicked(MouseEvent e) { }

   public void keyReleased(KeyEvent e) {
      if( aladin.inHelp ) { aladin.helpOff(); return; }

      Search sr = aladin.mesure.flagSplit ? aladin.mesure.search : aladin.search;

      int c = e.getKeyCode();
      if( c==KeyEvent.VK_UP || c==KeyEvent.VK_LEFT ) sr.execute("-");
      else if( c==KeyEvent.VK_DOWN || c==KeyEvent.VK_RIGHT ) sr.execute("+");
   }

   public void keyPressed(KeyEvent e) {
      int c = e.getKeyCode();
      if( c==KeyEvent.VK_DELETE || c==KeyEvent.VK_BACK_SPACE ) {
         aladin.view.delSelObjet();
      }
   }
   public void keyTyped(KeyEvent e) { }

   public void adjustmentValueChanged(AdjustmentEvent e) {
      repaint();
   }


   private WidgetControl voc=null;

   @Override
   public WidgetControl getWidgetControl() { return voc; }

   @Override
   public void createWidgetControl(int x, int y, int width, int height, float opacity,JComponent parent) {
      voc = new WidgetControl(this,x,y,width,height,opacity,parent);
      voc.setResizable(true);
   }

   @Override
   public void paintCollapsed(Graphics g) {
      Tool.drawVOTable(g, 4, 7);
   }

}
