package cds.allsky;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.Properties;
import java.util.Set;

import cds.fits.Fits;
import cds.moc.HealpixMoc;
import cds.tools.pixtools.Util;

public class SkyGen {

   private File file;
   private Context context;

   int order = -1;
   private Action action;

   public SkyGen() {
      this.context = new Context();
   }


   /**
    * Analyse le fichier contenant les param�tres de config de la construction
    * du allsky sous le format : option = valeur
    * 
    * @throws Exception
    *             si l'erreur dans le parsing des options n�cessite une
    *             interrption du programme
    */
   private void parseConfig() throws Exception {

      // Extrait toutes les options du fichier
      // pour construire le contexte

      // Ouverture et lecture du fichier
      Properties properties = new Properties();
      Reader reader = new FileReader(file);
      properties.load(reader);

      Set<Object> keys = properties.keySet();
      for (Object opt : keys) {
         String val = properties.getProperty((String)opt);

         try {
            setContextFromOptions((String)opt, val);
         } catch (Exception e) {
            e.printStackTrace();
            break;
         }

      }

      reader.close();
   }

   /**
    * Lance quelques v�rifications de coh�rence entre les options donn�es
    * 
    * @throws Exception
    *             si une incoh�rence des options n�cessite une interrption du
    *             programme
    */
   private void validateContext() throws Exception {
      // ---- Qq v�rifications

      // arguments des r�pertoires de d�part
      if ( (action==Action.TILES || action==Action.INDEX || action==null) && context.getInputPath() == null) {
         throw new Exception("Argument \"input\" is missing");
         // Par d�faut le r�pertoire courant
         //context.setInputPath(System.getProperty("user.dir"));
      }
      // Par d�faut le r�pertoire courant
      if (context.getOutputPath() == null && context.getInputPath()!=null ) context.setOutputPath(context.getInputPath() + Constante.ALLSKY);

      // Deuxi�me v�rif
      if (context.getOutputPath() == null) {
         throw new Exception("Argument \"output\" is missing");
      }


      // donn�es d�j� pr�sentes ?
      if ( (action==Action.TILES || action==Action.INDEX || action==null) && !context.isExistingDir()) {
         throw new Exception("Input dir does NOT exist : " + context.getInputPath());
      }
      if (context.isExistingAllskyDir()) {
//         context.warning("Output dir already exists");
         if (context.getCoAddMode() == null) {
            if( action==Action.TILES || action==null ) context.warning("Default behaviour for computing pixels already computed : " + CoAddMode.getDefault());
            context.setCoAddMode(CoAddMode.getDefault());
         }
      }
      // � l'inverse, si il y a l'option "pixel"
      // ca laisse sous entendre que l'utilisateur pensait avoir dej� des
      // donn�es
      else if (context.getCoAddMode() != null) {
         context.warning("There is NO already computed tiles, option " + context.getCoAddMode() + " will be ignored");
      }
      

      // si on n'a pas d'image etalon, on la cherche + initialise avec
      if ( (action==null || action==Action.TILES || action==Action.INDEX || action==Action.ALLSKY) 
            && context.getImgEtalon()==null ) {
         
         double memoCut[] = context.getCut();
         boolean found=false;
         if( context.getInputPath()!=null ) {
           found = context.findImgEtalon(context.getInputPath());
            if (!found) {
               String msg = "There is no available images in source directory: " + context.getInputPath();
               context.warning(msg);
               throw new Exception(msg);
            }
         // On va d�terminer les cuts par les tuiles d�j� construites
         } else if( context.getOutputPath()!=null ) {
            String s = context.getOutputPath()+Util.FS+"Norder3"+Util.FS+"Dir0";
            found = context.findImgEtalon(s);
            if (!found) {
               String msg = "There is no tiles images in output directory ("+s+")";
               throw new Exception(msg);
            } else {
               context.warning("Using tiles images as reference image");
            }
         }
         
         // On remet le cut qui avait �t� explicitement indiqu� en param�tre
         if( found && memoCut!=null ) context.setCut(memoCut);

         if( action==null || action==Action.TILES || action==Action.INDEX ) {
            Fits file = new Fits();
            try {
               file.loadHeaderFITS(context.getImgEtalon());
               // calcule le meilleur nside/norder
               long nside = healpix.core.HealpixIndex.calculateNSide(file.getCalib().GetResol()[0] * 3600.);
               order = ((int) Util.order((int) nside) - Constante.ORDER);
            } catch (Exception e) {
              throw new Exception("Reference image calibration error ("+context.getImgEtalon()+")");
            }
         }
      }

      // si le num�ro d'order donn� est diff�rent de celui calcul�
      // attention n'utilise pas la m�thode context.getOrder car elle a un default � 3
      if (order != context.order && -1 != context.order) {
         context.warning("Order given (" + context.getOrder() + ") != auto (" + order + ")");
      } else {
         context.setOrder(order);
      }
      // si le bitpix donn� est diff�rent de celui calcul�
      if (context.getBitpix() != context.getBitpixOrig()) {
         context.warning("Bitpix given (" + context.getBitpix() + ") != auto (" + context.getBitpixOrig() + ")");
      }

      // il faut au moins un cut (ou img) pour construire des JPEG ou ALLSKY
      if (context.getCut()==null && (action==Action.JPEG || action==Action.ALLSKY))
         throw new Exception("Range cuts unknown: option \"img\" or \"pixelCut\" are required");
   }

   /**
    * Affecte � un objet Context l'option de configuration donn�e
    *
    * @param opt
    *            nom de l'option
    * @param val
    *            valeur de l'option
    * @throws Exception
    *             si l'interpr�tation de la valeur n�cessite une interrption du
    *             programme
    */
   private void setContextFromOptions(String opt, String val) throws Exception {
      // enl�ve des �ventuels apostrophes ou guillemets
      val = val.replace("\'", "");
      val = val.replace("\"", "");
      System.out.println("OPTION: "+opt + "=" + val);

      // System.out.println(opt +" === " +val);
      if(opt.equalsIgnoreCase("h"))
         usage();
      else if (opt.equalsIgnoreCase("verbose"))
         Context.setVerbose(Integer.parseInt(val));
      else if (opt.equalsIgnoreCase("input"))
         context.setInputPath(val);
      else if (opt.equalsIgnoreCase("output"))
         context.setOutputPath(val);
      else if (opt.equalsIgnoreCase("blank"))
         context.setBlankOrig(Double.parseDouble(val));
      else if (opt.equalsIgnoreCase("order"))
         context.setOrder(Integer.parseInt(val));
      else if (opt.equalsIgnoreCase("pixel"))
         context.setCoAddMode(CoAddMode.valueOf(val.toUpperCase()));
      else if (opt.equalsIgnoreCase("bitpix"))
         context.setBitpix(Integer.parseInt(val));
      else if (opt.equalsIgnoreCase("region")) {
         if (val.endsWith("fits")) {
            HealpixMoc moc = new HealpixMoc();
            moc.read(val);
            context.setMoc(moc);
         }
         else context.setMoc(val);
      }
      else if (opt.equalsIgnoreCase("frame"))
         context.setFrameName(val);
      else if (opt.equalsIgnoreCase("skyval"))
         context.setSkyval(val);
      else if (opt.equalsIgnoreCase("border"))
         try {
            context.setBorderSize(val);
         } catch (ParseException e) {
            System.err.println(e.getMessage());
         }
         else if (opt.equalsIgnoreCase("pixelCut"))
            context.setPixelCut(val);
         else if (opt.equalsIgnoreCase("method"))
            context.setMethod(val);
         else if (opt.equalsIgnoreCase("dataCut")) {
            context.setDataCut(val);
         }
         else if (opt.equalsIgnoreCase("color"))
            context.setColor(Boolean.parseBoolean(val));
         else if (opt.equalsIgnoreCase("img")) {
            context.setImgEtalon(val);
         }
         else System.out.println("ERROR : unknown option " + opt);

   }

   enum Action {
      INDEX, TILES, JPEG, MOC, MOCINDEX, ALLSKY, GZIP, GUNZIP,
      FINDER  // Pour compatibilit�
   }

   public static void main(String[] args) {
      SkyGen generator = new SkyGen();
      int length = args.length;
      if (length == 0) {
         usage();
         return;
      }
      // extrait les options en ligne de commande, et les analyse
      for (String arg : args) {
         // si c'est dans un fichier
         String param = "-param=";
         if (arg.startsWith(param)) {
            try {
               generator.setConfigFile(arg.substring(param.length()));
            } catch (Exception e) {
               e.printStackTrace();
               return;
            }
            continue;
         }
         else if (arg.equalsIgnoreCase("-h") || arg.equalsIgnoreCase("-help")) {
            SkyGen.usage();
            return;
         }
         // toutes les autres options �crasent les pr�c�dentes
         else if (arg.contains("=")) {
            String[] opts = arg.split("=");
            try {
               // si il y a un - on l'enl�ve
               opts[0] = opts[0].substring(opts[0].indexOf('-') + 1);

               generator.setContextFromOptions(opts[0], opts[1]);
            } catch (Exception e) {
               e.printStackTrace();
               return;
            }
         }
         // les autres mots sont suppos�es des actions (si +ieurs, seule la
         // derni�re est gard�e)
         else {
            try {
               generator.action = Action.valueOf(arg.toUpperCase());
               if( generator.action==Action.FINDER ) generator.action=Action.INDEX;   // Pour compatibilit�
            } catch (Exception e) {
               e.printStackTrace();
               return;
            }
         }

      }

      System.out.println("ACTION: "+(generator.action==null?"All steps (index+tiles+jpeg+allsky+moc)":generator.action));

      try {
         generator.validateContext();
      } catch (Exception e) {
         System.out.println("FATAL ERROR: "+e.getMessage());
         return;
      }
      // lance les calculs
      generator.start();
   }

   private void start() {
      context.setIsRunning(true);
      
      if (action==null) {
         // aucune action d�finie -> on fait la totale (sauf jpg)
         action = Action.INDEX;
         start();
         action = Action.TILES;
         start();
         action = Action.JPEG;
         start();
         action = Action.MOC;
         start();
         return;
      }

      switch (action) {
         case INDEX : {
            System.out.println("*** Create HEALPix index...");
            BuilderIndex builder = new BuilderIndex(context);
            ThreadProgressBar progressBar = new ThreadProgressBar(builder);
            (new Thread(progressBar)).start();
            // laisse le temps au thread de se lancer
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            File f = new File(context.getHpxFinderPath()+Util.FS+"Norder"+order);
            if (f.exists()) context.warning("Found previous index => use it");
            else {
               builder.build();
               System.out.println("HEALPix index created !");
            }
            progressBar.stop();
            break;
         }
         case JPEG : {
            System.out.println("*** Create Jpeg tiles...");
            BuilderJpg builder = new BuilderJpg(null, context);
            ThreadProgressBar progressBar = new ThreadProgressBar(builder);
            (new Thread(progressBar)).start();
            // laisse le temps au thread de se lancer
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            builder.run();
            progressBar.stop();
            System.out.println("Jpeg tiles created !");
            // la construction du allsky est automatiquement faite par le builder
            break;
         }
         case MOC : {
            System.out.println("*** Create MOC covering generated tiles)...");
            BuilderMoc builder = new BuilderMoc();
            builder.createMoc(context.outputPath);
            System.out.println("Tile MOC created in "+context.outputPath);
            break;
         }
         case MOCINDEX : {
            System.out.println("*** Create MOC covering HEALPix index)...");
            BuilderMoc builder = new BuilderMoc();
            builder.createMoc(context.getHpxFinderPath());
            System.out.println("Index MOC created in "+context.getHpxFinderPath());
            break;
         }
         case GZIP : {
            System.out.println("*** Gzip all FITS tiles...");
            BuilderGzip gz = new BuilderGzip( context.getOutputPath(), context.getVerbose() );
            gz.gzip();
            System.out.println("Gzip done !");
            break;
         }
         case GUNZIP : {
            System.out.println("*** Gunzip all FITS tiles...");
            BuilderGzip gz = new BuilderGzip( context.getOutputPath(), context.getVerbose() );
            gz.gunzip();
            System.out.println("Gunzip done !");
            break;
         }
         case TILES : {
            System.out.println("*** Create FITS tiles... ");
            Task task = new Task(context);
            BuilderController builder = new BuilderController(task,context);
            ThreadProgressBar progressBar = new ThreadProgressBar(builder);
            (new Thread(progressBar)).start();
            // laisse le temps au thread de se lancer
            try {
               Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            try {
               builder.build();
            } catch (Exception e) {
//               e.printStackTrace();
               System.out.println("FATAL ERROR: "+e.getMessage());
               System.exit(0);
            } finally {
               progressBar.stop();
            }
            // force � recr�er le allsky
            action = Action.ALLSKY;
            start();
            System.out.println("FITS tiles created !");
            break;
         }
         case ALLSKY : {
            System.out.println("*** Create Allsky views (FITS and JPEG if possible)... ");
            BuilderAllsky builder = new BuilderAllsky(context, -1);
            try {
               builder.createAllSky(3, 64);
               if (context.getCut()!=null) builder.createAllSkyJpgColor(3,64,false);
               System.out.println("Allsky view created !");
            } catch (Exception e) {
               System.out.println("FATAL ERROR: "+e.getMessage());
//               e.printStackTrace();
               System.exit(0);
            }
            break;
         }
      }
      context.setIsRunning(false);
   }

   private static void usage() {
      System.out.println("SkyGen -param=configfile\n");
      System.out.println("This configfile must contains these following options, or use them in comand line :");
      System.out.println(
            "input     Directory of original images (fits or jpg+hhh)" + "\n" +
            "output    Target directory (default $PWD+\"ALLSKY\")" + "\n" +
            "pixel     keep|keepall|overwrite|average|replaceall - in case of already computed values (default overwrite)" + "\n" +
            "method    Level up pixel computation for Jpeg (and jpeg color) : median|mean (default is median)" + "\n" +
            "region    Healpix region to compute (ex: 3/34-38 50 53) or Moc.fits file (nothing means all the sky)" + "\n" +
            "blank     BLANK value alternative (use of FITS header by default)" + "\n" +
            "border    Margins to ignore in the original images (N W S E or constant)" + "\n" +
            "frame     Healpix frame (C or G - default C for ICRS)" + "\n" +
            "skyval    Fits key to use for removing sky background" + "\n" +
            "bitpix    Target bitpix (default is original one)" + "\n" +
            "order     Number of Healpix Order (default computed from the original resolution)" + "\n" +
            "pixelCut  Display range cut (BSCALE,BZERO applied)(required JPEG 8 bits conversion - ex: \"120 140\")" + "\n" +
            "dataCut   Range for pixel vals (BSCALE,BZERO applied)(required for bitpix conversion - ex: \"-32000 +32000\")" + "\n" +
            "color     True if your input images are colored jpeg (default is false)" + "\n" +
            "img       Image path to use for initialization (default is first found)" + "\n" +
            "verbose   Show live statistics : tracelevel from -1 (nothing) to 4 (a lot)" + "\n");
      System.out.println("\nUse one of these actions at end of command line :" + "\n" +
            "index     Build finder index" + "\n" +
            "tiles     Build Healpix tiles" + "\n" +
            "jpeg      Build JPEG tiles from original tiles" + "\n" +
            "moc       Build MOC (based on generated tiles)" + "\n" +
            "mocindex  Build MOC (based on HEALPix index)" + "\n" +
            "allsky    Build Allsky.fits and Allsky.jpg fits pixelCut exists (even if not used)" + "\n" +
            "gzip      gzip all fits tiles and Allsky.fits (by keeping the same names)" + "\n" +
            "gunzip    gunzip all fits tiles and Allsky.fits (by keeping the same names)");
   }

   private void setConfigFile(String configfile) throws Exception {
      this.file = new File(configfile);
      parseConfig();
   }

   class ThreadProgressBar implements Runnable {
      Progressive builder;
      boolean isRunning = false;
      public ThreadProgressBar(Progressive builder) {
         this.builder = builder;
      }

      public void run() {
         isRunning=true;
         while (isRunning) {
            context.setProgress(this.builder.getProgress());
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
         }

      }
      public void stop() {
         context.setProgress(this.builder.getProgress());
         isRunning=false;
      }
   }
}
