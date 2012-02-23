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

package cds.allsky;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.Border;

import cds.aladin.Aladin;
import cds.aladin.Chaine;
import cds.aladin.Localisation;
import cds.tools.Util;

public class TabDesc extends JPanel implements ActionListener {

   private String REP_SOURCE;
   private String REP_DEST;
   private String REP_DEST_RESET;
   private String INDEX_RESET;

   private JLabel infoLabel;
   private JLabel destLabel;
   private JLabel sourceLabel;
   private JLabel labelAllsky;
   private String LABELALLSKY;
   private String NEXT;
   private String SEE;
   private String INFOALLSKY;
   private String PARAMALLSKY;
   private String KEEPALLSKY,COADDALLSKY,OVERWRITEALLSKY,KEEPCELLALLSKY;
   private String SPECIFALLSKY,BLANKALLSKY,BORDERALLSKY, SKYVALALLSKY ;

   
   private JLabel paramLabel;
   private JRadioButton keepRadio,coaddRadio,overwriteRadio,keepCellRadio;
   private JCheckBox specifCheckbox;
   private JCheckBox blankCheckbox;
   private JCheckBox borderCheckbox;
   private JCheckBox skyvalCheckbox;
   
   private ButtonGroup tilesGroup;
   private JTextField specifTextField;
   protected JTextField blankTextField;
   protected JTextField borderTextField;
   private JTextField skyvalTextField;

   private JCheckBox resetHpx = new JCheckBox();
   private JCheckBox resetIndex = new JCheckBox();
   private JButton browse_S = new JButton();
   private JButton browse_D = new JButton();
   private JTextField dir_S = new JTextField(35); 
   protected JTextField dir_D = new JTextField(35);
   private JTextField textFieldAllsky = new JTextField(35);
   private String defaultDirectory;
   final private MainPanel mainPanel;
   private String BROWSE;
   private JButton b_next,b_see;
   private String help, titlehelp;

   public TabDesc(String defaultDir, MainPanel mPanel) {
      super(new BorderLayout());
      mainPanel = mPanel;
      createChaine();
      init();
      
      JPanel px;
      JPanel pCenter = new JPanel(new GridBagLayout());
      this.defaultDirectory = defaultDir;

      GridBagConstraints c = new GridBagConstraints();
      c.insets = new Insets(1, 3, 1, 3);
      c.anchor = GridBagConstraints.NORTHWEST;
      c.fill = GridBagConstraints.HORIZONTAL;
      
      // Baratin explicatif
      c.gridy = 0;
      c.gridx = 0;
      c.gridwidth = 4;
      pCenter.add(infoLabel,c);

      // R�pertoire source
      c.insets.top = 20;
      c.gridwidth = 1;
      c.gridy++;
      c.gridx = 0;
      pCenter.add(sourceLabel, c);
      c.gridx++;
      pCenter.add(Util.getHelpButton(this, getString("HELPDIRSRCALLSKY")),c);
      c.gridx++;
      pCenter.add(dir_S, c);
      c.gridx++;
      pCenter.add(browse_S, c);

      // R�pertoire destination
      c.insets.top = 1;
      c.gridy++;
      c.gridx = 0;
      pCenter.add(destLabel, c);
      c.gridx++;
      pCenter.add(Util.getHelpButton(this, getString("HELPDIRTRGALLSKY")),c);
      c.gridx++;
      pCenter.add(dir_D, c);
      c.gridx++;
      pCenter.add(browse_D, c);
      
      // Label
      c.insets.bottom = 20;
      c.gridy++;
      c.gridx = 0;
      pCenter.add(labelAllsky, c);
      c.gridx++;
      pCenter.add(Util.getHelpButton(this, getString("HELPLABELALLSKY")),c);
      c.gridx++;
      pCenter.add(textFieldAllsky, c);

      // Param�tres avanc�es
      c.insets.bottom=1;
      c.gridx=0;
      c.gridy++;
      pCenter.add(paramLabel,c);
      c.gridx++;
      pCenter.add(Util.getHelpButton(this, getString("HELPPARAMALLSKY")),c);
      c.gridx++;
      c.gridwidth=2;
      pCenter.add(resetIndex, c);
      c.gridy++;
      pCenter.add(resetHpx, c);
      c.gridy++;
      JPanel pTiles = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
      pTiles.setBorder( BorderFactory.createEmptyBorder(0,10,0,0));
      pTiles.add(keepRadio);      //keepRadio.setEnabled(false);
      pTiles.add(overwriteRadio); //overwriteRadio.setEnabled(false);
      pTiles.add(coaddRadio);     //coaddRadio.setEnabled(false);
      pCenter.add(pTiles, c);
      
      if( Aladin.PROTO ) {
         c.gridy++;
         pTiles = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
         pTiles.setBorder( BorderFactory.createEmptyBorder(0,10,0,0));
         pTiles.add(keepCellRadio);
         pCenter.add(pTiles, c);
      }
      
      c.gridx=2;
      
      c.gridy++;
      px = new JPanel( new BorderLayout(0,0));
      blankTextField.addKeyListener(new KeyAdapter() {
         public void keyReleased(KeyEvent e) {
            blankCheckbox.setSelected( blankTextField.getText().trim().length()>0 );
         }
      });
      px.add(blankCheckbox,BorderLayout.WEST);
      px.add(blankTextField,BorderLayout.CENTER);
      pCenter.add(px,c); 

      c.gridy++;
      px = new JPanel( new BorderLayout(0,0));
      skyvalTextField.addKeyListener(new KeyAdapter() {
          public void keyReleased(KeyEvent e) {
              skyvalCheckbox.setSelected( skyvalTextField.getText().trim().length()>0 );
          }
      });
      px.add(skyvalCheckbox,BorderLayout.WEST);
      px.add(skyvalTextField,BorderLayout.CENTER);
      pCenter.add(px, c);

      c.gridy++;
      px = new JPanel( new BorderLayout(0,0));
      borderTextField.addKeyListener(new KeyAdapter() {
         public void keyReleased(KeyEvent e) {
            borderCheckbox.setSelected( borderTextField.getText().trim().length()>0 );
         }
      });
      px.add(borderCheckbox,BorderLayout.WEST);
      px.add(borderTextField,BorderLayout.CENTER);
      pCenter.add(px,c); 
      
      c.gridy++;
      px = new JPanel( new BorderLayout(0,0));
      px.add(specifCheckbox,BorderLayout.WEST);
      px.add(specifTextField,BorderLayout.CENTER);
      specifTextField.addKeyListener(new KeyAdapter() {
         public void keyReleased(KeyEvent e) {
            specifCheckbox.setSelected( specifTextField.getText().trim().length()>0 );
         }
      });
      pCenter.add(px,c);
      

      
      if (Aladin.PROTO) {
         c.gridy++;
         final JCheckBox cb1 = new JCheckBox("HEALPix in galactic (default is ICRS)", false);
         cb1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               mainPanel.context.setFrame(cb1.isSelected() ? Localisation.GAL : Localisation.ICRS);
            }
         });
         pCenter.add(cb1, c);
      }
     
      // boutons
      JPanel fin = new JPanel(new BorderLayout());
      JPanel pBtn = new JPanel();
      pBtn.setLayout(new BoxLayout(pBtn, BoxLayout.X_AXIS));
      pBtn.add(Box.createHorizontalGlue());
      pBtn.add(b_see);
      pBtn.add(b_next);
      pBtn.add(Box.createHorizontalGlue());
      fin.add(pBtn, BorderLayout.CENTER);

      // composition du panel principal
      add(pCenter, BorderLayout.CENTER);
      add(fin, BorderLayout.SOUTH);
      setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 5));
   }

   private void createChaine() {
      REP_SOURCE = getString("REPSALLSKY");
      BROWSE = getString("FILEBROWSE");
      REP_DEST = getString("REPDALLSKY");
      REP_DEST_RESET = getString("REPRESALLSKY");
      INDEX_RESET = getString("INDEXRESETALLSKY");
      LABELALLSKY = getString("LABELALLSKY");
      SEE = getString("SEE");
      NEXT = getString("NEXT");
      titlehelp = getString("HHELP");
      INFOALLSKY = getString("INFOALLSKY");
      PARAMALLSKY = getString("PARAMALLSKY");
      COADDALLSKY = getString("COADDALLSKY");
      KEEPALLSKY = getString("KEEPALLSKY");
      KEEPCELLALLSKY = getString("KEEPCELLALLSKY");
      OVERWRITEALLSKY = getString("OVERWRITEALLSKY");
      SPECIFALLSKY  = getString("SPECIFALLSKY");
      BLANKALLSKY  = getString("BLANKALLSKY");
      BORDERALLSKY  = getString("BORDERALLSKY");
      SKYVALALLSKY  = getString("SKYVALALLSKY");
   }
   
   private String getString(String k) { return mainPanel.aladin.getChaine().getString(k); }

   public void init() {
      infoLabel = new JLabel(Util.fold(INFOALLSKY,100,true));
      infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
      sourceLabel = new JLabel(REP_SOURCE);
      sourceLabel.setFont(sourceLabel.getFont().deriveFont(Font.BOLD));
      destLabel = new JLabel(REP_DEST);
      destLabel.setFont(destLabel.getFont().deriveFont(Font.BOLD));
      dir_S.addActionListener(this);
      dir_S.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent e) {
            super.keyTyped(e);
            if (!dir_S.getText().equals(""))
               actionPerformed(new ActionEvent(dir_S, -1, "dirBrowser Action"));
         }
      });

      browse_S.setText(BROWSE);
      browse_S.addActionListener(this);
      dir_D.addActionListener(this);
      dir_D.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent e) {
            super.keyTyped(e);
            if (!dir_D.getText().equals(""))
               actionPerformed(new ActionEvent(dir_D, -1, "dirBrowser Action"));
         }
      });
      browse_D.setText(BROWSE);
      browse_D.addActionListener(this);
      
      labelAllsky = new JLabel(LABELALLSKY);
      labelAllsky.setFont(labelAllsky.getFont().deriveFont(Font.BOLD));
      
      paramLabel = new JLabel(PARAMALLSKY);
      paramLabel.setFont(paramLabel.getFont().deriveFont(Font.BOLD));
      
      tilesGroup = new ButtonGroup();
      keepRadio = new JRadioButton(KEEPALLSKY); tilesGroup.add(keepRadio);
      overwriteRadio = new JRadioButton(OVERWRITEALLSKY); tilesGroup.add(overwriteRadio);
      coaddRadio = new JRadioButton(COADDALLSKY); tilesGroup.add(coaddRadio);
      keepCellRadio = new JRadioButton(KEEPCELLALLSKY); tilesGroup.add(keepCellRadio);
      keepRadio.setSelected(true);
      
      specifCheckbox = new JCheckBox(SPECIFALLSKY); specifCheckbox.setSelected(false);
      specifTextField = new JTextField();
      blankCheckbox = new JCheckBox(BLANKALLSKY); blankCheckbox.setSelected(false);
      blankTextField = new JTextField();
      borderCheckbox = new JCheckBox(BORDERALLSKY); borderCheckbox.setSelected(false);
      borderTextField = new JTextField();
      skyvalCheckbox = new JCheckBox(SKYVALALLSKY); skyvalCheckbox.setSelected(false);
      skyvalTextField = new JTextField();

      resetHpx.setText(REP_DEST_RESET);
      resetHpx.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) { 
            resumeWidgetsStatus();
         }
      });

      resetIndex.setText(INDEX_RESET);
      resetIndex.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            resumeWidgetsStatus();
         }
      });

      b_see = new JButton(SEE);
      b_see.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadImgEtalon();
         }
      });


      b_next = new JButton(NEXT);
      b_next.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            mainPanel.showBuildTab();
         }
      });
//      b_help = Util.getHelpButton(this, help);

      resetHpx.setSelected(false);
      resetIndex.setSelected(true);
      resumeWidgetsStatus();
  }
   
   // Chargement dans Aladin de l'image "�talon"
   private void loadImgEtalon() {
      String fileName = mainPanel.context.getImgEtalon();
      if( fileName.endsWith(".hhh") ) fileName=fileName.substring(0,fileName.length()-4)+".jpg";

      mainPanel.aladin.execAsyncCommand("load "+fileName);
   }
   
   public void show() {
      super.show();
      resumeWidgetsStatus();
   }
   
   protected void resumeWidgetsStatus() {
      boolean color = mainPanel.context!=null && mainPanel.context.getBitpix()==0;
      boolean allskyExist = mainPanel.isExistingAllskyDir();
      boolean isRunning = mainPanel.isRunning();
      resetHpx.setEnabled(allskyExist && !isRunning && !color);
      resetIndex.setEnabled(allskyExist && !isRunning);

      boolean flag = !resetHpx.isSelected() && resetHpx.isEnabled();
      keepRadio.setEnabled(flag);
      keepCellRadio.setEnabled(flag);
      overwriteRadio.setEnabled(flag);
      coaddRadio.setEnabled(flag);
      
      if( resetHpx.isSelected() && resetHpx.isEnabled() && resetIndex.isSelected() && resetIndex.isEnabled()) mainPanel.setRestart();
      else if( resetHpx.isSelected() && resetHpx.isEnabled() || resetIndex.isSelected() && resetIndex.isEnabled() ) mainPanel.setResume();
      else mainPanel.setStart();
      
      boolean isExistingDir = mainPanel.isExistingDir();
      mainPanel.setStartEnabled(isExistingDir);
      
      boolean ready = isExistingDir && dir_D.getText().trim().length()>0;
      b_see.setEnabled(mainPanel.context!=null && mainPanel.context.getImgEtalon()!=null);
      b_next.setEnabled(ready);
      blankCheckbox.setEnabled(ready && !isRunning && !color);
      blankTextField.setEnabled(ready && !isRunning && !color);
      borderCheckbox.setEnabled(ready && !isRunning);
      borderTextField.setEnabled(ready && !isRunning);
      skyvalCheckbox.setEnabled(ready && !isRunning);
      skyvalTextField.setEnabled(ready && !isRunning);
      specifCheckbox.setEnabled(ready && !isRunning);
      specifTextField.setEnabled(ready && !isRunning);
      dir_S.setEnabled(!isRunning);
      dir_D.setEnabled(!isRunning);
      textFieldAllsky.setEnabled(!isRunning);
      setCursor( isRunning ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR) ); 
   }

   public void clearForms() {
      dir_S.setText("");
      dir_D.setText("");
      if( mainPanel!=null ) mainPanel.actionPerformed(new ActionEvent("", -1, "dirBrowser Action"));
      textFieldAllsky.setText("");
      resetHpx.setSelected(false);
      resetIndex.setSelected(true);
      resumeWidgetsStatus();
   }

   private void dirBrowser(JTextField dir) {
      String currentDirectoryPath = dir.getText().trim();
      if( currentDirectoryPath.length()==0 ) currentDirectoryPath=defaultDirectory;
      if ( !(new File(currentDirectoryPath).exists()) )
    	  currentDirectoryPath = defaultDirectory;
      String s = Util.dirBrowser(this, currentDirectoryPath);
      if( s==null ) return;
      dir.setText(s);
      mainPanel.actionPerformed(new ActionEvent(dir, -1, "dirBrowser Action"));
   }

   public String getInputPath() {
      return dir_S.getText();
   }

   public String getOutputPath() {
      return dir_D.getText();
   }

   public CoAddMode getCoaddMode() {
      return resetHpx.isSelected() || !resetHpx.isEnabled()? CoAddMode.REPLACEALL : 
            keepRadio.isSelected() ? CoAddMode.KEEP : keepCellRadio.isSelected() ? CoAddMode.KEEPALL
            :overwriteRadio.isSelected() ? CoAddMode.OVERWRITE : CoAddMode.AVERAGE;
   }

   public JTextField getSourceDirField() {
      return dir_S;
   }

   public void setFieldEnabled(boolean enabled) {
      dir_S.setEnabled(enabled);
      dir_D.setEnabled(enabled);
   }

   public void actionPerformed(ActionEvent e) {
      if (e.getSource() == dir_S) {
         initTxt();
      } else if (e.getSource() == browse_S) {
         dirBrowser(dir_S);
         initTxt();
      }

      if (e.getSource() == dir_D) {
         newAllskyDir();
      } else if (e.getSource() == browse_D) {
         dirBrowser(dir_D);
         newAllskyDir();
      }
   }

   /**
    * Itialisation des variables textuelles en fonction du nouveau r�pertoire
    * SOURCE
    */
   private void initTxt() {
      String txt = dir_S.getText();
      int i = txt.lastIndexOf(Util.FS);
      if (i == -1) return;

      // ne traite pas le dernier s�parateur
      while (i + 1 == txt.length()) txt = txt.substring(0, i);
      
      // cherche le dernier mot et le met dans le label
      String str = txt.substring(txt.lastIndexOf(Util.FS) + 1);
      textFieldAllsky.setText(str);
      // dir_A.setText(str+AllskyConst.SURVEY);

      // r��initialise le r�pertoire de destination avec le chemin des donn�es
      // d'entr�e
      dir_D.setText("");
      newAllskyDir();
   }

   public String getLabel() {
      return textFieldAllsky.getText();
   }
   
   public String getSpecifNpix() {
      if( !specifCheckbox.isSelected() ) return "";
      return specifTextField.getText();
   }

   public String getBlank() {
      if( !blankCheckbox.isSelected() ) return "";
      return blankTextField.getText();
   }
   
   public String getBorderSize() {
      if( !borderCheckbox.isSelected() ) return "0";
      return borderTextField.getText();
   }

   public String getSkyval() {
	   if( !skyvalCheckbox.isSelected() ) return null;
	   return skyvalTextField.getText();
   }
   
   /**
    * Applique les modifications si le nom du r�pertoire DESTINATION change
    */
   private void newAllskyDir() {
      String str = dir_D.getText();
      // enl�ve les multiples FS � la fin
      while (str.endsWith(Util.FS))
         str = str.substring(0, str.lastIndexOf(Util.FS));

      // si l'entr�e est vide, on remet le d�faut
      if (str.equals("")) {
         // r�initalise le r�pertoire SURVEY et l'utilise
         initDirD();
         mainPanel.newAllskyDir();
         return;
      }
      // cherche le dernier mot
      Constante.SURVEY = str.substring(str.lastIndexOf(Util.FS) + 1);

      mainPanel.newAllskyDir();
   }

   private void initDirD() {
      Constante.SURVEY = getLabel() + Constante.ALLSKY;
      String path = dir_S.getText();
      // enl�ve les multiples FS � la fin
      while (path.endsWith(Util.FS))
         path = path.substring(0, path.lastIndexOf(Util.FS));

      dir_D.setText(path + Constante.ALLSKY + Util.FS);
   }

   public boolean isResetHpx() {
      return resetHpx.isSelected() && resetHpx.isEnabled();
   }

   public boolean isResetIndex() {
      return resetIndex.isSelected() && resetIndex.isEnabled();
   }

//   public void setResetEnable(boolean enable) {
//      // resetLabel.setEnabled(enable);
//      resetHpx.setEnabled(enable);
//      resetIndex.setEnabled(enable);
//      enableUpdate();
//   }

   public void help() {
      JOptionPane.showMessageDialog(this, help, titlehelp,
            JOptionPane.INFORMATION_MESSAGE);
   }
}
