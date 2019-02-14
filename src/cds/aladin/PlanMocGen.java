// Copyright 1999-2018 - Universit� de Strasbourg/CNRS
// The Aladin Desktop program is developped by the Centre de Donn�es
// astronomiques de Strasbourgs (CDS).
// The Aladin Desktop program is distributed under the terms
// of the GNU General Public License version 3.
//
//This file is part of Aladin.
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
//    along with Aladin.
//

package cds.aladin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import cds.aladin.stc.STCObj;
import cds.moc.Healpix;
import cds.moc.HealpixMoc;
import cds.tools.pixtools.CDSHealpix;

/** Generation d'un plan MOC � partir d'une liste de plans (Image, Catalogue ou map HEALPix) 
 * @author P.Fernique [CDS]
 * @version 1.1 - mar 2016 - ajout probability sky map
 * @version 1.0 - nov 2012
 */
public class PlanMocGen extends PlanMoc {
   
   private Plan [] p;       // Liste des plans � ajouter dans le MOC
   private double radius;   // Pour un plan catalogue, rayon autour de chaque source (en degres), sinon 0
   private boolean fov;     // Plan un plan catalogue, true si on prend les FOVs associ�s
   private double pixMin;   // Pour un plan Image ou map Healpix, valeur minimale des pixels retenus (sinon NaN)
   private double pixMax;   // Pour un plan Image ou map Healpix, valeur maximale des pixels retenus (sinon NaN)
   private double threshold;// Pour un plan Image Healpix, seuil max de l'int�gration (sinon NaN)
   private int order;         // R�solution (ordre) demand�e
   
   private double gapPourcent;  // Pourcentage de progression par plan (100 = tout est termin�)
   
   protected PlanMocGen(Aladin aladin,String label,Plan[] p,int order,double radius,
         double pixMin,double pixMax,double threshold,boolean fov) {
      super(aladin,null,null,label,p[0].co,30);
      this.p = p;
      this.order=order;
      this.radius=radius;
      this.pixMin=pixMin;
      this.pixMax=pixMax;
      this.threshold=threshold;
      this.fov=fov;
      
      pourcent=0;
      gapPourcent = 100/p.length;
      
      suiteSpecific();
      threading();
      log();
   }
   
   protected void suite1() {}
   
   private void addMocFromCatFov(Plan p1,int order) {
      Iterator<Obj> it = p1.iterator();
      int m= p1.getCounts();
      int n=0;
      double incrPourcent = gapPourcent/m;
      while( it.hasNext() ) {
         Obj o = it.next();
         if( !(o instanceof Source) ) continue;
         if( m<100 || n%100==0 ) pourcent+=incrPourcent;
         Source s = (Source)o;
         SourceFootprint sf = s.getFootprint();
         if( sf==null ) continue;
         List<STCObj> listStcs = sf.getStcObjects();
         if( listStcs==null ) continue;
         try {
            HealpixMoc m1 = aladin.createMocRegion(listStcs,order);
            if( m1!=null ) moc.add(m1);
         } catch( Exception e ) {
            if( aladin.levelTrace>=3 ) e.printStackTrace();
         }
      }
   }
      
   // Ajout d'un plan catalogue au moc en cours de construction
   private void addMocFromCatalog(Plan p1,double radius,int order) {
      Iterator<Obj> it = p1.iterator();
      Healpix hpx = new Healpix();
      int o1 = order;
      Coord coo = new Coord();
      int n=0;
      int m= p1.getCounts();
      double incrPourcent = gapPourcent/m;
      while( it.hasNext() ) {
         Obj o = it.next();
         if( !(o instanceof Position) ) continue;
         if( m<100 || n%100==0 ) pourcent+=incrPourcent;
         try {
            coo.al = ((Position)o).raj;
            coo.del = ((Position)o).dej;
            long [] npix ;
            if( radius==0 ) npix = new long[] { hpx.ang2pix(o1, coo.al, coo.del) };
            else npix = hpx.queryDisc(o1, coo.al, coo.del, radius);
            
            for( long pix : npix ) moc.add(o1,pix);
            n+=npix.length;
            if( n>10000 ) { moc.checkAndFix(); n=0; }
         } catch( Exception e ) {
            if( aladin.levelTrace>=3 ) e.printStackTrace();
         }
      }
   }

   // Ajout d'un plan Image au MOC en cours de construction
   private void addMocFromImage(Plan p1,double pixMin,double pixMax) {
      boolean flagRange = !Double.isNaN(pixMin) || !Double.isNaN(pixMax);
      PlanImage pimg = (PlanImage)p1;
      Healpix hpx = new Healpix();
      int o1 = order;
      Coord coo = new Coord();
      double gap=1;
      double gapA=0,gapD=0;
      try { 
         gapA = Math.min(p1.projd.getPixResAlpha(),p1.projd.getPixResDelta());
         for( o1=order; CDSHealpix.pixRes( o1 )/3600. <= gapA*2; o1--);
      } catch( Exception e1 ) {
         e1.printStackTrace();
      }
//      if( gap<1 || Double.isNaN(gap) ) gap=1;
//      
      gapD = CDSHealpix.pixRes( o1 )/3600.;
//      System.out.println("res="+res+" order="+order+" gapA ="+Coord.getUnit(gapA)+" gapD ="+Coord.getUnit(gapD)+" gap="+gap);
      
      // Pour garder en m�moire les pixels 
      pimg.setLockCacheFree(true);
      pimg.pixelsOriginFromCache();

      double incrPourcent = gapPourcent/pimg.naxis2;
      long oNpix=-1;  
      for( double y=0; y<pimg.naxis2; y+=gap ) {
         pourcent += incrPourcent;
         for( double x=0; x<pimg.naxis1; x+=gap ) {
            try {
               coo.x = x;
               coo.y = (pimg.naxis2-y-1);
               
               // dans du vide - on test d'abord le buffers 8bits, et on v�rifie si on tombe sur 0
               if( pimg.getPixel8Byte((int)x,(int)coo.y)==0 && Double.isNaN(pimg.getPixel((int)x,(int)y)) ) continue;
               
               // Hors de la plage de pixels
               if( flagRange ) {
                  double pix = pimg.getPixel((int)x,(int)y);
                  if( !Double.isNaN(pixMin) && pix<pixMin ) continue;
                  if( !Double.isNaN(pixMax) && pix>pixMax ) continue;
               }
               
               pimg.projd.getCoord(coo);
               long npix=0;
               npix = hpx.ang2pix(o1, coo.al, coo.del);

               // Juste pour �viter d'ins�rer 2x de suite le m�me npix
               if( npix==oNpix ) continue;
               
               moc.add(o1,npix);
               oNpix=npix;
            } catch( Exception e ) {
               if( aladin.levelTrace>=3 ) e.printStackTrace();
            }
         }
      }
      
      // Les pixels peuvent d�sormais �tre lib�r�s
      pimg.setLockCacheFree(false);
   }
   
   // Ajout d'un plan map Healpix au MOC en cours de construction
   private void addMocFromPlanBG(Plan p1,int order, double pixMin,double pixMax) {
      boolean flagRange = !Double.isNaN(pixMin) || !Double.isNaN(pixMax);
      PlanBG p = (PlanBG)p1;
      
      
      // Passage en mode FITS
      if( !p.hasOriginalPixels() ) p.switchFormat();
      
      // D�termination de l'ordre pixel (order) et tuiles (fileOrder)
      int tileOrder = p.getTileOrder();
      int z = (int)p.getZ();
      
      int divOrder=0;
      int fileOrder = order - tileOrder;
      
      // L'ordre des tuiles ne peut �tre inf�rieur � 3
      if( fileOrder<3 ) {
         divOrder=(3-fileOrder)*2;
         fileOrder=3;
      }
      
      /// L'ordre des tuiles ne peut entrainer le d�passement de la r�solution
      // de la map HEALPix
      if( fileOrder>p.getMaxFileOrder() ) {
         fileOrder=p.getMaxFileOrder();
         order = fileOrder+tileOrder;
      }
      
      // On g�n�re d'abord un MOC dans le syst�me de r�f�rence de la map HEALPix
      // on fera la conversion en ICRS � la fin du processus
      moc.setCoordSys( p.frameOrigin==Localisation.GAL ? "G" : 
                       p.frameOrigin==Localisation.ECLIPTIC ? "E" : "C");
      frameOrigin = p.frameOrigin;
      try { moc.setCheckConsistencyFlag(false); } catch(Exception e) {}
      
      // Nombre de losanges � traiter
      int n = (int)CDSHealpix.pow2(fileOrder); 
      n=12*n*n;
      
//      System.out.println("Nombre de losanges � traiter : "+n);
      
      // Sans doute inutile car d�j� fait
      try { p.createHealpixOrder(tileOrder); } catch( Exception e1 ) { e1.printStackTrace(); }
      long nsize = CDSHealpix.pow2(tileOrder);
      
      double incrPourcent = gapPourcent/n;
      
      for( int npixFile=0; npixFile<n; npixFile++ ) {
//         System.out.println("Traitement de "+npixFile);
         pourcent += incrPourcent;
         HealpixKey h = p.getHealpixLowLevel(fileOrder,npixFile,z,HealpixKey.SYNC);
         if( h==null ) continue;
         
         long min = nsize * nsize * npixFile;
         try {
            int nb=0;
            long oNpix=-1;  
            for( int y=0; y<h.height; y++ ) {
               for( int x=0; x<h.width; x++ ) {
                  try {
                     int idx = y * h.width + x;
                     double pixel = h.getPixel(idx,HealpixKey.NOW);
                     
                     // Pixel vide
                     if( Double.isNaN( pixel ) || pixel==blank ) continue;
                     
                     // En dehors de la plage
                     if( flagRange ) {
                        if( !Double.isNaN(pixMin) && pixel<pixMin ) continue;
                        if( !Double.isNaN(pixMax) && pixel>pixMax ) continue;
                     }
                     
                     long npix = min + p.xy2hpx(idx);

                     // Juste pour �viter d'ins�rer 2x de suite le m�me npix
                     if( npix==oNpix ) continue;
                     
                     moc.add(order,npix>>>divOrder);
                     oNpix=npixFile;
                     nb++;
                  } catch( Exception e ) { e.printStackTrace(); }
               }
            }
            
            // On remet imm�diatement au propre le MOC uniquement si on a ins�r�
            // au-moins 1000 cellules (histoire de ne pas exploser la m�moire)
            if( nb>10000 ) moc.checkAndFix();
            
         } catch( Exception e ) {
            e.printStackTrace();
         }
      }
      
      // Conversion en ICRS si n�cessaire
      if( frameOrigin!=Localisation.ICRS ) {
         try {
            moc.setCheckConsistencyFlag(true);
            moc=toReferenceFrame("C");
            frameOrigin=Localisation.ICRS;
         } catch( Exception e ) { e.printStackTrace(); }
      }
   }
   
   /** Creation d'un plan Moc � partir d'un HiPS en prenant toutes les pixels qui repr�sentent
    * threshold (sommation) de la totalit� des pixels. On commence par les valeurs les plus grandes.
    * Permet par exemple de cr�er un MOC � 10% (threshold=0.1) pour des maps de probabilit�
    * issues de l'�tude des ondes gravitationnelles.
    */
   private void addMocFromPlanBG(Plan p1, double threshold) throws Exception {
      PlanBG p = (PlanBG)p1;
      
      order=p.getMaxHealpixOrder();
      
      // D�termination de l'ordre pixel (o1) et tuiles (fileOrder)
      int o1 = p.getTileOrder();
      int z = (int)p.getZ();
      
      int divOrder=0;
      int fileOrder = order - o1;
      
      // L'ordre des tuiles ne peut �tre inf�rieur � 3
      if( fileOrder<3 ) {
         divOrder=(3-fileOrder)*2;
         fileOrder=3;
      }
      
      /// L'ordre des tuiles ne peut entrainer le d�passement de la r�solution
      // de la map HEALPix
      if( fileOrder>p.getMaxFileOrder() ) {
         fileOrder=p.getMaxFileOrder();
         order = fileOrder+o1;
      }
      
      moc.setMaxLimitOrder(order);
      
      // On g�n�re d'abord un MOC dans le syst�me de r�f�rence de la map HEALPix
      // on fera la conversion en ICRS � la fin du processus
      moc.setCoordSys( p.frameOrigin==Localisation.GAL ? "G" : 
                       p.frameOrigin==Localisation.ECLIPTIC ? "E" : "C");
      frameOrigin = p.frameOrigin;
      
      // Nombre de losanges � traiter
      int n = (int)CDSHealpix.pow2(fileOrder); 
      n=12*n*n;
      
//      System.out.println("Nombre de losanges � traiter : "+n);
      
      // Sans doute inutile car d�j� fait
      try { p.createHealpixOrder(o1); } catch( Exception e1 ) { e1.printStackTrace(); }
      long nsize = CDSHealpix.pow2(o1);
      
      double incrPourcent = gapPourcent/n;
      
      // Principe de l'algo: on parcours la map, pixel apr�s pixel qu'on ajoute
      // � la liste cumul en la triant imm�diatement en ordre d�croissant, 
      // tout en calculant la somme.
      //D�s qu'on d�passe le threshold, l'insertion sera conditionn�e au fait qu'il faut
      // que le nouveau pixel soit plus petit que la derni�re valeur de la liste 
      // Et si oui, on va l'ins�rer, mais virer autant des pixels les plus petits que n�cessaire
      ArrayList<PixCum> list;
      try {
         list = new ArrayList<>((int)(n*nsize*nsize));
      } catch( Exception e1 ) {
         throw new Exception("Sorry! too large probability sky map !");
      }
      double somme=0;
      
      for( int npixFile=0; npixFile<n; npixFile++ ) {
         pourcent += incrPourcent;
         HealpixKey h = p.getHealpixLowLevel(fileOrder,npixFile,z,HealpixKey.SYNC);
         if( h==null ) continue;
         
         long min = nsize * nsize * npixFile;
         try {
            for( int y=0; y<h.height; y++ ) {
               for( int x=0; x<h.width; x++ ) {
                  try {
                     int idx = y * h.width + x;
                     double pixel = h.getPixel(idx,HealpixKey.NOW);
                     
                     // Pixel vide
                     if( Double.isNaN( pixel ) || pixel==blank ) continue;
                     
                     long npix = min + p.xy2hpx(idx);
                     list.add( new PixCum(npix,pixel) );
                     somme += pixel;
                     
                  } catch( Exception e ) {
                     e.printStackTrace();
                  }
               }
            }
         } catch( Exception e ) {
            e.printStackTrace();
         }
      }
      
      Collections.sort(list);
      
      // Normalisation �ventuelle
      if( Math.abs(1-somme)>1e-8 ) {
         for( PixCum pc : list ) pc.val/=somme;
      }
      
      somme=0;
      try {
         // Remplissage du Moc
         moc.setCheckConsistencyFlag(false);
         int nb=0;
         for( PixCum pc : list ) {
            long npix = pc.npix;
            somme += pc.val;
            if( somme>threshold ) break;
            moc.add(order,npix>>>divOrder);
            nb++;
            if( nb>10000 ) { moc.checkAndFix(); nb=0; }
         }

         // Conversion en ICRS si n�cessaire
         moc.setCheckConsistencyFlag(true);
         moc=toReferenceFrame("C");
         frameOrigin=Localisation.ICRS;
         
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }
   
   // Pour g�rer un pixel Healpix
   class PixCum implements Comparable {
      long npix;   // indice dans la map
      double val;  // valeur du pixel
      
      PixCum(long npix,double val) {
         this.npix=npix;
         this.val=val;
      }

      @Override
      public int compareTo(Object o) {
         if( val == ((PixCum)o).val ) {
            return npix == ((PixCum)o).npix ? 0 : npix < ((PixCum)o).npix ? 1 : -1;
         }
         return val < ((PixCum)o).val ? 1 : -1;
      }
   }
   
   
//   /** Creation d'un plan Moc � partir d'un HiPS en prenant tous les pixels dont les
//    * valeurs sont comprises dans un interval (bornes incluses)
//    */
//   private void addMocFromPlanBG(Plan p1, double min, double max) throws Exception {
//      PlanBG p = (PlanBG)p1;
//      
//      order=p.getMaxHealpixOrder();
//      
//      // D�termination de l'ordre pixel (o1) et tuiles (fileOrder)
//      int o1 = p.getTileOrder();
//      int z = (int)p.getZ();
//      
//      int divOrder=0;
//      int fileOrder = order - o1;
//      
//      // L'ordre des tuiles ne peut �tre inf�rieur � 3
//      if( fileOrder<3 ) {
//         divOrder=(3-fileOrder)*2;
//         fileOrder=3;
//      }
//      
//      /// L'ordre des tuiles ne peut entrainer le d�passement de la r�solution
//      // de la map HEALPix
//      if( fileOrder>p.getMaxFileOrder() ) {
//         fileOrder=p.getMaxFileOrder();
//         order = fileOrder+o1;
//      }
//      
//      moc.setMaxLimitOrder(order);
//      
//      // On g�n�re d'abord un MOC dans le syst�me de r�f�rence de la map HEALPix
//      // on fera la conversion en ICRS � la fin du processus
//      moc.setCoordSys( p.frameOrigin==Localisation.GAL ? "G" : 
//                       p.frameOrigin==Localisation.ECLIPTIC ? "E" : "C");
//      frameOrigin = p.frameOrigin;
//      
//      // Nombre de losanges � traiter
//      int n = (int)CDSHealpix.pow2(fileOrder); 
//      n=12*n*n;
//      
////      System.out.println("Nombre de losanges � traiter : "+n);
//      
//      // Sans doute inutile car d�j� fait
//      try { p.createHealpixOrder(o1); } catch( Exception e1 ) { e1.printStackTrace(); }
//      long nsize = CDSHealpix.pow2(o1);
//      
//      double incrPourcent = gapPourcent/n;
//      
//      // Principe de l'algo: on parcours la map, pixel apr�s pixel qu'on ajoute
//      // et on les ajoute au fur et � mesure au MOC en construction
//      for( int npixFile=0; npixFile<n; npixFile++ ) {
//         pourcent += incrPourcent;
//         HealpixKey h = p.getHealpixLowLevel(fileOrder,npixFile,z,HealpixKey.SYNC);
//         if( h==null ) continue;
//         
//         long minTile = nsize * nsize * npixFile;
//         int nb=0;
//         moc.setCheckConsistencyFlag(false);
//         try {
//            for( int y=0; y<h.height; y++ ) {
//               for( int x=0; x<h.width; x++ ) {
//                  try {
//                     int idx = y * h.width + x;
//                     double pixel = h.getPixel(idx,HealpixKey.NOW);
//                     
//                     // Pixel vide
//                     if( Double.isNaN( pixel ) || pixel==blank ) continue;
//                     
//                     // Pixel hors interval
//                     if( !Double.isNaN(min) && pixel<min ) continue;
//                     if( !Double.isNaN(max) && pixel>max ) continue;
//                     
//                     long npix = minTile + p.xy2hpx(idx);
//                     moc.add(order,npix>>>divOrder);
//                     nb++;
//                     if( nb>10000 ) { moc.checkAndFix(); nb=0; }
//                     
//                  } catch( Exception e ) {
//                     e.printStackTrace();
//                  }
//               }
//            }
//         } catch( Exception e ) {
//            e.printStackTrace();
//         }
//      }
//      
//      // Conversion en ICRS si n�cessaire
//      if( frameOrigin!=Localisation.ICRS ) {
//         moc.setCheckConsistencyFlag(true);
//         moc=toReferenceFrame("C");
//         frameOrigin=Localisation.ICRS;
//      }
//   }
   
   protected boolean waitForPlan() {
      try {
         moc = new HealpixMoc();
         moc.setMinLimitOrder(3);
         if( order!=-1) moc.setMaxLimitOrder(order);
         moc.setCoordSys("C");
         frameOrigin=Localisation.ICRS;
         moc.setCheckConsistencyFlag(false);
         for( Plan p1 : p ) {
            if( p1.isCatalog() ) {
               if( fov ) addMocFromCatFov(p1,order);
               else addMocFromCatalog(p1,radius,order);
            }
            else if( p1.isImage() ) addMocFromImage(p1,pixMin,pixMax);
            else if( p1 instanceof PlanBG && !Double.isNaN(threshold) ) addMocFromPlanBG(p1,threshold);
            else if( p1 instanceof PlanBG ) addMocFromPlanBG(p1,order,pixMin,pixMax);
         }
         moc.setCheckConsistencyFlag(true);
      } catch( Exception e ) {
         error=e.getMessage();
         if( aladin.levelTrace>=3 ) e.printStackTrace();
         flagProcessing=false;
         return false;
      }
      flagProcessing=false;
      if( moc.getSize()==0 ) error="Empty MOC";
      flagOk=true;
      return true;
   }
   

      
}

