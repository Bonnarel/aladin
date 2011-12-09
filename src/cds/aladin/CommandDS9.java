// Copyright 2010 - UDS/CNRS
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


package cds.aladin;

import java.util.*;

import cds.tools.Util;

/**
 * Gestion des équivalences de commandes entre DS9 et Aladin
 * @version 1.0 (dec 2011) Creation
 * @author  P.Fernique [CDS]
 */
public final class CommandDS9  {

   private Aladin a;

   public CommandDS9(Aladin aladin) {
      this.a=aladin;
   }
   
   /** Traduit une commande DS9 en l'équivalent Aladin
    * @param s La commande DS9 (une seule)
    * @return le script Aladin correspondant (éventuellement plusieurs commandes),
    *         null s'il ne s'agit pas d'une commande DS9 reconnue
    * @throws Exception (en cas d'erreur de parsing)
    */
   public String translate(String s) throws Exception {
      StringBuffer s1 = new StringBuffer();
      Tok tok = new Tok(s,";");
      while( tok.hasMoreTokens() ) {
         if( s1.length()>0 ) s1.append(';');
         String a = translateOne(tok.nextToken());
         if( a==null ) return null;
         s1.append(a);
      }
      return s1.toString();
   }
   
   public String translateOne(String s) throws Exception {
      s = trimCom(s);
      if( s==null ) return null;
      String cmd = (new Tok(s,"( ")).nextToken();
      
      if( cmd.equalsIgnoreCase("circle") 
       || cmd.equalsIgnoreCase("line") 
       || cmd.equalsIgnoreCase("polygon")
       || cmd.equalsIgnoreCase("box")
       || cmd.equalsIgnoreCase("global")
       || cmd.equalsIgnoreCase("ellipse") )  return basicDS9toAladin(s);
      if( cmd.equalsIgnoreCase("rotbox") )   return rotboxCIAtoAladin(s);
      if( cmd.equalsIgnoreCase("ruler") )    return rulerDS9toAladin(s);
      if( cmd.equalsIgnoreCase("point") )    return pointDS9toAladin(s);
      if( cmd.equalsIgnoreCase("vector") )   return basicDS9toAladin(s);
      if( cmd.equalsIgnoreCase("text") )     return textDS9toAladin(s);
      if( cmd.equalsIgnoreCase("composite") )return compositeDS9toAladin(s);
      if( cmd.equalsIgnoreCase("format:") )  return formatSAOTNtoAladin(s);
      if( cmd.equalsIgnoreCase("region") )   return formatCIAOtoAladin(s);
      if( cmd.equalsIgnoreCase("fk5") 
       || cmd.equalsIgnoreCase("j2000")
       || cmd.equalsIgnoreCase("fk4")
       || cmd.equalsIgnoreCase("b1950") 
       || cmd.equalsIgnoreCase("galactic")
       || cmd.equalsIgnoreCase("ecliptic")
       || cmd.equalsIgnoreCase("icrs")
       || cmd.equalsIgnoreCase("image"))     return frameDS9toAladin(s);
      return null;
   }
   
   private String trimCom(String s) {
      if( s==null ) return null;
      int i,n=s.length();
      char c;
      for( i=0; i<n && ((c=s.charAt(i))=='#' || c=='+' || Character.isSpace(c)); i++ );
      return i==0 ? s : i==n ? null : s.substring(i);
   }

   // Commande ruler
   // in : ruler(05:34:40.816,+22:01:18.78,05:34:15.778,+21:58:13.80) ruler=physical physical color=blue
   // out: draw dist(05:34:40.816,+22:01:18.78,05:34:15.778,+21:58:13.80)
   private String rulerDS9toAladin(String cmd) throws Exception {
      int i = cmd.indexOf("ruler");
      return basicDS9toAladin("dist"+cmd.substring(i+5));
   }

   // Commande rotbox (CIAO)
   // in : rotbox(05:34:45.666,+22:01:15.28,1.12529',0.755795',360)
   // out: draw box(05:34:45.666,+22:01:15.28,1.12529',0.755795',360)
   private String rotboxCIAtoAladin(String cmd) throws Exception {
      int i = cmd.indexOf("rot");
      return basicDS9toAladin(cmd.substring(i+3));
   }

   // Commande region (CIAO)
   // in : Region file format: CIAO version 1.0
   // out: setconf frame=J2000
   private String formatCIAOtoAladin(String cmd) throws Exception  {
      if( cmd.indexOf("CIAO version")<0 ) return null;
      return "setconf frame=J2000";
   }

   static private String SAOTNFORMATERROR = "!!!SAOTN compatibility error (format parsing failed)";

   // Commande format (SAOTN)
   // in : format: degrees (fk4)
   // out: set frame=b1950
   private String formatSAOTNtoAladin(String cmd) throws Exception  {
      int deb = cmd.lastIndexOf('(');
      int fin = cmd.indexOf(')',deb);
      if( deb<0 || fin<0 ) throw new Exception(SAOTNFORMATERROR);
      return frameDS9toAladin(cmd.substring(deb+1,fin));
   }

  // Commande "text"
  // in : text(83.646667,22.01715) color=red width=2 text={du texte}
  // in : text(83.433687,22.014895, "du texte") # green
  // out: draw string(83.646667,22.01715,"du texte")
  private String textDS9toAladin(String cmd) throws Exception {
     boolean saotng=false;
     int fin=0,deb = cmd.lastIndexOf("text={");
     if( deb<0 ) saotng=true;
     else {
        deb+=6;
        fin = cmd.indexOf("}",deb);
     }
     Tok tok = new Tok(cmd,"( ,)");
     tok.nextToken();
     String s = "draw string("+tok.nextToken()+","+tok.nextToken()+","+
               Tok.quote(saotng? tok.nextToken():cmd.substring(deb,fin))+")";
//     a.trace(4,"Command.textDS9toAladin() "+cmd+" => "+s);
     return s;
  }
  
  // Commande "point"
  // in : point(83.667432,22.012471) || # point=boxcircle
  // out: draw tag(83.646667,22.01715)
  private String pointDS9toAladin(String cmd) throws Exception {
     Tok tok = new Tok(cmd,"( ,)");
     tok.nextToken();       // On passe "point"
     return "draw tag("+tok.nextToken()+","+tok.nextToken()+")";
  }
  
  // Commande "global"
  // in : composite(83.667432,22.012471,23.324274) || composite=1
  // out: draw newfov(83.667432,22.012471)
  private String compositeDS9toAladin(String cmd) throws Exception {
     Tok tok = new Tok(cmd,"( ,)");
     tok.nextToken();
     return "draw newfov("+tok.nextToken()+","+tok.nextToken()+")";
  }
  
  // Commande du système de coordonnées (fk5,fk4,icrs,galactic,ecliptic,image)
  // in : fk5
  // out: setconf frame=J2000;draw mode(RADEC)
  private String frameDS9toAladin(String cmd) throws Exception {
          if( cmd.equals("fk5")
           || cmd.equals("j2000") )    return("setconf frame=J2000");
     else if( cmd.equals("icrs") )     return("setconf frame=ICRS");
     else if( cmd.equals("fk4")
           || cmd.equals("b1950") )    return("setconf frame=B1950");
     else if( cmd.equals("galactic") ) return("setconf frame=Gal");
     else if( cmd.equals("ecliptic") ) return("setconf frame=Ecliptic");
     else if( cmd.equals("image") )    return("setconf frame=XY");
     else return null;
  }

  // Commandes basiques directement réutilisable. On se contente de gérer l'éventuelle
  // absence de parenthèses
  // in : circle 293.00068 485.00134 31.69569  || ... # ...
  // out: draw circle(293.00068,485.00134,31.69569)
  private String basicDS9toAladin(String cmd) throws Exception {
     // On enlève ce qu'il y a au bout de la ligne (commentaire, paramètres...)
     int c1 = cmd.indexOf('#');
     int c2 = cmd.indexOf('|');
     int c3 = cmd.indexOf(')');
     int c = c3>0 ? c3+1 : c1>0 && c2>0 ? Math.min(c1,c2) : c1>0 ? c1 : c2;
     if( c==-1 ) c = cmd.length();
     cmd = cmd.substring(0,c).trim();
     Tok tok = new Tok(cmd,"( ,)");
     StringBuffer s = new StringBuffer("draw "+tok.nextToken()+"(");
     boolean first=true;
     while( tok.hasMoreTokens() ) {
        if( !first ) s.append(',');
        first=false;
        s.append(Tok.quote(tok.nextToken()));
     }
     s.append(')');
     return s.toString();
  }
  
  static final private String [] TEST = {
//   "# Region file format: DS9 version 4.1",
//   "# Filename: C:/Documents and Settings/Standard/Mes documents/Fits et XML/dss1.fits",
//   "global color=green dashlist=8 3 width=1 font=\"helvetica 10 normal\" select=1 highlite=1 dash=0 fixed=0 edit=1 move=1 delete=1 include=1 source=1",
//   "fk5",
//   "circle(83.660376,22.042708,31.940586\")",
//   "ellipse(83.585086,22.03882,79.491097\",38.786247\",351.70206)",
//   "box(83.690273,22.020911,67.517674\",45.347692\",359.61543)",
//   "polygon(83.63402,22.012313,83.621943,22.012238,83.622024,22.001041,83.634101,22.001117)",
//   "polygon 83.610533 22.011943 83.584443 22.011775",
//   "line(83.661236,22.006322,83.671597,21.992668) # line=0 0",
//   "ecliptic;line(83.646735,22.007633,83.627889,21.983442) # line=0 0",
//   "#  vector(83.702004,22.049953,94.710207\",20.171478) vector=1",
//   "# text(83.646667,22.01715) color=red width=2 text={Region}",
//   "# composite(83.667432,22.012471,23.324274) || composite=1",
//   "point(83.667432,22.012471) || # point=boxcircle",
//   "  point  83.667432  22.012471  ",
//   "text(84.059752,21.854908) || textangle=22.939706 text={S0}",
//   "polygon(84.099463,21.761356,83.958987,21.817998,84.019991,21.948451,84.16057,21.891758) ||",
//   "",
   "b1950; circle 82.907937d 22.010159d 31.940586",

  };
  
  public static void main(String []argv) {
     CommandDS9 ds9 = new CommandDS9(null);
     for( int i=0; i<TEST.length; i++ ) System.out.println(TEST[i]);
     System.out.println();
     for( int i=0; i<TEST.length; i++ ) {
        String s;
        try { s = ds9.translate(TEST[i]);
        } catch( Exception e ) { s="Exception: "+e.getMessage(); e.printStackTrace(); }
        System.out.println(s==null?"null":s);
     }
  }
}

