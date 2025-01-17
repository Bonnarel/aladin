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

import cds.astro.Astrocoo;
import cds.astro.Coo;
import cds.tools.Util;

/**
 * Manipulation des coordonnees
 *
 * @author Francois Bonnarel [CDS], Pierre Fernique [CDS]
 * @version 1.0 : (5 mai 99) Toilettage du code
 * @version 0.9 : (??) creation
 */
public final class Coord {
   /** Ascension droite (J2000 en degres) */
   public double al ;
   /** Declinaison (J2000 en degres) */
   public double del ;
   /** abcisse courante dans une projection (en reel) */
   public double x;
   protected double dx;
   /** ordonnee courante dans une projection (en reel)*/
   public double y;
   protected double dy;
   /** 1ere coordonnee standard */
   protected double xstand ;
   /** 2eme coordonnee standard */
   protected double ystand ;

   // L'objet de traitement de la coordonnees
   static Astrocoo coo = new Astrocoo();

   /** Creation */
   public Coord() {}
   public Coord(double ra,double dej) { al=ra; del=dej; coo.set(al,del); }

   /** Creation et affection al,del en fonction d'une chaine sexagesimale
    * ou degre */
//   public Coord(Aladin aladin,String sexa) throws Exception {
//      
//      // La coordonn�e peut �tre pr�c�d� par le frame � la FOX (ex: ICRS: 12 34 1.9 +5 4 5.6)
//      int frameSrc=aladin.localisation.getFrame();
//      int frameDst=frameSrc;
//      int i = sexa.indexOf(':');
//      if( i>0 ) {
//         String prefix = sexa.substring(0,i);
//         if( Localisation.isFrameFox(prefix) ) {
//            frameSrc = Localisation.getFrameComboValue(prefix);
//            sexa = sexa.substring(i+1).trim();
//         }
//      }
//      
//      coo.set(sexa);
//      al  = coo.getLon();
//      del = coo.getLat();
//      
//      if( frameSrc!=frameDst ) {
//         Coord c = Localisation.frameToFrame(this, frameSrc, frameDst);
//         al=c.al;
//         del=c.del;
//      }
//   }
   
   public Coord(String sexa) throws Exception {
      coo.set(sexa);
      al  = coo.getLon();
      del = coo.getLat();
   }

   public boolean equals(Coord c) {
      if( c==null ) return false;
      return al==c.al && del==c.del;
   }

   /** Affichage sexagesimal de coordonnees passees en parametre.
    * @param al ascension droite
    * @param del declinaison
    * @return la chaine contenant la forme sexagesimale
    */
   public static String getSexa(double al, double del) { return getSexa(al,del,"s"); }

   /** Affichage sexagesimal de coordonnees passees en parametre.
    * @param al ascension droite
    * @param del declinaison
    * @param c le caractere seperateur des h,m,s,d
    * @return la chaine contenant la forme sexagesimale
    */
   public static String getSexa(double al, double del, String c) {
      Astrocoo coo = new Astrocoo();
      coo.set(al,del);
//      coo.setPrecision(Astrocoo.ARCSEC+1);
      try{
         String o = "2s"+(!c.equals(" ")?c:"");
         //System.out.println("al="+al+" del="+del+" Options="+o+" coo="+coo.toString(o+"f"));
         return coo.toString(o);
      } catch( Exception e ) { System.err.println(e); }
      return "";
   }
   
   /** Affichage en degr� de l'objet
    * @param 
    */
   public String getDeg() {
      Astrocoo coo = new Astrocoo();
      coo.set(al,del);
      coo.setPrecision(Astrocoo.ARCSEC+1);
      try{
         String o = "2d";
         return coo.toString(o);
      } catch( Exception e ) { System.err.println(e); }
      return "";
      
//     return Util.myRound(al)+" "+(del>=0?"+":"")+Util.myRound(del);
   }

   /** Affichage sexagesimal de l'objet.
    * @param c le caractere seperateur des h,m,s,d
    * @return la chaine contenant la forme sexagesimale
    */
   public String getSexa() { return getSexa(""); }
   public String getSexa(String c) { return getSexa(al,del,c); }
   
   /** Retourne les coordonn�es sous forme degr�s plan�taires
    * ex: N 12.2345, E 1.6378*/
   public String getDegPlanet() {
      String lat = Util.myRound(Math.abs(del));
      String latL = del<0 ? "S" : "N";
      double al1 = al>180 ? al - 360 : al<-180 ? al + 360 : al;
      String lng = Util.myRound(Math.abs(al1));
      String lngL = al1<0 ? "W" : "E";
      return lat+latL+", "+lng+lngL;
   }
   
   /** Retourne les coordonn�es sous forme sexag�simal (degr�s) plan�taire
    * ex: 12�34'5.23 N", 1�3'2.22 E" */
   public String getSexaPlanet() {
      String lat = getSexaD(del);
      String latL = del<0 ? "S" : "N";
      double al1 = al>180 ? al - 360 : al<-180 ? al + 360 : al;
      String lng = getSexaD(al1);
      String lngL = al1<0 ? "W" : "E";
      return lat+" "+latL+", "+lng+" "+lngL;
   }
   
   /** Retourne un r�el sous forme sexag�simal (degr�s) plan�taire
    * ex: 12�3'21.5"
    * @param x
    * @return
    */
   public String getSexaD(double x) {
      x = Math.abs(x);
      int deg = (int)x;
      double minx = (x - deg)*60.;
      int min = (int)minx;
      double secx = (minx - min)*60.;      
      return deg+"�"+min+"'"+Util.myRound(secx)+"\"";
   }

   public String toString() { return getSexa(); }


   /** Retourne RA en sexagesimal, separateur par defaut :
    * @param sep le separateur des champs
    * @return RA en sexagesimal
    */
   public String getRA() { return getRA(':'); }
   public String getRA(char sep) {
      try {
         String s = getSexa(sep+"");
         int i = s.indexOf('+');
         if( i==-1 ) i=s.indexOf('-');
         return s.substring(0,i-1);
      } catch( Exception e ) { }
      return "";
   }

   /** Retourne DE en sexagesimal, separateur par defaut :
    * @param sep le separateur des champs
    * @return DE en sexagesimal
    */
   public String getDE() { return getDE(':'); }
   public String getDE(char sep) {
      try {
         String s = getSexa(sep+"");
         int i = s.indexOf('+');
         if( i==-1 ) i=s.indexOf('-');
         return s.substring(i);
      } catch( Exception e ) { }
      return "";
   }

   /** Affichage dans la bonne unite.
    * Retourne un angle en d�cimal sous forme de chaine dans la bonne unite
    * @param x l'angle (en degr�s)
    * @return l'angle dans une unite coherente + l'unite utilisee
    */
   public static String getUnit(double x) { return getUnit(x,false,false); }
   public static String getUnit(double x,boolean entier,boolean flagSurface) {
      if( x==0 ) return "";
      String s=null;
      double fct = flagSurface ? 3600 : 60;
      double fct1 = flagSurface ? 100000 : 1000;
      if( Math.abs(x)>=1.0 ) s="�";
      if( Math.abs(x)<1.0 ) { s="'"; x=x*fct; }
      if( Math.abs(x)<1.0 ) { s="\""; x=x*fct; }
      if( Math.abs(x)<1.0 ) { s="mas"; x=x*fct1; }
      if( Math.abs(x)<1.0 ) { s="�as"; x=x*fct1; }

      if( entier && ((int)x)!=0 ) return ((int)x)+s;

      s=Util.myRound(x)+s;

      return s;
   }

   /** Affichage dans la bonne unite (H:M:S).
    * Retourne un angle en degres sous forme de chaine dans la bonne unite
    * @param x l'angle
    * @return l'angle dans une unite coherente + l'unite utilisee
    */
   public static String getUnitTime(double x) {
      String s=null;
      if( x>=1.0 ) s="h";
      if( x<1.0 ) { s="min"; x=x*60.0; }
      if( x<1.0 ) { s="s"; x=x*60.0; }
      x=((int)(x*100.0))/100.0;
      s=x+" "+s;

      return s;
   }

   /** Calcul d'un distance entre deux points reperes par leurs coord
    * @param c1 premier point
    * @param c2 deuxieme point
    * @return La distance angulaire en degres
   protected static double getDist1(Coord c1, Coord c2) {
      double dra = c2.al-c1.al;
      double dde = Math.abs(c1.del-c2.del);
      dra = Math.abs(dra);
      if( dra>180 ) dra-=360;
      double drac = dra*Astropos.cosd(c1.del);
      return Math.sqrt(drac*drac+dde*dde);
   }
    */

   public static double getDist(Coord c1, Coord c2) {
      return Coo.distance(c1.al,c1.del,c2.al,c2.del);
   }
}
