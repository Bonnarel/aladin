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


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.StringTokenizer;

import cds.aladin.MyProperties;
import cds.aladin.Tok;
import cds.moc.SMoc;
import cds.tools.pixtools.Util;

public class BuilderCube extends Builder {

   private String inputPath [];
   private Mode mode = Mode.COPY;

   private int nbFmt=1;         // Nombre de formats de tuiles concern�s
   private boolean hasFITS=false,hasPNG=false,hasJPEG=false;    // Formats d�j� rencontr�s
   private int statNbFile;
   private long startTime,totalTime;

   public BuilderCube(Context context) {
      super(context);
   }

   public Action getAction() { return Action.CUBE; }

   public void run() throws Exception {
      build();
      
      context.resetCheckCode();

      if( !context.isTaskAborting() ) {
         BuilderMoc bm = new BuilderMoc(context);
         bm.createMoc( context.getOutputPath() );
         context.moc=bm.moc;
      }
      if( !context.isTaskAborting() ) context.writeMetaFile();
   }

   // Pour les stats, retourne le nombre de formats de tuiles rencontr�s et ou estim�s
   private int getNbFmt() {
      int n = 0;
      if( hasFITS ) n++;
      if( hasJPEG ) n++;
      if( hasPNG ) n++;
      return Math.max(n,nbFmt);
   }

   // Demande d'affichage des stats (dans le TabRgb)
   public void showStatistics() {
      context.showJpgStat( statNbFile/getNbFmt(), totalTime,0,0);
   }

   public void validateContext() throws Exception {

      boolean propFound=false;

      // d�coupage et v�rification de la liste des HiPS sources (s�par�s par des ";")
      String s = context.getInputPath();
      Tok st = new Tok(s,";");
      inputPath = new String [st.countTokens()];
      for( int i=0; i<inputPath.length; i++ ) {
         String path = inputPath[i]=st.nextToken();
         if( !(new File(path)).isDirectory() ) throw new Exception("Input HiPS error ["+path+"]");

         // M�morisation ou check du order
         int order = Util.getMaxOrderByPath( path );
         if( i==0 && context.order==-1) context.order = order;
         else if( order<context.order ) context.warning("Input HiPS ["+path+" => order="+order+"] not enough depth => ignored");

         // M�morisation des propri�t�s � partir du premier HiPS
         if( !propFound ) {
            String propFile = path+Util.FS+Constante.FILE_PROPERTIES;
            context.prop = new MyProperties();
            File f = new File( propFile );
            if( f.exists() ) {
               if( !f.canRead() ) throw new Exception("Propertie file not available ! ["+propFile+"]");
               InputStreamReader in = new InputStreamReader( new BufferedInputStream( new FileInputStream(propFile) ), "UTF-8");
               context.prop.load(in);
               in.close();
               propFound=true;

               // Pour la stat d'avancement => une id�e du nombre de format de tuiles
               try {
                  String fmt = context.prop.getProperty(Constante.KEY_HIPS_TILE_FORMAT);
                  if( fmt==null ) fmt = context.prop.getProperty(Constante.OLD_HIPS_TILE_FORMAT);
                  int n = (new StringTokenizer(fmt," ")).countTokens();
                  if( n>1 ) nbFmt=n;
               } catch( Exception e ) { }
            }
         }

         // R�cup�ration des noms des bandes
         String lab= getALabel(path,null);
         context.setPropriete(Constante.KEY_OBS_TITLE+"_"+i, lab);

         // Estimation du MOC final (union)
         try {
            SMoc m = new SMoc();
            m.read( path+Util.FS+Constante.FILE_MOC);
            if( context.moc==null ) context.moc=m;
            else context.moc = context.moc.union(m);
         } catch( Exception e ) {
            context.warning("Missing original MOC in "+path+" => running time estimation will be wrong");
         }
      }

      // Check du r�pertoire de destination
      validateOutput();
      //      validateFrame();
      validateLabel();

      // Ajout des param�tres propres aux cubes
      context.depth=inputPath.length;

      // Mode de travail (link ou copy)
      Mode m = context.getMode();
      if( m==Mode.getDefault() ) mode=Mode.COPY;   // Le d�faut de TILES est remplac� par le d�faut de CUBE
      else mode=m;
      if( mode!=Mode.COPY && mode!=Mode.LINK ) {
         throw new Exception("Coadd mode ["+mode+"] not supported for CUBE action");
      }
      context.info(mode.getExplanation(mode));
   }

   private void initStat() { statNbFile=0;  startTime = System.currentTimeMillis(); }


   // Mise � jour des stats
   private void updateStat() {
      statNbFile++;
      totalTime = System.currentTimeMillis()-startTime;
   }

   public void build() throws Exception  {
      initStat();
      String output = context.getOutputPath();
      String outputHpxFinder = output+Util.FS+Constante.FILE_HPXFINDER;

      for( int z=0; z<context.depth; z++ ) {
         String input = inputPath[z];

         for( int order = 0; order<=context.getOrder(); order++ ) {
            treeCopy(input, output, "Norder"+order,z);
         }

         String inputHpxFinder = input+Util.FS+Constante.FILE_HPXFINDER;
         if( (new File(inputHpxFinder).isDirectory()) ) {
            for( int order = 0; order<=context.getOrder(); order++ ) {
               treeCopy(inputHpxFinder, outputHpxFinder, "Norder"+order,z);
            }
         }

      }
   }

   // Parcours r�cursive du cube source input � copier dans output avec comme profondeur z
   private void treeCopy(String input, String output, String path,int z) throws Exception {
      File src = new File( input+Util.FS+path );
      if( !src.exists() ) return;

      // proc�dure r�cursive par r�pertoire
      if( src.isDirectory() ) {

         File [] list = src.listFiles();
         for( int i=0; i<list.length; i++ ) {
            String s1 = list[i].getName();
            treeCopy(input,output,path+Util.FS+s1,z);
         }
         return;
      }

      // Copie simple
      String s = src.getName();
      int pos = s.lastIndexOf('.');
      String ext  = pos<0 ? "" : s.substring(pos);
      String name = pos<0 ? s  : s.substring(0,pos);
      String suffixe = z==0 ? "" : "_"+z;

      if( !hasPNG && ext.equals(".png") ) hasPNG=true;
      else if( !hasFITS && ext.equals(".fits") ) hasFITS=true;
      else if( !hasJPEG && ext.equals(".jpg") ) hasJPEG=true;

      String subPath = (new File(path)).getParent();

      (new File(output+Util.FS+subPath)).mkdirs();
      File trg = new File( output+Util.FS+subPath+Util.FS+name+suffixe+ext );

      if( mode==Mode.LINK ) link(src,trg);
      else copy(src,trg);

      updateStat();
   }

   static private void link(File src, File trg) throws Exception {

      // Lorsqu'on sera compatible 1.7 uniquement
      Files.createSymbolicLink(trg.toPath(), src.toPath() );

      // En attendant, on travaille par "r�flexion"
//      Method toPath = File.class.getDeclaredMethod("toPath",new Class[]{});
//      Object pathSrc = toPath.invoke((Object)src, new Object[] {});
//      Object pathTrg = toPath.invoke((Object)trg, new Object[] {});
//      Class files = Class.forName("java.nio.file.Files");
//      Class path  = Class.forName("java.nio.file.Path");
//      Class fileAttribute = Class.forName("java.nio.file.attribute.FileAttribute");
//      Object [] attrib = (Object[])Array.newInstance(fileAttribute, 0);
//      Class<?>[] a = { path, path, attrib.getClass() };
//      Method createSymbolicLink = files.getDeclaredMethod("createSymbolicLink", a);
//      createSymbolicLink.invoke((Object)null, new Object[] { pathSrc, pathTrg, attrib });
   }


//      static public void main(String [] args) {
//         try {
//            File trg = new File("VISTA");
//            File src = new File("C:\\Users\\Pierre\\Desktop\\Link");
//            src.delete();
//            link(src,trg);
//         } catch( Exception e ) {
//            e.printStackTrace();
//         }
//      }

   // Copie du fichier
   private void copy(File src, File trg) throws Exception {
      RandomAccessFile r = null;
      byte buf [] =null;
      try {
         r = new RandomAccessFile(src, "r");
         buf = new byte[ (int)src.length() ];
         r.read( buf );
      } finally { if( r!=null ) r.close(); }

      r=null;
      try {
         r = new RandomAccessFile(trg, "rw");
         r.write(buf);
      }
      catch( Exception e ) { e.printStackTrace(); }
      finally { if( r!=null ) r.close(); }
   }
}
