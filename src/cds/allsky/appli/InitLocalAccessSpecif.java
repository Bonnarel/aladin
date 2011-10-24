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


package cds.allsky.appli;

import java.text.ParseException;

import cds.allsky.Context;
import cds.allsky.BuilderIndex;
import cds.tools.Util;

public class InitLocalAccessSpecif extends BuilderIndex {
   
	/**
	 * Lance l'initialisation de l'index healpix pour des besoins particuliers
	 * Le chemin de sortie est celui donn� en entr�e + ALLSKY + r�pertoire HpxFinder
	 * on peut passer une expression r�guli�re pour d�finir les fichiers � traiter
	 * (utlis�e via Pattern.matches)
	 * @param args chemin_entr�e order 'N W S E' chemin_sortie
	 * @see #AllskyConst.HPX_FINDER
	 */
	public static void main(String[] args) {
		long t=System.currentTimeMillis();
		if (args.length<3) {
			System.out.println("Usage : chemin_entr�e order 'N W S E' chemin_sortie");
			System.exit(0);
		}
		String pathSource = args[0]+ Util.FS;
		String pathDest = pathSource;
		pathDest = args[3];
		String border = args[2];
		int order = Integer.parseInt(args[1]);

		Context allsky = new Context();
		try {
			allsky.setBorderSize(border);
		} catch (ParseException e) {
			System.err.println(e);
		}
		allsky.setOrder(order);
		
		BuilderIndex init = new BuilderIndex(allsky);
		init.build();
		System.out.println("done => "+(System.currentTimeMillis()-t)+"ms");
	}
}
