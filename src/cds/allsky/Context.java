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

package cds.allsky;

import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.swing.JProgressBar;

import cds.aladin.Aladin;
import cds.aladin.Coord;
import cds.aladin.Localisation;
import cds.aladin.MyInputStream;
import cds.aladin.MyProperties;
import cds.aladin.Tok;
import cds.astro.Astrocoo;
import cds.astro.Astroframe;
import cds.fits.CacheFits;
import cds.fits.Fits;
import cds.fits.HeaderFits;
import cds.moc.Healpix;
import cds.moc.SMoc;
import cds.tools.Util;
import cds.tools.hpxwcs.Tile2HPX;
import cds.tools.hpxwcs.Tile2HPX.WCSFrame;
import cds.tools.pixtools.CDSHealpix;

/**
 * Classe pour unifier les acc�s aux param�tres n�cessaires pour les calculs
 * + acc�s aux m�thodes d'affichages qui sont redirig�es selon l'interface
 * existante
 * @author Oberto + Fernique
 *
 */
public class Context {
   
   private boolean TERM=false;   // True si on va utiliser les codes couleurs du terminal
     
   static final public String FORCOMPATIBILITY = "#____FOR_COMPATIBILITY_WITH_OLD_HIPS_CLIENTS____";
   
   static private String [] FITSKEYS = { 
         "DATE","MJD_OBS","UTC","LST","DATE-OBS","MJD-OBS","MJD-END",
         "OBS-DATE","DATE-END","DATEOBS1","DATEOBS2","MIDOBS",
         "ORDATE","TIMESYS","MJDREF","JD","EXPTIME","TEXPTIME","OBSTIME","TIME-OBS",
         "WAVELMIN","WAVELMAX","WAVELEN","TELESCOP","TELNAME"
   };

   
   private static boolean verbose=false;
   protected String hipsId=null;             // Identificateur du HiPS (publisher_did, sinon publisher_id/obs_id sans le pr�fixe ivo://)
   protected String label;                   // Nom du survey

   protected String inputPath;               // R�pertoire des images origales ou path de l'image originale (unique)
   protected String outputPath;              // R�pertoire de la boule HEALPix � g�n�rer
   protected String split;                   // R�pertoires out alternatifs si trop plein - syntaxe: 300g;/hips3 500m;/hips4
   protected String hpxFinderPath;           // R�pertoire de l'index spatial Healpix (null si d�faut => dans outputPath/HpxFinder)
   protected String timeFinderPath;          // R�pertoire de l'index temporel (null si d�faut => dans outputPath/TimeFinder)
   protected String imgEtalon;               // Nom (complet) de l'image qui va servir d'�talon
   protected HeaderFits header=null;         // Ent�te FITS associ�e
   protected boolean isInputFile=false;      // true si le param�tre input concerne un fichier unique
   public int depth=1;                    // profondeur du cube (1 pour une image)
   protected boolean depthInit=false;        // true si la profondeur du cube a �t� positionn�
   public double crpix3=0,crval3=0,cdelt3=0;    // param�tre pour un cube
   public String cunit3=null;
   protected int [] hdu = null;              // Liste des HDU � prendre en compte
   public int bitpixOrig = -1;               // BITPIX des images originales
   protected double blankOrig= Double.NaN;   // Valeur du BLANK en entr�e
   public String blankKey=null;           // Mot cl� alternatif pour le BLANK
   protected boolean hasAlternateBlank=false;// true si on a indiqu� une valeur BLANK alternative
   public double bZeroOrig=0;                // Valeur BZERO d'origine
   public double bScaleOrig=1;               // Valeur BSCALE d'origine
   protected boolean bscaleBzeroOrigSet=false; // true si on a positionn�
   protected boolean flagNoInitEtalon=false;      // true => bloque la maj du cutOrig par estimation automatique
   protected double[] cutOrig;               // Valeurs cutmin,cutmax, datamin,datamax des images originales (valeurs raw)
   protected double[] pixelRangeCut;         // range et cut pass� sur la ligne de commande (valeurs physiques)
   public double[] pixelGood=null;           // Plage des valeurs des pixels conserv�s (valeurs physiques)
   public double[] good=null;                // Plage des valeurs de pixels conserv�s (raw)
   public int[] borderSize = {0,0,0,0};      // Bords � couper sur les images originales
   public Shape globalShape = null;            // Polygone global des pixels observ�s
   public boolean scanFov=false;             // true s'il faut rechercher des fichiers xxxx.fov
//   protected int circle = 0;                 // Rayon du cercle � garder, <=0 pour tout
   public int dataArea = Constante.SHAPE_UNKNOWN; // Type d'observable (totalit�, en ellipse ou en rectangle)
   public double maxRatio = Constante.PIXELMAXRATIO; // Rapport max tol�rable entre hauteur et largeur d'une image source
   protected boolean fading=false;           // Activation du fading entre les images originales
   protected boolean mixing=true;            // Activation du m�lange des pixels des images originales
   protected boolean fake=false;             // Activation du mode "just-print norun"
   protected boolean cdsLint=false;          // Activation du mode "cds" pour LINT (plus de v�rif)
   protected boolean flagRecomputePartitioning=true; // true si hipsgen doit d�terminer automatiquement la taille du partitionnement
   protected boolean partitioning=true;      // Activation de la lecture par blocs des fimages originales
   public String skyvalName;                 // Nom du champ � utiliser dans le header pour soustraire un valeur de fond (via le cacheFits)
   public double pourcentMin=-1;             // Pourcentage de l'info � garder en d�but d'histog. si autocut (ex: 0.003), -1 = d�faut
   public double pourcentMax=-1;             // Pourcentage de l'info � garder en fin d'histog. si autocut (ex: 0.9995), -1 = d�faut
   public String expTimeName;                // Nom du champ � utiliser dans le header pour diviser par une valeur (via le cacheFits)
   protected double coef;                    // Coefficient permettant le calcul dans le BITPIX final => voir initParameters()
   protected ArrayList<String> defaultFitsKey; // Liste des mots cl�s dont la valeur devra �tre m�moris�e dans les fichiers d'index JSON par d�faut
   private ArrayList<String> fitsKeys=null;  // Liste des mots cl�s dont la valeur devra �tre m�moris�e dans les fichiers d'index JSON explicite
   protected int typicalImgWidth=-1;         // Taille typique d'une image d'origine
   protected int mirrorDelay=0;              // d�lais entre deux r�cup�rartion de fichier lors d'un MIRROR (0 = sans d�lai)
   protected boolean notouch=false;          // true si on ne doit pas modifier la date du hips_release_date
   protected boolean mirrorCheck=true;      // true si on relance un hipsgen MIRROR => pas de v�rif sur les tuiles d�j� l�
   protected boolean hipslintTileTest=true;  // true pour faire un hipslint test sur des tuiles au hasard
   
   protected int bitpix = -1;                // BITPIX de sortie
   protected double blank = Double.NaN;      // Valeur du BLANK en sortie
   protected double bzero=0;                 // Valeur BZERO de la boule Healpix � g�n�rer
   protected double bscale=1;                // Valeur BSCALE de la boule HEALPix � g�n�rer
   //   protected boolean bscaleBzeroSet=false;   // true si le bScale/bZero de sortie a �t� positionn�s
   protected double[] cut;   // Valeurs cutmin,cutmax, datamin,datamax pour la boule Healpix � g�n�rer
   protected TransfertFct fct = TransfertFct.LINEAR; // Fonction de transfert des pixels fits -> jpg
   private JpegMethod jpegMethod = JpegMethod.MEDIAN;
   protected Mode mode=Mode.getDefault();   // Methode de traitement par d�faut
   protected int maxNbThread=-1;             // Nombre de threads de calcul max impos� par l'utilisateur
   protected String creator=null;          // Le nom de la personne qui a fait le HiPS
   protected String status=null;             // status du HiPs (private|public clonable|unclonable|clonableOnce)
   protected String hipsCheckCode=null;                // La ligne CRC associ� au HiPS
   protected boolean hipsCheckForce=false;     // true si on a le droit de recalculer les CRC
   protected String redInfo;                 // Information de colormap lors de la g�n�ration d'un HIPS RGB (composante red)
   protected String greenInfo;               // Information de colormap lors de la g�n�ration d'un HIPS RGB (composante green)
   protected String blueInfo;                // Information de colormap lors de la g�n�ration d'un HIPS RGB (composante blue)
   protected boolean gaussFilter=false;      // Filtrage gaussien lors de la g�n�ration d'un HiPS RGB (pour am�liorer le rendu du fond)
   protected int nbPilot=-1;                 // Indique le nombre d'images � prendre en compte (pour faire un test pilot)

   protected boolean flagLupton = false;         // M�thode Lupton ?
   protected double luptonQ = Double.NaN;;
   protected double [] luptonM = new double[] { Double.NaN, Double.NaN, Double.NaN };;                
   protected double [] luptonS= new double[] { Double.NaN, Double.NaN, Double.NaN };
   
   protected int order = -1;                 // Ordre maximal de la boule HEALPix � g�n�rer
   public int minOrder= -1;                  // Ordre minimal de la boule HEALPix � g�n�rer (valide uniquement pour les HiPS HpxFinder)
   private int frame =-1;                    // Syst�me de coordonn�e de la boule HEALPIX � g�n�r�e
   protected SMoc mocArea = null;        // Zone du ciel � traiter (d�crite par un MOC)
   protected SMoc mocIndex = null;       // Zone du ciel correspondant � l'index Healpix
   protected SMoc moc = null;            // Intersection du mocArea et du mocIndex => reg�n�r�e par setParameters()
   protected int mocOrder=-1;                // order du MOC des tuiles
   protected int mocTimeOrder=-1;            // time order du MOC
   protected long mocMaxSize=-1; //20*1024*1024;   // Taille max du MOC
   protected String mocRuleSize=null;        // R�gle de d�gradation de la r�solution d'un MOC limit� en taille
   protected int nside=1024;                 // NSIDE pour la g�n�ration d'une MAP healpix
   protected int tileOrder=-1;               // Valeur particuli�re d'un ordre pour les tuiles
   protected CacheFits cacheFits;            // Cache FITS pour optimiser les acc�s disques � la lecture
   protected Vector<String> keyAddProp=null; // Cl�s des propri�t�s additionnelles � m�moriser dans le fichier properties
   protected Vector<String> valueAddProp=null;// Valeurs des propri�t�s additionnelles � m�moriser dans le fichier properties
   protected String target=null;             // ra,de en deg du "centre" du HiPS s'il est indiqu�
   protected String targetRadius=null;       // radius en deg de la taille du premier champ HiPS � afficher
   protected String resolution=null;         // resolution en arcsec du pixel des images originales
   protected String scriptCommand;           // M�morisation de la commande  script
   protected int targetColorMode = Constante.TILE_PNG;       // Mode de compression des tuiles couleurs

   protected ArrayList<String> tileFormat=null;          // Liste des formats de tuiles � copier (mirror) s�par�s par un espace
   protected boolean testClonable=true;
   protected boolean live=false;             // true si on doit garder les tuiles de poids
   protected long bytes=0L;                 // Taille du HiPS g�n�r� en Kb

   public Context() { 
      
      // CE NE FONCTIONNE PAS BIEN, NOTAMMENT SOUS CYGWIN
      if( !(this instanceof ContextGui ) ) {
//         System.out.println("getenv().get(\"TERM\") => "+ System.getenv().get("TERM")+" System.console()="+System.console());
         if( /* System.console()!=null && */ System.getenv().get("TERM") != null) {
            setTerm(true);
        } else {
            setTerm(false);
        }
      }
   }

   public void reset() {
      mocArea=mocIndex=moc=null;
      mode=Mode.getDefault();
      hasAlternateBlank=false;
      blankOrig = Double.NaN;
      bscaleBzeroOrigSet=false;
      blankKey=null;
      imgEtalon=hpxFinderPath=inputPath=outputPath=null;
      isMap=false;
      prop=null;
      pixelGood=null;
      good=null;
      pixelRangeCut=null;
      depth=1;
      depthInit=false;
      crpix3=crval3=cdelt3=0;
      cunit3=null;
      tileFormat = null;
      outputRGB = null;
      redInfo=blueInfo=greenInfo=null;
      gaussFilter = false;
      plansRGB = new String [3];
      cmsRGB = new String [3];
      flagNoInitEtalon=false;
      tile2Hpx=null;
      flagLupton  = false;
      luptonQ     = Double.NaN;
      luptonM     = new double[] { Double.NaN, Double.NaN, Double.NaN };
      luptonS = new double[] { Double.NaN, Double.NaN, Double.NaN };
      bytes = 0L;
      resetProgressParam();
   }
   
   public void resetProgressParam() {
      lastNorder3=-2;
      live=validateOutputDone=validateInputDone=validateCutDone=validateRegion=false;
   }
   
   /** Ajoute au compteur de taille le volume indiqu� (en bytes) */
   public void incrBytes(long bytes ) { this.bytes += bytes; }
   
   /** Donne la taille du HiPS g�n�r�s (en bytes) */
   public long getBytes() { return bytes; }

   // manipulation des chaines d�signant le syst�me de coordonn�es (syntaxe longue et courte)
   static public String getFrameName(int frame) { return frame==Localisation.GAL ? "galactic"
         : frame==Localisation.ECLIPTIC ? "ecliptic" : frame==-1 ? "?" : "equatorial"; }
   static public String getFrameCode(int frame ) { return frame==Localisation.GAL ? "G"
         : frame==Localisation.ECLIPTIC ? "E" : frame==-1 ? "?" : "C"; }
   static public int getFrameVal( String frame) {
      frame = frame.toUpperCase();
      return (frame.equals("G") || frame.startsWith("GAL"))? Localisation.GAL:
         frame.equals("E") || frame.startsWith("ECL") ? Localisation.ECLIPTIC : Localisation.ICRS;
   }
   static public String getCanonicalFrameName( String s) { return getFrameName( getFrameVal(s)); }

   
   // Getters
   public String getLabel() {
      if( label==null ) return getLabelFromHipsId();
      return label;

   }
   public String getHipsId() { return hipsId; }
   public boolean getFading() { return fading; }
   public int[] getBorderSize() { return dataArea==Constante.SHAPE_UNKNOWN ?  borderSize : new int[]{0,0,0,0}; }
   public double getMaxRatio() { return maxRatio; }
   public int getOrder() { return order; }
   public boolean hasFrame() { return frame>=0; }
   public int getFrame() { return hasFrame() ? frame : Localisation.ICRS; }
   public String getFrameName() { return getFrameName( getFrame() ); }
   public String getFrameCode() { return getFrameCode( getFrame() ); }
   public CacheFits getCache() { return cacheFits; }
   public String getInputPath() { return inputPath; }
   public boolean getMirrorCheck() { return mirrorCheck; }
   public String getOutputPath() { return outputPath; }
   public String getSplit() { return split; }
   public String getHpxFinderPath() { return hpxFinderPath!=null ? hpxFinderPath : Util.concatDir( getOutputPath(),Constante.FILE_HPXFINDER); }
   public String getTimeFinderPath() { return timeFinderPath!=null ? hpxFinderPath : Util.concatDir( getOutputPath(),Constante.FILE_TIMEFINDER); }
   public String getImgEtalon() { return imgEtalon; }
   public int getBitpixOrig() { return bitpixOrig; }
   public int getBitpix() { return isColor() ? bitpixOrig : bitpix; }
   public int getNpix() { return isColor() || bitpix==-1 ? 4 : Math.abs(bitpix)/8; }  // Nombre d'octets par pixel
   public int getNpixOrig() { return isColor() || bitpixOrig==-1 ? 4 : Math.abs(bitpixOrig)/8; }  // Nombre d'octets par pixel
   public double getBScaleOrig() { return bScaleOrig; }
   public double getBZeroOrig() { return bZeroOrig; }
   public double getBZero() { return bzero; }
   public double getBScale() { return bscale; }
   public double getBlank() { return blank; }
   public double getBlankOrig() { return blankOrig; }
   public String getBlankKey() { return blankKey; }
   public boolean hasAlternateBlank() { return hasAlternateBlank; }
   public SMoc getArea() { 
      if( mocArea!=null ) mocArea.setSys( getFrameCode() );  // On s'assure qu'on exprime bien le MOC dans le bon frame
      return mocArea; 
   }
   public Mode getMode() { return mode; }
   public double[] getCut() throws Exception { return cut; }
   public double[] getCutOrig() throws Exception { return cutOrig; }
   public String getSkyval() { return skyvalName; }
   public boolean isColor() { return bitpixOrig==0; }
   public boolean isCube() { return depth>1; }
   public boolean isCubeCanal() { return crpix3!=0 || crval3!=0 || cdelt3!=0; }
   public boolean isInMocTree(int order,long npix)  { return moc==null || moc.isIntersecting(order,npix); }
   public boolean isInMoc(int order,long npix) { return moc==null || moc.isIntersecting(order,npix); }
   public boolean isMocDescendant(int order,long npix) { return moc==null || moc.isIncluding(order,npix); }
   public int getMaxNbThread() { return maxNbThread; }
   public int getMocOrder() { return mocOrder; }
   public int getTMocOrder() { return mocTimeOrder; }
   public long getMocMaxSize() { return mocMaxSize; }
   public String getMocRuleSize() { return mocRuleSize; }
   public long getMapNside() { return nside; }
   public int getMinOrder() {
      if( minOrder==-1 ) return 0;   // Le d�faut
      return minOrder;
   }
   public int getTileOrder() { return tileOrder==-1 ? Constante.ORDER : tileOrder; }
   public int getTileSide() { return (int) CDSHealpix.pow2( getTileOrder() ); }
   public int getDepth() { return depth; }
   public boolean isCDSLint() { return cdsLint; }
   public boolean isPartitioning() { return partitioning; }
   public int getPartitioning() { return Constante.ORIGCELLWIDTH; }

   
   
   // Setters
   public void setMirrorCheck(boolean flag) { this.mirrorCheck = flag; }
   public void setLive(boolean flag) { live=flag; }
   public void setFlagInputFile(boolean flag) { isInputFile=flag; }
   public void setHeader(HeaderFits h) { header=h; }
   public void setCreator(String s) { creator=s; }
   public void setStatus(String s ) { status=s; }
   public void setHipsId(String s) { hipsId= canonHipsId(s); }
   public void setLabel(String s)     { label=s; }
   public void setMaxNbThread(int max) { maxNbThread = max; }
   public void setFading(boolean fading) { this.fading = fading; }
   public void setFading(String s) { fading = s.equalsIgnoreCase("false") ? false : true; }
   public void setMixing(String s) { mixing = s.equalsIgnoreCase("false") ? false : true; }
   public void setPartitioning(String s) {
      try {
         int val = Integer.parseInt(s);
         Constante.ORIGCELLWIDTH = val;
         partitioning = true;
         flagRecomputePartitioning = false;
      } catch( Exception e ) {
         partitioning = s.equalsIgnoreCase("false") ? false : true;
      }
   }
//   public void setCircle(String r) throws Exception { this.circle = Integer.parseInt(r); }
   public void setMaxRatio(String r) throws Exception { maxRatio = Double.parseDouble(r); }
   public void setBorderSize(String borderSize) throws ParseException { this.borderSize = parseBorderSize(borderSize); }
   public void setBorderSize(int[] borderSize) { this.borderSize = borderSize; }
   public void setOrder(int order) { this.order = order; }
   public void setMinOrder(int minOrder) { this.minOrder = minOrder; }
   public void setMocOrder(int mocOrder) { this.mocOrder = mocOrder; }
   public void setMocOrder(String s) {
      int slash = s.indexOf('/' );
      int end = s.length();
      int min = s.indexOf('<');
      boolean inf=min>=0;
      if( min==-1 ) min=end;
      
      // Une expression sous la forme  spaceOrder/timeOrder
      if( slash>=0 ) {
         try {
            mocOrder = Integer.parseInt(s.substring(0,slash));
         } catch( Exception e ) {
            mocOrder=-1;
         }
         mocTimeOrder = Integer.parseInt(s.substring(slash+1,min));
         slash=min;
         
      // Le spaceOrder uniquement (syntaxe la plus simple)
      } else if( !inf ) {
         mocOrder = Integer.parseInt( s.substring(0,min).trim());
      }
      
      // Une expression sous la forme <20MB:tttss
      if( min<end ) {
         int point = s.indexOf(':');
         if( point==-1 ) point=end;
         while( s.charAt(min)==' ' && min<end ) min++;
         String s1 = s.substring(min,point);
         double fact=1.;
         int unit = s1.indexOf("KB");
         if( unit>0 ) fact=1024.;
         else {
            unit = s1.indexOf("MB");
            if( unit>0 ) fact=1024.*1024.;
            else {
               unit = s1.indexOf("GB");
               if( unit>0 ) fact=1024.*1024.*1024.;
            }
         }
        
         // extraction du mocMaxSize
         if( unit>0 ) {
            mocMaxSize = (long)(  Double.parseDouble( s1.substring(1,unit).trim() ) * fact );
         }
         
         // Extraction de la r�gle de d�gradation
         if( point<end ) {
            mocRuleSize = s.substring(point+1,end);
         }
      } 
      
      
   }
   public void setMapNside(int nside) { this.nside = nside; }
   public void setTileOrder(int tileOrder) { this.tileOrder = tileOrder; }
   public void setFrame(int frame) { this.frame=frame; }
   public void setFrameName(String frame) { this.frame=getFrameVal(frame); }
   public void setFilter(String s) throws Exception {
      if( s.equalsIgnoreCase("gauss") )  gaussFilter=true;
      else throw new Exception("Unknown filter ["+s+"] (=> only \"gauss\" are presently supported)");
   }
   public void setSkyValName(String s ) {
      skyvalName=s;
      if( s==null ) return;
      if(s.equalsIgnoreCase("true") || s.equalsIgnoreCase("auto") ) {
         skyvalName="auto";
         info("Skyval automatical adjustement activated...");
      }
      else info("Skyval adjustement based on the FITS keyword ["+s+"]");
   }
   public int [] getHDU() { return hdu; }
   public void setHDU(String s) throws Exception { hdu = parseHDU(s); }

   public void setShape(String s) throws Exception {
      if( Util.indexOfIgnoreCase(s,"circle")>=0      || Util.indexOfIgnoreCase(s,"ellipse")>=0 ) {
         dataArea=Constante.SHAPE_ELLIPSE;
         info("Ellipse shape data area autodetection");
      }
      else if( Util.indexOfIgnoreCase(s,"square")>=0 || Util.indexOfIgnoreCase(s,"rectangular")>=0 ) {
         dataArea=Constante.SHAPE_RECTANGULAR;
         info("Rectangular shape data area autodetection");
      }
      else {
         dataArea=Constante.SHAPE_UNKNOWN;
         throw new Exception("Unknown observation shape ["+s+"] (=> circle, ellipse, square, rectangular)");
      }
   }
   
   
   private String addendum_id=null;
   public void setAddendum(String addId) { addendum_id=addId; }
   public void addAddendum(String addId) throws Exception {
      if( addId.equals(hipsId) ) throw new Exception("Addendum_id identical to the original HiPS ID ["+hipsId+"]");
      if( addendum_id==null ) addendum_id=addId;
      else {
         Tok tok = new Tok(addendum_id,"\t");
         while( tok.hasMoreTokens() ) { if( tok.nextToken().equals(addId) ) throw new Exception("Addendum_id already applied  ["+addendum_id+"]"); }
         addendum_id += "\t"+addId;
      }
   }

   public String getRgbOutput() { return getOutputPath(); }
   public JpegMethod getHierarchyAlgo() { return getJpegMethod(); }
   public int getRgbFormat() { return targetColorMode; }

   public void setFov(String r) throws Exception {
      scanFov = r.equalsIgnoreCase("true") || (globalShape = createFov(r))!=null;
      if( scanFov ) info("FoV files associated to the original images");
   }
   
   // retourne l'identificateur du HiPS � partir des propri�t�s pass�es en param�tre
   public String getIdFromProp(MyProperties prop) {
      if( prop==null ) return null;
      String s = prop.getProperty(Constante.KEY_CREATOR_DID);
      if( s!=null ) return s;
      s = prop.getProperty(Constante.KEY_PUBLISHER_DID);
      if( s!=null ) return s;
      s = prop.getProperty(Constante.KEY_OBS_ID);
      if( s==null ) return null;
      String creator = prop.getProperty(Constante.KEY_CREATOR_ID);
      if( creator==null ) creator = prop.getProperty(Constante.KEY_PUBLISHER_ID);
      if( creator==null ) creator="ivo://UNK.AUT";
      return creator+"?"+s;
   }


   /** V�rifie l'ID pass� en param�tre, et s'il n'est pas bon le met en forme
    * @param s ID propos�e, null si g�n�ration automatique
    * @param withException true si on veut avoir une exception en cas d'erreur
    * @return l'ID canonique
    */
   public String canonHipsId(String s) {
      try {
         s=checkHipsId(s,false);
      } catch( Exception e ) { }
      return s;
   }
   public String checkHipsId(String s ) throws Exception { return checkHipsId(s,true); }
   private String checkHipsId(String s,boolean withException) throws Exception {

      String auth,id;
      boolean flagQuestion=false;  // true si l'identificateur utilise un ? apr�s l'authority ID, et non un /
      
      if( s==null && prop!=null )  s = getIdFromProp(prop);
      
      if( s==null || s.trim().length()==0 ) {
         verbose=false;
         s=getLabel()!=null?getLabel():"";
         if( withException ) throw new Exception("Missing ID (ex: creator_did=CDS/P/DSS2/color)");
      }

      if( s.startsWith("ivo://")) s=s.substring(6);

      // Check de l'authority
      int offset = s.indexOf('/');
      int offset1 = s.indexOf('?');
      if( offset>=0 || offset1>=0) {
         if( offset==-1 ) offset=s.length();
         if( offset1==-1 ) offset1=s.length();
         if( offset1<offset ) { offset=offset1; flagQuestion=true; }
         else flagQuestion=false;
      }
      
      if( offset==-1) {
         auth="UNK.AUTH";
//         if( verbose ) warning("Id error => missing authority => assuming "+auth);
         if( withException ) throw new Exception("ID error => missing authority (ex: creator_did=CDS/P/DSS2/color)");

      } else {
         auth = s.substring(0,offset);
         s=s.substring(offset+1);
         if( auth.length()<3) {
            while( auth.length()<3) auth=auth+"_";
//            if( verbose ) warning("Creator ID error => at least 3 characters are required => assuming "+auth);
            if( withException ) throw new Exception("ID error => at least 3 authority characters are required (ex: creator_did=CDS/P/DSS2/color)");
         }
         StringBuilder a = new StringBuilder();
         boolean bug=false;
         for( char c : auth.toCharArray()) {
            if( !Character.isLetterOrDigit(c) && c!='.' && c!='-' ) { c='.'; bug=true; }
            a.append(c);
         }
         if( bug ) {
            auth=a.toString();
//            if( verbose ) warning("Creator ID error => some characters are not allowed => assuming "+auth);
            if( withException ) throw new Exception("ID error => some characters are not allowed (ex: creator_did=CDS/P/DSS2/color)");
         }
      }

      // Check de l'identifier
      id=s.trim();
      if( id.startsWith("P/") || id.startsWith("C/")) id=id.substring(2);

      if( id.length()==0) {
         id="ID"+(System.currentTimeMillis()/1000);
//         if( verbose ) warning("Id error => missing ID => assuming "+id);
         if( withException ) throw new Exception("ID error: suffix Id missing (ex: creator_did=CDS/P/DSS2/color)");
      } else {
         StringBuilder a = new StringBuilder();
         boolean bug=false;
         for( char c : id.toCharArray()) {
            if( Character.isSpaceChar(c) ) { c='-'; bug=true; }
            a.append(c);
         }
         if( bug ) {
            id=a.toString();
//            if( verbose ) warning("Id identifier error => some characters are not allowed => assuming "+id);
            if( withException ) throw new Exception("ID suffix error: some characters are not allowed (ex: creator_did=CDS/P/DSS2/color)");
         }
      }

      String mode = isCube() ? "C": "P";

      return "ivo://"+auth+(flagQuestion?"?":"/")+mode+"/"+id;
   }

   /** retourne un label issu de l'ID du HiPS */
   public String getLabelFromHipsId() { return getLabelFromHipsId( hipsId ); }
   public String getLabelFromHipsId( String hipsId) {
      if( hipsId==null ) return null;
      String s = hipsId;
      if( s.startsWith("ivo://") ) s=s.substring(6);
      int offset = s.indexOf('/');
      int offset1 = s.indexOf('?');
      if( offset==-1 && offset1==-1 ) return null;
      if( offset==-1 ) offset=s.length();
      if( offset1==-1 ) offset1=s.length();
      offset = Math.min(offset1,offset);
      String s1 = s.substring(offset+1);
      if( s1.startsWith("P/") ) s1=s1.substring(2);
      s1=s1.replace('/',' ');
      return s1;
   }

   static public Shape createFov(String s) throws Exception {
      
      Tok tok = new Tok(s," ,;\t");
      
      // S'il y a 3 valeurs c'est un cercle (xc,yc,rayon)
      if( tok.countTokens()==3 ) {
         double x =  Double.parseDouble(tok.nextToken());
         double y =  Double.parseDouble(tok.nextToken());
         double r =  Double.parseDouble(tok.nextToken());
         return new Ellipse2D.Double(x-r, y-r, r*2, r*2);
      }
      
      
      // sinon c'est un polygone
      Polygon p = new Polygon();
      while( tok.hasMoreTokens()) {
         int x = (int)( Double.parseDouble(tok.nextToken()) +0.5);
         int y = (int)( Double.parseDouble(tok.nextToken()) +0.5);
         p.addPoint(x, y);
      }
      return p;
   }

   /** Indication des types de tuiles � copier lors d'une action MIRROR */
   protected void setTileFormat(String s) {
      Tok tok = new Tok(s);
      while( tok.hasMoreTokens() ) addTileFormat(tok.nextToken());
   }

   /** M�morisation d'une extension pour le mirroring HiPS (MIRROR).
    * Ajoute le '.' en pr�fixe, sauf si l'extension est vide */
   protected void addTileFormat(String s) {
      if( s.equalsIgnoreCase("jpeg") ) s="jpg";
      if( tileFormat==null ) tileFormat = new ArrayList<>();
      tileFormat.add( s.length()==0 ? s : "."+s.toLowerCase() );
   }

   /** Retourne la liste des formats de tuiles mirror�es */
   protected String getTileFormat() {
      if( tileFormat==null ) return null;
      StringBuilder format = new StringBuilder();
      for( String s :tileFormat) {
         if( s.length()==0 ) continue;
         if( format.length()>0 ) format.append(' ');
         if( s.equals(".jpg")) format.append("jpeg");
         else format.append(s.substring(1));
      }
      return format.toString();
   }


   // Construit le tableau des HDU � partir d'une syntaxe "1,3,4-7" ou "all"
   // dans le cas de all, retourne un tableau ne contenant que -1
   static public int [] parseHDU(String s) throws Exception {
      int [] hdu = null;
      if( s.length()==0 || s.equals("0") ) return hdu;
      if( s.equalsIgnoreCase("all") ) return new int[]{-1}; // Toutes les extensions images
      StringTokenizer st = new StringTokenizer(s," ,;-",true);
      ArrayList<Integer> a = new ArrayList<>();
      boolean flagRange=false;
      int previousN=-1;
      while( st.hasMoreTokens() ) {
         String s1=st.nextToken();
         if( s1.equals("-") ) { flagRange=true; continue; }
         else if( !Character.isDigit( s1.charAt(0) ) ) continue;
         int n = Integer.parseInt(s1);
         if( flagRange ) {
            for( int i=previousN+1; i<=n && i<1000; i++ ) a.add(i);
            flagRange=false;
         }  else a.add(n);
         previousN = n;
      }
      hdu = new int[a.size()];
      for( int i=0; i<hdu.length; i++ ) hdu[i]=a.get(i);
      return hdu;
   }

   public void setInputPath(String path)  { this.inputPath = path;  }
   public void setOutputPath(String path) { this.outputPath = path; }
   public void setSplit(String split) { this.split = split; }
   public void setImgEtalon(String filename) throws Exception { imgEtalon = filename; initFromImgEtalon(); }
   public void setIndexFitskey(String list) {
      StringTokenizer st = new StringTokenizer(list);
      fitsKeys = new ArrayList<>(st.countTokens());
      while( st.hasMoreTokens() ) fitsKeys.add(st.nextToken());
   }
   public void setMode(Mode coAdd) { this.mode = coAdd;  }
   public void setTarget(String target) { this.target = target; }
   public void setTargetRadius(String targetRadius) { this.targetRadius = targetRadius; }
   public void setBScaleOrig(double x) { bScaleOrig = x; bscaleBzeroOrigSet=true; }
   public void setBZeroOrig(double x) { bZeroOrig = x; bscaleBzeroOrigSet=true; }
   //   public void setBScale(double x) { bscale = x; bscaleBzeroSet=true; }
   //   public void setBZero(double x) { bzero = x; bscaleBzeroSet=true; }
   public void setBitpixOrig(int bitpixO) {
      this.bitpixOrig = bitpixO;
      if (this.bitpix==-1) this.bitpix = bitpixO;
   }
   public void setBitpix(int bitpix) { this.bitpix = bitpix; }
   public void setBlankOrig(double x) {  
      blankOrig = x;
      hasAlternateBlank=true;
   }
   public void setBlankOrig(String key ) { blankKey = key; hasAlternateBlank=false; }
   public void setColor(String colorMode) {
      if( colorMode.equalsIgnoreCase("false")) return;
      bitpixOrig=0;
      if( colorMode.equalsIgnoreCase("png")) targetColorMode=Constante.TILE_PNG;
      else targetColorMode=Constante.TILE_JPEG;
   }
   public void setCut(double [] cut) { this.cut=cut; }
   public void setPixelCut(String scut) throws Exception {
      StringTokenizer st = new StringTokenizer(scut," ");
      int i=0;
      if( pixelRangeCut==null ) pixelRangeCut = new double[]{Double.NaN,Double.NaN,Double.NaN,Double.NaN};
      while( st.hasMoreTokens() ) {
         String s = st.nextToken();
         try {
            pixelRangeCut[i]=Double.parseDouble(s);
            i++;
         } catch( Exception e) {
            setTransfertFct(s);
         }

      }
      if( i==1 || i>2 ) throw new Exception("pixelCut parameter error");
   }
   public void setPilot(int nbPilot) { this.nbPilot=nbPilot; }
   
   /** M�morisation de la chaine des check codes (ex: "png:452738951 fit:184622110") */
   public void setCheckCode( String hipscrc) { this.hipsCheckCode= hipscrc; }
   
   /** retourne la chaine des check codes (ex: "png:452738951 fit:184622110")
    * ou null si non connue */ 
   public String getCheckCode() { return hipsCheckCode; }
   
   /// true si on peut modifier les Check codes
   public void setCheckForce(boolean flag) { hipsCheckForce=flag; }
   public boolean getCheckForce() { return hipsCheckForce; }
   
   public void setPixelGood(String sGood) throws Exception {
      StringTokenizer st = new StringTokenizer(sGood," ");
      if( pixelGood==null ) pixelGood = new double[]{Double.NaN,Double.NaN};
      try {
         pixelGood[0] = Double.parseDouble(st.nextToken());
         if( st.hasMoreTokens() ) pixelGood[1] = Double.parseDouble(st.nextToken());
         else pixelGood[1] = pixelGood[0];
      } catch( Exception e ) { throw new Exception("pixelGood parameter error"); }
   }
   
   /** positionnement des pourcentages pour le cut de l'histogramme, soit
    * sous la forme d'une seule valeur (pourcentage centrale retenue => ex:99)
    * soit sous la forme de deux valeurs (pourcentage min et pourcentage max
    * ex => 0.3 et 99.7
    * @param sHist
    * @throws Exception
    */
   public void setHistoPercent(String sHist) throws Exception {
      StringTokenizer st = new StringTokenizer(sHist," ");
      int n = st.countTokens();
      
      try {
         if( n>2 ) throw new Exception();
         pourcentMin = Double.parseDouble(st.nextToken())/100.;
         
         // Une seule valeur => repr�sente le pourcentage central retenue
         // ex: 99 => pourcentMin=0.005 et pourcentMax=0.995
         if( n==1 ) {
            pourcentMin = (1-pourcentMin)/2;
            pourcentMax = 1-pourcentMin;
            
         // Deux valeurs => repr�sente le pourcentMin et pourcentMax directement
         } else {
            pourcentMax = Double.parseDouble(st.nextToken())/100;
         }
      } catch( Exception e ) { throw new Exception("histoPercent parameter error"); }
   }


   public double [] getPixelRangeCut() throws Exception { return pixelRangeCut; }


   public TransfertFct getFct() throws Exception { return fct; }
   public String getTransfertFct()  throws Exception { return getFct().toString().toLowerCase(); }

   public void setTransfertFct(String txt) {
      this.fct=TransfertFct.valueOf(txt.toUpperCase());
   }

   /** Donne l'extension des fichiers losanges */
   public String getTileExt() {
      return isColor() ? Constante.TILE_EXTENSION[ targetColorMode ] : ".fits";
   }

   protected enum JpegMethod { MEDIAN, MEAN, FIRST; }

   /**
    * @param jpegMethod the method to set
    * @see Context#MEDIAN
    * @see Context#MEAN
    */
   public void setJpegMethod(JpegMethod jpegMethod) {
      this.jpegMethod = jpegMethod;
   }
   public void setMethod(String jpegMethod) {
      this.jpegMethod = JpegMethod.valueOf(jpegMethod.toUpperCase());
   }
   public JpegMethod getJpegMethod() { return jpegMethod; }

   public void setDataCut(String scut) throws Exception {
      StringTokenizer st = new StringTokenizer(scut," ");
      int i=2;
      //      if( cut==null ) cut = new double[4];
      if( pixelRangeCut==null ) pixelRangeCut = new double[]{Double.NaN,Double.NaN,Double.NaN,Double.NaN};
      while( st.hasMoreTokens() && i<4 ) {
         String s = st.nextToken();
         pixelRangeCut[i]=Double.parseDouble(s);
         i++;
      }
      if( i<4 ) throw new Exception("Missing dataCut parameter");
      
      // BIZARRE !
//      setCutOrig(cutOrig);
   }


   public void setCutOrig(double [] cutOrig) {
      this.cutOrig=cutOrig;
   }

   /** Initialisation de la profondeur d'un cube */
   public void setDepth(int depth) {
      this.depth=depth;
      depthInit=true;
   }

   private String lastImgEtalon = null;

   /**
    * Lit l'image etalon, et affecte les donn�es d'origines (bitpix, bscale, bzero, blank, cut)
    * @throws Exception s'il y a une erreur � la lecture du fichier
    */
   protected void initFromImgEtalon() throws Exception {
      
      // D�ja fait ,
      if( lastImgEtalon!=null && lastImgEtalon.equals(imgEtalon)) return;

      String path = imgEtalon;
      Fits fitsfile = new Fits();
      int code = fitsfile.loadHeaderFITS( path );
      
      // Lupton
      if( (code&cds.fits.Fits.LUPTON)!=0 ) {
         info("Lutpon BOFFSET/BSOFTEN detected => default output bitpix -32");
         setBitpixOrig(-32);
         
      } else {
         setBitpixOrig(fitsfile.bitpix);
         if( !isColor() ) {
            setBZeroOrig(fitsfile.bzero);
            setBScaleOrig(fitsfile.bscale);
            if( !Double.isNaN(fitsfile.blank) ) setBlankOrig(fitsfile.blank);
         }
      }

      // M�morise la taille typique de l'image �talon
      typicalImgWidth = Math.max(fitsfile.width,fitsfile.height);
      
      // Peut �tre s'agit-il d'un cube ?
      try {
         setDepth( fitsfile.headerFits.getIntFromHeader("NAXIS3") );

         try {
            crpix3 = fitsfile.headerFits.getDoubleFromHeader("CRPIX3");
            crval3 = fitsfile.headerFits.getDoubleFromHeader("CRVAL3");
            cdelt3 = fitsfile.headerFits.getDoubleFromHeader("CDELT3");
            cunit3 = fitsfile.headerFits.getStringFromHeader("CUNIT3");
         }catch( Exception e ) { crpix3=crval3=cdelt3=0; cunit3=null; }

      } catch( Exception e ) { setDepth(1); }

      // Il peut s'agir d'un fichier .hhh (sans pixel)
      try { initCut(fitsfile); } catch( Exception e ) {
         Aladin.trace(4,"initFromImgEtalon :"+ e.getMessage());
      }

      // Positionnement initiale du HiPS par d�faut
      if( target==null ) {
         Coord c = fitsfile.calib.getImgCenter();
         String s = Util.round(c.al,5)+" "+(c.del>=0?"+":"")+Util.round(c.del,5);
         setTarget(s);
         info("Set default target => "+s);
         if( targetRadius==null ) {
            double r = Math.max( fitsfile.calib.getImgHeight(),fitsfile.calib.getImgWidth());
            setTargetRadius(Util.round(r,5)+"");
         }
      }

      // M�morisation de la r�solution initiale
      double [] res = fitsfile.calib.GetResol();
      resolution = Util.myRound(Math.min(res[0],res[1]));
      
      
      lastImgEtalon = imgEtalon;
   }
   
   // Retourne la liste des Fits Keys de l'ent�te qui matchent la liste par d�faut.
   // Si aucun retourne null
   protected ArrayList<String> scanDefaultFitsKey( HeaderFits h ) {
      ArrayList<String> a = new ArrayList<>();
      Enumeration<String> e = h.getKeys();
      while( e.hasMoreElements() ) {
         String s = e.nextElement();
         if( Util.indexInArrayOf(s, FITSKEYS)>=0 ) a.add( s );
      }
      return a.size()>0 ? a : null;
   }

   /**
    * Lit l'image et calcul son autocut : affecte les datacut et pixelcut *Origines*
    * @param file
    */
   protected void initCut(Fits file) throws Exception {
      int w = file.width;
      int h = file.height;
      int d = file.depth;
      int x=0, y=0, z=0;
      if (w > 1024) { w = 1024; x=file.width/2 - 512; }
      if (h > 1024) { h = 1024; y=file.height/2 -512; }
      if (d > 1 ) { d = 1;  z=file.depth/2 - 1/2; }
      if( file.getFilename()!=null ) file.loadFITS(file.getFilename(), file.getExt(), x, y, z, w, h, d);
      
      if( !flagNoInitEtalon ) {

         double[] cutOrig = file.findAutocutRange();

         //       cutOrig[2]=cutOrig[3]=0;  // ON NE MET PAS LE PIXELRANGE, TROP DANGEREUX... // J'HESITE DE FAIT !!!

         // PLUTOT QUE DE NE PAS INITIALISER, ON VA DOUBLER LA TAILLE DE L'INTERVALLE (sans d�passer les limites)
         double rangeData   = cutOrig[3] - cutOrig[2];
         double centerRange = cutOrig[2]/2 + cutOrig[3]/2;
         if( !Double.isInfinite( centerRange-rangeData ) ) cutOrig[2] = centerRange-rangeData;
         if( !Double.isInfinite( centerRange+rangeData ) ) cutOrig[3] = centerRange+rangeData;
         
         double max = Fits.getMax(file.bitpix);
         double min = Fits.getMin(file.bitpix);
         if( cutOrig[2]<min ) cutOrig[2]=min;
         if( cutOrig[3]>max ) cutOrig[3]=max;

         setCutOrig(cutOrig);
      }
   }

   static private int nbFiles;  // nombre de fichiers scann�s
   /**
    * S�lectionne un fichier de type FITS (ou �quivalent) dans le r�pertoire donn� => va servir d'�talon
    * @return true si trouv�
    */
   boolean findImgEtalon(String rootPath) {
      if( isInputFile ) {
         try {
            setImgEtalon(rootPath);
         }  catch( Exception e) { return false; }
         return true;
      }
      nbFiles=0;
      return findImgEtalon1(rootPath);
   }
   boolean findImgEtalon1(String rootPath) {
      File main = new File(rootPath);
      String[] list = main.list();
      if( list==null ) return false;
      String path = rootPath;

      ArrayList<String> dir = new ArrayList<>();

      for( int f = 0 ; f < list.length ; f++ ) {
         if( !rootPath.endsWith(Util.FS) ) rootPath = rootPath+Util.FS;
         path = rootPath+list[f];

         if( (new File(path)).isDirectory() ) {
            if( !list[f].equals(Constante.SURVEY) ) dir.add(path);
            continue;
         }

         nbFiles++;
         if( nbFiles>100 ) {
            Aladin.trace(4, "Context.findImgEtalon: too many files - ignored this step...");
            return false;
         }

         // essaye de lire l'entete fits du fichier
         // s'il n'y a pas eu d'erreur �a peut servir d'�talon
         MyInputStream in = null;
         try {
            in = (new MyInputStream( new FileInputStream(path))).startRead();
            if( (in.getType()&MyInputStream.FITS) != MyInputStream.FITS && !in.hasCommentCalib() ) continue;
            Aladin.trace(4, "Context.findImgEtalon: "+path+"...");
            setImgEtalon(path);
            return true;

         }  catch( Exception e) { Aladin.trace(4, "findImgEtalon : " +e.getMessage()); continue; }
         finally { if( in!=null ) try { in.close(); } catch( Exception e1 ) {} }
      }

      for( String s : dir ) {
         if( findImgEtalon1(s) ) return true;
      }

      return false;
   }

   String justFindImgEtalon(String rootPath) throws MyInputStreamCachedException {
      MyInputStream in = null;
      
      if( isInputFile ) {
         
//         // Cas particulier d'une image couleur avec un fichier .hhh qui l'accompagne
//         if( isColor() ) {
//            String file = rootPath;
//            int offset = file.lastIndexOf('.');
//            if( offset!=-1 )  file = file.substring(0,offset);
//            file += ".hhh";
//            if( (new File(file).exists()) ) rootPath=file;
//         }
         
         return rootPath;
      }

      File main = new File(rootPath);
      String[] list = main.list();
      if( list==null ) return null;
      String path = rootPath;

      ArrayList<String> dir = new ArrayList<>();

      for( int f = 0 ; f < list.length ; f++ ) {
         if( !rootPath.endsWith(Util.FS) ) rootPath = rootPath+Util.FS;
         path = rootPath+list[f];

         if( (new File(path)).isDirectory() ) {
            if( !list[f].equals(Constante.SURVEY) ) dir.add(path);
            continue;
         }

         // essaye de lire l'entete fits du fichier
         // s'il n'y a pas eu d'erreur �a peut servir d'�talon
         try {
            // cas particulier d'un survey couleur en JPEG ou PNG avec calibration externe
            if( path.endsWith(".hhh") ) return path;

//            in = (new MyInputStream( new FileInputStream(path)) ).startRead();
            in = (new MyInputStreamCached(path, getHDU()) ).startRead();
            
            long type = in.getType();
            if( (type&MyInputStream.FITS) != MyInputStream.FITS && !in.hasCommentCalib() ) continue;
            return path + (hdu==null || hdu.length>0 && hdu[0]==-1 ? "":"["+hdu[0]+"]");

         }  
         catch( MyInputStreamCachedException e) { taskAbort(); throw e; }
         catch( Exception e) {
            e.printStackTrace();
            Aladin.trace(4, "justFindImgEtalon : " +e.getMessage());
            continue;
         }
         finally { if( in!=null ) try { in.close(); } catch( Exception e1 ) {} }
      }

      for( String s : dir ) {
         String rep = justFindImgEtalon(s);
         if( rep!=null ) return rep;
      }
      return null;
   }

   protected String outputRGB;
   protected JpegMethod hierarchyAlgo;
   protected String [] plansRGB = new String [3];
   protected String [] cmsRGB = new String [3];

   public void setRgbInput(String path,int c) { plansRGB[c] = path; }
   public void setRgbCmParam(String cmParam,int c) { cmsRGB[c] = cmParam; }
   public void setRgbLuptonQ(String s) throws Exception { setRgbLuptonParam(s,0); }
   public void setRgbLuptonM(String s) throws Exception { setRgbLuptonParam(s,1); }
   public void setRgbLuptonS(String s) throws Exception { setRgbLuptonParam(s,2); }
   
   /** Parsing des param�tres Lupton
    * 0: Q => une valeur unique
    * 1: m => "auto" ou "val" ou "val/val/val" avec val=num�rique ou auto ou vide
    * 2: idem que 1
    * @param s la valeur du param�tre
    * @param param le num�ro du parametre
    * @throws Exception
    */
   private void setRgbLuptonParam(String s,int param) throws Exception {
      flagLupton = true;
      try {
         
         // 0 - Q
         if( param==0 ) {
            try { luptonQ = Double.parseDouble(s); } catch( Exception e ) { luptonQ=Double.NaN; }
            return;
         }

         // 1 - m, 2 - scale
         double [] lup = param==1 ? luptonM : luptonS;
         Tok tok = new Tok(s,"/");
         double lastVal=Double.NaN;
         for( int i=0;i<3; i++ ) {
            if( tok.hasMoreTokens() ) {
               String s1 = tok.nextToken();
               if( s1.equals("auto") || s1.length()==0 ) lup[i]=Double.NaN;
               else lup[i]=Double.parseDouble(s1);
            } else lup[i]=lastVal;
            lastVal = lup[i];
         }
         
      } catch( Exception e) {
         throw new Exception("lupton param error ["+s+"] (ex: luptonQ=20 luptonS=auto luptonM=0.02/0.03/0.01)");
      }
   }

   public void setSkyval(String fieldName) throws Exception {
      boolean flagNum = false;
      
      // S'agit-il de valeurs num�riques pour indiquer un
      // pourcentage de l'histogramme � conserver ?
      try {
         StringTokenizer st = new StringTokenizer(fieldName);
         Double.parseDouble( st.nextToken() );
         flagNum = true;
      } catch( Exception e ) { }
      
      // Va pour les valeurs num�riques
      if( flagNum ) {
         this.skyvalName = "auto";
         setHistoPercent(fieldName);
         
      // Simple mot cl�
      } else {
         this.skyvalName = fieldName.toUpperCase();
      }
   }
   
   /** Postionnement direct des valeurs du skyval, notamment pour un CONCAT
    * @param s contient les 4 valeurs du cutOrig[]
    * @throws Exception
    */
   public void setSkyValues(String s) throws Exception {
      Tok tok = new Tok(s);
      cutOrig = new double[4];
      for( int i=0; tok.hasMoreTokens(); i++ ) {
         try { cutOrig[i] = Double.parseDouble( tok.nextToken() );
         } catch( Exception e) { throw new Exception("hips_skyval_values parsing error ["+s+"]"); }
      }
      flagNoInitEtalon=true;
   }

   public void setExpTime(String expTime) {
      this.expTimeName = expTime.toUpperCase();
   }

   public void setCache(CacheFits cache) {
      this.cacheFits = cache;
      cache.setContext(this);
   }

   protected void setMocArea(String s) throws Exception {
      if( s.length()==0 ) return;
      mocArea = new SMoc(s);
      if( mocArea.isEmpty() ) throw new Exception("MOC sky area syntax error");
   }

   public void setMocArea(SMoc area) throws Exception {
      mocArea = area;
   }

   public double getSkyArea() {
      if( moc==null ) return 1;
      return moc.getCoverage();
   }
   
   public ArrayList<String> getFitsKeys() {
      if( fitsKeys!=null ) return fitsKeys;
      return defaultFitsKey;
   }

   public double getIndexSkyArea() {
      if( mocIndex==null ) return 1;
      return mocIndex.getCoverage();
   }

   /** Initialisation des param�tres */
   public void initParameters() throws Exception {

      if( !isColor() ) {
         bitpix = getBitpix();

         bitpixOrig = getBitpixOrig();
         cutOrig = getCutOrig();
         blankOrig = getBlankOrig();
         //         bZeroOrig = getBZeroOrig();
         //         bScaleOrig = getBScaleOrig();

         // Le blank de sortie est impos�e
         blank = getDefaultBlankFromBitpix(bitpix);

         // le cut de sortie est par d�faut le m�me que celui d'entr�e
         cut = new double[5];
         if( cutOrig==null ) cutOrig = new double[5];
         System.arraycopy(cutOrig, 0, cut, 0, cutOrig.length);

         // si les dataCut d'origine sont nuls ou incorrects, on les mets au max
         if( cutOrig[2]>=cutOrig[3] ) {
            cutOrig[2] = bitpixOrig==-64?-Double.MAX_VALUE : bitpixOrig==-32? -Float.MAX_VALUE
                  : bitpixOrig==64?Long.MIN_VALUE+1 : bitpixOrig==32?Integer.MIN_VALUE+1 : bitpixOrig==16?Short.MIN_VALUE+1:1;
            cutOrig[3] = bitpixOrig==-64?Double.MAX_VALUE : bitpixOrig==-32? Float.MAX_VALUE
                  : bitpixOrig==64?Long.MAX_VALUE : bitpixOrig==32?Integer.MAX_VALUE : bitpixOrig==16?Short.MAX_VALUE:255;
         }

         // Y a-t-il un changement de bitpix ?
         // Les cuts changent
         if( bitpixOrig!=-1 && bitpix != bitpixOrig ) {
            cut[2] = bitpix==-64? -Double.MAX_VALUE :
                     bitpix==-32? -Float.MAX_VALUE : 
                     bitpix==64?   Long.MIN_VALUE+1 :
                     bitpix==32?   Integer.MIN_VALUE+1 :
                     bitpix==16?   Short.MIN_VALUE+1 : 
                     1;
            cut[3] = bitpix==-64? Double.MAX_VALUE :
                     bitpix==-32? Float.MAX_VALUE :
                     bitpix==64?  Long.MAX_VALUE :
                     bitpix==32?  Integer.MAX_VALUE :
                     bitpix==16?   Short.MAX_VALUE :
                     255;
            
//            double plageOrig = cutOrig[3]-cutOrig[2];
//            if( bitpixOrig>0 ) plageOrig++;   // [0..N] => N+1 valeurs possibles contrairement aux r�elles
//            double plage = cut[3]-cut[2];
//            if( bitpix>0 ) plage++;
//            coef = plage / plageOrig;
               coef = (cut[3]-cut[2]) / (cutOrig[3]-cutOrig[2]);

            cut[0] = (cutOrig[0]-cutOrig[2])*coef + cut[2];
            cut[1] = (cutOrig[1]-cutOrig[2])*coef + cut[2];

            bzero = bZeroOrig + bScaleOrig*(cutOrig[2] - cut[2]/coef);
            bscale = bScaleOrig/coef;


            info("Change BITPIX from "+bitpixOrig+" to "+bitpix);
            info("Map original pixel range ["+cutOrig[2]+" .. "+cutOrig[3]+"] " +
                  "to ["+cut[2]+" .. "+cut[3]+"]");
            info("Change BZERO,BSCALE,BLANK="+bZeroOrig+","+bScaleOrig+","+blankOrig
                  +" to "+bzero+","+bscale+","+blank);

            if( Double.isInfinite(bzero) || Double.isInfinite(bscale) ) throw new Exception("pixelRange parameter required !");

            // Pas de changement de bitpix
         } else {
            bzero=bZeroOrig;
            bscale=bScaleOrig;
            Aladin.trace(3,"BITPIX kept "+bitpix+" BZERO,BSCALE,BLANK="+bzero+","+bscale+","+blank);
         }

         // Calcul des valeurs raw des good pixels
         if( pixelGood!=null ) {
            good = new double[2];
            good[0] = (pixelGood[0]-bZeroOrig)/bScaleOrig;
            good[1] = (pixelGood[1]-bZeroOrig)/bScaleOrig;
         }

      }

      // D�termination de la zone du ciel � calculer
      initRegion();
   }

   /** D�termination de la zone du ciel � calculer (appeler par initParameters()) ne pas utiliser tout
    * seul sauf si besoin explicite */
   protected void initRegion() throws Exception {
      if( isValidateRegion() ) return;
      
      try {
         if( mocIndex==null ) {
            if( isMap() )  mocIndex=new SMoc("0/0-11"); 
            else loadMocIndex();
         }
      } catch( Exception e ) {
         //         warning("No MOC index found => assume all sky");
         mocIndex=new SMoc("0/0-11"); 
      }
      SMoc mocArea = getArea();
      if( mocArea==null ) moc = mocIndex;
      else moc = mocIndex.intersection(mocArea);
      setValidateRegion(true);
   }

   /** Retourne la zone du ciel � calculer */
   protected SMoc getRegion() { return moc; }

   /** Chargement du MOC de l'index */
   protected void loadMocIndex() throws Exception {
      SMoc mocIndex = new SMoc();
      mocIndex.read( getHpxFinderPath()+Util.FS+Constante.FILE_MOC);
      this.mocIndex=mocIndex;
   }

   /** Chargement du MOC r�el */
   protected void loadMoc() throws Exception {
      SMoc mocIndex = new SMoc();
      mocIndex.read( getOutputPath()+Util.FS+Constante.FILE_MOC);
      this.mocIndex=mocIndex;
   }

   protected SMoc getMocIndex() { return mocIndex; }

   //   /** Positionne les cuts de sortie en fonction du fichier Allsky.fits
   //    * @return retourn le cut ainsi calcul�
   //    * @throws Exception
   //    */
   //   protected double [] setCutFromAllsky() throws Exception {
   //      double [] cut = new double[4];
   //      String fileName=getOutputPath()+Util.FS+"Norder3"+Util.FS+"Allsky.fits";
   //      try {
   //         if( !(new File(fileName)).exists() ) throw new Exception("No available Allsky.fits file for computing cuts");
   //         Fits fits = new Fits();
   //         fits.loadFITS(fileName);
   //         cut = fits.findAutocutRange(0, 0, true);
   //         info("setCut from Allsky.fits => cut=["+cut[0]+".."+cut[1]+"] range=["+cut[2]+".."+cut[3]+"]");
   //         setCut(cut);
   //      } catch( Exception e ) { throw new Exception("No available Allsky.fits file for computing cuts"); }
   //      return cut;
   //   }

   public boolean verifTileOrder() {

      // R�cup�ration d'un �ventuel changement de TileOrder dans les propri�t�s du HpxFinder
      InputStreamReader in = null;
      boolean flagTileOrderFound=false;
      try {
         String propFile = getHpxFinderPath()+Util.FS+Constante.FILE_PROPERTIES;
         MyProperties prop = new MyProperties();
         prop.load( in=new InputStreamReader( new FileInputStream( propFile )) );
         int o;
         String s = prop.getProperty(Constante.KEY_HIPS_TILE_WIDTH);
         if( s!=null ) o = (int)CDSHealpix.log2( Integer.parseInt(s));
         else o = Integer.parseInt( prop.getProperty(Constante.OLD_TILEORDER) );

         if( o!=getTileOrder() ) {
            if( tileOrder!=-1 && o!=tileOrder ) {
               warning("Uncompatible tileOrder="+tileOrder+" compared to pre-existing survey tileOrder="+o);
               return false;

            }
            setTileOrder(o);
            int w = getTileSide();
            info("Specifical tileOrder="+o+" tileSize="+w+"x"+w);
         }
         flagTileOrderFound=true;
      }
      catch( Exception e ) { }
      finally { if( in!=null ) { try { in.close(); } catch( Exception e ) {} } }

      // Si rien d'indiqu� dans Properties du HpxFinder, c'est que ce doit �tre l'ordre par d�faut
      if( !flagTileOrderFound ) {
         if( getTileOrder()!=Constante.ORDER ) {
            warning("Uncompatible tileOrder="+getTileOrder()+" compared to default pre-existing survey tileOrder="+Constante.ORDER);
            return false;

         }
      }

      return true;
   }

   public boolean verifFrame() {

      // R�cup�ration d'un �ventuel changement de hips_frame dans les propri�t�s du HpxFinder
      InputStreamReader in = null;
      boolean flagFrameFound=false;
      try {
         String propFile = getHpxFinderPath()+Util.FS+Constante.FILE_PROPERTIES;
         MyProperties prop = new MyProperties();
         prop.load( in=new InputStreamReader( new FileInputStream( propFile )) );
         int o=0;
         String s = prop.getProperty(Constante.KEY_HIPS_FRAME);
         if( s!=null ) {
            flagFrameFound=true;
            o = getFrameVal(s);
         }

         if( flagFrameFound ) {
            if( hasFrame() ) {
               if( o!=getFrame() ) {
                  warning("Uncompatible coordinate frame="+getFrameName()+" compared to pre-existing survey frame="+getFrameName(o));
                  return false;
               }
            } else {
               setFrame(o);
            }
         }
      } catch( Exception e ) { }
      finally { if( in!=null ) { try { in.close(); } catch( Exception e ) {} } }

      return true;
   }

   public boolean verifCoherence() {

      if( !verifFrame() ) return false;
      if( !verifTileOrder() ) return false;

      if( mode==Mode.REPLACETILE ) return true;

      if( !isColor() ) {
         String fileName=getOutputPath()+Util.FS+"Norder3"+Util.FS+"Allsky.fits";
         if( !(new File(fileName)).exists() ) return true;
         Fits fits = new Fits();
         try { fits.loadHeaderFITS(fileName); }
         catch( Exception e ) { return true; }
         if( fits.bitpix!=bitpix ) {
            warning("Uncompatible BITPIX="+bitpix+" compared to pre-existing survey BITPIX="+fits.bitpix);
            return false;
         }
         boolean nanO = Double.isNaN(fits.blank);
         boolean nan = Double.isNaN(blank);

         // Cas particulier des Survey pr�existants sans BLANK en mode entier. Dans ce cas, on accepte
         // tout de m�me de traiter en sachant que le blank d�fini par l'utilisateur sera
         // consid�r� comme celui du survey existant. Mais il faut n�cessairement que l'utilisateur
         // renseigne ce champ blank explicitement
         if( bitpix>0 && nanO ) {
            nan = !Double.isNaN(getBlankOrig());
         }

         if( nanO!=nan || !nan && fits.blank!=blank ) {
            warning("Uncompatible BLANK="+blank+" compared to pre-existing survey BLANK="+fits.blank);
            return false;
         }
      }

      int or = cds.tools.pixtools.Util.getMaxOrderByPath(getOutputPath());
      if( or!=-1 && or!=getOrder() ) {
         warning("Uncompatible order="+getOrder()+" compared to pre-existing survey order="+or);
         return false;
      }

      return true;
   }

   private double getDefaultBlankFromBitpix(int bitpix) {
      return bitpix<0 ? Double.NaN : bitpix==32 ? Integer.MIN_VALUE : bitpix==16 ? Short.MIN_VALUE : 0;
   }

   /** Interpr�tation de la chaine d�crivant les bords � ignorer dans les images sources,
    * soit une seule valeur appliqu�e � tous les bords,
    * soit 4 valeurs affect�es � la java de la mani�re suivante : Nord, Ouest, Sud, Est
    * @throws ParseException */
   private int [] parseBorderSize(String s) throws ParseException {
      int [] border = { 0,0,0,0 };
      try {
         StringTokenizer st = new StringTokenizer(s," ,;-");
         for( int i=0; i<4 && st.hasMoreTokens(); i++ ) {
            String s1 = st.nextToken();
            border[i] = Integer.parseInt(s1);
            if( i==0 ) border[3]=border[2]=border[1]=border[0];
         }
         int x = border[0]; border[0] = border[2]; border[2] = x;  // Permutations pour respecter l'ordre North West South East
      } catch( Exception e ) {
         throw new ParseException("Border error => assume 0", 0);
      }
      return border;
   }

   protected boolean isExistingDir() {
      String path = getInputPath();
      if( path==null ) return false;
      return  (new File(path)).isDirectory();
   }
   
   protected boolean isExistingAllskyDir() { return isExistingAllskyDir( getOutputPath() ); }
   protected boolean isExistingAllskyDir(String path) {
      if( path==null ) return false;
      File f = new File(path);
      if( !f.exists() ) {
         return false;
      }
      int order = cds.tools.pixtools.Util.getMaxOrderByPath(path);
      return order!=-1;
   }
   
   /** Retourne la largeur d'une tuile d�j� g�n�r�e, -1 si non trouv� */
   protected int getTileWidthByNpixFile(String path) throws Exception {
      String npixFile = findOneNpixFile(path);
      if( npixFile==null ) return -1;
      Fits f = new Fits();
      MyInputStream mi = null;
      try {
         mi = new MyInputStream(new FileInputStream(npixFile));
         mi = mi.startRead();
         if( (mi.getType()&MyInputStream.FITS)!=0 ) f.loadFITS(mi);
         else f.loadPreview(mi);
         return f.width;
      } catch( Exception e ) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); }
      finally { mi.close(); }
      return -1;
   }
   
   protected boolean isExistingTiles() { return isExistingTiles( getOutputPath() ); }
   protected boolean isExistingTiles(String path) {
      String npixFile = findOneNpixFile(path);
      return npixFile!=null && (new File(npixFile)).exists();
   }
   
   /** Retourne une tuile HiPS */
   protected String findOneNpixFile(String path) {
         File root = new File(path);
         
         // Recherche du premier NorderXX trouv� o� XX est un nombre
         File norder = null;
         for( String s : root.list() ) {
            if( s.startsWith("Norder") ) {
               try { Integer.parseInt( s.substring(6)); } catch( Exception e) { continue; }
               norder= new File(path+"/"+s);
               break;
            }
         }
         if( norder==null ) return null;
         
         // Recherche du premier Dir trouv� o� XX est un nombre
         File dir = null;
         for( String s : norder.list() ) {
            if( s.startsWith("Dir") ) {
               try { Integer.parseInt( s.substring(3)); } catch( Exception e) { continue; }
               dir = new File(norder.getAbsolutePath()+"/"+s);
               break;
            } 
         }
         if( dir==null ) return null;
         
         // Recherche du premier NpixXX.ext trouv� o� XX est un nombre
         String npix = null;
         for( String s : dir.list() ) {
            if( s.startsWith("Npix") ) {
               int j = s.lastIndexOf('.');
               if( j<0 ) continue;
               try { Integer.parseInt( s.substring(4,j)); } catch( Exception e) { continue; }
               npix = dir.getAbsolutePath()+"/"+s;
               break;
            } 
         }
         if( npix==null ) return null;
         
         return npix;
   }
   
   protected boolean hasPropertyFile(String path) {
      File f = new File(path+Util.FS+Constante.FILE_PROPERTIES);
      return f.exists();
   }

   protected boolean isExistingIndexDir() {
      String path = getHpxFinderPath();
      if( path==null ) return false;
      File f = new File(path);
      if( !f.exists() ) return false;
      for( File fc : f.listFiles() ) { if( fc.isDirectory() && fc.getName().startsWith("Norder") ) return true; }
      return false;
   }

   /** Positionne le MOC correspondant � l'index */
   protected void setMocIndex(SMoc m) throws Exception {
      mocIndex=m;
   }

   /** Retourne le nombre de cellules � calculer (baser sur le MOC de l'index et le MOC de la zone) */
   protected long getNbLowCells() {
      int o = getOrder();
      if( moc==null && mocIndex==null || o==-1 ) return -1;
      SMoc m = moc!=null ? moc : mocIndex;
      if( o!=m.getMocOrder() ) {
         try { 
            m =  m.clone();
            m.setMocOrder( o ); 
         } catch( Exception e ) {}
      }
      long res = m.getNbValues() * depth;
      //      Aladin.trace(4,"getNbLowsCells => mocOrder="+m.getMocOrder()+" => UsedArea="+m.getUsedArea()+"+ depth="+depth+" => "+res);
      return res;
   }

   /** Retourne le volume du HiPS en fits en fonction du nombre de cellules pr�vues et du bitpix */
   protected long getDiskMem() {
      long nbLowCells = getNbLowCells();
      if( nbLowCells==-1 || bitpix==0 ) return -1;
      long mem = nbLowCells *getTileSide()*getTileSide() * (Math.abs(bitpix)/8);

      return mem;
   }

   protected int lastNorder3=-2;
   protected void setProgressLastNorder3 (int lastNorder3) { this.lastNorder3=lastNorder3; }

   // Demande d'affichage des stats (dans le TabBuild)
   protected void showIndexStat(int statNbFile, int statBlocFile, int statNbZipFile, long statMemFile, long statPixSize, long statMaxSize,
         int statMaxWidth, int statMaxHeight, int statMaxDepth, int statMaxNbyte,long statDuree) {
      String s;
      if( statNbFile==-1 ) s = "";
      else {
         String nbPerSec =  statDuree>1000 ? ""+Util.round(statNbFile/(statDuree/1000.),1) : "";
         s= statNbFile+" file"+(statNbFile>1?"s":"")
               +" in "+Util.getTemps(statDuree*1000L)
               +(nbPerSec.length()==0 ? "":" => "+nbPerSec+"/s")
               + (statNbFile>0 && statNbZipFile==statNbFile ? " - all gzipped" : statNbZipFile>0 ? " ("+statNbZipFile+" gzipped)":"")
               + " => "+Util.getUnitDisk(statPixSize).replace("B","pix")
               + " using "+Util.getUnitDisk(statMemFile)
               + (statNbFile>1 && statMaxSize<0 ? "" : " => biggest: ["+statMaxWidth+"x"+statMaxHeight
                     +(statMaxDepth>1?"x"+statMaxDepth:"")+" x"+statMaxNbyte+"]");
      }
      stat(s);
      
   }

   // Demande d'affichage des stats (dans le TabBuild)
   protected void showTilesStat(int statNbThreadRunning, int statNbThread, long totalTime,
         int statNbTile, int statNbEmptyTile, int statNodeTile, long statMinTime, long statMaxTime, long statAvgTime,
         long statNodeAvgTime,long usedMem,long deltaTime,long deltaNbTile) {

      if( statNbTile==0 ) return;
      long nbCells = getNbLowCells();
      long nbLowTile = statNbTile+statNbEmptyTile;
      String sNbCells = nbCells==-1 ? "" : "/"+nbCells;
      String pourcentNbCells = nbCells==-1 ? "" :
         nbCells==0 ? "-":(Math.round( ( (double)nbLowTile/nbCells )*1000)/10.)+"%";
      
      long tempsTotalEstime = nbLowTile==0 ? 0 : nbCells==0 ? 0 :(nbCells*totalTime)/nbLowTile - totalTime;

      long nbTilesPerMin = (deltaNbTile*60000L)/deltaTime;

      String s=statNbTile+(statNbEmptyTile==0?"":"+"+statNbEmptyTile)+sNbCells+" tiles + "+statNodeTile+" nodes in "+Util.getTemps(totalTime*1000L)+" ("
            +pourcentNbCells+(nbTilesPerMin<=0 ? "": " "+nbTilesPerMin+" tiles/mn EndsIn:"+Util.getTemps(tempsTotalEstime*1000L))+") "
//            +Util.getTemps(statAvgTime)+"/tile ["+Util.getTemps(statMinTime)+" .. "+Util.getTemps(statMaxTime)+"] "
//            +Util.getTemps(statNodeAvgTime)+"/node"
            +(statNbThread==0 ? "":"by "+statNbThreadRunning+"/"+statNbThread+" threads")
            //         +" using "+Util.getUnitDisk(usedMem)
            ;

      stat(s);
      if( cacheFits!=null && cacheFits.getStatNbOpen()>0 ) stat(cacheFits+"");

      setProgress(statNbTile+statNbEmptyTile, nbCells);
   }

   protected void showMapStat(long  cRecord,long nbRecord, long cTime,CacheFits cache, String info ) {
      double pourcent = (double)cRecord/nbRecord;
      long totalTime = (long)( cTime/pourcent);
      long endsIn = totalTime-cTime;
      stat(Util.round(pourcent*100,1)+"% in " +Util.getTemps(cTime*1000L)+" endsIn:"+Util.getTemps(endsIn*1000L)
            + " (record="+(cRecord+1)+"/"+nbRecord+")");
      if( cache!=null && cache.getStatNbOpen()>0 ) stat(cache+"");
      setProgress(cRecord,nbRecord);
   }

   // Demande d'affichage des stats (dans le TabJpeg)
   protected void showJpgStat(int statNbFile, long cTime,int statNbThread,int statNbThreadRunning) {
      long nbLowCells = getNbLowCells();

      double pourcent = nbLowCells<=0 ? 0 : (double)statNbFile/nbLowCells;
      long totalTime = (long)( cTime/pourcent );
      long endsIn = totalTime-cTime;
      String pourcentNbCells = nbLowCells==-1 ? "" :
         (Math.round( ( (double)statNbFile/nbLowCells )*1000)/10.)+"%) ";

      String s;
      if( nbLowCells<=0 ) s = s=statNbFile+" tiles in "+Util.getTemps(cTime*1000L);
      else s=statNbFile+"/"+nbLowCells+" tiles in "+Util.getTemps(cTime*1000L)+" ("
            +pourcentNbCells+" endsIn:"+Util.getTemps(endsIn*1000L)
            +(statNbThread==0 ? "":" by "+statNbThreadRunning+"/"+statNbThread+" threads");

      stat(s);
      setProgress(statNbFile,nbLowCells);
   }

   // Demande d'affichage des stats (dans le TabJpeg)
   protected void showMirrorStat(int statNbFile, long cumul, long lastCumulPerSec,
         long cTime,int statNbThread,int statNbThreadRunning, long timeIP) {
      long nbLowCells = getNbLowCells();

      double pourcent = nbLowCells<=0 ? 0 : (double)statNbFile/nbLowCells;
      long totalTime = (long)( cTime/pourcent );
      long endsIn = totalTime-cTime;
      String pourcentNbCells = nbLowCells==-1 ? "" :
         (Math.round( ( (double)statNbFile/nbLowCells )*1000)/10.)+"%) ";

      String debit = cTime>1000L ? Util.getUnitDisk( cumul/(cTime/1000L) )+"/s" : "OB/s";
      String debitI = Util.getUnitDisk( lastCumulPerSec )+"/s";

      String s;
      if( nbLowCells<=0 ) s = s=statNbFile+" tiles in "+Util.getTemps(cTime*1000L);
      else s=statNbFile+"/"+nbLowCells+" tiles in "+Util.getTemps(cTime*1000L)+" ("
            +pourcentNbCells+"endsIn:"+Util.getTemps(endsIn*1000L)
            +" speed:"+ debitI + " avg:"+debit +" for "+Util.getUnitDisk(cumul)
            +(statNbThread==0 ? "":" by "+statNbThreadRunning+"/"+statNbThread+" threads")
            +(timeIP==0 ? "":" IPTime:"+timeIP+"ms");

      stat(s);
      setProgress(statNbFile,nbLowCells);
   }
   
   // Demande d'affichage des stats (dans le TabJpeg)
   protected void showRGBStat(int statNbFile, long cTime,int statNbThread,int statNbThreadRunning) {
      long nbLowCells = getNbLowCells();

      double pourcent = nbLowCells<=0 ? 0 : (double)statNbFile/nbLowCells;
      long totalTime = (long)( cTime/pourcent );
      long endsIn = totalTime-cTime;
      String pourcentNbCells = nbLowCells==-1 ? "" :
         (Math.round( ( (double)statNbFile/nbLowCells )*1000)/10.)+"%) ";

      String s;
      if( nbLowCells<=0 ) s = s=statNbFile+" tiles in "+Util.getTemps(cTime*1000L);
      else s=statNbFile+"/"+nbLowCells+" tiles in "+Util.getTemps(cTime*1000L)+" ("
            +pourcentNbCells+" endsIn:"+Util.getTemps(endsIn*1000L)
            +(statNbThread==0 ? "":" by "+statNbThreadRunning+"/"+statNbThread+" threads");

      stat(s);
      setProgress(statNbFile,nbLowCells);
   }


//   // Demande d'affichage des stats (dans le TabRgb)
//   protected void showRgbStat(int statNbFile, long statSize, long totalTime) {
//      if( statNbFile>0 ) showJpgStat(statNbFile, totalTime, 1, 1);
//   }

   protected Action action=null;      // Action en cours (voir Action)
   protected double progress=-1;       // Niveau de progression de l'action en cours, -1 si non encore active, =progressMax si termin�e
   protected double progressMax=Double.MAX_VALUE;   // Progression max de l'action en cours (MAX_VALUE si inconnue)
   protected JProgressBar progressBar=null;  // la progressBar attach� � l'action
   protected MyProperties prop=null;

   private boolean isMap=false;       // true s'il s'agit d'une map HEALPix FITS
   protected boolean isMap() { return isMap; }
   protected void setMap(boolean flag ) { isMap=flag; }

   protected boolean ignoreStamp;
   public void setIgnoreStamp(boolean flag) { ignoreStamp=true; }

   private boolean taskRunning=false;        // true s'il y a un processus de calcul en cours
   public boolean isTaskRunning() { return taskRunning; }
   public void setTaskRunning(boolean flag) {
      if( flag ) taskAborting=false;            // Si la derni�re tache a �t� interrompue, il faut reswitcher le drapeau
      else progressBar=null;
      taskRunning=flag;
      resumeWidgets();
   }
   private boolean taskPause=false;          // true si le processus de calcul est en pause
   public boolean isTaskPause() { return taskPause; }
   public void setTaskPause(boolean flag) {
      taskPause=flag;
      resumeWidgets();
   }

   protected boolean taskAborting=false;       // True s'il y a une demande d'interruption du calcul en cours
   public void taskAbort() { taskAborting=true; taskPause=false; }
   public boolean isTaskAborting() {
      if( taskAborting ) return true;
      while( taskPause ) Util.pause(500);
      return false;
   }

   static private SimpleDateFormat DATEFORMAT = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
   //   static protected String getNow() { return DATEFORMAT.format( new Date() ); }
   static protected String getNow() { return Constante.getDate(); }
   static long getTime(String date) throws Exception { return DATEFORMAT.parse(date).getTime(); }

   static private String getKeyActionStart(Action a) { return "Processing."+a+".start"; }
   static private String getKeyActionEnd(Action a)   { return "Processing."+a+".end"; }

   public void startAction(Action a) throws Exception {
      action=a;
      action.startTime();
      //      running("========= "+action+" ==========");
      //      updateProperties( getKeyActionStart(action), getNow(),true);
      setProgress(0,-1);
   }
   public void endAction() throws Exception {
      if( action==null ) return;
      if( isTaskAborting() ) abort(action+" abort (after "+Util.getTemps(action.getDuree()*1000L)+")");
      else {
         done(action+" done (in "+Util.getTemps(action.getDuree()*1000L)+")");
         //         updateProperties( getKeyActionEnd(action), getNow(),true);
      }
      action=null;
   }
   public Action getAction() { return action; }

   /** true si l'action a �t� correctement estampill�e comme termin�e dans le fichier des propri�t�s */
   public boolean actionAlreadyDone(Action a) {
      if( ignoreStamp ) return false;
      try {
         if( prop==null ) loadProperties();
         if( prop==null ) return false;
         String end = prop.getProperty( getKeyActionEnd(a) );
         if( end==null ) return false;    // Jamais encore termin�e
         String start = prop.getProperty( getKeyActionStart(a) );
         if( start==null ) return false;  // Jamais encore commenc�e
         //         System.out.println("ActionAlready done: "+a+" start="+start+" end="+end);
         if( getTime(end)<getTime(start) ) return false; // Certainement relanc�e, mais non-achev�e
      } catch( Exception e ) {
         e.printStackTrace();
         return false;
      }
      return true;
   }

   /** true si les deux actions sont termin�es et que la premi�re pr�c�de la seconde */
   public boolean actionPrecedeAction(Action avant,Action apres) {
      if( ignoreStamp ) return false;
      try {
         if( prop==null ) loadProperties();
         if( prop==null ) return false;
         if( !actionAlreadyDone(avant) || !actionAlreadyDone(apres)) return false; // L'une des 2 actions n'a pas �t� termin�e
         String endAvant = prop.getProperty( getKeyActionEnd(avant) );
         String endApres = prop.getProperty( getKeyActionEnd(apres) );
         //         System.out.println("actionPrecedeAction done: "+avant+"="+endAvant+" "+apres+"="+endApres);
         if( getTime(endApres)<getTime(endAvant) ) return false;  // L'action avant est post�rieure
      } catch( Exception e ) {
         e.printStackTrace();
         return false;
      }
      return true;
   }

   public void setProgress(double progress,double progressMax) { setProgress(progress); setProgressMax(progressMax); }
   public void setProgress(double progress) { this.progress=progress; }
   public void setProgressMax(double progressMax) { this.progressMax=progressMax; }
   public void progressStatus() { System.out.print('.'); flagNL=true; }

   //   static private char []  STATUS = { '|','/','-','\\' };
   //   private int indexStatus=0;
   //
   //   public void progressStatus() {
   //      System.out.print( "\b" );
   //      System.out.print( STATUS[indexStatus] );
   //      indexStatus++;
   //      if( indexStatus>=STATUS.length ) indexStatus=0;
   //      flagNL=true;
   //   }

   public void enableProgress(boolean flag) { System.out.println("progress ["+action+"] enable="+flag); }
   public void setProgressBar(JProgressBar bar) { }
   public void resumeWidgets() { }

   public void trace(int i, String string) {
      if (Aladin.levelTrace>=i)
         System.out.println(string);
   }
   public void setTrace(int trace) {
      Aladin.levelTrace = trace;
   }

   /**
    * @param verbose the verbose level to set
    */
   public static void setVerbose(boolean verbose) {
      Context.verbose = verbose;
      BuilderTiles.DEBUG=true;
   }

   /** Verbose or not ? */
   public static int getVerbose() { return Aladin.levelTrace; }

   /**
    * Niveau de verbosit� :
    * -1    rien
    * 0     stats
    * 1-4   traces habituelles d'Aladin
    * @param verbose the verbose to set
    */
   public static void setVerbose(int level) {
      if (level>=0) {
         Context.verbose = true;
         Aladin.levelTrace = level;
      }
      else {
         Context.verbose = false;
         Aladin.levelTrace = 0;
      }
   }


   private boolean flagNL=false;   // indique qu'il faut ou non ins�rer un NL dans les stats, infos...

   // Ins�re un NL si n�cessaire
   private void nl() { if( flagNL ) System.out.println(); flagNL=false; }
   //   private void nl() { if( flagNL ) System.out.print("\b"); flagNL=false; }
   
   // Retourne la chaine indiqu�e encadr�e par deux traits
   // ex: ------------------- toto -------------------
   static public String getTitle(String s ) { return getTitle(s,'-'); }
   static public String getTitle(String s, char c ) { return getTitle(s,c,102); }
   static public String getTitle(String s, char c, int len) {
      int m = (len - 2 - s.length() )/2;
      StringBuilder s1 = new StringBuilder();
      for( int i=0; i<m; i++ ) s1.append(c);
      return s1+" "+s+" "+(s.length()%2==0?"":" ")+s1;
   }
   
   // True si on veut utiliser les codes couleurs du terminal
   protected void setTerm(boolean isTermCompliant) { TERM=isTermCompliant;  }
   
   private String rouge() { return TERM ? "\033[31m" : ""; }
   private String vert()  { return TERM ? "\033[32m" : ""; }
   private String brun()  { return TERM ? "\033[33m" : ""; }
   private String blue()  { return TERM ? "\033[34m" : ""; }
   private String violet(){ return TERM ? "\033[35m" : ""; }
   private String bluec() { return TERM ? "\033[36m" : ""; }
   private String cyan()  { return TERM ? "\033[37m" : ""; }
   private String end()   { return TERM ? "\033[0m"  : ""; }

   public void running(String s)  { nl(); System.out.println(blue()  +"RUN   : "+getTitle(s,'=')+end()); }
   public void done(String r)     { nl(); System.out.println(blue()  +"DONE  : "+r+end()); }
   public void abort(String r)    { nl(); System.out.println(rouge() +"ABORT : "+r+end()); }
   public void info(String s)     { nl(); System.out.println(         "INFO  : "+s); }
   public void run(String s)      { nl(); System.out.println(blue()  +"RUN   : "+s+end()); }
   public void warning(String s)  { nl(); System.out.println(violet()+"*WARN*: "+s+end()); }
   public void error(String s)    { nl(); System.out.println(rouge() +"*ERROR: "+s+end()); }
   public void action(String s)   { nl(); System.out.println(blue()  +"ACTION: "+s+end()); }
   public void stat(String s)     { nl(); System.out.println(bluec() +"STAT  : "+s+end()); }
   
   
   static private final int MAXREMOVEDFILE=100;
   private HashSet<String> removeList = null;
   public void addFileRemoveList(String file) {
      if( removeList==null ) removeList = new HashSet<>();
      if( (removeList.size()) >MAXREMOVEDFILE ) {
         abort("Too many removed original files (>"+MAXREMOVEDFILE+")");
         taskAbort();
      }
      
      // j'enl�ve un �ventuel suffixe [xxxx]
      if( file.endsWith("]") ) {
         int i = file.lastIndexOf('[');
         if( i>0 ) file = file.substring(0,i);
      }
      warning("Problem on open => file retired: "+file);

      removeList.add(file);
   }
   
   public void removeListReport() {
      if( removeList==null || removeList.size()==0 ) return;
      warning("Report on problematic files:");
      for( String s : removeList) {
         nl(); System.out.println(violet()  +"  "+s+end());
      }
   }

   private boolean validateOutputDone=false;
   public boolean isValidateOutput() { return validateOutputDone; }
   public void setValidateOutput(boolean flag) { validateOutputDone=flag; }

   private boolean validateInputDone=false;
   public boolean isValidateInput() { return validateInputDone; }
   public void setValidateInput(boolean flag) { validateInputDone=flag; }

   private boolean validateCutDone=false;
   public boolean isValidateCut() { return validateCutDone; }
   public void setValidateCut(boolean flag) { validateCutDone=flag; }

   private boolean validateRegion=false;
   public boolean isValidateRegion() { return validateRegion; }
   public void setValidateRegion(boolean flag) { validateRegion=flag; }

//   static private final Astrocoo COO_GAL = new Astrocoo(new Galactic());
//   static private final Astrocoo COO_EQU = new Astrocoo(new ICRS());
//   static private Astroframe AF_GAL1 = new Galactic();
//   static private Astroframe AF_ICRS1 = new ICRS();
   static private final Astrocoo COO_GAL = new Astrocoo( Astroframe.create("GALACTIC") );
   static private final Astrocoo COO_EQU = new Astrocoo( Astroframe.create("ICRS") );
   static private Astroframe AF_GAL1 = Astroframe.create("GALACTIC"); 
   static private Astroframe AF_ICRS1 = Astroframe.create("ICRS"); 
   
  /** M�morisation d'une propri�t� � ajouter dans le fichier properties */
   protected void setComment(String comment) { setPropriete1("#","#"+comment,false); }
   protected void insertPropriete(String key,String value) { setPropriete1(key,value,true); }
   protected void setPropriete(String key, String value) { setPropriete1(key,value,false); }
   
   private void setPropriete1(String key, String value,boolean flagInsert) {
      if( keyAddProp==null ) {
         keyAddProp = new Vector<>();
         valueAddProp = new Vector<>();
      }
      
      // Suppression ?
      if( value==null ) {
         int i = keyAddProp.indexOf(key);
         if( i!=-1 ) {
            keyAddProp.remove(i);
            valueAddProp.remove(i);
         }
      }
      
      // Insertion en premi�re position
      if( flagInsert ) {
         keyAddProp.insertElementAt(key,0);
         valueAddProp.insertElementAt(value,0);
         
      // Insertion � la fin
      } else {
         keyAddProp.addElement(key);
         valueAddProp.addElement(value);
      }
   }

   private String INDEX =

         "<HTML>\n" +
               "<HEAD>\n" +
               "   <script type=\"text/javascript\" src=\"https://code.jquery.com/jquery-1.10.1.min.js\"></script>\n" +
               "   <link rel=\"stylesheet\" href=\"https://aladin.cds.unistra.fr/AladinLite/api/v2/latest/aladin.min.css\" >\n" +
               "   <script type=\"text/javascript\">var jqMenu = jQuery.noConflict();</script>\n" +
               "   <script type=\"text/javascript\">\n" +
               "var hipsDir=null;</script>\n" +
               "</HEAD>\n" +

         "<H1>\"$LABEL\" progressive survey</H1>\n" +
         "This Web resource contains HiPS(*) components for <B>$LABEL</B> progressive survey.\n" +
         "<script type=\"text/javascript\">\n" +
         "hipsDir = location.href;\n" +
         "hipsDir = hipsDir.substring(0,hipsDir.lastIndexOf(\"/\",hipsDir.length));\n" +
         "document.getElementById(\"hipsBase\").innerHTML=hipsDir;\n" +
         "</script>\n" +
         "<TABLE>\n" +
         "<TR>\n" +
         "<TD>\n" +
         "   <script type=\"text/javascript\" src=\"https://aladin.cds.unistra.fr/AladinLite/api/v2/latest/aladin.min.js\" charset=\"utf-8\"></script>\n" +
         "<div id=\"aladin-lite-div\" style=\"width:70vw;height:70vh;\"></div>\n" +
         "<script type=\"text/javascript\">\n" +
         "//var hipsDir = location.href;\n" +
         "//hipsDir = hipsDir.substring(0,hipsDir.lastIndexOf(\"/\",hipsDir.length));\n" +
         "var aladin = $.aladin(\"#aladin-lite-div\", {showSimbadPointerControl: true });\n" +
         "aladin.setImageSurvey(aladin.createImageSurvey('$LABEL', '$LABEL',\n" +
         "hipsDir, '$SYS', $ORDER, {imgFormat: '$FMT'}));\n" +

         "</script>    \n" +

         "</TD>\n" +
         "<TD>\n" +
         "<UL>\n" +
         "$INFO" +
         "   <LI> <B>Property file:</B> <A HREF=\"properties\">properties</A>\n" +
         "   <LI> <B>Base URL:<p id=\"hipsBase\"></p></B> \n" +
         "</UL>\n" +
         "</TD>\n" +
         "</TR>\n" +
         "</TABLE>\n" +

         "This survey can be displayed by <A HREF=\"https://aladin.cds.unistra.fr/AladinLite\">Aladin Lite</A> (see above), \n" +
         "by <A HREF=\"https://aladin.cds.unistra.fr/java/nph-aladin.pl?frame=downloading\">Aladin Desktop</A> client\n" +
         "(just open the base URL)<BR>or any other HiPS aware clients.\n" +
         "<HR>\n" +
         "<I>(*) HiPS is a recommended <A HREF=\"www.ivoa.net\">International Virtual Observatory Alliance</A> standard:"
         + "<A HREF=\"www.ivoa.net/documents/HiPS\">HiPS REC</A>. \n" +
         "The HiPS technology allows a dedicated client to access an astronomical survey at any location and at any scale. \n" +
         "HiPS has been invented by <A HREF=\"https://aladin.cds.unistra.fr/hips\">CDS-Universit&eacute; de Strasbourg/CNRS</A> (<A HREF=\"https://ui.adsabs.harvard.edu/abs/2015A%26A...578A.114F/abstract\">2015A&amp;A...578A.114F</A>). "
         + "It is based on HEALPix sky tessellation and it is designed for astronomical scientifical usages (low distorsion, true pixel values...).</I>" +
         "<script type=\"text/javascript\">\n" +
         "document.getElementById(\"hipsBase\").innerHTML=hipsDir;\n" +
         "</script>\n" +

         "</HTML>\n" +
         "";

   /** G�n�re un fichier index.html afin de pouvoir afficher qq chose si l'utilisateur charge
    * directement dans son navigateur le r�pertoire de base du HiPS g�n�r�
    */
   protected void writeIndexHtml() throws Exception {
      String label = getLabel();
      if( label==null || label.length()==0 ) label= "XXX_"+(System.currentTimeMillis()/1000);

      int order = getOrder();
      if( order==-1 ) order = cds.tools.pixtools.Util.getMaxOrderByPath( getOutputPath() );

      if( moc==null ) try { loadMoc(); } catch( Exception e ) { }
      if( prop==null ) loadProperties();
      String sys = prop.getProperty(Constante.KEY_HIPS_FRAME);
      if( sys==null ) sys="galactic";

      long nside = CDSHealpix.pow2(order);
//      long nsideP = CDSHealpix.pow2(order+getTileOrder());
      double resol = CDSHealpix.pixRes(order+getTileOrder())/3600;

      int width = getTileSide();
      String tiles = getAvailableTileFormats();
      String fmt = tiles.indexOf("png")>=0 ? "png" : "jpg";

      String res = INDEX.replace("$LABEL",label);
      StringBuilder info = new StringBuilder();
      info.append("   <LI> <B>Label:</B> "+label+"\n");
      info.append("   <LI> <B>Type:</B> "+(depth>1?"HiPS cube ("+depth+" frames)" : isColor() ? "colored HiPS image" : "HiPS image")+"\n");
      info.append("   <LI> <B>Best pixel angular resolution:</B> "+Coord.getUnit( resol )+"\n");
      info.append("   <LI> <B>Max tile order:</B> "+order+" (NSIDE="+nside+")\n");
      info.append("   <LI> <B>Available encoding tiles:</B> "+tiles+"\n");
      info.append("   <LI> <B>Tile size:</B> "+width+"x"+width+"\n");
      if( bitpix!=0 && bitpix!=-1 ) info.append("   <LI> <B>FITS tile BITPIX:</B> "+bitpix+"\n");
      info.append("   <LI> <B>Processing date:</B> "+getNow()+"\n");
      info.append("   <LI> <B>HiPS builder:</B> "+"Aladin/HipsGen "+Aladin.VERSION+"\n");
      info.append("   <LI> <B>Coordinate frame:</B> " +sys+"\n");
      if( moc!=null ) {
         double cov = moc.getCoverage();
         double degrad = Math.toDegrees(1.0);
         double skyArea = 4.*Math.PI*degrad*degrad;
         info.append("   <LI> <B>Area:</B> "+Util.round(cov*100, 3)+"% of sky => "+Coord.getUnit(skyArea*cov, false, true)+"^2\n");
         info.append("   <LI> <B>Associated coverage map:</B> <A HREF=\""+Constante.FILE_MOC+"\">MOC</A>\n");
      }

      String metadata = cds.tools.Util.concatDir( getHpxFinderPath(),Constante.FILE_METADATAXML);
      if( (new File(metadata)).exists() ) {
         info.append("   <LI> <B>Original data access template:</B> <A HREF=\"HpxFinder/"+Constante.FILE_METADATAXML+"\">"+Constante.FILE_METADATAXML+"</A>\n");
      }

      res = res.replace("$INFO",info);
      res = res.replace("$ORDER",order+"");
      res = res.replace("$SYS",sys);
      res = res.replace("$FMT",fmt);

      String tmp = getOutputPath()+Util.FS+"index.html";
      File ftmp = new File(tmp);
      if( ftmp.exists() ) ftmp.delete();
      FileOutputStream out = null;
      try {
         out = new FileOutputStream(ftmp);
         out.write(res.getBytes());
      } finally {  if( out!=null ) out.close(); }
   }

   /** Cr�ation d'un fichier metadata.txt associ� au HiPS */
   protected void writeMetadataFits() throws Exception {
      writeMetadataFits(getOutputPath(),header);
   }
   
   static public void writeMetadataFits(String path, HeaderFits header) throws Exception {

      // POUR LE MOMENT JE PREFERE NE PAS LE METTRE
      //      // Si je n'ai pas de Header sp�cifique, je r�cup�re
      //      // celui de l'image �talon
      //      if( header==null && imgEtalon!=null ) {
      //         try {
      //            MyInputStream in = new MyInputStream( new FileInputStream( imgEtalon ) );
      //            header = new HeaderFits( in );
      //            in.close();
      //         } catch( Exception e ) { e.printStackTrace(); }
      //      }

      if( header==null )  return;

      String tmp = path+Util.FS+Constante.FILE_METADATATXT;
      File ftmp = new File(tmp);
      if( ftmp.exists() ) ftmp.delete();
      FileOutputStream out = null;
      try {
         out = new FileOutputStream(ftmp);
         out.write(( header.getOriginalHeaderFits()).getBytes() );
      } finally {  if( out!=null ) out.close(); }

   }

   /** Cr�ation, ou mise � jour des fichiers meta associ�es au survey
    */
   protected void writeMetaFile() throws Exception {
      writePropertiesFile(null);
      
      // On en profite pour �crire le fichier index.html
      writeIndexHtml();

      // Et metadata.fits
      writeMetadataFits();

    }
   
   /** Cr�ation, ou mise � jour du fichier des Properties associ�es au survey
    * @param stream null pour l'�crire � l'emplacement pr�vu par d�faut
    */
   protected void writePropertiesFile(OutputStreamWriter stream) throws Exception {


      // Ajout de l'IVORN si besoin
      if( hipsId==null ) setHipsId(null);

//      // Ajout de l'order si besoin
//      int order = getOrder();
//      if( order==-1 ) order = cds.tools.pixtools.Util.getMaxOrderByPath( getOutputPath() );
//      
      // Recherche des orders si besoin est
      int min = getMinOrder();
      int max = getOrder();
      int [] minMaxOrder = new int[] { min, max };
      if( min==-1 || max==-1 ) {
         int [] mm = findMinMaxOrder();
         if( min==-1 ) minMaxOrder[0] = mm[0];
         if( max<=0 ) minMaxOrder[1] = mm[1];
      }
      
      // Recherche de la taille des tuiles si besoin est
      long nSide;
      if( tileOrder==-1 ) nSide = findTileNSide();
      else nSide = CDSHealpix.pow2( tileOrder );
      
      //      loadProperties();

      insertPropriete(Constante.KEY_CREATOR_DID,hipsId);
      
      // Y a-t-il un creator indiqu� ?
      if( creator!=null ) setPropriete(Constante.KEY_CREATOR,creator);
      else setPropriete("#"+Constante.KEY_CREATOR,"HiPS creator (institute or person)");
      setPropriete("#"+Constante.KEY_HIPS_COPYRIGHT,"Copyright mention of the HiPS");
      
      if( addendum_id!=null ) setPropriete(Constante.KEY_ADDENDUM_ID,addendum_id);
      
      String title = prop!=null ? prop.get( Constante.KEY_OBS_TITLE) : null;
      if( title==null ) title=getLabel();
      setPropriete(Constante.KEY_OBS_TITLE,title);
      setPropriete("#"+Constante.KEY_OBS_COLLECTION,"Dataset collection name");
      setPropriete("#"+Constante.KEY_OBS_DESCRIPTION,"Dataset text description");
      setPropriete("#"+Constante.KEY_OBS_ACK,"Acknowledgement mention");
      setPropriete("#"+Constante.KEY_PROV_PROGENITOR,"Provenance of the original data (free text)");
      setPropriete("#"+Constante.KEY_BIB_REFERENCE,"Bibcode for bibliographic reference");
      setPropriete("#"+Constante.KEY_BIB_REFERENCE_URL,"URL to bibliographic reference");
      setPropriete("#"+Constante.KEY_OBS_COPYRIGHT,"Copyright mention of the original data");
      setPropriete("#"+Constante.KEY_OBS_COPYRIGHT_URL,"URL to copyright page of the original data");
      setPropriete("#"+Constante.KEY_T_MIN,"Start time in MJD ( =(Unixtime/86400)+40587  or https://heasarc.gsfc.nasa.gov/cgi-bin/Tools/xTime/xTime.pl)");
      setPropriete("#"+Constante.KEY_T_MAX,"Stop time in MJD");
      setPropriete("#"+Constante.KEY_OBS_REGIME,"Waveband keyword (Radio Infrared Optical UV X-ray Gamma-ray)");
      setPropriete("#"+Constante.KEY_EM_MIN,"Start in spectral coordinates in meters ( =2.998E8/freq in Hz, or =1.2398841929E-12*energy in MeV )");
      setPropriete("#"+Constante.KEY_EM_MAX,"Stop in spectral coordinates in meters");
      //      setPropriete("#"+Constante.KEY_CLIENT_CATEGORY,"ex: Image/Gas-lines/Halpha/VTSS");
      //      setPropriete("#"+Constante.KEY_CLIENT_SORT_KEY,"ex: 06-03-01");


      setPropriete(Constante.KEY_HIPS_BUILDER,"Aladin/HipsGen "+Aladin.VERSION);
      setPropriete(Constante.KEY_HIPS_VERSION, Constante.HIPS_VERSION);
      if( !notouch ) setPropriete(Constante.KEY_HIPS_RELEASE_DATE,getNow());

      setPropriete(Constante.KEY_HIPS_FRAME, getFrameName());
      setPropriete(Constante.KEY_HIPS_ORDER,minMaxOrder[1]+"");
      setPropriete(Constante.KEY_HIPS_ORDER_MIN, minMaxOrder[0]+"");
      setPropriete(Constante.KEY_HIPS_TILE_WIDTH,nSide+"");

      // L'url
      setPropriete("#"+Constante.KEY_HIPS_SERVICE_URL,"ex: http://yourHipsServer/"+label+"");
      
      // le status du HiPS : par defaut "public master clonableOnce"
//      setPropriete(Constante.KEY_HIPS_STATUS,"public master clonableOnce");
      String pub = Constante.PUBLIC;
      String clone = " "+Constante.CLONABLEONCE;
      if( status!=null ) {
         Tok tok = new Tok(status);
         while( tok.hasMoreTokens() ) {
            String s = tok.nextToken().toLowerCase();
            if( s.equals(Constante.PRIVATE) ) pub = Constante.PRIVATE;
            if( s.equals(Constante.UNCLONABLE) ) clone=" "+Constante.UNCLONABLE;
            else if( s.equals(Constante.CLONABLE) ) clone=" "+Constante.CLONABLE;
         }
      }
      setPropriete(Constante.KEY_HIPS_STATUS,pub+" "+Constante.MASTER+clone);

      // Ajout des formats de tuiles support�s
      String fmt = getAvailableTileFormats();
      if( fmt.length()>0 ) setPropriete(Constante.KEY_HIPS_TILE_FORMAT,fmt);

      if( fmt.indexOf("fits")>=0) {
         if( bitpix!=-1 ) setPropriete(Constante.KEY_HIPS_PIXEL_BITPIX,bitpix+"");
      }
      //      setPropriete(Constante.KEY_HIPS_PROCESS_OVERLAY,
      //            isMap() ? "none" : mode==Mode.ADD ? "add" :
      //               fading ? "border_fading" : mixing ? "mean" : "first");
//      setPropriete(Constante.KEY_HIPS_PROCESS_HIERARCHY, jpegMethod.toString().toLowerCase());


      if( cut!=null ) {
         if( cut[0]!=0 || cut[1]!=0 ) {
            setPropriete(Constante.KEY_HIPS_PIXEL_CUT,  Util.myRound(bscale*cut[0]+bzero)+" "+Util.myRound(bscale*cut[1]+bzero));
         }
         if( cut[2]!=0 || cut[3]!=0 ) setPropriete(Constante.KEY_HIPS_DATA_RANGE,Util.myRound(bscale*cut[2]+bzero)+" "+Util.myRound(bscale*cut[3]+bzero));
      }

      // Ajout du target et du radius par d�faut
      if( target!=null ) {
         int offset = target.indexOf(' ');
         setPropriete(Constante.KEY_HIPS_INITIAL_RA,  target.substring(0,offset));
         setPropriete(Constante.KEY_HIPS_INITIAL_DEC, target.substring(offset+1));
      }
      if( targetRadius!=null ) setPropriete(Constante.KEY_HIPS_INITIAL_FOV, targetRadius);

      // Resolution du hiPS
      double res = CDSHealpix.pixRes( order + getTileOrder() );
      setPropriete(Constante.KEY_HIPS_PIXEL_SCALE, Util.myRound(res/3600.) );

      if( resolution!=null ) setPropriete(Constante.KEY_S_PIXEL_SCALE, resolution);

      // Pour le cas d'un Cube
      if( depth>1 ) {
         setPropriete(Constante.KEY_DATAPRODUCT_TYPE,"cube");
         setPropriete(Constante.KEY_CUBE_DEPTH,depth+"");
         setPropriete(Constante.KEY_CUBE_FIRSTFRAME,depth/2+"");

         if( isCubeCanal() ) {
            setPropriete(Constante.KEY_CUBE_CRPIX3,crpix3+"");
            setPropriete(Constante.KEY_CUBE_CRVAL3,crval3+"");
            setPropriete(Constante.KEY_CUBE_CDELT3,cdelt3+"");
            setPropriete(Constante.KEY_CUBE_BUNIT3,cunit3+"");
         }

         // Sinon c'est un HiPS image
      } else {
         setPropriete(Constante.KEY_DATAPRODUCT_TYPE,"image");
      }
      
      // En cas de HiPS pouvant �tre �tendu
      setPropriete(Constante.KEY_DATAPRODUCT_SUBTYPE,live ? "live" : null);

      // Dans le cas d'un HiPS couleur
      if( isColor() ) {
         setPropriete(Constante.KEY_DATAPRODUCT_SUBTYPE, live ? "color live" : "color");
         if( redInfo!=null )   setPropriete(Constante.KEY_HIPS_RGB_RED,redInfo);
         if( greenInfo!=null ) setPropriete(Constante.KEY_HIPS_RGB_GREEN,greenInfo);
         if( blueInfo!=null )  setPropriete(Constante.KEY_HIPS_RGB_BLUE,blueInfo);
      }
      
      // Check Code ?
      if( hipsCheckCode!=null ) setPropriete(Constante.KEY_HIPS_CHECK_CODE,hipsCheckCode);

// DESORMAIS MAJ PAR L'ACTION CRC - PF 19 JUILLET 2022
//      SMoc m = moc!=null ? moc : mocIndex;
//      double skyFraction = m==null ? 0 : m.getCoverage();
//      if( skyFraction>0 ) {
//
//         setPropriete(Constante.KEY_MOC_SKY_FRACTION, Util.myRound( skyFraction ) );
//
//         long nbpix = CDSHealpix.pow2( getTileOrder()) * CDSHealpix.pow2( getTileOrder());
//         long tileSizeFits = Math.abs(bitpix/8) * nbpix + 2048L;
//         long tileSizeJpeg = nbpix/15;
//         long tileSizePng = (long)(tileSizeJpeg*1.5);
//         double coverage = m.getCoverage();
//         long numberOfTiles =  CDSHealpix.pow2(order) *  CDSHealpix.pow2(order) * 12L;
//         
////         System.out.println("nbpix="+nbpix+" TileSizeFits="+Util.getUnitDisk(tileSizeFits)+ " tileSizeJpeg="+Util.getUnitDisk(tileSizeJpeg)+" tileSizePng="+Util.getUnitDisk(tileSizePng)
////         +" nbTiles="+numberOfTiles+" depth="+depth);
//         long fitsSize = (long)( ( tileSizeFits*numberOfTiles * 1.4 * coverage) )/1024L;
//         long jpegSize = (long)( ( tileSizeJpeg*numberOfTiles * 1.4 * coverage) )/1024L;
//         long pngSize = (long)( ( tileSizePng*numberOfTiles * 1.4 * coverage) )/1024L;
//         long size = (fmt.indexOf("fits")>=0 ? fitsSize : 0)
//               + (fmt.indexOf("jpeg")>=0 ? jpegSize : 0)
//               + (fmt.indexOf("png")>=0 ? pngSize : 0)
//               + 8;
//         size *= depth;
////         System.out.println("fmt="+fmt+" => full size="+Util.getUnitDisk(size*1024L)+" => estsize="+size);
//         setPropriete(Constante.KEY_HIPS_ESTSIZE, size+"" );
//      }

      // Mise en place effective des propr�t�s
      String k[] = new String[ keyAddProp==null ? 0 : keyAddProp.size() ];
      String v[] = new String[ k.length ];
      for( int i=0; i<k.length; i++ ) {
         k[i] = keyAddProp.get(i);
         v[i] = valueAddProp.get(i);
      }
      updateProperties(k,v,true,stream);

   }

   /** Ecriture du fichier des propri�t�s pour le HpxFinder */
   protected void writeHpxFinderProperties() throws Exception {

      // Ajout de l'IVORN si besoin
      if( hipsId==null ) setHipsId(null);

      MyProperties prop = new MyProperties();
      String label = getLabel()+"-meta";
      String finderHipxId = getHipsId()+"/meta";

//      int offset = finderHipxId.indexOf('/');
//      if( offset==-1 ) prop.setProperty(Constante.KEY_OBS_ID,finderHipxId);
//      else {
//         prop.setProperty(Constante.KEY_OBS_ID,finderHipxId.substring(offset+1));
//         prop.setProperty(Constante.KEY_PUBLISHER_ID,"ivo://"+finderHipxId.substring(0,offset));
//      }
      
      prop.setProperty(Constante.KEY_CREATOR_DID,finderHipxId);
      
//      obs_id               = /CDS/P/toto/meta
//            publisher_id         = ivo://ivo:
//            obs_collection       = toto-meta

      prop.setProperty(Constante.KEY_OBS_TITLE, label);
      prop.setProperty(Constante.KEY_DATAPRODUCT_TYPE, "meta");
      prop.setProperty(Constante.KEY_HIPS_FRAME, getFrameName());
      prop.setProperty(Constante.KEY_HIPS_ORDER, getOrder()+"");
      if( tileOrder!=9 ) prop.setProperty(Constante.KEY_HIPS_TILE_WIDTH, getTileSide()+"");
      if( minOrder!=-1  ) prop.setProperty(Constante.KEY_HIPS_ORDER_MIN, minOrder+"");
      if( !notouch ) prop.setProperty(Constante.KEY_HIPS_RELEASE_DATE, getNow());
      prop.setProperty(Constante.KEY_HIPS_VERSION, Constante.HIPS_VERSION);
      prop.setProperty(Constante.KEY_HIPS_BUILDER, "Aladin/HipsGen "+Aladin.VERSION);

      // Gestion de la compatibilit�
      // Pour compatibilit� (A VIRER D'ICI UN OU DEUX ANS (2017?))
      while( prop.removeComment(FORCOMPATIBILITY) );
//      prop.add("#",FORCOMPATIBILITY);
//      prop.add(Constante.OLD_OBS_COLLECTION,label);
//      prop.add(Constante.OLD_HIPS_FRAME, getFrameCode() );
//      prop.add(Constante.OLD_HIPS_ORDER,prop.getProperty(Constante.KEY_HIPS_ORDER) );
//      if( minOrder>3 ) prop.add(Constante.OLD_HIPS_ORDER_MIN, minOrder+"");
      
// SANS DOUTE UN MAUVAIS COPIER/COLLER -> RIEN A VOIR  AVEC UN HPXFINDER      
//      prop.add(Constante.KEY_HIPS_TILE_WIDTH,CDSHealpix.pow2( getTileOrder())+"");


      String propFile = getHpxFinderPath()+Util.FS+Constante.FILE_PROPERTIES;
      File f = new File(propFile);
      if( f.exists() ) f.delete();
      OutputStreamWriter out = null;
      try {
         out = new OutputStreamWriter( new FileOutputStream(f), "UTF-8");
         prop.store( out, null);
      } finally {  if( out!=null ) out.close(); }
   }

   // Retourne les types de tuiles d�j� construites (en regardant l'existence de allsky.xxx associ�)
//   protected String getAvailableTileFormats() {
//      String path = BuilderAllsky.getFileName(getOutputPath(),3,0);
//      StringBuffer res = new StringBuffer();
//      for( int i=0; i<Constante.TILE_EXTENSION.length; i++ ) {
//         File f = new File(path+Constante.TILE_EXTENSION[i]);
//         if( !f.exists() ) continue;
//         if( res.length()>0 ) res.append(' ');
//         res.append(Constante.TILE_MODE[i]);
//      }
//      return res.toString();
//   }
   
// Retourne les types de tuiles d�j� construites (en regardant les tuiles d�j� construites)
   protected String getAvailableTileFormats() { return getAvailableTileFormats( getOutputPath() ); }
   protected String getAvailableTileFormats( String path ) {
      
      File root = new File(path);
      
      // Recherche du premier NorderXX trouv� o� XX est un nombre
      File norder = null;
      String [] rootList = root.list();
      if( rootList!=null ) {
         for( String s : rootList ) {
            if( s.startsWith("Norder") ) {
               try { Integer.parseInt( s.substring(6)); } catch( Exception e) { continue; }
               norder= new File(path+"/"+s);
               break;
            }
         }
      }
      if( norder==null ) return "";
      
      // Recherche du premier Dir trouv� o� XX est un nombre
      File dir = null;
      String [] norderList = norder.list();
      if( norderList!=null ) {
         for( String s : norderList ) {
            if( s.startsWith("Dir") ) {
               try { Integer.parseInt( s.substring(3)); } catch( Exception e) { continue; }
               dir = new File(norder.getAbsolutePath()+"/"+s);
               break;
            } 
         }
      }
      if( dir==null ) return "";
      
      // Recherche du premier NpixXX.ext trouv� o� XX est un nombre
      String npix = null;
      String [] dirList = dir.list();
      if( dirList!=null ) {
         for( String s : dirList ) {
            if( s.startsWith("Npix") ) {
               int j = s.lastIndexOf('.');
               if( j<0 ) continue;
               try { Integer.parseInt( s.substring(4,j)); } catch( Exception e) { continue; }
               npix = dir.getAbsolutePath()+"/"+s.substring(0,j);
               break;
            } 
         }
      }
      if( npix==null ) return "";
      
      
      StringBuilder res = new StringBuilder();
      for( int i=0; i<Constante.TILE_EXTENSION.length; i++ ) {
         File f = new File(npix+Constante.TILE_EXTENSION[i]);
         if( !f.exists() ) continue;
         if( res.length()>0 ) res.append(' ');
         res.append(Constante.TILE_MODE[i]);
      }
      return res.toString();
   }
   
   // Retourne le min et le max order en fonction des tuiles pr�sentes
   protected int [] findMinMaxOrder() {
      
      String path = getOutputPath();
      File root = new File(path);
      
      // Recherche des NorderXX o� XX est un nombre
      int min=-1,max=-1;
      String [] lists = root.list();
      if( lists==null ) lists = new String [] {};
      for( String s : lists ) {
         if( s.startsWith("Norder") ) {
            try {
               int n = Integer.parseInt( s.substring(6));
               if( min==-1 || n<min ) min=n;
               if( max==-1 || n>max ) max=n;
            } catch( Exception e) { }
         }
      }
      return new int[]{ min, max };
   }
   
// Retourne le nside des tuiles pr�sentes
   private long findTileNSide() {
      
      String path = getOutputPath();
      File root = new File(path);
      
      // Recherche du premier NorderXX trouv� o� XX est un nombre
      File norder = null;
      String [] rootList = root.list();
      if( rootList!=null ) {
         for( String s : rootList ) {
            if( s.startsWith("Norder") ) {
               try { Integer.parseInt( s.substring(6)); } catch( Exception e) { continue; }
               norder= new File(path+"/"+s);
               break;
            }
         }
      }
      if( norder==null ) return -1;
      
      // Recherche du premier Dir trouv� o� XX est un nombre
      File dir = null;
      String [] norderList = norder.list();
      if( norderList!=null ) {
         for( String s : norderList ) {
            if( s.startsWith("Dir") ) {
               try { Integer.parseInt( s.substring(3)); } catch( Exception e) { continue; }
               dir = new File(norder.getAbsolutePath()+"/"+s);
               break;
            } 
         }
      }
      if( dir==null ) return -1;
      
      // Recherche du premier NpixXX.ext trouv� o� XX est un nombre
      String npix = null;
      String [] dirList = dir.list();
      if( dirList!=null ) {
         for( String s : dirList ) {
            if( s.startsWith("Npix") ) {
               int j = s.lastIndexOf('.');
               if( j<0 ) continue;
               try { Integer.parseInt( s.substring(4,j)); } catch( Exception e) { continue; }
               npix = dir.getAbsolutePath()+"/"+s;
               break;
            } 
         }
      }
      if( npix==null ) return -1;
      
//    Chargement de la tuile en fonction de son format
      MyInputStream in = null;
      try {
         in=Util.openAnyStream( npix );
         long type = in.getType();

         Fits fits = null;
         if( (type&MyInputStream.FITS)!=0 ) {
            fits = new Fits();
            fits.loadFITS(in);
         } else {
            fits = new Fits();
            fits.loadPreview(in);
         }
         in.close();
         in=null;
         
         // Retour de sa taille
         return fits.width;

      }
      catch( Exception e ) {}
      finally { try { if( in!=null) in.close(); } catch( Exception e1 ) {} }
      
      return -1;

   }


   private void replaceKey(MyProperties prop, String oldKey, String key) {
      String s;
      if( (s=prop.getProperty(key))==null && prop.getProperty(oldKey)!=null ) {
         info("Replace properties key "+oldKey+" by "+key+" ["+s+"]");
         prop.replaceKey(oldKey,key);
      }
   }

   private void replaceKeys(MyProperties prop) {
      replaceKey(prop,Constante.OLD_HIPS_PUBLISHER,Constante.KEY_CREATOR);
      replaceKey(prop,Constante.OLD_HIPS_BUILDER,Constante.KEY_HIPS_BUILDER);
      replaceKey(prop,Constante.OLD_OBS_COLLECTION,Constante.KEY_OBS_TITLE);
      replaceKey(prop,Constante.OLD_OBS_TITLE,Constante.KEY_OBS_TITLE);
      replaceKey(prop,Constante.OLD_OBS_DESCRIPTION,Constante.KEY_OBS_DESCRIPTION);
      replaceKey(prop,Constante.OLD1_OBS_DESCRIPTION,Constante.KEY_OBS_DESCRIPTION);
      replaceKey(prop,Constante.OLD_OBS_ACK,Constante.KEY_OBS_ACK);
      replaceKey(prop,Constante.OLD_OBS_COPYRIGHT,Constante.KEY_OBS_COPYRIGHT);
      replaceKey(prop,Constante.OLD_OBS_COPYRIGHT_URL,Constante.KEY_OBS_COPYRIGHT_URL);
      replaceKey(prop,Constante.OLD_CUBE_DEPTH,Constante.KEY_CUBE_DEPTH);
      replaceKey(prop,Constante.OLD_CUBE_FIRSTFRAME,Constante.KEY_CUBE_FIRSTFRAME);
      replaceKey(prop,Constante.OLD_HIPS_RELEASE_DATE,Constante.KEY_HIPS_RELEASE_DATE);
      replaceKey(prop,Constante.OLD_HIPS_DATA_RANGE,Constante.KEY_HIPS_DATA_RANGE);
      replaceKey(prop,Constante.OLD_HIPS_PIXEL_CUT,Constante.KEY_HIPS_PIXEL_CUT);
      replaceKey(prop,Constante.OLD_HIPS_ORDER,Constante.KEY_HIPS_ORDER);
      replaceKey(prop,Constante.OLD_HIPS_ORDER_MIN,Constante.KEY_HIPS_ORDER_MIN);
      replaceKey(prop,Constante.OLD_HIPS_TILE_FORMAT,Constante.KEY_HIPS_TILE_FORMAT);
      replaceKey(prop,Constante.OLD_HIPS_TILE_WIDTH,Constante.KEY_HIPS_TILE_WIDTH);
      replaceKey(prop,Constante.OLD_NBPIXGENERATEDIMAGE,Constante.KEY_HIPS_TILE_WIDTH);
      replaceKey(prop,Constante.OLD_CLIENT_CATEGORY,Constante.KEY_CLIENT_CATEGORY);
      replaceKey(prop,Constante.OLD_HIPS_RGB_RED,Constante.KEY_HIPS_RGB_RED);
      replaceKey(prop,Constante.OLD_HIPS_RGB_GREEN,Constante.KEY_HIPS_RGB_GREEN);
      replaceKey(prop,Constante.OLD_HIPS_RGB_BLUE,Constante.KEY_HIPS_RGB_BLUE);

      String s;
      
      // Certains champs seront en plus convertis
      
      // On supprime toutes r�f�rences au PUBLISHER, et on utilise le CREATOR
      if( prop.getProperty(Constante.KEY_CREATOR_DID)==null ) {
         s= prop.getProperty(Constante.KEY_PUBLISHER_DID);
         if( s!=null ) {
            prop.insert( Constante.KEY_CREATOR_DID, s);
         } else {
            s= prop.getProperty(Constante.KEY_CREATOR_ID);
            if( s==null ) s= prop.getProperty(Constante.KEY_PUBLISHER_ID);
            if( s==null ) s="ivo://UNK.AUT";
            String obs_id = prop.getProperty(Constante.KEY_OBS_ID);
            if( obs_id!=null ) {
               String creator_did = s+"?"+obs_id;
               prop.insert( Constante.KEY_CREATOR_DID, creator_did);
            }
         }
      }
      prop.remove(Constante.KEY_PUBLISHER_DID);
      prop.remove(Constante.KEY_PUBLISHER_ID);
      
//      s = prop.getProperty(Constante.KEY_OBS_ID);
//      if( s==null ) {
//         s = prop.getProperty(Constante.KEY_CREATOR_DID);
//         if( s!=null ) {
//            int index = s.indexOf("/",6);
//            if( index>0 ) {
//               prop.insert(Constante.KEY_CREATOR_DID, s.substring(0,index));
//               prop.insert(Constante.KEY_OBS_ID, s.substring(index+1));
//               prop.remove(Constante.KEY_PUBLISHER_ID);
//            }
//         }
//      }
      
      s = prop.getProperty(Constante.OLD_HIPS_CREATION_DATE);
      if( s!=null && prop.getProperty(Constante.KEY_HIPS_CREATION_DATE)==null) {
         try {
            String v = Constante.sdf.format( HipsGen.SDF.parse(s) )+"Z";
            prop.replaceKey(Constante.OLD_HIPS_CREATION_DATE, Constante.KEY_HIPS_CREATION_DATE);
            prop.replaceValue(Constante.KEY_HIPS_CREATION_DATE, v);
         } catch( ParseException e ) { }
      }
      s = prop.getProperty(Constante.OLD_HIPS_RELEASE_DATE);
      if( s!=null && prop.getProperty(Constante.KEY_HIPS_RELEASE_DATE)==null) {
         try {
            String v = Constante.sdf.format( HipsGen.SDF.parse(s) )+"Z";
            prop.replaceKey(Constante.OLD_HIPS_RELEASE_DATE, Constante.KEY_HIPS_RELEASE_DATE);
            if( !notouch ) prop.replaceValue(Constante.KEY_HIPS_RELEASE_DATE, v);
         } catch( ParseException e ) { }
      }

      s = prop.getProperty(Constante.OLD_HIPS_FRAME);
      if( s!=null && prop.getProperty(Constante.KEY_HIPS_FRAME)==null) {
         String v = getCanonicalFrameName(s);
         prop.setProperty(Constante.KEY_HIPS_FRAME,v);
      }
      s = prop.getProperty(Constante.OLD_TARGET);
      if( s!=null ) {
         int i = s.indexOf(' ');
         prop.setProperty(Constante.KEY_HIPS_INITIAL_RA,s.substring(0,i));
         prop.setProperty(Constante.KEY_HIPS_INITIAL_DEC,s.substring(i+1));
         prop.remove(Constante.OLD_TARGET);
      }
      s = prop.getProperty(Constante.OLD_HIPS_INITIAL_FOV);
      if( s!=null ) prop.replaceKey(Constante.OLD_HIPS_INITIAL_FOV,Constante.KEY_HIPS_INITIAL_FOV);

      // Certains champs sont remplac�s sous une autre forme, � moins qu'ils n'aient �t�
      // d�j� mis � jour
      s = prop.getProperty(Constante.OLD_ISCOLOR);
      if( s==null ) s = prop.getProperty("isColor");
      if( s!=null ) {
         if( s.equals("true")
               && prop.getProperty(Constante.KEY_DATAPRODUCT_SUBTYPE)==null) prop.setProperty(Constante.KEY_DATAPRODUCT_SUBTYPE, "color");
         //         prop.remove(Constante.OLD_ISCOLOR);
      }
      s = prop.getProperty(Constante.OLD_ISCAT);
      if( s!=null ) {
         if( s.equals("true")
               && prop.getProperty(Constante.KEY_DATAPRODUCT_TYPE)==null) prop.setProperty(Constante.KEY_DATAPRODUCT_TYPE, "catalog");
         //         prop.remove(Constante.OLD_ISCAT);
      }
      s = prop.getProperty(Constante.OLD_ISCUBE);
      if( s!=null ) {
         if( s.equals("true")
               && prop.getProperty(Constante.KEY_DATAPRODUCT_TYPE)==null) prop.setProperty(Constante.KEY_DATAPRODUCT_TYPE, "cube");
         //         prop.remove(Constante.OLD_ISCUBE);
      }

      // Nettoyage des vieux mots cl�s � la mode PlanHealpix et autres
      prop.remove(Constante.OLD_ALADINVERSION);
      prop.remove(Constante.OLD_LAST_MODIFICATON_DATE);
      prop.remove(Constante.OLD_CURTFORMBITPIX);
      prop.remove(Constante.OLD_NBPIXGENERATEDIMAGE);
      prop.remove(Constante.OLD_ORDERING);
      prop.remove(Constante.OLD_ISPARTIAL);
      prop.remove(Constante.OLD_ISCOLORED);
      prop.remove(Constante.OLD_ISIAU);
      prop.remove(Constante.OLD_ARGB);
      prop.remove(Constante.OLD_TYPEHPX);
      prop.remove(Constante.OLD_LENHPX);
      prop.remove(Constante.OLD_TTYPES);
      prop.remove(Constante.OLD_TFIELDS);
      prop.remove(Constante.OLD_TILEORDER);
      prop.remove(Constante.OLD_NSIDE_FILE);
      prop.remove(Constante.OLD_NSIDE_PIXEL);
      prop.remove(Constante.OLD_ISMETA);
      prop.remove(Constante.OLD_ISCAT);
      prop.remove(Constante.KEY_SIZERECORD);
      prop.remove(Constante.KEY_OFFSET);
      prop.remove(Constante.KEY_GZ);
      prop.remove(Constante.KEY_LOCAL_DATA);
      prop.remove(Constante.KEY_ORIGINAL_PATH);
      prop.remove("hips_glu_tag");
      prop.remove("imageSourcePath");
      prop.remove("orderGeneratedImgs");
      
      // On vire les vieux mots cl�s qui �taient encore utilis�s pour compatibilit�
      prop.remove(Constante.OLD_OBS_COLLECTION);
      prop.remove(Constante.OLD_OBS_COLLECTION);
      prop.remove(Constante.OLD_HIPS_TILE_FORMAT);
      prop.remove(Constante.OLD_HIPS_FRAME);
      prop.remove(Constante.OLD_HIPS_ORDER);
      prop.remove(Constante.OLD_HIPS_PIXEL_CUT);
      prop.remove(Constante.OLD_HIPS_DATA_RANGE);
      prop.remove(Constante.OLD_ISCOLOR);
      prop.remove(Constante.OLD_ISCUBE);
      prop.remove(Constante.OLD_CUBE_DEPTH);
      prop.remove(Constante.OLD_ISCOLOR);
   }   

   /** Mise � jour du fichier des propri�t�s associ�es au survey HEALPix (propertie file dans la racine)
    * Conserve les cl�s/valeurs existantes.
    * @param key liste des cl�s � mettre � jour
    * @param value liste des valuers associ�es
    * @param overwrite si false, ne peut modifier une cl�/valeur d�j� existante
    * @param stream null pour �criture � l'endroit par d�faut
    * @throws Exception
    */
   protected void updateProperties(String[] key, String[] value,boolean overwrite) throws Exception {
      updateProperties(key,value,overwrite,null);
   }
   protected void updateProperties(String[] key, String[] value,boolean overwrite,OutputStreamWriter stream) throws Exception {

      waitingPropertieFile();
      try {
         String propFile = getOutputPath()+Util.FS+Constante.FILE_PROPERTIES;

         // Chargement des propri�t�s existantes
         prop = new MyProperties();
         File f = new File( propFile );
         if( f.exists() ) {
            if( !f.canRead() ) throw new Exception("Propertie file not available ! ["+propFile+"]");
            InputStreamReader in = new InputStreamReader( new FileInputStream(propFile), "UTF-8" );
            prop.load(in);
            in.close();
         }

         // Changement �ventuel de vocabulaire
         replaceKeys(prop);
         
         // S'il n'y a pas d'indication hips_initial... on les indique manu-militari
         String ra  = prop.get( Constante.KEY_HIPS_INITIAL_RA );
         String dec = prop.get( Constante.KEY_HIPS_INITIAL_DEC );
         String fov = prop.get( Constante.KEY_HIPS_INITIAL_FOV );
         if( ra==null || dec==null || fov==null ) {
            if( fov==null ) {

               // On va pr�f�rer prendre le moc_order indiqu� dans les properties
               // pour �viter de r�cup�rer le bug sur le MocOrder
               try {
                  int n = Integer.parseInt( prop.get("moc_order"));
                  SMoc mm = new SMoc();
                  mm.setMocOrder(n);
                  fov =  mm.getAngularRes()+"";
               } catch( Exception e) {
                  fov = moc.getAngularRes()+"";
               }
               prop.replaceValue( Constante.KEY_HIPS_INITIAL_FOV,fov);
            }
            if( ra==null || dec==null ) {
               Healpix hpx = new Healpix();
               if( moc.isFull() ) { ra="0"; dec="+0"; }
               else {
                  try {
                     int o = moc.getMocOrder();
                     long pix = moc.valIterator().next();
                     double coo[] = hpx.pix2ang(o,pix);
                     ra = coo[0]+"";
                     dec = coo[1]+"";
                  } catch( Exception e ) { }
               }
               prop.replaceValue( Constante.KEY_HIPS_INITIAL_RA,ra);
               prop.replaceValue( Constante.KEY_HIPS_INITIAL_DEC,dec);
            }
         }


         String v;
         // Mise � jour des propri�t�s
         for( int i=0; i<key.length; i++ ) {
            
            if( !notouch && key[i].equals(Constante.KEY_HIPS_RELEASE_DATE) ) {
               // Conservation de la premi�re date de processing si n�cessaire
               if( prop.getProperty(Constante.KEY_HIPS_CREATION_DATE)==null
                     && (v=prop.getProperty(Constante.KEY_HIPS_RELEASE_DATE))!=null) {
                  prop.setProperty(Constante.KEY_HIPS_CREATION_DATE, v);
               }
            }

            // Je n'ajoute une proposition de cl� que si elle n'y est pas d�j�
            if( key[i].charAt(0)=='#') {
               if( prop.getProperty(key[i].substring(1))!=null ) continue;
            }

            // insertion ou remplacement
            if( overwrite ) {
               if( value[i]==null ) prop.remove(key[i]);
               else if( value[i]!=null ) prop.setProperty(key[i], value[i]);

               // insertion que si nouveau
            } else {
               v = prop.getProperty(key[i]);
               if( v==null && value[i]!=null ) prop.setProperty(key[i], value[i]);
            }

            // Suppression d'une ancienne proposition de cl� �ventuelle
            if( value[i]!=null && key[i].charAt(0)!='#') {
               if( prop.getProperty("#"+key[i])!=null ) prop.remove("#"+key[i]);
            }
            
         }
         
         // M�morisation des param�tres de g�n�rations
         if( scriptCommand!=null ) {
            int n=0;
            while( prop.getProperty("hipsgen_params"+(n==0?"":"_"+n))!=null) n++;
            prop.add("hipsgen_date"+(n==0?"":"_"+n),getNow());
            prop.add("hipsgen_params"+(n==0?"":"_"+n),scriptCommand);
            scriptCommand=null;
         }
         
         // Gestion de la compatibilit�
         // Pour compatibilit� (A VIRER D'ICI UN OU DEUX ANS (2017?))
         while( prop.removeComment(FORCOMPATIBILITY) );
//         prop.add("#",FORCOMPATIBILITY);
//         prop.add(Constante.OLD_OBS_COLLECTION,getLabel());
//         prop.add(Constante.OLD_HIPS_FRAME, getFrameCode() );
//         prop.add(Constante.OLD_HIPS_ORDER,prop.getProperty(Constante.KEY_HIPS_ORDER) );
//         String fmt = getAvailableTileFormats();
//         if( fmt.length()>0 ) prop.add(Constante.OLD_HIPS_TILE_FORMAT,fmt);
//         if( fmt.indexOf("fits")>=0 && cut!=null ) {
//            if( cut[0]!=0 || cut[1]!=0 ) prop.add(Constante.OLD_HIPS_PIXEL_CUT, Util.myRound(bscale*cut[0]+bzero)+" "+Util.myRound(bscale*cut[1]+bzero));
//            if( cut[2]!=0 || cut[3]!=0 ) prop.add(Constante.OLD_HIPS_DATA_RANGE,Util.myRound(bscale*cut[2]+bzero)+" "+Util.myRound(bscale*cut[3]+bzero));
//         }
//         if( isColor() ) prop.add(Constante.OLD_ISCOLOR,"true");
//         if( isCube() ) {
//            prop.add(Constante.OLD_ISCUBE,"true");
//            prop.add(Constante.OLD_CUBE_DEPTH,depth+"");
//         }
         
         // Remplacement du pr�c�dent fichier
         if( stream!=null ) prop.store( stream, null);
         else {
            String tmp = getOutputPath()+Util.FS+Constante.FILE_PROPERTIES+".tmp";
            File ftmp = new File(tmp);
            if( ftmp.exists() ) ftmp.delete();
            File dir = new File( getOutputPath() );
            if( !dir.exists() && !dir.mkdir() ) throw new Exception("Cannot create output directory");
            OutputStreamWriter out = null;
            try {
               out = new OutputStreamWriter( new FileOutputStream(ftmp), "UTF-8");
               prop.store( out, null);


            } finally {  if( out!=null ) out.close(); }

            if( f.exists() && !f.delete() ) throw new Exception("Propertie file locked ! (cannot delete)");
            if( !ftmp.renameTo(new File(propFile)) ) throw new Exception("Propertie file locked ! (cannot rename)");
         }

      }
      finally { releasePropertieFile(); }
   }
   
   /** Lecture des propri�t�s */
   protected void loadProperties() throws Exception {
      waitingPropertieFile();
      try {
         String propFile = getOutputPath()+Util.FS+Constante.FILE_PROPERTIES;
         prop = new MyProperties();
         File f = new File( propFile );
         if( f.exists() ) {
            if( !f.canRead() ) throw new Exception("Propertie file not available ! ["+propFile+"]");
            InputStreamReader in = new InputStreamReader( new BufferedInputStream( new FileInputStream(propFile) ), "UTF-8");
            prop.load(in);
            in.close();

            // Changement �ventuel de vocabulaire
            replaceKeys(prop);
         }
      }
      finally { releasePropertieFile(); }
   }

   // Gestion d'un lock pour acc�der de mani�re exclusive aux fichiers des propri�t�s
   transient private boolean lock;
   private final Object lockObj= new Object();
   private void waitingPropertieFile() {
      while( !getLock() ) {
         try { Thread.currentThread().sleep(100); } catch( InterruptedException e ) {  }
      }
   }
   private void releasePropertieFile() { lock=false; }
   private boolean getLock() {
      synchronized( lockObj ) {
         if( lock ) return false;
         lock=true;
         return true;
      }
   }

   protected double[] gal2ICRSIfRequired(double al, double del) { return gal2ICRSIfRequired(new double[]{al,del}); }
   protected double[] gal2ICRSIfRequired(double [] aldel) {
      if( getFrame()==Localisation.ICRS ) return aldel;
      Astrocoo coo = (Astrocoo) COO_GAL.clone();
      coo.set(aldel[0],aldel[1]);
      coo.convertTo(AF_ICRS1);
      aldel[0] = coo.getLon();
      aldel[1] = coo.getLat();
      return aldel;
   }
   protected double[] ICRS2galIfRequired(double al, double del) { return ICRS2galIfRequired(new double[]{al,del}); }
   protected double[] ICRS2galIfRequired(double [] aldel) {
      if( getFrame()==Localisation.ICRS ) return aldel;
      Astrocoo coo = (Astrocoo) COO_EQU.clone();
      coo.set(aldel[0], aldel[1]);
      coo.convertTo(AF_GAL1);
      aldel[0] = coo.getLon();
      aldel[1] = coo.getLat();
      return aldel;
   }

   public int[] xy2hpx = null;
   public int[] hpx2xy = null;

   /** M�thode r�cursive utilis�e par createHealpixOrder */
   private void fillUp(int[] npix, int nsize, int[] pos) {
      int size = nsize * nsize;
      int[][] fils = new int[4][size / 4];
      int[] nb = new int[4];
      for (int i = 0; i < size; i++) {
         int dg = (i % nsize) < (nsize / 2) ? 0 : 1;
         int bh = i < (size / 2) ? 1 : 0;
         int quad = (dg << 1) | bh;
         int j = pos == null ? i : pos[i];
         npix[j] = npix[j] << 2 | quad;
         fils[quad][nb[quad]++] = j;
      }
      if (size > 4)
         for (int i = 0; i < 4; i++)
            fillUp(npix, nsize / 2, fils[i]);
   }

   /** Creation des tableaux de correspondance indice Healpix <=> indice XY */
   public void createHealpixOrder(int order) {
      int nside = (int) CDSHealpix.pow2(order);
      if( xy2hpx!=null && xy2hpx.length == nside*nside ) return;  // d�ja fait
      xy2hpx = new int[nside * nside];
      hpx2xy = new int[nside * nside];
      fillUp(xy2hpx, nside, null);
      for (int i = 0; i < xy2hpx.length; i++) hpx2xy[xy2hpx[i]] = i;
   }

   /**
    * Retourne l'indice XY en fonction d'un indice Healpix => n�cessit�
    * d'initialiser au pr�alable avec createHealpixOrdre(int)
    */
   final public int xy2hpx(int hpxOffset) {
      return xy2hpx[hpxOffset];
   }

   /**
    * Retourne l'indice XY en fonction d'un indice Healpix => n�cessit�
    * d'initialiser au pr�alable avec createHealpixOrdre(int)
    */
   final public int hpx2xy(int xyOffset) {
      return hpx2xy[xyOffset];
   }

   /** Retourne le nombre d'octets disponibles en RAM */
   public long getMem() {
      return Runtime.getRuntime().maxMemory()-
            (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
   }
   
   private Tile2HPX tile2Hpx = null;
   
   protected void updateHeader(Fits fits,int order,long npix) {
      if( fits.headerFits==null ) return;
      if( creator!=null) fits.headerFits.setKeyValue("ORIGIN", creator);
      fits.headerFits.setKeyValue("CPYRIGHT", "See HiPS properties file");
      fits.headerFits.setKeyValue("COMMENT", "HiPS FITS tile generated by Aladin/Hipsgen "+Aladin.VERSION);
      fits.headerFits.setKeyValue("ORDER", ""+order);
      fits.headerFits.setKeyValue("NPIX", ""+npix);
      
      // G�n�ration des mots cl�s WCS dans l'ent�te des tuiles (appel code FX)
      try {
         tile2Hpx = new Tile2HPX(order, fits.width, frame==Localisation.ICRS ? WCSFrame.EQU: 
                  frame==Localisation.ECLIPTIC ? WCSFrame.ECL : WCSFrame.GAL );
         Map<String, String> map = tile2Hpx.toFitsHeader(npix);
         for(Map.Entry<String, String> e : map.entrySet()) {
            
            // Je vire les commentaires qui foutent le bouzin
            String key = e.getKey().trim();
            String val=e.getValue();
            int i=val.indexOf('/');
            if( i>0 ) val = val.substring(0, i).trim();
            
            fits.headerFits.setKeyValue(key, val);
         }
      } catch( Exception e ) {
         e.printStackTrace();
      }

   }
   
   /** Supprime toutes les m�triques (en cas de modif/update d'un HiPS */
   public void resetMetrics() {
      setPropriete(Constante.KEY_HIPS_CHECK_CODE, null );
      setPropriete(Constante.KEY_HIPS_ESTSIZE, null );
      setPropriete(Constante.KEY_HIPS_NB_TILES, null );
   }
   
   /** Supprime tous les check codes */
   public void resetCheckCode() {
      hipsCheckCode=null;
      resetMetrics();
   }
   
   /** Supprime le Check code concernant un format sp�cifique du hipsCheckCode du context
    * Par exemple "png:46574930 fits:2847219305 jpg:853095383" => "png:46574930 jpg:853095383"
    * @param fmt Le format dont on veut supprimer le check code
    */
   public void resetCheckCode(String fmt) {
      hipsCheckCode = resetCheckCode( fmt, hipsCheckCode ); 
      resetMetrics();
   }

   /** Ajoute/remplace le Check code concernant un format sp�cifique du hipsCheckCode du context
    * Par exemple "png:46574930 jpg:853095383" => "png:46574930 fits:2847219305 jpg:853095383"
    * @param fmt Le format dont on veut ajouter/modfier le check code
    * @param checkCode  (sous la forme d'un entier 32 bits = cl� de hash)
    */
   public void addCheckCode(String fmt, int checkCode) { 
      hipsCheckCode = addCheckCode( fmt, checkCode, hipsCheckCode ); 
   }

   
   /** Extrait le Check code correspondant au format indiqu� � partir d'une chaine hipsCheckCode
    * du genre "png:46574930 fits:2847219305"
    * @param fmt Le format souhait� (ex:png)
    * @param hipsCheckCode la chaine contenant tous les check codes format par format
    * @return le check code du format sp�cifi� (int sous la forme d'un String)
    */
   static public String getCheckCode(String fmt, String hipsCheckCode) {
      if( hipsCheckCode==null ) return null;
      Tok tok = new Tok(hipsCheckCode," ,");
      while( tok.hasMoreTokens() ) {
         String s = tok.nextToken();
         if( s.startsWith(fmt+":") ) return s.substring(fmt.length()+1);
      }
      return null;
   }
   
   /** Supprime le Check code concernant un format sp�cifique d'une chaine hhipsCheckCode
    * Par exemple "png:46574930 fits:2847219305 jpg:853095383" => "png:46574930 jpg:853095383"
    * @param fmt Le format dont on veut supprimer le check code
    * @param hipsCheckCode la chaine hips_crc en entr�e
    * @return la chaine hips_check_code en sortie, ou null si plus aucun Check code
    */
   static public String resetCheckCode(String fmt, String hipsCheckCode) {
      if( hipsCheckCode==null ) return null;
      StringBuilder r=null;
      Tok tok = new Tok(hipsCheckCode," ,");
      while( tok.hasMoreTokens() ) {
         String s = tok.nextToken();
         if( s.startsWith(fmt+":") ) continue;
         if( r==null ) r = new StringBuilder();
         else r.append(' ');
         r.append( s );
      }
      return r.toString();
   }

   /** Ajoute/remplace le CRC concernant un format sp�cifique d'une chaine hipsCheckCode
    * Par exemple "png:46574930 jpg:853095383" => "png:46574930 fits:2847219305 jpg:853095383" 
    * @param fmt Le format dont on veut ajouter/modifier le check code
    * @param checkCode le check code (sous la fvorme d'un entier 32 bits = cl� de hash)
    * @param hipsCheckCode la chaine hips_crc ou null en entr�e
    * @return la chaine hipsCheckCode en sortie
    */
   static public String addCheckCode(String fmt, int checkCode, String hipsCheckCode) {
      hipsCheckCode = resetCheckCode(fmt,hipsCheckCode);
      return (hipsCheckCode==null ? "":hipsCheckCode+" ") + fmt+":"+checkCode;
   }


}
