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

package cds.fits;

import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import cds.aladin.Aladin;
import cds.aladin.FrameHeaderFits;
import cds.aladin.MyInputStream;
import cds.aladin.Save;

/**
 * Classe dediee a la gestion d'un header FITS.
 *
 * @author Pierre Fernique [CDS]
 * @version 1.7 : Jul 2018 : Prise en compte des descriptions des champs
 * @version 1.6 : D�c 2007 : Possibilit� de surcharger les mots cl�s
 * @version 1.5 : 20 aout 2002 methode readFreeHeader
 * @version 1.4 : 19 juin 00 Utilisation du PushbackInputStream et
 *                implantation de isHCOMP()
 * @version 1.3 : (6 juin 2000) format HCOMPRESS
 * @version 1.2 : (20 mars 2000) prise en compte du champ EQUINOX enquote
 * @version 1.1 : (14 jan 99) affichage du header fits dans un frame
 * @version 0.9 : (18 mai 99) Creation
 */
public final class HeaderFits {

   private StringBuilder   memoHeaderFits = null;  // Memorisation de l'entete FITS telle quelle (en Strings)
   
  /** Les elements de l'entete */
   protected Hashtable header;     // Valeur pour chaque cl�
   protected Hashtable headDescr;  // Description de chaque cl�
   protected Vector<String> keysOrder;

   /** La taille de l'entete FITS (en octets) */
    private int sizeHeader=0;

    /** Cr�ation du header Fits � partir de rien */
    public HeaderFits() {
        alloc();
    }

  /** Creation du header.
   */
    public HeaderFits(MyInputStream dis) throws Exception {
      readHeader(dis);
   }

    public HeaderFits(String s) throws Exception {
       readFreeHeader(s);
    }

    public HeaderFits(String s,FrameHeaderFits frameHeaderFits) throws Exception {
       readFreeHeader(s,false,frameHeaderFits);
    }

    public HeaderFits(MyInputStream dis,FrameHeaderFits frameHeaderFits) throws Exception {
       readHeader(dis,frameHeaderFits);
    }
    
    /** Retourne le header FITS original (en Strings) */
    public String getOriginalHeaderFits() { return memoHeaderFits.toString(); }
    
    /** M�morise le header FITS original (en Strings) */
    public void setOriginalHeaderFits(String s) { memoHeaderFits= new StringBuilder(s); }

   /** Ajoute la ligne courante a la memorisation du header FITS
    * en supprimant les blancs en fin de ligne
    * @param s la chaine a ajouter
    */
    public void appendMHF(String s) {
       if( memoHeaderFits==null ) memoHeaderFits=new StringBuilder();
       memoHeaderFits.append(s.trim()+"\n");
    }


  /** Taille en octets de l'entete FITS.
   * Uniquemenent mis a jour apres readHeader()
   * @return La taille de l'entete
   */
   public int getSizeHeader() { return sizeHeader; }
   
   /** retourne la table de hash des mots cl�s */
   public Hashtable<String,String> getHashHeader() { return header; }
   
   /** Retourne un �numerateur sur la liste des mots cl�s (ordonn�s) */
   public Enumeration<String> getKeys() { return keysOrder.elements(); }

  /** Extraction de la valeur d'un champ FITS. Si on commence par une quote, va jusqu'� la
   * prochaine quote, sinon jusqu'au commentaire, ou sinon la fin de la ligne
   * @param buffer La ligne
   * @return La valeur
   */
   static public String getValue(String s) {
      byte [] a = new byte[80];
      int i;
      for( i=0; i<s.length(); i++ ) a[i]=(byte)s.charAt(i);
      while( i<80 ) a[i++]=(byte)' ';
      return getValue(a);
   }
   
   /** Extraction de la valeur d'un champ FITS. Si on commence par une quote, va jusqu'� la
    * prochaine quote, sinon jusqu'au commentaire, ou sinon la fin de la ligne
    * @param buffer La ligne
    * @return La valeur
    */
//   static public String getValue(byte [] buffer) {
//       int i;
//       boolean quote = false;
//       boolean blanc=true;
//       int offset = 9;
//
//       for( i=offset ; i<80; i++ ) {
//          if( !quote ) {
//             if( buffer[i]==(byte)'/' ) break;   // on a atteint le commentaire
//          } else {
//             if( buffer[i]==(byte)'\'') break;   // on a atteint la prochaine quote
//          }
//
//          if( blanc ) {
//             if( buffer[i]!=(byte)' ' ) blanc=false;
//             if( buffer[i]==(byte)'\'' ) { quote=true; offset=i+1; }
//          }
//       }
//       return (new String(buffer, 0, offset, i-offset)).trim();
//   }
   
   /** Extraction de la valeur d'un champ FITS. va jusqu'au commentaire, ou sinon la fin de la ligne
    * Vire les quotes si n�cessaires. Vire les blancs apr�s.
    * @param buffer La ligne
    * @return La valeur
    */
   static public String getValue(byte [] buffer) {
      int mode=0;
      int tailSpace=0;
      boolean end=false;
      boolean oQuote=false;
      
      StringBuilder s1 = new StringBuilder(80);
      
      for( int i=9; i<buffer.length && i<80 && !end; i++ ) {
         char ch= (char) buffer[i];
         switch(mode) {
            case 0: // Avant la valeur
               if( ch!=' ' ) {
                  mode=1;
                  if( ch=='\'' ) mode=1;
                  else { mode=2; s1.append(ch); }
               }
               break;
            case 1: // Dans la valeur quot�e
               if( ch=='\'' ) {
                  
                  // Premi�re quote ?
                  if( !oQuote ) {
                     
                     // Pas de quote apr�s => c'est termin�
                     char nCh = (char)( i>=buffer.length-1 || i>=79 ? ' ' : buffer[i+1] );
                     if( nCh!='\'' ) { mode=2; break; }
                     
                  // Deuxi�me quote
                  } else { oQuote=false; break; }
               }
               oQuote = ch=='\'';
               s1.append(ch);
               break;
            case 2: // Dans la valeur non quot� (ou fin de valeur quot�e)
               if( ch=='/' ) { end=true; break;  } // je suis arriv� au commentaire
               if( ch==' ' ) tailSpace++;
               else tailSpace=0;
               s1.append(ch);
               break;
         }
      }
      return s1.substring(0, s1.length()-tailSpace);
   }



   /** Extraction de la description d'un champ FITS.
    * @return La description, ou null si aucune
    */
   static public String getDescription(byte [] buffer) {
      int mode=0;
      int deb=-1;
      char och=' ';
      
      for( int i=9; i<buffer.length && i<80 && deb<0; i++ ) {
         char ch= (char) buffer[i];
         switch(mode) {
            case 0: // Dans le champ valeur
               if( ch=='/' ) deb=i+1;
               else if( ch=='\'' ) mode=1;
               break;
            case 1: // Dans une valeur quot�e
               if( ch=='\'' && och!='\\' ) mode=0;
               break;
         }
         och=ch;
      }
      if( deb<0 ) return null;
      String s = new String(buffer,deb,buffer.length-deb).trim();
      if( s.length()==0 ) return null;
      return s;
   }


   /** retourne la cl� d'une ligne d'entete FITS */
   static public  String getKey(byte [] buffer) {
      return new String(buffer, 0, 0, 8).trim();
   }

   /** retourne la cl� d'une ligne d'entete FITS */
   static public String getKey(String s) {
      if( s.length()<8 ) return s;
      return s.substring(0,8).trim();
   }
   
   /** Lecture d'une entete PDS */
   public boolean readHeaderPDS(MyInputStream dis,FrameHeaderFits frameHeaderFits) throws Exception {
      
      int linesRead=0;
      alloc();
      try {
         while( true ) {
            String s = dis.readLine();
            linesRead++;
            if( s.length()==0 ) continue;
//            System.out.println("["+s+"]");
            if( s.trim().equals("END") ) return true;
            if( frameHeaderFits!=null ) frameHeaderFits.appendMHF(s);
            int i = s.indexOf('=');
            if( i<0 ) continue;
            String key = s.substring(0,i-1).trim();
            String value = s.substring(i+1).trim();
            header.put(key, value);
            keysOrder.addElement(key);
         }
      } catch( Exception e ) {
         Aladin.error="PDS header error (line "+(linesRead+1)+")";
         if( Aladin.levelTrace>=3 ) e.printStackTrace();
         throw new Exception();
      }
   }

  /** Lecture de l'entete FITS.
   * Mise a jour de tableau associatif header.
   * @param dis Flux de donnees
   * @return true si OK, false sinon
   */
   public boolean readHeader(MyInputStream dis) throws Exception { return readHeader(dis,null); }
   public boolean readHeader(MyInputStream dis,FrameHeaderFits frameHeaderFits) throws Exception {
      int blocksize = 2880;
      int fieldsize = 80;
      String key, value, desc;
      int linesRead = 0;
      sizeHeader=0;
//      boolean firstLine=true;

//Aladin.trace(3,"Reading FITS header");
      byte[] buffer = new byte[fieldsize];

      alloc();
      try {
         while (true) {
            dis.readFully(buffer);
//System.out.println(Thread.currentThread().getName()+":"+linesRead+":["+new String(buffer,0)+"]");
            key =  getKey(buffer);
            if( linesRead==0 && !key.equals("SIMPLE") && !key.equals("XTENSION") ) {
//               System.out.println("pb: key="+key+" s="+new String(buffer,0));
               throw new Exception("probably not a FITS file");
            }
            sizeHeader+=fieldsize;
            linesRead++;
            if( key.equals("END" ) ) break;
            appendMHF(new String(buffer,0));
            
            value=getValue(buffer);
            desc = null;
            
            // Pour supporter la convention CONTINUE
            if( key.equals("CONTINUE") && buffer[8] != '=') {
               int n = keysOrder.size();
               if( n==0 ) {
                  //                  throw new Exception("FITS CONTINUE convention error: no previous keyword");
                  System.err.println("FITS CONTINUE convention error: no previous keyword => ignored");
               } else {
                  String lastKey = keysOrder.get( n-1 );
                  String lastValue = (String ) header.get( lastKey );
                  n = lastValue.length();
                  if( n==0 || lastValue.charAt(n - 1)!='&' ) {
                     //                  throw new Exception("FITS CONTINUE convention error: & missing");
                     System.err.println("FITS CONTINUE convention error: & missing => ignored");
                  } else {
                     value = lastValue.substring(0,n-1)+value;
                     key=lastKey;
                  }
               }
               
            } else {
               if( buffer[8] != '=' ) continue;
               desc=getDescription(buffer);
            }
//Aladin.trace(3,key+" ["+value+"]");
            header.put(key, value);
            if( desc!=null ) headDescr.put(key, desc);
            keysOrder.addElement(key);
         }

        // Test s'il s'agit de FITS Hcompresse 
        if( dis.isHCOMP() ) return true;
        
         // On passe le bourrage eventuel
         int bourrage = blocksize - sizeHeader%blocksize;
         if( bourrage!=blocksize ) {
            byte [] tmp = new byte[bourrage];
            dis.readFully(tmp);
            sizeHeader+=bourrage;
         }
      } catch( Exception e ) {
//System.out.println("lig="+(linesRead+1)+" "+new String(buffer,0));
// CETTE VARIABLE error AURAIT DU ETRE NON STATIC ET PASSE VIA LA REFERENCE A ALADIN
// SI ON VEUT POUVOIR UTILISER CORRECTEMENT PLUSIEURS INSTANCES D'ALADIN
         if( linesRead==0 ) Aladin.error="Remote server message:\n"+new String(buffer,0);
         else {
            Aladin.error="Fits header error (line "+(linesRead+1)+")";
//            if( Aladin.levelTrace>=3 ) e.printStackTrace();
         }
         throw e;
      }

      return true;
   }

   /** Recherche d'un caract�re c dans buf � partir de la position from. Retourne
    * la position courante si on trouve un \n avant et que l'on atteint la limite finLigne
    * ou buf.length. Dans le cas de recherche du caract�re '/' (pour les commentaires FITS),
    * il est ignor� s'il se trouve dans une chaine quot�e par '
    * @param buf buffer de recherche
    * @param from offset de d�part
    * @param finLigne offset de fin
    * @param c caract�re � rechercher
    * @return position de c, ou de \n ou de finLigne
    */
   static private int getPos(char buf[],int from,int finLigne,char c) {
      int max = buf.length;
      boolean inQuote= from+2<buf.length && buf[from+2]=='\'' && c=='/';
      int deb=from;
      while( from<max && from<finLigne && (buf[from]!=c || buf[from]==c && inQuote)
            && buf[from]!='\n' ) {
         if( from>deb+2 && buf[from]=='\'' && buf[from-1]!='\'' ) inQuote=false;
         from++;
      }
      return from;
   }
   
   /** Lecture d'une ent�te Fits quelque soit sa structure. Soit des lignes ASCII s�par�es
    * par des \n, soit des lignes de 80 caract�res sans \n. Le = n'est pas obligatoirement
    * en 8�me position.
    * Mise a jour de tableau associatif header
    * @param s La chaine contenant le header Fits
    * @return true si OK, false sinon
    */
   public boolean readFreeHeader(String s) { return readFreeHeader(s,false,null); }
   public boolean readFreeHeader(String s,boolean specialDSS,FrameHeaderFits frameHeaderFits) {
      alloc();
      int len=79;
      char buf [] = s.toCharArray();
      int i=0;
      String key,value,com;
      boolean first=true;
      int a,b,c;
      while( i<buf.length ) {
         
         // Si on ne commence pas par SIMPLE, on l'ajoute sinon �a posera souci au cas
         // o� l'on sauvegarde en FITS par la suite une image PNG ou JPEG avec ent�te par .hhh
         if( first ) {
            first=false;
            if( buf.length>i+7 && !(new String(buf,i,6)).equals("SIMPLE") 
                  && !(new String(buf,i,6)).equals("XTENSI")) {
               appendMHF((new String(Save.getFitsLine("SIMPLE","T",null))).trim());
            }
         }
         
         // Cas particulier d'une ligne vide
         c=getPos(buf,i,i+len,'\n');
         if( (new String(buf,i,c-i)).trim().length()==0 ) {
            appendMHF("");
            

         // Cas particulier pour COMMENT XXXX
         } else if( buf.length>i+7 && (new String(buf,i,7)).equals("COMMENT") ) {
            a=i+7;
            c = getPos(buf,a,i+len,'\n');
            com = (c-a>0) ? (new String(buf,a+1,c-a-1)).trim() : "";
            appendMHF((new String(Save.getFitsLineComment(com))).trim());

         // Cas particulier pour HISTORY XXXX
         } else if( buf.length>i+7 && (new String(buf,i,7)).equals("HISTORY") ) {
               a=i+7;
               c = getPos(buf,a,i+len,'\n');
               com = (c-a>0) ? (new String(buf,a+1,c-a-1)).trim() : "";
               appendMHF((new String(Save.getFitsLineHistory(com))).trim());

            // Cas g�n�ral
         } else {
            a = getPos(buf,i,i+len,'=');
            b = getPos(buf,a,i+len,'/');
            c = getPos(buf,b,i+len,'\n');
            if( i!=a || i!=b || i!=c ) {
               key = new String(buf,i,a-i).trim();
               value = (b-a>0 ) ? (new String(buf,a+1,b-a-1)).trim() : "";
               com = (c-b>0) ? (new String(buf,b+1,c-b-1)).trim() : "";
               //System.out.println(i+":"+a+"["+key+"]="+b+"["+value+"]/"+c+"["+com+"]");
               if( key.equals("END") ) {
                  value=com=null;
                  break;
               }
               
               // Dans le cas d'une ent�te DSS dans un fichier ".hhh" il ne faut pas retenir les mots
               // cl�s concernant l'astrom�trie de la plaque enti�re
               //            if( !( specialDSS && (key.startsWith("AMD") || key.startsWith("PLT"))) ) {
               
               header.put(key, value);
               if( com.length()>0 ) headDescr.put(key, com);
               keysOrder.addElement(key);
               //            }
               appendMHF((new String(Save.getFitsLine(key, value, com))).trim());
            }
         }
         i=c+1;
      }
      
      // NORMALEMENT C'EST CORRIGE PAR BOF - PF f�v 2011
//      if( specialDSS ) purgeAMDifRequired();
      
      return true;
   }
   
   // HORRIBLE PATCH (Pierre)
   // Dans le cas des ent�tes .hhh associ�es aux imagettes DSS, il y a souvent deux calibrations, non compatibles
   // dans ce cas, je supprime celle de la plaque et je ne garde que celle de l'imagette.
//   private void purgeAMDifRequired() {
//      if( header.get("CRPIX1")==null ) return; 
//      
//      System.err.println("*** Double calibration on DSS image => remove AMD/PLT one");
//      Vector<String> nKeysOrder = new Vector<String>();
//      for( String key : keysOrder ) {
//         if( key.startsWith("AMD") || key.startsWith("PLT") ) header.remove(key);
//         else nKeysOrder.addElement(key);
//      }
//      keysOrder = nKeysOrder;
//   }

   /**
    * Teste si un mot cl� est pr�sent dans l'ent�te
    * @param key la cl� � tester
    * @return true si la cl� est pr�sente
    */
   public boolean hasKey(String key) {
      return header.get(key.trim())!=null;
   }


  /** Recherche d'un element entier par son mot cle
   * @param key le mot cle  (inutile de l'aligner en 8 caract�res)
   * @return la valeur recherchee
   */
   public int getIntFromHeader(String key)
                 throws NumberFormatException,NullPointerException {
      String s;
      int result;

      s = (String) header.get(key.trim());
      result = (int)Double.parseDouble(s.trim());
      return result;
   }

  /** Extrait les elements d'un floattant.
   * Purge d'eventuels ' et blancs avant et apres
   */
   private String trimDouble(String s) {
      char [] a = s.toCharArray();
      int i;				// offset du debut
      int j;				// offset de fin
      char ch;				// tmp

      // On cherche le signe ou le premier chiffre
      for( i=0; i<a.length; i++ ) {
         ch = a[i];
         if( ch=='+' || ch=='-' || ch=='.' || (ch>='0' && ch<='9' ) ) break;
      }

      // on cherche le dernier chiffre ou un '.'
      for( j=a.length-1; j>=i; j-- ) {
         ch=a[j];
         if( (ch>='0' && ch<='9' ) || ch=='.' ) { j++; break; }
      }

      return new String(a,i,j-i);
   }

   /** Surcharge ou ajout d'un mot cl� */
   public void setKeyword(String key,String value) {
      header.put(key,value);
   }

   /** Surcharge ou ajout d'un mot cl� */
   public void setKeyword(String key,String value,String description) {
      header.put(key,value);
      if( !keysOrder.contains(key) ) keysOrder.add(key);
      headDescr.put(key,description);
   }

  /** Recherche d'un element double par son mot cle
   * @param key le mot cle (inutile de l'aligner en 8 caract�res)
   * @return la valeur recherchee
   */
   public double getDoubleFromHeader(String key)
                 throws NumberFormatException,NullPointerException {
      String s;
      double result;

      s = (String) header.get(key.trim());
      result = Double.valueOf(trimDouble(s)).doubleValue();
      return result;
   }
   
   /** Enl�ve les quotes d'une valeur String FITS (si n�cessaire) */
   static public String unquoteFits( String s ) {
      int n;
      if( s==null || (n=s.length())<2 ) return s;
      char c = s.charAt(0);
      if( c!='\'' || c!=s.charAt(n-1) ) return s;
      
      char [] a = s.toCharArray();
      StringBuilder s1 = new StringBuilder(a.length);
      boolean quote=false;
      for( int i=1; i<n-1; i++ ) {
         c=a[i];
         if( quote && c=='\'' ) { quote=false; continue; }
         else s1.append(c);
         quote= c=='\'';
      }
      return s1.toString();
   }
   
   /** Ajoute les quotes pour mettre en forme une chaine */
   static public String quoteFits( String s ) {
      if( s==null ) return s;
      StringBuilder s1 = new StringBuilder( s.length()+10 );
      s1.append('\'');
      for( char a : s.toCharArray() ) {
         if( a=='\'' ) s1.append('\'');
         s1.append(a);
      }
      s1.append('\'');
      return s1.toString();
   }

   /** Recherche d'une chaine par son mot cle
    * @param key le mot cle  (inutile de l'aligner en 8 caract�res)
    * @return la valeur recherchee
    */
    public String getStringFromHeader(String key) throws NullPointerException {
       String s = (String) header.get(key.trim());
       if( s==null || s.length()==0 ) return s;
//       if( s.charAt(0)=='\'' ) return s.substring(1,s.length()-1).trim();
//       return s;
       return unquoteFits(s).trim();
    }

    /** Recherche de la description d'une entr�e par son mot cle
     * @param key le mot cle  (inutile de l'aligner en 8 caract�res)
     * @return la valeur recherchee
     */
     public String getDescriptionFromHeader(String key) throws NullPointerException {
        String s = (String) headDescr.get(key.trim());
        return s;
     }

   /** Ajout, surcharge ou suppression d'un mot cle
    * @param key le mot cl� (inutile de l'aligner en 8 caract�res)
    * @param value la valeur � positionner, null si suppression
    */
   public void setToHeader(String key,String value) {
      if( value==null ) header.remove(key);
      else header.put(key.trim(),value);
   }

   /** Ajoute/remplace/supprime un couple (MOTCLE,VALEUR) - l'ordre des mots cl�s
    * est m�moris� dans keysOrder, et les valeurs sont stock�es dans header
    * Ra : VALEUR=null signifie une suppression */
   public void setKeyValue(String key, String value) {

      // Suppression ?
      if( value==null ) {
         if( !hasKey(key) ) return;
         header.remove(key);
         keysOrder.remove(key);
         return;
      }

      // Ajout
      if( !hasKey(key) ) keysOrder.addElement(key);
      header.put(key, value);
   }
   
   /** Retourne le DATASUM de l'ent�te si pr�sent */
   public String getDataSum() { return (String)header.get("DATASUM"); }
   
   /** M�morise le DATASUM et la date associ�e
    * @param dataSum  Le DATASUM sous la forme d'une chaine �quivalente � UINT32
    * @param dateISO le commentaire correspondant � la date ISO de g�n�ration de ce DATASUM
    */
   public void addDataSum(String dataSum, String dateISO) {
      setKeyword("DATASUM",dataSum,dateISO);
   }

   /** Ecriture de l'ent�te FITS des mots cl�s m�moris�s. L'ordre est conserv�
    * comme � l'origine - les commentaires sont restitu�s 
    * @return le nombre d'octets �crits */
   public int writeHeader(OutputStream os ) throws Exception {
      int n=keysOrder.size()*80;
      byte [] b= getEndBourrage(n);
      byte buf [] = new byte[n + b.length];
      
      int m=0;
      Enumeration e = keysOrder.elements();
      while( e.hasMoreElements() ) {
         String key = (String)e.nextElement();
         String value = (String) header.get(key);
         if( value==null ) continue;
         String desc = (String) headDescr.get(key);
         System.arraycopy(getFitsLine(key,value,desc),0,buf,m,80 );
         m+=80;
      }
      System.arraycopy(b,0,buf,m,b.length);
      n+=b.length;
      os.write(buf);
      return n;
   }

   /** G�n�ration de la fin de l'ent�te FITS, c�d le END et le byte de bourrage
    * pour que cela fasse un multiple de 2880.
    * @param headSize taille actuelle de l'ent�te
    */
  static public byte [] getEndBourrage(int headSize) {
      int size = 2880 - headSize%2880;
      if( size<3 ) size+=2880;
      byte [] b = new byte[size];
      b[0]=(byte)'E'; b[1]=(byte)'N';b[2]=(byte)'D';
      for( int i=3; i<b.length; i++ ) b[i]=(byte)' ';
      return b;
   }

  /**
   * Mise en forme d'une ligne pour une ent�te FITS. Prends en compte si la valeur
   * est num�rique, String et m�me �ventuellement String d�j� quot� � la FITS
   * @param key La cl�
   * @param value La valeur
   * @param comment Un �ventuel commentaire, sinon ""
   * @return la chaine de 80 caract�res au format FITS
   */
  static public byte [] getFitsLine(String key, String value) {
     return getFitsLine(key,value,null);
  }
  static public  byte [] getFitsLine(String key, String value, String comment) {
     int i=0,j;
     char [] a;
     byte [] b = new byte[80];

     // Le mot cle
     a = key.toCharArray();
     for( j=0; i<8; j++,i++) b[i]=(byte)( (j<a.length)?a[j]:' ' );

     // La valeur associee
     if( value!=null ) {
        b[i++]=(byte)'='; b[i++]=(byte)' ';

        a = value.toCharArray();

        // Valeur num�rique => alignement � droite
        if( !isFitsString(value) && !key.equals("DATASUM") ) {
           for( j=0; j<20-a.length; j++)  b[i++]=(byte)' ';
           for( j=0; i<80 && j<a.length; j++,i++) b[i]=(byte)a[j];

        // Chaine de caract�res => formatage
        } else {
           a = formatFitsString(a);
           for( j=0; i<80 && j<a.length; j++,i++) b[i]=(byte)a[j];
           while( i<30 ) b[i++]=(byte)' ';
        }
     }

     // Le commentaire
     if( comment!=null && comment.length()>0 ) {
        if( value!=null ) { b[i++]=(byte)' ';b[i++]=(byte)'/'; b[i++]=(byte)' '; }
        a = comment.toCharArray();
        for( j=0; i<80 && j<a.length; j++,i++) b[i]=(byte) a[j];
     }

     // Bourrage
     while( i<80 ) b[i++]=(byte)' ';

     return b;
  }

  /**
   * Test si c'est une chaine � la FITS (ni num�rique, ni bool�en)
   * @param s la chaine � tester
   * @return true si s est une chaine ni num�rique, ni bool�enne
   * ATTENTION: NE PREND PAS EN COMPTE LES NOMBRES IMAGINAIRES
   */
  static private boolean isFitsString(String s) {
     if( s.length()==0 ) return true;
     char c = s.charAt(0);
     if( s.length()==1 && (c=='T' || c=='F') ) return false;   // boolean
     if( !Character.isDigit(c) && c!='.' && c!='-' && c!='+' && c!='E' && c!='e' ) return true;
     try {
        Double.valueOf(s);
        return false;
     } catch( Exception e ) { return true; }
  }

  /**
   * Mise en forme d'une chaine pour une ent�te FITS en suivant la r�gle suivante:
   * si mot plus petit que 8 lettres, bourrage de blancs
   * utilisation de quotes simples + double quote simple � l'int�rieur
   * @param a la chaine a mettre en forme. Elle peut �tre d�j� quot�e
   * @return la chaine mise en forme
   */
  static private char [] formatFitsString(char [] a) {
     if( a.length==0 ) return a;
     StringBuffer s = new StringBuffer();
     int i;
     boolean flagQuote = a[0]=='\''; // Chaine d�j� quot�e ?

     s.append('\'');

     // recopie sans les quotes
     for( i= flagQuote ? 1:0; i<a.length- (flagQuote ? 1:0); i++ ) {
        if( !flagQuote && a[i]=='\'' ) s.append('\'');  // Double quotage
        s.append(a[i]);
     }

     // bourrage de blanc si <8 caract�res + 1�re quote
     for( ; i< (flagQuote ? 9:8); i++ ) s.append(' ');

     // ajout de la derni�re quote
     s.append('\'');

     return s.toString().toCharArray();
  }
  
  /** Retourne true si l'ent�te ne contient encore rien */
  public boolean isEmpty() { return header.size()==0; }

  /** Allocation ou r�allocation des structures de m�morisation */
  protected void alloc() {
//     if( header!=null && keysOrder!=null ) return;
     header = new Hashtable(200);
     headDescr = new Hashtable(200);
     keysOrder = new Vector(200);
  }
  
  /** Copie des donn�es du Header dans le Header pass� en param�tre (�crasement des donn�es pr�c�dentes �ventuelles) */
  protected void copyTo( HeaderFits out ) {
     out.header = (Hashtable) header.clone();
     out.headDescr = (Hashtable) headDescr.clone();
     out.keysOrder = (Vector) keysOrder.clone();
     
  }
  
  /** Retourne la taille m�moire approximative */
  public long getMem() { return 16+(keysOrder==null?0:keysOrder.size()*50); }



}
