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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

import cds.aladin.Aladin;
import cds.aladin.MyInputStream;
import cds.aladin.MyProperties;
import cds.aladin.Tok;
import cds.fits.Fits;
import cds.moc.SMoc;
import cds.tools.Util;

/** Recopie d'un HiPS distant via HTTP
 * @author Pierre Fernique
 * @version 1.0 juin 2015 cr�ation
 * @version 1.1 f�vrier 2019 gestion du multi-partition (param�tre split=...)
 */
public class BuilderMirror extends BuilderTiles {
   
   static private final int TIMEOUT = 15000;    // 15 sec sans nouvelle on r�initialise la connection HTTP
   static private String USERAGENT = "Hipsgen (mirror) Aladin/"+Aladin.VERSION.substring(1);

   private Fits bidon;                  // Ne sert qu'� renvoyer quelque chose pour faire plaisir � BuilderTiles
   private MyProperties prop;           // Correspond aux propri�t�s distantes
   private boolean isPartial=false;     // indique que la copie sera partielle (spatialement, order ou format des tuiles)
   private boolean isSmaller=false;     // indique que la copie concerne une zone spatial plus petite que l'original
   private boolean isUpdate=false;      // true s'il s'agit d'une maj d'un HiPS d�j� copi�
   private boolean flagIsUpToDate=false;      // true s'il s'agit d'une maj d'un HiPS d�j� copi� et d�j� � jour
   private String dateRelease="";       // Date of last release date of the local copy
   private boolean isLocal=false;       // true s'il s'agit d'une copie locale
   private long timeIP;
   private boolean check=false;         // true si on red�marre une session => pas de test de taille sur les tuiles d�j� arriv�es
   boolean flagCat=false;               // true s'il s'agit d'un HiPS catalogue

   public BuilderMirror(Context context) {
      super(context);
   }

   public Action getAction() { return Action.MIRROR; }
   
   // Valide la coh�rence des param�tres
   public void validateContext() throws Exception {
      
      // D�termination d'une �ventuelle copie locale
      String dir = context.getInputPath();
      check = context.getMirrorCheck();
      isLocal = !dir.startsWith("http://") && !dir.startsWith("https://") && !dir.startsWith("ftp://");
      if( isLocal ) context.info("Local mirror copy");
      if( !isLocal && check ) context.info("Will check all date and size for already loaded tiles");

      // Chargement des propri�t�s distantes
      prop = new MyProperties();
      MyInputStream in = null;
      
      InputStreamReader in1=null;
      try {
         in1 = new InputStreamReader( Util.openAnyStream( context.getInputPath()+"/properties"), "UTF-8" );
         prop.load(in1);
      } finally{  if( in1!=null ) in1.close(); }
      
      // On valide le r�pertoire de destination
      validateOutput();
      
      // D�termination du statut
      String s = prop.getProperty(Constante.KEY_HIPS_STATUS);
      if( s!=null && context.testClonable && s.indexOf("unclonable")>=0 ) {
         throw new Exception("This HiPS is unclonable => status: "+s);
      }

      // D�termination du type de HiPS
      flagCat=false;
      s = prop.getProperty("dataproduct_type");
      if( s!=null && s.indexOf("catalog")>=0 ) {
         flagCat=true;
         
         throw new Exception("Hipsgen mirror not usable for catalog HiPS");
         
         // TEST POUR COPIE HIPSCAT
//         context.info("Mirroring a HiPS catalog...");
      }

      // D�termination de l'ordre max: si non pr�cis�, r�cup�r� depuis
      // les propri�t�s distantes
      s = prop.getProperty(Constante.KEY_HIPS_ORDER);
      if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_ORDER);
      if( s==null ) context.warning("No order specified in the remote HiPS properties file !");
      int o = s==null ? -1 : Integer.parseInt(s) ;
      int paramO = context.getOrder();
      if( paramO ==-1 ) {
         if( o==-1 ) throw new Exception("Order unknown !");
         context.setOrder(o);
      } else {
         if( o!=-1 ) {
            if( paramO>o ) throw new Exception("Order greater than the original");
            else if( o!=paramO ) isPartial=true;
         }
      }

      // D�termination de l'ordre min: si non pr�cis�, r�cup�r� depuis
      // les propri�t�s distantes
      s = prop.getProperty(Constante.KEY_HIPS_ORDER_MIN);
      if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_ORDER_MIN);
      if( s==null ) context.info("No min order specified in the remote HiPS properties file !");
      o = s==null ? -1 : Integer.parseInt(s) ;
      paramO = context.minOrder;
      if( paramO ==-1 ) { 
         if( o==-1 ) {
            o = flagCat ? 1 : 3;    // Le d�faut en fonction de la nature du HiPS
            context.warning("Min order unknown => use default ["+o+"]");
         }
         context.setMinOrder(o);
      } else {
         if( o!=-1 ) {
            if( paramO<o ) throw new Exception("Specified min Order lower than the original");
            else if( o!=paramO ) isPartial=true;
         }
      }

      // D�termination des types de tuiles: si non pr�cis�, r�cup�r�
      // depuis les propri�t�s distantes
      s = prop.getProperty(Constante.KEY_HIPS_TILE_FORMAT);
      if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_TILE_FORMAT);
      if( context.tileFormat==null ) {
         if( s==null ) throw new Exception("tile format unknown");
         Tok tok = new Tok(s);
         while( tok.hasMoreTokens() ) context.addTileFormat(tok.nextToken());
      } else {
         if( s!=null && !context.getTileFormat().equals(s) ) isPartial=true;
      }
      context.info("Mirroring tiles: "+context.getTileFormat()+"...");
      
      // r�f�rence spatiale
      s = prop.getProperty(Constante.KEY_HIPS_FRAME);
      if( s==null ) s = prop.getProperty(Constante.OLD_HIPS_FRAME);
      if( s!=null ) context.setFrameName(s);

      // D�termination du Moc
      SMoc area = new SMoc();
      try {
         in = Util.openAnyStream( context.getInputPath()+"/Moc.fits");
         area.read(in);
         
         // Si le syst�me de coordonn�es du MOC n'est pas le m�me que celui de HiPS, il faut 
         // convertir le MOC
         if( !context.getFrameCode().equals( area.getSpaceSys()) ) {
            context.info("MOC conversion in "+context.getFrameName()+" frame (HiPS target frame)...");
            area = new SMoc( Util.convertTo( area, context.getFrameCode()) );
         }
         
         if( context.getArea()==null ) {
            context.setMocArea( area );
         } else {
            if( !context.getArea().equals(area)) {
               isSmaller=isPartial=true;
               context.setMocArea( area.intersection( context.getArea()) );
               context.info("Partial spacial mirror");
            }
         }
      } finally{  if( in!=null ) in.close(); }

      // Mode couleur ou non
      s = prop.getProperty(Constante.KEY_DATAPRODUCT_SUBTYPE);
      if( s!=null ) { if( s.equals("color")) context.setBitpixOrig(0); }
      else {
         s = prop.getProperty(Constante.OLD_ISCOLOR);
         if( s==null ) s = prop.getProperty("isColor");
         if( s!=null && s.equals("true")) context.setBitpixOrig(0);
      }
      if( context.isColor() ) context.info("Mirroring colored HiPS");

      // Cube ?
      s = prop.getProperty(Constante.KEY_CUBE_DEPTH);
      if( s==null ) s = prop.getProperty(Constante.OLD_CUBE_DEPTH);
      if( s!=null ) {
         int depth = Integer.parseInt(s);
         context.setDepth(depth);
      }
      if( context.isCube() ) context.info("Mirroring cube HiPS (depth="+context.depth+")");

      // D�termination de la zone � copier
      context.moc = context.getArea();
      context.setValidateRegion(true);

      // Peut �tre existe-t-il d�j� une copie locale � jour ?
      if( (new File(context.getOutputPath()+"/properties")).exists() ) {
         MyProperties localProp = new MyProperties();
         in1 = null;
         try {
            in1 = new InputStreamReader( Util.openAnyStream( context.getOutputPath()+"/properties") , "UTF-8");
            localProp.load(in1);

            String dLocal = localProp.getProperty(Constante.KEY_HIPS_RELEASE_DATE);
            String dRemote = prop.getProperty(Constante.KEY_HIPS_RELEASE_DATE);
            if( dLocal!=null && dRemote!=null && dLocal.equals(dRemote) ) {
               dateRelease=dLocal;
               flagIsUpToDate=true && !isSmaller && !isPartial;
//               throw new Exception("Local copy already up-to-date ("+dLocal+") => "+context.getOutputPath());
            }

            // IL FAUDRAIT VERIFIER ICI QUE
            //   1) SI LE MOC LOCAL COUVRE UNE ZONE EN DEHORS DU MODE DISTANT
            //   2) SI L'ORDER LOCAL EST PLUS GRAND QUE L'ORDER DISTANT
            //   3) SI LES TYPES DE TUILES EN LOCAL NE SONT PLUS DISTRIBUES PAR LE SITE DISTANT
            // => ALORS IL FAUDRAIT FAIRE LE MENAGE EN LOCAL, OU FORCER UN CLEAN LOCAL AVANT

            isUpdate=true;
            context.info("Updating a previous HiPS copy ["+context.getOutputPath()+"]...");

         } finally{ if( in1!=null ) in1.close(); }
      }

      validateSplit(prop);
      
      // Le Mode n'est pas param�trable par l'utilisateur
      // KEEPTILE Obligatoire pour pouvoir tester les branches plus courtes dans le cas d'un catalogue
      // => cf findLeaf(...)
      context.setMode( flagCat ? Mode.KEEPTILE : Mode.REPLACETILE );
   }
   
   // R�cup�ration des param�tres pour effectuer un split multi-partitions
   // puis appel � la m�thode de g�n�ration des liens  qu'l faut.
   private void validateSplit(MyProperties prop) throws Exception {
      String splitCmd = context.getSplit();
      if( splitCmd==null ) return;
      
      int bitpix=0, tileWidth=0, depth, order;
      SMoc m;
      
      
      try { 
         if( !flagCat ) {
            bitpix       = Integer.parseInt( prop.getProperty(Constante.KEY_HIPS_PIXEL_BITPIX) );
            tileWidth    = Integer.parseInt( prop.getProperty(Constante.KEY_HIPS_TILE_WIDTH) );
         } 
         order        = Integer.parseInt( prop.getProperty(Constante.KEY_HIPS_ORDER) );
         try { depth  = Integer.parseInt( prop.getProperty(Constante.KEY_CUBE_DEPTH) ); }
         catch( Exception e1 ) { depth = 1; }
      } catch( Exception e ) { throw new Exception("Missing info in properties file => splitting action not possible"); }
      
      m = context.moc!=null ? context.moc.clone() : context.mocIndex.clone();
      if( m==null ) throw new Exception("No MOC available => splitting action not possible");

      validateSplit( context.getOutputPath(), splitCmd, m, order, bitpix, tileWidth, depth, context.getTileFormat() );
   }
   
   public void run() throws Exception {
      if( flagIsUpToDate ) {
         context.info("Local HiPS copy seems to be already up-to-date (same hips_release_date="+dateRelease+")");
         context.info("Only the properties file will be updated");
      } else {
         build();

         if( !context.isTaskAborting() ) {
            setDisplayWarn(false);
            setMaxtry(2);

            copyX(context.getInputPath()+"/index.html",context.getOutputPath()+"/index.html");
            copyX(context.getInputPath()+"/preview.jpg",context.getOutputPath()+"/preview.jpg");
            copyX(context.getInputPath()+"/metadata.xml",context.getOutputPath()+"/metadata.xml");
            
            // M�morisation des param�tres de la commande MIRROR
            if( context.scriptCommand!=null ) {
               int n=0;
               while( prop.getProperty("hipsgen_params"+(n==0?"":"_"+n))!=null) n++;
               prop.add("hipsgen_date"+(n==0?"":"_"+n),context.getNow());
               prop.add("hipsgen_params"+(n==0?"":"_"+n),context.scriptCommand);
            }
            
            // On recopie simplement le MOC, sauf si copie partielle, ou erreur
            // et dans ce cas, on le recalcule.
            try {
               if( isSmaller ) throw new Exception();
               copy(context.getInputPath()+"/Moc.fits",context.getOutputPath()+"/Moc.fits");
            } catch( Exception e ) {
               (b=new BuilderMoc(context)).run(); b=null;
            }
            copyAllsky();

            //  regeneration de la hierarchie si n�cessaire
            if( isSmaller ) {
               b = new BuilderTree(context);
               b.run();
               b=null;
            }
         }

         // Nettoyage des vieilles tuiles (non remise � jour)
         // CA NE VA PAS MARCHER CAR CERTAINES TUILES PEUVENT NE PAS AVOIR ETE MIS A JOUR SUR LE SERVEUR DISTANT
         // BIEN QUE LA RELEASE DATE AIT EVOLUEE. ELLES SERONT ALORS SUPPRIMEES PAR ERREUR

         //      if( isUpdate && !context.isTaskAborting()) {
         //         b=new BuilderCleanDate(context);
         //         ((BuilderCleanDate)b).setDate(lastReleaseDate);
         //         b.run();
         //         b=null;
         //      }
      }

      // Maj des properties
      if( !context.isTaskAborting() ) {

         prop.remove(Constante.KEY_HIPS_SERVICE_URL);
         prop.remove(Constante.KEY_MOC_ACCESS_URL);
         
         // ON DEVRAIT FAIRE CELA PLUS FINEMENT DANS LE CAS OU L'ON LAISSE DE COTE TOUT UN FORMAT
         // MAIS BON...
         if( isPartial ) {
            prop.remove(Constante.KEY_HIPS_ESTSIZE);
            prop.remove(Constante.KEY_HIPS_NB_TILES);
            prop.remove(Constante.KEY_HIPS_CHECK_CODE);
         }

         double skyFraction = context.moc.getCoverage();
         prop.replaceValue(Constante.KEY_MOC_SKY_FRACTION, Util.myRound( skyFraction ) );

         prop.replaceValue(Constante.KEY_HIPS_TILE_FORMAT, context.getTileFormat() );

         String status = prop.getProperty(Constante.KEY_HIPS_STATUS);
         StringBuilder status1;
         if( status==null ) status1 = new StringBuilder(Constante.PUBLIC+" "+Constante.MIRROR+" "+Constante.CLONABLEONCE);
         else {
            Tok tok = new Tok(status);
            status1 = new StringBuilder();
            while( tok.hasMoreTokens() ) {
               String s = tok.nextToken();
               if( s.equals(Constante.MASTER)) s= isPartial ? Constante.PARTIAL : Constante.MIRROR;
               if( s.equals(Constante.CLONABLEONCE) ) s=Constante.UNCLONABLE;
               if( status1.length()>0 ) status1.append(' ');
               status1.append(s);
            }
         }
         prop.replaceValue(Constante.KEY_HIPS_STATUS, status1.toString());
         prop.replaceValue(Constante.KEY_HIPS_ORDER, context.getOrder()+"");
         
         OutputStreamWriter out = null;
         try {
            out = new OutputStreamWriter( new FileOutputStream( context.getOutputPath()+"/properties"), "UTF-8");
            prop.store( out, null);
         } finally {  if( out!=null ) out.close(); }
      }
      
      

   }

   private int statNbFile=0;
   private long statCumul=0L;
   private long lastCumul=0L;
   private long lastTime=0L;
   private long timeIPArray[];
   private int timeIPindex=0;
   private final int MAXTIMEIP=50;
   
   private final int MAXMESURE=3;
   private int nbMesure=0;
   private long mesure[] = new long[ MAXMESURE ];
   boolean acceleration = true;    // true= acc�l�ration, false = d�cc�l�ration
   private long lastMesure=0L;

   /** Demande d'affichage des stats via Task() */
   public void showStatistics() {
      if( flagIsUpToDate ) return;
      long t = System.currentTimeMillis();
      long delai = t-lastTime;
      long lastCumulPerSec = delai>1000L && lastTime>0 ? lastCumul/(delai/1000L) : 0L;
      lastTime=t;
      lastCumul=0;
      long lastTimeIP=0L;
      if( statNbFile>=MAXTIMEIP ) {
         t=0L;
         for( long a : timeIPArray ) t+=a;
         lastTimeIP = t/MAXTIMEIP;
      }
      
      int nbThreads = getNbThreads();
      int statNbThreadRunning = getNbThreadRunning();
      
      int maxThreads = (lastTimeIP==0?20:64);
      int max = context.getMaxNbThread();
      if( max!=-1 && maxThreads>max ) maxThreads=max;
      int minThreads=16;
      
      // Ajustement du nombre de threads pour optimiser le d�bit
      if( !isLocal && statNbFile>1 && nbThreads>=0 && maxThreads>minThreads ) {
         
         // PLUS ou MOINS de threads ?
         // 1) on m�rorise le d�bit instantann�
         // 2) on change le nombre de threads en fonction du mode courant
         // 3) on mesure le nouveau d�bit instantann� sur une p�riode de temps suffisante longue
         // 4) si moins bien qu'avant on inverse le mode
         // 5) en bout de cours (max ou min) on inverse le mode
         
         if( nbMesure<MAXMESURE ) mesure[ nbMesure++ ] = lastCumulPerSec;
         else {
            long moyenne=0L;
            for( long m : mesure ) moyenne += m/nbMesure;
            if( lastMesure!=0 && moyenne<lastMesure ) acceleration = !acceleration;   // Changement de sens
            try {
               if( context.getVerbose()>=3 ) context.info("MODE "+(acceleration?"acceleration":"deceleration")
                     +" lastMeasure="+Util.getUnitDisk(lastMesure)+" newMeasure="+Util.getUnitDisk(moyenne));
               if( acceleration ) {
                  if( nbThreads<maxThreads ) addThreadBuilderHpx( nbThreads+4<=maxThreads? 4 : maxThreads-nbThreads);
               } else {
                  if( nbThreads>minThreads ) removeThreadBuilderHpx(2);
               }
            } catch( Exception e) { e.printStackTrace(); }
            
            // Si on est au max (resp. au min) on change de sens
            if( nbThreads<=minThreads ) acceleration=true;
            if( nbThreads>=maxThreads ) acceleration=false;
            
            lastMesure=moyenne;
            nbMesure=0;
         }

      }
      
      context.showMirrorStat(statNbFile, statCumul, lastCumulPerSec, totalTime, nbThreads,statNbThreadRunning, lastTimeIP);
   }

   public void build() throws Exception {
      bidon = new Fits();
      initStat();
      super.build();
   }

   protected Fits createLeaveHpx(ThreadBuilderTile hpx, String file,String path,int order,long npix, int z) throws Exception {
    return createLeaveHpx(hpx,file,path,order,npix,z,true);
 }

// TEST POUR COPIE HIPSCAT
//   protected Fits createLeaveHpx(ThreadBuilderTile hpx, String file,String path,int order,long npix, int z) throws Exception {
//    if( !isSmaller ) return bidon;  
//    return createLeaveHpx(hpx,file,path,order,npix,z,true);
// }

   private Fits createLeaveHpx(ThreadBuilderTile hpx, String file,String path,int order,long npix, int z,boolean stat) throws Exception {
      String fileInX = context.getInputPath()+"/"+cds.tools.pixtools.Util.getFilePath(order,npix,z);

      try {
         long size=0L;
         for( String ext : context.tileFormat ) {
            String fileIn = fileInX+ext;
            String fileOut = file+ext;
            size+=copy(hpx,order,fileIn,fileOut);
         }
         if( stat ) updateStat(size,timeIP);
      } catch( Exception e ) {
         e.printStackTrace();
         context.taskAbort();
      }
      return bidon;
   }

   // Copie des Allsky
   private void copyAllsky() throws Exception {
      for( int o = context.getMinOrder(); o<=3; o++ ) {
         for( int z=0; z<context.depth; z++) {
            for( String ext : context.tileFormat ) {
               String suf = z==0 ? "" : "_"+z;
               String fileIn = context.getInputPath()+"/Norder"+o+"/Allsky"+suf+ext;
               String fileOut = context.getOutputPath()+"/Norder"+o+"/Allsky"+suf+ext;
               copyX(fileIn,fileOut);
            }
         }
      }
   }
   
// TEST POUR COPIE HIPSCAT
//   /** Dans le cas d'une copie, mirroir on profite de ce test pour copier la tuile
//    * Et dans le cas d'un Hips catalogue, on d�termine si on doit aller vraiment plus profond
//    * en fonction du commentaire de compl�tude en premi�re ligne de la tuile
//    * @return ATTENTION retourn null si on doit aller plus profond
//    */
//   protected Fits findLeaf(ThreadBuilderTile hpx, String file, String path,int order,long npix, int z) throws Exception { 
//      if( isSmaller ) return bidon;
//      try {
//         createLeaveHpx(hpx,file,path,order,npix,z,order==ordermax);
//         if( flagCat && order<ordermax && stopCompleteness(file) ) return bidon; 
//      } catch( Exception e ) {
//         e.printStackTrace();
//      }
//      return null;
//   }
//   
//   public Fits findLeaf(String file) { return null; }
//   
//   // V�rifie la compl�tude de la tuile catalogue
//   private boolean stopCompleteness(String file) throws Exception {
//      boolean stop=false;
//      String ext = context.tileFormat.get(0);
//      String fileOut = file+ext;
//      if( !(new File(fileOut)).exists() ) {
//         stop=true;
//      } else {
//         RandomAccessFile f = null;
//         byte [] buf=new byte[128];
//         try {
//            f = new RandomAccessFile(fileOut, "r");
//            f.read(buf);
//            stop = testLast(buf);
//            f.close();
//            f=null;
//         } finally { 
//            if( f!=null ) try{ f.close(); } catch( Exception e) {} 
//         }
//      }
//      if( stop ) {
//         System.err.println("Stop tree for "+file);
//      }
//      return stop;
//   }
//         
//   static final private char [] COMPLETENESS = { '#',' ','C','o','m','p','l','e','t','e','n','e','s','s',' ','=',' ' };
//
//   // V�rifie la compl�tude de la tuile catalogue (les 128 premiers caract�res)
//   // ex: "# Completeness = 903 / 90811"  => Return false
//   private boolean testLast(byte [] stream) {
//      boolean rep[];  // [0] test achev� true|false, [1] r�sultat du test
//      
//      // En d�but de fichier
//      rep = testLast(stream,0,COMPLETENESS);
//
//      // Parmi des commentaires ?
//      if( !rep[0] ) {
//         for( int i=1; !rep[0] && i<stream.length-1; i++) {
//            if( stream[i]=='\n' || stream[i]=='\r' ) {  
//               if( stream[i+1]=='#' ) {
//                  rep=testLast(stream,i+1,COMPLETENESS);    
//               }
//               else if( stream[i+1]!='\n' && stream[i+1]!='\r' ) break;  // fin des commentaires ?
//            }
//         }
//      }
//      return rep[1];
//   }
//
//   // Scanne � partir de l'offset
//   private boolean[] testLast(byte [] stream,int offset, char [] signature) {
//      boolean last=false;
//      
//      if( stream.length<signature.length ) return new boolean[] {false,false};
//      for( int i=offset; i<signature.length; i++ ) {
//         if( signature[i]!=stream[i] ) return new boolean[] {false,false};
//      }
//      int deb=offset+signature.length;
//      int fin;
//      int slash=0;
//      for( fin=offset+signature.length; fin<stream.length 
//            && stream[fin]!='\n' && stream[fin]!='\r'; fin++ ) {
//         if( stream[fin]=='/' ) slash=fin;
//      }
//      if( slash==0 ) return new boolean[] {false,false};
//      if( fin==stream.length ) return new boolean[] {false,false};
//      try {
//         String a = new String(stream,deb,slash-deb);
//         String b = new String(stream,slash+1,fin-(slash+1));
//         int nLoaded = Integer.parseInt(a);
//         int nTotal = Integer.parseInt(b);
//         last = nLoaded==nTotal;
//      } catch( Exception e ) { last=false; }
//      return new boolean[] {true,last};
//   }


   // Copie d'un fichier distant (url) vers un fichier local sans g�n�rer d'exception
   private int copyX(String fileIn, String fileOut) throws Exception {
      try { return copy(fileIn,fileOut); } catch( Exception e) {};
      return 0;
   }

   private int copy(String fileIn, String fileOut) throws Exception { return copy(null,-1,fileIn,fileOut); }
   private int copy(ThreadBuilderTile hpx, int order,String fileIn, String fileOut) throws Exception {
      try {
         if( isLocal ) return copyLocal(fileIn,fileOut);
         return copyRemote(hpx,fileIn,fileOut);
         
      } catch( FileNotFoundException e ) {
//         if( order>=3 ) context.warning("File not found ["+fileIn+"] => ignored (may be out of the MOC)");
      }
      return 0;
   }
   
//   static public void main(String [] s) {
//      try {
//         String fileIn="http://alasky.u-strasbg.fr/SDSS/DR9/color/Norder10/Dir20000/Npix28115.jpg";
////         String fileIn="http://alasky.u-strasbg.fr/SDSS/DR9/color/Norder10/Dir20000/Npix28124.jpg";
//         String fileOut="/Users/Pierre/Desktop/toto.jpg";
//         int size = copyRemote(fileIn,fileOut);
//         System.out.println("copy done => "+size);
//      } catch( Exception e ) {
//         
//         e.printStackTrace();
//      }
//      
//   }

   
   class TimeOut extends Thread {
      HttpURLConnection con;
      long lastSize=0;
      long size=0;
      boolean encore=true;
      int timeout;
      String file;

      TimeOut(HttpURLConnection con,String file, int timeout) { this.timeout=timeout; this.file=file; this.con = con; }

      void end() { encore=false; this.interrupt(); }

      private long getBytes() { return size; }
      void setSize(long size) { this.size=size; }

      public void run() {
         while( encore ) {
            try { Thread.sleep(timeout); } catch (InterruptedException e) { }
            if( encore ) {
               long size = getBytes();
               
               // Rien lu depuis la derni�re fois ?
               if( size==lastSize ) {
                  con.disconnect();
                  encore=false;
               }
               lastSize=size;
            }
         }
      }
   }
   
   private int maxtry = 10;      // Nombre max de r�initialisations possibles avant erreur d�finitive
   private void setMaxtry(int n) { maxtry=n; }
   private int getMaxtry() { return maxtry; }
   
   private boolean displayWarn = true;  // true pour affichage des messages verbeux sur les essais de lecture
   private void setDisplayWarn(boolean flag) { displayWarn=flag; }
   private boolean getDisplayWarn() { return displayWarn; }
   
   // Copie d'un fichier distant (url) vers un fichier local, uniquement si la copie locale �venutelle
   // et plus ancienne et/ou de taille diff�rente � l'originale.
   // V�rifie si possible que la taille du fichier copi� est correct.
   // Effectue 3 tentatives cons�cutives avant d'abandonner
   private int copyRemote(ThreadBuilderTile hpx, String fileIn, String fileOut) throws Exception {
      File fOut=null;
      long lastModified=0L;
      int size=0;
      int sizeRead=0;
      int n;
      long len;
      byte [] buf = new byte[512];
      int maxtry = getMaxtry();

      // Laisse-t-on souffler un peu le serveur HTTP ?
      try {
         if( context.mirrorDelay>0 ) Thread.currentThread().wait(context.mirrorDelay);
      } catch( Exception e ) { }

      for( int i=0; i<maxtry; i++ ) {
         InputStream dis=null;
         RandomAccessFile f = null;
         TimeOut timeout = null;
         HttpURLConnection httpc=null;
         
         try {
            lastModified=-1;

            URL u = new URL(fileIn);
            fOut = new File(fileOut);
            
//            if( i>0 ) context.warning("Reopen connection for "+fileIn+" ...");
   
            // Si on a d�j� la tuile, on v�rifie qu'elle est � jour
            if( fOut.exists() && (len=fOut.length())>0 ) {
               
               // reprise => pas de v�rif date&size des tuiles d�j� arriv�es
               // On garde un vieux doute sur les fichiers vraiments petits
               // ON POURRAIT VERIFIER QUE LE FICHIER N'EST PAS TRONQUE EN CHARGEANT LA TUILE SANS ERREUR MAIS CA VA PRENDRE DES PLOMBES...
               if( !check ) {
                  
                  // Des heuristiques simples pour ne pas recopier des tuiles
                  // visiblement d�j� copi�es et bonnes.
                  if( fileOut.endsWith(".fits") ) {
                     
                     // si assez grand, peut �tre d�j� bon
                     if( len>2048L ) {
                        MyInputStream in = null;
                        try { 
                           in=new MyInputStream( new FileInputStream( fOut ) );
                           
                           // C'est pas du GZ et c'est assez grand => on estime que c'est bon
                           if( !in.isGZ() ) { in.close(); in=null; return 0; }
                        } catch( Exception e ) {
                        } finally { if( in!=null ) in.close(); }
                     }
                     
                  // assez grand pour du non fits =>  on estime que c'est bon
                  } else if(  len>1024L )  return 0;  
               }
               
               httpc = (HttpURLConnection)u.openConnection();
               timeout = new TimeOut(httpc,fileIn,TIMEOUT);
               timeout.start();
               httpc.setReadTimeout(TIMEOUT-500);
               httpc.setConnectTimeout(TIMEOUT-500);
               httpc.setRequestProperty("User-Agent", USERAGENT);
               httpc.setRequestMethod("HEAD");
               lastModified = httpc.getLastModified();
               size = httpc.getContentLength();
               if( size==fOut.length() && lastModified<=fOut.lastModified() ) {

                  // On doit tout de m�me vider le buffer
                  dis = httpc.getInputStream();
                  while( (n=dis.read(buf)) > 0) { sizeRead+=n; }
                  dis.close();  
                  dis=null;
                  return 256; // sizeRead;  // d�j� fait  (� la louche la taille du HEAD http)
               }
            }

            long t0 = System.currentTimeMillis();
            httpc = (HttpURLConnection)u.openConnection();
            timeout = new TimeOut(httpc,fileIn,TIMEOUT);
            timeout.start();
            httpc.setReadTimeout(TIMEOUT-500);
            httpc.setConnectTimeout(TIMEOUT-500);
            httpc.setRequestProperty("User-Agent", USERAGENT);
            httpc.setRequestMethod("GET");
            if( lastModified==-1 ) lastModified = httpc.getLastModified();
//            if( hpx!=null ) hpx.threadBuilder.setInfo("copyRemote opening inputstream from  "+fileIn+"...");
            dis = httpc.getInputStream();
            long t1 = System.currentTimeMillis();
            timeIP=t1-t0;
            
            Util.createPath(fileOut);
            f = new RandomAccessFile(fileOut, "rw");
            while( (n=dis.read(buf))>0 ) {
               sizeRead+=n;
               timeout.setSize(sizeRead);
//               if( hpx!=null ) hpx.threadBuilder.setInfo("loading try:"+i+" "+n+" bytes from tile "+fileIn+"...");
               f.write(buf,0,n);
            }
            timeout.end(); timeout=null;
            dis.close(); dis=null;
            f.close(); f=null;

         } catch( Exception e ) {
            if( e instanceof FileNotFoundException ) throw e;

//            e.printStackTrace();
            fOut.delete();
            if( i<maxtry-1 ) {
               if( getDisplayWarn() ) context.warning("File copy error  => try again ("+(i+1)+"x) ["+fileIn+"]");
               if( i==maxtry/2 ) Util.pause(10000);
            }
            else throw new Exception("File copy error (try "+maxtry+"x) ["+fileIn+"]");
            continue;
            
         } finally {
            if( timeout!=null ) timeout.end();
            if( dis!=null ) try{ dis.close(); } catch( Exception e) {} 
            if( f!=null ) try{ f.close(); } catch( Exception e) {}
         }

         if( lastModified!=0L ) fOut.setLastModified(lastModified);

         if( sizeRead>0 && (new File(fileOut)).length()<size) {
            if( i==maxtry-1 ) throw new Exception("Truncated file copy ["+fileIn+"]");
            if( getDisplayWarn() ) context.warning("Truncated file copy => try again ["+fileIn+"]");
         }
         else break;  // a priori c'est bon
      }

      return sizeRead;

   }
   
   // Copie d'un fichier local (path) vers un fichier local, uniquement si la copie �venutelle
   // et plus ancienne et/ou de taille diff�rente � l'originale.
   public static int copyLocal(String fileIn, String fileOut) throws Exception {
      File fOut,fIn;
      long lastModified;
      long size=-1;
      int n;

      fIn = new File(fileIn);
      lastModified = fIn.lastModified();
      fOut = new File(fileOut);

      // Si m�me taille et date ant�rieure ou �gale => d�j� fait
      if( fOut.exists() && fOut.length()>0 ) {
         size = fIn.length();
         if( size==fOut.length() && lastModified<=fOut.lastModified() ) {
            return 0;  // d�j� fait
         }
      }

      // Copie par bloc
      size=0L;
      RandomAccessFile f = null;
      RandomAccessFile g = null;
      byte [] buf=new byte[512];
      try {
         Util.createPath(fileOut);
         f = new RandomAccessFile(fileOut, "rw");
         g = new RandomAccessFile(fileIn, "r");
         while( (n=g.read(buf))>0 ) { f.write(buf,0,n); size+=n; }
         f.close(); f=null;
         g.close(); g=null;
      } finally { 
         if( f!=null ) try{ f.close(); } catch( Exception e) {} 
         if( g!=null ) try{ g.close(); } catch( Exception e) {} 
      }
      fOut.setLastModified(lastModified);

      return (int)size;
   }
   
   boolean oneWaiting() {
      try {
         Iterator<ThreadBuilder> it = threadList.iterator();
         while( it.hasNext() ) if( it.next().isWaitingAndUsable(false) ) return true;
      } catch( Exception e ) { }
      return false;
   }

// TEST POUR COPIE HIPSCAT   
//   protected Fits createNodeHpx(String file,String path,int order,long npix,Fits fils[], int z) throws Exception {
////      if( !isSmaller ) return createLeaveHpx(null,file,path,order,npix,z,false);
//      return bidon;
//   }
   
   // Dans le cas d'un mirroir complet, on copie �galement les noeuds. En revanche pour un miroir partiel
   // on reg�n�rera l'arborescence � la fin
   protected Fits createNodeHpx(String file,String path,int order,long npix,Fits fils[], int z) throws Exception {
      if( !isSmaller ) return createLeaveHpx(null,file,path,order,npix,z,false);
      return bidon;
   }

   /** Recherche et chargement d'un losange d�j� calcul�
    *  Retourne null si non trouv�
    * @param file Nom du fichier ( sans extension)
    */
   public Fits findLeaf(String file) throws Exception { return null; }

   private void initStat() {
      statNbFile=0;
      statCumul=0L;
      startTime = System.currentTimeMillis();
      timeIPArray = new long[MAXTIMEIP ];
      timeIPindex=0;
   }

   // Mise � jour des stats
   private void updateStat(long size,long timeIP) {
      statNbFile++;
      lastCumul+=size;
      statCumul+=size;
      totalTime = System.currentTimeMillis()-startTime;
      try {
         timeIPArray[timeIPindex++]=timeIP;
      } catch( Exception e ) { }
      if( timeIPindex>=MAXTIMEIP ) timeIPindex=0;
      
   }
}
