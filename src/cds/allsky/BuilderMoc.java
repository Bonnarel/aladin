// Copyright 2012 - UDS/CNRS
// The Aladin program is distributed under the terms
// of the GNU General Public License version 3.
//
// This file is part of Aladin.
//
// Aladin is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, version 3 of the License.
//
// Aladin is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// The GNU General Public License is available in COPYING file
// along with Aladin.
//

package cds.allsky;

import static cds.tools.Util.FS;

import java.io.File;
import java.io.FileInputStream;

import cds.aladin.Localisation;
import cds.aladin.MyInputStream;
import cds.aladin.PlanHealpix;
import cds.aladin.PlanMoc;
import cds.fits.Fits;
import cds.moc.HealpixMoc;
import cds.tools.pixtools.CDSHealpix;
import cds.tools.pixtools.Util;

/** Cr�ation d'un fichier Moc.fits correspondant aux tuiles de plus bas niveau
 * @author Ana�s Oberto [CDS] & Pierre Fernique [CDS]
 */
public class BuilderMoc extends Builder {

   public static final String MOCNAME = "Moc.fits";

   protected HealpixMoc moc;
   protected int mocOrder;
   protected int fileOrder;
   protected boolean isMocHight;
   
   private String ext; // Extension � traiter, null si non encore affect�e.

   public BuilderMoc(Context context) {
      super(context);
    }
   
   public Action getAction() { return Action.MOC; }

   public void run() throws Exception {
      createMoc(context.getOutputPath());
   }
   
   public void validateContext() throws Exception { validateOutput(); }

   public HealpixMoc getMoc() { return moc; }

   /** Cr�ation d'un Moc associ� � l'arborescence trouv�e dans le r�pertoire path */
   protected void createMoc(String path) throws Exception {
      
      moc = new HealpixMoc();
      fileOrder = mocOrder = Util.getMaxOrderByPath(path);

      // dans le cas d'un survey � faible r�solution
      // ou qui couvre une petite partie du ciel, 
      boolean isLarge=true;
      try { 
         if( context.mocIndex==null ) context.loadMocIndex();
         isLarge = context.mocIndex.getCoverage()>1/6.;
      } catch( Exception e ) { }
      
      // mocOrder explicitement fourni par l'utilisateur
      if( context.getMocOrder()!=-1 ) mocOrder = context.getMocOrder();
      
      // mocOrder d�termin� par la nature du survey
      else {
         if( mocOrder<Constante.DEFAULTMOCORDER || !isLarge ) {
            mocOrder = context.getOrder()+Constante.ORDER-Constante.DIFFMOCORDER;
         }
         if( mocOrder< Constante.DEFAULTMOCORDER ) mocOrder = Constante.DEFAULTMOCORDER;
      }
      isMocHight = mocOrder>fileOrder;
      
      moc.setMocOrder(mocOrder);

      String outputFile = path + FS + MOCNAME;
      
      long t = System.currentTimeMillis();
      context.info("MOC generation ("+(isMocHight?"hight resolution":"low resolution")+" mocOrder="+moc.getMocOrder()+")...");
      moc.setCoordSys(getFrame());
      moc.setCheckConsistencyFlag(false);
      generateMoc(moc,fileOrder, path);
      moc.setCheckConsistencyFlag(true);
      moc.write(outputFile);
      
      long time = System.currentTimeMillis() - t;
      context.info("MOC done in "+cds.tools.Util.getTemps(time,true)
                        +": mocOrder="+moc.getMocOrder()
                        +" size="+cds.tools.Util.getUnitDisk( moc.getSize()));
   }
   
   
   long startTime=0L;
   int nbTiles = -1;
   
   private void initStat() {
      startTime = System.currentTimeMillis();
      nbTiles=1;
   }
   
   private void updateStat() {
      nbTiles++;
   }
   
   /** Demande d'affichage des statistiques (via Task()) */
   public void showStatistics() {
      long now = System.currentTimeMillis();
      long cTime = now-startTime;
      if( cTime<2000 ) return;
      
      context.stat(nbTiles+" tile"+(nbTiles>1?"s":"")+" scanned in "+cds.tools.Util.getTemps(cTime) );
   }
   
   /** Retourne la surface du Moc (en nombre de cellules de plus bas niveau */
   public long getUsedArea() { return moc.getUsedArea(); }

   /** Retourne le nombre de cellule de plus bas niveau pour la sph�re compl�te */
   public long getArea() { return moc.getArea(); }

   protected void generateMoc(HealpixMoc moc, int fileOrder,String path) throws Exception {
      
      initStat();
      
      ext = null;
      File f = new File(path + Util.FS + "Norder" + fileOrder );
      

      File[] sf = f.listFiles();
      if( sf==null || sf.length==0 ) throw new Exception("No tiles found !");
      for( int i = 0; i < sf.length; i++ ) {
         if( context.isTaskAborting() ) throw new Exception("Task abort !");
         if( !sf[i].isDirectory() ) continue;
         File[] sf1 = sf[i].listFiles();
         for( int j = 0; j < sf1.length; j++ ) {
            String file = sf1[j].getAbsolutePath();

            long npix = Util.getNpixFromPath(file);
            if( npix == -1 ) continue;

            // Ecarte les fichiers n'ayant pas l'extension requise
            String e = getExt(file);
            if( ext == null ) ext = e;
            else if( !ext.equals(e) ) continue;

            generateTileMoc(moc,sf1[j], fileOrder, npix);
         }
         moc.checkAndFix();
      }
   }
   
   private void generateTileMoc(HealpixMoc moc,File f,int fileOrder, long npix) throws Exception {
      updateStat();
      if( isMocHight ) generateHighTileMoc(moc,fileOrder,f,npix);
      else moc.add(fileOrder,npix);
   }
   
   private void generateHighTileMoc(HealpixMoc moc,int fileOrder, File f, long npix) throws Exception {
      Fits fits = new Fits();
      MyInputStream dis = new MyInputStream(new FileInputStream(f));
      fits.loadFITS(dis);
      dis.close();
      
      long nside = fits.width;
      long min = nside * nside * npix;
      int mocOrder = moc.getMocOrder();
      int tileOrder = (int) CDSHealpix.log2(nside);
      
      int div = (fileOrder+tileOrder - mocOrder) *2;
      context.createHealpixOrder( tileOrder );

      
      long oNpix=-1;  
      for( int y=0; y<fits.height; y++ ) {
         for( int x=0; x<fits.width; x++ ) {
            try {
               npix = min + context.xy2hpx(y * fits.width + x);
               
               npix = npix >>> div;
               
               // Juste pour �viter d'ins�rer 2x de suite le m�me npix
               if( npix==oNpix ) continue;
               
               double pixel = fits.getPixelDouble(x,y);
               
               // Pixel vide
               if( fits.isBlankPixel(pixel) ) continue;

               moc.add(mocOrder,npix);
               
               oNpix=npix;
            } catch( Exception e ) {
               e.printStackTrace();
            }
         }
      }
      
      moc.checkAndFix();
   }

   
   // Retourne l'extension du fichier pass� en param�tre, "" si aucune
   private String getExt(String file) {
      int offset = file.lastIndexOf('.');
      if( offset == -1 ) return "";
      int pos = file.indexOf(Util.FS,offset);
      if( pos!= -1 ) return "";
      return file.substring(offset + 1, file.length());
   }

}
