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

import java.awt.image.ColorModel;

import cds.aladin.Aladin;
import cds.aladin.CanvasColorMap;
import cds.allsky.Context.JpegMethod;
import cds.fits.Fits;

/** Construction de la hi�rarchie des tuiles JPEG � partir des tuiles FITS de plus bas
 * niveau. La m�thode employ�e est soit la m�diane soit la moyenne pour passer des 4 pixels de niveau
 * inf�rieur au pixel de niveau sup�rieur (fait disparaitre peu � peu les �toiles
 * faibles). Le passage en 8 bits se fait, soit par une table de couleur (cm) fournie,
 * soit par une intervalle (cut[]).
 * @author Ana�s Oberto & Pierre Fernique
 */
public class BuilderJpg extends BuilderTiles {

   private double[] cut;
   protected byte [] tcm;
   private int bitpix;
   private int width;
   private double blank,bscale,bzero;

   private int statNbFile;

   protected String fmt;
   protected String ext;

   /**
    * Cr�ation du g�n�rateur JPEG.
    * @param cut borne de l'intervalle pour le passage en 8 bits (uniquement si cm==null)
    * @param cm table des couleurs pour le passage en 8 bits (prioritaire sur cut),
    * @param context
    */
   public BuilderJpg(Context context) {
      super(context);
      init();
   }

   protected void init() {
      fmt = "jpeg";
      ext = ".jpg";
   }

   public Action getAction() { return Action.JPEG; }

   // Valide la coh�rence des param�tres pour la cr�ation des tuiles JPEG
   public void validateContext() throws Exception {
      validateOutput();
      if( !context.isExistingAllskyDir() ) throw new Exception("No Fits tile found");
      validateOrder(context.getOutputPath());
      validateDepth();
      if( !context.isColor() ) validateCut();

      // Chargement du MOC r�el � la place de celui de l'index (moins pr�cis)
      try { context.loadMoc(); } catch( Exception e ) {
         context.warning("Tile MOC not found => use index MOC");
      }

      // reprise du frame si n�cessaire depuis le fichier de propri�t�
      if( !context.hasFrame() ) context.setFrameName( getFrame() );

      context.initRegion();
      //      context.initParameters();
   }


   protected int getMinCM() { return 0; }

   public void run() throws Exception {
      context.resetCheckCode(fmt);
      ColorModel cm = context.getFct()==null ? null : CanvasColorMap.getCM(0, 128, 255,false,
            0/*PlanImage.CMGRAY*/, context.getFct().code());
      tcm = cm==null ? null : cds.tools.Util.getTableCM(cm,2);
      cut = context.getCut();
      double bz = context.bzero;
      double bs = context.bscale;
      context.info("Map pixel cut ["+ip(cut[0],bz,bs)+" .. "+ip(cut[1],bz,bs)+"] to ["+getMinCM()+"..255] ("+context.getTransfertFct()+")");

      context.info("Tile aggregation method="+context.getJpegMethod());
      build();
      if( !context.isTaskAborting() ) {
         //         (new BuilderAllsky(context)).createAllSkyColor(context.getOutputPath(),3,fmt,64,0);
         //         context.writePropertiesFile();

         (new BuilderAllsky(context)).runJpegOrPngOnly(fmt);
         if( context instanceof ContextGui && ((ContextGui) context).mainPanel.planPreview!=null ) {
            if( fmt.equals("jpeg") ) ((ContextGui) context).mainPanel.planPreview.inJPEG = true;
            else ((ContextGui) context).mainPanel.planPreview.inPNG = true;
         }
      }

   }

   public boolean isAlreadyDone() {
      if( context.isColor() ) {
         context.info(fmt+" conversion not required for Healpix colored survey");
         return true;
      }
      if( !context.actionPrecedeAction(Action.INDEX, Action.TILES)) return false;
      if( !context.actionPrecedeAction(Action.TILES, getAction())) return false;
      context.info("Pre-existing HEALPix "+fmt+" survey seems to be ready");
      return true;
   }

   /** Demande d'affichage des stats via Task() */
   public void showStatistics() {
      context.showJpgStat(statNbFile, totalTime, getNbThreads(), getNbThreadRunning() );
      if( !(context instanceof ContextGui ) ) super.showStatistics();
   }

   public void build() throws Exception {
      initStat();
      super.build();
   }
   
   protected void activateCache(long size,long sizeCache) { }
   
   protected Fits createLeaveHpx(ThreadBuilderTile hpx, String file,String path,int order,long npix, int z) throws Exception {
      Fits out = createLeaveJpg(file);
      if( out==null ) return null;

      out.writeCompressed(file+ext,cut[0],cut[1],tcm,fmt);
      Aladin.trace(4, "Writing " + file+ext);
      updateStat();
      return out;
   }

   protected Fits createNodeHpx(String file,String path,int order,long npix,Fits fils[], int z) throws Exception {
      JpegMethod method = context.getJpegMethod();
      Fits out = createNodeJpg(fils, method);
      if( out==null ) return null;
      out.writeCompressed(file+ext,cut[0],cut[1],tcm,fmt);
      Aladin.trace(4, "Writing " + file+ext);
      return out;
   }

   /** Mise � jour de la barre de progression en mode GUI */
   protected void setProgressBar(int npix) { context.setProgress(npix); }


   private void initStat() {
      context.setProgressMax(768);
      statNbFile=0;
      startTime = System.currentTimeMillis();
   }

   // Mise � jour des stats
   private void updateStat() {
      statNbFile++;
      totalTime = System.currentTimeMillis()-startTime;
   }

   /** Construction d'une tuile terminale. De fait, simple chargement
    * du fichier FITS correspondant. */
   private Fits createLeaveJpg(String file) throws Exception {
      Fits out = null;
      if( context.isTaskAborting() ) throw new Exception("Task abort !");
      try {
         out = new Fits();
         out.loadFITS(file+".fits");
         if( first ) { first=false; setConstantes(out); }
      } catch( Exception e ) { out=null; }
      return out;
   }

   private boolean first=true;
   private void setConstantes(Fits f) {
      bitpix = f.bitpix;
      blank  = f.blank;
      bscale = f.bscale;
      bzero  = f.bzero;
      width  = f.width;
   }

   /** Construction d'une tuile interm�diaire � partir des 4 tuiles filles */
   private Fits createNodeJpg(Fits fils[], JpegMethod method) throws Exception {
      if( width==0 || fils[0]==null && fils[1]==null && fils[2]==null && fils[3]==null ) return null;
      Fits out = new Fits(width,width,bitpix);
      out.setBlank(blank);
      out.setBscale(bscale);
      out.setBzero(bzero);

      Fits in;
      double p[] = new double[4];
      double coef[] = new double[4];

      for( int dg=0; dg<2; dg++ ) {
         for( int hb=0; hb<2; hb++ ) {
            int quad = dg<<1 | hb;
            in = fils[quad];
            int offX = (dg*width)/2;
            int offY = ((1-hb)*width)/2;

            for( int y=0; y<width; y+=2 ) {
               for( int x=0; x<width; x+=2 ) {

                  double pix=blank;
                  if( in!=null ) {

                     // On prend la moyenne (sans prendre en compte les BLANK)
                     if( method==Context.JpegMethod.MEAN ) {
                        double totalCoef=0;
                        for( int i=0; i<4; i++ ) {
                           int dx = i==1 || i==3 ? 1 : 0;
                           int dy = i>=2 ? 1 : 0;
                           p[i] = in.getPixelDouble(x+dx,y+dy);
                           if( in.isBlankPixel(p[i]) ) coef[i]=0;
                           else coef[i]=1;
                           totalCoef+=coef[i];
                        }
                        if( totalCoef!=0 ) {
                           pix=0;
                           for( int i=0; i<4; i++ ) {
                              if( coef[i]!=0 ) pix += p[i]*(coef[i]/totalCoef);
                           }
                        }

                        // On garde la valeur m�diane (les BLANK seront automatiquement non retenus)
                     } else {

                        double p1 = in.getPixelDouble(x,y);
                        if( in.isBlankPixel(p1) ) p1=Double.NaN;
                        double p2 = in.getPixelDouble(x+1,y);
                        if( in.isBlankPixel(p2) ) p1=Double.NaN;
                        double p3 = in.getPixelDouble(x,y+1);
                        if( in.isBlankPixel(p3) ) p1=Double.NaN;
                        double p4 = in.getPixelDouble(x+1,y+1);
                        if( in.isBlankPixel(p4) ) p1=Double.NaN;

                        if( p1>p2 && (p1<p3 || p1<p4) || p1<p2 && (p1>p3 || p1>p4) ) pix=p1;
                        else if( p2>p1 && (p2<p3 || p2<p4) || p2<p1 && (p2>p3 || p2>p4) ) pix=p2;
                        else if( p3>p1 && (p3<p2 || p3<p4) || p3<p1 && (p3>p2 || p3>p4) ) pix=p3;
                        else pix=p4;
                     }
                  }

                  out.setPixelDouble(offX+(x/2), offY+(y/2), pix);
               }
            }
         }
      }

      for( int i=0; i<4; i++ ) {
         if( fils[i]!=null ) fils[i].free();
      }
      return out;
   }

}
