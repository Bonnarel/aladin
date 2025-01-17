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

public enum Action {

   INDEX     ("Build spatial index (in HpxFinder directory) + MOC index"),
   TINDEX    ("Build time index (in TimeFinder directory) + TMOC index"),                  // PROTO
   TILES     ("Build all true value pixel tiles (FITS) + Allsky.fits + MOC"),
   JPEG      ("Build all preview tiles (JPEG) + Allsky.jpg"),
   PNG       ("Build all preview tiles (PNG) + Allsky.png"),
   MOC       ("(Re)build the MOC (MultiOrder Coverage map)"),
   MOCERROR  ("Build the MOC of suspected erroneous FITS tiles (TEST)"),                   // PROTO
   MAP       ("Build an HEALPix map from the HiPS tiles"),
   MOCINDEX  ("(Re)build the index MOC (MultiOrder Coverage map) in HpxFinder directory"),
   MOCHIGHT  ("Build a high resolution output coverage map (MOC order=pixelRes)"),
   ALLSKY    ("(Re)build all Allsky files + index.html"),
   GZIP      ("Compress some FITS tiles and Allsky.fits"),                                 // DEPRECATED
   GUNZIP    ("Uncompress FITS tiles and Allsky.fits"),                                    // DEPRECATED
   CLEAN     ("Delete all Hips files (except properties file)"),
   CLEANALL  ("Delete all Hips files"),
   CLEANINDEX("Delete spatial index (HpxFinder dir)"),
   CLEANTINDEX("Delete temporal index (TimeFinder dir)"),
   CLEANDETAILS("Delete detail index (HpxFinder tree except last order dir)"),
   CLEANTILES("Delete all HiPS files except index (tiles, dir, Allsky, MOC, ...)"),
   CLEANFITS ("Delete all FITS tiles and Allsky.fits"),
   CLEANJPEG ("Delete all JPEG tiles and Allsky.jpg"),
   CLEANPNG  ("Delete all PNG tiles and Allsky.png"),
   CLEANWEIGHT ("Delete all WEIGHT tiles"),
   CLEANDATE ("Delete all tiles older than a specifical date"),
   TREE      ("(Re)build HiPS tree structure from already existing tiles"),
   APPEND    ("Append new images/cubes to an already existing HiPS"),
   CONCAT    ("Concatenate one HiPS to another HiPS"),
   CUBE      ("Create a HiPS cube based on a list of HiPS (; separated)"),
   DETAILS   ("Adapt HiPS index for supporting the \"detail table\" facility"),
   UPDATE    ("Upgrade HiPS metadata additionnal files to last HiPS standard"),
   CHECKCODE ("Compute+store the check codes (and the size) associated to the target HiPS"),
   UPDATEDATASUM  ("Add/update DATASUM in all FITS HiPS tiles"),
   PROP      ("Display HiPS properties files in HiPS version "+Constante.HIPS_VERSION+" syntax"), // DEBUG
   MIRROR    ("Mirror a remote HiPS locally"),
   RGB       ("Build and RGB HiPS based on 2 or 3 other HiPS"),
   CHECK     ("Verify the HiPS based on the check codes previously computed (CHECKCODE)"),
   CHECKDATASUM ("Verify the FITS tiles based on internal FITS DATASUM and the check code"),
   LINT      ("Check HiPS IVOA 1.0 standard compatibility"),
   ZIP       ("ZIP HiPS directories"),                                                     // PROTO
   //   INFO      ("Generate properties and index.html information files"),
   MAPTILES  ("Build all FITS tiles from a HEALPix Fits map"),
   FINDER,PROGEN,                                                                          // DEPRECATED
   ABORT, PAUSE, RESUME,
   
   SMOC      ("Build a SMOC.fits based on HpxFinder tile descriptions (TEST)"),            // PROTO
   TMOC      ("Build a TMOC.fits based on HpxFinder tile descriptions (TEST)"),            // PROTO
   STMOC     ("Build a STMOC.fits based on HpxFinder tile descriptions (TEST)");           // PROTO

   Action() {}
   Action(String s ) { doc=s; }

   private String doc;
   String doc() { return doc; }
   void startTime() { startTime=System.currentTimeMillis(); }
   void stopTime()  { stopTime=System.currentTimeMillis(); }
   long getDuree() {
      return (stopTime==0 ? System.currentTimeMillis():stopTime)-startTime;
   }

   public long startTime,stopTime=0L;
   public long nbFile;

}
