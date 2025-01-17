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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import javax.swing.SwingUtilities;

import cds.aladin.stc.STCCircle;
import cds.aladin.stc.STCObj;
import cds.aladin.stc.STCPolygon;
import cds.allsky.Constante;
import cds.astro.Coo;
import cds.fits.HeaderFits;
import cds.moc.Healpix;
import cds.moc.MocCell;
import cds.moc.SMoc;
import cds.tools.Util;
import cds.tools.pixtools.CDSHealpix;

/**
 * Gestion d'un plan image pour l'affichage du ciel de fond
 *
 * Les classes concern�es sont:
 * - PlanBG : cette classe
 * - HealpixKey : g�re un losange Healpix
 * - HealpixLoader (classe interne) : gestion asynchrone de la liste des HealpixKeys
 * - Loader (classe interne) : chargement asynchrone des donn�es
 *
 * METHODE :
 * Le fond du ciel est subdivis� dans des losanges HEALPIX accessibles directement via
 * des URLs du type http://serveur/SURVEY.NorderNNN.NpixXXX.hpx (ou .jpg). Les losanges
 * les plus appropri�s sont charg�es du r�seau ou d'un cache disque en commen�ant par les
 * plus mauvaises r�solutions. Le trac� des losanges est effectu� � chaque repaint() de chaque
 * vue d'Aladin (=> doit �tre au max de qq ms) par transform�e affine approchant les coordonn�es
 * c�lestes des 4 coins dans la solution astrom�trique de la vue. Les losanges sont gard�s en
 * m�moire tant qu'ils sont trac�s.
 *
 * Nota : Les coordonn�es des 4 coins peuvent �tre imm�diatement connues � partir de l'ordre
 * et du num�ro HEALPIX du losange.
 *
 * les losanges ont g�n�ralement 512x512 pixels utilisant eux-m�mes la subdivision HEALPIX d'ordre +9
 * par rapport � l'ordre du num�ro npix du fichier. Ces pixels sont ordonn�es en lignes
 * et colonnes "image", et non pas dans l'ordre de num�rotation HEALPIX nested (�a afficherait
 * n'importe quoi)
 *
 *
 * La liste des serveurs est fournie par des enregistrements GLU
 * => classe GluSky, liste dans Glu.vGluSky
 * Exemple :
 * %A 2MASSK.hpx
 * %D 2MASS K survey in HEALPIX access
 * %O CDS'aladin
 * %Z ALADIN
 * %U http://alinda.u-strasbg.fr/~oberto
 * %Aladin.Profile >=5.9 beta hpx
 * %Aladin.Label InfraRed (2MASS K)
 * %Aladin.Survey K
 *
 * L'algorithme de tracage fonctionne de la mani�re suivante (voir draw(...)) :
 * 1) Boucle depuis la plus mauvaise r�solution HEALPIX (ordre 2 ou 3)
 *    jusqu'� la r�solution courante de la vue
 *    1.1) D�termination de tous les losanges HEALPIX candidats (qui couvrent la vue)
 *         => voir classe HealpixKey
 *    1.2) Affichage (ou demande d'affichage => pixList) des losanges concern�es
 *         ssi leurs fils ne vont pas �tre tous affich�s
 *    1.3) R�veil du Thread de gestion des losanges (class HealpixLoader)
 *
 * L'algorithme du HealpixLoader fonctionne de la mani�re suivante :
 * 1) Sommeil
 * 2) R�veil (par timeout ou par wakeUp())
 *     2.1) Gestion du cache disque (suppression des plus vieux fichiers acc�d�s si n�cessaire)
 *     2.2) Purge des losanges en m�moire inutilis�e (temps de vie>dernier affichage forc�)
 *     2.3) Changement des �tats des losanges (ASKING=>TOBELOAD...) en vue de leur
 *          chargement par les threads de gestion du cache et de gestion du net
 *
 * 2 threads, les loaders, g�rent les chargements (cache et r�seau)
 * L'algorithme des Loader fonctionne de la mani�re suivante:
 * 1) Sommeil
 * 2) R�veil (par timeout ou wakeUp())
 *     2.1) Chargement du "meilleur" losange � �tre TOBELOAD..
 *
 * @author Pierre Fernique + Ana�s Oberto [CDS]
 */
public class PlanBG extends PlanImage {

   static final boolean NOALLSKY = false;

   static final int DRAWPIXEL=0;
   static final int DRAWPOLARISATION=1;

   // pour l'affichage de la polarisation
   protected boolean segmentIAUConv = false;
   private float segmentLenFactor = 1f;
   private int segmentThickness = 1;
   private float segmentDensityFactor = 1f;

   private Color oc=null;
   protected int drawMode=DRAWPIXEL;
   protected void setDrawMode(int mode) {
      drawMode=mode;
      if( drawMode==DRAWPOLARISATION ) {
         if( opacityLevel<0.1 ) setOpacityLevel(1.0f);
         if( oc==null ) oc=c = Couleur.getNextDefault(aladin.calque);
         else c=oc;
      } else c=Color.black;

      if( mode==DRAWPOLARISATION ) type = ALLSKYPOL;
      else type = ALLSKYIMG;
   }


   static final String CACHE = "Background";
   static long MAXCACHE=4*1024*1024;                // taille max du cache en Ko
   static final protected int LIVETIME = 3*1000;    // temps de vie des losanges en m�moire (ms)

   protected MyProperties prop = null; // La liste des propri�t�s associ�es au HiPS

   protected String gluTag=null;   // Identificateur dans le dico GLU
   protected String survey;        // Nom du background
   protected String urlSuffix=null; // Param�tres de l'URL (HiPS dynamique)
   protected String version="";    // Num�ro de version du background si existant (ex: -v1.2)
   protected String url;           // Pr�fixe de l'url permettant d'acc�der au background
   protected int minOrder=3;       // Ordre HEALPIX "fichier" min du background
   protected int maxOrder=14;       // Ordre HEALPIX "fichier" max du background
   protected Hashtable<String,HealpixKey> pixList;      // Liste des losanges disponibles
   //   protected HealpixKey allsky;    // Losanges sp�ciaux d'acc�s � tout le ciel niveau 3
   protected HealpixLoader loader;   // G�re le chargement des losanges
   protected boolean hasDrawnSomething=false;   // True si le dernier appel � draw() � dessiner au moins un losange
   protected boolean allWaitingKeysDrawn=false;   // true si tous les losanges de r�solution attendue ont �t� trac�s
   protected boolean useCache=true;
   protected boolean cube=false;     // true s'il s'agit d'un HiPS cube
   protected boolean color=false;    // true si le survey est fourni en couleur (JPEG|PNG)
   protected boolean colorPNG=false; // true si le survey est fourni en couleur PNG
   protected boolean colorUnknown=false; // true si on ne sait pas a priori si le survey est en JPEG|PNG couleur ou non
   public boolean fitsGzipped=false; // true si le survey est fourni en true pixels (FITS) mais gzipp�
   public boolean truePixels=false;  // true si le survey est fourni en true pixels (FITS)
   public boolean inFits=false;      // true: Les losanges originaux peuvent �tre fournis en FITS
   public boolean inJPEG=false;      // true: Les losanges originaux peuvent �tre fournis en JPEG
   public boolean inPNG=false;       // true: Les losanges originaux peuvent �tre fournis en PNG
   protected boolean hasMoc=false;     // true si on on peut disposer du MOC correspondant au survey
   protected boolean hasHpxFinder=false;     // true si on on peut disposer du HpxFinder correspondant au survey
   protected int frameOrigin=Localisation.ICRS; // Mode Healpix du survey (GAL, EQUATORIAL...)
   protected int frameDrawing=aladin!=null && aladin.configuration!=null ? aladin.configuration.getFrameDrawing() : 0;   // Frame de trac�, 0 si utilisation du rep�re g�n�ral
   protected boolean local;
   protected boolean live=false;       // True si le HiPS est un survey "live" => tester les dates de chaque losange individuellement et pas seulement globalement
   protected boolean loadMocNow=false; // Demande le chargement du MOC d�s le d�but
   protected String pixelRange=null;   // Valeur du range si d�crit dans le fichier properties "min max" (valeur physique, pas raw)
   protected String pixelCut=null;     // Valeur du cut si d�crit dans le fichier properties "min max" (valeur physique, pas raw)
   protected int transferFct4Fits=LINEAR;  // Fonction de transfert si d�crite dans le fichier properties (mots-cl�s reconnus: asinh, log, linear, pow2)
   protected int transferFct4Preview=LINEAR;  // Fonction de transfert pour le preview (par d�faut celui de la configuration user)
   protected boolean flagNoTarget=false; // Par d�faut pas de target indiqu�e
   private boolean flagWaitAllSky;     // En attente du chargement AllSky
   protected boolean hasAllSky=true;    // false si le HiPS n'a pas de AllSky
   protected int tileOrder=-1;        // Ordre des losanges

   protected int RGBCONTROL[] = { 0,128, 255 , 0,128, 255 , 0,128, 255 };
   protected int RGBControl[];
   
   protected boolean specificProj = false;  // true si on utilise une projection sp�cifique pour ce plan


   // Gestion du cache
   //   static volatile long cacheSize=MAXCACHE-1024*2;   // Taille actuelle du cache
   static volatile long cacheSize=-1;   // Taille actuelle du cache
   static private Object cacheLock = new Object();

   // Polarisation : facteur multiplicatif pour longueur segments
   private int polaScaleFactor = 40;


   // Gestion de la m�moire
   protected long memSize=0;   // Taille actuelle de la m�moire utilis� par le fond duc iel

   // pour classes d�riv�es
   protected PlanBG(Aladin aladin) {
      super(aladin);
      initCache();
      suiteSpecific();
      type=Plan.ALLSKYIMG;
   }

   /**
    * Cr�ation d'un plan Healpix
    * @param aladin
    * @param to
    * @param c Coordonn�e centrale ou null si non sp�cifi�e
    * @param radius Taille du champ en degr�s, ou <=0 si non sp�cifi�
    */
   protected PlanBG(Aladin aladin, TreeObjDir to, String label, Coord c,double radius,String startingTaskId) {
      super(aladin);
      this.startingTaskId = startingTaskId;
      initCache();

      gluTag = to.getID();
      id = to.internalId;
      url = to.getUrl();
      survey = to.label;
      version = to.version;
      minOrder = to.minOrder;
      maxOrder = to.maxOrder;
      useCache = to.useCache();
      local=to.local;
      loadMocNow=to.loadMocNow();
      frameOrigin=to.frame;
      description=to.description;
      verboseDescr=to.verboseDescr;
      ack=to.ack;
      copyright=to.copyright;
      copyrightUrl=to.copyrightUrl;
      co=c;
      coRadius=radius;
      if( label!=null && label.trim().length()>0 ) setLabel(label);
      setSpecificParams(to);
      //      if( copyrightUrl==null ) copyrightUrl=url;
      aladin.trace(3,"AllSky creation: "+to.toString1()+(c!=null ? " around "+c:""));
      suite();
   }

   /** Mise � jour des param�tres globaux du plan � partir du chargement des tuiles */
   protected void initTileParam(int width,int height,int bitpix,byte [] pixelsOrigin,byte [] pixels) {
      if( color ) return;   // pas de maj pour les plans couleurs (pas sur de comprendre - PF jan 2020)
      
      this.pixelsOrigin = pixelsOrigin;
      this.pixels = pixels;
      this.bitpix=bitpix;
      this.npix=Math.abs(bitpix)/8;
      this.naxis1=this.width=width;
      this.naxis2=this.height=height;
      
      if( hasRecutListener() ) getRecutListener().initTileParam(width,height);
   }

   // Supprime le cache en le renommant simplement => il sera nettoy� par la suite
   // Puis recr�e un r�pertoire pour accueillir les nouvelles donn�es
   private void resetCache() {
      String dirname = getCacheDir()+Util.FS+getCacheName();
      File f = new File(dirname);
      for( int i=1; i<10; i++ ) {
         if( f.renameTo(new File(getCacheDir()+Util.FS+getCacheName()+"."+i+".old")) ) break;
      }
      (new File(getCacheDir()+Util.FS+getCacheName())).mkdir();
      aladin.trace(3,"HEALPix local cache for "+getCacheName()+" is out of date => renamed => will be removed");
   }

   /** Charge les propri�t�s � partir du fichier "properties" et en profite
    * 1) pour d�terminer le meilleur site miroir (le cas �ch�ant)
    * 2) pour v�rifier que le cache est � jour, en comparant les dates du fichier "properties" local et distant
    */
   protected MyProperties loadPropertieFile() {
      if( prop!=null ) return prop;
      String dateRef=null;
      
      boolean local=!(url.startsWith("http:") || url.startsWith("https:") ||url.startsWith("ftp:"));
      try {
         InputStream in=null;

         // S'il s'agit d'un produit local, acc�s direct sans se poser de questions
         if( local ) in = new FileInputStream(new File(url+Util.FS+Constante.FILE_PROPERTIES));
         else {

            // Eventuellement changera de site Web s'il y a mieux
            // MAINTENANT ON LE FAIT EN AMONT
//            checkSite(false);

            String cacheFile = getCacheDir()+Util.FS+getCacheName()+Util.FS+Constante.FILE_PROPERTIES;
            
            File f = new File(cacheFile);
            String urlFile = url+"/"+Constante.FILE_PROPERTIES;
            
            if( isDynHiPS() ) {
               urlFile += "?"+urlSuffix;
               System.out.println("Scanning for "+urlFile);
            }

            HttpURLConnection conn = (HttpURLConnection) (new URL(urlFile)).openConnection();

            // Ne charge la version distante que si elle est plus r�cente que celle du cache
            if( useCache && f.exists() ) {
               conn.setIfModifiedSince( f.lastModified() );
               prop = new MyProperties();
               InputStreamReader in1 = new InputStreamReader(new FileInputStream(f));
               prop.load(in1,true,false);
               in1.close();
               dateRef = prop.getProperty(Constante.KEY_HIPS_RELEASE_DATE);
               if( dateRef==null ) dateRef = prop.getProperty(Constante.OLD_HIPS_RELEASE_DATE,"");
               //               System.out.println("Cache hips_release_date="+dateRef);
            }

            MyInputStream dis = null;
            try {
               in = conn.getInputStream();
               int code = conn.getResponseCode();
               if( code==304 ) throw new Exception();

               // R�initialisation du cache et r��criture ?
               if( useCache ) {
                  dis = new MyInputStream(in);
                  byte [] buf = dis.readFully();
                  dis.close();

                  // On va v�rifier tout de m�me que la date indiqu�e dans le fichier
                  // properties est bien diff�rente de celle de la version d�j� en cache
                  // (n�cessaire dans le cas de sites miroirs, ou d'acc�s via CGI FX)
                  prop = new MyProperties();
                  InputStreamReader in1 = new InputStreamReader( new ByteArrayInputStream(buf) );
                  prop.load(in1,true,false);
                  in1.close();
                  String dateRef1= prop.getProperty(Constante.KEY_HIPS_RELEASE_DATE);
                  if( dateRef1==null ) dateRef1 = prop.getProperty(Constante.OLD_HIPS_RELEASE_DATE,"");
                  //                  System.out.println("Remote hips_release_date="+dateRef1);
                  if( dateRef1.equals(dateRef) ) {
                     //                     aladin.trace(4,"PlanBG.loadPropertieFile() => hips_release_date identical !");
                     throw new Exception();
                  }

                  RandomAccessFile fcache = null;
                  try {
                     resetCache();
                     f = new File(cacheFile);
                     fcache = new RandomAccessFile(f, "rw");
                     fcache.write(buf);
                  }
                  catch( Exception e ) { e.printStackTrace(); }
                  finally { if( fcache!=null ) fcache.close(); }
               }

               // La version dans le cache est la bonne.
            } catch( Exception e ) {
               
               // Pour �viter un nettoyage intempestif du cache
               touch( new File(getCacheDir()+Util.FS+getCacheName()) ); 
               touch( new File(cacheFile) );
               
               aladin.trace(3,"HEALPix local cache for "+survey+" is ok ["+cacheFile+"]");
            } finally {
               if( dis!=null ) dis.close();
            }

            // Faut bien lire les prori�t�s tout de m�me
            if( f.exists() ) in = new FileInputStream(f);

         }
         if( in==null ) throw new Exception();
         prop = new MyProperties();
         
         prop.load( new InputStreamReader(in),true,false);
         in.close();
      } catch( Exception e ) { prop=null; }
      return prop;
   }
   
   static final void touch(File f) { f.setLastModified( System.currentTimeMillis() ); }

   protected boolean scanMetadata() {
      MyInputStream in=null;
      try {
         in = Util.openAnyStream(url+Util.FS+Constante.FILE_METADATATXT);
         byte [] res = in.readFully();
         HeaderFits h = new HeaderFits();
         h.readFreeHeader( new String(res) );
         headerFits = new FrameHeaderFits(h);
      }
      catch( Exception e ) { return false; }
      finally { try { in.close(); } catch( Exception e) {} }

      return true;
   }
   
   // D�termination de l'identificateur du HiPs, m�thode post Markus, pr�-Markus, 
   // et m�me encore avant
   static public String getHiPSID(MyProperties prop) {
      String s = prop.getProperty(Constante.KEY_CREATOR_DID);
     if( s==null ) s = prop.getProperty(Constante.KEY_PUBLISHER_DID);
      if( s==null ) {
         s = prop.getProperty(Constante.KEY_OBS_ID);
         if( s!=null ) {
            String s1 = prop.getProperty(Constante.KEY_CREATOR_ID);
            if( s1==null )  s1 = prop.getProperty(Constante.KEY_PUBLISHER_ID);
            if( s1!=null ) s=s1+"?"+s;
            else s=null;
         }
      }
      if( s==null ) s = prop.getProperty(Constante.OLD_CREATOR_DID);
      if( s.startsWith("ivo://") ) s = s.substring(6);
      return s;
   }
   
   // Positionne l'ordre des mirroirs en fonction de la derni�re session (si possible)
   protected boolean checkSite() {
      
      // Positionne l'ordre des mirroirs en fonction de la derni�re session (si possible)
      boolean lookForFaster = !aladin.glu.checkSiteHistory(gluTag);
      
      // On positionne la meilleure solution qu'on avait d�tect�
      if( !lookForFaster ) {
         try {
            URL u = aladin.glu.getURL(gluTag, "", false,true,1);
            if( u!=null ) {
               url=u.toString();
            }
         } catch( Exception e ) { }
      }
      
      return lookForFaster;
   }
   
   /** Chargement des propri�t�s du HiPS.
    * On en profite pour v�rifier s'il n'y aurait pas un site miroirs plus rapide
    */
   protected boolean scanProperties() {
      boolean rep=true;
      boolean alternative=true;
      boolean lookForFaster=false;
      
      // Pas de mirroir pour un acc�s local ou direct URL
      if( !( local || id!=null && id.startsWith(TreeObjDir.DIRECT) )) {

         // Positionne l'ordre des mirroirs en fonction de la derni�re session (si possible)
         lookForFaster = checkSite();

         // V�rifie qu'il y a au-moins une alternative
         URL u = gluTag==null ? null : aladin.glu.getURL(gluTag,"",false,false,2);
         if( u==null ) alternative=false;
      }
      
      // Pas de r�ponse imm�diate => on cherche un autre site tout de suite
      if( !scanProperties1() && alternative ) {
         Aladin.trace(3,"HiPS server unreachable ["+url+"] ! Trying another...");
         checkSite(false);
         rep = scanProperties1();
         
      // Une r�ponse, mais peut �tre y a-t-il plus rapide => on teste en parall�le
      } else if( alternative && lookForFaster ) {
         Aladin.trace(3,"HiPS server OK ["+url+"], looking for a faster...");
         (new Thread() { public void run() { checkSite(false); } }).start();
      }
      return rep;
   }

   private boolean scanProperties1() {
      // Information suppl�mentaire par le fichier properties ?
      try {
         MyProperties prop = loadPropertieFile();
         if( prop==null ) throw new Exception();

         Aladin.trace(4,"PlanBG.setSpecificParams() found a \"properties\" file");

         // Frame
         int frame=-1;
         String strFrame = prop.getProperty(Constante.KEY_HIPS_FRAME,"X");
         if( strFrame.equals("equatorial") || isPlanet(strFrame) )  frame=Localisation.ICRS;
         else if( strFrame.equals("galactic"))  frame=Localisation.GAL;
         else if( strFrame.equals("ecliptic"))  frame=Localisation.ECLIPTIC;
         
         // Body
         if( isPlanet(strFrame) ) setBody( strFrame.trim().toLowerCase() ) ;
         else setBody(Plan.BODYSKY);

         // Pour compatibilit� avec le vieux vocabulaire
         if( strFrame.equals("X")) {
            strFrame = prop.getProperty(Constante.OLD_HIPS_FRAME,"X");
            char c1 = strFrame.charAt(0);
            if( c1=='C' || c1=='Q' ) frame=Localisation.ICRS;
            else if( c1=='E' ) frame=Localisation.ECLIPTIC;
            else if( c1=='G' ) frame=Localisation.GAL;
         }

         if( frame!=-1 && frame!=frameOrigin ) {
            aladin.trace(1,"Coordinate frame found in properties file ("+Localisation.getFrameName(frame)
                  +") differs from the remote information ("+Localisation.getFrameName(frameOrigin)+") => assume "+Localisation.getFrameName(frame));
            frameOrigin=frame;
         }

         String s;
         s = prop.getProperty(Constante.KEY_OBS_TITLE);
         if( s==null ) s = prop.getProperty(Constante.OLD_OBS_TITLE);
         if( s!=null ) description = s;

         s = prop.getProperty(Constante.KEY_OBS_DESCRIPTION);
         if( s==null ) s = prop.getProperty(Constante.OLD_OBS_DESCRIPTION);
         if( s!=null ) verboseDescr = s;

         s = prop.getProperty(Constante.KEY_OBS_ACK);
         if( s==null ) s = prop.getProperty(Constante.OLD_OBS_ACK);
         if( s!=null ) ack = s;

         s = prop.getProperty(Constante.KEY_OBS_COPYRIGHT);
         if( s==null ) s = prop.getProperty(Constante.OLD_OBS_COPYRIGHT);
         if( s!=null ) copyright = s;

         s = prop.getProperty(Constante.KEY_OBS_COPYRIGHT_URL);
         if( s==null ) s = prop.getProperty(Constante.OLD_OBS_COPYRIGHT_URL);
         if( s!=null ) copyrightUrl = s;

         s = prop.getProperty(Constante.KEY_HIPS_DATA_RANGE);
         if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_DATA_RANGE);
         if( s!=null ) pixelRange = s;

         s = prop.getProperty(Constante.KEY_HIPS_PIXEL_CUT);
         if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_PIXEL_CUT);
         if( s!=null ) pixelCut = s;
         
         s = prop.getProperty(Constante.KEY_HIPS_PIXEL_FUNCTION);
         if( s!=null ) {
            int i = Util.indexInArrayOf(s, TRANSFERTFCT, true);
            if( i>=0 ) transferFct4Fits=i;
         }

         // D�termination de l'identificateur du HiPs, m�thode post Markus, pr�-Markus, 
         // et m�me encore avant
         id = getHiPSID(prop);
//         s = prop.getProperty(Constante.KEY_OBS_ID);
//         if( s!=null ) {
//            String s1 = prop.getProperty(Constante.KEY_CREATOR_ID);
//            if( s1!=null ) s=s1+"/"+s;
//            else s=null;
//         }
//         if( s==null ) s = prop.getProperty(Constante.KEY_PUBLISHER_DID);
//         if( s==null ) s = prop.getProperty(Constante.OLD_PUBLISHER_DID);
//         id = s;
//         if( id.startsWith("ivo://") ) id = s.substring(6);
         
         s = prop.getProperty(Constante.KEY_HIPS_ORDER);
         if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_ORDER);
         if( s!=null ) {
            try { maxOrder=Integer.parseInt(s);
            } catch( Exception e ) {};
         }

         s = prop.getProperty(Constante.KEY_HIPS_ORDER_MIN);
         if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_ORDER_MIN);
         if( s!=null ) {
            try { minOrder=Integer.parseInt(s);
            } catch( Exception e ) {};
         }

         s = prop.getProperty(Constante.KEY_HIPS_TILE_WIDTH);
         if( s!=null ) {
            try {
               int w=Integer.parseInt(s);
               tileOrder=(int)CDSHealpix.log2(w);
            } catch( Exception e ) {};

            // Ancien vocabulaire
         } else {
            s = prop.getProperty(Constante.OLD_TILEORDER);
            if( s!=null ){
               try { tileOrder=Integer.parseInt(s); } catch( Exception e ) {};
            }
         }

         s = prop.getProperty(Constante.KEY_DATAPRODUCT_SUBTYPE);
         if( s!=null && s.indexOf("live")>=0 ) live=true;
         
      } catch( Exception e ) { aladin.trace(3,"No properties file found ...");  return false; }
      return true;

   }


   protected void setSpecificParams(TreeObjDir gluSky) {
      type = ALLSKYIMG;
      video = aladin.configuration.getCMVideo();
      inFits = gluSky.isFits();
      inJPEG = gluSky.isJPEG();
      inPNG = gluSky.isPNG();
      truePixels=gluSky.isTruePixels();
      color = gluSky.isColored();
      cube = gluSky.isCube();

      scanProperties();
      scanMetadata();

      gluSky.reset();
   }

   protected int getTileMode() {
      if( isTruePixels() ) return HealpixKey.FITS;
      //      if( color ) return HealpixKey.JPEG;
      if( inPNG ) return HealpixKey.PNG;
      return HealpixKey.JPEG;
   }

   public PlanBG(Aladin aladin, String path, String label, Coord c, double radius,String startingTaskId) {
      super(aladin);
      this.startingTaskId=startingTaskId;
      initCache();
      aladin.trace(2,"Creating allSky directory plane ["+path+"]");
      type = ALLSKYIMG;
      video= aladin.configuration.getCMVideo();
      File f = new File(path);
      url = f.getAbsolutePath();
      survey = f.getName();
      maxOrder=3;
      useCache = false;
      this.label=label;
      TreeObjDir gsky = null;
      try { gsky= new TreeObjDir(aladin, url); }
      catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }
      paramByTreeNode(gsky, c, radius);
      scanProperties();
      scanMetadata();
      aladin.trace(3,"AllSky local... frame="+Localisation.getFrameName(frameOrigin)+" "+this+(c!=null ? " around "+c:""));
      suite();
   }

   protected PlanBG(Aladin aladin, URL u, String label, Coord c, double radius, String startingTaskId) {
      super(aladin);
      this.startingTaskId=startingTaskId;
      initCache();
      aladin.trace(2,"Creating allSky http plane ["+u+"]");
      type = ALLSKYIMG;
      video=aladin.configuration.getCMVideo();
      url = u.toString();
      
      // Cas particulier d'une URL HiPS avec suffixe
      // => http://host.fr/truc/_HIPS_?param=xx&...
      url=checkDynHiPS(url);

      maxOrder = 3;
      useCache = true;
      local = false;
      co=c;
      coRadius=radius;
      TreeObjDir gsky = null;
      try { gsky= new TreeObjDir(aladin, url); }
      catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }
      paramByTreeNode(gsky,c,radius);
      int n = url.length();
      if( url.endsWith("/") ) n--;
      survey = this.label!=null && this.label.length()>0 ? this.label : url.substring(url.lastIndexOf('/',n-1)+1,n);
      scanProperties();
      scanMetadata();
      aladin.trace(3,"HiPS http... "+this+(c!=null ? " around "+c:""));   
         
//      setParamForDynHiPS();
      suite();
   }
   
   /** True s'il s'agit d'un HiPS dynamique (param�tres en suffixe des URLs d'appels) */
   protected boolean isDynHiPS() { return urlSuffix!=null; }
   
   /** Rep�re s'il s'agit d'un HiPS dynamique. Pour le moment se base sur la pr�sence
    * d'un "joker" dans l'URL d'appel. Si celle-ci contient la chaine "_HIPS_, il va en d�duire
    * l'URL racine du HiPS et la liste des param�tres dynamiques. Si c'est le cas, met � jour
    * l'url et la variable urlsuffix.
    * @param url ex: http://alasky/cattiles/_HIPS_?param=value..
    * @return l'URL, �ventuellement modifi�e
    */
   protected String checkDynHiPS(String url) {
      int i=url.indexOf("_HIPS_");
      if( i<0 ) return url;
      urlSuffix = url.substring(i+7);
      url = url.substring(0,i);
      if( url.endsWith("/" ) ) url=url.substring(0,url.length()-1);
      id = TreeObjDir.DIRECT+System.currentTimeMillis()/1000;
      aladin.trace(1,"Dynamical HiPS detected ! id="+id+" param=["+urlSuffix+"]");
      return url;
   }

//   /** (Re)positionne certains param�tres en cas de HiPS dynamique */
//   private void setParamForDynHiPS() {
//      if( !isDynHiPS() ) return;
//      
//      // POUR LE MOMENT, EN DUR CAR LE SERVEUR DE THOMAS N'A PAS DE PROPERTIES
//      truePixels=false;
//      inFits=false;
//      inJPEG=false;
//      hasMoc=false;
//      hasHpxFinder=false;
//      color=true;
//      colorPNG=false;
//      colorUnknown=false;
//      frameOrigin = Localisation.ICRS;
//   }

   protected void paramByTreeNode(TreeObjDir gSky, Coord c, double radius) {
      if( label!=null && label.trim().length()>0 ) setLabel(label);
      else setLabel(gSky.label);
      maxOrder=gSky.getMaxOrder();
      inFits=gSky.isFits();
      inJPEG=gSky.isJPEG();
      inPNG=gSky.isPNG();
      truePixels=gSky.isTruePixels();
      color=gSky.isColored();
      cube=gSky.isCube();
      frameOrigin=gSky.getFrame();
      tileOrder=gSky.getLosangeOrder();
      local=gSky.isLocal();
      loadMocNow=gSky.loadMocNow();
      version = gSky.getVersion();
      //      truePixels=inFits && localAllSky || !inJPEG && !localAllSky;
      useCache=!local && gSky.useCache();
      co=c!=null ? c : gSky.getTarget();
      coRadius= c!=null ? radius : gSky.getRadius();
   }

   //   public String getDataMinInfo()  { return truePixels ? super.getDataMinInfo():"0"; }
   //   public String getDataMaxInfo()  { return truePixels ? super.getDataMaxInfo():"255"; }
   //   public String getPixelMinInfo() { return truePixels ? super.getPixelMinInfo():"0"; }
   //   public String getPixelMaxInfo() { return truePixels ? super.getPixelMaxInfo():"255"; }

   protected String getPixelInfoFromGrey(int greyLevel,int mode) {
      if( truePixels ) return super.getPixelInfoFromGrey(greyLevel,mode);
      return "";
   }

   private boolean testMoc=false; // true : la pr�sence d'un MOC a �t� test�

   /** Retourne true si le survey dispose d'un Moc associ�. Il doit �tre sur la racine
    * et avoir le nom Moc.fits */
   protected boolean hasMoc() {
      if( hasMoc || testMoc ) return hasMoc;
      String s = getProperty(Constante.KEY_MOC_ACCESS_URL);
      if( s!=null ) { hasMoc=testMoc=true; }
      else {
         String moc = url+"/"+Constante.FILE_MOC;
         hasMoc = local ? (new File(moc)).exists() : Util.isUrlResponding(moc);
         testMoc=true;
      }
      return hasMoc;
   }

   private boolean testHpxFinder=false; // true : la pr�sence d'un HpxFinder a �t� test�
   
   /** Ajoute des infos sur le plan */
   protected void addMessageInfo( StringBuilder buf, MyProperties prop ) {
      String s;
      if( prop!=null && (s=prop.get("moc_sky_fraction"))!=null ) ADD( buf,"\n* Coverage: ",getCoverageSpace(s));
      ADD( buf,"\n","* HiPS order: "+getOrder()+"/"+maxOrder);
   }

   // Juste pour se simplifier la vie sur le test de prop==null
   protected String getProperty(String key) { return prop==null ? null : prop.getProperty(key); }
   
   protected boolean hasHpxFinder() {
      if( hasHpxFinder || testHpxFinder ) return hasHpxFinder;
      String s = getProperty(Constante.KEY_HIPS_PROGENITOR_URL);
      if( s!=null ) { hasHpxFinder=testHpxFinder=true; }
      else {
         String f = url+"/"+Constante.FILE_HPXFINDER+"/"+Constante.FILE_METADATAXML;
         hasHpxFinder = local ? (new File(f)).exists() : Util.isUrlResponding(f);
         testHpxFinder=true;
      }
      return hasHpxFinder;
   }

   //   protected void frameProgenResume(Graphics g,ViewSimple v) {
   //      if( planBGIndex==null || aladin.frameProgen==null ) return;
   //      planBGIndex.updateHealpixIndex(v);
   //      HealpixIndex hi = ((PlanBGIndex)planBGIndex).getHealpixIndex();
   //      aladin.frameProgen.resume(hi,this);
   //      aladin.frameProgen.progen.draw(g,v);
   //   }

   /** Chargement du Moc associ� au survey */
   protected void loadMoc() {
      if( moc==null ) {
         try { loadInternalMoc(); } catch( Exception e ) {
            if( Aladin.levelTrace>=3 ) e.printStackTrace();
            return;
         }
      }
      aladin.calque.newPlanMOC(moc,label+" MOC", getUrl()+"/"+Constante.FILE_MOC);
   }

   protected SMoc moc;

   /** Chargement du MOC associ� au HiPS, soit depuis le cache, soit depuis le net */
   protected void loadInternalMoc() throws Exception {
      String fcache = getCacheDir()+"/"+getCacheName()+"/"+Constante.FILE_MOC;
      if( !local && useCache ) {
         if( new File(fcache).exists() ) {
            InputStream in = null;
            try {
               in = new BufferedInputStream( new FileInputStream(fcache) );
               moc = new SMoc(in);
               moc.setMinOrder(3);
               removeHealpixOutsideMoc();
               Aladin.trace(3,"Loading "+id+" MOC from cache");
            } catch( Exception e ) { e.printStackTrace(); }
            finally { if( in!=null ) in.close(); }
            return;
         }
      }
//      String f = getProperty("moc_access_url");
//      if( f==null ) f = getUrl()+"/"+Constante.FILE_MOC;
      String f = getUrl()+"/"+Constante.FILE_MOC;
      MyInputStream mis = null;
      try {
         mis = Util.openAnyStream(f);
         moc = new SMoc(mis);
         moc.setMinOrder(3);
         removeHealpixOutsideMoc();
         Aladin.trace(3,"Loading "+id+" MOC from net");
      } catch( Exception e ) { e.printStackTrace(); }
      finally { if( mis!=null) mis.close(); }

      if( !local && useCache ) {
         moc.write(fcache);
         Aladin.trace(3,"Saving "+survey+" MOC in cache");
      }
   }

   // Suppression des losanges hors du surveys qu'on a essay� de charger par erreur
   // en attendant que le MOC soit disponible
   private void removeHealpixOutsideMoc() {
      if( moc==null ) return;
      Enumeration<String> e = pixList.keys();
      while( e.hasMoreElements() ) {
         String k = e.nextElement();
         HealpixKey h = pixList.get(k);
         if( h==null ) continue;
         if( h.npix==-1 ) continue;
         if( moc.isIntersecting(h.order,h.npix) ) continue;
         h.abort();
         pixList.remove(k);
         //         System.out.println("*** removeHealpixErrorOutsideMoc "+k);
      }

      // On en profite pour m�moriser la position de la premi�re cellule
      if( (co==null || flagNoTarget) && !moc.isEmpty() && frameOrigin==Localisation.ICRS ) {
         try {
            MocCell cell = moc.iterator().next();
            double res[] = CDSHealpix.pix2ang_nest( cell.order, cell.start);
            double[] radec = CDSHealpix.polarToRadec(new double[] { res[0], res[1] });
            co = new Coord(radec[0],radec[1]);
         } catch( Exception e1 ) {
            if( aladin.levelTrace>=3 ) e1.printStackTrace();
         }
      }

   }

   /** Chargement d'un plan Progen associ� au survey */
   protected void loadProgen() {
      String progen = getProperty(Constante.KEY_HIPS_PROGENITOR_URL);
      if( progen==null ) progen = url+"/"+Constante.FILE_HPXFINDER;
      aladin.execAsyncCommand("'Details "+label+"'=load "+progen);
   }

   /** Retourne le frame d'affichage, 0 si utilisation du frame g�n�ral */
   protected int getFrameDrawing() { return frameDrawing; }

   /** Positionne le frame d'affichage. 0 pour prendre le frame g�n�ral */
   protected void setFrameDrawing(int frame) {
      frameDrawing=frame;
      //      System.out.println("PlanBG.setFrameDrawing("+Localisation.FRAME[frame]+")..");
      if( projd.frame!=getCurrentFrameDrawing() ) {
         //         System.out.println("PlanBG.setFrameDrawing: => new proj = "+Localisation.REPERE[getFrame()]);

         //         ViewSimple v = aladin.view.getView(this);
         //         Coord c = new Coord(aladin.view.repere.raj,aladin.view.repere.dej);
         //         projd.frame = getCurrentFrameDrawing();
         //         aladin.view.newView(1);
         //         v.goToAllSky(c);
         //         aladin.view.repaintAll();

         projd.frame = getCurrentFrameDrawing();
         syncProjLocal();
         aladin.view.repaintAll();
      }
   }

   /** Adaptation des projLocal dans chaque vue */
   protected void syncProjLocal() {
      int m=aladin.view.getNbView();
      for( int j=0; j<m; j++ ) {
         ViewSimple v = aladin.view.viewSimple[j];
         if( v.pref!=this ) continue;
         Coord c = v.projLocal.getProjCenter();
         v.projLocal.frame = projd.frame;
         //         v.projLocal.setProjCenter(c.al,c.del);
         if( projd.frame!=Localisation.ICRS ) c = Localisation.frameToFrame(c, Localisation.ICRS, projd.frame);
         v.projLocal.modify(projd.label,projd.modeCalib,c.al,c.del,projd.rm,projd.rm,projd.cx,projd.cy,projd.r,projd.r,
               projd.rot,projd.sym,projd.t,projd.system);
         v.newView(1);
      }

      // Idem pour les vues non visibles
      for( int j=aladin.view.viewMemo.size()-1; j>=0; j--) {
         ViewMemoItem memo = aladin.view.viewMemo.memo[j];
         if( memo==null ) continue;
         if( memo.pref!=this ) continue;
         Coord c = memo.projLocal.getProjCenter();
         memo.projLocal.frame = projd.frame;
         //         memo.projLocal.setProjCenter(c.al,c.del);
         if( projd.frame!=Localisation.ICRS ) c = Localisation.frameToFrame(c, Localisation.ICRS, projd.frame);
         memo.projLocal.modify(projd.label,projd.modeCalib,c.al,c.del,projd.rm,projd.rm,projd.cx,projd.cy,projd.r,projd.r,
               projd.rot,projd.sym,projd.t,projd.system);
      }

   }

   /** Retourne le frame courant en fonction du s�lecteur du frame d'affichage */
   protected int getCurrentFrameDrawing() {
      if( frameDrawing==0 ) return aladin.localisation.getFrame();
      return Util.indexInArrayOf(Localisation.FRAME[frameDrawing], Localisation.REPERE);
   }

   /** retourne le systeme de coordonn�e natif de la boule Healpix */
   public int getFrameOrigin() { return frameOrigin; }


   static protected boolean isPlanHpxFinder(String path) {
      File f = new File(path);
      return f.getName().equals(Constante.FILE_HPXFINDER) && f.isDirectory();
   }

//   static protected boolean isPlanBG(String path) {
//      if( isPlanHpxFinder(path) ) return true;
//      String s = path+Util.FS+"Norder3";
//      File f = new File(s);
//      return f.isDirectory();
//   }

   protected void suite() {

      if( this.label==null || this.label.trim().length()==0) setLabel( id!=null ? id : survey);
      int defaultProjType = aladin.projSelector.getProjType();
//      int defaultProjType = Aladin.BETA ? aladin.projSelector.getProjType() 
//               : aladin.configuration.getProjAllsky();
      
      if( co==null  ) {
         flagNoTarget=true;
         co = new Coord(0,0);
         co=Localisation.frameToFrame(co,aladin.localisation.getFrame(),Localisation.ICRS );
         coRadius=220;
      }
      if( coRadius<=0 ) coRadius=220;

      objet = co+"";

      // On va garder le m�me type de projection que le plan de base.
//      Plan base = aladin.calque.getPlanBase();
//      if( base instanceof PlanBG ) defaultProjType = base.projd.t;

      // Choix sp�cifique d'une projection et d'un sens pour la longitude dans le cas
      // d'une plan�te
      boolean isPanorama = isPanorama();
      boolean isAPlanet = isPlanet();
      boolean longAsc = isAPlanet;
      int projection = isPanorama ? Calib.MER : isAPlanet ? Calib.SIN : defaultProjType ;
      specificProj = isAPlanet;
      
      Projection p = new Projection("allsky",Projection.WCS,co.al,co.del,60*4,60*4,250,250,500,500,0,longAsc,
            projection,Calib.FK5,this);
      p.frame = getCurrentFrameDrawing();
//      if( Aladin.OUTREACH ) p.frame = Localisation.GAL;
      setNewProjD(p);
      
      typeCM = aladin.configuration.getCMMap();
      transferFct4Preview = aladin.configuration.getCMFct();
      transfertFct = truePixels ? transferFct4Fits : transferFct4Preview;
      video = aladin.configuration.getCMVideo();
      
      if( Aladin.levelTrace==6 ) {
         System.err.println("WARNING: Cache off for perf tests on "+id);
         useCache=false;   // Pour des tests de perfs
      }

      setDefaultZoom(co,coRadius);
      suiteSpecific();
      launchLoading();
   }

   protected void launchLoading() {
      threading();
      log();
   }
   
   /** retourne true si le plan dispose d'une projection sp�cifique et n'est plus
    * asservi au s�lecteur global (widget ProjSelector) */
   protected boolean hasSpecificProj() { return specificProj; }
   
   /** Positionne le flag d'une projection sp�cifique associ�e � ce plan */
   protected void setSpecificProj(boolean flag) { specificProj = flag; }
   
   
   static private final String [] SKYFRAME = { "g","gal","galactic","e","ecl","ecliptic","c","equ","equatorial" };
   
   /** Retourne le frame indiqu� dans les fichiers de properties, ou � d�faut le frame interne d'affichage */
   protected String getHipsFrame() {
      String frame;
      if( prop!=null && (frame = prop.getProperty("hips_frame"))!=null ) {
        return frame;
      }
      return Localisation.getFrameName(frameOrigin);
   }
   
   /** Retourne true si le syst�me de coordonn�e est li�e � une vue panorama */
   protected boolean isPanorama() {
      String s;
      if( prop==null ) return false;
      s = prop.getProperty("hips_frame");
      if( !isPlanet(s) ) return false;
      if( id.toLowerCase().indexOf("/pan")<0 && s.toLowerCase().indexOf("pan")<0 ) return false;
      return true;
   }
      
   /** Retourne true si le syst�me de coordonn�e est li�e � une plan�te */
   protected boolean isPlanet() {
      if( prop==null ) return false;
      String s = prop.getProperty("hips_frame");
      return isPlanet( s );
   }
      
   protected boolean isPlanet(String frame) {
      return frame!=null && Util.indexInArrayOf(frame, SKYFRAME, true)<0;
   }

   /** Retourne le syst�me de r�f�rence dela Plan�te, ou null sinon */
   protected String getPlanetSys() {
      if( !isPlanet() ) return null;
      return prop.getProperty("hips_frame");
   }

   protected void suiteSpecific() {
      dataMin=pixelMin=0;
      dataMax=pixelMax=255;
      active=selected=true;
      isOldPlan=false;

      pixList = new Hashtable<>(1000);
      if( error==null ) loader = new HealpixLoader();

      RGBControl = new int[RGBCONTROL.length];
      for( int i=0; i<RGBCONTROL.length; i++) RGBControl[i] = RGBCONTROL[i];

      aladin.endMsg();
      creatDefaultCM();
      resetStats();
   }

   /** Positionne la taille initiale du champ. */
   protected void setDefaultZoom(Coord c,double radius) {
      setDefaultZoom(c,radius,aladin.view.getCurrentView().getWidth());
   }
   protected void setDefaultZoom(Coord c,double radius,int width) {
      initZoom=-1;
      if( radius>0 && c!=null ) {
         double projPixelRes = (projd.rm/60) / projd.r;
         double taille = width * projPixelRes;
         double z = taille/radius;
         initZoom = aladin.calque.zoom.getNearestZoomFct(z);
      }
      if( initZoom==-1 ) initZoom = c==null ? 1./(/*Aladin.OUTREACH?64:*/32) : 16;
      aladin.trace(4,"PlanBG.setDefaultZoom("+c+","+Coord.getUnit(radius)+") => zoom = "+initZoom);
   }

   protected void log() {
      aladin.log(Plan.Tp[type],label);
   }

   protected void initCache() {
      if( cacheSize==-1 ) {
         cacheSize=aladin.configuration.getHpxCacheSize();
         Aladin.trace(3,"Cache Size => "+cacheSize);
      }

      if( Aladin.MAXMEM<50 ) {
         aladin.error("Full sky Aladin mode requires atleast 64MB of RAM\n" +
               "We strongly suggest to adjust the JAVA memory parameter and relaunch Aladin.\n" +
               "See the corresponding Aladin FAQ entry available via the Help menu");
      }
   }

   static final private int MAXCHECKSITE = 3;
   private int nbCheckSite=0;

   /** Tentative de changement de site Web dans le cas de mirroirs
    * On ne le fait qu'un certain nombre de fois par survey
    * @return true si �a a march�
    */
   public boolean checkSite(boolean withTrace) {
      if( nbCheckSite>=MAXCHECKSITE ) return false;
      if( gluTag==null || gluTag.startsWith("__") || gluTag.startsWith(TreeObjDir.DIRECT)) return false;
      aladin.glu.checkIndirection(gluTag, "/properties" ); //"");
      URL u = aladin.glu.getURL(gluTag, "", false,true,1);
      if( u==null ) return false;
      String url1 = ""+u;
      if( url1.equals(url) ) return false;
      nbCheckSite++;
      if( withTrace ) {
         aladin.command.console("!!! Dynamic Web server site switching => "+url1);
         Aladin.trace(2,"Plan "+label+" Dynamic Web server site switching: from "+url+" to "+url1);
      }
      url=url1;
      resetStats();   // puisqu'on change de serveur, on resete les stats
      return true;
   }
   
   /** Retourne la liste des URLs pour tous les sites (en commen�ant par la courante) */
   public ArrayList<String> getMirrorsUrl() {
      if( gluTag==null || gluTag.startsWith("__") || gluTag.startsWith(TreeObjDir.DIRECT)) return null;
      ArrayList<String> a = aladin.glu.getAllUrls(gluTag);
      if( a.size()<2 ) return null;
      return a;
   }
   
   @Override
   public String getUrl() { return url; }

   @Override
   protected void planReady(boolean ready) {
      super.planReady(ready);
      setPourcent(0);
      flagOk=ready;
      aladin.synchroPlan.stop(startingTaskId);
      
      if( co!=null ) aladin.view.setRepere(co);
      
      // Chargement du MOC associ�, avec ou sans cr�ation d'un plan d�di�
      planReadyMoc();
   }
   
   /** Chargement du plan MOC si n�cessaire, avec ou sans cr�ation d'un plan d�di� */
   protected void planReadyMoc() {
      
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            try {
               if( loadMocNow ) loadMoc();
               else if( hasMoc() ) loadInternalMoc();
               
               // D�placement a posteriori car compl�tement hors champ du HiPS
               if( hasMoc() && co!=null ) {
                  Healpix hpx = new Healpix();
                  if( !moc.contains(hpx, co.al, co.del) ) {
                     co.al = Double.parseDouble( prop.getFirst("hips_initial_ra") );
                     co.del = Double.parseDouble( prop.getFirst("hips_initial_dec") );
                     aladin.view.setRepere(co);
                     aladin.trace(4,"PlanBG.planReadyMoc(): default target out of HiPS moc => moving on "+co);
                  }
               }
               
            } catch( Exception e ) {
               e.printStackTrace();
            }
         }
      });
   }

   @Override
   protected boolean waitForPlan() {
      return error==null;
   }

   /** Modifie la table des couleurs */
   @Override
   protected void setCM(Object cm) {
      this.cm = (ColorModel)cm;
      changeImgID();
   }
   
   /** Lib�ration du plan Background */
   @Override
   protected boolean Free() {
      String stat = getShortStats();
      if( stat!=null ) {
         aladin.log("HealpixStats",stat);
         Aladin.trace(4,"PlanBG.Free() stat => "+stat);
         nbLoadNet=nbLoadCache=0;   // Pour �viter un possible double envoi des stats
      }
      
      hpx2xy = xy2hpx = null;
      frameOrigin=Localisation.ICRS;
      FreePixList();
      prop=null;
      return super.Free();
   }
   
   protected void setFmt() { }
   
   private FrameHipsProperties frameHipsProperties = null;
   
   /** Visualisation des propri�t�s */
   protected void seeHipsProp() {
      try {
         if( frameHipsProperties==null ) frameHipsProperties = new FrameHipsProperties(this);
         frameHipsProperties.seeHeaderFits();
      } catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }
   }

   /** Lib�ration de la pixList du plan */
   protected void FreePixList() {
      try {
         if( pixList!=null ) {
            Enumeration<HealpixKey> e = pixList.elements();
            while( e.hasMoreElements() ) {
               HealpixKey healpix = e.nextElement();
               if( healpix!=null ) {
                  if( useCache && healpix.shouldBeCached() ) healpix.write();
                  healpix.free();
               }
            }
            pixList.clear();
         }
      } catch( Exception e ) { }
      //      if( allsky!=null ) { allsky.free(); allsky=null; }
   }

   // DEJA PRIS EN COMPTE PAR "proj"
//   @Override
//   protected void setPropertie(String prop,String specif,String value) throws Exception {
//      if( prop.equalsIgnoreCase("Projection") ) {
//         Projection p = this.projd;
//         this.modifyProj(null,Projection.SIMPLE,p.alphai,p.deltai,p.rm1,
//               p.cx,p.cy,p.r1,p.rot,p.sym,Projection.getProjType(value),p.system);
//         aladin.view.newView(1);
//         aladin.calque.repaintAll();
//      } else {
//         super.setPropertie(prop,specif,value);
//      }
//   }
   
   protected int getPolaScaleFactor() {
      return polaScaleFactor;
   }
   protected void setPolaScaleFactor(int polaScaleFactor) {
      this.polaScaleFactor = polaScaleFactor;
   }

   /** Lib�ration du maximum de m�moire possible */
   protected void clearBuf() {
      Enumeration<HealpixKey> e = pixList.elements();
      while( e.hasMoreElements() ) {
         HealpixKey healpix = e.nextElement();
         if( healpix!=null ) healpix.clearBuf();
      }
      //      if( allsky!=null ) allsky.clearBuf();
      gc();
   }

   // Positionn� � true afin d'�viter de refaire des gc() inutiles
   private boolean flagClearBuf=false;

   /** Suppression des images en m�moire cons�cutif � l'arr�t de visualisation du plan
    * Attention : le nom de la m�thode est ambigue
    */
   @Override
   protected boolean pixelsOriginIntoCache() {
      if( flagClearBuf ) return true;
      clearBuf();
      changeImgID();
      flagClearBuf=true;
      return true;
   }

   /** Retourne true si on dispose (ou peut disposer) des pixels originaux */
   @Override
   protected boolean hasOriginalPixels() { return isTruePixels(); }
   
   protected double getInvPixel(double pix8) {
      if( width==0 && local && truePixels ) loadOneKey();
      return super.getInvPixel(pix8);
   }

   // On va forcer le chargement d'une tuile pour r�cup�rer les param�tres
   private void loadOneKey() {
      try {
         
         int order = cds.tools.pixtools.Util.getMaxOrderByPath(url);
         
         // On r�cup�re la premi�re tuile que l'on trouve � l'ordre Max
         File f = new File( HealpixKey.getFilePath(url, order, 0, 0));
         f=f.getParentFile().getParentFile();
         File [] list = f.listFiles();
         list = list[0].listFiles();
         
         // On en extrait le num�ro Healpix
         String name = list[0].getName();
         int i = name.lastIndexOf('.');
         int npix = Integer.parseInt(name.substring(4,i));
         
         // Et on charge la tuile correspondante
         new HealpixKey(this, order, npix, HealpixKey.SYNC);
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }
   
   /** Retournement de l'image
    * @param methode 0-N/S, 1-D/G, 2-N/S+D/G
    */
   protected void flip(int methode) throws Exception {
      setSpecificProj(true);
      
      boolean sym  = methode==1 || methode==2 ? !projd.sym : projd.sym;
      double angle = methode==0 || methode==2 ? projd.rot+180 : projd.rot;
      
      Projection p = projd;
      modifyProj(null,Projection.SIMPLE,p.alphai,p.deltai,p.rm1,p.cx,p.cy,p.r1,
            angle,sym,p.t,p.system);
      aladin.view.newView(1);
      aladin.view.repaintAll();
   }
   
   // M�morise un plan qui doit �tre averti (recut()) en cas de modif de la table des couleurs
   // et ou du cut
   private PlanBGRgb planBgRgb = null;
   protected void addRecutListener( PlanBGRgb p )  { planBgRgb=p; }
   protected boolean hasRecutListener() { return planBgRgb!=null; }
   protected PlanBGRgb getRecutListener() { return planBgRgb; }

   @Override
   protected boolean recut(double min,double max,boolean autocut) {
      FreePixList();
      changeImgID();
      double tmpRangeMin=dataMin;
      double tmpRangeMax=dataMax;
      findMinMax( pixelsOrigin ,bitpix,width,height,min,max,autocut,0,0,0,0);

      if( tmpRangeMin!=tmpRangeMax ) {
         dataMin=tmpRangeMin;
         dataMax=tmpRangeMax;
      }

      // Juste pour mettre � jour l'histogramme des pixels
      if( pixelsOrigin!=null ) {
         int size = pixelsOrigin.length/(Math.abs(bitpix)/8);
         if( pixels==null || pixels.length!=size ) pixels = new byte[size];
         to8bits(pixels, 0, pixelsOrigin, size, bitpix, pixelMin, pixelMax, false);
         resetHist();
      }
      
      // Y a-t-il des plans RGB d�pendants
      if( planBgRgb!=null ) planBgRgb.recut();

      return true;
   }
   
   
   protected boolean flagRecut=true;
   protected double flagRecutRadius=0;
   protected Coord flagRecutCoo=null;

   /** Postionne la taille approximative de tous les losanges en m�moire */
   protected void setMem() { return; }

   @Override
   protected boolean setActivated(boolean flag) {
      if( flag && aladin.calque.hasHpxGrid() ) System.out.println(getStats());
      return super.setActivated(flag);
   }


   private String cacheName=null;   // Nom du cache (pour �viter de le reconstruire � chaque fois)

   /** Retourne le nom du r�pertoire cache pour ce HiPS */
   protected String getCacheName() {
      if( cacheName!=null ) return cacheName;

      // On prend d�sormais l'identificateur (sans le pr�fixe ivo://) qu'il faut aller
      // p�cher dans le fichier properties. Sauf si on a un id qui commence par TreeObjDir.DIRECT
      // on remplae les / et \ par des _
      try {
         String s;
         
         // Dans le cas d'un ID qui commence par DIRECT/, on ne va surtout pas r�utiliser
         // le m�me cache
         if( id!=null && id.startsWith(TreeObjDir.DIRECT) ) {
            s=id;
            if( s.startsWith("ivo://") ) s = s.substring(6);
         }
         
         else {
            MyProperties prop = new MyProperties();
            String urlFile = url+"/"+Constante.FILE_PROPERTIES;
            
            if( isDynHiPS() ) urlFile += "?"+urlSuffix;

            InputStreamReader in = null;
            try {
               in= new InputStreamReader( Util.openAnyStream(urlFile), "UTF-8" );
               prop.load(in);
            } finally { if( in!=null ) try { in.close(); } catch( Exception e ) {} }

            s = getHiPSID(prop);
         }
         
         s = s.replace(":","_");
         s = s.replace("/","_");
         s = s.replace("\\","_");
         s = s.replace("?","_");
         s = s.replace("#","_");
         s = s.replace("&","_");
         cacheName = s;
      } catch( Exception e ) {
         cacheName = survey + version;
      }

      Aladin.trace(4,"getCacheName(): Cache name = ["+cacheName+"]");
      return cacheName;
   }

   // Pour �viter les tests � chaque fois
   private String dirCache=null;
   private boolean flagCache=false;

   /** Retourne et cr�e si n�cessaire le r�pertoire qui contient le cache ou null si impossible � cr�er */
   protected String getCacheDir() {
      if( flagCache ) return dirCache;
      flagCache=true;

      // Cr�ation de $HOME/.aladin/Cache si ce n'est d�j� fait
      if( !aladin.createCache() ) return null;

      // Cr�ation de $HOME/.aladin/Cache si ce n'est d�j� fait
      String dir = System.getProperty("user.home") + Util.FS + Aladin.CACHE + Util.FS + Cache.CACHE;
      File f = new File(dir);
      if( !f.isDirectory() && !f.mkdir() ) return null;

      // Cr�ation de $HOME/.aladin/Cache/Background si ce n'est d�j� fait
      dir = dir+Util.FS+CACHE;
      f = new File(dir);
      if( !f.isDirectory() && !f.mkdir() ) return null;

      dirCache=dir;
      return dir;
   }

   /** Retourne le nom du r�pertoire qui contient le cache via une m�thode static sans se soucier de son existence */
   static protected String getCacheDirStatic() {
      return System.getProperty("user.home") + Util.FS + Aladin.CACHE + Util.FS + Cache.CACHE + Util.FS + CACHE;
   }

   /** Positionne le niveau max du cache en Ko (minimum 500 Mo) */
   static protected void setMaxCacheSize(long maxCacheSize) {
      if( maxCacheSize<512*1024 ) maxCacheSize=512*1024;

      MAXCACHE = maxCacheSize;
      Aladin.trace(4,"PlanBG.setMaxCacheSize() => "+MAXCACHE/1024+"MB");
   }

   protected int nbReady=0;

   /** Taille de la m�moire cache pour le fond du ciel (en Ko) */
   static protected long getCacheSize() {
      synchronized( cacheLock ) { return cacheSize; }
   }

   /** Augmentation de la taille du cache - size en Ko*/
   static void addInCache(long size) {
      synchronized( cacheLock ) { cacheSize+=size; }
   }

   /** Positionnement de la taille du cache - size en Ko*/
   static void setCacheSize(long size) {
      synchronized( cacheLock ) { cacheSize=size; }
   }

   static private Thread scanCache = null;

   /** Scan du cache et suppression des vieux fichiers */
   static synchronized void scanCache() {
      if( (cacheSize!=-1 && cacheSize<MAXCACHE) ) return;
      cleanCache();
   }
   
   static public synchronized void cleanCache() {
      if( scanCache!=null) {
//         Aladin.trace(4,"Scan cache already running !");
         return;
      }
      
      String dir = PlanBG.getCacheDirStatic();
      if( dir==null ) {
         setCacheSize(0);
         return;
      }
      
      // V�rifie qu'une autre instance n'est pas d�j� en train de faire le m�me boulot
      // en regardant la pr�sence ou non du fichier ScanRunning.bin
      File ft = new File(Cache.getCacheDir()+Util.FS+"ScanRunning.bin");
      if( ft.exists() ) {
         long t = System.currentTimeMillis();
         
         // Peut �tre un vieux scan qui s'est mal fini
         if(  t - ft.lastModified() <3600 ) ft.setLastModified(t);
         
         // Sinon, c'est que quelqu'un fait d�j� le boulot
         else {
            Aladin.trace(4,"Detect concurrent scanning HiPS cache => abort");
            return;
         }
         
      }
      // Sinon je le cr�e
      try { (new RandomAccessFile(ft,"rw")).close(); } catch( Exception e) {}
      

      (scanCache=new Thread("Scan cache") {
         @Override
         public void run() {
            currentThread().setPriority(MIN_PRIORITY);
            long size=0;
            long t = System.currentTimeMillis();
            String dir = PlanBG.getCacheDirStatic();

            Aladin.trace(3,"Scanning HiPS cache...");

            // Premier parcours pour virer les surveys obsoletes
            File fold[] = new File(dir).listFiles();
            for( int i=0; i<fold.length; i++ ) {
               if( fold[i].isDirectory() && fold[i].getName().endsWith(".old") ) {
                  Aladin.trace(4,"PlanBG.scanCache(): removing folder "+fold[i].getName()+"...");
                  Util.deleteDir(fold[i]);
               }
            }

            // Parcours du cache
            Vector<FileItem> listCache = new Vector<>(2000);
            size  = getCacheSize(new File(dir),listCache);
            size += getCacheSizePlanHealpix(new File(PlanHealpix.getCacheDirPath()), listCache);
            try {
               Collections.sort(listCache,(new Comparator() {
                  public int compare(Object o1, Object o2) {
                     if( o1==o2 ) return 0;
                     if( o1==null ) return -1;
                     if( o2==null ) return 1;
                     long t1 = ((FileItem)o1).date;
                     long t2 = ((FileItem)o2).date;
                     return t1==t2 ? 0: t1>t2 ? 1 : -1;
                  }
               }));


               // Suppression des vieux fichiers si n�cessaires
               Enumeration<FileItem> e = listCache.elements();
               while( e.hasMoreElements() && size > (3*MAXCACHE)/4 ) {
                  FileItem fi = e.nextElement();
                  File f = fi.f;
                  if( !fi.hasBeenModified() && size > (3*MAXCACHE)/4 ) {

                     Aladin.trace(4,"PlanBG.scanCache(): removing "+ f+" ("+fi.date+")");
                     if( f.isFile() ) {
                        size-=f.length()/1024;
                        if( fi.hasBeenModified() ) throw new Exception("File :"+f.getAbsolutePath());
                        f.delete();
                     }
                     // cache HPX des fichiers locaux
                     else if( f.isDirectory()) {
                        long dirSize = Util.dirSize(f)/1024;
                        size-=dirSize;
                        // TODO : v�rifier qu'on n'efface pas des donn�es d'un PlanHealpix dans la pile
                        if( fi.hasBeenModified() ) throw new Exception("Dir :"+f.getAbsolutePath());
                        Util.deleteDir(f);
                        //                      System.out.println("je vire le repertoire "+f.getAbsolutePath()+" de taille "+dirSize);
                     }
                  }
               }
               Aladin.trace(3," => Cache size="+Util.getUnitDisk(size*1024)+" maxCache="+Util.getUnitDisk(MAXCACHE*1024)+" scan in "+(System.currentTimeMillis()-t)+"ms");
               setCacheSize(size);
               
            } catch( Exception e1 ) {
               Aladin.trace(3,"Simultaneous access on cache => Clean aborted for avoiding conflict"
                        +(e1.getMessage()!=null?" => "+e1.getMessage():""));
            }
            finally {

               // C'est termin�, j'enl�ve le fichier de lock
               File ft = new File(Cache.getCacheDir()+Util.FS+"ScanRunning.bin");
               ft.delete();

               scanCache=null;
            }
         }
      }).start();
   }
   
   static class FileItem {
      File f;
      long date;
      
      FileItem(File f) { this.f=f; date = f.lastModified(); }
      boolean hasBeenModified() { return date!=f.lastModified(); }
   }

   /** Nettoyage complet du cache Healpix */
   static void clearCache() {
      String dir = PlanBG.getCacheDirStatic();
      if( dir!=null ) Util.deleteDir(new File(dir));
      dir = PlanHealpix.getCacheDirPath();
      if( dir!=null ) Util.deleteDir(new File(dir));
      setCacheSize(0);
   }

   static private long  NBFILE=0;

   /** M�thode r�cursive pour le scan du cache */
   static public long getCacheSize(File dir,Vector<FileItem> listCache) {
      long size=0;
      File f[] = dir.listFiles();
      for( int i=0; f!=null && i<f.length; i++ ) {
         NBFILE++;
         if( NBFILE%100==0 ) Util.pause(50);    // On souffle un peu
         if( f[i].isDirectory() ) {
            long n = getCacheSize(f[i],listCache);
            if( n==0 ) f[i].delete();       // r�pertoire vide
            else size +=n;
            
         } else {
            size+=f[i].length()/1024;
            if( listCache!=null ) listCache.addElement( new FileItem(f[i]));
         }
      }
      return size;
   }

   /**
    * ne retourne que les repertoires qui nous interessent
    * @param dir
    * @param listCache
    * @return
    */
   static private long getCacheSizePlanHealpix(File dir, Vector<FileItem> listCache) {
      File f[] = dir.listFiles();
      long size = 0;
      for (int i=0; f!=null && i<f.length; i++) {
         if ( ! f[i].isDirectory()) {
            continue;
         }
         size  += Util.dirSize(f[i]);
         listCache.addElement(new FileItem(f[i]));
         Util.pause(100);
      }

      return size/1024;
   }

   /** Construction de la cl� de Hashtable pour ce losange */
   protected String key(HealpixKey h) { return key(h.order,h.npix,h.z); }

   /** Construction d'une cl� pour les Hasptable */
   protected String key(int order, long npix) { return order+"/"+npix; }

   protected String key(int order, long npix,int z) { return order+"/"+npix+(z<=0?"":"_"+z); }

   /** Demande de chargement du losange rep�r� par order,npix */
   public HealpixKey askForHealpix(int order,long npix) {
      HealpixKey pixAsk;

      readyAfterDraw=false;

      if( drawMode==DRAWPOLARISATION ) pixAsk = new HealpixKeyPol(this,order,npix);
      else pixAsk = new HealpixKey(this,order,npix);
      pixList.put( key(order,npix), pixAsk);
      return pixAsk;
   }

   // D�compte du nombre de losanges ayant lib�r�s de la m�moire afin de faire
   // un gc() de temps en temps
   protected int nbFlush=0;
   private long lastGc=0L;
   private long lastMemP=0L,lastMemI=0L;
   static private int MEMREQUIREDFORGC=40*1024*1024;
   private boolean memCpt=true;

   protected void gc() {
      long t= System.currentTimeMillis();
      if(t-lastGc<1000 ) return;
      lastGc=t;

      // Si on a de la m�moire en rab, on s'assure qu'on va lib�rer au-moins
      // 40Mo pour lancer un gc(). Comme le gc prend du temps on ne peut pas mesurer
      // imm�diatement son r�sultat, on le fera au coup suivant en alternant lastMemP et lastMemI
      if( aladin.getMem()<256 ) {
         if( memCpt ) lastMemP=Runtime.getRuntime().freeMemory();
         else lastMemI=Runtime.getRuntime().freeMemory();
         if( Math.abs(lastMemP-lastMemI)<MEMREQUIREDFORGC ) {
            //            System.out.println("GC inutile lastMemP="+lastMemP/(1024*1024)+"Mo lastMemI="+lastMemI/(1024*1024)+"Mo");
            return;
         }
         //         System.out.println("GC derni�re lib�ration "+Math.abs(lastMemP-lastMemI)/(1024*1024)+"Mo");
      }
      
      nbFlush=0;
      memCpt=!memCpt;
      aladin.gcIfRequired();

//      (new Thread("gc"){
//         @Override
//         public void run() {
//            memCpt=!memCpt;
//            nbFlush=0;
//            System.runFinalization();
//            System.gc();
//            aladin.setMemory();
//            //      System.out.println("GC done");
//            //            if( Aladin.BETA ) System.out.println(HealpixKey.getStats()
//            //                  +"."+nbReady+" hpx in mem ("+memSize/(1024*1024)+"Mb) - "+getCacheSize()/1024+"Mb in cache");
//         }
//      }).start();
   }

   /** Suppression d'un losange */
   protected void purge(HealpixKey healpix) {
      nbFlush+=healpix.free();
      if( nbFlush>20  ) gc();
      pixList.remove( key(healpix) );
   }

   /** Lance un wakeUp sur le loader si n�cessaire */
   protected void tryWakeUp() {
      loader.wakeUp();
   }

   // Juste pour un message
   private boolean firstSubtil=true;

   /** Acc�s ou chargement si n�cessaire d'un HealpixKey
    * @param mode HealpixKey.SYNC,SYNCONLYIFLOCAL,HealpixKey.ASYNC
    * @return null si le HealpixKey n'est pas READY
    */
   protected HealpixKey getHealpixLowLevel(int order,long npix,int z,int mode) {
      HealpixKey h = pixList.get( key(order,npix,z) );
      if( h==null) {
         h = new HealpixKey(this,order,npix,z,mode);
         pixList.put( key(order,npix,z), h);
      }
      if( h.getStatus()!=HealpixKey.READY ) return null;
      return h;
   }

   /** Retourne le losange Healpix s'il est charg�, sinon retourne null
    * et si flagLoad=true, demande en plus son chargement si n�cessaire */
   protected HealpixKey getHealpix(int order,long npix,boolean flagLoad) {
      return getHealpix(order,npix,(int)getZ(),flagLoad);
   }
   protected HealpixKey getHealpix(int order,long npix,int z, boolean flagLoad) {

      HealpixKey healpix =  pixList.get( key(order,npix,z) );
      if( healpix!=null ) return healpix;

      // Peut �tre peut-on se servir du allsky.fits|.jpeg ?
      healpix = getHealpixFromAllSky(order,npix);
      if( healpix!=null ) return healpix;

      if( flagLoad ) return askForHealpix(order,npix);
      return null;
   }

   // Si la map n'est pas profonde, les losanges Allsky feront l'affaire */
   protected HealpixKey getHealpixFromAllSky(int order,long npix) {
      if( order>3 /* || allsky==null */ ) return null;

      HealpixKey allsky = pixList.get( key(getMinOrder(),  -1) );
      if( allsky==null || allsky.getStatus()!=HealpixKey.READY ) return null;

      int orderLosange= getTileOrder();
      if( orderLosange>0 && orderLosange <= getAllSkyOrder(allsky) ) {
         HealpixKey [] list = allsky.getPixList();
         if( list==null ) return null;
         HealpixKey healpix = list[ (int)npix ];

         if( healpix!=null ) {
            if( firstSubtil ) {
               aladin.trace(4,"PlanBG.getHealpix "+label+" will use Allsky for order 3 diamonds!");
               firstSubtil=false;
            }
            return healpix;
         }
      }
      return null;
   }

   //   protected HealpixKey getHealpix(int order,long npix,boolean flagLoad) {
   //      HealpixKey healpix =  (HealpixKey) pixList.get( key(order,npix) );
   //      if( healpix!=null ) return healpix;
   //      if( flagLoad ) return askForHealpix(order,npix);
   //      return null;
   //   }

   static final private long [] ALLSKY = { 0L,1L,2L,3L,4L,5L,6L,7L,8L,9L,10L,11L };
   
   static long [] getNpixList(int order, Coord center, double radius) throws Exception {
      if( order==0 ) return ALLSKY;
      return CDSHealpix.query_disc( order,center.al, center.del, Math.toRadians(radius),true);
   }

   static protected String CURRENTMODE="";

   /** Retourne le centre de la vue. */
   protected Coord getCooCentre(ViewSimple v) {
      Coord center = v.getCooCentre();
      if( center==null ) return null;
      center = Localisation.frameToFrame(center,Localisation.ICRS,frameOrigin);
      return center;
   }

   protected long [] getPixListView(ViewSimple v, int order) {
      return getPixList(v,null,order);
      //      Projection proj = v.getProj();
      //      ArrayList<double[]> vlist = new ArrayList<double[]>(4);
      //      Coord coo = new Coord();
      //      for( int i=0; i<4; i++ ) {
      //         PointD p = v.getPosition((double)(i==0 || i==3 ? 0 : v.getWidth()),
      //                                  (double)(i==0 || i==1 ? 0 : v.getHeight()) );
      //         coo.x = p.x; coo.y = p.y;
      //         proj.getCoord(coo);
      //         if( Double.isNaN(coo.al) ) return new long[0];
      //         coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
      //         if( coo.del < -90) coo.del = -180-coo.del;
      //         if( coo.al < 0   ) coo.al  = 360+coo.al;
      //         if( coo.del > 90 ) coo.del = 180-coo.del;
      //         if( coo.al > 360 ) coo.al  = coo.al-360;
      //
      //         vlist.add( new double[]{coo.al,coo.del} );
      //      }
      //      long[] b=null;
      //      try {
      //         long nside = CDSHealpix.pow2(order);
      //         b = CDSHealpix.query_polygon(nside,vlist);
      //      } catch( Exception e ) {
      //         e.printStackTrace();
      //         b=new long[0];
      //      }
      //      return b;
   }
   
   
   /** Retourne la liste des losanges susceptibles de recouvrir la vue pour un order donn� */
   protected long [] getPixList(ViewSimple v, Coord center, int order) {
      
      try {
         if( center==null ) center = getCooCentre(v);
         double radius = v.getTaille();
         radius= (radius/2)*1.43;
         return getNpixList(order,center,radius).clone();
      } catch( Exception e ) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); return null; } // new long[]{}; }
      
   }

//   /** Supprime du tableau les losanges qui sont hors du MOC */
//   private long [] filterByMoc(long [] pix,int order) {
//      if( moc==null || moc.isAllSky() ) return pix;
//      int j=0;
//      for( int i=0; i<pix.length; i++ ) {
//         if( moc.isIntersecting(order,pix[i]) ) {
//            if( i!=j ) pix[j]=pix[i];
//            j++;
//         }
//      }
//      if( j==pix.length ) return pix;
//      long [] p = new long[j];
//      System.arraycopy(pix, 0, p, 0, j);
//      return p;
//   }

   protected double RES[]=null;

   /** Retourne le Nordre des losanges (images) */
   protected int getTileOrder() {
      if( tileOrder==-1 ) return Constante.ORDER;
      return tileOrder;
   }

   // Positionne l'ordre des losanges (trouv� lors de la lecture du premier losange
   protected void setTileOrder(int tileOrder) {
      if( this.tileOrder!=-1 || tileOrder<=0 ) return;
      this.tileOrder=tileOrder;
   }

   private int allSkyOrder=-1;

   /** Retourne le Nordre des losanges de la liste allsky, -1 si inconnu */
   protected int getAllSkyOrder(HealpixKey allsky) {
      if( allSkyOrder==-1 ) {
         if( allsky!=null ) {
            HealpixKey healpix[] = allsky.getPixList();
            if( healpix!=null ) {
               allSkyOrder=healpix[0].getLosangeOrder();
            }
         }
      }
      return allSkyOrder;
   }

   /** Retourne true s'il est possible d'acc�der � la valeur
    * du pixel Origin courant par un acc�s disque direct */
   protected boolean pixelsOriginFromDisk() {
      return flagOk && !color && isTruePixels() /* && !lockGetPixelInfo*/ ;
   }

   //   protected boolean lockGetPixelInfo=false;

   /** Retourne l'order de l'affichage actuel */
   protected int getOrder() {
      int o = Math.min(maxOrder(),maxOrder);
      if( o<getMinOrder() ) o = getMinOrder();
      return o;
   }

   protected int maxOrder() {
      ViewSimple v = aladin.view.getCurrentView();
      return maxOrder(v);
   }

   public String getSurveyDir() {
      if( local ) return url;
      return getCacheDir() + Util.FS + getCacheName();
   }

   /** Retourne sous forme d'une chaine �ditable la valeur du pixel  */
   protected String getPixelInfo(double x,double y,int mode) {
      if( !pixelsOriginFromDisk() || mode!=View.REAL ) return "";
      double pixel = getPixelInDouble(x,y);
      if( Double.isNaN(pixel) ) return "";
      return Y( pixel );
   }

   /** Retourne la valeur 8 bits du pixel indiqu� en coordonn�es image*/
   public int getPixel8(int x,int y) {
      double pix = getPixelInDouble(x,y);
      return (int)( (pix - pixelMin)*256/(pixelMax-pixelMin) );
   }

   /** Retourne la valeur du pixel en x,y sous la forme d'un double, ou NaN si impossible */
   protected double getPixelOriginInDouble(int x,int y) {
      return getPixelInDouble(x,y);
   }

   /** Retourne la valeur du pixel en double  */
   protected double getPixelInDouble(int x,int y) { return getPixelInDouble((double)x,(double)y); }
   protected double getPixelInDouble(double x,double y) {
      if( !pixelsOriginFromDisk() ) return Double.NaN;
      int bitpix = getBitpix();
      int mynpix = Math.abs(bitpix)/8;

      byte onePixelOrigin[] = new byte[mynpix];
      if( !getOnePixelFromCache(onePixelOrigin,x,y) ) return Double.NaN;
      return getPixVal(onePixelOrigin,bitpix,0)*bScale+bZero;
   }

   /** Relecture dans le cache d'un unique pixel d'origine.
    * Si besoin, ouvre le flux sur le fichier cache, mais ne le referme pas (voir Free() )
    * @param projd pour pouvoir faire une extraction de pixel m�me si la projection courante est modifi� (crop) - PF - mars 2010
    * @param pixels le tableau qui va accueillir le pixel lu (doit avoir �t� taill� assez grand)
    * @param x,y coord du pixel � extraire  (attention, les lignes sont � l'envers !!!)
    * @return true si le pixel est disponible, sinon false
    */
   protected boolean getOnePixelFromCache(byte [] pixels, double x,double y) {
      return getOnePixelFromCache(projd,pixels,x,y);
   }

   protected boolean getOnePixelFromCache(Projection projd,byte [] pixels, double x,double y) {
      double val = getOnePixelFromCache(projd,x,y);
      if( Double.isNaN(val) ) return false;
      setPixVal(pixels, getBitpix() , 0, val);
      return true;
   }

   protected double getOnePixelFromCache(Projection projd, double x,double y) {
      return getOnePixelFromCache(projd,x,y,-1,HealpixKey.ONLYIFDISKAVAIL);
   }

   /**
    * @param order l'ordre Healpix pour lequel le pixel sera r�cup�r�, -1 si ordre courant de l'affichage
    * @param mode HealpixKey.NOW - les donn�es seront charg�es imm�diatement o� qu'elles soient, sinon asynchrone
    *             HealpixKey.ONLYIFDISKAVAIL - les donn�es seront charg�es imm�diatement si elles sont pr�sentes sur le disque locale, sinon asynchrone
    */
   protected double getOnePixelFromCache(Projection projd, double x,double y,int order,int mode) {
      return getOnePixelFromCache(projd, x, y,order,(int)getZ(),mode);
   }
   protected double getOnePixelFromCache(Projection projd, double x,double y,int order,int z,int mode) {
      double pixel = Double.NaN;
      if( order<=0 ) order = getOrder();   // L'ordre n'est pas mentionn�, on prend l'ordre de l'affichage courant
//      int nSideFile = (int)CDSHealpix.pow2(order);

      try {
         Coord coo = new Coord();
         coo.x = x; coo.y = y;
         projd.getCoord(coo);
         coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
         if( Double.isNaN(coo.al) || Double.isNaN(coo.del) ) return Double.NaN;
         double[] polar = CDSHealpix.radecToPolar(new double[] {coo.al, coo.del});
         long npixFile = CDSHealpix.ang2pix_nest( order, polar[0], polar[1]);

         pixel = getHealpixPixel(order,npixFile,polar[0], polar[1], z,mode);

      } catch( Exception e ) {
         if( aladin.levelTrace>=3 ) e.printStackTrace();
      }

      return pixel;
   }
   
//   try {
//      Coord coo = new Coord();
//      coo.x = x; coo.y = y;
//      projd.getCoord(coo);
//      coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
//      if( Double.isNaN(coo.al) || Double.isNaN(coo.del) ) return Double.NaN;
//      double[] polar = CDSHealpix.radecToPolar(new double[] {coo.al, coo.del});
//      long npixFile = CDSHealpix.ang2pix_nest( nSideFile, polar[0], polar[1]);
//
//      pixel = getHealpixPixel(order,npixFile,polar[0], polar[1], z,mode);
//
//   } catch( Exception e ) {
//      if( aladin.levelTrace>=3 ) e.printStackTrace();
//   }


   /**
    * Retourne la valeur du pixel Healpix qui se trouve en theta,phi pour un losange HealpixKey orderFile/npixFile
    * @param HealpixKey.NOW - les donn�es seront charg�es imm�diatement o� qu'elles soient, sinon asynchrone
    *             HealpixKey.ONLYIFDISKAVAIL - les donn�es seront charg�es imm�diatement si elles sont pr�sentes sur le disque locale, sinon asynchrone
    */
   protected double getHealpixPixel(int orderFile,long npixFile,double theta, double phi,int z,int mode) {
      HealpixKey h = mode==HealpixKey.PIX8 ? pixList.get( key(orderFile,npixFile,z) ) :
         getHealpixLowLevel(orderFile,npixFile,z,mode==HealpixKey.NOW ? HealpixKey.SYNC : HealpixKey.SYNCONLYIFLOCAL);
      //      HealpixKey h = getHealpixLowLevel(orderFile,npixFile,z,mode==HealpixKey.NOW ? HealpixKey.SYNC : HealpixKey.SYNCONLYIFLOCAL);
      if( h==null || h.getStatus()!=HealpixKey.READY ) return Double.NaN;
//      long nside = h.width * CDSHealpix.pow2(h.order);
      int order = (int)CDSHealpix.log2(h.width) + h.order;
      try {
         long healpixIdxPixel = CDSHealpix.ang2pix_nest(order, theta, phi);
         return h.getPixelValue(healpixIdxPixel,mode);
      } catch( Exception e ) {
         return Double.NaN;
      }
   }

   /**
    * Retourne la valeur du pixel Healpix qui se trouve en healpixIdxPixel pour un losange HealpixKey orderFile/npixFile
    * @param HealpixKey.NOW - les donn�es seront charg�es imm�diatement o� qu'elles soient, sinon asynchrone
    *             HealpixKey.ONLYIFDISKAVAIL - les donn�es seront charg�es imm�diatement si elles sont pr�sentes sur le disque locale, sinon asynchrone
    */
   protected double getHealpixPixel(int orderFile,long npixFile,long healpixIdxPixel,int mode) throws Exception {
      return getHealpixPixel(orderFile,npixFile,healpixIdxPixel,-1,mode);
   }
   protected double getHealpixPixel(int orderFile,long npixFile,long healpixIdxPixel,int z,int mode) throws Exception {
      if( z==-1 ) z=(int)getZ();
      HealpixKey h = getHealpixLowLevel(orderFile,npixFile,z,mode==HealpixKey.ASYNC ? HealpixKey.SYNC : HealpixKey.SYNCONLYIFLOCAL);
//      if( h==null ) return Double.NaN;
      if( h==null ) throw new Exception("Tile "+orderFile+"/"+npixFile+" not yet ready");
      return h.getPixelValue(healpixIdxPixel,mode);
   }

   /**
    * R�cup�ration d'une valeur de pixel HEALPix pour une coordonn�e particuli�re
    * par approximation lin�aire avec le pixel le plus proche
    * ainsi que les 4 voisins N-S-E-W.
    * @param order l'ordre Healpix pour lequel le pixel sera r�cup�r�, -1 si ordre courant de l'affichage
    */
   protected double getHealpixClosestPixel(double ra,double dec,int order) {
      double pixel = Double.NaN;

      try {
         double[] polar = CDSHealpix.radecToPolar(new double[] {ra, dec});
         long npixFile = CDSHealpix.ang2pix_nest( order, polar[0], polar[1]);

         HealpixKey h = getHealpixLowLevel(order,npixFile,(int)getZ(),HealpixKey.SYNC);
         if( h==null ) return Double.NaN;

         int orderPix = (int)CDSHealpix.log2( h.width) + h.order;
         long npixPixel = CDSHealpix.ang2pix_nest(orderPix, polar[0], polar[1]);

         HealpixKey h1 = getHealpixLowLevel(order,npixFile,(int)getZ(),HealpixKey.SYNC);
         if( h1==null ) return Double.NaN;
         pixel = h1.getPixelValue(npixPixel,HealpixKey.NOW);

      } catch( Exception e ) {
         if( aladin.levelTrace>=3 ) e.printStackTrace();
      }

      return pixel;
   }

   /**
    * R�cup�ration d'une valeur de pixel HEALPix pour une coordonn�e particuli�re
    * par approximation lin�aire 3 voisins (S,SW,W) + le pixel en question
    * @param order l'ordre Healpix pour lequel le pixel sera r�cup�r�, -1 si ordre courant de l'affichage
    */
   protected double getHealpixLinearPixel(double ra,double dec,double ra1,double dec1, int order) {
      double pixel = Double.NaN;
//      int nSideFile = (int)CDSHealpix.pow2(order);

      try {
         double[] polar = CDSHealpix.radecToPolar(new double[] {ra, dec});
         double[] polar1 = CDSHealpix.radecToPolar(new double[] {ra1, dec1});
         long npixFile = CDSHealpix.ang2pix_nest( order, polar[0], polar[1]);

         HealpixKey h = getHealpixLowLevel(order,npixFile,(int)getZ(),HealpixKey.SYNC);
         if( h==null ) return Double.NaN;

//         long nside = h.width * CDSHealpix.pow2(h.order);
         int orderPix = (int)CDSHealpix.log2(h.width) + h.order;
         long npixPixel = CDSHealpix.ang2pix_nest(orderPix, polar[0], polar[1]);

         long [] voisins = CDSHealpix.neighbours(orderPix,npixPixel);

         // On ne va prendre 3 voisins (S,SW,W) + le pixel en question
         // pour l'interpolation
         int m = 4;
         for( int i=m; i>=1; i-- ) voisins[i] = voisins[i-1];
         voisins[0]=npixPixel;
         double totalPixel=0,totalCoef=0;
         HealpixKey h1;
         for( int i=0; i<m; i++ ) {
            h1=h;
            long nlpix = voisins[i];

            // Test au cas o� l'on d�borde du HealpixKey courant
            long startIdx =  h.npix * h.width * h.width;
            long pixOffset = nlpix-startIdx;
            if( pixOffset<0 || pixOffset>=h.width*h.width ) {
               long npixFile1 = nlpix/(h.width*h.width);
               HealpixKey htmp = getHealpixLowLevel(order,npixFile1,(int)getZ(),HealpixKey.SYNC);
               if( htmp==null ) continue;
               h1=htmp;
            }

            // Pond�ration en prenant comme coefficient l'inverse de la distance sur les coordonn�es polaires
            try {
               double pix = h1.getPixelValue(nlpix,HealpixKey.NOW);
               if( Double.isNaN(pix) ) continue;
               double [] polar2 = CDSHealpix.pix2ang_nest(orderPix, nlpix);
               double coef = Coo.distance(polar1[0],polar1[1],polar2[0],polar2[1]);
               if( coef==0 ) return pix;  // Je suis pile dessus
               double c = 1/coef;
               totalPixel += pix * c;
               totalCoef += c;
            } catch( Exception e ) { continue; }
         }
         pixel = totalPixel/totalCoef;

      } catch( Exception e ) {
         if( aladin.levelTrace>=3 ) e.printStackTrace();
      }

      return pixel;
   }


   public static final int DIRSIZE = 10000;

   static public String getFilePath(String survey,int order, long npix) {
      return
            survey + Util.FS +
            "Norder" + order + Util.FS +
            "Dir" + ((npix / DIRSIZE)*DIRSIZE) + Util.FS +
            "Npix" + npix;
   }

   protected int [] xy2hpx = null;
   protected int [] hpx2xy = null;

   /** M�thode r�cursive utilis�e par createHealpixOrder */
   static private void fillUp(int [] npix, int nsize, int [] pos) {
      int size = nsize*nsize;
      int [][] fils = new int[4][size/4];
      int [] nb = new int[4];
      for( int i=0; i<size; i++ ) {
         int dg = (i%nsize) < (nsize/2) ? 0 : 1;
         int bh = i<(size/2) ? 1 : 0;
         int quad = (dg<<1) | bh;
         int j = pos==null ? i : pos[i];
         npix[j] = npix[j]<<2 | quad;
         fils[quad][ nb[quad]++ ] = j;
      }
      if( size>4 )  for( int i=0; i<4; i++ ) fillUp(npix, nsize/2, fils[i]);
   }

   /** Creation des tableaux de correspondance indice Healpix <=> indice XY */
   protected void createHealpixOrder(int order) throws Exception {
      if (order==0) {
         xy2hpx = hpx2xy = new int[] {0};
         return;
      }
      int nsize = (int)CDSHealpix.pow2(order);
      if( CDSHealpix.log2(nsize)!=order ) throw new Exception("Only HEALPix order power of 2 are supported");
      xy2hpx = new int[nsize*nsize];
      hpx2xy = new int[nsize*nsize];
      fillUp(xy2hpx,nsize,null);
      for( int i=0; i<xy2hpx.length; i++ ) hpx2xy[ xy2hpx[i] ] = i;
   }

   /** Retourne l'indice XY en fonction d'un indice Healpix
    * => n�cessit� d'initialiser au pr�alable avec createHealpixOrdre(int) */
   final public int xy2hpx(int hpxOffset) {
      return xy2hpx[hpxOffset];
   }

   /** Retourne l'indice XY en fonction d'un indice Healpix
    * => n�cessit� d'initialiser au pr�alable avec createHealpixOrdre(int) */
   final public int hpx2xy(int xyOffset) {
      return hpx2xy[xyOffset];
   }

   private long lastTouch=0L;
   protected void touchCache() {
      long t = System.currentTimeMillis();
      if( t-lastTouch<60*1000L ) return;     // On "touch" au mieux toutes les minutes
      lastTouch=t;
      String pathName = getCacheDir()+Util.FS+getCacheName();
      (new File(pathName)).setLastModified(t);
      Date d = new Date();
      d.setTime(lastTouch);
      aladin.trace(4,"PlanBG.touchCache() : Date:"+d+" => "+pathName);
   }

   protected long lastIz=-1;
   protected int lastMaxOrder=3;

   private int oLosangeOrder = -1;

   protected int maxOrder(ViewSimple v) {
      long iz = v.getIZ();
      if( lastIz==iz ) return lastMaxOrder;
      lastIz=iz;

      // D�couverte ou actualisation de la r�solution des losanges ?
      int myOrder = getTileOrder();
      if( RES==null || oLosangeOrder!=myOrder ) {
         oLosangeOrder = myOrder;
         if( RES==null ) RES = new double[20];
         for( lastMaxOrder=0; lastMaxOrder<20; lastMaxOrder++ ) {
//            long nside = CDSHealpix.pow2(lastMaxOrder+myOrder+1);
            int order = lastMaxOrder+myOrder+1;
            RES[lastMaxOrder]=CDSHealpix.pixRes(order)/3600;
         }
      }
      double pixSize = v.getPixelSize();
      for( lastMaxOrder=0; lastMaxOrder<RES.length && RES[lastMaxOrder]>pixSize; lastMaxOrder++ );
      lastMaxOrder=adjustMaxOrder(lastMaxOrder,pixSize);
      
      return lastMaxOrder;
   }

   protected int adjustMaxOrder(int lastMaxOrder,double pixSize) {
      if( lastMaxOrder<=2 && pixSize<0.06 && getMinOrder()==3) lastMaxOrder=3;
      return lastMaxOrder;
   }

   /** Retourne l'indication du format de codage des losanges */
   protected String getFormat() {
      if( color ) {
         if( inFits ) return "FITS RGB color";
         else return (colorPNG || inPNG ? "PNG":"JPEG")+" color";
      }
      if( truePixels ) return "FITS true pixels (BITPIX="+bitpix+")";
      else return (colorPNG || inPNG ? "PNG":"JPEG")+" 8 bits pixels";
   }

   /** Change le format d'affichage truePixels (Fits) <=> 8bits (JPEG) */
   protected void switchFormat() {
      
      // m�morisation de la fonction de transfert de l'ancien mode
      if( truePixels ) transferFct4Fits = transfertFct;
      else transferFct4Preview = transfertFct;
      
      truePixels = !truePixels;
      setPixMode(truePixels ? PIX_TRUE : colorPNG ? PIX_255 : PIX_256);
      
      // Positionnement de la fonction de transfert pour le nouveau mode
      transfertFct = truePixels ? transferFct4Fits : transferFct4Preview;
      
      restoreCM();
      forceReload();
      if( aladin.frameCM!=null ) aladin.frameCM.majCM(true);
   }

   /** force le rechargement des losanges */
   public void forceReload() {
      FreePixList();
      changeImgID();
      flagRecut=true;
      flagRecutCoo = new Coord( aladin.view.repere.raj, aladin.view.repere.dej );
      flagRecutRadius = aladin.view.getCurrentView().getTaille()/2;
      resetHist();
   }


   /** Retourne la r�solution angulaire du pixel au NSIDE max (en degr�s)
    *  avec une unit� ad�quate */
   protected String getMaxResolution() {
      return Coord.getUnit( getPixelResolution() );
   }

   /** Retourne l'ordre "fichier" maximal (plus grand r�pertoire NOrderNN) */
   public int getMaxFileOrder() { return maxOrder; }

   /** Retourne l'ordre Healpix max */
   public int getMaxHealpixOrder() {
      return maxOrder + getTileOrder();
   }

   /** Retourne la r�solution angulaire du pixel au NSIDE max (en degr�s) */
   public double getPixelResolution() {
//      long nside = CDSHealpix.pow2(getMaxHealpixOrder());
      return CDSHealpix.pixRes(getMaxHealpixOrder())/3600;
   }

   /** Retourne true si l'image a �t� enti�rement "draw�" � la r�solution attendue */
   protected boolean isFullyDrawn() { return isDrawn () && allWaitingKeysDrawn; }

   /** Retourne true si le dernier appel � draw() � dessiner au moins un losange */
   //   protected boolean isDrawn() { return pixList.size()>0 && hasDrawnSomething; }
   protected boolean isDrawn() { return readyDone; }

   /** Retourne true si l'image n'est pas pr�te � �tre affich� */
   protected boolean isLoading() { return !loader.isReadyForDrawing(); }

   /** retourne true s'il est encore possible de zoomer en avant pour avoir plus de d�tail */
   protected boolean hasMoreDetails() { return hasMoreDetails; }

   /** retourne l'ordre courant sur l'ordre max */
   protected String getInfoDetails() { return getOrder()+"/"+maxOrder; }

   /** positionne le flage indiquant qu'il y a ou non encore plus de d�tails disponibles */
   protected void setHasMoreDetails(boolean flag) { hasMoreDetails=flag; }

   private boolean hasMoreDetails=true;

   @Override
   protected boolean isSync() {
      if( error!=null ) {
         aladin.trace(6,"PlanBG.isSync()=true:"+label+" => in error (error!=null)");
         return true;
      }
      if( !flagOk ) {
         aladin.trace(6,"PlanBG.isSync()=false: "+label+" => not ready (!flagOk)");
         return false;
      }
      if( !active ) {
         aladin.trace(6,"PlanBG.isSync()=true: "+label+"=> not active (!active)");
         return true;
      }
      if( getOpacityLevel()==0f && !ref ) {
         aladin.trace(6,"PlanBG.isSync()=true: "+label+"=> transparent (!ref && opacity="+getOpacityLevel()+")");
         return true;
      }
      if( flagProcessing ) {
         aladin.trace(6,"PlanBG.isSync()=false: "+label+"=> is processing (flagProcessing)");
         return false;
      }
      if( isLoading() ) {
         aladin.trace(6,"PlanBG.isSync()=false: "+label+"=> is loading (isLoading())");
         return false;
      }
      return true;
   }

   /** Retourne true s'il s'agit d'un HiPS cube */
   public boolean isCube() { return cube; }

   /** Retourne true s'il s'agit d'un HiPS en couleur */
   public boolean isColored() { return color; }

   /** Retourne true si le all-sky est affich� en FITS */
   public boolean isTruePixels() { return truePixels; }

   /** Retourne true si le all-sky peut �tre affich� en FITS */
   public boolean canbeTruePixels() { return inFits; }

   /** Retourne true si le all-sky est local */
   public boolean isLocalAllSky() { return local; }

   private long [] children = null;

   /** Retourne true si tous les fils du losange "susceptibles" d'�tre
    * trac�s sont d�j� pr�ts � �tre dessin� */
   private boolean childrenReady(HealpixKey healpix,ViewSimple v,int maxOrder) {
      int nextOrder = healpix.order+1;
      children = healpix.getChildren(children);
      for( int i=0; i<4; i++ ){
         if( isOutMoc(nextOrder,children[i]) ) continue;
         HealpixKey fils = getHealpix(nextOrder,children[i],healpix.z,false);
         if( fils==null ) fils = new HealpixKey(this,nextOrder,children[i],HealpixKey.NOLOAD);
         if( fils.isOutView(v) ) continue;
         if( fils.getStatus()!=HealpixKey.READY ) {
            if( nextOrder==maxOrder-1 ) return false;
            // Mais peut �tre que la generation en dessous est dej� pr�te ?
            if( nextOrder<maxOrder-1 && !childrenReady(fils,v,maxOrder) ) return false;
         }
      }
//      System.out.println("***** "+ healpix+" inutile car a tout ses fils ou petits fils");
      return true;
   }

   /** Modification d'une projection */
   @Override
   protected void modifyProj(String label,int modeCalib,
         double alphai, double deltai, double rm,
         double cx, double cy,double r,
         double rot,boolean sym,int t,int system) {
      super.modifyProj(label,modeCalib,alphai,deltai,rm,cx,cy,r,rot,sym,t,system);

      // POUR LE MOMENT CE N'EST PAS UTILISE (PF FEV 2009)
      //      for( int i=0; i<aladin.view.viewSimple.length; i++ ) {
      //         ViewSimple v = aladin.view.viewSimple[i];
      //         if( v.pref!=this || v.projLocal==null ) continue;
      //         v.projLocal.modify(label,modeCalib,alphai,deltai,rm,rm,cx,cy,r,r,rot,sym,t);
      //      }
   }

   /** Trac� de control des losanges Healpix d'ordre "order" visible dans
    * la vue */
   //   protected void drawCtrl(Graphics g,ViewSimple v,int order) {
   //      try {
   //         long pix[];
   //         pix = getPixListView(v,order);
   //         if( pix.length>4096 ) return;
   //         g.setColor( Couleur.getCouleur(order) );
   //         for( int i=0; i<pix.length; i++ ) {
   //            HealpixKey healpix = new HealpixKey(this,order,pix[i],HealpixKey.NOLOAD);
   //            if( healpix.isOutView(v) ) continue;
   //            healpix.drawCtrl(g,v);
   //         }
   //      } catch( Exception e ) {}
   //   }

   //   protected void setDebugIn(double raj,double dej,double radius) {
   //      if( !DEBUGMODE ) return;
   //      ViewSimple v = aladin.view.getCurrentView();
   //      int order = Math.max(3,maxOrder(v));
   //      long [] npix = null;
   //      Coord c = new Coord(raj,dej);
   //      try {
   //         c = Localisation.frameToFrame(c,Localisation.ICRS,frameOrigin);
   //         npix = getNpixList(order,c,radius);
   //      } catch( Exception e ) {
   //         e.printStackTrace();
   //         return;
   //      }
   //
   //      boolean diff = true;
   //      if( npix.length == pixDebugIn.length ) {
   //         diff = false;
   //         for( int i=0; i<npix.length; i++ ) {
   //            if( npix[i]!=pixDebugIn[i] ) { diff=true; break; }
   //         }
   //      }
   //
   //      if( diff ) {
   //         pixDebugIn = npix;
   //         changeImgID();
   //         v.repaint();
   //
   //         System.out.print("querydisk("+aladin.localisation.J2000ToString(raj,dej)+", "+Coord.getUnit(radius)+")\n   2^"+order+" => ");
   //         for( int i=0; i<pixDebugIn.length; i++ )  System.out.print(" "+pixDebugIn[i]);
   //         System.out.println();
   //      }
   //   }


   //   protected void setDebugIn(Ligne deb) {
   //      if( !DEBUGMODE ) return;
   //      ViewSimple v = aladin.view.getCurrentView();
   //      int order = Math.max(3,maxOrder(v));
   //      ArrayList<double[]> vlist = new ArrayList<double[]>();
   //      Coord coo = new Coord();
   //      Ligne tmp;
   //      int i;
   //      for( i=0, tmp=deb.getFirstBout(); tmp.finligne!=null; tmp=tmp.finligne, i++ ) {
   //         coo.al = tmp.raj; coo.del = tmp.dej;
   //         if( Double.isNaN(coo.al) ) return;
   //         coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
   //         if( coo.del < -90) coo.del = -180-coo.del;
   //         if( coo.al < 0   ) coo.al  = 360+coo.al;
   //         if( coo.del > 90 ) coo.del = 180-coo.del;
   //         if( coo.al > 360 ) coo.al  = coo.al-360;
   //
   //         vlist.add( new double[]{coo.al,coo.del} );
   //      }
   //      long[] npix=null;
   //      try {
   //         long nside = CDSHealpix.pow2(order);
   //         npix = CDSHealpix.query_polygon(nside,vlist);
   //      } catch( Exception e ) {
   //         npix=new long[0];
   //      }
   //
   //      boolean diff = true;
   //      if( npix.length == pixDebugIn.length ) {
   //         diff = false;
   //         for( i=0; i<npix.length; i++ ) {
   //            if( npix[i]!=pixDebugIn[i] ) { diff=true; break; }
   //         }
   //      }
   //
   //      if( diff ) {
   //         pixDebugIn = npix;
   //         changeImgID();
   //         v.repaint();
   //         StringBuffer s = new StringBuffer();
   //         for( i=0; i<vlist.size(); i++ ) {
   //            double a[] = vlist.get(i);
   //            if( s.length()>0 ) s.append(", ");
   //            s.append(Util.myRound(a[0]+"",2)+(a[1]<0?"":"+")+Util.myRound(a[1]+"",2));
   //         }
   //         System.out.print("query_polygon("+s+")\n   2^"+order+" => ");
   //         for( i=0; i<pixDebugIn.length; i++ )  System.out.print(" "+pixDebugIn[i]);
   //         System.out.println();
   //      }
   //   }


   protected int futurGetMinOrder() { return minOrder==-1 ? 3 : minOrder; }
//   protected int getMinOrder() { return 3; }
   
   protected int getMinOrder() {
      if( !hasAllSky || hasRecutListener() ) return futurGetMinOrder();
      return 3;
   }
   
   /** Chargement synchrone du allsky (n�cessaire dans le cas d'une modif de la table des couleurs (Densit� map),
    * avant m�me le premier affichage) */
   protected void loadAllSkyNow() {
      HealpixKey allsky = pixList.get( key(getMinOrder(),  -1) );
      if( allsky==null ) {
         allsky =  new HealpixAllsky(this,getMinOrder());
         pixList.put( key(allsky) /* key(ALLSKYORDER,-1)*/, allsky);
         try { allsky.loadNow(); } catch( Exception e ) { e.printStackTrace(); }
      }
   }
   

   /** Dessin du ciel complet en rapide � l'ordre indiqu� */
   protected boolean drawAllSky(Graphics g,ViewSimple v,BufferedImage imgb) {
      boolean hasDrawnSomething=false;
      int z = (int)getZ(v);
      int order = getMinOrder();
      HealpixKey allsky = pixList.get( key(order,  -1, z) );
      flagWaitAllSky=false;
      
      if( allsky==null ) {
         if( drawMode==DRAWPOLARISATION ) allsky = new HealpixAllskyPol(this,order);
         else allsky =  new HealpixAllsky(this,order,z);
         pixList.put( key(allsky) , allsky);

         if( local ) {
            allsky.waitLock();
            if( allsky.getStatus()==HealpixKey.TOBELOADFROMNET ) allsky.loadFromNet();
            allsky.unLock();
         } else {
            if( !useCache || !allsky.isCached() ) {
               tryWakeUp();
               if( v.pref==this ) drawBackground(g, v);
               //               g.setColor(Color.white);
               //               g.fillRect(0,0,v.getWidth(),v.getHeight());
               return true;

            } else allsky.loadFromCache();
         }
      }
      int status= allsky.getStatus();

      
      if( status==HealpixKey.ERROR ) {
//         System.out.println("Allsky not found");
         hasAllSky=false;
         return false;
      }
      // A POURSUIVRE QUAND J'AURAIS LE TEMPS
//      if( status==HealpixKey.ERROR ){
//         if( minOrder!=0 ) return false;
//         
//         System.out.println("On g�n�re les tuiles allsky par les tuiles Norder0");     
//         allsky =  new HealpixAllsky(this,xxxxx 0,z);
//         pixList.put( key(allsky) , allsky);
//         tryWakeUp();
//         if( v.pref==this ) drawBackground(g, v);
//         return true;
//      }
      
      if( status==HealpixKey.READY ) {
         allsky.resetTimer();
         //         long t = Util.getTime();
         statNbItems=0L;

         double taille = Math.min(v.getTailleRA(),v.getTailleDE());
         if( NOALLSKY ) return true;

         
         HealpixKey [] allSkyPixList = allsky.getPixList();
         long [] pixList;
         
         // Petite portion du ciel => recherche des losanges sp�cifiquement
         if( taille<40 && !v.isAllSky() ) {
            pixList = getPixList(v, null, order);
            
         // Sinon tous les losanges sans r�fl�chir
         } else {
            pixList = new long[ allSkyPixList.length ];
            for( int i=0; i<pixList.length; i++ ) pixList[i]=i;
         }

         for( int i=0; i<pixList.length; i++ ) {
            int npix = (int)pixList[i];
            HealpixKey healpix = allSkyPixList[ npix ];
            if( healpix==null || healpix.isOutView(v) ) continue;
            if( drawMode==DRAWPIXEL ) healpix.draw(g,v);
            else if( drawMode==DRAWPOLARISATION ) ((HealpixKeyPol)healpix).drawPolarisation(g, v);
            statNbItems++;
            hasDrawnSomething=true;
         }


         // Rebouchage des trous par m�thode inverse
         drawHoles(imgb,v,allsky.getPixList());
         
      } else {
         flagWaitAllSky=true;
      }

      return hasDrawnSomething;
   }
   
//       Tous les cas o� c'est mieux de ne pas faire le nettoyage (trop lent a priori)
   private boolean mustDrawHoles(ViewSimple v) {
      if( mustDrawFast() ) return false;
      if( !v.isAllSky() ) return false;
      try {
         if( moc!=null && (moc.getSpaceMoc()).getCoverage()<0.3) return false;
      } catch( Exception e1 ) { }
      
      // POUR LE MOMENT - PF Juillet 2022
      // Le souci de "boucher" les trous sur un HiPS transparent oblige � faire un "aplat" dans le drawBackground()
      // et nom un gradian. Cela pose �galement un souci lorsqu'il y a des plans en dessous dans la pile
      // Pour le moment j'inhibe cette fonction pour les plans transparents
      if( isTransparent() ) return false;
      return true;
   }
   
   // On bouche les trous du ciel complet en m�thode inverse, c�d en parcourant les pixels d'arriv�e
   // qui n'ont pas �t� pris en compte par le trac� des losanges HEALPix
   // et en recherchant sa valeur dans la bonne tuile du allsky (m�thode plus proche)
   private void drawHoles(BufferedImage imgb,ViewSimple v,HealpixKey allsky[]) {
      
      if( imgb==null ) return;
      if( !mustDrawHoles(v) ) return;
      
      long t0 = System.currentTimeMillis();
      
      int vide = 0x00FFFFFF & getColorHoles().getRGB(); // Valeur du pixel si c'est un "trou"
      
      // Buffer des pixels d�ja trac� par les losanges
      int [] pixelsRGB = ((DataBufferInt)imgb.getRaster().getDataBuffer()).getData();
      int width = imgb.getWidth();
      int height = imgb.getHeight();
      
      Projection proj = v.getProj();
      Coord c = new Coord();
      int orderAllSky = (int)CDSHealpix.log2( 64 );
      int orderPix = orderAllSky + 3;
      int offset=0;
      int rgb,pix;
      int nb=0;
      for(int y=0; y<height; y++ ) {
         for( int x=0; x<width; x++, offset++ ) {
            rgb = pixelsRGB[offset];
            if( (0x00FFFFFF & rgb)!=vide ) continue;   // encore du fond donc pas dans un trou => rien � faire
            
            Point p = v.getPosition(x, y);
            c.x=p.x; c.y=p.y;
            proj.getCoord(c);
            c = Localisation.frameToFrame(c,Localisation.ICRS,frameOrigin);
            
            if( Double.isNaN(c.al) ) continue;  // hors projection => rien � faire
            else {

               // Recherche de la valeur du pixel dans la bonne tuile
               // soit en couleur, soit en niveaux de gris
               try {
                  double[] polar = CDSHealpix.radecToPolar(new double[] {c.al, c.del});
                  long npixPixel = CDSHealpix.ang2pix_nest(orderPix, polar[0], polar[1]);
                  long npixFile = npixPixel>>> (orderAllSky<<1);
                  if( isOutMoc(3,npixFile) ) continue;
//                  if( isOutMoc(orderPix,npixPixel) ) continue;

                  HealpixKey h1 = allsky[ (int)npixFile ];
                  if( h1==null ) continue;
                  nb++;
                  if( color ) pix = h1.getPixelRGB(npixPixel);
                  else  pix = cm.getRGB( h1.getPixelByte(npixPixel) );

                  if( aladin.levelTrace>=4 ) pix=pix | 0xFFFF00FF;  // en cyan pour voir les trous bouch�s
                  pixelsRGB[offset] = pix;
               } catch( Exception e ) {
               }
            }
         }
      }
      if( nb>0 ) imgb.setRGB(0, 0, width, height, pixelsRGB, 0, width);
      
      long t1 = System.currentTimeMillis();
      aladin.trace(4,"drawHoles in "+(t1-t0)+"ms redrawnPixel: "+nb+"/"+offset);
   }
   

//   protected PlanImage crop( double ra, double de, double sizeRa, double sizeDe, double factor) {
//      
//      double pixRes = getPixelResolution();
//      int width = sizeRa/pixRes;
//      
//      
//      PlanImage pi = new PlanImage(aladin);
//      pi.width = pi.naxis1 = width;
//      pi.height = pi.naxis2 = height;
//      pi.bitpix = bitpix;
//      pi.pixelsOrigin = new byte[ Math.abs( (bitpix/8) ) * width*height ];
//      
//      pi.projd = new Projection(,Projection.WCS,co.al,co.del,60*4,60*4,250,250,500,500,0,longAsc,
//            projection,Calib.FK5);
//   
//      AladinData ad = aladin.createAladinData("My Image");
//      
//      // Pixels
//      double [][] pix = new double[200][300];
//      for( int x=0; x<200; x++ ) {
//         for( int y=0; y<300; y++ ) pix[x][y] = (x*y);
//      }
//      ad.setPixels(pix,16);
//      
//      // Calibration 
//      String header = 
//         "SIMPLE  = T\n"+
//         "BITPIX  = 16\n"+
//         "NAXIS   = 2\n"+
//         "NAXIS1  = 200\n"+
//         "NAXIS2  = 300\n"+
//         "CRPIX1  = 100\n"+
//         "CRPIX2  = 150\n"+
//         "CRVAL1  = 83.63310542835717\n"+
//         "CRVAL2  = 22.014486753213667\n"+
//         "CTYPE1  = RA---TAN\n"+
//         "CTYPE2  = DEC--TAN\n"+
//         "CD1_1   = -2.8004558788238224E-4\n"+
//         "CD1_2   = -3.078969511615841E-6\n"+
//         "CD2_1   = -3.078969511615841E-6\n"+
//         "CD2_2   = 2.8004558788238224E-4\n"+
//         "RADECSYS= FK5\n";
//      ad.setFitsHeader(header);
//
//      
//      
//   }

   /** Retourne un tableau de pixels d'origine couvrant la vue courante */
   protected void getCurrentBufPixels(PlanImage pi,RectangleD rcrop, double zoom,double resMult,boolean fullRes) {
      int w = (int)Math.round(rcrop.width*zoom);
      int h = (int)Math.round(rcrop.height*zoom);
      int bitpix= getBitpix()==-64 ? -64 : -32;
      int npix = Math.abs(bitpix)/8;
      byte [] pixelsOrigin = new byte[w*h*npix];
      byte [] onePixelOrigin = new byte[npix];

      double blank = Double.NaN;

      //      boolean flagClosest = maxOrder()*resMult>maxOrder+4;
      boolean flagClosest = false;
      boolean testClosest = false;

      int order = fullRes ? maxOrder : (int)(getOrder()*resMult);
      if( order<3 ) order=3;
      else if( order>maxOrder ) order=maxOrder;


      int offset=0;
      double fct = 100./h;
      Coord coo = new Coord();
      Coord coo1 = new Coord();
      for( int y=h-1; y>=0; y-- ) {
         pi.pourcent+=fct;
         for( int x=0; x<w; x++ ) {
            double val;

            // Point de r�f�rence milieu bord gauche du pixel d'arriv�e
            // Pour trouver au mieux les 4 pixels Healpix recouvrant le pixel d'arriv�e
            double x1 = rcrop.x + (x+0.5)/zoom;
            double y1 = rcrop.y + (y)/zoom;
            coo.x = x1; coo.y = y1;
            pi.projd.getCoord(coo);
            coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);

            // Point central du pixel d'arriv�e
            double x2 = rcrop.x + (x+1)/zoom;
            double y2 = rcrop.y + (y)/zoom;
            coo1.x = x2; coo1.y = y2;
            pi.projd.getCoord(coo1);
            coo1 = Localisation.frameToFrame(coo1,Localisation.ICRS,frameOrigin);

            // Passe en mode Closest si il y a sur�chantillonnage
            if( !testClosest ) {
               testClosest=true;
               double resDest = Coo.distance(coo.al,coo.del,coo1.al,coo1.del)*2;
               double resSrc = getPixelResolution();
               if( resDest<resSrc/2 ) flagClosest=true;
               //               System.out.println("resSrc="+resSrc+" resDst="+resDest+" flagClosest="+flagClosest);
            }

            if( Double.isNaN(coo.al) || Double.isNaN(coo.del) ) val = Double.NaN;
            else if( flagClosest ) val = getHealpixClosestPixel(coo1.al,coo1.del,order);
            else val = getHealpixLinearPixel(coo.al,coo.del,coo1.al,coo1.del,order);
            if( Double.isNaN(val) ) {
               setPixVal(onePixelOrigin, bitpix, 0, blank);
               if( !pi.isBlank ) {
                  pi.isBlank=true;
                  pi.blank=blank;
                  if( bitpix>0 && pi.headerFits!=null) pi.headerFits.setKeyValue("BLANK", blank+"");
               }
            } else {
               val = val*bScale+bZero;
               setPixVal(onePixelOrigin, bitpix, 0, val);
            }

            System.arraycopy(onePixelOrigin, 0, pixelsOrigin, offset, npix);
            offset+=npix;
            if( offset>pixelsOrigin.length ) break;  // Le tableau est plein
         }
      }

      // Ajustement des variables en fonction du changement de bitpix
      pi.bitpix = bitpix;
      pi.pixelsOrigin = pixelsOrigin;
      pi.dataMin = dataMin*bScale+bZero;
      pi.dataMax = dataMax*bScale+bZero;
      pi.pixelMin = pixelMin*bScale+bZero;
      pi.pixelMax = pixelMax*bScale+bZero;
      pi.bScale=1; pi.bZero=0;
      pi.pixels = getPix8Bits(null,pi.pixelsOrigin,pi.bitpix,pi.width,pi.height,pi.pixelMin,pi.pixelMax,false,0,0,0);
      pi.invImageLine(pi.width,pi.height,pi.pixels);
      pi.colorBackground=Color.white;

   }
   
   protected void getCurrentBufPixels(PlanImage pi,RectangleD rcrop, STCObj stcObj, double zoom,double resMult,boolean fullRes) {
          int w = (int)Math.round(rcrop.width*zoom);
          int h = (int)Math.round(rcrop.height*zoom);
          int bitpix= getBitpix()==-64 ? -64 : -32;
          int npix = Math.abs(bitpix)/8;
          byte [] pixelsOrigin = new byte[w*h*npix];
          byte [] onePixelOrigin = new byte[npix];

          double blank = Double.NaN;

          //      boolean flagClosest = maxOrder()*resMult>maxOrder+4;
          boolean flagClosest = false;
          boolean testClosest = false;

          int order = fullRes ? maxOrder : (int)(getOrder()*resMult);
          if( order<3 ) order=3;
          else if( order>maxOrder ) order=maxOrder;


          int offset=0;
          double fct = 100./h;
          Coord coo = new Coord();
          Coord coo1 = new Coord();
            Healpix healPix = new Healpix();
//            HealpixMoc posBounds = null;
            SMoc posBounds = null;
            try {
                if (stcObj != null) {
//                   posBounds = new HealpixMoc( aladin.createMocRegion(stcObj, -1, true) );
                   posBounds = new SMoc( aladin.createMocRegion(stcObj, -1, true) );
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace(); //in this case the bounding box is the outline instead
            }
            
          for( int y=h-1; y>=0; y-- ) {
             pi.pourcent+=fct;
             for( int x=0; x<w; x++ ) {
                double val = Double.NaN;
                
                try {
                    // Point de r�f�rence milieu bord gauche du pixel d'arriv�e
                    // Pour trouver au mieux les 4 pixels Healpix recouvrant le pixel d'arriv�e
                    double x1 = rcrop.x + (x+0.5)/zoom;
                    double y1 = rcrop.y + (y)/zoom;
                    coo.x = x1; coo.y = y1;
                    pi.projd.getCoord(coo);
                    coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
                    
                    // Point central du pixel d'arriv�e
                    double x2 = rcrop.x + (x+1)/zoom;
                    double y2 = rcrop.y + (y)/zoom;
                    coo1.x = x2; coo1.y = y2;
                    pi.projd.getCoord(coo1);
                    coo1 = Localisation.frameToFrame(coo1,Localisation.ICRS,frameOrigin);

                    if (posBounds != null && !posBounds.contains(healPix, coo1.al,coo1.del)) {
                        val = Double.NaN;
                    } else {
                        // Passe en mode Closest si il y a sur�chantillonnage
                        if( !testClosest ) {
                           testClosest=true;
                           double resDest = Coo.distance(coo.al,coo.del,coo1.al,coo1.del)*2;
                           double resSrc = getPixelResolution();
                           if( resDest<resSrc/2 ) flagClosest=true;
                           //               System.out.println("resSrc="+resSrc+" resDst="+resDest+" flagClosest="+flagClosest);
                        }
                        
                        if( Double.isNaN(coo.al) || Double.isNaN(coo.del) ) val = Double.NaN;
                        else if( flagClosest ) val = getHealpixClosestPixel(coo1.al,coo1.del,order);
                        else val = getHealpixLinearPixel(coo.al,coo.del,coo1.al,coo1.del,order);
                        }
                    } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                    }

                if( Double.isNaN(val) ) {
                   setPixVal(onePixelOrigin, bitpix, 0, blank);
                   if( !pi.isBlank ) {
                      pi.isBlank=true;
                      pi.blank=blank;
                      if( bitpix>0 && pi.headerFits!=null) pi.headerFits.setKeyValue("BLANK", blank+"");
                   }
                } else {
                   val = val*bScale+bZero;
                   setPixVal(onePixelOrigin, bitpix, 0, val);
                }

                System.arraycopy(onePixelOrigin, 0, pixelsOrigin, offset, npix);
                offset+=npix;
                if( offset>pixelsOrigin.length ) break;  // Le tableau est plein
             }
          }

          // Ajustement des variables en fonction du changement de bitpix
          pi.bitpix = bitpix;
          pi.pixelsOrigin = pixelsOrigin;
          pi.dataMin = dataMin*bScale+bZero;
          pi.dataMax = dataMax*bScale+bZero;
          pi.pixelMin = pixelMin*bScale+bZero;
          pi.pixelMax = pixelMax*bScale+bZero;
          pi.bScale=1; pi.bZero=0;
          pi.pixels = getPix8Bits(null,pi.pixelsOrigin,pi.bitpix,pi.width,pi.height,pi.pixelMin,pi.pixelMax,false,0,0,0);
          pi.invImageLine(pi.width,pi.height,pi.pixels);
          pi.colorBackground=Color.white;

       }
   

   protected void getCurrentBufPixelsBubbleWrapped(PlanImage pi,RectangleD rcrop, STCObj stcObj, double zoom,double resMult,boolean fullRes) {
      int w = (int)Math.round(rcrop.width*zoom);
      int h = (int)Math.round(rcrop.height*zoom);
      int bitpix= getBitpix()==-64 ? -64 : -32;
      int npix = Math.abs(bitpix)/8;
      
      try {
        aladin.askIMResourceCheck(w*h*npix);
      } catch (Exception e1) {
            // TODO Auto-generated catch block
        Aladin.trace(3, "No sufficient memory");
        aladin.notifyIMStatusChange(IMListener.ERROR);
        return;
      }
      
      SoftReference<byte[]> ref = new SoftReference<>(new byte[w*h*npix]);
      if (ref == null || ref.get() == null) {
          aladin.notifyIMStatusChange(IMListener.LOWMEMORY);
          Aladin.trace(3, "can't process this"); return;
      } else {//if only using SF scheme, consider checking additional memory as well.
//        SoftReference<byte[]> buffer = new SoftReference<byte[]>(new byte[(int) (w*h*npix)]); 
//      if (buffer == null || buffer.get() == null) {
//          aladin.notifyIMStatusChange(IMListener.LOWMEMORY);
//          Aladin.trace(3, "not enough buffer memory"); return;
//      } else if (ref == null || ref.get() == null) {
//          aladin.notifyIMStatusChange(IMListener.LOWMEMORY);
//          Aladin.trace(3, "the byte array is now gone"); return;
//      } else {
//          buffer = null;
            aladin.notifyIMStatusChange(IMListener.PROCESSING);
//      }
      }
//      byte [] pixelsOrigin = new byte[w*h*npix];
//      byte [] pixelsOrigin = ref.get(); ref = null;
      
      byte [] onePixelOrigin = new byte[npix];

      double blank = Double.NaN;

      //      boolean flagClosest = maxOrder()*resMult>maxOrder+4;
      boolean flagClosest = false;
      boolean testClosest = false;

      int order = fullRes ? maxOrder : (int)(getOrder()*resMult);
      if( order<3 ) order=3;
      else if( order>maxOrder ) order=maxOrder;


      int offset=0;
      double fct = 100./h;
      Coord coo = new Coord();
      Coord coo1 = new Coord();
        Healpix healPix = new Healpix();
//        HealpixMoc posBounds = null;
        SMoc posBounds = null;
        try {
            if (stcObj != null) {
//               posBounds = new HealpixMoc( aladin.createMocRegion(stcObj, -1, true) );
               posBounds = new SMoc( aladin.createMocRegion(stcObj, -1, true) );
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace(); //in this case the bounding box is the outline instead
        }
        
      for( int y=h-1; y>=0; y-- ) {
         pi.pourcent+=fct;
         for( int x=0; x<w; x++ ) {
            double val = Double.NaN;
            
            try {
                // Point de r�f�rence milieu bord gauche du pixel d'arriv�e
                // Pour trouver au mieux les 4 pixels Healpix recouvrant le pixel d'arriv�e
                double x1 = rcrop.x + (x+0.5)/zoom;
                double y1 = rcrop.y + (y)/zoom;
                coo.x = x1; coo.y = y1;
                pi.projd.getCoord(coo);
                coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
                
                // Point central du pixel d'arriv�e
                double x2 = rcrop.x + (x+1)/zoom;
                double y2 = rcrop.y + (y)/zoom;
                coo1.x = x2; coo1.y = y2;
                pi.projd.getCoord(coo1);
                coo1 = Localisation.frameToFrame(coo1,Localisation.ICRS,frameOrigin);

                if (posBounds != null && !posBounds.contains(healPix, coo1.al,coo1.del)) {
                    val = Double.NaN;
                } else {
                    // Passe en mode Closest si il y a sur�chantillonnage
                    if( !testClosest ) {
                       testClosest=true;
                       double resDest = Coo.distance(coo.al,coo.del,coo1.al,coo1.del)*2;
                       double resSrc = getPixelResolution();
                       if( resDest<resSrc/2 ) flagClosest=true;
                       //               System.out.println("resSrc="+resSrc+" resDst="+resDest+" flagClosest="+flagClosest);
                    }
                    
                    if( Double.isNaN(coo.al) || Double.isNaN(coo.del) ) val = Double.NaN;
                    else if( flagClosest ) val = getHealpixClosestPixel(coo1.al,coo1.del,order);
                    else val = getHealpixLinearPixel(coo.al,coo.del,coo1.al,coo1.del,order);
                    }
                } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                }

            if( Double.isNaN(val) ) {
               setPixVal(onePixelOrigin, bitpix, 0, blank);
               if( !pi.isBlank ) {
                  pi.isBlank=true;
                  pi.blank=blank;
                  if( bitpix>0 && pi.headerFits!=null) pi.headerFits.setKeyValue("BLANK", blank+"");
               }
            } else {
               val = val*bScale+bZero;
               setPixVal(onePixelOrigin, bitpix, 0, val);
            }

//            System.arraycopy(onePixelOrigin, 0, pixelsOrigin, offset, npix);
            int length = -1;
            if (ref == null || ref.get() == null) { 
                Aladin.trace(3, "ooo im thinking out of memory. free:"+(Runtime.getRuntime().freeMemory())/1E6+" request: "+stcObj);
                return;
            } else {
                length = ref.get().length;
                System.arraycopy(onePixelOrigin, 0, ref.get(), offset, npix);
            }
            
            offset+=npix;
            if( offset>length ) break;  // Le tableau est plein
         }
      }

      // Ajustement des variables en fonction du changement de bitpix
      pi.bitpix = bitpix;
//      pi.pixelsOrigin = pixelsOrigin;
      if (ref == null || ref.get() == null) {
          Aladin.trace(3, "ooo im thinking out of memory. free:"+(Runtime.getRuntime().freeMemory())/1E6+" request: "+stcObj);
            return;
        } else {
            pi.pixelsOrigin = ref.get();
        }
      pi.dataMin = dataMin*bScale+bZero;
      pi.dataMax = dataMax*bScale+bZero;
      pi.pixelMin = pixelMin*bScale+bZero;
      pi.pixelMax = pixelMax*bScale+bZero;
      pi.bScale=1; pi.bZero=0;
      pi.pixels = getPix8Bits(null,pi.pixelsOrigin,pi.bitpix,pi.width,pi.height,pi.pixelMin,pi.pixelMax,false,0,0,0);
      pi.invImageLine(pi.width,pi.height,pi.pixels);
      pi.colorBackground=Color.white;

   }

   boolean first1=false; //Aladin.PROTO;

   /** Retourne un tableau de pixels 8 bits de la zone d�limit�e par le rectangle rcrop (coordonn�es de la vue), ou la vue si null */
   protected byte [] getBufPixels8(ViewSimple v) {
      return getPixels8Area(v,new RectangleD(0,0,v.rv.width,v.rv.height),true);
   }
   protected byte [] getPixels8Area(ViewSimple v,RectangleD rcrop,boolean now) {
      int rgb [] = getPixelsRGBArea(v,rcrop,now);
      if( rgb==null ) return null;
      int taille = rgb.length;
      byte [] pixels = new byte[taille];
      for( int i=0; i<taille; i++ ) {
         if( ((rgb[i] >>> 24) & 0xFF)==0 ) pixels[i]=0;  // transparent
         else {
            int pix = rgb[i] & 0xFF;
            if( pix<255 ) pix++;
            pixels[i] = (byte)(pix & 0xFF);
         }
      }
      rgb=null;
      return pixels;
   }
    //Method repeated just to isolate existing from the new developments.   
   protected byte [] getPixels8Area(ViewSimple v,RectangleD rcrop, STCObj stcObj, boolean now) {
      int rgb [] = getPixelsRGBArea(v,rcrop, stcObj, now);
      if( rgb==null ) return null;
      int taille = rgb.length;
      byte [] pixels = new byte[taille];
      for( int i=0; i<taille; i++ ) {
         if( ((rgb[i] >>> 24) & 0xFF)==0 ) pixels[i]=0;  // transparent
         else {
            int pix = rgb[i] & 0xFF;
            if( pix<255 ) pix++;
            pixels[i] = (byte)(pix & 0xFF);
         }
      }
      rgb=null;
      return pixels;
   }

   /** Retourne un tableau de pixels couleurs de la zone d�limit�e par le rectangle rcrop (coordonn�es de la vue)*/
   protected int [] getPixelsRGBArea(ViewSimple v,RectangleD rcrop,boolean now) {
      if( v==null ) return null;
      BufferedImage imgBuf = new BufferedImage(v.rv.width,v.rv.height,BufferedImage.TYPE_INT_ARGB);
      Graphics g = imgBuf.getGraphics();
      drawLosanges(g, v, now, imgBuf);
      g.finalize(); g=null;

      int width=(int)Math.ceil(rcrop.width);
      int height=(int)Math.ceil(rcrop.height);
      int taille=width*height;
      int rgb[] = new int[taille];

      // En cas de probl�me d'arrondi n�gatif
      int x = (int)Math.floor(rcrop.x);
      if( x<0 ) x=0;
      int y = (int)Math.floor(rcrop.y);
      if( y<0 ) y=0;

      imgBuf.getRGB(x, y, width, height, rgb, 0,width);
      imgBuf.flush(); imgBuf=null;

      return rgb;
   }
   
   protected int [] getPixelsRGBArea(ViewSimple v,RectangleD rcrop, STCObj stcObj, boolean now) {
      if( v==null ) return null;
      BufferedImage imgBuf = new BufferedImage(v.rv.width,v.rv.height,BufferedImage.TYPE_INT_ARGB);
      Graphics g = imgBuf.getGraphics();
      Shape shape = getShape(v, stcObj);
      if (shape != null) {
          g.setClip(shape);
      }
      drawLosanges(g, v, now, imgBuf);
      g.finalize(); 
      if (shape != null) {
          g.setClip(null);
      }
      g=null;
      
      int width = (int)Math.ceil(rcrop.width);
      int height = (int)Math.ceil(rcrop.height);
      int taille = width * height;
      int rgb[] = new int[taille];

      // En cas de probl�me d'arrondi n�gatif
      int x = (int)Math.floor(rcrop.x);
      if( x<0 ) x=0;
      int y = (int)Math.floor(rcrop.y);
      if( y<0 ) y=0;
      
      imgBuf.getRGB(x, y, width, height, rgb, 0,width);
      imgBuf.flush(); imgBuf=null; 

      return rgb;
   }
   
   /**
    * Gets a java.awt.Shape from an STCObj
    * @param v
    * @param stcObj
    * @return
    */
    public Shape getShape(ViewSimple v, STCObj stcObj) {
        Shape shape = null;
        Coord coord = null;
        PointD pt = null;
        if (stcObj instanceof STCCircle) {
            STCCircle circle = (STCCircle) stcObj;
            coord = getCoodSetXY(circle.getCenter().al, circle.getCenter().del);
            double radius = Server.getAngleInArcmin(String.valueOf(circle.getRadius()), Server.RADIUSd) / 60.;
            int plotRadius = (int) Math.round(Fov.getPlotRadiusForCircleFromCoord(projd, coord, radius) * v.getZoom());
            pt = v.getViewCoordDble(coord.x, coord.y);
            shape = new Ellipse2D.Double((int) (pt.x-plotRadius), (int) (pt.y-plotRadius), plotRadius * 2, plotRadius * 2);
        } else if (stcObj instanceof STCPolygon) {
            STCPolygon poly = (STCPolygon) stcObj;
            List<Double> xCorners = poly.getxCorners();
            List<Double> yCorners = poly.getyCorners();
            int xPoly[] = new int[xCorners.size()];
            int yPoly[] = new int[xCorners.size()];
            for (int i = 0; i < xCorners.size(); i++) {
                coord = getCoodSetXY(xCorners.get(i), yCorners.get(i));
                pt = v.getViewCoordDble(coord.x,coord.y);
                xPoly[i] = (int)Math.floor(pt.x);
                if( xPoly[i]<0 ) xPoly[i]=0;
                yPoly[i] = (int)Math.floor(pt.y);
                if( yPoly[i]<0 ) yPoly[i]=0;
            }
            shape = new Polygon(xPoly, yPoly, xPoly.length);
        }
        return shape;
    }
    
    public Coord getCoodSetXY(double ra, double dec) {
        Coord coord = new Coord(ra, dec);
        coord = Localisation.frameToFrame(coord,Localisation.ICRS,frameOrigin);
        coord = projd.getXY(coord);
        return coord;
    }

   /** Return une Image (au sens Java). M�morise cette image pour �viter de la reconstruire
    * si ce n'est pas n�cessaire
    * @param now true s'il faut imm�diatement fournir une image compl�te � la r�solution ad�quate
    */
   @Override
   protected Image getImage(ViewSimple v,boolean now) {
      if( now ) {
         BufferedImage img = new BufferedImage(v.rv.width,v.rv.height, BufferedImage.TYPE_INT_ARGB_PRE);
         Graphics g = img.getGraphics();
//         v.drawBackground(g);    // impossible si NOGUI (JE NE COMPRENDS PAS PF - juin 2022)
         drawLosanges(g,v,now, img);
         adjustCM( img );
         g.dispose();
         return img;
      }

      // Pas de modif depuis la derni�re fois, je redonne l'image pr�c�dente
      if( v.imageBG!=null && v.ovizBG == v.iz
            && v.oImgIDBG==imgID && v.rv.width==v.owidthBG && v.rv.height==v.oheightBG ) {
         return v.imageBG;
      }

      // Dois-je cr�er une nouvelle image ?
      if( v.imageBG==null || v.rv.width!=v.owidthBG || v.rv.height!=v.oheightBG ) {
         if( v.imageBG!=null ) v.imageBG.flush();
         if( v.g2BG!=null ) v.g2BG.dispose();
         v.imageBG = new BufferedImage(v.rv.width,v.rv.height, BufferedImage.TYPE_INT_ARGB_PRE);
         v.g2BG = v.imageBG.getGraphics();

         // ou simplement remettre de la transparence ?
      } else {
         ((Graphics2D)v.g2BG).setComposite(AlphaComposite.Clear);
         v.g2BG.fillRect(0, 0, v.rv.width, v.rv.height);
         ((Graphics2D)v.g2BG).setComposite(AlphaComposite.Src);
      }

      v.oImgIDBG=imgID;
      v.owidthBG=v.rv.width;
      v.oheightBG=v.rv.height;
      v.ovizBG=v.iz;
      flagClearBuf=false;

      //         if( v.pref==this ) drawBackground(v.g2BG,v);
      if( !isTransparent() ) drawBackground(v.g2BG,v);

      // Je trace les losanges
      drawLosanges(v.g2BG,v,now,v.imageBG);

      // Ajustement de la table des couleurs
      try {
         adjustCM( v.imageBG );
      } catch( Exception e ) { }

      // Pour pouvoir d�tecter s'il y a un objet sous la souris
      //      v.pixelsRGB = ((DataBufferInt)v.imageBG.getRaster().getDataBuffer()).getData();

      try {
         v.w=v.imageBG.getWidth();
         v.h=v.imageBG.getHeight();
      } catch( Exception e ) { }

      return v.imageBG;
   }


   private int lastHistID=-1;   // ID associ� au dernier histogramme

   private void adjustCM( BufferedImage img) {
      if( !isColored() ) return;

      int width=img.getWidth();
      int height=img.getHeight();
      boolean modif= (RGBControl[0]!=0 || RGBControl[1]!=128 || RGBControl[2]!=255
            || RGBControl[3]!=0 || RGBControl[4]!=128 || RGBControl[5]!=255
            || RGBControl[6]!=0 || RGBControl[7]!=128 || RGBControl[8]!=255
            || video!=VIDEO_NORMAL);

      int size = width*height;
      boolean flagInitRGB = lastHistID!=imgID;
      if( flagInitRGB ) {
         lastHistID=imgID;
         if( red==null || size!=red.length ) {
            red = new byte[size];
            green = new byte[size];
            blue = new byte[size];
         }
      }

      if( !flagInitRGB && !modif ) return;

      int a,r,g,b ;
      int [] pixelsRGB = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

      for( int pos=0; pos<pixelsRGB.length ; pos++ ) {

         int rgb = pixelsRGB[pos];
         a = (rgb & 0xFF000000) >> 24 ; r = (rgb & 0x00FF0000) >> 16 ; g = (rgb & 0x0000FF00) >> 8 ; b = (rgb & 0x000000FF);

         if( flagInitRGB ) {
            red[pos]   = (byte)(0xFF & r);
            green[pos] = (byte)(0xFF & g);
            blue[pos]  = (byte)(0xFF & b);
         }

         if( modif ) {
            r = PlanImageRGB.filter(RGBControl[0],RGBControl[1],RGBControl[2],r&0xFF);
            g = PlanImageRGB.filter(RGBControl[3],RGBControl[4],RGBControl[5],g&0xFF);
            b = PlanImageRGB.filter(RGBControl[6],RGBControl[7],RGBControl[8],b&0xFF);

            if( video==VIDEO_INVERSE ) { r=~r; g=~g; b=~b; }

            pixelsRGB[pos] = ((a&0xFF)<<24) | ((r&0xFF)<<16) | ((g&0xFF)<<8) | (b&0xFF);
         }
      }
      if( modif ) img.setRGB(0, 0, width, height, pixelsRGB, 0, width);
      if( flagInitRGB ) {
         resetHist();
         //         System.out.println("adjustCM/resetHist histID="+histID+" lastHistID="+lastHistID);
         if( aladin.frameCM!=null ) aladin.frameCM.repaint();
      }
   }

   protected void filterRGB(int [] triangle, int color) {
      changeImgID();
      RGBControl[color*3]   = triangle[0];
      RGBControl[color*3+1] = triangle[1];
      RGBControl[color*3+2] = triangle[2];
   }

   private double histRed[]  = new double[256];
   private double histGreen[]= new double[256];
   private double histBlue[] = new double[256];

   /** Retourne le tableau de l'histogramme avant initialisation */
   protected double [] getHistArray(int rgb) {
      return rgb==0 ? histRed : rgb==1 ? histGreen : histBlue;
   }

   /** Retourne le tableau des pixels servants � l'histogramme */
   protected byte [] getPixelHist(int rgb) {
      return rgb== -1 ? super.getPixelHist(rgb)
            : rgb==0 ? red : rgb==1 ? green : blue;
   }

   protected byte [] red;        // Tableau des pixels de l'image Rouge
   protected byte [] green;        // Tableau des pixels de l'image Verte
   protected byte [] blue;        // Tableau des pixels de l'image Bleue

   private Timer timer = null;
   synchronized protected void redrawAsap() {
      if( timer!=null ) return;
//      System.out.println("starting fading process");
      timer = new Timer();
      TimerTask timerTask = new TimerTask() {
         public void run() {
//            System.out.println("redrawAsap now");
            changeImgID();
            aladin.view.repaintAll();
         }
      };
      timer.scheduleAtFixedRate(timerTask, 5, 5);
   }

   synchronized protected void stopRedraw() {
      if( timer==null ) return;
      System.out.println("stopping fading process");
      timer.cancel();
      timer=null;
      
      op=0f;
      
//      changeImgID();
//      aladin.view.repaintAll();
   }

   /** Tracage de tous les losanges concern�s, utilisation d'un cache (voir getImage())
    * @param op niveau d'opacit�, -1 pour celui d�finit dans le plan
    * @param now true si l'afficahge doit �tre imm�diatement complet � la r�solution ad�quate
    */
   protected void draw(Graphics g,ViewSimple v, int dx, int dy,float op) {
      draw(g,v,dx,dy,-1,false);
   }
   protected void draw(Graphics g,ViewSimple v, int dx, int dy,float op,boolean now) {
      if( v==null ) return;
      if( op==-1 ) op=getOpacityLevel();
      if(  op<=0.1 ) return;

      resetFading();
      
      if( g instanceof Graphics2D ) {
         Graphics2D g2d = (Graphics2D)g;
         Composite saveComposite = g2d.getComposite();
         try {
            if( op < 0.9 ) {
               Composite myComposite = Util.getImageComposite(op);
               g2d.setComposite(myComposite);
            }
            if( drawMode==DRAWPIXEL ) g2d.drawImage(getImage(v,now), dx, dy, aladin);
            else if( drawMode==DRAWPOLARISATION ) drawPolarisation(g2d, v);

         } catch( Exception e ) { if( aladin.levelTrace>=3 ) e.printStackTrace(); }
         g2d.setComposite(saveComposite);

      } else {
         if( drawMode==DRAWPIXEL ) g.drawImage(getImage(v,now),dx,dy,aladin);
         else if( drawMode==DRAWPOLARISATION ) drawPolarisation(g, v);
      }

      setHasMoreDetails( maxOrder(v)<maxOrder );

      if( isFading() ) redrawAsap();
      else stopRedraw();

      readyDone = readyAfterDraw;
   }

   boolean readyAfterDraw=false;
   boolean readyDone=false;

   /** Trace le losange HEALPix sous la position de la souris */
   protected void drawHealpixMouse(Graphics g,ViewSimple v) {
      try {
         Coord coo = new Coord(aladin.localisation.getLastCoord().al,aladin.localisation.getLastCoord().del);
         coo = Localisation.frameToFrame(coo,Localisation.ICRS,frameOrigin);
         int norder = aladin.getOrder();
         if( norder<0 ) return;
         double polar[] = CDSHealpix.radecToPolar(new double[]{coo.al,coo.del});
//         long nside = CDSHealpix.pow2(norder);
         long npix = CDSHealpix.ang2pix_nest(norder, polar[0], polar[1]);
         HealpixKey hk = new HealpixKey(this,norder,npix,HealpixKey.NOLOAD);
         hk.drawCtrl(g, v);
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

   protected int priority=0;
   protected void resetPriority() {
      Enumeration<HealpixKey> e = pixList.elements();
      while( e.hasMoreElements() ) {
         HealpixKey healpix = e.nextElement();
         if( healpix.npix!=-1 && healpix.priority<1000 ) healpix.priority+=1000;
      }
      priority=0;
   }

   //   private void compareList(ViewSimple v,int order,long []p,long [] c) {
   //      long [] c1 = new long[c.length];
   //      System.arraycopy(c, 0, c1, 0, c.length);
   //      StringBuffer pres = new StringBuffer();
   //      StringBuffer cres = new StringBuffer();
   //      int pn=0,cn=0;
   //      for( int i=0; i<p.length; i++ ) {
   //         boolean out =   (new HealpixKey(this, order, p[i], true)).isOutView(v);
   //         boolean trouve = false;
   //         for( int j=0; j<c1.length; j++ ) {
   //            if( c1[j]==p[i] ) {
   //               c1[j]=-1;
   //               trouve=true;
   //               break;
   //            }
   //         }
   //         if( !trouve ) { pres.append(" "+(out?"["+p[i]+"]":p[i])); pn++; }
   //      }
   //      for( int i=0; i<c1.length; i++ ) {
   //         if( c1[i]!=-1 ) {
   //            boolean out =   (new HealpixKey(this, order, c1[i], true)).isOutView(v);
   //            cres.append(" "+(out?"["+c1[i]+"]":"*"+c1[i]+"*")); cn++;
   //      }
   //      }
   //
   //      if( pn>0 ) System.out.println("diff Polygon ("+pn+"): "+pres);
   //      if( cn>0 ) System.out.println("diff Circle  ("+cn+"): "+cres);
   //
   //   }

   /** Trac� des segments de polarisation disponibles dans la vue et demande de ceux manquants */
   protected void drawPolarisation(Graphics g,ViewSimple v) {
      long [] pix=null;
      int max = Math.min(maxOrder(v),maxOrder);
      int nb=0;
      if( v.getTaille()>20 && (hasDrawnSomething=drawAllSky(g, v, null))) return;

      setMem();
      resetPriority();

      int order=Math.max(3,max);
      pix = getPixListView(v,order);

      for( int i=0; i<pix.length; i++ ) {

         if( (new HealpixKey(this,order,pix[i],HealpixKey.NOLOAD)).isOutView(v) ) continue;

         HealpixKey healpix = getHealpix(order,pix[i], true);

         // Inconnu => on ne dessine pas
         if( healpix==null ) continue;

         // Positionnement de la priorit� d'affichage
         healpix.priority=250-(priority++);

         int status = healpix.getStatus();

         // Losange erron� ?
         if( status==HealpixKey.ERROR ) continue;

         // On change d'avis
         if( status==HealpixKey.ABORTING ) healpix.setStatus(HealpixKey.ASKING,true);

         // Losange � g�rer
         healpix.resetTimer();

         // Pas encore pr�t
         if( status!=HealpixKey.READY ) continue;

         nb+=((HealpixKeyPol)healpix).drawPolarisation(g,v);
      }

      hasDrawnSomething=nb>0;

      tryWakeUp();
   }



   static protected int nDraw1=0;
   static protected int nOut1=0;

   //   /** Trac� des losanges disposibles pour une image */
   //   protected void drawLosangesInImage(Graphics g,ViewSimple v) {
   //      long [] pix=null;
   //      int min = Math.max(3,minOrder);
   //      int max = Math.min(maxOrder(v),maxOrder);
   //
   //      drawBackground(g, v);
   //      drawAllSky(g,v);
   //      redraw.clear();
   //
   //      for( int order=min; order<=max; order++ ) {
   //         pix = getPixListView(v,order);
   //         for( int i=0; i<pix.length; i++ ) {
   //            if( (new HealpixKey(this,order,pix[i],true)).isOutView(v) ) { pix[i]=-1; continue; }
   //            HealpixKey healpix = getHealpix(order,pix[i], false);
   //            if( healpix==null ) continue;
   //            int status = healpix.getStatus();
   //            if( status==HealpixKey.READY ) {
   //               healpix.draw(g,v);
   //            }
   //         }
   //      }
   //      redraw(g,v);
   //   }


   static final int DRAWFASTMS = 100;     // Tps max autoris� pour un trac� complet, si > mode rapide
   private boolean computeDrawFast=true;  // true autorise une nouvelle �valuation du temps de trac�
   private boolean lastMustDrawFast=true; // Derni�re mesure du mustDrawFast

   /** Retourne true s'il faut afficher l'image le plus vite possible
    * Dans le cas o� c'est effectivement le cas, ce mode restera activ� jusqu'�
    * l'appel � resetDrawFastDetection() appel� notamment par le mouseRelease() de ViewSimple
    * (ce qui indique la fin d'un clic and drag)
    * @return
    */
   protected boolean mustDrawFast() {
      if( !computeDrawFast ) return lastMustDrawFast;
      boolean rep = aladin.view.mustDrawFast();
      lastMustDrawFast = !rep ? false : statTimeDisplay>DRAWFASTMS;
      if( lastMustDrawFast ) computeDrawFast=false;
      return lastMustDrawFast;
   }

   /** Autorise � nouveau la mesure du DrawFast (voir ViewSimple.mouseRelease()) */
   protected void resetDrawFastDetection() { computeDrawFast=true; }


   protected void drawHealpixGrid(Graphics g, ViewSimple v) {
      int order = Math.max(getMinOrder(), Math.min(maxOrder(v),maxOrder) );
      long [] pix;
      if( v.isAllSky() ) {
         pix = new long[12*(int)CDSHealpix.pow2(order)*(int)CDSHealpix.pow2(order)];
         for( int i=0; i<pix.length; i++ ) pix[i]=i;
      } else pix = getPixList(v,getCooCentre(v),order); // via query_disc()

      for( int i=0; i<pix.length; i++ ) {
         HealpixKey healpix = new HealpixKey(this,order,pix[i],HealpixKey.NOLOAD);
         if( isOutMoc(order,pix[i]) || healpix.isOutView(v) ) continue;
         //         healpix.drawRealBorders(g, v);
         try {
            healpix.drawLosangeBorder(g, v, null,null,null );
         } catch( Exception e ) {
            e.printStackTrace();
         }
      }
   }
   


   /** Trac� des losanges � la r�solution ad�quate dans la vue
    * mais en mode synchrone */
   protected void drawLosangesNow(Graphics g,ViewSimple v,BufferedImage imgb) {
      int order = Math.max(getMinOrder(), Math.min(maxOrder(v),maxOrder) );
      boolean lowResolution = v.isAllSky() && order==getMinOrder();

      HealpixKey allsky = pixList.get( key(getMinOrder(),  -1) );

      if( lowResolution ) {
         if( allsky==null ) {
            allsky =  new HealpixAllsky(this,getMinOrder());
            try { allsky.loadNow();
            } catch( Exception e ) { e.printStackTrace(); }
         }
      }

      long [] pix;
      if( v.isAllSky() ) {
         pix = new long[12*(int)CDSHealpix.pow2(order)*(int)CDSHealpix.pow2(order)];
         for( int i=0; i<pix.length; i++ ) pix[i]=i;
      } else pix = getPixList(v,getCooCentre(v),order); // via query_disc()

      for( int i=0; i<pix.length; i++ ) {
         if( isOutMoc(order, pix[i]) ) continue;
         if( (new HealpixKey(this,order,pix[i],HealpixKey.NOLOAD)).isOutView(v) ) continue;
         HealpixKey healpix;
         if( lowResolution && allsky!=null ) healpix = (allsky.getPixList())[i];
         else {
            healpix = getHealpix(order,pix[i], true);
            try { healpix.loadNow(); }
            catch( Exception e ) { e.printStackTrace(); continue; }
         }
         if( healpix.getStatus()==HealpixKey.READY )  {
            healpix.resetTimer();
            healpix.draw(g,v/*,localRedraw*/);
         }
      }
      
      if( Aladin.TESTV12 )  {
         try {
            drawHoles(imgb, v, allsky.getPixList());
         } catch( Exception e ) {
            if( aladin.levelTrace>= 3 ) e.printStackTrace();
         }
      }
   }

   // le synchronized permet d'�viter que 2 draw simultan�s s'entrem�lent (sur un crop par exemple)
   synchronized protected void drawLosanges(Graphics g,ViewSimple v, boolean now,BufferedImage imgb) {
      if( now ) drawLosangesNow(g,v,imgb);
      else drawLosangesAsync(g,v,imgb);
   }

   /** Retourne true si on est s�r que ce losange est en dehors du
    * MOC associ� au HIPS */
   protected boolean isOutMoc(int order,long npix) {
      if( moc==null ) return false; // pas de MOC charg�, je ne sais pas !
      
      // Attention, le Hips et le MOC n'ont pas le m�me syst�me de coord
      char a = moc.getSpaceSys().charAt(0);
      int frameMoc = a=='G' ? Localisation.GAL : a=='E' ? Localisation.ECLIPTIC : Localisation.ICRS;
      if( frameOrigin!=frameMoc )  return false;
      boolean res = !moc.isIntersecting(order, npix);
      return res;
   }
   
// ESSAI POUR ALGO DE TRACE PLUS SIMPLE - 17/7/2002 PF
// => Pour le moment, plus simple, mais fonctionne moins bien
//
//   protected void drawLosangesAsync(Graphics g,ViewSimple v,BufferedImage imgb) {
//      long t1 = Util.getTime(0);
//      int minOrder = getMinOrder();
//      int maxOrder = Math.max( minOrder, Math.min(maxOrder(v),this.maxOrder) );
////      boolean lowResolution = v.isAllSky() && maxOrder==getMinOrder();
//      
//      // Les num�ros de pixels concern�s
//      ArrayList<Long> pix = new ArrayList<>();
//      for( long npix: getPixList(v,getCooCentre(v),maxOrder) ) pix.add( npix );
//      
//      // Les losanges � tracer
//      ArrayList<HealpixKey> listKey = new ArrayList<>(50);
//      
//      // Je dois parcouvrir tous les losanges � l'ordre max.
//      // Si le losange est d�j� pr�t il sera plac� � tracer
//      // sinon, on va chercher le premier anc�tre disponible et le trac� en amont
//      //   (il faudra v�rifier qu'il n'est pas d�j� pris)
//      //   et si pas d'anc�tre, on cherchera dans le allsky
//      System.out.println("\ndrawAsync:");
//      int order;
//      for( order = maxOrder; pix.size()>0 && order>=minOrder; order-- ) {
//         System.out.print("\n.order="+order );
//         ArrayList<Long> anc = new ArrayList<>();
//         for( long npix : pix ) {
//            if( order==maxOrder && isOutMoc(order, npix) ) continue;
//            System.out.print(" "+npix);
//            boolean flagLoad = order==maxOrder;
//            HealpixKey hk = getHealpix(order,npix, flagLoad);
//            if( hk!=null && hk.getStatus()==HealpixKey.READY ) {
//               hk.resetTimer();
//               listKey.add(hk);
//            } else {
//               long a = npix/4;
//               if( !anc.contains(a) ) anc.add(a);
//            }
//         }
//         pix=anc;
//      }
//      
//      // S'il reste des trous, il faut voir si on peut les combler par le allsky
//      HealpixKey allsky = pixList.get( key(minOrder,  -1) );
//      if( allsky==null ) {
//         allsky =  new HealpixAllsky(this,minOrder);
//         flagWaitAllSky=false;
//         try { allsky.loadNow();
//         } catch( Exception e ) { e.printStackTrace(); }
//      }
//      
//      if( pix.size()>0 ) {
//         System.out.print("\n.allsky"+minOrder+ " order="+order);
//         for( long npix : pix ) {
//            int i = (int)( order<minOrder ? npix<< ((minOrder-order)*2) : npix>>> ((order-minOrder)*2));
//            System.out.print(" "+i);
//            HealpixKey hk = (allsky.getPixList())[i];
//            if( hk!=null ) listKey.add(hk);
//         }
//      }
//      
//      // et puis je les dessine dans l'ordre inverse
//      System.out.println("\n.draw");
//      for( int i=listKey.size()-1; i>=0; i-- ) {
//         HealpixKey hk = listKey.get(i);
//         System.out.print(" "+hk.order+"/"+hk.npix);
//         hk.draw(g, v);
//      }
//      System.out.println();
//      
//      if( Aladin.TESTV12 ) drawHoles(imgb, v, allsky.getPixList());
//      
//      hasDrawnSomething= listKey.size()>0;
//      tryWakeUp();
//
//      // Vitesse de trac� - sur les MAXSTAT derniers trac�
//      long t2 = Util.getTime(0);
//      statTimeDisplayArray[nStat++] = (t2-t1)/1000000L;
//      if( nStat==statTimeDisplayArray.length ) nStat=0;
//      long totalStatTime=0;
//      int nbStat=0;
//      for( int i=0; i<statTimeDisplayArray.length; i++ ) {
//         if( statTimeDisplayArray[i]==0 ) continue;
//         totalStatTime+=statTimeDisplayArray[i];
//         nbStat++;
//      }
//      statTimeDisplay = nbStat>0 ? totalStatTime/nbStat : -1;
//      statNbItems = listKey.size();
//   }

   /** Trac� des losanges disposibles dans la vue et demande de ceux manquants */
   protected void drawLosangesAsync(Graphics g,ViewSimple v,BufferedImage imgb) {
      allWaitingKeysDrawn = false;

      boolean first=true;
      long t1 = Util.getTime(0);
      int nb=0;
      long [] pix=null;
      int z = (int)getZ(v);
      int min = Math.max(getMinOrder(),minOrder);
      int max = Math.min(maxOrder(v),maxOrder);
      boolean allKeyReady = false;
      boolean oneKeyReady = false;
      boolean allskyDrawn=false;           // true si on a d�j� essay� de tracer un allsky
//      StringBuilder debug=new StringBuilder(" ");
      HealpixKey allsky = isDynHiPS() ? null : pixList.get( key(getMinOrder(),  -1, z) );

//      if( z>0 ) debug.append(" z="+z);
      
      // EN ATTENDANT QUE LE SERVEUR DE THOMAS FASSE DES ORDRES PLUS FAIBLES
      if( isDynHiPS() ) min=3;

      // On dessine le ciel entier � basse r�solution
      if( min<getMinOrder() ) {
         if( drawAllSky(g,v,imgb) ) {
            nb++;
//            debug.append(" allsky1");
         }
         allskyDrawn=true;
      } else {
         
         // On acc�l�re pendant un clic-and-drag
         boolean fast = mustDrawFast();
         if( fast ) min=max;

         // Position centrale
         double theta=0,phi=0;
         Coord center = getCooCentre(v);
         if( center!=null ) {
            theta = Math.PI/2 - Math.toRadians(center.del);
            phi = Math.toRadians(center.al);
         }

         // Recherche des losanges qui couvrent la vue � la r�solution max
         // uniquement si on est au-dela de l'order 3 (sauf si c'est le dernier)
         allKeyReady=true;
         if( max<getMinOrder() ) allKeyReady=false;
         else {
            pix = getPixList(v,center,max);
            for( int i=0; i<pix.length; i++ ) {
               HealpixKey healpix = getHealpix(max,pix[i],z, false);
               if( healpix==null ) {
                  if( isOutMoc(max,pix[i]) ||
                        (new HealpixKey(this,max,pix[i],HealpixKey.NOLOAD)).isOutView(v) ) {
                     pix[i]=-1; continue;
                  } else { allKeyReady=false;  break; }
               } else {
                  if( isOutMoc(max,pix[i] ) || healpix.isOutView(v) ) { pix[i]=-1; continue; }
                  int status = healpix.getStatus();
                  if( status!=HealpixKey.READY
                        && status!=HealpixKey.ERROR ) { allKeyReady=false; break; }
                  else {
                     healpix.resetTimer();
                     oneKeyReady=true;
                  }
               }
            }

            //            // Dans le cas d'un cube, peut �tre ai-je d�j� tous les losanges d'� cot�
            //            if( !allKeyReady && isCube() ) {
            //               allKeyReady=true;
            //               for( int i=0; i<pix.length; i++ ) {
            //                  if( getHealpixPreviousFrame(max, pix[i])==null ) {
            //                     allKeyReady=false;
            //                     break;
            //                  };
            //               }
            //               if( allKeyReady ) debug.append(" allPreviousKeyReady");
            //            }
            //
            //            if( !allKeyReady ) pix=null;
         }

         if( nb==0 && max<=getMinOrder()  && (!allKeyReady  || (!oneKeyReady && allsky!=null)) ) {
            if( drawAllSky(g,v,imgb) ) {
               nb++;
//               debug.append(" allsky2");
            }
            allskyDrawn=true;
         }

         resetPriority();
         //         redraw.clear();
         HealpixKey healpix = null;
         int cmin = min<max && allKeyReady ? max : min<max-3 ? max-3 : min;
         
         // Petite acc�l�ration
         if( aladin.isAnimated() ) {
            if( cmin<max-1) { cmin=max-1; oneKeyReady=true; }
         }
         
         BufferedImage imgPost=null;
         Graphics gImgPost=null;
         HealpixKey oanc=null;
         int n1=0;

         if( max>=getMinOrder() )
            for( int order=cmin; order<=max || !oneKeyReady && order<=max+2 && order<=maxOrder; order++ ) {

               //               if( !allKeyReady ) {
               pix = getPixList(v,center,order);
               if( pix.length==0 ) break;

               nDraw1+=pix.length;

               // On place le losange central en premier dans la liste
               try {
                  if( center!=null ) {
                     long firstPix = CDSHealpix.ang2pix_nest(order,theta, phi);

                     // Permutation en d�but de liste
                     for( int i=0; i<pix.length; i++ ) {
                        if( pix[i]==firstPix ) {
                           long a = pix[0]; pix[0] = pix[i]; pix[i]=a;
                           break;
                        }
                     }
                  }
               } catch( Exception e ) { }
               //               }

               //               boolean debugOrder=false; // passe � true si on a dessin� au-moins un losange de cet ordre
               for( int i=0; i<pix.length; i++ ) {
                  if( pix[i]==-1 ) continue;
                  healpix = getHealpix(order,pix[i],z, false);
                  HealpixKey testIn = healpix!=null ? healpix : new HealpixKey(this,order,pix[i],HealpixKey.NOLOAD);

                  if( !allKeyReady && (isOutMoc(order,pix[i]) || testIn.isOutView(v)) ) {
                     nOut1++;
                     pix[i]=-1;
                     continue;
                  }

                  if( healpix==null && order<=max ) healpix = getHealpix(order,pix[i],z, true );

                  // Inconnu => on ne dessine pas
                  if( healpix==null ) {

                     // Si c'est un cube et qu'on dispose du losange de la tranche d'�-cot�, on affiche cette derniere en attendant
                     // plut�t que d'afficher une r�solution diff�rente
                     //                     HealpixKey h = getHealpixPreviousFrame(order,pix[i]);
                     //                     if( h!=null ) nb+=h.draw(g,v);

                     continue;
                  }

                  // Juste pour tester la synchro
                  //            Util.pause(100);

                  // Positionnement de la priorit� d'affichage
                  healpix.priority=order<max ? 500-(priority++) : priority++;

                  int status = healpix.getStatus();

                  // Losange erron� ?
                  if( status==HealpixKey.ERROR ) continue;

                  // On change d'avis
                  if( status==HealpixKey.ABORTING ) healpix.setStatus(HealpixKey.ASKING,true);

                  // Losange � g�rer
                  healpix.resetTimer();

                  // Pas encore pr�t
                  if( status!=HealpixKey.READY ) {

                     // Si c'est un cube et qu'on dispose du losange de la tranche d'�-cot�, on affiche cette derniere en attendant
                     // plut�t que d'afficher une r�solution diff�rente
                     HealpixKey h = getHealpixPreviousFrame(order,pix[i]);
                     if( h!=null ) nb+=h.draw(g,v);

                     continue;
                  }

                  // EN FAIT CA N'ARRIVE QUASI JAMAIS - JE LAISSE TOMBER
                  // Tous les fils, ou petits-fils � tracer sont d�j� pr�ts => on passe
                  //                  if( order<max && childrenReady(healpix,v,max) ) {
                  ////                     healpix.filsFree();  POURQUOI DIABLE AI-JE FAIT CELA ???
                  //                     continue;
                  //                  }

                  Graphics g1 = g;

                  if( Aladin.TESTFADING ) {
                     
                     // Si le losange vient d'arriver, on va appliquer un fading
                     float op1 = healpix.getFadingOpacity();
                     if( op1<1f && op<1f ) {
                        
                        // On va appliquer le fading sur un anc�tre d�j� disponible
                        HealpixKey anc = null;
                        for( int o=healpix.order-1,n=(int)healpix.npix/4; o>=3 && anc==null; o--, n/=4) anc = getHealpix(o, n, false);
//                        anc = getHealpix(healpix.order-1, healpix.npix/4, false);
                        if( anc!=null && anc!=oanc) {
                           
                           // trac� de l'anc�tre pr�sent dans le buffer principal
                           n1++;
                           anc.resetTimer();
                           anc.draw(g, v);
//                           System.out.println("Drawing ancetre "+anc.order+"/"+anc.npix);
                           oanc=anc;

                           // Pr�paration d'un buffer image (voir ci-dessous)
                           if( imgPost==null ) {
                              imgPost = new BufferedImage(v.rv.width,v.rv.height, BufferedImage.TYPE_INT_ARGB_PRE);
                              gImgPost = imgPost.getGraphics();
                           }

                           // le losange qui vient d'arriver va �tre trac� dans une buffer image sp�cifique 
                           // qui sera trac� avec le niveau de transparence max des losanges
                           // en cours de chargement (pour �viter le clignotement)
                           if( op1>op ) op=op1;
                           g1=gImgPost;
                        }
                     }
                  }

                  nb+=healpix.draw(g1,v);
                  setHealpixPreviousFrame(order,pix[i]);

                  if( first && !isColored() ) {
                     first=false;
                     pixels=healpix.pixels;
                     pixelsOrigin = healpix.pixelsOrigin;
                     //                     System.out.println("drawLosange memo Pixels4Hist healpix="+healpix);
                     resetHist();
                  }

                  //                  if( !debugOrder ) { debug.append(" "+order); debugOrder=true; }
               }
            }

         // Trac� de du buffer image des tuiles en fading
         if( imgPost!=null ) {
            System.err.println("imgPost drawn n1="+n1+" op="+op);
            Graphics2D g2 = (Graphics2D) g;
            Composite saveComposite = null;
            try {
               saveComposite = g2.getComposite();
               g2.setComposite( AlphaComposite.getInstance(AlphaComposite.SRC_OVER,op) );
               g2.drawImage(imgPost, 0, 0, null);
               
               // demande mise � jour du Fading
               updateFading(true);
            } finally {
               if( saveComposite!=null ) g2.setComposite( saveComposite );
            }
         }

         //essai
         allWaitingKeysDrawn = allKeyReady || (max<=getMinOrder() && hasDrawnSomething);

      }

      // On a rien dessin�, on met tout de m�me le allsky du fond
      if( isPause() && nb==0 && !allskyDrawn && allsky!=null && allsky.getStatus()==HealpixKey.READY
            && drawAllSky(g, v,imgb) ) {
         nb++;
//         debug.append(" allsky3");
      }

      hasDrawnSomething= nb>0;

      tryWakeUp();

      // Vitesse de trac� - sur les MAXSTAT derniers trac�
      long t2 = Util.getTime(0);
      long statTime = statTimeDisplayArray[nStat++] = (t2-t1)/1000000L;
      if( nStat==statTimeDisplayArray.length ) nStat=0;
      long totalStatTime=0;
      int nbStat=0;
      for( int i=0; i<statTimeDisplayArray.length; i++ ) {
         if( statTimeDisplayArray[i]==0 ) continue;
         totalStatTime+=statTimeDisplayArray[i];
         nbStat++;
      }
      statTimeDisplay = nbStat>0 ? totalStatTime/nbStat : -1;
      statNbItems = nb/*+nb1*/;
//            aladin.trace(4,"Draw"+debug+(aladin.isAnimated()?" anim.":"")+" in "+statTime+"ms");
   }

   /** Ne marche que pour les cubes */
   protected HealpixKey getHealpixPreviousFrame(int order, long npix) { return null; }
   protected void setHealpixPreviousFrame(int order,long npix) { }

   static final private int MAXSTAT=5;
   private int nStat=0;
   private long[] statTimeDisplayArray = new long[MAXSTAT];

   /** Retourne le Fps du dernier trac� des losanges */
   protected double getFps() { return mustDrawFast() ? 0 : statTimeDisplay>0 ? 1000./statTimeDisplay : -1 ; }

   private boolean first=true;

   private boolean fading=false;
   private float op=0f;
   synchronized private boolean isFading() {return fading; }
   synchronized protected void resetFading() { fading=false; }
   synchronized protected void updateFading(boolean flag) {
      fading |= flag;
   }

   /** Demande de r�affichage des vues */
   protected void askForRepaint() {
      changeImgID();
      if( aladin.view==null ) return;
      if( first ) {
         first=false;
         aladin.view.setRepere(this);
      }
      aladin.view.repaintAll();
   }

   //   protected int x=0,y=0,rayon=0,grandAxe=0;
   //   protected double angle=0;
   static final int M = 2; //4;
   //   static final int EP =4; //12;
   
   protected void drawForeground(Graphics gv, ViewSimple v) {
      drawForeground1(gv,v);
      
      if( flagWaitAllSky && drawMode==DRAWPIXEL && getMinOrder()==3 ) {
         gv.setColor(Color.red);
         gv.drawString("Whole sky in progress...", 5,30);
      }
   }

   /** Trac� d'un bord le long de projection pour att�nuer le ph�nom�ne de "feston" */
   private void drawForeground1(Graphics gv,ViewSimple v) {
      v = v.getProjSyncView();

      if( aladin.calque.hasHpxGrid() || isOverlay() ) {
         if( aladin.calque.hasHpxGrid() ) drawHealpixGrid(gv, v);
         return;
      }

      Graphics2D g = (Graphics2D)gv;
      int x=0,y=0,rayon=0,grandAxe=0;
      double angle=0;

      if( v.getTaille()<15 ) return;
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      
      
      Color bckCol = v.getCouleurFond();
      g.setColor( bckCol );
//      g.setColor( Color.yellow );
      
//      g.setColor( Aladin.COLOR_BACKGROUND );
      Stroke st = g.getStroke();

      int epaisseur = projd.t==Calib.CAR || projd.t==Calib.MER ? 20 : 100;
      g.setStroke(new BasicStroke(epaisseur));

      Projection projd = v.getProj().copy();
      projd.setProjCenter(0, 0);
      projd.frame=0;

      rayon=0;
      int m=epaisseur/2;
      int chouilla = 1;

      if( projd.t==Calib.SIN || projd.t==Calib.ARC || projd.t==Calib.ZEA) {
         Coord c = projd.c.getProjCenter();
         projd.getXYNative(c);
         PointD center = v.getViewCoordDble(c.x, c.y);
         double signe = c.del<0?1:-1;
         c.del = c.del + signe*( projd.t==Calib.SIN ? 89 : 179);
         projd.getXYNative(c);
         PointD haut = v.getViewCoordDble(c.x, c.y);
         double deltaY = haut.y-center.y;
         double deltaX = haut.x-center.x;
         rayon = (int)(Math.abs(Math.sqrt(deltaX*deltaX+deltaY*deltaY)))-chouilla;
         x = (int)(center.x-rayon);
         y = (int)(center.y-rayon);

         g.drawOval(x-m,y-m,(rayon+m)*2,(rayon+m)*2);

      } else if( projd.t==Calib.AIT || projd.t==Calib.MOL) {
         Projection p =  projd.copy();
         angle = -p.c.getProjRot();
         p.setProjRot(0);
         p.frame = Localisation.ICRS;
         p.setProjCenter(0,0.1);
         Coord c =p.c.getProjCenter();
         p.getXYNative(c);
         PointD center = v.getViewCoordDble(c.x, c.y);
         double signe = c.del<0?1:-1;
         double del = c.del;
         c.del += signe*89;
         p.getXYNative(c);
         PointD haut = v.getViewCoordDble(c.x, c.y);
         c.del = del;
         c.al+=179;
         p.getXYNative(c);
         PointD droit = v.getViewCoordDble(c.x, c.y);
         rayon = (int)(Math.abs(haut.y-center.y))-chouilla;
         grandAxe = (int)(Math.abs(droit.x-center.x))-chouilla;
         x = (int)(center.x-grandAxe);
         y = (int)(center.y-rayon);

         if( angle==0 ) g.drawOval(x-m,y-m,(grandAxe+m)*2,(rayon+m)*2);
         else Util.drawEllipse(g, x+grandAxe,y+rayon, grandAxe+m, rayon+m, angle );
         
      } else if( projd.t==Calib.CAR ) {
         projd = v.getProjSyncView().getProj();
         Coord c1 = new Coord(1, 89);
         projd.getXYNative(c1);
         Point p1 = v.getViewCoord(c1.x, c1.y);
         Coord c2 = new Coord(359, -89);
         projd.getXYNative(c2);
         Point p2 = v.getViewCoord(c2.x, c2.y);
         x = p1.x;
         y = p1.y;
         int w = p2.x - p1.x;
         int h = p2.y - p1.y;
         
         g.drawRect(x, y, w, h);
         
      } else if( projd.t==Calib.MER ) {
         Projection p =  projd.copy();
         p.setProjCenter(0,0);
         Coord c =p.c.getProjCenter();
         p.getXY(c);
         Point center = v.getViewCoord(c.x, c.y);
         c.al+=180;
         p.getXY(c);
         Point gauche = v.getViewCoord(c.x, c.y);
         x = gauche.x;
         y = 0;
         int w = 2* (center.x - gauche.x);
         int h = v.rv.height;
         
         g.drawLine(x, 0, x, h);
         g.drawLine(x+w, 0, x+w, h);
      }
      g.setStroke(st);

      if( pixMode!=PIX_ARGB && pixMode!=PIX_RGB && video==VIDEO_INVERSE ) {
         m=0;
         g.setStroke(new BasicStroke(2));
//         g.setColor( new Color(210,220,255) );
         if( projd.t==Calib.SIN || projd.t==Calib.ARC || projd.t==Calib.ZEA) {
            g.drawOval(x-m,y-m,(rayon+m)*2,(rayon+m)*2);
         } else if( projd.t==Calib.AIT || projd.t==Calib.MOL) {
            if( angle==0 ) g.drawOval(x-m,y-m,(grandAxe+m)*2,(rayon+m)*2);
            else Util.drawEllipse(g, x+grandAxe,y+rayon, grandAxe+m, rayon+m, angle );
         }
         g.setStroke(st);
      }


      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

   }
   
   // Retourne la couleur utilis�e pour "colorier les trous"
   private Color getColorHoles() {
      
      int r,g,b;
      if( isTransparent() ) { r=69; g=145; b=161; }
      else if( color ) r=g=b=0;
      else if( cm==null ) r=g=b=255;      
      else { r = cm.getRed(0); g = cm.getGreen(0); b = cm.getBlue(0); }

      // On ne prend pas la premi�re (resp la derni�re) valeur de la CM
      // car elle sera utilis�e bien plus souvent que si on d�cale un peu
      if( Aladin.TESTV12 ) {
         if( b==255 ) b--;
         else b++;
      }
      
      return new Color(r,g,b);
   }
   
   /** Trac� d'un fond couvrant la forme de tout le ciel en fonction du type de projection
    * pour att�nuer le ph�nom�ne de "feston" */
   protected void drawBackground(Graphics g,ViewSimple v) {

      if( aladin.calque.hasHpxGrid() || isOverlay() ) return;
      
      Projection projd = v.getProjSyncView().getProj().copy();
      projd.setProjCenter(0, 0);
      projd.frame=0;

      int x=0,y=0,rayon=0,grandAxe=0;
      double angle=0;

      Color bckCol = getColorHoles();
      g.setColor( bckCol );
      rayon=0;
      
      boolean isTransparent = isTransparent();
         
      if( projd.t==Calib.SIN || projd.t==Calib.ARC || projd.t==Calib.ZEA) {
         Coord c = projd.c.getProjCenter();
         projd.getXYNative(c);
         PointD center = v.getViewCoordDble(c.x, c.y);
         double signe = c.del<0?1:-1;
         c.del = c.del + signe*( projd.t==Calib.SIN ? 89 : 179);
         projd.getXYNative(c);
         PointD haut = v.getViewCoordDble(c.x, c.y);
         double deltaY = haut.y-center.y;
         double deltaX = haut.x-center.x;
         rayon = (int)(Math.abs(Math.sqrt(deltaX*deltaX+deltaY*deltaY)));
         x = (int)(center.x-rayon);
         y = (int)(center.y-rayon);

         if( isTransparent ) {
            Graphics2D g2d = (Graphics2D)g;
            Paint paint = g2d.getPaint();
            Paint gradient = new GradientPaint(x, y,new Color(0,0,70),
                  x+rayon*2,y+rayon*2, new Color(180,190,200));
            g2d.setPaint(gradient);
            g.fillOval(x,y,rayon*2,rayon*2);
            g2d.setPaint(paint);
         } else {
            g.fillOval(x,y,rayon*2,rayon*2);
         }

      } else if( projd.t==Calib.AIT || projd.t==Calib.MOL) {
         Projection p =  projd.copy();
         angle = -p.c.getProjRot();
         p.setProjRot(0);
         p.frame = Localisation.ICRS;
         p.setProjCenter(0,0.1);
         Coord c =p.c.getProjCenter();
         p.getXYNative(c);
         PointD center = v.getViewCoordDble(c.x, c.y);
         double signe = c.del<0?1:-1;
         double del = c.del;
         c.del += signe*89;
         p.getXYNative(c);
         PointD haut = v.getViewCoordDble(c.x, c.y);
         c.del = del;
         c.al+=179;
         p.getXYNative(c);
         PointD droit = v.getViewCoordDble(c.x, c.y);
         rayon = (int)(Math.abs(haut.y-center.y));
         grandAxe = (int)(Math.abs(droit.x-center.x));
         x = (int)(center.x-grandAxe);
         y = (int)(center.y-rayon);

         if( isTransparent ) {
            Graphics2D g2d = (Graphics2D)g;
            Paint paint = g2d.getPaint();
            Paint gradient = new GradientPaint(x+rayon/4, y+rayon/4,new Color(0,0,70),
                  x+grandAxe*2-rayon/4,y+rayon*2-rayon/4, new Color(180,190,200));
            g2d.setPaint(gradient);
            if( angle==0 ) g.fillOval(x,y,grandAxe*2,rayon*2);
            else Util.fillEllipse(g, x+grandAxe,y+rayon, grandAxe, rayon, angle );
            g2d.setPaint(paint);
         } else {
            if( angle==0 ) g.fillOval(x,y,grandAxe*2,rayon*2);
            else Util.fillEllipse(g, x+grandAxe,y+rayon, grandAxe, rayon, angle );
         }
         
      } else if( projd.t==Calib.CAR ) {
         projd = v.getProjSyncView().getProj();
         Coord c1 = new Coord(1, 89);
         projd.getXYNative(c1);
         Point p1 = v.getViewCoord(c1.x, c1.y);
         Coord c2 = new Coord(359, -89);
         projd.getXYNative(c2);
         Point p2 = v.getViewCoord(c2.x, c2.y);
         x = p1.x;
         y = p1.y;
         int w = p2.x - p1.x;
         int h = p2.y - p1.y;
         
         if( isTransparent ) {
            Graphics2D g2d = (Graphics2D)g;
            Paint paint = g2d.getPaint();
            Paint gradient = new GradientPaint(0, 0,new Color(0,0,70), w,h, new Color(180,190,200));
            g2d.setPaint(gradient);
            g.fillRect(x,y, w,h);
            g2d.setPaint(paint);
         } else {
            g.fillRect(x,y, w,h);
         }
        
      } else if( projd.t==Calib.MER ) {
         Projection p =  projd.copy();
         p.setProjCenter(0,0);
         Coord c =p.c.getProjCenter();
         p.getXYNative(c);
         Point center = v.getViewCoord(c.x, c.y);
         c.al+=180;
         p.getXYNative(c);
         Point gauche = v.getViewCoord(c.x, c.y);
         x = gauche.x;
         y = 0;
         int w = 2* (center.x - gauche.x);
         int h = v.rv.height;
         if( isTransparent ) {
            Graphics2D g2d = (Graphics2D)g;
            Paint paint = g2d.getPaint();
            Paint gradient = new GradientPaint(0, 0,new Color(0,0,70), w,h, new Color(180,190,200));
            g2d.setPaint(gradient);
            g.fillRect(x,y, w,h);
            g2d.setPaint(paint);
         } else {
            g.fillRect(x,y, w,h);
         }
      
      } else {
         int w = v.rv.width;
         int h = v.rv.height;
         if( isTransparent ) {
            Graphics2D g2d = (Graphics2D)g;
            Paint paint = g2d.getPaint();
            Paint gradient = new GradientPaint(0, 0,new Color(0,0,70), w,h, new Color(180,190,200));
            g2d.setPaint(gradient);
            g.fillRect(0, 0, w,h);
            g2d.setPaint(paint);
         } else {
            g.fillRect(0, 0,w,h);
         }
      }
   }

   protected boolean isSegmentIAUConv() {
      return segmentIAUConv;
   }
   
   protected float getSegmentLenFactor() {
      return segmentLenFactor;
   }
   protected void setSegmentLenFactor(float segmentLenFactor) {
      this.segmentLenFactor = segmentLenFactor;
   }
   protected int getSegmentThickness() {
      return segmentThickness;
   }
   protected void setSegmentThickness(int segmentThickness) {
      this.segmentThickness = segmentThickness;
   }
   protected float getSegmentDensityFactor() {
      return segmentDensityFactor;
   }
   protected void setSegmentDensityFactor(float segmentDensityFactor) {
      this.segmentDensityFactor = segmentDensityFactor;
   }

   private long timerLastDrawBG = 0L;

   /** demande un r�affichage complet si le dernier affichage commence � se faire
    * vieux histoire de pouvoir purger les losanges d�sormais inutiles */
   private void shouldRefresh() {
      long t = System.currentTimeMillis();
      if( t - timerLastDrawBG < 500 ) return;
      timerLastDrawBG = t;
      changeImgID();
      aladin.view.repaintAll();
   }

   // Test qui rep�re un probl�me sur le serveur
   protected boolean detectServerError(int nb[]) {
      if( moc==null ) return false;
      else removeHealpixOutsideMoc();
      
//      return nb[HealpixKey.READY]==0 && nb[HealpixKey.ERROR]>5;
      
      int nbReady=0;
      int nbError=0;
      
      for( HealpixKey key : pixList.values() ) {
         int status = key.getStatus();
         if( status==HealpixKey.READY ) nbReady++;
         else if( status==HealpixKey.ERROR ) nbError++;
      }

      return nbReady==0 && nbError>5;
   }


   /** Test r�seau sur le HiPS. Se contente de charger un certain nombre de losange
    * pour d�terminer la qualit� du serveur et du r�seau pour le HiPS
    * @throws Exception
    */
   public String testnet() throws Exception {

      try {
         if( moc==null ) loadInternalMoc();
      } catch( Exception e1 ) { }
      long time=0L;
      long tileTime=0L,allskyTime=0L;
      long size=0L;
      int minOrder=3;

      long timeMax = 10*1000;
//      long sizeMax = 30*1024*1024;

      long start = System.currentTimeMillis();

      resetGzippedMode();
      System.out.println("Testnet HiPS "+label+" maxOrder="+maxOrder+" from "+url+" :");
      int n=0;
      boolean allsky=false;

      int memo = aladin.levelTrace;
      aladin.levelTrace=0;

      while( true ) {

         try {
            // Fin du test
            if( System.currentTimeMillis() - start > timeMax ) {
               if( allsky ) break;
               allsky=true;
            }
//            if( size > sizeMax ) break;
            int order = minOrder + (int)(Math.random()*(maxOrder-minOrder));
            long npix;
            ArrayList<String> a = new ArrayList<>();

            // On prend l'�l�ment central d'un intervalle au hasard
            int nb = moc.getNbRanges();
            int i = (int)( Math.random()*nb);
            npix = (moc.seeRangeList().begins(i)+ (moc.seeRangeList().ends(i)-1))/2L;
            int shift = (SMoc.MAXORD_S - order)*2;
            npix = npix>>>shift;
         
            // Pour �viter de charger plusieurs fois la m�me tuiles
            String key = order+"/"+npix;
            if( a.contains(key) ) continue;
            a.add(key);
            
            HealpixKey hk;
            if( allsky ) {
               key="3/-1";
               hk = new HealpixAllsky(this, 3, 0, HealpixKey.TESTNET);
            } else  hk = new HealpixKey(this, order, npix, HealpixKey.TESTNET);

            long t = hk.timeNet;
            long s = hk.sizeStream;
            if( s==0 ) System.out.println(" error => "+url+"/"+hk.getFileNet());
            else  System.out.println(".Loading "+key+" "+Util.getUnitDisk(s)+(hk.gzipped?"/gzip":"")+" in "+Util.getTemps(t*1000L));
            time += t;
            if( allsky ) allskyTime=t;
            else tileTime +=t;
            size += s;
            n++;
         } catch( Exception e ) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }

      aladin.levelTrace=memo;
      System.out.println("=> Downloaded "+n+" tiles in "+HealpixKey.EXT[getTileMode()]
            +" : "+Util.getUnitDisk(size)+" in "+Util.getTemps(time*1000L));

      long rate = (long)( size/(time/1000.) );
      String res = "=> Stream rate "+Util.getUnitDisk(rate)+"/s ("+n+" tiles)";
      System.out.println(res);
      aladin.console.printInfo(label+" net perf "+res);
      
      return Util.getUnitDisk(rate)+"/s"
            +" ("+n+" tiles/avg="+Util.getTemps(tileTime/(n-1)*1000L)
            +" allsky/"+Util.getTemps(allskyTime*1000L)+")";
   }
   
   /** Incr�mente le d�compte des tuiles charg�es: 1-gzipped, 2- no-gzipped */
   protected void incrGzipMode( int mode, long t ) {
      if( inJPEG || inPNG  ) return;
      if( mode==1 ) { nbgzip++;  timegzip+=t; }
      else { nbnogzip++; timenogzip+=t; }
   }
   
   private int nbgzip=0;          // nombre de tuiles charg�es en gzip
   private long timegzip=0L;      // cumul du temps de chargement des tuiles gzipp�es
   private int nbnogzip=0;        // nombre de tuiles charg�es en nogzip
   private long timenogzip=0L;    // cumul du temps de chargement des tuiles nogzipp�es
   
   private int gzipMode=0;  // 0-pas encore d�termin�, -1-test gzip, -2-test nogzip, 1-gzip, 2-nogzip
   
   /** reset les statistiques et le mode gzip d�termin� pr�c�demment (typiquement lorsqu'on change de mirroir) */
   protected void resetGzippedMode() {
      nbgzip=nbnogzip=0;
      timegzip=timenogzip=0L;
      gzipMode=0;
   }
   
   /** D�termine le mode de chargement gzip ou nogzip en fonction de la nature des tuiles
    * et de tests pr�alables pour d�terminer le meilleur choix en fonction du contexte.
    * @return -1-test gzip, -2-test nogzip, 1-gzip, 2-nogzip
    */
//   protected int getGzippedMode() {
//      int rep = getGzippedMode1();
//      System.out.println("getGzippedMode() nbgzip="+nbgzip+" nbnogzip="+nbnogzip+" gzipMode="+gzipMode+" rep="+rep);
//      return rep;
//   }
   protected int getGzippedMode() {
      if( inJPEG || inPNG ) return 2;
      if( gzipMode>0 ) return gzipMode;
      if( gzipMode==0 ) {
         
         // On charge au moins 2 tuiles en gzip pour calculer une valeur moyenne
         if( nbgzip<2 ) return -1;
         // On charge au moins 2 tuiles en nogzip pour calculer une valeur moyenne
         else if( nbnogzip<2 ) return -2;
         
         // Et on d�termine la meilleure strat�gie
         else {
            if( nbgzip==0 ) return -1;   // toujours pas assez de tuiles gzip charg�es
            if( nbnogzip==0 ) return -2;   // toujours pas assez de tuiles nogzip charg�es
            
            // On peut d�cider de la meilleure strat�gie
            gzipMode = (timegzip/nbgzip)<(timenogzip/nbnogzip) ? 1 : 2;
//            System.out.println("XXX timegzip="+(timegzip/nbgzip)+"ms timenogzip="+(timenogzip/nbnogzip)+"ms =>gzipmode="+gzipMode);
         }
      }
      return gzipMode;
   }
   
   /**
    * G�re le chargement des losanges de mani�re asynchrone
    */
   class HealpixLoader implements Runnable {
      static final int POOLSIZE = 1; //8;
      boolean POOLTEST = true;   // EN COURS DE DEVELOPPEMENT POUR METTRE NE PLACE UN POOL DE THREADS DE CHARGEMENT DE TUILES
      static final int DELAI =500;   // delai en ms entre deux demandes de chargement des losanges

      private boolean loading;      // false s'il n'y a plus de losange en cours de chargement
      private boolean purging;      // false s'il n'y a plus aucun losange � purger
      private Thread thread=null;
      private Loader cacheLoader, netLoader;
      private Loader [] netPool;

      HealpixLoader() {
         loading=false;
         purging=false;
         cacheLoader = new Loader(0);
         
//         POOLTEST = aladin.view.getNbView()<=2;
//         if( POOLTEST ) {
            netPool = new Loader[POOLSIZE];
            for( int i=0; i<netPool.length; i++ ) netPool[i] = new Loader(1);
//         } else {
            netLoader = new Loader(1);
//         }
         wakeUp();
      }

      /** Retourne true si l'image de meilleure r�solution est pr�te */
      protected boolean isReadyForDrawing() { return readyAfterDraw;/* pourcent==-1; */ }

      /** Retourne true s'il y a encore au-moins un losange en cours de chargement */
      protected boolean isLoading() { return loading; }

      /** Retourne true s'il y a encore au-moins un losange � purger */
      protected boolean isPurging() { return purging; }

      /** Demande de parcours de la liste des losanges */
      protected void wakeUp() {

         if( thread!=null ) {
            loading=true;
            noSleep();
            if( isSleeping() ) thread.interrupt();
         }
         else {
            thread = new Thread(this,"HealpixLoader");
            Util.decreasePriority(Thread.currentThread(), thread);
            //            thread.setPriority(Thread.currentThread().getPriority()-1);
            thread.start();
         }
      }

      volatile private boolean sleep;
      private boolean isSleeping=false;
      private Object lockSleep = new Object();

      private void noSleep() { synchronized( lockSleep ) { sleep=false; } }

      private boolean isSleeping() {
         synchronized( lockSleep ) { return isSleeping; }
      }

      private void setSleep(boolean flag) {
         synchronized( lockSleep ) { isSleeping=flag; }
      }
      
      private long ot = -1;

      /** retourne true tant que le thread du loader a quelque chose sur le feu */
      private boolean shouldRun() {
         return isLoading() || isPurging();
      }

      /** Boucle de surveillance des losanges en attentes */
      public void run() {
         Util.pause(100);
         if( useCache ) cacheLoader.start();
         POOLTEST = !local && aladin.view.getNbView()<=2;
         if( POOLTEST ) {
            for( Loader a : netPool ) a.start();
         } else {
            netLoader.start();
         }
         do {
            sleep=true;
            try {
               try { launchJob(); }
               catch( Exception e ) { e.printStackTrace(); };

               if( useCache ) scanCache();

               if( sleep ) {
                  setSleep(true);
                   Aladin.trace(5,"PlanBG.HealpixLoader sleeping");
                  try { Thread.currentThread().sleep(DELAI); }
                  catch( Exception e ) {
                     Aladin.trace(5,"PlanBG.HealpixLoader wake up !");
                  }
                  setSleep(false);
               }

            } catch( Throwable e1 ) { e1.printStackTrace(); }
         } while ( shouldRun() );
         if( useCache ) cacheLoader.stop();
         if( POOLTEST ) {
            for( Loader a : netPool ) a.stop();
         } else {
            netLoader.stop();
         }
         thread=null;
         Aladin.trace(5,"PlanBG.HealpixLoader died");
         nbFlush=0;
         aladin.gcIfRequired();
      }

      /** Parcours de la liste des losanges healpix et lancement du chargement de ceux
       * qui n'ont pas encore �t� demand�. Mise � jour du flag "loading" => voir isLoading()
       */
      private void launchJob() throws Exception {
         //System.out.println("Thread working...");
         boolean stillOnePurge=false;
         boolean perhapsOneDeath=false;
         int [] nb = new int[HealpixKey.NBSTATUS];
//         boolean flagVerbose =  aladin.calque.hasHpxGrid();
         boolean flagVerbose =  aladin.levelTrace>=5;

         boolean first=true;
         int n=0;
         
         // Parcours de la liste en commen�ant par les r�solutions les plus mauvaises
         try {
            ArrayList<HealpixKey> list = new ArrayList<>();
            Enumeration<HealpixKey> e = pixList.elements();
            while( e.hasMoreElements() ) list.add(e.nextElement());
            try { Collections.sort(list); } catch( Exception e1 ) { }

            Iterator<HealpixKey> it = list.iterator();
            while( it.hasNext() ) {
               final HealpixKey healpix = it.next();
               int status = healpix.getStatus();

               // Un peu de d�buging si besoin
               if( flagVerbose && status!=HealpixKey.ERROR ) {
                  System.out.println((first?"\n":"")+healpix);
                  first=false;
               }

               // Purge ?
               int live = healpix.getLive();
               if( live==HealpixKey.DEATH ) purge(healpix);
               else {
                  if( live==HealpixKey.MAYBEDEATH ) {
                     perhapsOneDeath=true;
                     stillOnePurge=true;
                  }
                  else if( status==HealpixKey.READY ) healpix.purgeFils();
               }

               switch( status ) {
                  case HealpixKey.ASKING:
                     if( !healpix.allSky && healpix.priority>=1000 ) healpix.setOld();
                     else {
                        if( useCache && healpix.isCached() ) healpix.setStatus(status=HealpixKey.TOBELOADFROMCACHE);
                        else healpix.setStatus(status=HealpixKey.TOBELOADFROMNET);
                     }
                     break;
                  case HealpixKey.READY:
                     if( useCache ) healpix.write();
                     break;
               }

               nb[status]++;

               if( healpix.priority<250 && !(status==HealpixKey.READY || status==HealpixKey.ERROR) ) n++;
               else if( healpix.npix==-1 && !(status==HealpixKey.READY || status==HealpixKey.ERROR) ) n+=10;

            }
         } catch( Exception e) { e.printStackTrace(); return; }


         loading= nb[HealpixKey.ASKING]>0 || nb[HealpixKey.TOBELOADFROMCACHE]>0
               || nb[HealpixKey.TOBELOADFROMNET]>0 || nb[HealpixKey.LOADINGFROMCACHE]>0
               || nb[HealpixKey.LOADINGFROMNET]>0;
               
         purging= stillOnePurge || nb[HealpixKey.PURGING]>0;

         pourcent = n==0 ? -2 : n>=10 ? 1 : (10-n)*10.;
         readyAfterDraw= n==0;

         // Pour du debug
         nbReady=nb[HealpixKey.READY];


//         System.out.print("HealpixKey loader (loading="+loading+" purging="+purging+"): ");
//         for( int i=0; i<HealpixKey.NBSTATUS; i++ ) {
//            if( nb[i]>0 ) System.out.print(HealpixKey.STATUS[i]+"="+nb[i]+" ");
//         }
//         System.out.println();

         if( detectServerError(nb) ) error="Server not available";

         // Eventuel arr�t du chargement en cours si priorit� d�sormais plus faible
         HealpixKey healpixMin=null,healpixNet=null;
         int min = Integer.MAX_VALUE;
         Enumeration<HealpixKey> e = pixList.elements();
         while( e.hasMoreElements() ) {
            HealpixKey healpix = e.nextElement();
            int status = healpix.getStatus();
            if( status!=HealpixKey.TOBELOADFROMNET && status!=HealpixKey.LOADINGFROMNET ) continue;
            if( status==HealpixKey.LOADINGFROMNET ) healpixNet=healpix;
            if( healpix.priority<min ) {
               min=healpix.priority;
               healpixMin=healpix;
            }
         }

         if( healpixNet!=null && healpixNet!=healpixMin ) healpixNet.abort();

         if( nb[HealpixKey.TOBELOADFROMCACHE]>0 ) cacheLoader.wakeUp();
         if( nb[HealpixKey.TOBELOADFROMNET]>0 )   {
            if( POOLTEST ) {
               for( Loader a : netPool ) if( a.isPause() ) a.wakeUp();
            } else {
               netLoader.wakeUp();
            }
         }

         // Pour faire blinker le plan
         if( aladin.calque!=null && oLoading!=loading ) {
            oLoading=loading;
            aladin.calque.select.repaint();
         }

         if( perhapsOneDeath ) shouldRefresh();

      }
   }


   class MyEnum implements Enumeration<HealpixKey> {
      HealpixKey [] tab;
      int size;
      int pos;

      MyEnum(Hashtable<String, HealpixKey> pixList) {
         tab = new HealpixKey[ pixList.size() ];
         Enumeration<HealpixKey> e = pixList.elements();
         int i;
         for( i=0; e.hasMoreElements() && i<tab.length; i++ ) tab[i] = e.nextElement();
         size = i;
         try { Arrays.sort(tab,0,size); } catch( Exception e1 ) { }
         pos=0;
      }

      public boolean hasMoreElements() {
         return pos<size;
      }

      public HealpixKey nextElement() {
         return tab[pos++];
      }
   }

   static private int THREADID = 0;
   private boolean oLoading = false;
   static private final int MAXTIMETOBELOADFROMNET = 1000;      // Temps maximum autoris� pour prendre en charge plus d'un losange distant

   /** G�re le chargement d'un losange de mani�re asynchrone
    */
   class Loader implements Runnable {


      boolean encore;
      Thread thread;
      int type;
      String label;

      Loader(int type) {
         this.type=type;
         label=(type==0?"LoaderCache-":"LoaderNet-")+THREADID++;
      }

      void start() {
         encore=true;
         if( thread!=null ) thread.interrupt();
         thread = new Thread(this,label);
         if( !Aladin.NOGUI ) Util.decreasePriority(Thread.currentThread(), thread);
         thread.start();
      }

      void wakeUp() {
         if( thread==null ) start();
         else if( pause ) thread.interrupt();
      }

      private boolean pause=false;
      synchronized void setPause(boolean flag) { pause=flag; }
      
      boolean isPause() { return pause; };

      void stop() {
         encore=false;
         if( thread!=null ) thread.interrupt();
      }

      public void run() {
         boolean flagLoad;

         while( encore ) {
            try {
               //System.out.println(label+" running...");
               flagLoad=false;
               
               // On ne charge que si on a le temps...
               if( !aladin.view.mustDrawFast() ) {
                  try {
                     ArrayList<HealpixKey> list = new ArrayList<>();
                     //                     Enumeration<HealpixKey> e = pixList.elements();
                     Enumeration<HealpixKey> e = new MyEnum(pixList);


                     //                     int min = Integer.MAX_VALUE;
                     //                     HealpixKey minHealpix=null;
                     //                     if( e.hasMoreElements() ) System.out.println("Load :");

                     while( e.hasMoreElements() ) {
                        final HealpixKey healpix = e.nextElement();
                        int status = healpix.getStatus();

                        // Du cache on charge imm�diatement et en priorit�
                        if( type==0 && status==HealpixKey.TOBELOADFROMCACHE ) {
                           healpix.loadFromCache();
                           if( !healpix.allSky ) setTileOrder(healpix.getLosangeOrder());
                           flagLoad=true;
//                           Aladin.trace(5,"PlanBG.Loader ("+label+") loading "+healpix);

                           //System.out.println("   Load "+healpix);

                           // Du net on va voir si on a le temps
                        } else if( type==1 && status==HealpixKey.TOBELOADFROMNET ) {
                           list.add(healpix);
                        }
                     }

                     // Quelque chose du Net ?
                     long t = System.currentTimeMillis();
                     if( list.size()>0 ) {
                        Collections.sort(list);
                        for( HealpixKey h :  list ) {
                           h.waitLock();
                           if( h.getStatus()!=HealpixKey.TOBELOADFROMNET) {
                              h.unLock();
                              continue;   // ca a chang�, tant pis !
                           }

                           //System.out.println("   Load "+h);
                           if( type==0 ) h.loadFromCache();
                           else h.loadFromNet();
                           h.unLock();

                           if( !h.allSky ) setTileOrder(h.getLosangeOrder());
                           //                           Aladin.trace(5,"PlanBG.Loader ("+label+") loading "+h);

                           // Trop long pour un autre ? =>  �a sera pour le prochain tour
//                           if( System.currentTimeMillis() - t > MAXTIMETOBELOADFROMNET ) break;
                        }
                        flagLoad = true;
                     }
                  } catch( Exception e) { if( Aladin.levelTrace>=3 ) e.printStackTrace(); }
               }

               if( flagLoad ) loader.wakeUp();
               else {
//                  Aladin.trace(5,"PlanBG.Loader ("+label+") sleeping...");
                  try {
                     setPause(true);
                     Thread.currentThread().sleep(10000);
                  } catch( Exception e ) {
//                     Aladin.trace(5,"PlanBG.Loader ("+label+") wake up !");
                  }
               }
            } catch( Throwable t ) { if( Aladin.levelTrace>=3 ) t.printStackTrace(); }
         }
            Aladin.trace(5,"PlanBG.Loader ("+label+") died !");
         thread=null;

      }
   }

   public void center(Coord coord) {
      aladin.view.setRepere(coord);
      aladin.view.showSource();
      aladin.view.zoomview.repaint();
      aladin.calque.repaintAll();
   }


   /**************************************** Informations statistiques du m�canisme Allsky ******************************/

   // pour pouvoir mesurer la moyenne du temps des chargements
   protected long cumulTimeLoadNet=0;    // Temps cumul� de chargement via le Net
   protected long cumulTimeLoadCache=0;  // Temps cumul� de chargement via le Cache
   protected long cumulTimeWriteCache=0; // Temps cumul� d'�criture dans le Cache
   protected long cumulTimeDraw=0;       // Temps cumul� d'affichage des losanges
   protected long cumulTimeStream=0;     // Temps cumul� pour le d�chargement (via le Net)
   protected long cumulTimeJPEG=0;       // Temps cumul� pour le d�codage JPEG (via le Net)
   protected long cumulTimePixel=0;      // Temps cumul� pour l'extraction des pixels (via le Net)
   protected int nbLoadNet=0;            // Nombre de losanges lus via le Net
   protected int nbLoadCache=0;          // Nombre de losanges lus via le Cache
   protected int nbWriteCache=0;         // Nombre de losanges �cris dans le Cache
   protected int nbImgDraw=0;            // Nombre d'affichages de losange
   protected long nByteReadNet=0L;       // Nombre de bytes lus via le Net
   protected long nByteReadCache=0L;     // Nombre de bytes lus via le Cache
   protected long nByteWriteCache=0L;    // Nombre de bytes �crit dans le Cache
   protected int nbImgCreated=0;         // Nombre d'image cr��es
   protected int nbImgInBuf=0;           // Nombre d'image reprise en m�moire
   protected int nbCreated=0;            // Nombre de losanges cr��s (sans compter les fils)
   protected int nbAborted=0;            // Nombre de losanges interrompus
   protected int nbFree=0;               // Nombre de losanges supprim�s

   /** Reset des statistiques */
   protected void resetStats() {
      cumulTimeLoadNet=cumulTimeLoadCache=cumulTimeWriteCache=cumulTimeDraw=0L;
      cumulTimeStream=cumulTimeJPEG=cumulTimePixel=0L;
      nbLoadNet=nbLoadCache=nbWriteCache=0;
      nByteReadNet=nByteReadCache=nByteWriteCache=0L;
      nbImgCreated=nbImgInBuf=nbCreated=nbFree=nbAborted=nbImgDraw=0;
      
      // On en profite pour resetter la strat�gie gzip/nogzip 
      resetGzippedMode();
   }

   /** retourne une chaine donnant des stats sur l'usage des losanges Healpix */
   protected String getStats() {
      return "HealpixKey stats: "+label+":\n" +
            ".Created: "+nbCreated+"   Abort: "+nbAborted+"   Free: "+nbFree+"\n" +
            ".Net   : "+nbLoadNet  +" => "+Util.round(nByteReadNet/(1024*1024.),2)  +"Mb in ~" +Util.round(avgLoadNet(),0)  +"ms"
            +" "+streamJpegPixel()+"\n"+
            ".CacheR: "+nbLoadCache+" => "+Util.round(nByteReadCache/(1024*1024.),2)+"Mb in ~" +Util.round(avgLoadCache(),0)+"ms\n" +
            ".CacheW: "+nbWriteCache+" => "+Util.round(nByteWriteCache/(1024*1024.),2)+"Mb in ~" +Util.round(avgWriteCache(),0)+"ms\n" +
            ".Img created: "+nbImgCreated+"    reused:"+nbImgInBuf+"    drawn "+nbImgDraw+" in ~"+Util.round(avgDraw(),0)+"ms\n"
            ;
   }
   
   /** Retourne le cr�ateur du HiPS indiqu� dans l'identificateur => cad le premier champ */
   public String getCreatorFromId() {
      String s = id;
      if( s==null ) return null;
      if( s.startsWith("ivo://") ) s=s.substring(6);
      int offset = s.indexOf('/');
      int offset1 = s.indexOf('?');
      if( offset==-1 && offset1==-1 ) return null;
      if( offset==-1 ) offset=s.length();
      if( offset1==-1 ) offset1=s.length();
      if( offset1<offset ) offset=offset1;
      return s.substring(0,offset);
   }

   // Extrait le nom du host du serveur
   private String getHost() {
      int i = url.indexOf("//");
      if( i==-1 ) return "?";
      int j = url.indexOf("/",i+2);
      if( j<0 ) j=url.length();
      return url.substring(i+2,j);
   }

   /** retourne une chaine donnant des stats minimales sur l'usage des losanges Healpix */
   protected String getShortStats() {
      if( nbLoadNet==0 && nbLoadCache==0 ) return null;
      boolean flagLocal = isLocalAllSky();
      String name = id!=null?id:label;
      return name+(flagLocal?" Local:":" Net["+getHost()+"]:")+nbLoadNet  +"/"+Util.round(nByteReadNet/(1024*1024.),2)  +"Mb/" +Util.round(avgLoadNet(),0)  +"ms"
      +" CacheR:"+nbLoadCache+"/"+Util.round(nByteReadCache/(1024*1024.),2)+"Mb/" +Util.round(avgLoadCache(),0)+"ms"
      +" CacheW:"+nbWriteCache+"/"+Util.round(nByteWriteCache/(1024*1024.),2)+"Mb/" +Util.round(avgWriteCache(),0)+"ms";
   }

   /** Retourne le temps moyen pour le chargement r�seau d'une image, sa d�compression JPEG, l'extraction des pixels */
   protected String streamJpegPixel() {
      if( nbLoadNet==0 ) return "";
      return "(stream="+Util.round(cumulTimeStream/nbLoadNet,1)
            +"/jpeg="+Util.round(cumulTimeJPEG/nbLoadNet,1)
            +"/getpix="+Util.round(cumulTimePixel/nbLoadNet,1)+")";
   }


   /** Retourne une chaine indiquant la qualit� du r�seau entre le serveur et le client, null si la mesure n'est pas disponible */
   protected String getNetSpeed() {
      long r = rateLoadNet();
      int er = nbTileError();
      if( er==0 && r==0 ) return null;  // Rien � dire
      StringBuilder s=new StringBuilder();

      // D�bit
      if( r!=0 ) s.append( Util.getUnitDisk(r)+"/s");

      // Il y a des losanges en erreur
      if( er>0 ) {
         if( s.length()>0 ) s.append(" - ");
         s.append("tile errors: "+er+"/"+pixList.size()+" ("+Util.myRound(100.*er/pixList.size())+"%)");
      }

      return s.toString();
   }


   /** Retourne le nombre de losanges en erreur qui pourtant se trouvent dans le survey */
   protected int nbTileError() {
      if( moc==null ) return 0;   // non significatif car contient �galement les losanges hors champs
      removeHealpixOutsideMoc();

      Enumeration<HealpixKey> e = pixList.elements();
      int n=0;
      while( e.hasMoreElements() ) {
         HealpixKey h = e.nextElement();
         if( h.getStatus()==HealpixKey.ERROR ) {
            n++;
            aladin.trace(4,"Error on "+h);
         }
      }
      return n;
   }

   /** Retourne le d�bit des chargements des losanges via le r�seau (en octets/seconde) */
   protected long rateLoadNet() {
      if( cumulTimeLoadNet==0 ) return 0;
      long r = (nByteReadNet/cumulTimeLoadNet)*1000;
      return r;
   }

   /** Retourne le temps moyen pour l'affichage d'un losange en ms */
   protected double avgDraw() {
      if( nbImgDraw==0 ) return 0;
      return ((double)cumulTimeDraw/nbImgDraw)/1000000;
   }

   /** Retourne le temps moyen de chargement d'un losange via le r�seau (en ms) */
   protected double avgLoadNet() {
      if( nbLoadNet==0 ) return 0;
      return ((double)cumulTimeLoadNet/nbLoadNet);
   }

   /** Retourne le temps moyen de chargement d'un losange depuis le cache (en ms) */
   protected double avgLoadCache() {
      if( nbLoadCache==0 ) return 0;
      return ((double)cumulTimeLoadCache/nbLoadCache);
   }

   /** Retourne le temps moyen de chargement d'un losange en �criture sur le cache (en ms) */
   protected double avgWriteCache() {
      if( nbWriteCache==0 ) return 0;
      return ((double)cumulTimeWriteCache/nbWriteCache);
   }




}
