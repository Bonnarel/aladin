// Copyright 2012 - UDS/CNRS
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

package cds.allsky;


import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import cds.aladin.Aladin;
import cds.aladin.Calib;
import cds.aladin.Coord;
import cds.aladin.Localisation;
import cds.fits.Fits;
import cds.tools.pixtools.CDSHealpix;
import cds.tools.pixtools.Util;

/** Permet la g�n�ration de l'index HEALPix
 * Rq : le MOC de l'index sera �galement g�n�r� � l'issue de la g�n�ration de l'index
 * @author Standard Ana�s Oberto [CDS] & Pierre Fernique [CDS]
 *
 */
public class BuilderIndex extends Builder {

   private int [] borderSize= {0,0,0,0};
   private int radius = 0;
   private String currentfile = null;
   private boolean blocking;

   // Pour les stat
   private int statNbFile;                 // Nombre de fichiers sources
   private int statNbZipFile;              // Nombre de fichiers sources gzipp�s
   private int statBlocFile;              // Nombre de fichiers qu'il aura fallu d�couper en blocs
   private long statMemFile;               // Taille totale des fichiers sources (en octets)
   private long statPixSize;               // Nombre total de pixels
   private long statMaxSize;               // taille du plus gros fichier trouv�
   private long statTime;                  // Date de d�but
   private int statMaxWidth, statMaxHeight, statMaxNbyte; // info sur le plus gros fichier trouv�

   boolean stopped = false;

   public BuilderIndex(Context context) { super(context); }
   
   public Action getAction() { return Action.INDEX; }

   public void run() throws Exception {
      build();

      BuilderMocIndex builderMocIndex = new BuilderMocIndex(context);
      builderMocIndex.run();
      context.setMocIndex( builderMocIndex.getMoc() ) ;
   }
   
   public boolean isAlreadyDone() {
      if( !context.isExistingIndexDir() ) return false;
      if( !context.actionAlreadyDone(Action.INDEX)) return false;
      if( context.getMocIndex()==null ) {
         try { context.loadMocIndex(); } catch( Exception e ) { return false; }
      }
      context.info("Pre-existing HEALPix index seems to be ready");
      return true;
   }
   
   public void validateContext() throws Exception { 
      if( context instanceof ContextGui ) {
         context.setProgressBar( ((ContextGui)context).mainPanel.getProgressBarIndex() );
      }
      
      blocking = context.cutting;
      if( blocking ) context.info("Splitting large original image files in blocs of "+Constante.FITSCELLSIZE+"x"+Constante.FITSCELLSIZE+" pixels");

      validateInput();
      validateOutput();
      
      // Tests order
      int order = context.getOrder();
      if( order==-1 ) {
         String img = context.getImgEtalon();
         if( img==null ) {
            img = context.justFindImgEtalon( context.getInputPath() );
            context.info("Use this reference image => "+img);
         }
         if( img==null ) throw new Exception("No source image found in "+context.getInputPath());
         try {
            Fits file = new Fits();
            file.loadHeaderFITS(img);
            long nside = calculateNSide(file.getCalib().GetResol()[0] * 3600.);
            order = ((int) Util.order((int) nside) - Constante.ORDER);
            context.setOrder(order);
         } catch (Exception e) {
            context.warning("The reference image has no astrometrical calibration ["+img+"] => order can not be computed");
         }
      }
      if( order==-1 ) throw new Exception("Argument \"order\" is required");
      else if( order<context.getOrder() ) {
         context.warning("The provided order ["+order+"] is less than the optimal order ["+context.getOrder()+"] => OVER-sample will be applied");
      } else if( order>context.getOrder() ) {
         context.warning("The provided order ["+order+"] is greater than the optimal order ["+context.getOrder()+"] => SUB-sample will be applied");
      }
      
   }
   
   static public int calculateNSide(double pixsize) {
      double arcsec2rad=Math.PI/(180.*60.*60.);
      double nsd = Math.sqrt(4*Math.PI/12.)/(arcsec2rad*pixsize);
      int order_req=Math.max(0,Math.min(29,1+(int)CDSHealpix.log2((long)(nsd))));
      return 1<<order_req;
  }
   
   /** Demande d'affichage des stats (dans le TabBuild) */
   public void showStatistics() {
      long statDuree = System.currentTimeMillis()-statTime;
      context.showIndexStat(statNbFile, statBlocFile, statNbZipFile, statMemFile, statPixSize, statMaxSize,
            statMaxWidth, statMaxHeight, statMaxNbyte,statDuree);
   }


   // G�n�ration de l'index
   private boolean build() throws Exception {
      initStat();
      String input = context.getInputPath();
      String output = context.getOutputPath();
      int order = context.getOrder();
      borderSize = context.getBorderSize();
      radius = context.circle;

      File f = new File(output);
      if (!f.exists()) f.mkdir();
      String pathDest = context.getHpxFinderPath();
      
      create(input, pathDest, order);
      return true;
   }

   // Initialisation des statistiques
   private void initStat() {
      statNbFile = statNbZipFile = statBlocFile = 0;
      statPixSize=statMemFile = 0;
      statMaxSize = -1;
      statTime = System.currentTimeMillis();
   }

   // Mise � jour des stats
   private void updateStat(File f,int code, int width,int height,int nbyte,int deltaBlocFile) {
      statNbFile++;
      statBlocFile += deltaBlocFile;
      if( (code & Fits.GZIP) !=0 ) statNbZipFile++;
      long size = f.length();
      statPixSize += width*height;
      statMemFile += size;
      if( statMaxSize<size ) {
         statMaxSize=size;
         statMaxWidth = width;
         statMaxHeight = height;
         statMaxNbyte = nbyte;
      }
   }

   // Cr�ation si n�cessaire du fichier pass� en param�tre et ouverture en �criture
   private FileOutputStream openFile(String filename) throws Exception {
      File f = new File( filename/*.replaceAll(FS+FS, FS)*/ );
      if( !f.exists() ) {
         cds.tools.Util.createPath(filename);
         return new FileOutputStream(f);
      }
      return new FileOutputStream(f, true);
   }

   // Insertion d'un nouveau fichier d'origine dans la tuile d'index rep�r�e par out
   private void createAFile(FileOutputStream out, String filename, String stc)
   throws IOException {
      int o1 = filename.lastIndexOf('/');
      int o1b = filename.lastIndexOf('\\');
      if( o1b>o1 ) o1=o1b;
      int o2 = filename.indexOf('.',o1);
      if( o2==-1 ) o2 = filename.length();
      String name = filename.substring(o1+1,o2);
      
      DataOutputStream dataoutputstream = null;
      try {
         dataoutputstream = new DataOutputStream(out);
         dataoutputstream.writeBytes("{ \"name\": \""+name+"\", \"path\": \""+filename+"\", \"stc\": \""+stc+"\" }\n");
         dataoutputstream.flush();
      } finally { if( dataoutputstream!=null ) dataoutputstream.close(); }
   }

   // Pour chaque fichiers FITS, cherche la liste des losanges couvrant la
   // zone. Cr�� (ou compl�te) un fichier texte "d'index" contenant le chemin vers
   // les fichiers FITS
   private void create(String pathSource, String pathDest, int order) throws Exception {
      
      // pour chaque fichier dans le sous r�pertoire
      File main = new File(pathSource);

      ArrayList<File> dir = new ArrayList<File>();
      File[] list = main.listFiles();
      if (list == null) return;
      
      int i=0;
      context.setProgress(0,list.length-1);
      for( File file : list ) {
         if( context.isTaskAborting() ) throw new Exception("Task abort !");
         context.setProgress(i++);
         if( file.isDirectory() ) { dir.add(file); continue; }
         currentfile = file.getPath();

         Fits fitsfile = new Fits();
         int cellSize = Constante.FITSCELLSIZE;

         // L'image sera mosaiqu�e en cellSize x cellSize pour �viter de
         // saturer la m�moire par la suite
         try {
            int code = fitsfile.loadHeaderFITS(currentfile);

            try {

               // Test sur l'image enti�re
               if( !blocking /* || fitsfile.width*fitsfile.height<=4*Constante.FITSCELLSIZE*Constante.FITSCELLSIZE */ ) {
                  updateStat(file, code, fitsfile.width, fitsfile.height, fitsfile.bitpix==0 ? 4 : Math.abs(fitsfile.bitpix) / 8, 0);
                  testAndInsert(fitsfile, pathDest, currentfile, null, order);

                  // D�coupage en petits carr�s
               } else {   
                  //                     context.info("Scanning by cells "+cellSize+"x"+cellSize+"...");
                  int width = fitsfile.width - borderSize[3];
                  int height = fitsfile.height - borderSize[2];

                  updateStat(file, code, width, height, fitsfile.bitpix==0 ? 4 : Math.abs(fitsfile.bitpix) / 8, 1);

                  for( int x=borderSize[1]; x<width; x+=cellSize ) {

                     for( int y=borderSize[0]; y<height; y+=cellSize ) {
                        fitsfile.widthCell = x + cellSize > width ? width - x : cellSize;
                        fitsfile.heightCell = y + cellSize > height ? height - y : cellSize;
                        fitsfile.xCell=x;
                        fitsfile.yCell=y;
                        String currentCell = fitsfile.getCellSuffix();
                        testAndInsert(fitsfile, pathDest, currentfile, currentCell, order);
                     }
                  }
               }
            } catch (Exception e) {
               if( Aladin.levelTrace>=3 ) e.printStackTrace();
               return;
            }
         }  catch (Exception e) {
            Aladin.trace(3,e.getMessage() + " " + currentfile);
            continue;
         }
      }

      list=null;
      if( dir.size()>0 ) {
         for( File f1 : dir ) {
            if( !f1.isDirectory() ) continue;
            currentfile = f1.getPath();
//            System.out.println("Look into dir " + currentfile);
            try {
               create(currentfile, pathDest, order);
            } catch( Exception e ) {
               Aladin.trace(3,e.getMessage() + " " + currentfile);
               continue;
            }
         }
      }
   }

   private void testAndInsert(Fits fitsfile, String pathDest, String currentFile, String currentCell, int order) throws Exception {
      String hpxname;
      FileOutputStream out;
      
      try {
         // Recherche les 4 coins de l'image (cellule)
         Calib c = fitsfile.getCalib();
         ArrayList<double[]> cooList = new ArrayList<double[]>(4);
         Coord coo = new Coord();
         StringBuffer stc = new StringBuffer("POLYGON J2000");
         boolean hasCell = fitsfile.hasCell();
         for( int i=0; i<4; i++ ) {
            coo.x = (i==0 || i==3 ? fitsfile.xCell : fitsfile.xCell +fitsfile.widthCell);
            coo.y = (i<2 ? fitsfile.yCell : fitsfile.yCell+fitsfile.heightCell);
            if( !Fits.JPEGORDERCALIB || Fits.JPEGORDERCALIB && fitsfile.bitpix!=0 ) 
               coo.y = fitsfile.height - coo.y -1;
            c.GetCoord(coo);
            cooList.add( context.ICRS2galIfRequired(coo.al, coo.del) );
            
            // S'il s'agit d'une cellule, il faut �galement calcul� le STC pour l'observation compl�te
            if( hasCell ) {
               coo.x = (i==0 || i==3 ? 0 : fitsfile.width);
               coo.y = (i<2 ? 0 : fitsfile.height);
               if( !Fits.JPEGORDERCALIB || Fits.JPEGORDERCALIB && fitsfile.bitpix!=0 ) 
                  coo.y = fitsfile.height - coo.y -1;
               c.GetCoord(coo);
            }
            stc.append(" "+coo.al+" "+coo.del);
         }
         
         long [] npixs = CDSHealpix.query_polygon(CDSHealpix.pow2(order), cooList);

         // pour chacun des losanges concern�s
         for (int i = 0; i < npixs.length; i++) {
            long npix = npixs[i];

            // v�rifie la validit� du losange trouv�
            if (!isInImage(fitsfile, Util.getCorners(order, npix)))  continue;

            hpxname = cds.tools.Util.concatDir(pathDest,Util.getFilePath("", order,npix));
//            cds.tools.Util.createPath(hpxname);
            out = openFile(hpxname);

            // ajoute le chemin du fichier Source FITS, 
            // suivi �ventuellement de la d�finition de la cellule en question
            // (mode mosaic)
            String filename = currentFile + (currentCell == null ? "" : currentCell);
            
            createAFile(out, filename, stc.toString());
            out.close();
         }
      } catch( Exception e ) {
         if( Aladin.levelTrace>=3 ) e.printStackTrace();
      }

   }

   private boolean isInImage(Fits f, Coord[] corners) {
      int signeX = 0;
      int signeY = 0;
      try {
         int marge = 2;
         for (int i = 0; i < corners.length; i++) {
            Coord coo = corners[i];
            if (context.getFrame() != Localisation.ICRS) {
               double[] radec = context.gal2ICRSIfRequired(coo.al, coo.del);
               coo.al = radec[0];
               coo.del = radec[1];
            }
            f.getCalib().GetXY(coo);
            if (Double.isNaN(coo.x)) continue;
            coo.y = f.height - coo.y -1;
            int width = f.widthCell+marge;
            int height = f.heightCell+marge;
            if (coo.x >= f.xCell - marge && coo.x < f.xCell + width
                  && coo.y >= f.yCell - marge && coo.y < f.yCell + height) {
               return true;
            }
            // tous d'un cot� => x/y tous du meme signe
            signeX += (coo.x >= f.xCell + width) ? 1 : (coo.x < f.xCell - marge) ? -1 : 0;
            signeY += (coo.y >= f.yCell + height) ? 1 : (coo.y < f.yCell - marge) ? -1 : 0;

         }
      } catch (Exception e) {
         return false;
      }

      if (Math.abs(signeX) == Math.abs(corners.length) || Math.abs(signeY) == Math.abs(corners.length)) {
         return false;
      }
      return true;
   }
}
