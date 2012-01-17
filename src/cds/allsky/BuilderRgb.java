package cds.allsky;

import java.io.File;
import java.io.FileNotFoundException;

import cds.aladin.Aladin;
import cds.aladin.Coord;
import cds.aladin.Plan;
import cds.aladin.PlanBG;
import cds.aladin.PlanImageRGB;
import cds.fits.Fits;
import cds.tools.pixtools.CDSHealpix;
import cds.tools.pixtools.Util;

public class BuilderRgb implements Runnable {
	private int progress;
	private PlanBG[] p;
	private final Aladin aladin;
	private Context context;
	private BuilderAllsky builderAllsky;
	private String path;
    private int width=-1;
    private double [] blank;
    private double [] bscale;
    private double [] bzero;
    private byte [][] tcm;
    private int [] bitpix;
    private boolean stopped=false;
    private int maxOrder = 100;
    private int missing=-1;
    
    static public final int MEDIANE = 0;
    static public final int MOYENNE = 1;
    private int method;
    
    private int statNbFile;
    private long statSize;
    private long startTime,totalTime;
    private long statLastShowTime;

    public BuilderRgb(Aladin aladin, Context context, Object[] plans, String path,int method) {
       this.aladin = aladin;
       this.context = context;
       context.initParameters();
       p = new PlanBG[3];
       for( int c=0; c<3; c++ ) p[c]=(PlanBG)plans[c];
       this.path = path;
       
       bitpix = new int[3]; 
       for( int c=0; c<3; c++ ) bitpix[c]=0;
       blank = new double[3];
       bzero = new double[3];
       bscale = new double[3];
       tcm = new byte[3][];
       this.method=method;

       // recherche la meilleure r�solution commune
       int frame=-1;
       for( int c=0; c<3; c++) {
          if( p[c]==null ) { missing=c; continue; }
          if( frame==-1 ) frame = p[c].getFrameOrigin();
          else if( frame!=p[c].getFrameOrigin() ) {
             context.warning("All components must be used the same HEALPix coordinate system !");
             return;
          }
          tcm[c] = cds.tools.Util.getTableCM(p[c].getCM(), 2);
          int order = p[c].getMaxFileOrder();
          if( maxOrder > order)  maxOrder = order;
       }
       builderAllsky = new BuilderAllsky(context,frame);
       
       Aladin.trace(3,"BuilderRgb maxOrder="+maxOrder+" => "+path);
    }
    
    public int getProgress() {
       return progress;
    }

    private void initStat() { statNbFile=0; statSize=0; statLastShowTime=-1; startTime = System.currentTimeMillis(); }

    // Mise � jour des stats
    private void updateStat(File f) {
       statNbFile++;
       statSize += f.length();
       long t = System.currentTimeMillis();
       if( t-statLastShowTime < 1000 ) return;
       totalTime = System.currentTimeMillis()-startTime;
       statLastShowTime=t;
       showStat();
    }

    // Demande d'affichage des stats (dans le TabRgb)
    private void showStat() {
       context.showRgbStat(statNbFile, statSize, totalTime);
    }

    public synchronized void start(){
       (new Thread(this)).start();
    }

	// G�n�ration des RGB r�cursivement en repartant du niveau de meilleure r�solution
	// afin de pouvoir utiliser un calcul de m�diane sur chacune des composantes R,G et B
	private Fits [] createRGB(int order, long npix) throws Exception {

	   // si le process a �t� arr�t� on essaie de ressortir au plus vite
	   if( stopped ) return null;

	   // S'il n'existe pas au-moins 2 composantes en fichiers fits, c'est une branche morte
	   int trouve=0;
	   for( int c=0; c<3; c++ ) {
	      if( c==missing ) continue;
	      String file = Util.getFilePath(p[c].getUrl(),order,npix)+".fits";
	      if( new File(file).exists() ) trouve++;
	   }
	   if( trouve<2 ) return null;

	   Fits [] out = null;

	   // Branche terminale
	   if( order==maxOrder ) out = createLeaveRGB(order, npix);

	   // Noeud interm�diaire
	   else {
	      Fits [][] fils = new Fits[4][3];
	      boolean found = false;
	      for( int i=0; i<4; i++ ) {
	         fils[i] = createRGB(order+1,npix*4+i);
	         if( fils[i] != null && !found ) found = true;
	      }
	      if( found ) out = createNodeRGB(fils);
	   }
	   if( out!=null) generateRGB(out, order, npix);
	   return out;
	}


	// G�n�ration d'un noeud par la m�diane pour les 3 composantes
	// (on ne g�n�re pas encore le RGB (voir generateRGB(...))
	private Fits [] createNodeRGB(Fits [][] fils) throws Exception {
       
       Fits [] out = new Fits[3];
       for( int c=0; c<3; c++ ) {
          out[c] = new Fits(width,width,bitpix[c]);
          out[c].setBlank(blank[c]);
          out[c].setBscale(bscale[c]);
          out[c].setBzero(bzero[c]);
       }
       
       for( int dg=0; dg<2; dg++ ) {
          for( int hb=0; hb<2; hb++ ) {
             int quad = dg<<1 | hb;
             int offX = (dg*width)/2;
             int offY = ((1-hb)*width)/2;
             
             for( int c=0; c<3; c++ ) {
                if( c==missing ) continue;
                Fits in = fils[quad]==null ? null : fils[quad][c];
                double p[] = new double[4];
                double coef[] = new double[4];
                
                for( int y=0; y<width; y+=2 ) {
                   for( int x=0; x<width; x+=2 ) {
                      
                      double pix=blank[c];
                      if( in!=null ) {

                          // On prend la moyenne (sans prendre en compte les BLANK)
                         if( method==MOYENNE ) {
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

                      out[c].setPixelDouble(offX+(x/2), offY+(y/2), pix);
                   }
                }
             }
          }
       }

       for( int j=0; j<4; j++ ) {
          for( int c=0; c<3; c++ ) {
             if( c==missing ) continue;
             if( fils[j]!=null && fils[j][c]!=null ) fils[j][c].free();
          }
       }
       
       return out;
    }

    // G�n�ration d'une feuille terminale (=> simple chargement des composantes)
	private Fits [] createLeaveRGB(int order, long npix) throws Exception {
	   Fits[] out =null;
	   try {
	      out = new Fits[3];

	      // Chargement des 3 (ou �ventuellement 2) composantes
	      for( int c=0; c<3; c++ ) {
	         if( c==missing ) continue;
	         out[c] = new Fits();
	         out[c].loadFITS( Util.getFilePath( p[c].getUrl(),order, npix)+".fits");

	         // Initialisation des constantes pour cette composante
	         if( bitpix[c]==0 ) {
	            bitpix[c]=out[c].bitpix;
	            blank[c]=out[c].blank;
	            bscale[c]=out[c].bscale;
	            bzero[c]=out[c].bzero;
	            if( width==-1 ) width = out[c].width;  // La largeur d'un losange est la m�me qq soit la couleur
	         }
	      }
	   } catch( Exception e ) {
	      e.printStackTrace();
	   }
	   return out;
	}
	
    // g�n�ration du RGB � partir des composantes
    private void generateRGB(Fits [] out, int order, long npix) throws Exception {
       
       // Passage en 8 bits pour chaque composante
       for( int c=0; c<3; c++ ) {
          if( c==missing ) continue;
//          out[c].toPix8(p[c].getCutMin(),p[c].getCutMax(), p[c].getCM());
          out[c].toPix8(p[c].getCutMin(),p[c].getCutMax(), tcm[c]);
       }
       
       Fits rgb = new Fits(width,width,0);
       int [] pix8 = new int[3];
       for( int i=width*width-1; i>=0; i-- ) {
          int tot = 0;  // Pour faire la moyenne en cas d'une composante manquante
          for( int c=0; c<3; c++ ) {
             if( c==missing ) continue;
             pix8[c] = 0xFF & (int)out[c].pix8[i];
             tot += pix8[c];
          }
          if( missing!=-1 ) pix8[missing] = tot/2;
          int pix = 0xFF;
          for( int c=0; c<3; c++ ) pix = (pix<<8) | pix8[c];
          rgb.rgb[i]=pix;
       }
       String file = Util.getFilePath(path,order, npix)+".jpg";
       rgb.writeJPEG(file);
       rgb.free();
       
       File f = new File(file);
       updateStat(f);
    }
    
    private long t=0;
    
    public void run() {

       try {
          initStat();
          double progressFactor = 100f/768f;
          progress=0;
          for( int i=0; !stopped && i<768; i++ ) {
        	  // Si le fichier existe d�j� on ne l'�crase pas
        	  String rgbfile = Util.getFilePath(path,3, i)+".jpg";
        	  if ((new File(rgbfile)).exists())
        		  continue;
      		if( context.isInMocTree(3, i) ) createRGB(3,i);
             progress = (int)(i*progressFactor);
             long t1 = System.currentTimeMillis();
             if( t1-t>10000 ) { preview(path,0); t=t1; }
          }
//          sg.createAllSkyJpgColor(path,3,64);
          progress=100;
       } catch( Exception e ) {
          e.printStackTrace();
       }
       preview(path,0);
    }

//	public void run() {
//
//		PlanBG planBG = null;
//		// Les chemins vers les plans
//		String pathRouge = null;
//		String pathVert = null;
//		String pathBleu = null;
//		// Utilise le minmax des plans
//		double[] mmRouge = new double[2];
//		double[] mmVert = new double[2];
//		double[] mmBleu = new double[2];
//		for (int i=0; i<plans.length; i++) {
//			if (plans[i] != null) {
//				planBG = plans[i];
//				if (i==0) {
//					pathRouge = planBG.getUrl();
//					mmRouge[0] = planBG.getPixelMin();
//					mmRouge[1] = planBG.getPixelMax();
//				}
//				else if (i==1) {
//					pathVert = planBG.getUrl();
//					mmVert[0] = planBG.getPixelMin();
//					mmVert[1] = planBG.getPixelMax();
//				}
//				else if (i==2) {
//					pathBleu = planBG.getUrl();
//					mmBleu[0] = planBG.getPixelMin();
//					mmBleu[1] = planBG.getPixelMax();
//				}
//			}
//		}
//		
//		// recherche la meilleure r�solution commune
//		// -> sinon, on risque d'avoir seulement une couleur en zoomant
//		int orderLimit = 100;
//		for (int i=0; i<plans.length; i++) {
//			planBG = plans[i];
//			if (planBG != null && orderLimit > planBG.getMaxHealpixOrder()-DBBuilder.ORDER) {
//				orderLimit = planBG.getMaxHealpixOrder()-DBBuilder.ORDER;
//			}
//		}
//		
//		// pour chaque fichier fits de l'arborescence
//		for (int order = 3 ; order <= orderLimit ; order++) {
//		   long npixmax = cds.tools.pixtools.Util.getMax(order)+1;
//		   
//		   for (int npix = 0 ; npix < npixmax ; npix++) {
//		      // va chercher le meme fichier dans chacune des 2/3 arboresences
//		      String fRouge = (pathRouge!=null)?cds.tools.pixtools.Util.getFilePath(pathRouge, order, npix)+ext:null;
//		      String fVert = (pathVert!=null)?cds.tools.pixtools.Util.getFilePath(pathVert, order, npix)+ext:null;
//		      String fBleu = (pathBleu!=null)?cds.tools.pixtools.Util.getFilePath(pathBleu, order, npix)+ext:null;
//
//		      if (fRouge != null && !(new File(fRouge)).exists()) fRouge=null;
//		      if (fVert != null && !(new File(fVert)).exists()) fVert=null;
//		      if (fBleu != null && !(new File(fBleu)).exists()) fBleu=null;
//
//		      if (fRouge == null && fVert == null && fBleu == null) continue;
//
//		      // combinaison couleur classique Aladin
//		      PlanImageRGB rgb;
//		      try {
//		         rgb = new PlanImageRGB(aladin, fRouge,mmRouge,fVert,mmVert,fBleu,mmBleu);
//		         String pathRGB = cds.tools.pixtools.Util.getFilePath(output, order, npix)+".jpg";
//		         (new File(pathRGB.substring(0, pathRGB.lastIndexOf(Util.FS)))).mkdirs();
//		         aladin.save.saveImageColor(pathRGB, rgb, 2);
//		      } catch (FileNotFoundException e) {
//		         Aladin.trace(3, e.getMessage());
//		      } catch (Exception e) {
//		         Aladin.trace(3, e.getMessage());
//		      }
//		      progress = (int) (100*((float)npix/npixmax)/(1+orderLimit-3));
//		      if (order == 3 && npix%100==0)
//		         preview(output,npix);
//		   }
//		}
//		preview(output,0);
//		progress = 100;
//	}
    

	/** Cr�ation/rafraichissemnt d'un allsky (en l'�tat) et affichage */
	void preview(String path, int last) {
	   try {
          try {
        	  builderAllsky.createAllSkyJpgColor(path,3,64,true);
          } catch (Exception e) {
        	  Aladin.trace(3,e.getMessage());
          }
          
          Plan planPreview = aladin.calque.getPlan("MySkyColor");
          if( planPreview==null || planPreview.isFree() ) {
             double[] res = CDSHealpix.pix2ang_nest(Util.nside(3), last);
             double[] radec = CDSHealpix.polarToRadec(new double[] {res[0],res[1]});
             radec = context.gal2ICRSIfRequired(radec);
             int n = aladin.calque.newPlanBG(path, "=MySkyColor", Coord.getSexa(radec[0],radec[1]), "30" );
             Aladin.trace(4,"RGBGuild: Create MySky");
             planPreview = aladin.calque.getPlan(n);
          } else {
             ((PlanBG)planPreview).forceReload();
             aladin.calque.repaintAll();
             Aladin.trace(4,"RGBGuild: Create MySky");
             
          }
      } catch( Exception e ) {e.printStackTrace(); }
	}
		
}
