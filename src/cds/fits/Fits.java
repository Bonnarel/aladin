// Copyright 2010 - UDS/CNRS
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

package cds.fits;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.MediaTracker;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.ByteChannel;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import cds.aladin.Aladin;
import cds.aladin.Calib;
import cds.aladin.Coord;
import cds.aladin.MyInputStream;
import cds.allsky.Constante;
import cds.image.Hdecomp;
import cds.tools.pixtools.CDSHealpix;
import cds.tools.pixtools.Util;

/**
 * Classe de manipulation d'un fichier image FITS
 */
final public class Fits {

   static public final int PIX_ARGB = 0; // FITS ARGB, PNG couleur => couleur
                                         // avec transparence

   static public final int PIX_RGB = 1; // FITS RGB, JPEG couleur => Couleur
                                        // sans transparence

   static public final int PIX_TRUE = 2; // FITS => vraie valeur (d�finie par le
                                         // BITPIX) => transparence sur NaN ou
                                         // BLANK

   static public final int PIX_256 = 3; // JPEG N&B => 256 niveaux

   static public final int PIX_255 = 4; // PNG N&B => 255 niveaux - g�re la
                                        // transparence

   protected int pixMode; // Mode du losange (PIX_ARGB,PIX_RGB, PIX_TRUE,
                          // PIX_256, PIX_255

   public static final double DEFAULT_BLANK = Double.NaN;

   public static final double DEFAULT_BSCALE = 1.;

   public static final double DEFAULT_BZERO = 0.;

   public HeaderFits headerFits; // header Fits

   public byte[] pixels; // Pixels d'origine "fullbits" (y compt� depuis le bas)

   public int bitpix; // Profondeur des pixels (codage FITS 8,16,32,-32,-64)

   public int width; // Largeur totale de l'image

   public int height; // Hauteur totale de l'image

   public double bzero = DEFAULT_BZERO; // BZERO Fits pour la valeur physique du
                                        // pixel (BSCALE*pix+BZEO)

   public double bscale = DEFAULT_BSCALE; // BSCALE Fits pour la valeur physique
                                          // du pixel (BSCALE*pix+BZEO)

   public double blank = DEFAULT_BLANK; // valeur BLANK

   private long bitmapOffset = -1; // Rep�re le positionnement du bitmap des
                                   // pixels (voir releaseBitmap());

   // Dans le cas o� il s'agit d'une cellule sur l'image (seule une portion de
   // l'image sera accessible)
   public int xCell; // Position X � partir du coin haut gauche de la cellule de
                     // l'image (par d�faut 0)

   public int yCell; // Position Y � partir du coin haut gauche de la cellule de
                     // l'image (par d�faut 0)

   public int widthCell; // Largeur de la cellule de l'image (par d�faut =
                         // naxis1)

   public int heightCell; // Hauteur de la cellule de l'image (par d�faut =
                          // naxis2)

   // public byte [] pix8; // Pixels 8 bits en vue d'une sauvegarde JPEG (y
   // compt� depuis le haut)
   public int[] rgb; // pixels dans le cas d'une image couleur RGB

   private Calib calib; // Calibration astrom�trique

   /** Donne une approximation de l'occupation m�moire (en bytes) */
   public long getMem() {
      long mem = 12 * 4 + 4 * 8;
      if( calib != null ) mem += calib.getMem();
      if( headerFits != null ) mem += headerFits.getMem();
      if( pixels != null ) mem += pixels.length;
      // if( pix8!=null ) mem+=pix8.length;
      if( rgb != null ) mem += rgb.length;
      return mem;
   }

   public static String FS = System.getProperty("file.separator");

   /** Cr�ation en vue d'une lecture */
   public Fits() {
   }

   /**
    * Cr�ation en vue d'une construction "manuelle"
    * @param width Largeur
    * @param height Hauteur
    * @param bitpix Profondeur des pixels (codage FITS 8,16,32,-32,-64 ou 0 pour
    *           RGB)
    */
   public Fits(int width, int height, int bitpix) {
      this.width = this.widthCell = width;
      this.height = this.heightCell = height;
      this.bitpix = bitpix;
      xCell = yCell = 0;
      if( bitpix == 0 ) {
         pixMode = PIX_ARGB;
         rgb = new int[width * height];
      } else {
         pixMode = PIX_TRUE;
         pixels = new byte[width * height * Math.abs(bitpix) / 8];
         // pix8 = new byte[width*height];
         headerFits = new HeaderFits();
         headerFits.setKeyValue("SIMPLE", "T");
         headerFits.setKeyValue("BITPIX", bitpix + "");
         headerFits.setKeyValue("NAXIS", "2");
         headerFits.setKeyValue("NAXIS1", width + "");
         headerFits.setKeyValue("NAXIS2", height + "");
      }
   }

   // public double raMin=0,raMax=0,deMin=0,deMax=0;

   /**
    * Positionnement d'une calibration => initialisation de la coordonn�e
    * centrale de l'image (cf center)
    */
   public void setCalib(Calib c) {
      calib = c;
   }

   /** Retourne la calib ou null */
   public Calib getCalib() {
      return calib;
   }

   /* Chargement d'une image N&B sous forme d'un JPEG */
   public void loadJpeg(MyInputStream dis) throws Exception {
      loadJpeg(dis, false);
   }

   /** Chargement d'une image JPEG depuis un fichier */
   public void loadJpeg(String filename, int x, int y, int w, int h)
         throws Exception {
      loadJpeg(filename + "[" + x + "," + y + "-" + w + "x" + h + "]");
   }

   public void loadJpeg(String filename) throws Exception {
      loadJpeg(filename, false);
   }

   public void loadJpeg(String filename, boolean color) throws Exception {
      filename = parseCell(filename); // extraction de la descrition d'une
                                      // cellule �ventuellement en suffixe du
                                      // nom fichier.fits[x,y-wxh]
      MyInputStream is = null;
      try {
         is = new MyInputStream(new FileInputStream(filename));
         // is = is.startRead();
         is.getType(); // Pour �tre s�r de lire le commentaire �ventuel
         if( is.hasCommentCalib() ) {
            headerFits = is.createHeaderFitsFromCommentCalib();
            try {
               setCalib(new Calib(headerFits));
            } catch( Exception e ) {
               calib = null;
            }

         }
         loadJpeg(is, xCell, yCell, widthCell, heightCell, color);
      } finally {
         if( is != null ) is.close();
      }
      this.setFilename(filename);
   }

   public void loadJpeg(MyInputStream dis, boolean flagColor) throws Exception {
      loadJpeg(dis, 0, 0, -1, -1, flagColor);
   }

//   static private Component observer = (Component) new Label();

   // /** Chargement d'une image N&B ou COULEUR sous forme d'un JPEG */
   // protected void loadJpeg(MyInputStream dis,boolean flagColor) throws
   // Exception {
   // Image img = Toolkit.getDefaultToolkit().createImage(dis.readFully());
   // boolean encore=true;
   // while( encore ) {
   // try {
   // MediaTracker mt = new MediaTracker(observer);
   // mt.addImage(img,0);
   // mt.waitForID(0);
   // encore=false;
   // } catch( InterruptedException e ) { }
   // }
   // width=widthCell =img.getWidth(observer);
   // height=heightCell=img.getHeight(observer);
   // xCell=yCell=0;
   // bitpix= flagColor ? 0 : 8;
   //
   // BufferedImage imgBuf = new BufferedImage(widthCell,heightCell,
   // flagColor ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_BYTE_GRAY);
   // Graphics g = imgBuf.getGraphics();
   // g.drawImage(img,0,0,observer);
   // g.finalize(); g=null;
   // if( flagColor ) rgb =
   // ((DataBufferInt)imgBuf.getRaster().getDataBuffer()).getData();
   // else pixels =
   // ((DataBufferByte)imgBuf.getRaster().getDataBuffer()).getData();
   //
   // // BufferedImage imgBuf = new
   // BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
   // // Graphics g = imgBuf.getGraphics();
   // // g.drawImage(img,0,0,observer);
   // // g.finalize(); g=null;
   // // int taille=width*height;
   // // rgb = new int[taille];
   // // imgBuf.getRGB(0, 0, width, height, rgb, 0, width);
   // // if( bitpix!=0 ) {
   // // pixels = new byte[taille];
   // // for( int i=0; i<taille; i++ ) pixels[i] = (byte)(rgb[i] & 0xFF);
   // // rgb=null;
   // // }
   //
   // imgBuf.flush(); imgBuf=null;
   // img.flush(); img=null;
   // if( bitpix==8 ) {
   // pix8 = new byte[widthCell*heightCell];
   // initPix8();
   // }
   // }

   public void loadJpeg(MyInputStream dis, int x, int y, int w, int h,
         boolean flagColor) throws Exception {
      String coding = dis.getType() == MyInputStream.PNG ? "png" : "jpeg";
      Iterator readers = ImageIO.getImageReadersByFormatName(coding);
      ImageReader reader = (ImageReader) readers.next();
      ImageInputStream iis = null;
      try {
         iis = ImageIO.createImageInputStream(dis);
         reader.setInput(iis, true);
         width = reader.getWidth(0);
         height = reader.getHeight(0);

         // Ouverture compl�te de l'image
         if( w == -1 ) {
            widthCell = width;
            heightCell = height;
            xCell = yCell = 0;

            // Ouverture juste d'une cellule de l'image
         } else {
            if( x < 0 || y < 0 || x + w > width || y + h > height ) throw new Exception(
                  "Mosaic cell outside the image (" + width + "x" + height
                  + ") cell=[" + x + "," + y + " " + w + "x" + h + "]");
            widthCell = w;
            heightCell = h;
            xCell = x;
            yCell = y;
         }
         bitpix = flagColor ? 0 : 8;

         ImageReadParam param = reader.getDefaultReadParam();
         if( this.widthCell != width || this.heightCell != height ) {
            int yJpegCell;
            if( RGBASFITS ) yJpegCell = height - yCell - heightCell;
            else yJpegCell = yCell;
            Rectangle r = new Rectangle(xCell, yJpegCell, widthCell, heightCell);
            param.setSourceRegion(r);
         }
         BufferedImage imgBuf = reader.read(0, param);

         if( flagColor ) {
            pixMode = dis.getType() == MyInputStream.PNG ? PIX_ARGB : PIX_RGB;
            // int [] rgb1 = new int[widthCell*heightCell];
            int[] rgb1 = imgBuf.getRGB(0, 0, widthCell, heightCell, null, 0,
                  widthCell);
            if( RGBASFITS ) invImageLine(widthCell, heightCell, rgb1);
            rgb = rgb1;
            rgb1 = null;

         } else {
            pixMode = dis.getType() == MyInputStream.PNG ? PIX_255 : PIX_256;
            pixels = ((DataBufferByte) imgBuf.getRaster().getDataBuffer())
                  .getData();
            if( pixMode == PIX_255 ) {
               for( int i = 0; i < pixels.length; i++ )
                  if( pixels[i] < 255 ) pixels[i]++;
            }
            if( RGBASFITS ) {
               invImageLine(widthCell, heightCell, pixels);
               // pix8 = pixels;
            }

            // System.out.print("1�re ligne:");
            // for( int i=0; i<5; i++ ) System.out.print(" "+pixels[i]);
            // System.out.println();
            //
            // System.out.print("Derni�re ligne :");
            // for( int i=0; i<5; i++ )
            // System.out.print(" "+pixels[pixels.length-widthCell+i]);
            // System.out.println();
         }

         imgBuf.flush();
         imgBuf = null;
      } finally { if( iis!=null ) iis.close(); }
   }

   protected static void invImageLine(int width, int height, byte[] pixels) {
      byte[] tmp = new byte[width];
      for( int h = height / 2 - 1; h >= 0; h-- ) {
         int offset1 = h * width;
         int offset2 = (height - h - 1) * width;
         System.arraycopy(pixels, offset1, tmp, 0, width);
         System.arraycopy(pixels, offset2, pixels, offset1, width);
         System.arraycopy(tmp, 0, pixels, offset2, width);
      }
      tmp = null;
   }

   protected static void invImageLine(int width, int height, int[] pixels) {
      int[] tmp = new int[width];
      for( int h = height / 2 - 1; h >= 0; h-- ) {
         int offset1 = h * width;
         int offset2 = (height - h - 1) * width;
         System.arraycopy(pixels, offset1, tmp, 0, width);
         System.arraycopy(pixels, offset2, pixels, offset1, width);
         System.arraycopy(tmp, 0, pixels, offset2, width);
      }
      tmp = null;
   }

   // Extraction de la d�finition d'une cellule FITS pour une ouverture d'un
   // fichier FITS en mode "mosaic"
   // le filename doit �tre suffix� par [x,y-wxh] (sans aucun blanc).
   // => met � jour les variables xCell, yCell, widthCell et heigthCell
   // S'il n'y a pas de d�finition de cellule, laisse xCell et yCell � 0 et
   // widthCell et heightCell � -1
   // @return le nom de fichier sans le suffixe
   private String parseCell(String filename) throws Exception {
      xCell = yCell = 0;
      widthCell = heightCell = -1;
      int deb = filename.lastIndexOf('[');
      if( deb == -1 ) return filename;
      int fin = filename.indexOf(']', deb);
      if( fin == -1 ) return filename;
      StringTokenizer st = new StringTokenizer(
            filename.substring(deb + 1, fin), ",-x");
      try {
         xCell = Integer.parseInt(st.nextToken());
         yCell = Integer.parseInt(st.nextToken());
         widthCell = Integer.parseInt(st.nextToken());
         heightCell = Integer.parseInt(st.nextToken());
      } catch( Exception e ) {
         throw new Exception("Bad cell mosaic FITS definition => " + filename);
      }
      return filename.substring(0, deb);
   }

   /** Chargement d'une image FITS depuis un fichier */
   public void loadFITS(String filename, int x, int y, int w, int h)
         throws Exception {
      loadFITS(filename + "[" + x + "," + y + "-" + w + "x" + h + "]");
   }

   public void loadFITS(String filename) throws Exception {
      loadFITS(filename, false, true);
   }

   public void loadFITS(String filename, boolean color, boolean flagLoad)
         throws Exception {
      filename = parseCell(filename); // extraction de la descrition d'une
                                      // cellule �ventuellement en suffixe du
                                      // nom fichier.fits[x,y-wxh]
      MyInputStream is = null;
      try {
         is = new MyInputStream(new FileInputStream(filename));
         is = is.startRead();
         if( color ) {
            if( widthCell < 0 ) throw new Exception(
                  "Mosaic mode not supported yet for FITS color file");
            loadFITSColor(is);
         } else loadFITS(is, xCell, yCell, widthCell, heightCell, flagLoad);
      } finally {
         if( is != null ) is.close();
      }
      this.setFilename(filename);
   }

   /** Chargement d'une image FITS */
   public void loadFITS(MyInputStream dis) throws Exception {
      loadFITS(dis, 0, 0, -1, -1);
   }

   /** Chargement d'une cellule d'une image FITS */
   public void loadFITS(MyInputStream dis, int x, int y, int w, int h)
         throws Exception {
      loadFITS(dis, x, y, w, h, true);
   }

   public void loadFITS(MyInputStream dis, int x, int y, int w, int h,
         boolean flagLoad) throws Exception {
      dis = dis.startRead();
      // boolean flagHComp = (dis.getType() & MyInputStream.HCOMP) !=0;
      boolean flagHComp = dis.isHCOMP();
      if( flagHComp || dis.isGZ() ) {
         releasable = false;
         flagLoad = true;
      }

      pixMode = PIX_TRUE;
      headerFits = new HeaderFits(dis);
      bitpix = headerFits.getIntFromHeader("BITPIX");

      // MODIF D'ANAIS QUI NE PEUT PAS FONCTIONNER CAR UN FITS PEUT ETRE INDIQUE
      // COMME ETANT SUSCEPTIBLE
      // D'AVOIR UNE EXTENSION SANS POUR AUTANT EN AVOIR UNE !! [EXTEND = T]
      //
      // // Si on a une image avec extension
      // // ouvrir et lire le reste des infos depuis une image de l'extension
      // long type = dis.getType();
      // if ( (type & MyInputStream.XFITS)!=0) {
      // headerFits = new HeaderFits(dis);
      // int naxis = headerFits.getIntFromHeader("NAXIS");
      // // Il s'agit juste d'une ent�te FITS indiquant des EXTENSIONs
      // if( headerFits.getStringFromHeader("EXTEND")!=null ) {
      // while( naxis<2 ) {
      // // Je saute l'�ventuel baratin de la premi�re HDU
      // if (!headerFits.readHeader(dis))
      // throw new Exception("Naxis < 2");
      // naxis = headerFits.getIntFromHeader("NAXIS");
      // }
      // }
      // bitpix = headerFits.getIntFromHeader("BITPIX");
      // }
      width = headerFits.getIntFromHeader("NAXIS1");
      height = headerFits.getIntFromHeader("NAXIS2");

      // Ouverture compl�te de l'image
      if( w == -1 ) {
         widthCell = width;
         heightCell = height;
         xCell = yCell = 0;

         // Ouverture juste d'une cellule de l'image
      } else {
         if( x < 0 || y < 0 || x + w > width || y + h > height ) throw new Exception(
               "Mosaic cell outside the image (" + width + "x" + height
                     + ") cell=[" + x + "," + y + " " + w + "x" + h + "]");
         widthCell = w;
         heightCell = h;
         xCell = x;
         yCell = y;
      }
      try {
         blank = headerFits.getDoubleFromHeader("BLANK");
      } catch( Exception e ) {
         blank = /* bitpix>0 ? 0 : */DEFAULT_BLANK;
      }

      int n = (Math.abs(bitpix) / 8);
      bitmapOffset = dis.getPos();

      int size = widthCell * heightCell * n;

      // Pas le choix, il faut d'abord tout lire, puis ne garder que la cellule
      // si n�cessaire
      if( flagHComp ) {
         byte[] buf = Hdecomp.decomp(dis);
         if( w == -1 ) pixels = buf;
         else {
            pixels = new byte[size];
            for( int lig = 0; lig < heightCell; lig++ )
               System.arraycopy(buf, (lig * width + xCell) * n, pixels, lig
                     * widthCell * n, widthCell * n);
         }

      } else {
         if( flagLoad || bitpix == 8 ) {
            pixels = new byte[size];

            // Lecture d'un coup
            if( w == -1 ) dis.readFully(pixels);

            // Lecture ligne � ligne pour m�moriser uniquement la cellule
            else {
               dis.skip(yCell * width * n);
               byte[] buf = new byte[width * n]; // une ligne compl�te
               for( int lig = 0; lig < heightCell; lig++ ) {
                  dis.readFully(buf);
                  System.arraycopy(buf, xCell * n, pixels, lig * widthCell * n,
                        widthCell * n);
               }
               dis.skip((height - (yCell + heightCell)) * width * n);
            }
         } else bitmapReleaseDone = true;

      }
      try {
         bscale = headerFits.getDoubleFromHeader("BSCALE");
      } catch( Exception e ) {
         bscale = DEFAULT_BSCALE;
      }
      try {
         bzero = headerFits.getDoubleFromHeader("BZERO");
      } catch( Exception e ) {
         bzero = DEFAULT_BZERO;
      }
      try {
         setCalib(new Calib(headerFits));
      } catch( Exception e ) {
         calib = null;
      }
      // if( bitpix==8 ) initPix8();
   }

   /** Chargement d'une image FITS couleur mode ARGB */
   public void loadFITSARGB(MyInputStream dis) throws Exception {
      headerFits = new HeaderFits(dis);
      width = widthCell = headerFits.getIntFromHeader("NAXIS1");
      height = heightCell = headerFits.getIntFromHeader("NAXIS2");
      xCell = yCell = 0;
      pixMode = PIX_ARGB;
      pixels = new byte[widthCell * heightCell * 32];
      dis.readFully(pixels);
      setARGB();
      try {
         setCalib(new Calib(headerFits));
      } catch( Exception e ) {
         calib = null;
      }
   }

   /** Chargement d'une image FITS couleur mode RGB cube */
   public void loadFITSColor(MyInputStream dis) throws Exception {
      headerFits = new HeaderFits(dis);
      bitpix = headerFits.getIntFromHeader("BITPIX");
      width = widthCell = headerFits.getIntFromHeader("NAXIS1");
      height = heightCell = headerFits.getIntFromHeader("NAXIS2");
      xCell = yCell = 0;
      pixMode = PIX_RGB;
      pixels = new byte[widthCell * heightCell];
      dis.readFully(pixels);

      byte[] t2 = new byte[widthCell * heightCell];
      dis.readFully(t2);
      byte[] t3 = new byte[widthCell * heightCell];
      dis.readFully(t3);
      rgb = new int[widthCell * heightCell];
      for( int i = 0; i < heightCell * widthCell; i++ ) {
         int val = 0;
         val = 0 | (((pixels[i]) & 0xFF) << 16) | (((t2[i]) & 0xFF) << 8)
               | (t3[i]) & 0xFF;
         rgb[i] = val;
      }
      try {
         bscale = headerFits.getIntFromHeader("BSCALE");
      } catch( Exception e ) {
         bscale = DEFAULT_BSCALE;
      }
      try {
         bzero = headerFits.getIntFromHeader("BZERO");
      } catch( Exception e ) {
         bzero = DEFAULT_BZERO;
      }
      try {
         setCalib(new Calib(headerFits));
      } catch( Exception e ) {
         calib = null;
      }

   }

   static final public int GZIP = 1;

   static final public int HHH = 1 << 1;

   static final public int COLOR = 1 << 2;

   static final public int XFITS = 1 << 3;

   /**
    * Chargement de l'entete d'une image FITS depuis un fichier
    * @return un code GZIP|HHH|COLOR pour savoir de quoi il s'agit
    */
   public int loadHeaderFITS(String filename) throws Exception {
      filename = parseCell(filename); // extraction de la descrition d'une
                                      // cellule �ventuellement en suffixe du
                                      // nom fichier.fits[x,y-wxh]
      int code = 0;
      MyInputStream is = new MyInputStream(new FileInputStream(filename));
      try {
         if( is.isGZ() ) code |= GZIP;
         is = is.startRead();
         long type = is.getType();

         // Cas sp�cial d'un fichier .hhhh
         if( filename.endsWith(".hhh") ) {
            byte[] buf = is.readFully();
            headerFits = new HeaderFits();
            headerFits.readFreeHeader(new String(buf), true, null);
            code |= HHH;

            // Cas d'un fichier PNG ou JPEG avec un commentaire contenant la
            // calib
         } else if( is.hasCommentCalib() ) {
            headerFits = is.createHeaderFitsFromCommentCalib();
            bitpix = 0;
         }
         // Si on a une image avec extension
         // ouvrir et lire le reste des infos depuis une image de l'extension
         else if( (type & MyInputStream.XFITS) != 0 ) {
            headerFits = new HeaderFits(is);
            code |= XFITS;
            int naxis = headerFits.getIntFromHeader("NAXIS");
            // Il s'agit juste d'une ent�te FITS indiquant des EXTENSIONs
            if( headerFits.getStringFromHeader("EXTEND") != null ) {
               while( naxis < 2 ) {
                  // Je saute l'�ventuel baratin de la premi�re HDU
                  if( !headerFits.readHeader(is) ) throw new Exception(
                        "Naxis < 2");
                  naxis = headerFits.getIntFromHeader("NAXIS");
               }
            }
            bitpix = headerFits.getIntFromHeader("BITPIX");
         }

         // Cas habituel
         else {
            headerFits = new HeaderFits(is);
            try {
               bitpix = headerFits.getIntFromHeader("BITPIX");
            } catch( Exception e1 ) {
               bitpix = 0;
            }
         }

         if( bitpix == 0 ) code |= COLOR;

         width = headerFits.getIntFromHeader("NAXIS1");
         height = headerFits.getIntFromHeader("NAXIS2");
         if( !hasCell() ) {
            xCell = yCell = 0;
            widthCell = width;
            heightCell = height;
         }
         try {
            blank = headerFits.getDoubleFromHeader("BLANK");
         } catch( Exception e ) {
            blank = DEFAULT_BLANK;
         }
         try {
            bscale = headerFits.getDoubleFromHeader("BSCALE");
         } catch( Exception e ) {
            bscale = DEFAULT_BSCALE;
         }
         try {
            bzero = headerFits.getDoubleFromHeader("BZERO");
         } catch( Exception e ) {
            bzero = DEFAULT_BZERO;
         }
         try {
            setCalib(new Calib(headerFits));
         } catch( Exception e ) {
            if( Aladin.levelTrace >= 3 ) e.printStackTrace();
            calib = null;
         }
         this.setFilename(filename);
      } finally {
         if( is != null ) is.close();
      }
      return code;
   }

   /** Retourne la valeur BSCALE (1 si non d�finie) */
   public double getBscale() {
      return bscale;
   }

   /** Retourne la valeur BZERO (0 si non d�finie) */
   public double getBzero() {
      return bzero;
   }

   /** Retourne la valeur BLANK si elle existe (tester avec hasBlank() */
   public double getBlank() {
      return blank;
   }

   /**
    * Positionement d'une valeur BSCALE - si �gale � 1, supprime le mot cl� du
    * header fits
    */
   public void setBscale(double bscale) {
      this.bscale = bscale;
      if( headerFits != null ) headerFits.setKeyValue("BSCALE",
            bscale == 1 ? (String) null : bscale + "");
   }

   /**
    * Positionement d'une valeur BZERO - si �gale � 0, supprime le mot cl� du
    * header fits
    */
   public void setBzero(double bzero) {
      this.bzero = bzero;
      if( headerFits != null ) headerFits.setKeyValue("BZERO",
            bzero == 0 ? (String) null : bzero + "");
   }

   /** Positionement d'une valeur BLANK. Double.NaN est support� */
   public void setBlank(double blank) {
      this.blank = blank;
      if( headerFits != null ) headerFits.setKeyValue("BLANK",
            Double.isNaN(blank) ? (String) null : blank + "");
   }

   /**
    * Positionnement du flag COLORMOD = ARGB pour signifier qu'il s'agit d'un
    * FITS couleur ARGB
    */
   public void setARGB() {
      setARGB(true);
   }

   public void setARGB(boolean reverse) {
      pixMode = PIX_ARGB;
      bitpix = 0;
      if( headerFits != null ) headerFits.setKeyValue("COLORMOD", "ARGB");

      // G�n�ration du tableau rgb[] � partir des pixels ARGB stock�s dans
      // pixels[]
      rgb = new int[widthCell * heightCell];
      for( int y = 0; y < heightCell; y++ ) {
         for( int x = 0; x < widthCell; x++ ) {
            int i = y * widthCell + x;
            int offset = reverse ? (heightCell - y - 1) * widthCell + x : i;
            rgb[offset] = (pixels[i * 4] & 0xFF) << 24
                  | (pixels[i * 4 + 1] & 0xFF) << 16
                  | (pixels[i * 4 + 2] & 0xFF) << 8
                  | (pixels[i * 4 + 3] & 0xFF);
         }
      }
   }

   /**
    * Cr�e si n�cessaire le r�pertoire correspondant au filename
    * @param filename
    */
   private void createDir(String filename) throws Exception {
      cds.tools.Util.createPath(filename);
      // File dir = new File(filename).getParentFile();
      // if( !dir.exists() ) {
      // dir.mkdirs();
      // }
   }

   // /** G�n�ration d'un fichier FITS (sans calibration) */
   // public void writeFITS8(OutputStream os) throws Exception {
   // headerFits.setKeyValue("BITPIX", bitpix+"");
   // headerFits.writeHeader(os);
   // os.write(pix8);
   // }
   //
   // /** G�n�ration d'un fichier FITS (sans calibration) */
   // public void writeFITS8(String filename) throws Exception {
   // createDir(filename);
   // OutputStream os = new FileOutputStream(filename);
   // writeFITS8(os);
   // os.close();
   // this.setFilename(filename);
   // }

   static public byte[] getBourrage(int currentPos) {
      int n = currentPos % 2880;
      int size = n == 0 ? 0 : 2880 - n;
      byte[] b = new byte[size];
      return b;
   }

   /** G�n�ration d'un fichier FITS (sans calibration) */
   public void writeFITS(OutputStream os) throws Exception {
      int size = headerFits.writeHeader(os);
      bitmapOffset = size;

      // FITS couleur en mode ARGB
      if( pixMode == PIX_ARGB || pixMode == PIX_RGB ) {
         byte[] buf = new byte[rgb.length * 4];
         for( int i = 0; i < rgb.length; i++ ) {
            int pix = rgb[i];
            buf[i * 4] = (byte) (pixMode == PIX_ARGB ? (pix >> 24) & 0xFF
                  : 0xFF);
            buf[i * 4 + 1] = (byte) ((pix >> 16) & 0xFF);
            buf[i * 4 + 2] = (byte) ((pix >> 8) & 0xFF);
            buf[i * 4 + 3] = (byte) (pix & 0xFF);
         }
         os.write(buf);
         size += buf.length;
         buf = null;

         // Fits classique
      } else {
         os.write(pixels);
         size += pixels.length;
      }

      // Ecriture des �ventuelles extensions
      if( extHeader == null ) return;
      int n = extHeader.size();
      for( int i = 0; i < n; i++ ) {
         byte[] b = getBourrage(size);
         size += b.length;
         os.write(b);
         HeaderFits h = (HeaderFits) extHeader.elementAt(i);
         h.writeHeader(os);
         byte[] p = (byte[]) extPixels.elementAt(i);
         os.write(p);
         size += p.length;
      }

      // Bourrage final
      os.write(getBourrage(size)); // Quel gachi !
   }

   /** G�n�ration d'un fichier FITS (sans calibration) */
   public void writeFITS(String filename) throws Exception {
      createDir(filename);
      OutputStream os = null;
      try {
         os = new FileOutputStream(filename);
         writeFITS(os);
      } finally {
         os.close();
      }
      this.setFilename(filename);
   }

   private Vector extHeader = null;

   private Vector extPixels = null;

   /** Ajout d'extensions (uniquement des images - ou des cubes) */
   public void addFitsExtension(HeaderFits header, byte[] pixels) {
      if( extHeader == null ) {
         extHeader = new Vector();
         extPixels = new Vector();
      }
      headerFits.setKeyValue("EXTEND", "T");
      extHeader.addElement(header);
      extPixels.addElement(pixels);
   }

   /** Suppression de toutes les extensions */
   public void clearExtensions() {
      extHeader = extPixels = null;
   }

   /**
    * G�n�ration d'un fichier JPEG � partir des pixels 8bits Ceux-ci doivent
    * �tre positionn�s manuellement via la m�thode setPix8(x,y,val)
    */
   public void writeCompressed(String file, double pixelMin, double pixelMax,
         byte[] tcm, String format) throws Exception {
      createDir(file);
      FileOutputStream fos = null;
      try {
         fos = new FileOutputStream(new File(file));
         writeCompressed(fos, pixelMin, pixelMax, tcm, format);
      } finally {
         if( fos!=null ) fos.close();
      }
      this.setFilename(file);
   }

   public void writeCompressed(OutputStream os, double pixelMin,
         double pixelMax, byte[] tcm, String format) throws Exception {
      Image imgSrc;
      BufferedImage imgTarget;
      int[] rgb = this.rgb;
      int typeInt = format.equals("png") ? BufferedImage.TYPE_INT_ARGB
            : BufferedImage.TYPE_INT_RGB;

      if( bitpix == 0 || pixMode == PIX_RGB || pixMode == PIX_ARGB ) {
         if( RGBASFITS ) {
            rgb = new int[this.rgb.length];
            System.arraycopy(this.rgb, 0, rgb, 0, rgb.length);
            invImageLine(widthCell, heightCell, rgb);
         }
         imgSrc = Toolkit.getDefaultToolkit().createImage(
               new MemoryImageSource(widthCell, heightCell, rgb, 0, widthCell));
         imgTarget = new BufferedImage(width, height, typeInt);
      } else {
         int targetPixMode = format.equals("png") ? PIX_255 : PIX_256;
         byte[] pix8 = toPix8(pixelMin, pixelMax, tcm, targetPixMode);
         imgSrc = Toolkit.getDefaultToolkit().createImage(
               new MemoryImageSource(widthCell, heightCell,
                     getCM(targetPixMode), pix8, 0, widthCell));
         // imgTarget = new
         // BufferedImage(width,height,BufferedImage.TYPE_BYTE_INDEXED,(IndexColorModel)
         // getCM(targetPixMode));
         imgTarget = new BufferedImage(width, height, typeInt);
      }

      Graphics g = imgTarget.createGraphics();
      int yJpegCell = RGBASFITS ? height - yCell - heightCell : yCell;
      g.drawImage(imgSrc, xCell, yJpegCell, null); // observer);
      g.dispose();

      // if( RGBASFITS && bitpix==0 ) invImageLine(widthCell,heightCell, rgb);

      ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
      ImageWriteParam iwp = writer.getDefaultWriteParam();
      if( format.equals("jpeg") ) {
         iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         iwp.setCompressionQuality(0.95f);
      }

      ImageOutputStream out = null;
      try { 
         out = ImageIO.createImageOutputStream(os);
         writer.setOutput(out);
         writer.write(null, new IIOImage(imgTarget, null, null), iwp);
         writer.dispose();
      } finally { if( out!=null ) out.close(); }
   }

   /**
    * G�n�ration d'un fichier JPEG ou PNG � partir des pixels RGB
    * @param format "jpeg" ou "png"
    */
   public void writeRGBcompressed(String file, String format) throws Exception {
      createDir(file);
      FileOutputStream fos = null;
      try{
         fos = new FileOutputStream(new File(file));
         writeRGBcompressed(fos, format);
      } finally { if( fos!=null )  fos.close(); }
      this.setFilename(file);
   }

   public void writeRGBcompressed(OutputStream os, String format)
         throws Exception {
      Image img;
      int typeInt = format.equals("png") ? BufferedImage.TYPE_INT_ARGB
            : BufferedImage.TYPE_INT_RGB;

      img = Toolkit.getDefaultToolkit().createImage(
            new MemoryImageSource(widthCell, heightCell, rgb, 0, widthCell));

      BufferedImage bufferedImage = new BufferedImage(width, height, typeInt);
      Graphics g = bufferedImage.createGraphics();
      g.drawImage(img, xCell, yCell, null); //observer);
      g.dispose();

      ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
      ImageWriteParam iwp = writer.getDefaultWriteParam();
      if( format.equals("jpeg") ) {
         iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         iwp.setCompressionQuality(0.95f);
      }

      ImageOutputStream out = null;
      try {
         out = ImageIO.createImageOutputStream(os);
         writer.setOutput(out);
         writer.write(null, new IIOImage(bufferedImage, null, null), iwp);
         writer.dispose();
      } finally { if( out!=null ) out.close(); }
   }

   /**
    * Retourne true s'il s'agit d'un mode graphique qui supporte la transparence
    */
   protected boolean isTransparent() {
      return isTransparent(pixMode);
   }

   static protected boolean isTransparent(int pixMode) {
      return pixMode == PIX_255 || pixMode == PIX_TRUE || pixMode == PIX_ARGB;
   }

   private ColorModel getCM(int pixMode) {
      byte[] r = new byte[256];
      boolean transp = isTransparent(pixMode);
      int gap = transp ? 1 : 0;
      for( int i = 1; i < r.length; i++ )
         r[i] = (byte) (i - gap);
      return transp ? new IndexColorModel(8, 256, r, r, r, 0)
            : new IndexColorModel(8, 256, r, r, r);
   }

   static final public boolean INCELLS = true; // true => permet le d�coupage en
                                               // cellule d'un fichier .hhh

   static final public boolean JPEGORDERCALIB = false; // true => Ne fait pas le
                                                       // compl�ment � la
                                                       // hauteur lors des
                                                       // calculs de coord via
                                                       // Calib pour les JPEG

   static final public boolean JPEGFROMTOP = false; // true => les lignes dans
                                                    // rgb[] sont compt�es
                                                    // depuis le haut

   static final public boolean RGBASFITS = true; // true => les lignes dans
                                                 // rgb[] sont compt�es depuis
                                                 // le bas comme en FITS

   /** Retourne la valeur RGB du pixel dans le cas d'une image couleur RGB */
   public int getPixelRGB(int x, int y) {
      return rgb[(y - yCell) * widthCell + (x - xCell)];
   }

   public int getPixelRGBJPG(int x, int y) {
      if( Fits.JPEGFROMTOP ) return rgb[((height - y - 1) - yCell) * widthCell
            + (x - xCell)];
      return rgb[(y - yCell) * widthCell + (x - xCell)];
   }

   /**
    * Retourne la description de la cellule courante selon la syntaxe [x,y-wxh]
    * ou "" si le fichier n'a pas �t� ouvert en mode mosaic
    */
   public String getCellSuffix() {
      if( !hasCell() ) return "";
      return "[" + xCell + "," + yCell + "-" + widthCell + "x" + heightCell
            + "]";
   }

   /** Retourne true si le fichier a �t� ouvert en mode mosaic */
   public boolean hasCell() {
      return widthCell != -1 && heightCell != -1
            && (widthCell != width || heightCell != height);
   }

   /**
    * Retourne true s'il y a un pixel connu � la position x,y. Cela concerne le
    * mode mosaic FITS, o� l'on peut ouvrir juste une cellule sur le fichier
    * Dans le cas g�n�ral, cette m�thode retournera true d�s que le pixel
    * d�sign� est dans l'image
    */
   public boolean isInCell(int x, int y) {
      return x >= xCell && x < xCell + widthCell && y >= yCell
            && y < yCell + heightCell;
   }

   /**
    * Retourne la valeur physique (bscale*pix+bzero) du pixel en (x,y) (y compt�
    * � partir du bas) sous forme d'un double
    */
   public double getPixelFull(int x, int y) {
      return bscale
            * getPixValDouble(pixels, bitpix, (y - yCell) * widthCell
                  + (x - xCell)) + bzero;
   }

   /**
    * Retourne la valeur du pixel en (x,y) (y compt� � partir du bas) sous forme
    * d'un double
    */
   public double getPixelDouble(int x, int y) {
      return getPixValDouble(pixels, bitpix, (y - yCell) * widthCell
            + (x - xCell));
   }

   /**
    * Retourne la valeur du pixel en (x,y) (y compt� � partir du bas) sous forme
    * d'un entier
    */
   public int getPixelInt(int x, int y) {
      return getPixValInt(pixels, bitpix, (y - yCell) * widthCell + (x - xCell));
   }

   // /** Retourne la valeur du pixel 8 bits en (x,y) (y compt� � partir du
   // haut)
   // * exprim� en byte => destin� � �tre sauvegard� en JPEG */
   // public int getPix8FromTop(int x,int y) {
   // return getPixValInt(pix8,8, (y-yCell)*widthCell + (x-xCell));
   // }
   //
   // /** Retourne la valeur du pixel 8 bits en (x,y) (y compt� � partir du bas)
   // * exprim� en byte => destin� � �tre sauvegard� en JPEG */
   // public int getPix8(int x,int y) {
   // return getPixValInt(pix8,8, ((height-y-1)-yCell)*widthCell+(x-xCell));
   // }

   /**
    * Positionne la valeur du pixel RGB en (x,y) (y compt� � partir du bas)
    * exprim� en ARGB
    */
   public void setPixelRGB(int x, int y, int val) {
      rgb[(y - yCell) * widthCell + (x - xCell)] = val;
   }

   public void setPixelRGBJPG(int x, int y, int val) {
      if( Fits.JPEGFROMTOP ) rgb[((height - y - 1) - yCell) * widthCell
            + (x - xCell)] = val;
      else rgb[(y - yCell) * widthCell + (x - xCell)] = val;
   }

   /**
    * Positionne la valeur du pixel en (x,y) (y compt� � partir du bas) exprim�
    * en double
    */
   public void setPixelDouble(int x, int y, double val) {
      setPixValDouble(pixels, bitpix, (y - yCell) * widthCell + (x - xCell),
            val);
   }

   /**
    * Positionne la valeur du pixel en (x,y) (y compt� � partir du bas) exprim�
    * en entier
    */
   public void setPixelInt(int x, int y, int val) {
      setPixValInt(pixels, bitpix, (y - yCell) * widthCell + (x - xCell), val);
   }

   // /** Positionne la valeur du pixel 8 bits en (x,y) (y compt� � partir du
   // bas)
   // * exprim� en byte => destin� � �tre sauvegard� en JPEG */
   // private void setPix8(int x,int y, int val) {
   // setPixValInt(pix8,8, ((height-y-1)-yCell)*widthCell+x, val);
   // // setPixValInt(pix8,8, y*width+x, val);
   // }

   /**
    * Retourne les coordonn�es c�lestes en J2000 de (x,y) (y compt� � partir du
    * bas) Le buffer "c" peut �tre fourni pour �viter des allocations inutiles,
    * null sinon
    */
   protected double[] getRaDec(double[] c, double x, double y) throws Exception {
      if( c == null ) c = new double[2];
      cTmp.x = x;
      cTmp.y = y;
      calib.GetCoord(cTmp);
      c[0] = cTmp.al;
      c[1] = cTmp.del;
      return c;
   }

   /**
    * Retourne les coordonn�es image (x,y) (y compt� � partir du bas) en
    * fonction de coordonn�es c�lestes en J2000 Le buffer "c" peut �tre fourni
    * pour �viter des allocations inutiles, null sinon
    */
   protected double[] getXY(double[] c, double ra, double dec) throws Exception {
      if( c == null ) c = new double[2];
      cTmp.al = ra;
      cTmp.del = dec;
      calib.GetXY(cTmp);
      c[0] = cTmp.x;
      c[1] = cTmp.y;
      return c;
   }

   // /** Remplit le tableau des pixels 8 bits (pix8) en fonction de
   // l'intervalle
   // * des valeurs des pixels originaux [min..max] lin�airement */
   // public byte [] toPix8(double min, double max,int pixMode) {
   // int range = 256;
   // int gap = 0;
   // if( isTransparent(pixMode) ) { range=255; gap=1; }
   //
   // byte [] pix8 = new byte[widthCell*heightCell];
   // double r = range/(max - min);
   // range--;
   //
   // byte pixOut;
   // for( int y=0; y<heightCell; y++) {
   // for( int x=0; x<widthCell; x++ ) {
   // double pixIn = getPixelDouble(x+xCell,y+yCell);
   // if( isBlankPixel(pixIn) ) pixOut=0;
   // else pixOut = (byte)( gap+ ( pixIn<=min ?0x00:pixIn>=max ? range
   // : (int)( ((pixIn-min)*r) )) & 0xff);
   // // setPix8(x+xCell,y+yCell,pixOut);
   // setPixValInt(pix8,8, (height-y-1)*widthCell+(x+xCell), pixOut);
   //
   // }
   // }
   //
   // return pix8;
   // }

   // VOIR REMARQUE DANS toPix8(double,double,byte[]);
   // /** Remplit le tableau des pixels 8 bits (pix8) en fonction de
   // l'intervalle
   // * des valeurs des pixels originaux [min..max] et de la table des couleurs
   // cm.
   // * S'il y a usage d'une fonction de transfert, elle est d�j� appliqu�e � la
   // table cm (rien � faire de plus)
   // * Si la table cm n'est pas monochrome, l'algo travaille uniquement sur la
   // composante bleue */
   // public void toPix8(double min, double max, ColorModel cm) {
   // double r = 256./(max - min);
   //
   // for( int y=0; y<heightCell; y++) {
   // for( int x=0; x<widthCell; x++ ) {
   // double pixIn = getPixelDouble(x+xCell,y+yCell);
   // int pix = ( pixIn<=min || isBlankPixel(pixIn) ?0x00:pixIn>=max ?
   // 0xff : (int)( ((pixIn-min)*r) ) & 0xff);
   // byte pixOut =(byte) cm.getBlue(pix);
   // setPix8(x+xCell,y+yCell,pixOut);
   // }
   // }
   // }

   /**
    * Remplit le tableau des pixels 8 bits (pix8) en fonction de l'intervalle
    * des valeurs des pixels originaux [min..max] et de la table des couleurs
    * tcm. S'il y a usage d'une fonction de transfert, elle est d�j� appliqu�e �
    * la table tcm (rien � faire de plus) Il est pr�f�rable de travailler sur
    * byte[] tcm plutot que sur ColorModel directement, c'est trop lent
    */
   public byte[] toPix8(double min, double max, byte[] tcm, int pixMode) {
      int range = 256;
      int gap = 0;
      if( isTransparent(pixMode) ) {
         range = 255;
         gap = 1;
      }

      byte[] pix8 = new byte[widthCell * heightCell];
      double r = range / (max - min);
      range--;
      byte pixOut;

      for( int y = 0; y < heightCell; y++ ) {
         for( int x = 0; x < widthCell; x++ ) {
            double pixIn = getPixelDouble(x + xCell, y + yCell);
            if( isBlankPixel(pixIn) ) pixOut = 0;
            else {
               int pix = (gap
                     + (pixIn <= min ? 0x00 : pixIn >= max ? range
                           : (int) (((pixIn - min) * r))) & 0xff);
               pixOut = tcm == null ? (byte) pix : tcm[pix];
            }
            // setPix8(x+xCell,y+yCell,pixOut);
            setPixValInt(pix8, 8, (height - y - 1) * widthCell + (x + xCell),
                  pixOut);

         }
      }
      return pix8;
   }

   /**
    * Remplit le tableau des pixels 8 bits (pix8) en fonction de l'intervalle
    * des valeurs des pixels originaux [min..max] et de la table des couleurs
    * tcm. S'il y a usage d'une fonction de transfert, elle est d�j� appliqu�e �
    * la table tcm (rien � faire de plus) Il est pr�f�rable de travailler sur
    * byte[] tcm plutot que sur ColorModel directement, c'est trop lent
    */
   public byte[] toPix4(double min, double max, byte[] tcm) {
      int range = 15;
      int gap = 1;

      byte[] pix4 = new byte[widthCell * heightCell];
      double r = range / (max - min);
      range--;
      byte pixOut;

      for( int y = 0; y < heightCell; y++ ) {
         for( int x = 0; x < widthCell; x++ ) {
            double pixIn = getPixelDouble(x + xCell, y + yCell);
            if( isBlankPixel(pixIn) ) pixOut = 0;
            else {
               int pix = (gap
                     + (pixIn <= min ? 0x00 : pixIn >= max ? range
                           : (int) (((pixIn - min) * r))) & 0xff);
               pixOut = tcm == null ? (byte) pix : tcm[pix];
            }
            // setPix8(x+xCell,y+yCell,pixOut);
            setPixValInt(pix4, 8, (height - y - 1) * widthCell + (x + xCell),
                  pixOut);

         }
      }
      return pix4;
   }

   // /** Remplit le tableau des pixels 8 bits (pix8) � partir des pixels
   // * fullbits (uniquement si bitpix==8) avec inversion des lignes */
   // public void initPix8() throws Exception {
   // if( bitpix!=8 ) throw new Exception("bitpix!=8");
   // if( pix8==null ) pix8 = new byte[widthCell*heightCell];
   // initPix(pixels,pix8);
   // }
   //
   // /** Remplit le tableau des pixels Fits (pixels) � partir des pixels
   // * 8bits (uniquement si bitpix==8) avec inversion des lignes */
   // public void initPixFits() throws Exception {
   // if( bitpix!=8 ) throw new Exception("bitpix!=8");
   // initPix(pix8,pixels);
   // }
   //
   // private void initPix(byte src[],byte dst[]) {
   // for( int h=0; h<heightCell; h++ ){
   // System.arraycopy(src,h*widthCell, dst,(heightCell-h-1)*widthCell,
   // widthCell);
   // }
   // }

   /**
    * D�termine l'intervalle pour un autocut "� la Aladin".
    * @return range[0]..[1] => minPixCut..maxPixCut range[2]..[3] =>
    *         minPix..maxPix
    */
   public double[] findFullAutocutRange() throws Exception {
      double[] cut = findAutocutRange(0, 0);
      cut[0] = bscale * cut[0] + bzero;
      cut[1] = bscale * cut[1] + bzero;
      return cut;
   }

   /**
    * D�termine l'intervalle pour un autocut "� la Aladin".
    * @return range[0]..[1] => minPixCut..maxPixCut range[2]..[3] =>
    *         minPix..maxPix
    */
   public double[] findAutocutRange() throws Exception {
      return findAutocutRange(0, 0);
   }

   /**
    * D�termine l'intervalle pour un autocut "� la Aladin" en ne consid�rant que
    * les valeurs comprises entre min et max
    * @return range[0]..[1] => minPixCut..maxPixCut range[2]..[3] =>
    *         minPix..maxPix
    */
   public double[] findAutocutRange(double min, double max) throws Exception {
      double[] range = new double[4];
      try {
         findMinMax(range, pixels, bitpix, widthCell, heightCell, min, max,
               true, 0);
      } catch( Exception e ) {
         System.err.println("Erreur  MinMax");
         range[0] = range[2] = min;
         range[1] = range[3] = max;
      }
      return range;
   }

   /** Retourne true si la valeur du pixel est blank ou NaN */
   public boolean isBlankPixel(double pix) {
      return Double.isNaN(pix) || /* !Double.isNaN(blank) && */pix == blank;
   }

   public double[] findMinMax() {
      boolean first = true;
      long nmin = 0, nmax = 0;
      double c;
      double max = 0, max1 = 0;
      double min = 0, min1 = 0;
      int n = pixels.length / (Math.abs(bitpix) / 8);
      for( int i = 0; i < n; i++ ) {
         c = getPixValDouble(pixels, bitpix, i);
         if( isBlankPixel(c) ) continue;

         if( first ) {
            max = max1 = min = min1 = c;
            first = false;
         }

         if( min > c ) {
            min = c;
            nmin = 1;
         } else if( max < c ) {
            max = c;
            nmax = 1;
         } else {
            if( c == min ) nmin++;
            if( c == max ) nmax++;
         }

         if( c < min1 && c > min || min1 == min && c < max1 ) min1 = c;
         else if( c > max1 && c < max || max1 == max && c > min1 ) max1 = c;
      }
      double[] minmax = new double[] { min, max };
      return minmax;
   }

   private int users = 0;
   public boolean hasUsers() { return users > 0; }
   synchronized public void addUser() { users++; }
   synchronized public void rmUser() { users--; }

   /** Retourne true si le bitmap est releas� */
   private boolean bitmapReleaseDone = false;
   public boolean isReleased() { return bitmapReleaseDone; }

   private boolean releasable = true;
   public void setReleasable(boolean flag) { releasable = flag; }
   public boolean isReleasable() { return releasable; }

   /**
    * Lib�ration de la m�moire utilis� par le bitmap des pixels (on suppose que
    * le fichier pourra �tre relu ult�rieurement) Rq : ne fonctionne que pour
    * les fichiers FITS locaux (RandomAccessFile) qui ont �t� ouvert en mode
    * normal (pas de cellule)
    */
   public void releaseBitmap() throws Exception {
      if( bitpix == 0 ) return; // De fait du JPEG
      if( hasUsers() ) return; // Pas possible, qq s'en sert
      testBitmapReleaseFeature();
      bitmapReleaseDone = true;
      pixels = null;
      // System.out.println("releaseBitmap() size="+width+"x"+height+"x"+Math.abs(bitpix)/8+" offset="+bitmapOffset+" de "+filename);
   }

   /**
    * Rechargmeent de la m�moire utilis�e par le bitmap des pixels ==> voir
    * releaseBitmap()
    */
   public void reloadBitmap() throws Exception {
      if( bitpix == 0 ) return; // De fait du JPEG
      if( pixels != null ) return;
      if( !bitmapReleaseDone ) throw new Exception(
            "no releaseBitmap done before");
      testBitmapReleaseFeature();
      // System.out.println("reloadBitmap() size="+width+"x"+height+"x"+Math.abs(bitpix)/8+" offset="+bitmapOffset+" de "+filename);
      RandomAccessFile f = null;
      try {
         f = new RandomAccessFile(filename, "r");
         f.seek(bitmapOffset);
         int n = Math.abs(bitpix) / 8;
         pixels = new byte[widthCell * heightCell * n];

         // Lecture d'un coup
         if( !hasCell() ) f.readFully(pixels);

         // Lecture ligne � ligne pour m�moriser uniquement la cellule
         else {
            f.skipBytes(yCell * width * n);
            byte[] buf = new byte[width * n]; // une ligne compl�te
            for( int lig = 0; lig < heightCell; lig++ ) {
               f.readFully(buf);
               System.arraycopy(buf, xCell * n, pixels, lig * widthCell * n,
                     widthCell * n);
            }
         }
      } finally {
         if( f != null ) f.close();
      }

      bitmapReleaseDone = false;

   }

   private void testBitmapReleaseFeature() throws Exception {
      if( filename == null || bitmapOffset == -1 ) throw new Exception(
            "FITS stream not compatible (not a true file [" + filename + "])");
      if( !releasable ) throw new Exception(
            "FITS not compatible (compressed or reserved by user)");
      // if( xCell!=0 || yCell!=0 || widthCell!=width || heightCell!=height )
      // throw new Exception("Fits subcell not compatible ["+filename+"]");
      if( !(new File(filename)).canRead() ) throw new Exception(
            "FITS does not exist on disk [" + filename + "]");
   }

   /** Pour aider le GC */
   public void free() {
      pixels = null;
      // pix8 = null;
      rgb = null;
      calib = null;
      // center=null;
      headerFits = null;
      width = height = bitpix = 0;
      widthCell = heightCell = xCell = yCell = 0;
   }

   @Override
   public String toString() {
      return "Fits file: " + width + "x" + height + " bitpix=" + bitpix + " ["
            + xCell + "," + yCell + " " + widthCell + "x" + heightCell + "]";
   }

   // -------------------- C'est priv� --------------------------

   /**
    * D�termination du min et max des pixels pass�s en param�tre
    * @param En sortie : range[0]=minPixCut, range[1]=maxPixCut,
    *           range[2]=minPix, range[3]=maxPix;
    * @param pIn Tableau des pixels � analyser
    * @param bitpix codage FITS des pixels
    * @param width Largeur de l'image
    * @param height hauteur de l'image
    * @param minCut Limite min, ou 0 si aucune
    * @param maxCut limite max, ou 0 si aucune
    * @param autocut true si on doit appliquer l'autocut
    * @param ntest Nombre d'appel en cas de traitement r�cursif.
    */
   private void findMinMax(double[] range, byte[] pIn, int bitpix, int width,
         int height, double minCut, double maxCut, boolean autocut, int ntest)
         throws Exception {
      int i, j, k;
      boolean flagCut = (ntest > 0 || minCut != 0. && maxCut != 0.);

      // Recherche du min et du max
      double max = 0, max1 = 0;
      double min = 0, min1 = 0;

      // Marge pour l'�chantillonnage (on recherche min et max que sur les 1000
      // pixels centraux en
      // enlevant �ventuellement un peu de bord
      int MARGEW = (int) (width * 0.05);
      int MARGEH = (int) (height * 0.05);

      // LES DEUX LIGNES QUI SUIVENT SONT A COMMENTER SI ON VEUT ETRE SUR DE NE
      // PAS LOUPER
      // DES PARTICULARITES LOCALES SUR LES GROSSES IMAGES.
      if( width - 2 * MARGEW > 1000 ) MARGEW = (width - 1000) / 2;
      if( height - 2 * MARGEH > 1000 ) MARGEH = (height - 1000) / 2;

      double c;

      if( !autocut && (minCut != 0. || maxCut != 0.) ) {
         range[2] = min = minCut;
         range[3] = max = maxCut;

      } else {
         boolean first = true;
         long nmin = 0, nmax = 0;
         for( i = MARGEH; i < height - MARGEH; i++ ) {
            for( j = MARGEW; j < width - MARGEW; j++ ) {
               c = getPixValDouble(pIn, bitpix, i * width + j);

               // On ecarte les valeurs sans signification
               if( isBlankPixel(c) ) continue;

               if( flagCut ) {
                  if( c < minCut || c > maxCut ) continue;
               }

               if( first ) {
                  max = max1 = min = min1 = c;
                  first = false;
               }

               if( min > c ) {
                  min = c;
                  nmin = 1;
               } else if( max < c ) {
                  max = c;
                  nmax = 1;
               } else {
                  if( c == min ) nmin++;
                  if( c == max ) nmax++;
               }

               if( c < min1 && c > min || min1 == min && c < max1 ) min1 = c;
               else if( c > max1 && c < max || max1 == max && c > min1 ) max1 = c;
            }
         }

         if( autocut && max - min > 256 ) {
            if( min1 - min > max1 - min1 && min1 != Double.MAX_VALUE
                  && min1 != max ) min = min1;
            if( max - max1 > max1 - min1 && max1 != Double.MIN_VALUE
                  && max1 != min ) max = max1;
         }
         range[2] = min;
         range[3] = max;
      }

      if( autocut ) {
         int nbean = 10000;
         double l = (max - min) / nbean;
         int[] bean = new int[nbean];
         for( i = MARGEH; i < height - MARGEH; i++ ) {
            for( k = MARGEW; k < width - MARGEW; k++ ) {
               c = getPixValDouble(pIn, bitpix, i * width + k);

               j = (int) ((c - min) / l);
               if( j == bean.length ) j--;
               if( j >= bean.length || j < 0 ) continue;
               bean[j]++;
            }
         }

         // Selection du min et du max en fonction du volume de l'information
         // que l'on souhaite garder
         int[] mmBean = getMinMaxBean(bean);

         // Verification que tout s'est bien passe
         if( mmBean[0] == -1 || mmBean[1] == -1 ) {
            throw new Exception("beaning error");
         } else {
            min1 = min;
            max1 = mmBean[1] * l + min1;
            min1 += mmBean[0] * l;
         }

         if( mmBean[0] != -1 && mmBean[0] > mmBean[1] - 5 && ntest < 3 ) {
            if( min1 > min ) min = min1;
            if( max1 < max ) max = max1;
            findMinMax(range, pIn, bitpix, width, height, min, max, autocut,
                  ntest + 1);
            return;
         }

         min = min1;
         max = max1;
      }

      // Memorisation des parametres de l'autocut
      range[0] = min;
      range[1] = max;
   }

   /**
    * Determination pour un tableau de bean[] de l'indice du bean min et du bean
    * max en fonction d'un pourcentage d'information desire
    * @param bean les valeurs des beans provenant de l'analyse d'une image
    * @return mmBean[2] qui contient les indices du bean min et du bean max
    */
   private int[] getMinMaxBean(int[] bean) {
      double minLimit = 0.003; // On laisse 3 pour mille du fond
      double maxLimit = 0.9995; // On laisse 1 pour mille des etoiles
      int totInfo; // Volume de l'information
      int curInfo; // Volume courant en cours d'analyse
      int[] mmBean = new int[2]; // indice du bean min et du bean max
      int i;

      // Determination du volume de l'information
      for( totInfo = i = 0; i < bean.length; i++ ) {
         totInfo += bean[i];
      }

      // Positionnement des indices des beans min et max respectivement
      // dans mmBean[0] et mmBean[1]
      for( mmBean[0] = mmBean[1] = -1, curInfo = i = 0; i < bean.length; i++ ) {
         curInfo += bean[i];
         double p = (double) curInfo / totInfo;
         if( mmBean[0] == -1 ) {
            if( p > minLimit ) mmBean[0] = i;
         } else if( p > maxLimit ) {
            mmBean[1] = i;
            break;
         }
      }

      return mmBean;
   }

   private Coord cTmp = new Coord();

   private String filename;

   private void setPixValInt(byte[] t, int bitpix, int i, int val) {
      int c;
      switch( bitpix ) {
         case 8:
            t[i] = (byte) (0xFF & val);
            break;
         case 16:
            i *= 2;
            c = val;
            t[i] = (byte) (0xFF & (c >> 8));
            t[i + 1] = (byte) (0xFF & c);
            break;
         case 32:
            i *= 4;
            setInt(t, i, val);
            break;
         case -32:
            i *= 4;
            c = Float.floatToIntBits(val);
            setInt(t, i, c);
            break;
         case -64:
            i *= 8;
            long c1 = Double.doubleToLongBits(val);
            c = (int) (0xFFFFFFFFL & (c1 >>> 32));
            setInt(t, i, c);
            c = (int) (0xFFFFFFFFL & c1);
            setInt(t, i + 4, c);
            break;
      }
   }

   protected void setPixValDouble(byte[] t, int bitpix, int i, double val) {
      int c;
      switch( bitpix ) {
         case -32:
            setInt(t, i << 2, Float.floatToIntBits((float) val));
            break;
         case 16:
            i *= 2;
            c = (int) val;
            t[i] = (byte) (0xFF & (c >> 8));
            t[i + 1] = (byte) (0xFF & c);
            break;
         // case -32: i*=4;
         // c=Float.floatToIntBits((float)val);
         // setInt(t,i,c);
         // break;
         case 32:
            i *= 4;
            setInt(t, i, (int) val);
            break;
         case 8:
            t[i] = (byte) (0xFF & (int) val);
            break;
         case -64:
            i *= 8;
            long c1 = Double.doubleToLongBits(val);
            c = (int) (0xFFFFFFFFL & (c1 >>> 32));
            setInt(t, i, c);
            c = (int) (0xFFFFFFFFL & c1);
            setInt(t, i + 4, c);
            break;
      }
   }

   private int getPixValInt(byte[] t, int bitpix, int i) {
      try {
         switch( bitpix ) {
            case 8:
               return (t[i]) & 0xFF;
            case 16:
               i *= 2;
               return ((t[i]) << 8) | (t[i + 1]) & 0xFF;
            case 32:
               return getInt(t, i * 4);
            case -32:
               return (int) Float.intBitsToFloat(getInt(t, i * 4));
            case -64:
               i *= 8;
               long a = (((long) getInt(t, i)) << 32)
                     | ((getInt(t, i + 4)) & 0xFFFFFFFFL);
               return (int) Double.longBitsToDouble(a);
         }
         return 0;
      } catch( Exception e ) {
         return 0;
      }
   }

   private double getPixValDouble(byte[] t, int bitpix, int i) {
      try {
         switch( bitpix ) {
            case -32:
               return Float.intBitsToFloat(getInt(t, i << 2));
            case 16:
               i *= 2;
               return (((t[i]) << 8) | (t[i + 1]) & 0xFF);
            case 32:
               return getInt(t, i * 4);
            case 8:
               return ((t[i]) & 0xFF);
            case -64:
               i *= 8;
               long a = (((long) getInt(t, i)) << 32)
                     | ((getInt(t, i + 4)) & 0xFFFFFFFFL);
               return Double.longBitsToDouble(a);
         }
         return 0.;
      } catch( Exception e ) {
         return DEFAULT_BLANK;
      }
   }

   private void setInt(byte[] t, int i, int val) {
      t[i] = (byte) (0xFF & (val >>> 24));
      t[i + 1] = (byte) (0xFF & (val >>> 16));
      t[i + 2] = (byte) (0xFF & (val >>> 8));
      t[i + 3] = (byte) (0xFF & val);
   }

   private int getInt(byte[] t, int i) {
      return ((t[i]) << 24) | (((t[i + 1]) & 0xFF) << 16)
            | (((t[i + 2]) & 0xFF) << 8) | (t[i + 3]) & 0xFF;
   }

   /** Coadditionne les pixels (pix8[], pixels[] et rgb[] */
   public void coadd(Fits a) throws Exception {
      int taille = widthCell * heightCell;

      if( a.pixels != null && pixels != null ) {
         for( int i = 0; i < taille; i++ ) {
            double v1 = getPixValDouble(pixels, bitpix, i);
            double v2 = a.getPixValDouble(a.pixels, bitpix, i);
            double v = isBlankPixel(v1) ? v2 : a.isBlankPixel(v2) ? v1
                  : (v1 + v2) / 2;
            setPixValDouble(pixels, bitpix, i, v);
         }
      }
      if( a.rgb != null && rgb != null ) {
         for( int i = 0; i < taille; i++ ) {
            int t = ((0xFF000000 & a.rgb[i]) | (0xFF000000 & rgb[i])) != 0 ? 0xFF000000
                  : 0;
            int r = (0x00FF0000 & a.rgb[i]) + (0x00FF0000 & rgb[i]) / 2;
            int g = (0x0000FF00 & a.rgb[i]) + (0x0000FF00 & rgb[i]) / 2;
            int b = (0x000000FF & a.rgb[i]) + (0x000000FF & rgb[i]) / 2;
            rgb[i] = t | r | g | b;
         }
      }
   }

   /** Coadditionne les pixels (pix8[], pixels[] et rgb[] */
   public void coadd(Fits a, double[] weight1, double[] weight2)
         throws Exception {
      int taille = widthCell * heightCell;

      if( a.pixels != null && pixels != null ) {
         for( int i = 0; i < taille; i++ ) {
            double v1 = getPixValDouble(pixels, bitpix, i);
            double v2 = a.getPixValDouble(a.pixels, bitpix, i);
            double fct1 = weight1[i] / (weight1[i] + weight2[i]);
            double fct2 = weight2[i] / (weight1[i] + weight2[i]);
            weight1[i] += weight2[i];
            weight2[i] = 0;
            double v = isBlankPixel(v1) ? v2 : a.isBlankPixel(v2) ? v1 : v1
                  * fct1 + v2 * fct2;
            setPixValDouble(pixels, bitpix, i, v);
         }
      }
      if( a.rgb != null && rgb != null ) {
         for( int i = 0; i < taille; i++ ) {
            double fct1 = weight1[i] / (weight1[i] + weight2[i]);
            double fct2 = weight2[i] / (weight1[i] + weight2[i]);
            weight1[i] += weight2[i];
            weight2[i] = 0;
            int t = ((0xFF000000 & rgb[i]) | (0xFF000000 & a.rgb[i])) != 0 ? 0xFF000000
                  : 0;
            int r = (int) (((rgb[i] >> 16) & 0xFF) * fct1 + ((a.rgb[i] >> 16) & 0xFF)
                  * fct2) << 16;
            int g = (int) (((rgb[i] >> 8) & 0xFF) * fct1 + ((a.rgb[i] >> 8) & 0xFF)
                  * fct2) << 8;
            int b = (int) ((rgb[i] & 0xFF) * fct1 + (a.rgb[i] & 0xFF) * fct2);
            rgb[i] = t | r | g | b;
         }
      }
   }

   /**
    * Remplace les pixels (pix8[], pixels[] et rgb[] pour les nouvelles valeurs
    * != NaN
    */
   public void overwriteWith(Fits a) throws Exception {
      int taille = widthCell * heightCell;

      if( a.pixels != null && pixels != null ) {
         for( int i = 0; i < taille; i++ ) {
            double v = a.getPixValDouble(pixels, bitpix, i);
            if( a.isBlankPixel(v) ) v = -100; // continue;
            setPixValDouble(pixels, bitpix, i, v);
            if( a.rgb != null && rgb != null ) rgb[i] = a.rgb[i];
         }
      }
   }

   /** Ajoute les pixels (pix8[], pixels[] et rgb[] sur les valeurs NaN */
   public void mergeOnNaN(Fits a) throws Exception {
      int taille = widthCell * heightCell;

      if( a.pixels != null && pixels != null ) {
         for( int i = 0; i < taille; i++ ) {
            double v1 = getPixValDouble(pixels, bitpix, i);
            if( !isBlankPixel(v1) ) continue;

            double v = a.getPixValDouble(a.pixels, bitpix, i);
            setPixValDouble(pixels, bitpix, i, v);

            if( a.rgb != null && rgb != null ) rgb[i] = a.rgb[i];
         }
      }
   }

   public void setFilename(String filename) {
      if( filename == null ) {
         this.filename = null;
         return;
      }
      int p = filename.lastIndexOf('[');
      if( p >= 0 ) filename = filename.substring(0, p);
      this.filename = filename;
   }

   public String getFileNameExtended() {
      return getFilename() + getCellSuffix();
   }

   public String getFilename() {
      return filename;
   }

   public void write(String file, double pixelMin, double pixelMax, byte[] tcm,
         String format) throws Exception {
      createDir(file);
      FileOutputStream fos = null;
      try {
         fos = new FileOutputStream(new File(file));
         write(fos, pixelMin, pixelMax, tcm, format);
      } finally { if( fos!=null )  fos.close(); }
      this.setFilename(file);
   }

   public void write(OutputStream os, double pixelMin, double pixelMax,
         byte[] tcm, String format) throws Exception {
      BufferedImage imgTarget;
      byte[] r = new byte[16];
      for( int i = 0; i < r.length; i++ )
         r[i] = (byte) (i * 16);
      // IndexColorModel cm = new IndexColorModel(8, 256, r, r, r, 0);
      // byte [] pix8 = toPix8(pixelMin, pixelMax, r, PIX_255);
      IndexColorModel cm = new IndexColorModel(4, 16, r, r, r);
      byte[] pix8 = toPix4(pixelMin, pixelMax, null);
      // for( int i=0; i<pix8.length; i++ ) pix8[i] = (byte)(i%16);
      // for( int y=100; y<200; y++ ) for( int x=300; x<450; x++ ) pix8[
      // y*width+x ] = 0;
      WritableRaster raster = Raster.createPackedRaster(DataBuffer.TYPE_BYTE,
            width, height, 1, 4, null);
      raster.setDataElements(0, 0, width, height, pix8);

      // imgTarget = new
      // BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY);
      imgTarget = new BufferedImage(width, height,
            BufferedImage.TYPE_BYTE_INDEXED, cm);
      imgTarget.setData(raster);

      ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
      ImageWriteParam iwp = writer.getDefaultWriteParam();
      if( format.equals("jpeg") ) {
         iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         iwp.setCompressionQuality(0.95f);
      }

      ImageOutputStream out = null;
      try { 
         out = ImageIO.createImageOutputStream(os);
         writer.setOutput(out);
         writer.write(null, new IIOImage(imgTarget, null, null), iwp);
         writer.dispose();
      } finally { if( out!=null ) out.close(); }
   }

   public static void convert(String filename) throws Exception {
      Fits f = new Fits();
      f.loadJpeg(filename, true);
      invImageLine(f.width, f.height, f.rgb);
      for( int i = 0; i < f.rgb.length; i++ )
         f.rgb[i] |= 0xFF000000;
      f.writeRGBcompressed(filename + ".jpg", "jpeg");
   }

   public static void main(String[] args) {
      try {
         convert("C:\\Users\\Pierre\\Desktop\\PLANCK\\HFIColor100-217-545\\Norder3\\Allsky.jpg");
      } catch( Exception e ) {
         e.printStackTrace();
      }
   }

}
