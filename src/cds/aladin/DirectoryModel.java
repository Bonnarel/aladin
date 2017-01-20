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

import java.util.Enumeration;
import java.util.HashMap;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Gestion d'un model associ� � l'arbre Registry
 * @author Pierre Fernique [CDS]
 * @version 1.0 Janvier 2017 - cr�ation
 */
public class DirectoryModel extends DefaultTreeModel {
   protected DefaultMutableTreeNode root;
   private Aladin aladin;

   protected DirectoryModel(Aladin aladin) {
      super( new DefaultMutableTreeNode( new TreeObj(aladin,"root",null,"Collections","") ) );
      root = (DefaultMutableTreeNode) getRoot();
      this.aladin = aladin;
   }
   
   /** Colorations (noire, verte ou orange) des branches de l'arbre en fonction de l'�tat des feuilles */
   protected int populateFlagIn() { return populateFlagIn(root); }
   private int populateFlagIn(DefaultMutableTreeNode node) {
      TreeObj treeObj = (TreeObj) node.getUserObject();
      if( node.isLeaf() ) return treeObj.getIsIn();

      DefaultMutableTreeNode subNode = null;
      Enumeration e = node.children();
      int rep = -2;
      while( e.hasMoreElements() ) {
         subNode = (DefaultMutableTreeNode) e.nextElement();
         int isIn =  populateFlagIn(subNode);
         if( rep==-2 ) rep=isIn;
         else if( rep==0 && (isIn==-1 || isIn==1) ) rep=isIn;
         else if( rep==1 && (isIn==-1 || isIn==0) ) rep=-1;
      }
      treeObj.setIn(rep);
      return rep;
   }
   
   /** Comptage de la descendance de chaque branche (nombre de noeuds terminaux d'une branche)
    * M�morisation dans TreeObj, soit en tant que r�f�rence (hs!=null), soit
    * en tant que d�compte courant
    * @param hs m�morisation des valeurs sous forme path=n (ex: /Image/Optical=32, ...)
    */
   protected int countDescendance() { return countDescendance(null); }
   protected int countDescendance(HashMap<String,Integer> hs) { return countDescendance(root.toString(),root,hs); }
   private int countDescendance(String prefix,DefaultMutableTreeNode parent,HashMap<String,Integer> hs) {
      TreeObj to = (TreeObj) parent.getUserObject();
      if( parent.isLeaf() )  return 1;

      int n=0;
      Enumeration e = parent.children();
      while( e.hasMoreElements() ) {
         DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
         n += countDescendance( prefix, node,hs );
      }
      
      // M�morisation de r�f�rence
      if( hs!=null ) {
         hs.put(to.path, n ); 
         to.nb = to.nbRef = n;
         
      //D�compte temporaire
      } else to.nb = n;
      
      return n;
   }

   // Dernier noeud parent ayant eu une instertion => permet �ventuellement un ajout ult�rieur plus rapide
   private DefaultMutableTreeNode lastParentNode=null;
   
   /** Insertion d'un noeud, �ventuellement avec les diff�rents �l�ments de sa branche
    * si ceux-ci n'existent pas encore. Conserve le noeud parent de l'insertion
    * dans lastParentNode afin d'acc�lerer une �ventuelle insertion ult�rieure au m�me endroit
    * @param treeObj Le nouveau noeud � ins�rer
    */
   protected void createTreeBranch(TreeObj treeObj) {
      // Cr�ation imm�diate car c'est le m�me parent que la pr�c�dente insertion
      if( createLeafWithLastParent( this, treeObj) ) return;
      
      // Cr�ation r�cursive (�ventuellement pour la branche)
      DefaultMutableTreeNode nodeUp [] = new DefaultMutableTreeNode[1];
      int index [] = new int[1];
      lastParentNode = createTreeBranch( this, root, treeObj, 0, nodeUp, index);
      
      // Indication aux listeners du mod�le qu'une branche a �t� ins�r�
//      if( nodeUp[0]!=null ) nodesWereInserted( nodeUp[0], index);
   }
   
   /** M�thode interne - Tentative d'insertion d'un noeud sur le parent de la derni�re insertion. Retourne true
    * si l'insertion est effectivement possible, false sinon */
   private boolean createLeafWithLastParent(DefaultTreeModel model, TreeObj treeObj) {
      if( lastParentNode==null ) return false;
      int pos = treeObj.path.lastIndexOf('/');
      String path = treeObj.path.substring(0, pos);
      
      TreeObj pere = (TreeObj) lastParentNode.getUserObject();
      if( !path.equals(pere.path) ) return false;
      
//      int i = treeObj.treeIndex;
//      int n = lastParentNode.getChildCount();
//      if( i==-1 || i>n ) i=n;
//      lastParentNode.insert( new DefaultMutableTreeNode(treeObj), i);
//      model.nodesWereInserted( lastParentNode, new int[]{i});
      
      lastParentNode.add( new DefaultMutableTreeNode(treeObj) );
      
      return true;
   }

   /** M�thode interne - Insertion r�cursive d'un noeud en fonction du "path" du noeud � ins�rer.
    * Cr�ation �venutelle des noeuds des branches si ceux-ci n'existente pas encore
    * @param model Le mod�le associ� � l'arbre
    * @param parent Le noeud courant du parcours de l'arbre (root au d�but)
    * @param treeObj Le noeud � ins�rer
    * @param opos L'index courant dans le "path" du noeud, -1 si le path a �t� compl�tement parcouru
    *             (ex: Optical/Image/DSS2/color ) => pos = index du I de Image
    * @param parentUp tableau  (1er �lement) servant � m�moriser le noeud parent de l'insertion de la branche
    *               la plus haute dans l'arbre (pour pouvoir avertir les listeners de la greffe de la branche)
    * @param childIndex tableau  (1er �lement) servant � m�moriser l'indice de la brance greff�e au plus haut
    *              dans l'arbre (pour pouvoir avertir les listeners de la greffe de la branche)
    * @return Le parent direct de l'insertion du noeud (afin de pouvoir ins�rer plus rapidement un autre noeud au m�me endroit)
    */
   private DefaultMutableTreeNode createTreeBranch(DefaultTreeModel model, DefaultMutableTreeNode parent, 
         TreeObj treeObj, int opos, DefaultMutableTreeNode parentUp [], int childIndex []) {

      // D�termination du prochain �l�ment dans le path
      // Rq: On d�coupe par "/" mais sans prendre en compte "\/"
      int pos, offset=opos;
      do  {
         pos=treeObj.path.indexOf('/',offset);
         offset=pos;
         if( pos>1 && treeObj.path.charAt(pos-1)=='\\') offset++;
         else offset=-1;
      } while( offset!=-1 );

      // D�termination du label courant et de son path
      String label = pos<0 ? treeObj.path.substring(opos) : treeObj.path.substring(opos,pos);
      String path = pos<0 ? treeObj.path : treeObj.path.substring(0,pos);
      
      // Les noeuds utilisateurs n'utilisent pas de checkbox pour cet arbre (� supprimer si possible)
      ((TreeObj)parent.getUserObject()).noCheckbox();

      try {
         // Recherche du fils qui correspond � l'emplacement o� la greffe doit avoir lieu
         DefaultMutableTreeNode subNode = null;
         Enumeration e = parent.children();
         while( e.hasMoreElements() ) {
            subNode = (DefaultMutableTreeNode) e.nextElement();
            TreeObj fils = (TreeObj) subNode.getUserObject();
            if( label.equals(fils.label) ) break;
            subNode=null;
         }

         // Aucun fils ne correspond, il faut donc cr�er la branche (ou ins�rer le noeud terminal si on est au bout)
         if( subNode==null ) {
            
            // Noeud terminal ? c'est donc celui � ins�rer
            if( pos==-1 ) subNode = new DefaultMutableTreeNode( treeObj );
            
            // Branche interm�diaire ? d�j� connue ou non ?
            else {
//               TreeObj obj = retrieveOldBranch(path);
//               if( obj==null ) obj = new TreeObj(aladin,"",null,label,path);
//               subNode = new DefaultMutableTreeNode( obj );
               subNode = new DefaultMutableTreeNode( new TreeObj(aladin,"",null,label,path) );
            }
//            int i = ((TreeObj)subNode.getUserObject()).treeIndex;
//            int n = parent.getChildCount();
//            if( i==-1 || i>n ) i=n;
//            parent.insert(subNode,i);
            parent.add(subNode);
            
            // M�morisation du parent et de l'indice du fils pour la 1�re greffe op�r�e
//            if( parentUp[0]==null ) { parentUp[0]=parent; childIndex[0]=i; }
         }
         
         // On n'est pas au bout du path, il faut donc continuer r�cursivement
         // (en fait, une boucle serait plus adapt�e, mais comme on ne descend jamais
         // bien profond, �a ne va pas g�rer
         if( pos!=-1 ) return createTreeBranch(model, subNode, treeObj, pos + 1, parentUp, childIndex);
         
         // Retourne le noeud parent
         return parent;

      } catch( Exception e ) {
         e.printStackTrace();
      }
      return null;
   }
   
//   // Permet la m�morisation des vielles branches lors
//   // d'un �lagage afin de pouvoir les r�ins�rer au bon endroit le cas �ch�ant
//   private HashMap<String, TreeObj> memoPathIndex = null;
//   
//   /** Retrouve la branche qui aurait �t� supprim�e pr�c�demment afin de l'ins�rer au bon endroit */
//   private TreeObj retrieveOldBranch(String path ) {
//      if( memoPathIndex==null ) return null;
//      TreeObj treeObj = memoPathIndex.get(path);
//      if( treeObj==null ) return null;
//      treeObj.isIn=-1;
//      return treeObj;
//   }
//   
//   /** M�morisation de la position de la branche dans l'arbre afin de pouvoir la r�ins�rer au bon endroit */
//   private void memorizeOldBranche(TreeObj treeObj) {
//      if( memoPathIndex==null ) memoPathIndex = new HashMap<String, TreeObj>(10000);
//      memoPathIndex.put(treeObj.path,treeObj);
//   }
//   
//   /** Suppression d'un noeud, et de la branche morte si n�cessaire
//    * @param treeObj l'objet associ� au noeud qu'il faut supprimer
//    */
//   protected void removeTreeBranch(TreeObj treeObj) {
//      
//      // Il faut trouver le node correspondant au treeObj
//      boolean trouve = false;
//      DefaultMutableTreeNode node=null;
//      Enumeration e = root.preorderEnumeration();
//      while( e.hasMoreElements() ) {
//         node = (DefaultMutableTreeNode) e.nextElement();
//         if( treeObj == (TreeObj) node.getUserObject() ) { trouve=true; break; }
//      }
//      if( !trouve ) return;
//      
//      removeTreeBranch(this, node);
//   }
//   
//   /** Suppression d'un node, et de la branche morte si n�cessaire */
//   private void removeTreeBranch( DefaultTreeModel model, DefaultMutableTreeNode node ) {
//      DefaultMutableTreeNode fils=null;
//      int index = -1;
//      while( node!=root && node.isLeaf() ) {
//         DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
//         TreeObj treeObj = (TreeObj) node.getUserObject();
//         index = parent.getIndex(node);
//         
//         // M�morisation de l'index afin de pouvoir r�ins�rer la branche au bon endroit
//         treeObj.treeIndex = index;
//         
//         // S'il s'agit d'un noeud non terminal, on va le m�moriser pour pouvoir
//         // le r�sins�rer � la bonne place le cas �ch�ant
//         if( !(treeObj instanceof TreeObjReg) ) memorizeOldBranche(treeObj);
//         
//         parent.remove(index);
//         fils = node;
//         node = parent;
//      }
//      
//      // On alerte les listeners qu'une branche a �t� supprim�e
//      if( fils!=null ) model.nodesWereRemoved(node, new int[]{index}, new Object[]{fils} );
//   }
   
}
