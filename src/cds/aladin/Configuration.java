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

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingDeque;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import cds.aladin.prop.PropPanel;
import cds.moc.Moc;
import cds.moc.SMoc;
import cds.mocmulti.MultiMoc;
import cds.tools.Util;

/**
 * Gestion du fichier de configuration Aladin. Il va �tre enregistr� dans
 * ".aladin/Aladin.conf" La syntaxe est la suivante: # Commentaires �ventuels
 * cl�1 valeur 1 cl�2 valeur 2 Cette classe utilise un Vector de
 * ConfigurationItem (voir fin de la classe) (key,value). La cl� ne peut
 * contenir de blanc -automatiquement remplac� par des soulign�s. Pour une ligne
 * de commentaire, la cl� "#" est utilis�e, pour une ligne vide, la cl� " ",
 * mais sans valeur associ�e Le fichier n'est reg�n�r� que si un �l�ment a �t�
 * modifi�, ajout� ou supprim� pendant la session.
 *
 * REMARQUE : la gestion des acc�s concurrent au fichier en cas de multisession
 * par le m�me utilisateur n'est pas g�r� actuellement (le dernier est le
 * gagnant).
 *
 * AIDE : pour ajouter une nouvelle propri�t� : 1) cr�er une nouvelle variable
 * pour la cl� (ex: BROWSER = "UnixBrowser") 2) Dans le cas o� cette propri�t�
 * peut �tre modifi�e par les pr�f�rences de l'utilisateur a) mettre � jour le
 * formulaire: createPanel() b) mettre � jour l'action associ�e: apply()
 *
 * @author Pierre Fernique [CDS]
 * @version sept 2017 - Ajout couleur de grille
 * @version f�v 2007 - Ajout de Simbad pointer
 * @version nov 2005 - cr�ation
 */
public final class Configuration extends JFrame
implements Runnable, ActionListener, ItemListener, ChangeListener  {
   
   static final int DEF_MHEIGHT = 150;  // Hauteur par d�faut du panel des mesures
   static final int DEF_HWIDTH  = 250;  // Largeur par d�faut du panel de l'arbre des HiPS
   static final int DEF_ZWIDTH  = 220;  // Largeur par d�faut du panel du zoomView
   static final int DEF_ZHEIGHT = 150;  // Hauteur par d�faut du panel du zoomView

   static final String ASTRONOMER    = "astronomer";
   static final String UNDERGRADUATE = "undergraduate";
   static final String PREVIEW       = "preview";

   // Nom du fichier de configuration
   private static String   CONFIGNAME = "Aladin.conf";
   private static String   MOCFILTER = "MocFilter";

   // NOm du fichier des fonctions locales associ�es aux bookmarks
   private static String   CONFIGBKM = "Bookmarks.ajs";

   // Les mots cl�s possibles
   protected static String BROWSER    = "UnixBrowser";
   protected static String GLU        = "DefaultGluSite";
   protected static String DIR        = "DefaultDir";
   //   protected static String PIXEL      = "PixelMode";
   protected static String CM         = "DefaultCM";
   protected static String BKG        = "BackgroundCM";
   protected static String POSITION   = "PositionMode";
   protected static String LANG       = "Language";
   protected static String CSV        = "CSVchar";
   protected static String AUTODIST   = "AutoDistance";
   protected static String SIMBAD     = "SimbadPointer";
   protected static String VIZIERSED  = "VizierSEDPointer";
   protected static String FILTER     = "DedicatedFilter";
   protected static String TRANSOLD   = "FootprintTransparency";
   protected static String TRANS      = "Transparency";
   protected static String TRANSLEVEL = "TransparencyLevel";
   protected static String SURVEY     = "Survey";
   protected static String SERVER     = "Server";
   protected static String WINLOC     = "WindowLocation";
   protected static String RETICLE    = "Reticle";
   protected static String TOOLTIP    = "Tooltip";
   protected static String SCROLL     = "AutoScroll";
   protected static String MOD        = "Profile";
   protected static String HPXGRID    = "HealpixGrid";
   protected static String MHEIGHT    = "SplitMeasureHeight";
   protected static String ZHEIGHT    = "SplitZoomHeight";
   protected static String ZWIDTH     = "SplitZoomWidth";
   protected static String HWIDTH     = "SplitHiPSWidth";
   protected static String BOOKMARKS  = "Bookmarks";
   protected static String FRAME      = "Frame";
   protected static String FRAMEALLSKY= "FrameAllsky";
   protected static String PROJALLSKY = "ProjAllsky";
   protected static String VERSION    = "Version";
   protected static String OFFICIALVERSION= "OfficialVersion";
   protected static String CDSMESSAGE = "CDSMessage";
   protected static String CACHE      = "HpxCacheSize";
   protected static String MAXCACHE   = "HpxMaxCacheSize";
   protected static String LOG        = "Log";
   protected static String LOOKANDFEEL= "LookAndFeel";
   protected static String HELP       = "Wizard";
   protected static String SLEPOCH    = "SliderEpoch";
   protected static String SLSIZE     = "SliderSize";
   protected static String SLDENS     = "SliderDensity";
   protected static String SLCUBE     = "SliderCube";
   protected static String SLOPAC     = "SliderOpac";
   protected static String SLZOOM     = "SliderZoom";
   protected static String SEDWAVE    = "SEDWave";
   protected static String DIRFILER   = "DirFilter";
   protected static String LASTFILE   = "LastFile";
   protected static String LASTTARGET = "LastTarget";
   protected static String LASTTARGETNAME = "LastTargetName";
   protected static String LASTGLU    = "LastGlu";
   protected static String LASTRUN    = "LastRun";
   protected static String STOPHELP   = "StopHelp";
   protected static String LOOKANDFEELTHEME      = "LookAndFeelTheme";
   protected static String LOOKANDFEELSCALE      = "LookAndFeelScale";
   protected static String GRIDC      = "GridColor";
   protected static String GRIDCRA    = "GridColorRA";
   protected static String GRIDCDE    = "GridColorDE";
   protected static String GRIDF      = "GridColorFont";
   protected static String GRIDSTROKE = "GridStroke";
   protected static String INFOC      = "InfoColor";
   protected static String INFOCL     = "InfoColorLabel";
   protected static String INFOF      = "InfoFont";
   protected static String INFOB      = "InfoBorder";
   protected static String TAPSCHEMADISPLAY = "TapSchemaDisplay";
   protected static String FILTERHDU     = "FilterHDU";
   protected static String PLANET     = "Planet";
   
   static final private String [] UISCALESTR = {"Automatic","OS method","100%","110%","120%","130%","140%","150%","175%","200%","225%","250%"};
   static final private String [] STROKESTR  = {"Automatic","thin","medium","thick"};
   static final private float [] STROKE      = {0f,         0.4f,  1f,      2f};

   
   //   protected static String TAG        = "CenteredTag";
   //   protected static String WENSIZE    = "WenSize";
   
   // Liste des mots cl�s d�pr�ci�s (� virer)
   static final String [] DEPRECATED = { "MeasurementHeight" };

   static String NOTACTIVATED = "Not activated";
   static String ACTIVATED = "Activated";
   static String JAVA = "Java";
   static String OPSYS = "OS native";

   // Les labels des boutons
   static String TITLE,DEFDIR,DEFDIRH,LANGUE,LANGUEH,LANGCONTRIB,CSVCHAR,CSVCHARH,PIXB,/*PIXH,*/PIX8,PIXF,
   CMB,CMH,CMV,CMM,CMC,CMF,/*BKGB,BKGH,*/WEBB,WEBH,RELOAD,
   REGB,REGH,/*REGCL,REGMAN,*/APPLY,CLOSE,/*GLUTEST,GLUSTOP,*/BROWSE,FRAMEB,FRAMEALLSKYB,FRAMEH,OPALEVEL,
   PROJALLSKYB,PROJALLSKYH,FILTERB,FILTERH,FILTERN,FILTERY,SMBB,SMBH,TRANSB,TRANSH,
   IMGB,IMGH,IMGS,IMGC,MODE,MODEH,CACHES,CACHEH,UPHIDETAPSCHEMA,UPHIDETAPSCHEMAH,CLEARCACHE,LOGS,LOGH,HELPS,HELPH,FILTERHDUS,FILTERHDUH,PLANETS,PLANETH,
   SLIDERS,SLIDERH,SLIDEREPOCH,SLIDERDENSITY,SLIDERCUBE,SLIDERSIZE,SLIDEROPAC,SLIDERZOOM/*,TAGCENTER,TAGCENTERH*/,
   FILEDIALOG, FILEDIALOGHELP, FILEDIALOGJAVA, FILEDIALOGNATIVE,THEME,UISCALE,THEMEHELP,RESTART,
   GRID,GRIDH,GRIDFONT,GRIDCOLOR,GRIDRACOLOR,GRIDDECOLOR,GRIDTHICKNESS,INFO,INFOH,INFOFONT,INFOCOLOR,INFOLABELCOLOR,INFOFONTBORDER;


   static private String CSVITEM[] = { "tab","|",";",",","tab |","tab | ;" };
   static private String CSVITEMLONG[];

   // r�f�rence externe
   private Aladin          aladin;

   // Contient les propri�t�s (ConfigurationItem)
   private Vector          prop;

   // Les flags d'�tats
   private boolean         flagModif;            // true si la config est modifi�e pendant la session
   private boolean         first      = true;    // Pour savoir si le JPanel � d�j� �t� cr��
   private boolean         flagModifLang=true;   // true si on vient de modifier la langue (ou au d�marrage)
   private String          currentLang="En";     // Le suffixe de la langue courante
   protected LinkedBlockingDeque<String> lastFile;  // La liste des derniers fichiers charg�s
   protected LinkedHashMap<String,String>  filterExpr; // Liste des filtres pour l'arbre des d�couvertes -(name->filterRule)
   protected HashMap<String, Moc>   filterMoc;  // Liste des r�gions associ�es aux filtres

   // Les variables pour la gestion des champs de pr�f�rences
//   private JTextField       browser;              // Pour la saisie du browser de l'utilisateur
   private JTextField       dir;                  // Pour la saisie du r�pertoire par d�faut
   private JTextField       maxCache;             // Pour la saisie de la taille du cache
   private JLabel           cache;                // Pour indiquer la valeur du cache
   //   private JComboBox        pixelChoice;          // Pour la s�lection du mode Pixel par d�faut
   private JComboBox        frameChoice;          // Pour la s�lection du frame par d�faut
   private JComboBox        frameAllskyChoice;    // Pour la s�lection du frame par d�faut dans le cas des Allsky
   private JComboBox        projAllskyChoice;      // Pour la s�lection de la projection par d�faut pour les all-sky
   private JComboBox        videoChoice;          // Choix du mode vid�o
   private JComboBox        mapChoice;            // Choix de la color map
   private JComboBox        cutChoice;            // Choix de l'autocut
   private JComboBox        fctChoice;            // Choix de la fonction de transfert
   private JComboBox        gluChoice;            // Pour la s�lection du site GLU
   private JComboBox        lfChoice;             // Pour la s�lection du Look & Feel
   private JComboBox        themeChoice;          // Pour la s�lection du th�me du Look & Feel (dark or classic)
   private JComboBox        uiScaleChoice;        // Pour la s�lection du facteur d'�chelle de l'UI
   private JComboBox        langChoice;           // Pour la s�lection de la langue
   private JComboBox        modeChoice;           // Pour la s�lection du mode (astronomers | undergraduate)
   //   private JComboBox        smbChoice;            // Pour la s�lection du mode Simbad pointer
   private JComboBox        filterChoice;         // Pour l'activation du filtre par d�faut
   private JComboBox        transparencyChoice;   // Pour l'activation de la transparence des footprints
   private JComboBox        logChoice;            // Pour l'activation des logs
   private JComboBox        helpChoice;           // Pour l'activation de l'aide des d�butants
   private JComboBox        filterHDUChoice;        // Pour le chargement ou non de toutes les extensions FITS
   private JComboBox        planetChoice;         // Pour le chargement ou non des donn�es plan�taires
   //   private JComboBox        tagChoice;           // Pour l'activation du centrage des tags
   private JSlider          transparencyLevel;    // niveau de transparence pour footprints
   private JComboBox        csvChoice;            // Pour la s�lection du caract�re CSV
   private int              langItem;             // Pour savoir si langChoice a �t� modifi�
   private int              modeItem;             // Pour savoir si modeChoice a �t� modifi�
   private int 	            csvItem;              // Pour savoir si csvChoice a �t� modifi�
   private Vector           gluUrl;               // Les Urls correspondantes
   private JTextField       serverTxt;            // Le serveur d'images par d�faut
   private JTextField       surveyTxt;            // La couleur/survey par d�faut
   private int              lastGluChoice=-1;     // Dernier choix glu valid�
   private JButton          reload;               // Le bouton de reload du glu
   private JCheckBox        bxEpoch;              // Pour l'activation du slider de l'�poque
   private JCheckBox        bxSize;               // Pour l'activation du slider de la taille des sources
   private JCheckBox        bxDens;               // Pour l'activation du slider de la densit� des sources
   private JCheckBox        bxCube;               // Pour l'activation du slider de controle des cubes
   private JCheckBox        bxOpac;               // Pour l'activation du slider du controle de la transparence
   private JCheckBox        bxZoom;               // Pour l'activation du slider du controle du zoom
   
   private JComboBox gridFontCombo,strokeCombo;
   private CouleurBox gridColorBox,gridColorRABox,gridColorDEBox;

   private JComboBox infoFontCombo,infoFontBorderCombo;
   private CouleurBox infoColorBox,infoLabelColorBox;

   static private Langue lang[];                  // La liste des langues install�es
   private Vector remoteLang = null;              // Lal iste des langues connues mais non install�es
   private int previousPlanet=1;                  // Indice de l'activation du mode Planet
   private int previousTheme=0;                   // Indice du th�me au d�marrage
   private int previousUiScale=0;                   // Indice du facteur d'�chelle au d�marrage
   private JCheckBox hideTapSchema = null;			//Hide tapschema tables on tap clients or not.
   
   private Point initWinLocXY=new Point();          // Position initiale de la fen�tre
   private Dimension initWinLocWH=new Dimension();  // Dimension initiale de la fen�tre
   private int initFullWin=0;                       // 0-non, 1-fullWin, 2-FullScreen
//   private int initMesureHeight=0;                  // Hauteur de la fen�tre des mesures


   protected void createChaine() {
      TITLE = aladin.chaine.getString("UPTITLE");
      DEFDIR = aladin.chaine.getString("UPDEFDIR");
      DEFDIRH = aladin.chaine.getString("UPDEFDIRH");
      LANGUE = aladin.chaine.getString("UPLANGUE");
      LANGUEH = aladin.chaine.getString("UPLANGUEH");
      LANGCONTRIB = aladin.chaine.getString("UPLANGCONTRIB");
      MODE = aladin.chaine.getString("UPMODE");
      MODEH = aladin.chaine.getString("UPMODEH");
      CSVCHAR = aladin.chaine.getString("UPCSVCHAR");
      CSVCHARH = aladin.chaine.getString("UPCSVCHARH");
      PIXB = aladin.chaine.getString("UPPIXB");
      //      PIXH = aladin.chaine.getString("UPPIXH");
      PIX8 = "8 bit grey level";
      PIXF = "Full pixel";
      CMB = aladin.chaine.getString("UPCMB");
      CMH = aladin.chaine.getString("UPCMH");
      CMV = aladin.chaine.getString("UPCMV");
      CMM = aladin.chaine.getString("UPCMM");
      CMC = aladin.chaine.getString("UPCMC");
      CMF = aladin.chaine.getString("UPCMF");
      //      BKGB = aladin.chaine.getString("UPBKGB");
      //      BKGH = aladin.chaine.getString("UPBKGH");
      WEBB = aladin.chaine.getString("UPWEBB");
      WEBH = aladin.chaine.getString("UPWEBH");
      REGB = aladin.chaine.getString("UPREGB");
      REGH = aladin.chaine.getString("UPREGH");
      RELOAD = aladin.chaine.getString("PROPRELOAD");
      //      REGCL = aladin.chaine.getString("UPREGCL");
      //      REGMAN = aladin.chaine.getString("UPREGMAN");
      APPLY = aladin.chaine.getString("UPAPPLY");
      CLOSE = aladin.chaine.getString("UPCLOSE");
      //      GLUTEST = aladin.chaine.getString("UPTEST");
      //      GLUSTOP = aladin.chaine.getString("UPSTOP");
      BROWSE = aladin.chaine.getString("FILEBROWSE");
      FRAMEB = aladin.chaine.getString("UPFRAMEB");
      CACHES = aladin.chaine.getString("UPCACHE");
      CLEARCACHE = aladin.chaine.getString("NPRESET");
      CACHEH = aladin.chaine.getString("UPCACHEH");
      UPHIDETAPSCHEMA = aladin.chaine.getString("UPHIDETAPSCHEMA");
      UPHIDETAPSCHEMAH = aladin.chaine.getString("UPHIDETAPSCHEMAH");
      FRAMEALLSKYB = aladin.chaine.getString("UPFRAMEALLSKYB");
      FRAMEH = aladin.chaine.getString("UPFRAMEH");
      PROJALLSKYB = aladin.chaine.getString("UPPROJALLSKYB");
      PROJALLSKYH = aladin.chaine.getString("UPPROJALLSKYH");
      OPALEVEL = aladin.chaine.getString("PROPOPACITYLEVEL");
      SMBB = aladin.chaine.getString("UPSMBB");
      SMBH = aladin.chaine.getString("UPSMBH");
      FILTERB = aladin.chaine.getString("UPFILTERB");
      FILTERH = aladin.chaine.getString("UPFILTERH");
      TRANSB = aladin.chaine.getString("UPTRANSB");
      TRANSH = aladin.chaine.getString("UPTRANSH");
      IMGB = aladin.chaine.getString("UPIMGB");
      IMGH = aladin.chaine.getString("UPIMGH");
      IMGS = aladin.chaine.getString("UPIMGS");
      IMGC = aladin.chaine.getString("UPIMGC");
      LOGS = aladin.chaine.getString("UPLOG");
      LOGH = aladin.chaine.getString("UPLOGH");
      HELPS = aladin.chaine.getString("UPHELP");
      HELPH = aladin.chaine.getString("UPHELPH");
      FILTERHDUS = aladin.chaine.getString("UPFILTERHDU");
      FILTERHDUH = aladin.chaine.getString("UPFILTERHDUH");
      PLANETS = aladin.chaine.getString("UPPLANET");
      PLANETH = aladin.chaine.getString("UPPLANETH");
      SLIDERS = aladin.chaine.getString("UPSLIDERS");
      SLIDERH = aladin.chaine.getString("UPSLIDERH");
      SLIDEREPOCH = aladin.chaine.getString("SLIDEREPOCH");
      SLIDERDENSITY = aladin.chaine.getString("SLIDERDENSITY");
      SLIDERSIZE = aladin.chaine.getString("SLIDERSIZE");
      SLIDERCUBE = aladin.chaine.getString("SLIDERCUBE");
      SLIDEROPAC = aladin.chaine.getString("OPACITY");
      SLIDERZOOM = aladin.chaine.getString("ZOOM");
      FILEDIALOG = aladin.chaine.getString("FILEDIALOG");
      FILEDIALOGHELP = aladin.chaine.getString("FILEDIALOGHELP");
      FILEDIALOGJAVA = aladin.chaine.getString("FILEDIALOGJAVA");
      FILEDIALOGNATIVE = aladin.chaine.getString("FILEDIALOGNATIVE");
      THEME = aladin.chaine.getString("THEME");
      THEMEHELP = aladin.chaine.getString("THEMEHELP");
      UISCALE = aladin.chaine.getString("UISCALE");
      RESTART = aladin.chaine.getString("RESTART");
      GRID=aladin.chaine.getString("UPGRID");
      GRIDH=aladin.chaine.getString("UPGRIDH");
      GRIDFONT=aladin.chaine.getString("UPGRIDFONT");
      GRIDCOLOR=aladin.chaine.getString("UPGRIDCOLOR");
      GRIDRACOLOR=aladin.chaine.getString("UPGRIDRACOLOR");
      GRIDDECOLOR=aladin.chaine.getString("UPGRIDDECOLOR");
      GRIDTHICKNESS=aladin.chaine.getString("UPGRIDTHICKNESS");
      INFO=aladin.chaine.getString("UPINFO");
      INFOH=aladin.chaine.getString("UPINFOH");
      INFOFONT=aladin.chaine.getString("UPINFOFONT");
      INFOCOLOR=aladin.chaine.getString("UPINFOCOLOR");
      INFOLABELCOLOR=aladin.chaine.getString("UPINFOLABELCOLOR");
      INFOFONTBORDER=aladin.chaine.getString("UPINFOFONTBORDER");
      
      //      TAGCENTER = aladin.chaine.getString("UPTAGCENTER");
      //      TAGCENTERH = aladin.chaine.getString("UPTAGCENTERH");

      CSVITEMLONG = new String[] { "Tab","Pipe (|)","Semicolon (;)",
            "Comma (,)","Tab or Pipe (|)","Tab, Pipe (|) or Semicolon (;)" };
   }

   /** Cr�ation de la configuration */
   public Configuration(Aladin aladin) {
      super();
      this.aladin = aladin;
      Aladin.setIcon(this);
      enableEvents(AWTEvent.WINDOW_EVENT_MASK);
      Util.setCloseShortcut(this, false,aladin);
      prop = new Vector(10);
      filterMoc = new HashMap<>();
      filterExpr = new LinkedHashMap<>();
      setDirFilter("Color surveys", "dataproduct_subtype=color && moc_sky_fraction>0.2",null);
      setDirFilter("Large catalogs", "nb_rows>1000000",null);
      setDirFilter("Log missions", "ID=CDS/B*",null);
      flagModif = false;
   }

   /** Construction du panel des boutons de validation
    * @return Le panel contenant les boutons Apply/Close
    */
   protected JPanel getValidPanel() {
      JPanel p = new JPanel();
      p.setLayout( new FlowLayout(FlowLayout.CENTER));
      JButton b;
      p.add( b=new JButton(APPLY)); b.addActionListener(this);
      b.setFont(b.getFont().deriveFont(Font.BOLD));
      p.add( b=new JButton(CLOSE)); b.addActionListener(this);
      return p;
   }
   /**
    * Affichage du panel pour permettre � l'utilisateur de modifier sa
    * configuration
    */
   public void show() {
      if( first ) {
         createChaine();
         setTitle(TITLE);
         ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
         Aladin.makeAdd(getContentPane(), createPanel(), "Center");
         Aladin.makeAdd(getContentPane(), getValidPanel(), "South");
         pack();
         first = false;
         setLocation(Aladin.computeLocation(this));
      }
      updateWidgets();
      super.show();
   }

   /**
    * G�n�re le JComboBox des sites GLU possibles et positionne celui qui est
    * choisi par d�faut
    */
   private void createGluChoice() {
      gluChoice = new JComboBox();
      gluChoice.addItemListener(new ItemListener() {
         public void itemStateChanged(ItemEvent e) {
            if( reload!=null ) {
               int index = gluChoice.getSelectedIndex();
               reload.setEnabled( index==lastGluChoice );
            }
         }
      });
      gluUrl = new Vector();
      Enumeration e = aladin.glu.aladinDic.keys();
      int i = 0;
      while( e.hasMoreElements() ) {
         String key = (String) e.nextElement();
         if( !key.startsWith("AlaU.") ) continue;
         String v = (String) aladin.glu.aladinDic.get(key);
         int k = v.indexOf("//");
         if( i < 0 ) continue;
         int j = v.indexOf("/", k + 2);
         if( j < 0 ) continue;
         gluUrl.addElement(v);
         v = v.substring(k + 2, j);
         gluChoice.addItem(v);
         i++;
      }
   }


   /** Sp�cifie le mode d'affichage des pixels */
   //   private void setPixelMode(String mode) throws Exception {
   //      if( !aladin.pixel.setPixelMode(mode) ) {
   //         throw new Exception("Not available pixel mode ! ["+mode+"]");
   //      }
   //   }

   /** Sp�cifie le mode de synchronisation des commandes script */
   private void setSyncMode(String mode) throws Exception {
      mode = Util.toUpper(mode);
      aladin.command.setSyncMode( mode==null || mode.indexOf("OFF")>=0 || mode.indexOf("AUTO")>=0 ? Command.SYNCOFF : Command.SYNCOFF);
   }

   /** Sp�cifie le mode du simbad pointer. Si la pr�sence de la sous-chaine NO ou OFF
    * est d�tect� on d�sactive, sinon on active */
   private void setSimbadMode(String mode) throws Exception {
      mode = Util.toUpper(mode);
      boolean flag = mode!=null && mode.indexOf("NO")<0 && mode.indexOf("OFF")<0;
      aladin.calque.flagSimbad=flag;
      aladin.setButtonMode();
   }

   /** Sp�cifie le mode du filtre d�di�. Si la pr�sence de la sous-chaine NO ou OFF
    * est d�tect� on d�sactive, sinon on active */
   private void setFilterMode(String mode) throws Exception {
      aladin.FILTERDEFAULT=Util.toUpper(mode);
   }

   // Liste des valeurs possibles pour la commandes "setconf cm="
   static protected String CMPARAM[] = null;

   /** Sp�cifie le mode de mapping de pixel par d�faut (via command setconf)
    * La sauvegarde dans le fichier de conf ne sera pas faite pour autant */
   private void setCMMode(String mode) throws Exception { setMode1(mode,false); }

   /** Sp�cifie le mode de mapping du background par d�faut (via command setconf)
    * La sauvegarde dans le fichier de conf ne sera pas faite pour autant */
   private void setBkgMode(String mode) throws Exception { setMode1(mode,true); }

   // Voir setCMMod() et setBkgMode()
   private void setMode1(String mode,boolean flagBackground) throws Exception {
      if( CMPARAM==null ) {
         CMPARAM = new String[4+PlanImage.TRANSFERTFCT.length+FrameColorMap.CMA.length];
         CMPARAM[0] = "reverse"; CMPARAM[1]="noreverse";
         CMPARAM[2] = "autocut"; CMPARAM[3]="noautocut";
         System.arraycopy(PlanImage.TRANSFERTFCT,0,CMPARAM,4,PlanImage.TRANSFERTFCT.length);
         System.arraycopy(FrameColorMap.CMA,0,CMPARAM,4+PlanImage.TRANSFERTFCT.length,FrameColorMap.CMA.length);
      }
      Tok tok = new Tok(mode);
      StringBuffer cm = null;
      while( tok.hasMoreTokens() ) {
         String s = tok.nextToken();
         int i;
         if( cm==null ) cm = new StringBuffer();
         else cm.append(' ');
         if( (i=Util.indexInArrayOf(s,CMPARAM, true))<0 ) throw new Exception("Not available cm mode ! ["+s+"]");
         else cm.append(CMPARAM[i]);
      }
      if( flagBackground ) aladin.BKGDEFAULT=cm.toString();
      else aladin.CMDEFAULT=cm.toString();
      updateWidgets();
   }

   /** Positionnement du CSV */
   private void setCSV(String cs) {
      if( cs==null ) return;

      StringBuffer colsep = new StringBuffer();
      StringTokenizer st = new StringTokenizer(cs," ");
      while( st.hasMoreTokens() ) {
         String s = st.nextToken();
         if( s.equals("tab") ) colsep.append('\t');
         else colsep.append(s.charAt(0));
      }
      aladin.CSVCHAR=colsep.toString();
   }

   //   /** Positionnement du CENTEREDTAG - outil tag autocentr� */
   //   private void setCENTEREDTAG(String s) {
   //      if( s==null ) return;
   //      aladin.CENTEREDTAG = s.equals(ACTIVATED);
   //   }

   /** Positionnement de la taille du cache Healpix en KO */
   private void setMaxCache(String s) {
      if( s==null ) return;
      try {
         long maxCache = Long.parseLong(s);
         PlanBG.setMaxCacheSize(maxCache);
      } catch( Exception e ) {}
   }

   /** Transparence des footprints */
   private void setTransparency(String s, float level) {
      transparencyLevel.setToolTipText(OPALEVEL+" : "+(int)(level*100));
      boolean oTrans = Aladin.ENABLE_FOOTPRINT_OPACITY;
      boolean trans;
      if( s.equals(NOTACTIVATED) ) trans = false;
      else trans = true;

      if( trans!=oTrans ) {
         Aladin.ENABLE_FOOTPRINT_OPACITY = trans;
         aladin.calque.repaintAll();
      }

      float oLevel = Aladin.DEFAULT_FOOTPRINT_OPACITY_LEVEL;
      if( level!=oLevel ) {
         Aladin.DEFAULT_FOOTPRINT_OPACITY_LEVEL = level;
         aladin.calque.updateFootprintOpacity(oLevel, level);
         aladin.calque.repaintAll();
      }
   }

   /** Selection de l'item du JComboBox du Glu correspondant � s */
   protected void setSelectGluChoice(String s) {
      Enumeration e = gluUrl.elements();
      for( int i = 0; e.hasMoreElements(); i++ ) {
         if( s != null && s.startsWith((String) e.nextElement()) ) {
            gluChoice.setSelectedIndex(i);
            lastGluChoice=i;
            return;
         }
      }
   }

   private Thread gluTestThread = null;

   //   /** Lancement du thread de test des GLU */
   //   synchronized private void startGluTest() {
   //      if( gluTestThread != null ) return;
   //      gluTestThread = new Thread(this,"AladinGluTest");
   ////      gluTest.setText(GLUSTOP);
   //      gluTestThread.start();
   //   }

   //   /** Arr�t du thread du test des GLU */
   //   synchronized private void stopGluTest() {
   //      if( gluTestThread == null ) return;
   //      gluTestThread.interrupt();
   ////System.out.println("Je tente d'interrompre le thread "+gluTestThread);
   //      gluTestThread=null;
   ////      gluTest.setText(GLUTEST);
   //   }

   /** D�marrage dans un Thread s�par� du test sur le site GLU le plus proche */
   public void run() { launchGluTest(); }

   /** Lance le test des sites GLU et m�morise le plus rapide */
   private void launchGluTest() {
      Aladin.makeCursor(this, Aladin.WAITCURSOR);
      aladin.glu.testAlaSites(false, true);
      Aladin.makeCursor(this, Aladin.DEFAULTCURSOR);

      setSelectGluChoice(aladin.glu.NPHGLUALADIN);
   }

   /** Retourne vrai s'il s'agit d'une session Aladin non-applet Unix */
   private boolean isUnixStandalone() {
      if( Aladin.isApplet() ) return false;
      String syst = System.getProperty("os.name");
      if( syst == null || syst.startsWith("Windows") || syst.startsWith("Mac") ) return false;
      return true;
   }

   /** Retourne le mode video par d�faut pour le background */
   protected int getBkgVideo() {
      String s = aladin.BKGDEFAULT==null ? get(BKG) : aladin.BKGDEFAULT;
      if( s!=null && s.indexOf("noreverse")==-1 ) return PlanImage.VIDEO_INVERSE;
      return PlanImage.VIDEO_NORMAL;
//      if( s!=null && s.indexOf("noreverse")>=0 ) return PlanImage.VIDEO_NORMAL;
//      return Aladin.OUTREACH ? PlanImage.VIDEO_NORMAL : PlanImage.VIDEO_INVERSE;
   }

   /** Retourne la fonction de transfert par d�faut pour le background */
   protected int getBkgFct() {
      String s = aladin.BKGDEFAULT==null ? get(BKG) : aladin.BKGDEFAULT;
      return getFct1(s);
   }

   // Voir getCMFct() et getBkgFct()
   private int getFct1(String s) {
      if( s!=null ) {
         for( int i=0; i<PlanImage.TRANSFERTFCT.length; i++ ) {
            if( s.indexOf(PlanImage.TRANSFERTFCT[i])>=0 ) return i;
         }
      }
      return PlanImage.LINEAR;
   }

   /** Retourne la color map par d�faut pour le background */
   protected int getBkgMap() {
      String s = aladin.BKGDEFAULT==null ? get(BKG) : aladin.BKGDEFAULT;
      return getMap1(s);
   }

   // Voir getCMMap() et getBkgMap()
   private int getMap1(String s) {
      int i;
      if( s!=null ) {
         for( i=0; i<FrameColorMap.CMA.length; i++ ) {
            if( s.indexOf(FrameColorMap.CMA[i])>=0 ) return i;
         }
         if( CanvasColorMap.customCMName!=null ) {
            Enumeration e = CanvasColorMap.customCMName.elements();
            for( ;e.hasMoreElements(); i++ ) {
               String t = (String)e.nextElement();
               if( s.indexOf(t)>=0 ) return i;
            }
         }
      }
      return 0; // Aladin.OUTREACH ? 1 : 0;
   }

   /** Retourne le mode video par d�faut */
   protected int getCMVideo() {
      String s = aladin.CMDEFAULT==null ? get(CM) : aladin.CMDEFAULT;
//      if( s!=null && s.indexOf("noreverse")>=0 ) return PlanImage.VIDEO_NORMAL;
//      return Aladin.OUTREACH ? PlanImage.VIDEO_NORMAL : PlanImage.VIDEO_INVERSE;
      if( s!=null && s.indexOf("noreverse")==-1 ) return PlanImage.VIDEO_INVERSE;
      return PlanImage.VIDEO_NORMAL;
   }

   /** Retourne true si par d�fault l'autocut est activ� */
   protected boolean getCMCut() {
      String s = aladin.CMDEFAULT==null ? get(CM) : aladin.CMDEFAULT;
      if( s!=null && s.indexOf("noautocut")>=0 ) return false;
      return true;
   }

   /** Retourne la fonction de transfert par d�faut */
   protected int getCMFct() {
      String s = aladin.CMDEFAULT==null ? get(CM) : aladin.CMDEFAULT;
      return getFct1(s);
   }

   /** Retourne la color map par d�faut */
   protected int getCMMap() {
      String s = aladin.CMDEFAULT==null ? get(CM) : aladin.CMDEFAULT;
      return getMap1(s);
   }

   /** Retourne le serveur d'images par d�faut */
   protected String getServer() {
      String s = get(SERVER);
      s = s==null || s.equalsIgnoreCase("allsky") || s.trim().length()==0 ? "hips" : s;
      return s;
   }

   /** Retourne les bookmarks particuliers, null si aucun */
   public String getBookmarks() {
//      if( Aladin.OUTREACH ) return null;
      return get(BOOKMARKS);
   }

   /** Supprime les bookmarks particuliers */
   public void resetBookmarks() { remove(BOOKMARKS); }

   /** Retourne le survey d'images par d�faut */
   protected String getSurvey() {
      String s=get(SURVEY);
      s = s==null || s.length()==0 ? "P/DSS2/color" : s;
      return s;
   }

   /** Retourne la chaine d�crivant la derni�re version officielle */
   protected String getOfficialVersion() {
      return get(OFFICIALVERSION);
   }

   /** Retourne la chaine d�crivant la derni�re annonce du CDS 
    * Format:  tttt message... o� tttt est en temps Unix => http://www.unixtime.fr/ */
   protected String getCDSMessage() {
      return get(CDSMESSAGE);
   }

   /** Retourne la chaine d�crivant la derni�re version utilis�e */
   protected String getVersion() {
      return get(VERSION);
   }

   /** G�n�re la commande script de chargement d'une image sur le server d'image
    * par d�faut d�finit dans la configuration de l'utilisateur
    * exemple : "get ESO(DSS1)"
    */
   protected String getLoadImgCmd() {
      String survey = getSurvey();
      return getServer()+( survey==null ? "" : "("+survey+")");
   }

   /** Retourne 0 si le filtre d�di� doit �tre activ� par d�faut, sinon -1 */
   protected int getFilter() {
      String s = aladin.FILTERDEFAULT==null ? get(FILTER) : aladin.FILTERDEFAULT;
      if( s!=null && (Util.indexOfIgnoreCase(s,"NO")>=0
            || Util.indexOfIgnoreCase(s,"OFF")>=0) ) return -1;
      return 0;
   }

   private Vector<String> stopHelp = null;

   /** Affichage du help associ� � la cl�
    * @return true si le help a �t� affich�
    */
   protected boolean showHelpIfOk(String key) { 
//      return showHelpIfOk(null,key,500); }
//   protected boolean showHelpIfOk(final Component c,final String key, int delay) {
      if( stopHelp==null ) stopHelp = new Vector<>();
      if( stopHelp.contains(key) ) return false;
      
      aladin.calque.select.setMessageTip(key,aladin.chaine.getString(key));
      
//      Timer t = new Timer(delay, new ActionListener() {
//         public void actionPerformed(ActionEvent e) {
//            if( !aladin.confirmation(c==null?aladin:c,aladin.chaine.getString(key)
//                  +"\n \n"+aladin.chaine.getString("STOPHELP"))) stopHelp.add(key);
//        }
//      });
//      t.setRepeats(false);
//      t.start();
      return true;
   }
   
   /**
    * Retourne true s'il faut afficher le message d'aides (ou autre)
    * @param key
    * @return
    */
   protected boolean mustShowHelp(String key) {
      return stopHelp==null || !stopHelp.contains(key);
   }
   
   /** Acquittement d'un message d'aide ponctuelle afin qu'elle n'apparaisse plus une seconde fois */
   protected void showHelpDone(String key) {
      if( stopHelp==null ) stopHelp = new Vector<>();
      if( !stopHelp.contains(key) ) stopHelp.add(key);
      
   }

   // Initialisation de la liste des mots cl�s dont les HELPs ne doivent plus �tre affich�s
   private void initStopHelp(String s) {
      if( s==null ) return;
      stopHelp = new Vector<>();
      StringTokenizer st = new StringTokenizer(s);
      while( st.hasMoreTokens() ) stopHelp.add(st.nextToken());
   }

   // Maj de la liste des mots cl�s dont les HELPs ne doivent plus �tre affich�s
   private void majStopHelp() {
      if( stopHelp==null || stopHelp.size()==0 ) remove(STOPHELP);
      else {
         StringBuilder s = new StringBuilder();
         Enumeration<String> e = stopHelp.elements();
         while( e.hasMoreElements() ) {
            if( s.length()>0 ) s.append(' ');
            s.append(e.nextElement());
         }
         set(STOPHELP,s.toString());
      }
   }
   
   protected void stopWizard() {
      set(HELP,NOTACTIVATED);
   }

   /** retourne le suffixe de la langue courante. En premi�re approximation
    * on utilise simplement les deux premi�res lettres pr�c�d�es d'un point,
    * et rien pour l'anglais
    * @return suffixe de la langue (ex: .fr);
    */
   protected String getLang() {
      if( flagModifLang ) {
         String modeLang = "User";
         flagModifLang=false;
         String s = get(LANG);
         if( s==null ) {
            try {
               modeLang = "Default";
               s = System.getProperty("user.language");
               s = getLanguage(s);
            }catch( Exception e ) { s=null; }
         }
         currentLang = getLangSuffix(s);
         Aladin.trace(2,modeLang+" language ["+s+"] => assume ["+currentLang+"]");
      }
      return currentLang;
   }
   
   /** Retourne true si on veut un th�me sombre de l'interface graphique */
   protected boolean isDarkTheme() {
      String s = Aladin.THEME!=null ? Aladin.THEME : get(LOOKANDFEELTHEME);
      return s==null || s.equals("dark");
   }
   
   /** retourne le facteur d'�chelle s'il a �t� positionn�e (1.25 par exemple),
    * -1 si on laisse l'OS g�rer, et 0 si Aladin va essayer de le d�terminer automatiquement
    */
   protected float getUiScale() {
      String s = get(LOOKANDFEELSCALE);
      if( s==null || s.equals( UISCALESTR[0] ) ) return 0;   // Aladin va d�terminer automatiquement
      if( s.equals( UISCALESTR[1] ) ) return -1;   // On laisse l'OS g�rer

      // Bon, c'est alors un des items du genre "125%" on retourne d�s lors 1.25
       try {
         s = s.substring(0,s.length()-1);
         float f = Float.parseFloat(s);
         return f/100f;
      } catch( Exception e ) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); }
      return 0;
   }

   /** Retourne l'indice de la frame m�moris�e par l'utilisateur, ICRS par d�faut */
   protected int getFrame() {
//      if( Aladin.OUTREACH ) return Localisation.ICRS;
      try {
         String frame = get(FRAME);
         int i = Util.indexInArrayOf(frame, Localisation.REPERE);
         if( i>=0 ) return i;
      } catch( Exception e ) { }
      return Localisation.ICRS;
   }
   
   protected String getProj() { 
//      if( Aladin.OUTREACH ) return "Sinus";
      String s = get(PROJALLSKY);
      if( s==null ) return "Aitoff";
      else if( aladin.isCinema() ) return "Arc";
      return s;
   }

   /** Retourne le code Calib de la projection par d�faut pour les plans
    * en mode all-sky */
   protected int getProjAllsky() {
//      if( Aladin.OUTREACH ) return Calib.SIN;
      if( aladin.isCinema() ) return Calib.ARC;
      try {
         String proj = get(PROJALLSKY);
         int i= Projection.getAlaProjIndex(proj);
//         String calibProj = Projection.alaProjToType[i];
         String calibProj = Projection.getProjType(i);
         i=Calib.getProjType(calibProj);
         if( i>=0 ) return i;
      } catch( Exception e ) { }
      return Calib.AIT;
   }

   /** Positionne la projection par d�faut par script - non sauvegard�e */
   protected void setProjAllsky(String s) throws Exception {
      int i = Projection.getAlaProjIndex(s);
      if( i<0 ) throw new Exception("Unknown projection ["+s+"]");
      String s1 = Projection.getAlaProj(i);
      set(PROJALLSKY,s1);
      aladin.projSelector.setProjection(s1); // Pour garder la coh�rence du popup menu dans la v10
   }

   // EN ATTENDANT
   //   protected int getFrameAllsky() { return getFrame(); }

   private boolean setConfFrame=false; // true si l'utilisateur a modifi� par script

   /** Sp�cifie le mode d'affichage des positions (J2000, B1950...) */
   private void setPositionMode(String mode) throws Exception {
      if( mode.equalsIgnoreCase( "UNKNOWNFrame" ) ) return;
      if( !aladin.localisation.setPositionMode(mode) ) {
         throw new Exception("Not available position mode ! ["+mode+"]");
      }
      setConfFrame=true;
   }

   /** Retourne true si l'absisse du SED est en longueur d'onde plut�t qu'en fr�quence
    * - par d�faut false */
   protected boolean getSEDWave() {
//      if( Aladin.OUTREACH ) return false;
      String flag = get(SEDWAVE);
      if( flag==null ) return false;
      return flag.equalsIgnoreCase("On");
   }

   /** Retourne le flag de l'outil autodist - par d�faut inactif */
   protected boolean getAutoDist() {
//      if( Aladin.OUTREACH ) return false;
      String flag = get(AUTODIST);
      if( flag==null ) return false;
      return flag.equalsIgnoreCase("On");
   }

   /** Retourne le flag de Simbad Quick - par d�faut inactif */
   protected boolean getSimbadFlag() {
//      if( Aladin.OUTREACH ) return true;
      String flag = get(SIMBAD);
      if( flag==null ) return true;
      return !flag.equalsIgnoreCase("Off");
   }

   /** Retourne le flag de VizieRSED Quick - par d�faut inactif */
   protected boolean getVizierSEDFlag() {
//      if( Aladin.OUTREACH ) return false;
      String flag = get(VIZIERSED);
      if( flag==null ) return true;
      return !flag.equalsIgnoreCase("Off");
   }

   /** Retourne l'indice de la frame qui sera utilis� par d�faut pour le trac� des Allsky */
   protected int getFrameDrawing() {
//      if( Aladin.OUTREACH ) return 3;   // GAL
      //      if( !Aladin.PROTO ) return 0;   // Pour le moment le frame par d�faut pour les allsky n'est support� qu'en mode PROTO

      if( setConfFrame ) return getFrame();   // L'utilisateur a modifi� le cas par d�faut via une commande setconf frame=
      String frame = get(FRAMEALLSKY);
      try {
         int i = Util.indexInArrayOf(frame, Localisation.FRAME);
         if( i>=0 ) return i;
      } catch( Exception e ) { }
      return 0;   // Default => celui du rep�re c�leste
   }

   /** Retourne le code 2 lettres de la langue courante, m�me pour l'anglais */
   protected String getLanguage() {
      String s = getLang();
      if( s.length()==0 ) return "en";
      return s.substring(1);
   }
   
   /** Retourne la couleur de la grille */
   protected Color getGridColor() {
      try { return CouleurBox.getCouleur( get(GRIDC)); } catch( Exception e ) { }
      return Aladin.COLOR_GREEN;
   }

   /** Retourne la couleur des labels de la longitude de la grille */
   protected Color getGridColorRA() {
      try { return CouleurBox.getCouleur( get(GRIDCRA)); } catch( Exception e ) { }
      return Aladin.COLOR_GREEN_LIGHT;
   }

   /** Retourne la couleur des labels de la latitude de la grille */
   protected Color getGridColorDE() {
      try { return CouleurBox.getCouleur( get(GRIDCDE)); } catch( Exception e ) { }
      return Aladin.COLOR_GREEN_LIGHTER;
   }
   
   /** Retourne la taille de la fonte des labels de la grille */
   protected int getGridFontSize() {
      try { return Integer.parseInt( get(GRIDF)); } catch( Exception e ) { }
      return Aladin.SSIZE;
   }

   /** Retourne la taille de la fonte de r�f�rence des infos de la vue */
   protected int getInfoFontSize() {
      try { return Integer.parseInt( get(INFOF)); } catch( Exception e ) { }
      return Aladin.SSIZE;
   }
   
   /** Retourne l'�paisseur du trait des grilles, <=0 si automatique */
   protected float getGridThickness() {
      String s = get(GRIDSTROKE);
      if( s==null ) return 0f;
      int i = Util.indexInArrayOf(s, STROKESTR );
      if( i==-1 ) return 0;
      return STROKE[i];
   }
   
   /** Retourne true si on doit d�tourer les infos de la vue */
   protected boolean isInfoBorder() {
      String s = get(INFOB);
      return s==null || !s.equals("off");
   }
   
   /** Retourne la couleur des infos de la vue */
   protected Color getInfoColor() {
      try { return CouleurBox.getCouleur( get(INFOC)); } catch( Exception e ) { }
      return Color.cyan;
   }
   
   /** Retourne la couleur du label de la vue */
   protected Color getInfoLabelColor() {
      try { return CouleurBox.getCouleur( get(INFOCL)); } catch( Exception e ) { }
      return Color.yellow;
   }
   
   /** Positionnement du log de la conf via programmation */
   protected void setLog(boolean flag) {
      set(LOG, flag ? ACTIVATED : NOTACTIVATED );
   }
   
   /** Retourne true si le mode log est activ� */
   protected boolean isLog() {
      if( aladin.SETLOG ) return Default.LOG;
      String s = get(LOG);
      if( s==null ) return Default.LOG;
      return s.equals(ACTIVATED);
      
//      String s = get(LOG);
//      return s==null || s.equals(ACTIVATED);
   }

   /** Retourne true s'il faut un slider d'�poque */
   protected boolean isSliderEpoch() {
      String s = get(SLEPOCH);
      return s==null || !s.equals("off");
   }

   /** Retourne true s'il faut un slider de controle de la taille des sources */
   protected boolean isSliderSize() {
      String s = get(SLSIZE);
      return s==null || !s.equals("off");
   }

   /** Retourne true s'il faut un slider de controle de la densit� des sources (PlanBGCat) */
   protected boolean isSliderDensity() {
      String s = get(SLDENS);
      return s==null || !s.equals("off");
   }

   /** Retourne true s'il faut un slider de controle de cube */
   protected boolean isSliderCube() {
      String s = get(SLCUBE);
      return s!=null && s.equals("on");
   }

   /** Retourne true s'il faut un slider de controle de la transparence */
   protected boolean isSliderOpac() {
      String s = get(SLOPAC);
      return s==null || !s.equals("off");
   }

   /** Retourne true s'il faut un slider de zoom */
   protected boolean isSliderZoom() {
      String s = get(SLZOOM);
      return s==null || !s.equals("off");
   }

   /** Retourne true si le mode HELP pour les d�butants est activ� */
   protected boolean isHelp() {
      String s = get(HELP);
      return s==null || s.equals(ACTIVATED);
   }

   /** Retourne true si les HDU non int�ressantes doivent �tre filtr�es */
   protected boolean isFilterHDU() {
      String s = get(FILTERHDU);
      return s==null || s.startsWith(ACTIVATED);
   }

   /** Retourne true si les donn�es plan�taires doivent �tre pr�sentes */
   protected boolean isPlanet() {
      
      // Choix selon configuration utilisateur
      if( Aladin.PLANET==-1 ) {
         String s = get(PLANET);
         return s!=null && s.startsWith(ACTIVATED);
      }
      
      // Choix forc� par param�tre sur la ligne de commande (ou BETA)
      return Aladin.PLANET==1;
   }

   /** Retourne true si le mode Look & Feel est java (et non operating system) */
   public boolean isLookAndFeelJava() {
      String s = get(LOOKANDFEEL);
//      if( s==null && Aladin.macPlateform ) return false;
      if( s==null || s.equals(JAVA) ) return true;
      return false;
   }


//   /** Retourne le mode rep�r� dans le fichier de config */
//   protected boolean isOutReach() {
//      return false;    // A partir de la version 10
////      String s = get(MOD);
////      if( s!=null && s.charAt(0)=='u' ) return true;
////      return false;
//   }

   /** Retourne le mode rep�r� dans le fichier de config */
   protected boolean isBeginner() {
      String s = get(MOD);
      if( s!=null && s.charAt(0)=='p' ) return true;
      return false;
   }

   public boolean isTransparent() {
      //	   String s = get(TRANS);
      //	   if( s!=null && s.charAt(0)=='N' ) return false;
      return true;
   }

   public float getTransparencyLevel() {
      String s = get(TRANSLEVEL);
      if( s==null ) return 0.15f+0.000111f; // valeur par d�faut

      try {
         float f = Float.parseFloat(s);
         return f;
      }
      catch(NumberFormatException nfe) {}
      return 0.15f+0.000111f;
   }

   /** Ajoute au s�lecteur de langue la liste des langues distances en �vitant les
    * doublons
    */
   private void addRemoteLanguage() {
      Enumeration e = remoteLang.elements();
      while( e.hasMoreElements() ) {
         Langue lg = (Langue)e.nextElement();

         int i,n = langChoice.getItemCount();
         for( i=0; i<n && !((String)langChoice.getItemAt(i)).equals(lg.langue); i++);
         if( i==n ) langChoice.addItem(lg.langue);
      }
   }

   /**
    * Ajoute � remoteLang la langue d�crite par le nmo de fichier pass� en param�tre
    * qui doit suivre la syntaxe de l'exemple ci-dessous
    * ex : Aladin-SimplifiedChinese-4.024.string.utf
    */
   private void setRemoteLanguage(String t) {
      try {
         int t1 = t.indexOf('-');
         int t2 = t.indexOf('-',t1+1);
         int dot = t.indexOf(".string");

         StringBuffer name = new StringBuffer();
         for( int i=t1+1; i<t2; i++ ) {
            char ch = t.charAt(i);
            if( i>t1+1 && Character.isUpperCase(ch) ) name.append(' ');
            name.append(ch);
         }
         Langue lg = new Langue(name.toString());

         try { lg.version = Double.parseDouble(t.substring(t2+1,dot) ); }
         catch( Exception e) {}

         lg.file=t;

         remoteLang.addElement(lg);
      } catch( Exception e ) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); }
   }

   /**
    * Chargement de la description des langues distantes et mise � jour
    * de leur liste dans remoteLang
    */
   protected void loadRemoteLang() {
      remoteLang = new Vector();
      if( !Aladin.STANDALONE ) return;

      (new Thread("loadLang") {
         public void run() {
            try {
               Util.pause(1000);
               String s;
               Aladin.trace(3,"Checking language support...");
               InputStream in = aladin.cache.get(Aladin.LANGURL);
               BufferedReader dis = new BufferedReader(new InputStreamReader(in));
               while( (s=dis.readLine())!=null ) {
                  //System.out.println("Lang="+s);
                  setRemoteLanguage(s);
               }
               in.close();

               // Mise � jour �ventuelle
               s = get(LANG);
               Langue lg = getBestLangVersion(s);
               if( lg!=null ) {
                  Langue clg = findLangue(s);
                  if( clg!=null && clg.version!=lg.version ) {
                     Util.pause(10000);
                     installRemoteLanguage(s);
                  }
               }
            } catch( Exception e ) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); }
         }
      }).start();
   }

   /** Retourne l'objet Langue correspondant � la chaine */
   private Langue findLangue(String s) {
      for( int i=0; i<lang.length; i++ ) {
         if( lang[i].isLangue(s) ) return lang[i];
      }
      return null;
   }

   /** Retourne le code de la langue pr�c�d� d'un "." ou "" si inconnu d'Aladin */
   private String getLangSuffix(String s) {

      if( lang==null ) return "";
      if( s==null ) return "";
      for( int i=0; i<lang.length; i++ ) {
         if( s.equals(lang[i].langue) ) return ( lang[i].code.length()>0 ? ".":"") + lang[i].code;
      }
      return "";
   }

   /** Retourne la langue correspondant au code du pays ou null si inconnu d'Aladin */
   protected String getLanguage(String s) {
      for( int i=0; i<lang.length; i++ ) {
         if( lang[i].isLangue(s) ) return lang[i].langue;
      }
      return null;
   }

   /** Retourne l'auteur de la traduction correspondant au code du pays
    * ou null si inconnu d'Aladin */
   protected String getLanguageAuthor(String s) {
      for( int i=0; i<lang.length; i++ ) {
         if( lang[i].isLangue(s) ) return lang[i].auteur;
      }
      return null;
   }

   /** Retourne la taille du cache Healpix si on la connait, sinon -1 */
   protected long getHpxCacheSize() {
      String s = get(CACHE);
      if( s==null ) return -1;
      long size;
      try {  size = Long.parseLong(s); }
      catch( Exception e) { size=-1; }
      return size;
   }

   /** Retourne true si la fen�tre d'Aladin n'a ni boug�, ni �t� redimensionn�e */
   private boolean sameWinParam() {
      if( aladin.isApplet() ) return true;  // pas de gestion de positionnement en mode applet
      Dimension d = aladin.f.getSize();
      Point p = aladin.f.getLocation();
      return initWinLocXY.equals(p) && initWinLocWH.equals(d);
   }

   /** Retourne la position et la taille de la fen�tre Aladin. M�morise
    * cette position pour v�rifier qu'� la fin de la session on a boug� ou non */
   protected Rectangle getWinLocation() {
      try {
         String s = get(WINLOC);
         StringTokenizer st = new StringTokenizer(s);
         Rectangle r = new Rectangle(
               Integer.parseInt(st.nextToken()),
               Integer.parseInt(st.nextToken()),
               Integer.parseInt(st.nextToken()),
               Integer.parseInt(st.nextToken())
               );
         
         int fullWin=0;
         if( st.hasMoreTokens() ) {
            s = st.nextToken();
            fullWin = s.equals("-") ? 1 : s.equals("--") ? 2 : 0;
         }
         setInitWinLoc(r.x,r.y,r.width,r.height,fullWin);
         return r;
      } catch( Exception e ) { }
      return null;
   }
   
   /** Retourn 0-pas de FullWin, 1-FullWin, 2-Full Screen */
   protected int getWinFull() {
      getWinLocation();
      return initFullWin;
   }

   protected int getSplitMesureHeight() {
      try {
         int m=Integer.parseInt( get(MHEIGHT));
         if( m<20 ) throw new Exception();
         return m;
      } catch( Exception e ) {}
      return DEF_MHEIGHT; 
   }

   protected int getSplitHiPSWidth() {
      try { return Integer.parseInt( get(HWIDTH)); } catch( Exception e ) {}
      return DEF_HWIDTH; 
   }

   protected int getSplitZoomWidth() {
      try { return Integer.parseInt( get(ZWIDTH)); } catch( Exception e ) {}
      return DEF_ZWIDTH; 
   }

   protected int getSplitZoomHeight() {
      try { return Integer.parseInt( get(ZHEIGHT)); } catch( Exception e ) {}
      return DEF_ZHEIGHT; 
   }

   /** M�morisation de la position et de la taille de la fen�tre initiale d'Aladin en vue
    * de comparaison (voir save())*/
   protected void setInitWinLoc(int x,int y,int width, int height,int fullWin) {
      initWinLocXY.x=x;
      initWinLocXY.y=y;
      initWinLocWH.width=width;
      initWinLocWH.height=height;
      initFullWin=fullWin;
   }

   /** Etend le tableau des langues si n�cessaire de n cases et
    * retourne l'indice de la premi�re case inutilis�e
    */
   private int extendLang(int n) {
      int i;
      if( lang!=null ) {
         Langue a[] = lang;
         lang = new Langue[a.length+n];
         System.arraycopy(a,0,lang,0,a.length);
         i=a.length;
      } else {
         lang = new Langue[n];
         i=0;
      }
      return i;
   }

   /** Construit ou met � jour les tableaux de chaines contenant les langues
    * et les codes associ�s connues par Aladin (voir lang[] et langCode[]
    * Utilise le mot cl� LANGUAGE dans Aladin0.string
    * ex: LANGUAGE  English, Fran�ais (fr), Spanish (sp) 5.023 P.Gonzales
    */
   protected void setLanguage(String t) {
      StringTokenizer st = new StringTokenizer(t,",");
      int i,j,k,l;

      i=extendLang(st.countTokens());

      for( ; i<lang.length; i++ ) {
         String s = st.nextToken();
         int n=s.length();
         lang[i] = new Langue(s);

         // Recherche du code de la langue
         j = s.indexOf('(');
         k=-1;
         if( j>0 ) {
            k=s.indexOf(')',j);
            if( k>0 ) {
               lang[i].langue = s.substring(0,j).trim();
               lang[i].code = s.substring(j+1,k);
            }
         }

         // Recherche d'un num�ro de version d'Aladin associ�
         // (premier nombre apr�s la parenth�se qui contient le code de la langue)
         try {
            if( k>0 ) {
               j=k;
               for( ; k<n && !Character.isDigit(s.charAt(k)); k++ );
               if( k<n ) {
                  l = s.indexOf(' ',k);
                  if( l==-1 ) l=n;
                  try { lang[i].version = Double.parseDouble(s.substring(k,l)); }
                  catch( Exception e) {}
               }

               // Recherche de l'auteur de la traduction
               // (Premi�re chaine qui n'est pas un nombre qui suit la parenth�se du code
               // de la langue)
               for( l=j+1; l<n && !Character.isLetter(s.charAt(l)); l++ );
               if( l<n ) {
                  if( k>l ) lang[i].auteur=s.substring(l,k).trim();
                  else lang[i].auteur=s.substring(l).trim();
               }
            }
         }catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }

         Aladin.trace(2,"Supported language ["+lang[i].langue+"] ("+lang[i].code+") "
               +(lang[i].version>0?lang[i].version+"":"")+" "+lang[i].auteur);
      }
   }

   protected JPanel createPanel() {
//      if( Aladin.OUTREACH ) return createPanel1();
      JPanel p = new JPanel( new BorderLayout());
      JScrollPane sc = new JScrollPane(createPanel1());
      p.add(sc,BorderLayout.CENTER);
      //      p.setPreferredSize(new Dimension(580,600));
      return p;
   }

   /** Construction du panel de la configuration utilisateur */
   private JPanel createPanel1() {
      GridBagConstraints c = new GridBagConstraints();
      GridBagLayout g = new GridBagLayout();
      c.fill = GridBagConstraints.BOTH;
      c.insets = new Insets(2,2,2,2);
      JLabel l;
      JButton b;
      JComboBox x;
      JPanel panel;

      JPanel p = new JPanel();
      p.setLayout(g);

      // Le langage de l'interface
      panel = new JPanel(new BorderLayout(5,5));
      langChoice = new JComboBox();
      langChoice.addItem("-- default --");
      for( int i=0; i<lang.length; i++ ) langChoice.addItem(lang[i].langue);
      (l = new JLabel(LANGUE)).setFont(l.getFont().deriveFont(Font.BOLD));
      panel.add(langChoice,BorderLayout.CENTER);
//      if( !Aladin.OUTREACH ) {
         b=new JButton(LANGCONTRIB); b.addActionListener(this);
         b.setMargin( new Insets(2,4,2,4));
         panel.add(b,BorderLayout.EAST);
//      }
      PropPanel.addCouple(this, p, l, LANGUEH, panel, g, c, GridBagConstraints.EAST);

      addRemoteLanguage();

      // Le mode de l'interface (uniquement si non modifi� par param�tre
      // sur la ligne de commande
      modeChoice = new JComboBox();
      modeChoice.addItem(ASTRONOMER);
      modeChoice.addItem(UNDERGRADUATE);
      if( aladin.PROTO ) modeChoice.addItem(PREVIEW);
      (l = new JLabel(MODE)).setFont(l.getFont().deriveFont(Font.BOLD));
//         PropPanel.addCouple(this, p, l, MODEH, modeChoice, g, c, GridBagConstraints.EAST);

      // Wizard ?
      (l = new JLabel(HELPS)).setFont(l.getFont().deriveFont(Font.BOLD));
      helpChoice = new JComboBox();
      helpChoice.addItem(ACTIVATED);
      helpChoice.addItem(NOTACTIVATED);
      PropPanel.addCouple(this, p, l, HELPH, helpChoice, g, c, GridBagConstraints.EAST);

      // Le th�me du Look&Feel
      themeChoice = new JComboBox();
      themeChoice.addItem("dark");
      themeChoice.addItem("classic");
      themeChoice.addActionListener(this);
      uiScaleChoice = new JComboBox( UISCALESTR );
      uiScaleChoice.addActionListener(this);
      l = new JLabel(" - "+UISCALE);
      panel = new JPanel( new FlowLayout(FlowLayout.LEFT,5,0));
      panel.add(themeChoice);
      panel.add(l);
      panel.add(uiScaleChoice);
      (l = new JLabel(THEME)).setFont(l.getFont().deriveFont(Font.BOLD));
      PropPanel.addCouple(this, p, l, THEMEHELP, panel, g, c, GridBagConstraints.EAST);

      // Le Look&Feel des FileDialog
      (l = new JLabel(FILEDIALOG)).setFont(l.getFont().deriveFont(Font.BOLD));
      lfChoice = new JComboBox();
      lfChoice.addItem(FILEDIALOGJAVA);
      lfChoice.addItem(FILEDIALOGNATIVE);
      lfChoice.addActionListener(this);
      PropPanel.addCouple(this, p, l, FILEDIALOGHELP, lfChoice, g, c, GridBagConstraints.EAST);

      (l = new JLabel(SLIDERS)).setFont(l.getFont().deriveFont(Font.BOLD));
      JPanel sliderPanel = new JPanel( new GridLayout(1,0));
      sliderPanel.add( bxEpoch = new JCheckBox(SLIDEREPOCH));
      sliderPanel.add( bxSize  = new JCheckBox(SLIDERSIZE));
      sliderPanel.add( bxDens  = new JCheckBox(SLIDERDENSITY));
      sliderPanel.add( bxCube  = new JCheckBox(SLIDERCUBE));
      sliderPanel.add( bxOpac  = new JCheckBox(SLIDEROPAC));
      sliderPanel.add( bxZoom  = new JCheckBox(SLIDERZOOM));
      PropPanel.addCouple(this, p, l, SLIDERH, sliderPanel, g, c, GridBagConstraints.EAST);

      // Le R�pertoire par d�faut
      dir = new JTextField(30);
      b=new JButton(BROWSE); b.addActionListener(this);
      b.setMargin( new Insets(2,4,2,4));
      (l = new JLabel(DEFDIR)).setFont(l.getFont().deriveFont(Font.BOLD));
      panel = new JPanel(new BorderLayout(5,5));
      panel.add(dir,BorderLayout.CENTER);
      panel.add(b,BorderLayout.EAST);
      PropPanel.addCouple(this, p, l,DEFDIRH, panel, g, c, GridBagConstraints.EAST);

      // Le frame
      frameChoice = aladin.localisation.createSimpleChoice();
      frameAllskyChoice = aladin.localisation.createFrameCombo();
      (l = new JLabel(FRAMEB)).setFont(l.getFont().deriveFont(Font.BOLD));
      panel = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
      panel.add(frameChoice);
      panel.add(new JLabel(" - "+FRAMEALLSKYB));
      panel.add(frameAllskyChoice);
      PropPanel.addCouple(this, p, l, FRAMEH, panel, g, c, GridBagConstraints.EAST);

      // La projection par d�faut pour les allsky
      projAllskyChoice = new JComboBox( Projection.getAlaProj() );
      projAllskyChoice.setMaximumRowCount(10);  // Marche po !
      (l = new JLabel(PROJALLSKYB)).setFont(l.getFont().deriveFont(Font.BOLD));
      panel = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
      panel.add(projAllskyChoice);
      PropPanel.addCouple(this, p, l, PROJALLSKYH, panel, g, c, GridBagConstraints.EAST);

      // Le mode pixel
      //      pixelChoice = new JComboBox();
      //      pixelChoice.addItem(PIXF);
      //      pixelChoice.addItem(PIX8);

      // Le pixel mapping
      videoChoice = x=new JComboBox(); x.addItem("reverse"); x.addItem("noreverse");
      mapChoice = x = FrameColorMap.createComboCM();
      cutChoice = x=new JComboBox();   x.addItem("autocut"); x.addItem("noautocut");
      fctChoice = x=new JComboBox();   for( int i=0; i<PlanImage.TRANSFERTFCT.length; i++ ) x.addItem(PlanImage.TRANSFERTFCT[i]);
      panel = new JPanel(new GridLayout(2,2,4,4));
      panel.add(new JLabel("- "+CMV,JLabel.LEFT)); panel.add(videoChoice);
      panel.add(new JLabel("  - "+CMM,JLabel.LEFT)); panel.add(mapChoice);
      panel.add(new JLabel("- "+CMC,JLabel.LEFT)); panel.add(cutChoice);
      panel.add(new JLabel("  - "+CMF,JLabel.LEFT)); panel.add(fctChoice);
      (l = new JLabel(CMB)).setFont(l.getFont().deriveFont(Font.BOLD));
      PropPanel.addCouple(this, p, l, CMH, panel, g, c, GridBagConstraints.EAST);
      
      //      csvChoice = new JComboBox();
      //      for( int i=0; i<CSVITEM.length; i++ ) csvChoice.addItem(CSVITEMLONG[i]);
      //      (l = new JLabel(CSVCHAR)).setFont(l.getFont().deriveFont(Font.BOLD));
      //      if( !aladin.OUTREACH ) {
      //         Properties.addCouple(this, p, l, CSVCHARH, csvChoice, g, c, GridBagConstraints.EAST);
      //      }

      // Le filtre par d�faut
      filterChoice = new JComboBox();
      filterChoice.addItem(NOTACTIVATED);
      filterChoice.addItem(ACTIVATED);
      (l = new JLabel(FILTERB)).setFont(l.getFont().deriveFont(Font.BOLD));
      PropPanel.addCouple(this, p, l, FILTERH, filterChoice, g, c, GridBagConstraints.EAST);

     // Transparence des footprints
//      transparencyChoice = new JComboBox();
//      transparencyChoice.addItem(NOTACTIVATED);
//      transparencyChoice.addItem(ACTIVATED);
      JPanel transparencyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
      // TODO : cr�er une classe TransparencySlider (utilis� � plusieurs endroits)
//      transparencyPanel.add(transparencyChoice);
      transparencyLevel = new JSlider(0, 100);
      transparencyLevel.setValue((int)(100*Aladin.DEFAULT_FOOTPRINT_OPACITY_LEVEL));
      transparencyLevel.setMajorTickSpacing(20);
      transparencyLevel.setPaintLabels(true);
      transparencyLevel.setPaintTicks(true);
      transparencyLevel.setPaintTrack(true);
      transparencyLevel.setPreferredSize(new Dimension(230,transparencyLevel.getPreferredSize().height));
      transparencyLevel.setToolTipText(OPALEVEL+" : "+transparencyLevel.getValue());
      transparencyLevel.addChangeListener(this);
      transparencyPanel.add(transparencyLevel);

      //      if( !aladin.OUTREACH ) {
               (l = new JLabel(TRANSB)).setFont(l.getFont().deriveFont(Font.BOLD));
               PropPanel.addCouple(this, p, l, TRANSH, transparencyPanel, g, c, GridBagConstraints.EAST);

      //         // Les centrage des tags
      //         (l = new JLabel(TAGCENTER)).setFont(l.getFont().deriveFont(Font.BOLD));
      //         tagChoice = new JComboBox();
      //         tagChoice.addItem(ACTIVATED);
      //         tagChoice.addItem(NOTACTIVATED);
      //         Properties.addCouple(this, p, l, TAGCENTERH, tagChoice, g, c, GridBagConstraints.EAST);
      //      }

      // Le Web Browser
//      if( isUnixStandalone()  ) {
//         PropPanel.addFilet(p, g, c);
//         browser = new JTextField(30);
//         (l = new JLabel(WEBB)).setFont(l.getFont().deriveFont(Font.BOLD));
//         PropPanel.addCouple(this, p, l, WEBH, browser, g, c, GridBagConstraints.EAST);
//      }

      // Le survey par d�faut
      serverTxt = new JTextField(10);
      surveyTxt = new JTextField(10);
      JPanel p1 = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
      p1.add(new JLabel(IMGS,JLabel.LEFT)); p1.add(serverTxt);
      p1.add(new JLabel(IMGC,JLabel.LEFT)); p1.add(surveyTxt);
      (l = new JLabel(IMGB)).setFont(l.getFont().deriveFont(Font.BOLD));
      PropPanel.addCouple(this, p, l, IMGH, p1, g, c,GridBagConstraints.EAST);

      // Le GLU
      (l = new JLabel(REGB)).setFont(l.getFont().deriveFont(Font.BOLD));
      reload = b = new JButton(RELOAD);
      createGluChoice();

      // Le glu
      panel = new JPanel(new BorderLayout(5,5));
      panel.add(gluChoice,BorderLayout.WEST);
      b.setMargin( new Insets(3,4,2,4));
      b.addActionListener(this);
      panel.add(b,BorderLayout.EAST);
      PropPanel.addCouple(this, p, l, REGH, panel, g, c, GridBagConstraints.EAST);

      // Les param�tres de la grille
      CouleurBox y;
//      panel = new JPanel(new GridLayout(2,4,4,4));
      panel = new JPanel(new GridLayout(3,4,4,4));
      gridFontCombo = new JComboBox( new String[]{"6","7","8","9","10","11","12","13","14","16"} );
      gridFontCombo.setPrototypeDisplayValue(new Integer(100000000));
      gridFontCombo.setSelectedItem( getGridFontSize()+"" );
      panel.add(new JLabel("- "+GRIDFONT,JLabel.LEFT)); panel.add( gridFontCombo );
      gridColorBox = y = new CouleurBox( Aladin.COLOR_GREEN, aladin.view.gridColor );
      panel.add(new JLabel("  - "+GRIDCOLOR,JLabel.LEFT)); panel.add(y);
      
      gridColorRABox = y = new CouleurBox( Aladin.COLOR_GREEN_LIGHT, aladin.view.gridColorRA );
      panel.add(new JLabel("- "+GRIDRACOLOR,JLabel.LEFT)); panel.add(y);
      gridColorDEBox = y = new CouleurBox( Aladin.COLOR_GREEN_LIGHTER, aladin.view.gridColorDEC );
      panel.add(new JLabel("  - "+GRIDDECOLOR,JLabel.LEFT)); panel.add(y);
      (l = new JLabel(GRID)).setFont(l.getFont().deriveFont(Font.BOLD));
      
      strokeCombo = new JComboBox( STROKESTR);
      panel.add(new JLabel("- "+GRIDTHICKNESS)); panel.add(strokeCombo);
      
      PropPanel.addCouple(this, p, l, GRIDH, panel, g, c, GridBagConstraints.EAST);

      // Les param�tres des infos
      panel = new JPanel(new GridLayout(2,4,4,4));
      infoFontCombo = new JComboBox( new String[]{"8","9","10","11","12","13","14","16","18","20"} );
      infoFontCombo.setPrototypeDisplayValue(new Integer(100000000));
      infoFontCombo.setSelectedItem( getInfoFontSize()+"" );
      panel.add(new JLabel("- "+INFOFONT,JLabel.LEFT)); panel.add( infoFontCombo );
      infoFontBorderCombo = new JComboBox( new String[]{"on","off"} );
      infoFontBorderCombo.setSelectedIndex( isInfoBorder()?0:1 );
      panel.add(new JLabel("- "+INFOFONTBORDER,JLabel.LEFT)); panel.add( infoFontBorderCombo );
      infoColorBox = y = new CouleurBox( Color.cyan, getInfoColor() );
      panel.add(new JLabel("- "+INFOCOLOR,JLabel.LEFT)); panel.add(y);
      infoLabelColorBox = y = new CouleurBox( Color.yellow, getInfoLabelColor() );
      panel.add(new JLabel("  - "+INFOLABELCOLOR,JLabel.LEFT)); panel.add(y);
      (l = new JLabel(INFO)).setFont(l.getFont().deriveFont(Font.BOLD));
      PropPanel.addCouple(this, p, l, INFOH, panel, g, c, GridBagConstraints.EAST);

      // Les logs
      if( !Aladin.SETLOG ) {
         (l = new JLabel(LOGS)).setFont(l.getFont().deriveFont(Font.BOLD));
         logChoice = new JComboBox();
         logChoice.addItem(ACTIVATED);
         logChoice.addItem(NOTACTIVATED);
         PropPanel.addCouple(this, p, l, LOGH, logChoice, g, c, GridBagConstraints.EAST);
      }

      // Le cache
      (l = new JLabel(CACHES)).setFont(l.getFont().deriveFont(Font.BOLD));
      panel = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
      panel.add( cache=new JLabel("???? / "));
      panel.add( maxCache = new JTextField(6));
      panel.add( new JLabel("MB"));
      b=new JButton(CLEARCACHE); b.addActionListener(this);
      b.setMargin( new Insets(2,4,2,4));
      panel.add( b );
      PropPanel.addCouple(this, p, l, CACHEH, panel, g, c, GridBagConstraints.EAST);
         
      (l = new JLabel(FILTERHDUS)).setFont(l.getFont().deriveFont(Font.BOLD));
      filterHDUChoice = new JComboBox();
      filterHDUChoice.addItem(ACTIVATED);
      filterHDUChoice.addItem(NOTACTIVATED);
      PropPanel.addCouple(this, p, l, FILTERHDUH, filterHDUChoice, g, c, GridBagConstraints.EAST);
      
      if( Aladin.PLANET==-1 ) {
         (l = new JLabel(PLANETS)).setFont(l.getFont().deriveFont(Font.BOLD));
         planetChoice = new JComboBox();
         planetChoice.addItem(ACTIVATED);
         planetChoice.addItem(NOTACTIVATED);
         
         planetChoice.addItemListener(new ItemListener(){
            public void itemStateChanged(ItemEvent event) { infoPlanet(); }
          });
         PropPanel.addCouple(this, p, l, PLANETH, planetChoice, g, c, GridBagConstraints.EAST);
      }
      
      if( Aladin.BETA || Aladin.PROTO ) {
         //tap: display of schema tables
         (l = new JLabel(UPHIDETAPSCHEMA)).setFont(l.getFont().deriveFont(Font.BOLD));
         hideTapSchema = new JCheckBox();
         hideTapSchema.setSelected(hideTapSchema());
         PropPanel.addCouple(this, p, l, UPHIDETAPSCHEMAH, hideTapSchema, g, c, GridBagConstraints.EAST);
      }
     
      return p;
   }
   
   // Affichage d'un baratin pour pr�venir des contraintes sur les affichages des donn�es plan�taires
   private void infoPlanet() {
      if( previousPlanet==1 && planetChoice.getSelectedIndex()==0 ) aladin.info(this,PLANETH);
   }

   // Nettoyage du cache HPX et du cache GLU
   private void clearCache() {
      aladin.makeCursor(this,Aladin.WAITCURSOR);
      aladin.cache.clear();
      PlanBG.clearCache();
      set(CACHE,"0");
      cache.setText("0 / ");
      aladin.makeCursor(this,Aladin.DEFAULTCURSOR);
   }

   /** Positionnement des valeurs courantes */
   private void updateWidgets() {
      String s;

      // Peut �tre n'ai-je pas encore cr�� le panneau de la config. ?
      if( dir==null ) return;

      s = get(DIR);
      if( s == null ) s = "";
      dir.setText(s);

      s = get(LANG);
      if( s == null ) langChoice.setSelectedIndex(0);
      else langChoice.setSelectedItem(s);
      langItem = langChoice.getSelectedIndex();

      //      if( tagChoice!=null ) {
      //         s = get(TAG);
      //         if( s==null ) tagChoice.setSelectedIndex(0);
      //         else tagChoice.setSelectedItem(s);
      //      }

      s = get(MOD);
      if( s == null ) modeChoice.setSelectedIndex(0);
      else modeChoice.setSelectedItem(s);
      modeItem = modeChoice.getSelectedIndex();

      s = get(LOOKANDFEEL);
      if( s==null && Aladin.macPlateform )  lfChoice.setSelectedIndex(1);
      if( s==null || s.equals(JAVA) ) lfChoice.setSelectedIndex(0);
      else lfChoice.setSelectedIndex(1);

      s = get(LOOKANDFEELTHEME);
      if( s!=null ) themeChoice.setSelectedItem("classic");
      else themeChoice.setSelectedIndex(0);

      s = get(LOOKANDFEELSCALE);
      int i = Util.indexInArrayOf(s, UISCALESTR);
      if( i<0 ) i=0;
      uiScaleChoice.setSelectedIndex(i);

      s = get(FRAME);
      if( s == null ) frameChoice.setSelectedItem("ICRS");
      else frameChoice.setSelectedItem(s);

      s = get(PROJALLSKY);
      if( s == null ) projAllskyChoice.setSelectedItem("Aitoff");
      else projAllskyChoice.setSelectedItem(s);
      
      s = get(GRIDSTROKE);
      if( s==null )  strokeCombo.setSelectedIndex(0);
      else strokeCombo.setSelectedItem( s );

      s = get(FRAMEALLSKY);
      if( s == null ) frameAllskyChoice.setSelectedItem("GAL");
      else frameAllskyChoice.setSelectedItem(s);

      fctChoice.setSelectedIndex(PlanImage.LINEAR);
      s = aladin.CMDEFAULT!=null ? aladin.CMDEFAULT : get(CM);
      JComboBox c;
      if( s!=null ) {
         Tok tok = new Tok(s);
         while( tok.hasMoreTokens() ) {
            s = tok.nextToken();
            suite: for( i=0; i<4; i++) {
               c = i==0 ? videoChoice : i==1 ? mapChoice : i==2 ? cutChoice : fctChoice;
               for( int j=0; j<c.getItemCount(); j++ ) {
                  if( ((String)c.getItemAt(j)).equalsIgnoreCase(s) ) { c.setSelectedIndex(j); break suite; }
               }
            }
         }
      } else {
         videoChoice.setSelectedIndex(1);  // noreverse
         mapChoice.setSelectedIndex(0);    // BB
      }

      //      fctBkgChoice.setSelectedIndex(2);  // LINEAR par d�faut
      //      s = aladin.BKGDEFAULT!=null ? aladin.BKGDEFAULT : get(BKG);
      //      if( s!=null ) {
      //         Tok tok = new Tok(s);
      //         while( tok.hasMoreTokens() ) {
      //            s = tok.nextToken();
      //            suite: for( int i=0; i<3; i++) {
      //               c = i==0 ? videoBkgChoice : i==1 ? mapBkgChoice : fctBkgChoice;
      //               for( int j=0; j<c.getItemCount(); j++ ) {
      //                  if( ((String)c.getItemAt(j)).equalsIgnoreCase(s) ) { c.setSelectedIndex(j); break suite; }
      //               }
      //            }
      //         }
      //      }

      //      s = get(CSV);
      //      csvItem=0;
      //      if( s == null ) csvChoice.setSelectedIndex(0);
      //      else {
      //         for( int i=0; i<CSVITEM.length; i++ ) {
      //            if( CSVITEM[i].equals(s) ) csvChoice.setSelectedIndex(csvItem=i);
      //         }
      //      }

      s = get(FILTER);
      if( s != null && s.charAt(0)=='N' ) filterChoice.setSelectedIndex(0);
      else filterChoice.setSelectedIndex(1);

//      s = get(TRANS);
//      if( s != null && s.charAt(0)=='N' ) {
//         transparencyChoice.setSelectedIndex(0);
//         transparencyLevel.setEnabled(false);
//      }
//      else {
//         transparencyChoice.setSelectedIndex(1);
//         transparencyLevel.setEnabled(true);
//      }
//      transparencyChoice.addItemListener(this);

      s = get(TRANSLEVEL);
      if( s==null ) transparencyLevel.setValue(15);

//      if( isUnixStandalone() ) {
//         s = get(BROWSER);
//         if( s == null ) s = "";
//         browser.setText(s);
//      }

      s = getServer();
      serverTxt.setText(s);
      s = getSurvey();
      surveyTxt.setText(s);

      String defaultGlu = get(GLU);
      if( defaultGlu == null ) defaultGlu = Glu.NPHGLUALADIN;
      setSelectGluChoice(defaultGlu);

      reload.setEnabled( true );

      if( logChoice!=null) logChoice.setSelectedIndex(isLog()?0:1);

      if( helpChoice!=null) helpChoice.setSelectedIndex(isHelp()?0:1);

      if( filterHDUChoice!=null) filterHDUChoice.setSelectedIndex( isFilterHDU()?0:1 );

      if( planetChoice!=null) planetChoice.setSelectedIndex( isPlanet()?0:1 );

      if( bxEpoch!=null ) bxEpoch.setSelected( isSliderEpoch() );
      if( bxSize!=null )  bxSize.setSelected( isSliderSize() );
      if( bxDens!=null )  bxDens.setSelected( isSliderDensity() );
      if( bxOpac!=null )  bxOpac.setSelected( isSliderOpac() );
      if( bxCube!=null )  bxCube.setSelected( isSliderCube() );
      if( bxZoom!=null )  bxZoom.setSelected( isSliderZoom() );

      if( cache!=null ) {
         long cacheSize = PlanBG.cacheSize;
         try {
            if( cacheSize==-1 ) cacheSize = Long.parseLong(get(CACHE));
         } catch( NumberFormatException e ) { }
         if( cacheSize==-1 ) {
            SwingUtilities.invokeLater(new Runnable() {
               public void run() {
                  long cacheSize = PlanBG.getCacheSize(new File(PlanBG.getCacheDirStatic()), null);
                  PlanBG.setCacheSize(cacheSize);
                  cache.setText((cacheSize/1024)+" / ");
               }
            });
         } else cache.setText((cacheSize/1024)+" / ");
         int mCache = (int)(PlanBG.MAXCACHE/1024);
         maxCache.setText(mCache+"");
      }
   }

   /**
    * R�cup�ration de la valeur associ�e � une cl�
    * @param key la cl� de la propri�t�
    * @return la valeur associ�e � la cl� ou null si cl� inconnue
    */
   protected String get(String key) {
      ConfigurationItem item = getItem(key);
      if( item == null ) return null;
      return item.value;
   }

   /**
    * Suppression d'une propri�t�
    * @param key la cl� de la propri�t� � supprimer
    */
   protected void remove(String key) {
      ConfigurationItem item = getItem(key);
      if( item != null ) {
         prop.removeElement(item);
         flagModif = true;
      }
   }

   /**
    * Ajout d'une propri�t�, ou modification d'une propri�t� pr�-existante
    * @param key la cl� associ�e � la propri�t� (ne peut contenir de blancs)
    * @param value la (nouvelle) valeur associ�e � la cl�
    */
   protected void set(String key, String value) {
      if( value==null ) { remove(key); return; }
      
      key.replace(' ', '_');
      flagModif = true;
      ConfigurationItem item = getItem(key);
      if( item == null ) {
         item = new ConfigurationItem(key, value);
         prop.addElement(item);
      } else item.value = value;
   }

   /**
    * Retourne la propri�t� associ�e � une cl�
    * @param key la cl� de la propri�t� recherch�e
    * @return la propri�t� associ�e ou null si inconnue
    */
   private ConfigurationItem getItem(String key) {
      Enumeration e = prop.elements();
      while( e.hasMoreElements() ) {
         ConfigurationItem item = (ConfigurationItem) e.nextElement();
         if( item.key.equals(key) ) return item;
      }
      return null;
   }
   
 

   private int oFrame=Localisation.ICRS;

   /**
    * Sauvegarde des propri�t�s dans un fichier de Configuration (si n�cessaire)
    */
   protected void save() throws Exception {
      try { save1(); }
      catch( Exception e) {
         if( Aladin.levelTrace>=3 ) e.printStackTrace();
         throw e;
      }
   }
   protected void save1() throws Exception {
      if( Aladin.NOGUI ) return;
      
      // On vire les mots cl�s d�pr�ci�s pour faire du m�nage dans le fichier de config
      for( String k : DEPRECATED ) remove(k);

      // On m�morise les helps qu'on ne veut plus
      majStopHelp();

      // On m�morise la date de la session
      setLastRun();

      // M�morisation de la version utilis�e
      set(VERSION,Aladin.VERSION);

      // On conserve l'�tat du r�ticule (large ou normal)
      if( aladin.calque.reticleMode==2 && get(RETICLE)==null ) set(RETICLE,"Large");
      if( aladin.calque.reticleMode!=2 && get(RETICLE)!=null ) remove(RETICLE);

      // On conserve l'�tat du tooltip
      if( aladin.calque.flagTip && get(TOOLTIP)==null ) set(TOOLTIP,"On");
      if( !aladin.calque.flagTip && get(TOOLTIP)!=null ) remove(TOOLTIP);

      // On conserve l'�tat du frame
      //      int frame=aladin.localisation.getFrame();
      //      if( getFrame()!=frame ) set(FRAME,Localisation.REPERE[frame]);

//      // On conserve l'�tat de la fen�tre des mesures
//      if( aladin.mesure.isReduced() && get(MESURE)==null ) remove(MESURE);
//      if( !aladin.mesure.isReduced() && get(MESURE)!=null ) set(MESURE,"on");

      // On conserve la taille des diff�rents panels si n�cessaire
      int n;
      if( aladin.splitZoomHeight!=null ) {
         n = aladin.splitZoomHeight.getCompSize();    
         if( n!=DEF_ZHEIGHT ) set(ZHEIGHT,""+n );   
         else remove(ZHEIGHT);
      }
      if( aladin.splitZoomWidth!=null ) {
         n = aladin.splitZoomWidth.getCompSize();     
         if( n!=DEF_ZWIDTH )  set(ZWIDTH,""+n );    
         else remove(ZWIDTH);
      }
      if( aladin.TREEWIDTH==null && aladin.splitHiPSWidth!=null ) {
         n = aladin.splitHiPSWidth.getCompSize();   
         if( n!=DEF_HWIDTH )  set(HWIDTH,""+n );    
         else remove(HWIDTH);
      }
      if( aladin.splitMesureHeight!=null ) {
         n = aladin.splitMesureHeight.getCompSize();  
         if( n!=DEF_MHEIGHT ) set(MHEIGHT,""+n );   
         else remove(MHEIGHT);
      }

      // On m�morise les bookmarks si n�cessaire
      if( /* !Aladin.OUTREACH && */ aladin.bookmarks.canBeSaved() ) {
         String list = aladin.bookmarks.getBookmarkList();
         Aladin.trace(4,"Configuration.save(): updating bookmark list => "+list);
         if( aladin.bookmarks.isDefaultList() ) remove(BOOKMARKS);
         else if( get(BOOKMARKS)==null || !get(BOOKMARKS).equals(list) ) set(BOOKMARKS,list);
      }

      String ocache = get(CACHE);
      if( PlanBG.cacheSize!=-1L ) {
         String s=PlanBG.cacheSize+"";
         if( ocache==null || !s.equals(ocache) ) set(CACHE,s);
      }

      ocache = get(MAXCACHE);
      String s1 = PlanBG.MAXCACHE+"";
      if( ocache==null || !s1.equals(ocache) ) set(MAXCACHE,s1);

      String s;
//      s = get(LOG);
//      if( s!=null && s.equals(ACTIVATED) ) remove(LOG);

      s = get(HELP);
      if( s!=null && s.equals(ACTIVATED) ) remove(HELP);

      s = get(FILTERHDU);
      if( s!=null && s.equals(ACTIVATED) ) remove(FILTERHDU);

      s = get(PLANET);
      if( s!=null && s.equals(NOTACTIVATED) ) remove(PLANET);

      s = get(LOOKANDFEEL);
      if( s!=null && s.equals(JAVA) ) remove(LOOKANDFEEL);

      s = get(LOOKANDFEELTHEME);
      if( s!=null && s.equals("dark") ) remove(LOOKANDFEELTHEME);

      s = get(LOOKANDFEELSCALE);
      if( s!=null && s.equals("Automatic") ) remove(LOOKANDFEELSCALE);

      // On conserve l'�tat du pointeur Autodist, Simbad et du pointeur VizierSED
      if( !aladin.calque.flagSimbad ) set(SIMBAD,"Off");
      else remove(SIMBAD);

      if( !aladin.calque.flagVizierSED ) set(VIZIERSED,"Off");
      else remove(VIZIERSED);

      if( aladin.calque.flagAutoDist && !getAutoDist() ) set(AUTODIST,"On");  //remove(AUTODIST);
      if( !aladin.calque.flagAutoDist && getAutoDist() ) remove(AUTODIST);    //set(AUTODIST,"Off");

      try {
         if( aladin.calque.zoom.zoomView.sed.getSEDWave() ) set(SEDWAVE,"On");
         else remove(SEDWAVE);
      } catch( Exception e1 ) { }

      // On conserve la position de la fen�tre
      if( !flagModif && sameWinParam() ) return;

      // Existe-il d�j� un r�pertoire g�n�rique .aladin sinon je le cr�e ?
      String configDir = System.getProperty("user.home") + Util.FS + aladin.CACHE;
      File f = new File(configDir);
      if( !f.isDirectory() ) if( !f.mkdir() ) throw new Exception(
            "Cannot create " + aladin.CACHE + " directory");

      // Je vais (re)cr�er le fichier de configuration
      String configName = configDir + Util.FS + CONFIGNAME;
      f = new File(configName);
      f.delete();
      BufferedWriter bw = new BufferedWriter(new FileWriter(f));

      // D�termination de la derni�re position/taille de la fen�tre Aladin
      // Si la fen�tre est maximis�e, la largeur et la hauteur seront n�gatives et on r�duira
      // tout d'abord la fen�tre pour conna�tre sa taille r�duite
      if( !aladin.isApplet() ) {
         boolean max = (aladin.f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0 ;
         aladin.f.setExtendedState(Frame.NORMAL);
         Point p = aladin.f.getLocation();
         Dimension d = aladin.f.getSize();

         // Obligatoire pour les Macs
         if( p.x<0 ) p.x=0;
         if( p.y<0 ) p.y=0;

         if( aladin.LOCATION==null ) {
            String fullWin="";
            
            // Fullwin fen�tr� (=> suffixe "-") ? ou plein �cran (=> suffixe "--") ?
            if( aladin.isFullScreen() ) {
               fullWin = aladin.fullScreen.getMode()==FrameFullScreen.WINDOW ? " -": " --";
            }
            
            // Test pour �viter les valeurs incongrues
// NE MARCHE PAS EN CAS DE MULTI ECRANS -> J'ENLEVE PF 15/07/2022
//            if( fullWin.length()==0 || Math.abs(p.x)>aladin.SCREENSIZE.width
//                  || Math.abs(p.y)>aladin.SCREENSIZE.height
//                  || d.width<100 || d.width>aladin.SCREENSIZE.width*1.5
//                  || d.height<100 || d.height>aladin.SCREENSIZE.height*1.5
//                  ) remove(WINLOC);
//            else {
               set(WINLOC,p.x+" "+p.y+" "+(max?-d.width:d.width)+" "+(max?-d.height:d.height)+fullWin);
//            }
         }
      }

      // Je sauvegarde les propri�t�s de la configuration
      boolean first = true;
      Enumeration e = prop.elements();
      while( e.hasMoreElements() ) {
         ConfigurationItem item = (ConfigurationItem) e.nextElement();

         // Ent�te si n�cessaire
         if( first && !(item.key.equals("#") && item.value.startsWith("#Aladin")) ) {
            bw.write("#Aladin user configuration file");
            bw.newLine();
            bw.newLine();
         }
         first = false;

         if( item.key.equals("#") ) bw.write(item.value); // Commentaires
         else if( item.key.trim().length() > 0 ) bw.write(Util.align(item.key, 20) + item.value); // Propri�t�s
         bw.newLine();
      }

      // Je sauvegarde les paths des fichiers r�cemment ouverts
      int i=1;
      if( lastFile!=null ) {
         for( String path : lastFile ) {
            String key = LASTFILE+(i++);
            bw.write(Util.align(key, 20) + path);
            bw.newLine();
         }
      }
      
      // Je sauvegarde les 20 meilleures indirections (les plus r�centes)
      try { aladin.glu.saveGluHistory( bw); } catch( Exception e1 ) {
         if( Aladin.levelTrace>=3 ) e1.printStackTrace();
      }
      
      // Je sauvergarde les 40 derni�res target
      i=1;
      for( String target : aladin.targetHistory.getList() ) {
         String key = LASTTARGET+(i++);
         bw.write(Util.align(key, 20) + target);
         bw.newLine();
         if( i>40 ) break;
      }

      // Je sauvegarde les filtres du r�pertoire des collections
      // sous la forme : DirFilterNN   name : filter_rule
      // et j'en profite pour sauvegarder les MOCs correspondants (et nettoyer les vieux)
      String filterMocDir = configDir + Util.FS + MOCFILTER;
      File mocDir = new File(filterMocDir);
      Util.deleteDir( mocDir );
      
      boolean mocDirCreated=false;
      
      if( filterExpr.size()>0) {
         
         i=1;
         try {
            for( String name : filterExpr.keySet() ) {
               if( name.equals(Directory.ALLCOLL) ) continue;
               if( name.equals(Directory.MYLIST) ) continue;
               String expr = filterExpr.get(name);
               Moc moc = filterMoc.get(name);
               if( moc==null && (expr==null || expr.equals("*") || expr.equals("")) ) continue;
               
               String mocInfo = moc==null ? "" : MultiMoc.INTERSECT[ DirectoryFilter.getIntersect(moc) ]+":";
               
               // m�morisation du filtre dans le fichier de conf
               String key = DIRFILER+(i++);
               bw.write( Util.align(key, 20) + name+":" + mocInfo + (expr==null?"":expr) );
               bw.newLine();
               
               // m�morisation du Moc associ� dans le r�pertoire pr�vu � cet effet
               if( moc!=null ) {
                  if( !mocDirCreated ) {
                     mocDir.mkdir();
                     Util.pause(100);
                     mocDirCreated=true;
                  }
                  
                  String mocName = name2MocName( name );
                  OutputStream out = new FileOutputStream( filterMocDir + Util.FS + mocName );
                  moc.writeFITS(out);
                  out.close();
               }
            }
         } catch( Exception e1 ) {
            e1.printStackTrace();
         }
      }

      bw.close();
      flagModif = false;

      Aladin.trace(3, "Aladin user configuration file saved");
   }
   
   private String name2MocName( String name ) {
      String s = name.replace('/', '_')
            .replace('\\', '_').replace(':', '_').replace('.', '_')
            .replace('?', '_');
      return s+".fits";
   }

   protected void saveLocalFunction() throws Exception {
      if( !aladin.command.functionModif() ) return;
      String configDir = System.getProperty("user.home") + Util.FS + aladin.CACHE;
      File f  = new File(configDir + Util.FS + CONFIGBKM);
      f.delete();
      StringBuffer s = new StringBuffer();
      BufferedWriter bw=null;
      Enumeration e = aladin.command.getLocalFunctions().elements();
      while( e.hasMoreElements() ) {
         Function f1 = (Function)e.nextElement();
         if( !f1.isLocalDefinition() ) continue;
         if( s.length()>0 ) s.append(',');
         s.append(f1.getName());
         if( bw==null ) bw = new BufferedWriter(new FileWriter(f));
         bw.write(f1.toString(Util.CR)+Util.CR);
      }
      if( bw!=null ) bw.close();
      Aladin.trace(3, "Aladin user local functions saved: "+s);
   }

   public String getLocalBookmarksFileName() {
      return System.getProperty("user.home") + Util.FS + aladin.CACHE + Util.FS + CONFIGBKM;
   }
   
   
   /** Retourne vrai si la config indique que l'UISCALE doit �tre g�r� par l'OS
    * Cette m�thode statique peut �tre appel�e avant la cr�ation du premier objet Swing
    */
   static protected boolean isUIScaleByOs() {
      String s = getProperty( LOOKANDFEELSCALE );
      return s!=null && s.equals( UISCALESTR[1] );
   }
   
   /** Lecture manuelle du fichier de conf pour retourner la valeur d'une cl�, null si non trouv� */
   static private String getProperty(String k) {
      BufferedReader br=null;
      String s;
      int i,j;
      try {
         String configDir = System.getProperty("user.home") + Util.FS + Aladin.CACHE;
         File f = new File(configDir);
         if( !f.isDirectory() ) return null;
         f = new File( configDir + Util.FS + CONFIGNAME );
         if( !f.exists() ) return null;
         br = new BufferedReader(new FileReader(f));
         while( (s = br.readLine()) != null ) {
            char a[] = s.toCharArray();
            for( i = 0; i < a.length && !Character.isSpace(a[i]); i++ );
            String key = new String(a, 0, i);
            for( j = i; j < a.length && Character.isSpace(a[j]); j++ );
            String value = (new String(a, j, a.length - j)).trim();
            if( k.equals(key) ) return value;
         }
      } 
      catch( Exception e ) { }
      finally { if( br!=null ) try { br.close(); } catch( Exception e ) {}  }
      return null;
   }

   /** Chargement des propri�t�s depuis un fichier de configuration */
   protected void load() throws Exception {

      // Existe-il d�j� un r�pertoire g�n�rique .aladin
      String configDir = System.getProperty("user.home") + Util.FS + Aladin.CACHE;
      File f = new File(configDir);
      if( !f.isDirectory() ) return;

      // Je vais tenter de lire le fichier de configuration
      String configName = configDir + Util.FS + CONFIGNAME;
      f = new File(configName);
      if( !f.exists() ) return;
      BufferedReader br = new BufferedReader(new FileReader(f));

      // Je remet � z�ro les �ventuelles propri�t�s d�j� charg�es
      prop = new Vector();

      // Je lis les propri�t�s de la configuration
      String s;
      int line = 0;
      Aladin.trace(2, "Loading Aladin user configuration file...");
      while( (s = br.readLine()) != null ) {
         line++;

         if( s.trim().length() == 0 ) {
            prop.addElement(new ConfigurationItem(" ", null));
            continue;
         }
         if( s.charAt(0) == '#' ) {
            prop.addElement(new ConfigurationItem("#", s));
            continue;
         }
         // Cas particulier des paths des fichiers r�cemment ouverts
         char a[] = s.toCharArray();
         int i, j;
         for( i = 0; i < a.length && !Character.isSpace(a[i]); i++ );
         String key = new String(a, 0, i);
         for( j = i; j < a.length && Character.isSpace(a[j]); j++ );
         String value = (new String(a, j, a.length - j)).trim();
         if( key.equals(TRANSOLD) ) key=TRANS;      // Pour compatiblit�
         if( key.equals(LOOKANDFEELTHEME) && !value.equals("dark") ) previousTheme=1;
         if( key.equals(PLANET) && !value.equals(ACTIVATED) ) previousPlanet=1;
         aladin.trace(6, "Configuration.load() [" + key + "] = [" + value + "]");
         
         if( Aladin.TREEWIDTH!=null && key.equals(HWIDTH) ) value=Aladin.TREEWIDTH;
         
         if( key.equals(LOOKANDFEELSCALE) ) setLastUiScale(value);

         if( key.startsWith(LASTFILE) ) setLastFile(value,false);
         else if( key.startsWith(LASTTARGET) ) setLastTarget(value);
         else if( key.startsWith(DIRFILER) ) loadDirFilter(value);
         else if( key.startsWith(LASTGLU) ) memoLastGlu(value);
         else set(key, value);
      }
      br.close();

      // Positionnement du CSVCHAR s'il y a lieu
      setCSV(get(CSV));

      //      // Positionne du CENTEREDTAG s'il y a lieu
      //      setCENTEREDTAG(get(TAG));

      // Positionnement du MAXCACHE s'il y a lieu
      setMaxCache(get(MAXCACHE));

      // Positionnement des mots cl�s des helps qu'on ne veut plus voir
      initStopHelp(get(STOPHELP));

      flagModif = false;
   }

   /** On recharge toutes les d�finitions du glu */
   private void reloadGlu() {
      try {
         aladin.makeCursor(this, Aladin.WAITCURSOR);
         aladin.glu.reload(true,false);
         aladin.directory.reload();
         aladin.makeCursor(this, Aladin.DEFAULTCURSOR);
      } catch(Exception e) { e.printStackTrace(); }
   }

   /** Retourne la date de la derni�re Session - et si inconnue, retourne
    * la date courante => permet l'�ventuelle reg�n�ration du cache GLU
    * si �a fait trop longtemps depuis la derni�re version */
   protected long getLastRun() {
      try {
         return Long.parseLong( get(LASTRUN) );
      } catch( Exception e ) {
         return System.currentTimeMillis();
      }
   }

   /** Maj de la date de la derni�re session */
   protected void setLastRun() {
      set(LASTRUN,System.currentTimeMillis()+"");
   }

   /** Ouverture de la fen�tre de contribution � une nouvelle traduction */
   private void langContrib() {
      GridBagLayout g = new GridBagLayout();
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.NONE;
      c.gridwidth = GridBagConstraints.REMAINDER;
      c.anchor = GridBagConstraints.WEST;

      TextField lang = new TextField(15);
      TextField code = new TextField(3);
      Panel panel = new Panel();
      panel.setLayout(new GridLayout(2,2));
      panel.add(new Label("Language name:"));
      panel.add(lang);
      panel.add(new Label("2 letter code:"));
      panel.add(code);
      g.setConstraints(panel,c);

      if( !aladin.question(this,"Specify the new language name (in english)\n" +
            "and the corresponding 2 letter abbreviation.\nOr just press \"Ok\" " +
            "for checking/completing the current language translation.",panel) ) return;
      String s = Util.toLower(code.getText())+" "+Util.toUpLower(lang.getText());
      aladin.chaine.testLanguage(s);
      dispose();
   }

   /**
    * Activation des choix de l'utilisateur
    *
    * @return true si tout est bon, sinon false
    */
   public boolean apply() throws Exception {
      boolean rep = true;
      String s;

      // Pour le centrage des tags
      //      if( tagChoice!=null ) {
      //         s = (String)tagChoice.getSelectedItem();
      //         set(TAG,s);
      //         setCENTEREDTAG(s);
      //      }
      
      // Pour les param�tres de la grille
      if( gridColorBox!=null )   set(GRIDC,   gridColorBox.getCouleur() );
      if( gridColorRABox!=null ) set(GRIDCRA, gridColorRABox.getCouleur() );
      if( gridColorDEBox!=null ) set(GRIDCDE, gridColorDEBox.getCouleur() );
      if( gridFontCombo!=null )  {
         s = gridFontCombo.getSelectedItem()+"";
         if( (Aladin.SSIZE+"").equals(s) ) s=null;
         set(GRIDF,s);
      }
      if( strokeCombo!=null ) {
         s = strokeCombo.getSelectedItem()+"";
         if( STROKESTR[0].equals(s) ) s=null;
         set(GRIDSTROKE,s); 
      }
      aladin.view.initGridParam(true);

      // Pour les param�tres des infos de la ue
      if( infoFontBorderCombo!=null ) set(INFOB, (String)infoFontBorderCombo.getSelectedItem() );
      if( infoColorBox!=null ) set(INFOC, infoColorBox.getCouleur() );
      if( infoLabelColorBox!=null ) set(INFOCL, infoLabelColorBox.getCouleur() );
      if( infoFontCombo!=null )  {
         s = infoFontCombo.getSelectedItem()+"";
         if( (Aladin.SSIZE+"").equals(s) ) s=null;
         set(INFOF,s);
      }
      aladin.view.initInfoParam(true);

      // Pourle log
      if( logChoice!=null  ) {
//         if( logChoice.getSelectedIndex()==1 ) {
//            if( get(LOG)==null ) aladin.glu.log("Log", "off");
//            set(LOG,(String)logChoice.getSelectedItem());
//         }
//         else {
//            boolean pub = get(LOG)!=null && get(LOG).equals(NOTACTIVATED);
//            remove(LOG);
//            if( pub ) aladin.glu.log("Log", "on");
//         }
         set(LOG,(String)logChoice.getSelectedItem());
      }

      // Pour l'assistant d�butant
      if( helpChoice!=null ) {
         if( helpChoice.getSelectedIndex()==1 ) set(HELP,(String)helpChoice.getSelectedItem());
         else remove(HELP);
      }

      // Pour le filtrage des extensions FITS
      if( filterHDUChoice!=null ) {
         if( filterHDUChoice.getSelectedIndex()==1 ) set(FILTERHDU,(String)filterHDUChoice.getSelectedItem());
         else remove(FILTERHDU);
      }

      // Pour le filtrage des donn�es plan�taires
      if( planetChoice!=null ) {
         int t = planetChoice.getSelectedIndex();
         if( planetChoice.getSelectedIndex()==0 ) set(PLANET,(String)planetChoice.getSelectedItem());
         else remove(PLANET);
         if( t!=previousPlanet ) Aladin.info(this,RESTART);
      }

      // Pour le Look & Feel
      if( lfChoice!=null ) {
         if( lfChoice.getSelectedIndex()==1 ) set(LOOKANDFEEL,OPSYS);
         else remove(LOOKANDFEEL);
      }

      // Pour le Look & Feel
      if( themeChoice!=null ) {
         int t = themeChoice.getSelectedIndex();
         if( t!=0 ) set(LOOKANDFEELTHEME, (String) themeChoice.getSelectedItem() );
         else remove(LOOKANDFEELTHEME);
         if( t!=previousTheme ) Aladin.info(this,RESTART);
      }

      // Pour le facteur d'�chelle
      if( uiScaleChoice!=null ) {
         int t = uiScaleChoice.getSelectedIndex();
         if( t!=0 ) set(LOOKANDFEELSCALE, (String) uiScaleChoice.getSelectedItem() );
         else remove(LOOKANDFEELSCALE);
         if( t!=previousUiScale ) Aladin.info(this,RESTART);
      }

      // Les sliders de controle
      if( bxEpoch!=null ) {
         if( !bxEpoch.isSelected() ) set(SLEPOCH,"off");
         else remove(SLEPOCH);
      }
      if( bxSize!=null ) {
         if( !bxSize.isSelected() ) set(SLSIZE,"off");
         else remove(SLSIZE);
      }
      if( bxDens!=null ) {
         if( !bxDens.isSelected() ) set(SLDENS,"off");
         else remove(SLDENS);
      }
      if( bxCube!=null ) {
         if( !bxCube.isSelected() ) remove(SLCUBE);
         else set(SLCUBE,"on");
      }
      if( bxOpac!=null ) {
         if( !bxOpac.isSelected() ) set(SLOPAC,"off");
         else remove(SLOPAC);
      }
      if( bxZoom!=null ) {
         if( !bxZoom.isSelected() ) set(SLZOOM,"off");
         else remove(SLZOOM);
      }
      // Ca ne fonctionne pas correctement pour le moment
      aladin.calque.slider.adjustSliderPanel();
      //      if( aladin.f!=null ) {
      //         Dimension dim = aladin.f.getSize();
      //         aladin.f.pack();
      //         aladin.f.setSize(dim);
      //      }

      // Pour le site Glu
      int index = gluChoice.getSelectedIndex();
      if( index!=-1 && index!=lastGluChoice ) {
         s = (String) gluUrl.elementAt(index);
         String sNew = aladin.glu.setDefaultGluSite(s);
         lastGluChoice=index;
         if( sNew != null ) {
            setSelectGluChoice(sNew);
            rep = false;
         }
         reloadGlu();
      }

      // Pour le mode Pixel
      //      set(PIXEL,(String)pixelChoice.getSelectedItem());

      // Pour les frames par d�faut
      set(FRAME,(String)frameChoice.getSelectedItem());
      set(FRAMEALLSKY,(String)frameAllskyChoice.getSelectedItem());

      // Pour la projection all-sky par d�faut
      set(PROJALLSKY,(String)projAllskyChoice.getSelectedItem());

      // Pour le choix du mapping pixel
      s = videoChoice.getSelectedItem()+" "
            +mapChoice.getSelectedItem()+" "
            +cutChoice.getSelectedItem()+" "
            +fctChoice.getSelectedItem();
      set(CM,s);
      aladin.CMDEFAULT=null;    // plus de surcharge �ventuel via setconf

      //      // Pour le choix du mapping du background
      //      s = videoBkgChoice.getSelectedItem()+" "
      //          +mapBkgChoice.getSelectedItem()+" "
      //          +fctBkgChoice.getSelectedItem();
      //      set(BKG,s);
      //      aladin.calque.planBG.setCM();
      //      aladin.BKGDEFAULT=null;    // plus de surcharge �ventuel via setconf

      // Pour le choix du serveur d'images par d�faut
      set(SERVER,serverTxt.getText().trim());
      set(SURVEY,surveyTxt.getText().trim());

      // Pour le mode Simbad pointer
      //      set(SMB,(String)smbChoice.getSelectedItem());

      // Pour le filtre
      set(FILTER,(String)filterChoice.getSelectedItem());

      // Pour la transparence des footprints
      float level = (float)(transparencyLevel.getValue()/100.0);
      if( level>=1 ) level -= 0.000111f;
      else level += 0.000111f; // le '+/-0.000111f' me permet de distinguer les plans footprints ayant encore la valeur par d�faut

//      set(TRANS,(String)transparencyChoice.getSelectedItem());
      set(TRANSLEVEL,level+"");
//         setTransparency((String)transparencyChoice.getSelectedItem(), level);

      // Pour le langage
      index = langChoice.getSelectedIndex();
      if( index!=langItem ) {
         langItem=index;
         if( index>lang.length ) {
            installRemoteLanguage((String)langChoice.getSelectedItem());
         } else {
            setLang(index==0 ? null : (String)langChoice.getSelectedItem());
         }
         Aladin.info(this,RESTART);
      }

      // Pour le mode
      index = modeChoice.getSelectedIndex();
      if( index!=modeItem ) {
         modeItem=index;
         setMode(index==0 ? null : (String)modeChoice.getSelectedItem());
         Aladin.info(this,RESTART);
      }


      // Pour le caract�re de CVS
      if( csvChoice!=null ) {
         index = csvChoice.getSelectedIndex();
         if( index!=csvItem ) {
            set(CSV,CSVITEM[index]);
            setCSV(CSVITEM[index]);
         }
      }

      // Pour le browser
//      if( browser != null ) {
//         s = browser.getText().trim();
//         if( s.length() != 0 ) set(BROWSER, s);
//         else remove(BROWSER);
//      }

      // Pour le r�pertoire par d�faut
      if( dir != null ) {
         s = dir.getText().trim();
         //         setDir( s.length()==0 ? null : s );
         if( s.length()==0 ) remove(DIR);
         else { setDir(s); set(DIR,s); }
      }

      // Pour la taille du cache
      if( maxCache!=null ) {
         try {
            s=  maxCache.getText();
            long maxCacheSize = Long.parseLong(s);
            PlanBG.setMaxCacheSize(maxCacheSize*1024);
         } catch( Exception e ) { }
      }
      
      if( hideTapSchema != null ) {
    	  if (hideTapSchema.isSelected() != TapManager.getInstance(aladin).hideTapSchema) {
    		  set(TAPSCHEMADISPLAY, String.valueOf(hideTapSchema.isSelected()));
    		  Aladin.info(this,RESTART);
    	  }
      }

      // Sauvegarde imm�diate
      save();
      updateWidgets();

      return rep;
   }

   /** Retourne la meilleure version de la langue pass�e en param�tre
    * Il s'agit du num�ro de version le plus proche de celui d'Aladin
    */
   private Langue getBestLangVersion(String s) {
      Langue bestLang=null;
      try {
         double minDiff = Double.MAX_VALUE;
         double v =  Double.parseDouble( Aladin.VERSION.substring(1) );

         Enumeration e = remoteLang.elements();
         while( e.hasMoreElements() ) {
            Langue lg = (Langue)e.nextElement();
            if( !lg.isLangue(s) ) continue;
            double diff = Math.abs(v-lg.version);
            if( minDiff>diff ) { minDiff=diff; bestLang=lg; }
            //System.out.println((minDiff==diff?"* ":"  ")+lg);
         }
      } catch( Exception e ) { }
      return bestLang;
   }

   /** Supprime les fichiers de langues anciens qui traine dans le r�pertoire
    * cache en �vitant de supprimer les fichiers locaux de langues
    * (Aladin-xxxx-nnn-loc.string[.utf])*/
   private void clearPreviousRemoteLang() {
      try {
         String dir = System.getProperty("user.home")+Util.FS+Aladin.CACHE;
         FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
               return Util.matchMaskIgnoreCase("Aladin*.string*", name);
            }
         };
         File fdir = new File(dir);
         File [] list = fdir.listFiles(filter);
         for( int i=0; i<list.length; i++ ) {
            String name = list[i].getName();
            if( !name.endsWith(".utf") && !name.endsWith(".string") ) continue;
            if( name.endsWith("-perso.string") || name.endsWith("-perso.string.utf") ) continue;
            //            System.out.println("Je devrais supprimer "+list[i]);
            list[i].delete();
         }
      } catch( Exception e ) {
         if( Aladin.levelTrace>=3 ) e.printStackTrace();
      }
   }


   /**
    * D�chargement d'un fichier de traduction de la langue "s" le plus proche du num�ro
    * de version d'Aladin et m�morisation de celui-ci dans le
    * fichier .aladin/Lang/Aladin-xx.string[.utf]
    * @param s langue � r�cup�rer sur le site via le tag glu "Aladin.lang"
    */
   private void installRemoteLanguage(String s)  throws Exception {
      Langue lg = getBestLangVersion(s);

      // Lecture compl�te du flux
      URL u = new URL(Aladin.LANGURL+"&id="+lg.file);
      MyInputStream in = Util.openStream(u);
      //      MyInputStream in = new MyInputStream( u.openStream() );
      //      in = in.startRead();
      byte buf[] = in.readFully();
      in.close();
      if( buf.length<10 ) throw new Exception("Cannot download translation file ["+u+"]");

      // Nettoyage
      clearPreviousRemoteLang();

      // Cr�ation/installation du fichier de la nouvelle langue
      installLanguage(s,lg.file,buf);

      return;
   }

   /** Installation d'une nouvelle langue dans le r�pertoire cache.
    * @param langue Le nom de la langue en anglais
    * @param filename Le nom du fichier (ex: Aladin-Spanish-5.026b.string)
    * @param buf Le contenu du fichier de traduction (d�j� UTF si n�cessaire)
    */
   protected void installLanguage(String langue, String filename, byte buf[]) throws Exception {
      String dir = System.getProperty("user.home")+Util.FS+Aladin.CACHE;
      String fullName = dir+Util.FS+filename;

      // Ecriture dans un fichier temporaire
      File f = new File(fullName+".tmp");
      RandomAccessFile rf = new RandomAccessFile(f,"rw");
      rf.write(buf);
      rf.close();

      // Suppression d'une �ventuelle version UTF ou ISO du m�me fichier
      if( fullName.endsWith(".string") ) {
         File f2 = new File(fullName+".utf");
         f2.delete();
      } else if( fullName.endsWith(".utf") ) {
         File f2 = new File(fullName.substring(0,fullName.length()-4));
         f2.delete();
      }

      // Remplacement du fichier temporaire par son nom d�finitif
      File f1 = new File(fullName);
      f1.delete();
      f.renameTo(f1);
      Aladin.trace(1, "Translation file installed ["+fullName+"]");
      set(LANG,langue);
   }

   /** Positionnement du langage, avec v�rification d'existence */
   private void setLang(String s) throws Exception {
      if( s==null ) { remove(LANG); return; }

      String s1;
      if( (s1=getLanguage(s))==null ) throw new Exception("Language not supported ! ["+s+"]");
      set(LANG,s1);
   }

   /** Positionnement du mode */
   private void setMode(String s) throws Exception {
      if( s==null ) { remove(MOD); return; }
      set(MOD,s);
   }
   
   /** Recharge les filtres m�moris�s pour l'arbre des collections
    * Syntaxe   name:[overlaps|enclosed|covers:]filter.... 
    * => peut disposer d'un MOC associ� dans le r�pertoire MocFilter et sous le nom
    * name[_covers|_enclosed].fit
    */
   protected void loadDirFilter(String s) {
      int i = s.indexOf(':');
      if( i<0 ) return;
      
      SMoc moc = null;
      int intersect=-1;   // Par d�faut par de Moc associ�, sinon MultiMoc.OVERLAPS|ENCLOSED|COVERS
      String name = s.substring(0,i).trim();
      String expr = s.substring(i+1).trim();
      
      // Y a-t-il un MOC associ� au filtre, et si oui, selon quel mode de recouvrement de r�gion
      int j = s.indexOf(':',i+1);
      if( j>i  ) {
         String mocInfo = s.substring(i+1,j).trim();
         intersect = Util.indexInArrayOf(mocInfo, MultiMoc.INTERSECT);
         if( intersect>=0 ) expr = s.substring(j+1).trim();

         // r�cup�ration d'un �ventuel Moc associ� au filtre
         File f = new File( System.getProperty("user.home") + Util.FS + aladin.CACHE
               + Util.FS + MOCFILTER + Util.FS + name2MocName(name) );
         if( f.exists() ) {
            InputStream in = null;
            try {
               in = new BufferedInputStream( new FileInputStream(f) );
               moc = new SMoc(in);
               DirectoryFilter.setIntersect(moc, intersect);
            }
            catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }
            finally{ try{ in.close(); } catch( Exception e) {} }
         }
         
      }
      setDirFilter( name, expr, moc );
   }
   
   /** M�morise un nouveau filtre sur l'arbre des collections */
   protected void setDirFilter(String name,String expr, Moc moc) {
      filterExpr.put(name, expr);
      if( moc!=null ) filterMoc.put(name,moc);
   }

   static private final int MAXLASTFILE = 20;

   /** M�morise un nouveau path de fichier r�cemment ouvert */
   protected void setLastFile(String path,boolean checkDoublon) {
      if( lastFile==null ) lastFile = new LinkedBlockingDeque<>(MAXLASTFILE);
      if( checkDoublon ) {
         path=path.replace('\\','/');
         File f1 = new File(path);
         for( String f2 : lastFile ) {
            if( f1.equals(new File(f2)) ) { lastFile.remove(f2); break; }
         }
      }
      if( lastFile.size()==MAXLASTFILE ) lastFile.removeFirst();
      lastFile.add(path);
   }
   
   /** M�morise les derni�res targets */
   protected void setLastTarget(String target ) {
      if( aladin.targetHistory!=null ) aladin.targetHistory.add(target,false);
   }
   
   // Juste pour m�moriser temporairement l'historique pass�e des indirections Glu
   private ArrayList<String> memoGlu = null;
   
   /** Memorise les derniers tests GLU passer dans les sessions pr�c�dentes
    * on les traitera par la suite dans la classe Glu lorsqu'elle sera cr��e */
   private void memoLastGlu(String gluSerialized) {
      if( memoGlu==null ) memoGlu = new ArrayList<>();
      memoGlu.add(gluSerialized);
   }
   
   /** M�morise le dernier facteur d'agrandissement de l'interface graphique */
   private void setLastUiScale(String s) {
      previousUiScale=Util.indexInArrayOf(s, UISCALESTR);
      if( previousUiScale==-1 ) previousUiScale=0;
   }
   
   /** Mise en place des �l�ments historiques de r�solution GLu (appel� par GLu quand il sera cr��) */
   protected void proceedLastGlu() {
      if( memoGlu==null ) return;
      for( String s : memoGlu ) aladin.glu.setGluHistory(s);
      memoGlu=null;
   }

   /** Positionnement du r�pertoire par d�faut, avec v�rification d'existence */
   private void setDir(String d) throws Exception {
      if( d==null ) { remove(DIR); return; }
      File f = new File(d);
      if( !f.isDirectory() ) throw new Exception("Not a directory ! ["+d+"]");
      set(DIR,d);
   }

   /** Positionne la chaine d�crivrant la derni�re version officielle */
   protected void setOfficialVersion(String s) {
      set(OFFICIALVERSION,s);
   }
   
   /** Positionne la chaine d�crivrant le dernier message d'annonce du CDS */
   protected void setCDSMessage(String s) {
      set(CDSMESSAGE,s);
   }

   /** Propri�t�s modifiables par la commande script "setconf xxx=valeur"
    * uniquement pour la session en cours
    */
   protected void setconf(String prop,String value) throws Exception  {
      if( prop.equalsIgnoreCase(DIR)
            || prop.equalsIgnoreCase("dir") )     setDir(value);
      else if( prop.equalsIgnoreCase(CSV) )       setCSV(value);
      else if( prop.equalsIgnoreCase("sync") )    setSyncMode(value);
      else if( prop.equalsIgnoreCase(POSITION)
            || prop.equalsIgnoreCase("position")
            || prop.equalsIgnoreCase("frame"))    setPositionMode(value);
      //      else if( prop.equalsIgnoreCase(PIXEL)
      //            || prop.equalsIgnoreCase("pixel"))    setPixelMode(value);
      else if( prop.equalsIgnoreCase(SIMBAD)
            || prop.equalsIgnoreCase("simbad"))   setSimbadMode(value);
      else if( prop.equalsIgnoreCase(FILTER)
            || prop.equalsIgnoreCase("filter"))   setFilterMode(value);
      else if( prop.equalsIgnoreCase(CM)
            || prop.equalsIgnoreCase("Colormap")
            || prop.equalsIgnoreCase("cm"))       setCMMode(value);
      else if( prop.equalsIgnoreCase(BKG)
            || prop.equalsIgnoreCase("Background"))setBkgMode(value);
      else if( prop.equalsIgnoreCase(PROJALLSKY)
            || prop.equalsIgnoreCase("projection")
            || prop.equalsIgnoreCase("Proj") )    setProjAllsky(value);
      else throw new Exception("Unknown conf. propertie ["+prop+"]");
   }
   
   /** TAP hide schema tables */
   protected boolean hideTapSchema() {
      String s = get(TAPSCHEMADISPLAY);
      return s==null || !s.equals("false");
   }
   
//   private static final String DEFAULT_FILENAME = "-";

   // Gestion des evenements
   public void actionPerformed(ActionEvent evt) {
      Object src = evt.getSource();

      String what = src instanceof JButton ? ((JButton)src).getActionCommand() : "";

      if( CLOSE.equals(what) ) dispose();
      else if( LANGCONTRIB.equals(what)) langContrib();
      else if( RELOAD.equals(what) ) reloadGlu();
      else if( APPLY.equals(what) ) {
         try { if( apply() ) { aladin.calque.repaintAll(); /* dispose(); */ } }
         catch( Exception e ) { Aladin.error(this," "+e.getMessage(),1); }
      }
      //      else if( GLUTEST.equals(what) ) startGluTest();
      //      else if( GLUSTOP.equals(what) ) stopGluTest();

      // Affichage du selecteur de r�pertoires
      else if( CLEARCACHE.equals(what) ) {
         clearCache();
      }
      // Affichage du selecteur de r�pertoires
      else if( BROWSE.equals(what) ) {
         String initDir = dir.getText();
         if( initDir.length()==0 ) initDir=null;
         String path = Util.dirBrowser("", initDir, dir, 3);
         if( path!=null ) aladin.memoDefaultDirectory(path);
      }
   }

   // implementation d'EventListener (d�placement du niveau de transparence)
   public void stateChanged(ChangeEvent e) {
      float level = (float)(transparencyLevel.getValue()/100.0);
      if( level>=1 ) level -= 0.000111f;
      else level += 0.000111f;

      if( Math.abs(level-Aladin.DEFAULT_FOOTPRINT_OPACITY_LEVEL)>0.02 ) {
//         setTransparency((String)transparencyChoice.getSelectedItem(), level);
         setTransparency(ACTIVATED, level);
         set(TRANSLEVEL, level+"");
      }
   }

   // implementaion de ChangeListener
   public void itemStateChanged(ItemEvent e) {
      // on ne s'int�resse qu'� la s�lection
      if( e.getStateChange()!=ItemEvent.SELECTED ) return;
      transparencyLevel.setEnabled(e.getItem().equals(FILTERY));
   }


   protected void processWindowEvent(WindowEvent e) {
      if( e.getID()==WindowEvent.WINDOW_CLOSING ) dispose();
      super.processWindowEvent(e);
   }


   /**
    * Classe permettant la m�morisation d'un propri�t�, c'est-�-dire un couple
    * (cl�,valeur)
    */
   private class ConfigurationItem {
      protected String key;  // Cl� associ�e � la propri�t�

      protected String value; // valeur associ�e � la propri�t�

      private ConfigurationItem(String key, String value) {
         this.key = key;
         this.value = value;
      }
   }

   /**
    * Structure pour m�moriser le descriptifs des langues
    */
   class Langue {
      String code;      // Code deux lettres de la langue
      String langue;    // Langue en toute lettre (en anglais)
      double version;   // Num�ro de version d'Aladin pr�vue pour cette langue
      String auteur;    // Auteur de la traduction
      String file;      // Nom du fichier distant lorsqu'il s'agit de la description d'une
      // langue distance

      Langue(String s) {
         langue=s;
         code=auteur="";
         version=0;
         file=null;
      }

      /** Retourne true si s correspond au code ou � la langue
       * sans diff�rencier les majuscules et les minuscules
       */
      boolean isLangue(String s) {
         try {
            return code!=null   && s.equalsIgnoreCase(code)
                  || langue!=null && s.equalsIgnoreCase(langue);
         } catch( Exception e ) { return false; }
      }

      public String toString() {
         return langue+" ("+code+") "+version+" "+auteur+(file!=null?" "+file:"");
      }
   }

}
