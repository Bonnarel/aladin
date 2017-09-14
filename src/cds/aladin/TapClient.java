// Copyright 1999-2017 - Universit� de Strasbourg/CNRS
// The Aladin program is developped by the Centre de Donn�es
// astronomiques de Strasbourgs (CDS).
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
import static cds.aladin.Constants.ADQLVALUE_FORBETWEEN;
import static cds.aladin.Constants.EMPTYSTRING;
import static cds.aladin.Constants.EXAMPLES;
import static cds.aladin.Constants.GENERIC;
import static cds.aladin.Constants.GLU;
import static cds.aladin.Constants.OBSCORE;
import static cds.aladin.Constants.REGEX_OPALPHANUM;
import static cds.aladin.Constants.REGEX_OPNUM;
import static cds.aladin.Constants.REGEX_RANGENUMBERINPUT;
import static cds.aladin.Constants.REGEX_TAPSCHEMATABLES;
import static cds.aladin.Constants.TAPFORM_STATUS_NOTLOADED;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Insets;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import adql.db.DBChecker;
import adql.db.DBType;
import adql.db.DBType.DBDatatype;
import adql.db.DefaultDBColumn;
import adql.db.DefaultDBTable;
import adql.parser.QueryChecker;
import cds.aladin.Constants.TapClientMode;
import cds.tools.Util;
import cds.xml.VOSICapabilitiesReader;

/**
 * @author chaitra
 *
 */
public class TapClient{
	
	public static String[] modeIconImages = { "gluIconV2.png", "genericIconV2.png" };//tintin remove this
	public static String modeIconImage = "settings.png";
	public static String modesLabel = "Modes";
	public static String modesToolTip;
	public static String[] modeIconToolTips = new String[4];
	public DefaultComboBoxModel model = null;
	public static String TAPGLUGENTOGGLEBUTTONTOOLTIP,RELOAD, TIPRELOAD, GENERICERROR, TARGETERROR, NOGLURECFOUND;
	
	public TapManager tapManager;
	public String tapLabel;
	public String tapBaseUrl;
	public ServerGlu serverGlu;
	public ServerTap serverTap;
	public ServerObsTap serverObsTap;
	public ServerTapExamples serverExamples;
	
	public TapClientMode mode;
	public Future<JPanel> infoPanel;
	
	//metadata
	Map<String, TapTable> tablesMetaData;
	Future<VOSICapabilitiesReader> capabilities;
    public String nodeName = null;//tintin:: TODO::for future
    public static Map<String, DBDatatype> DBDATATYPES = new HashMap();
	List<DefaultDBTable> queryCheckerTables;
	Color primaryColor = Aladin.BLUE;
    Color secondColor = new Color(198,218,239);
    private boolean editing = false;
    public Map<String, TapTable> obscoreTables = new LinkedHashMap<String, TapTable>();
    
    public TapClient() {
		// TODO Auto-generated constructor stub
	}
	
	public TapClient(TapClientMode mode, TapManager tapManager, String tapLabel, String tapBaseUrl) {
		// TODO Auto-generated constructor stub
		this.mode = mode;
		this.tapManager = tapManager;
		this.tapLabel = tapLabel;
		this.tapBaseUrl = tapBaseUrl;
		
		model = new DefaultComboBoxModel(new String []{ GLU, EXAMPLES, GENERIC, OBSCORE }); 
		model.addListDataListener(new ListDataListener() {
			@Override
			public void intervalRemoved(ListDataEvent arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void intervalAdded(ListDataEvent arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void contentsChanged(ListDataEvent arg0) {
				// TODO Auto-generated method stub
				try {
					if (!editing) {
						String selected = (String) model.getSelectedItem();
						if ((selected.equals(GLU) && serverGlu == null)
								|| selected.equals(OBSCORE) && obscoreTables.isEmpty()) {
							return;
						}
						Server serverToDisplay = getServerToDisplay(selected);
						if (Aladin.levelTrace >= 3) System.out.println("contentsChanged" + TapClient.this.mode + "  " + selected);
						if (TapClient.this.mode == TapClientMode.TREEPANEL) {
							TapClient.this.tapManager.showTapPanelFromTree(TapClient.this.tapLabel, serverToDisplay);
						} else {
							TapClient.this.tapManager.showTapServerOnServerSelector(serverToDisplay);
						}

					}
				} catch (Exception ex) {
					if (Aladin.levelTrace >= 3)
						ex.printStackTrace();
					Aladin.warning("Error! unable load tap server!" + ex.getMessage());
				}
			}

		});
	}
	
	public static TapClient getUploadTapClient(Aladin aladin, String tapLabel, String mainServerUrl) {
		TapManager tapManager = TapManager.getInstance(aladin);
		TapClient tapClient = new TapClient(TapClientMode.UPLOAD, tapManager, tapLabel, mainServerUrl);
		tapClient.serverTap = new ServerTap(aladin);
		tapClient.serverTap.tapClient = tapClient;
		tapClient.primaryColor = new Color(198,218,239);
		tapClient.secondColor = Aladin.COLOR_CONTROL_FOREGROUND;
		tapClient.tablesMetaData = new HashMap<String, TapTable>();
		return tapClient;
	}
	
	public JPanel getOptionsPanel(Server server) {
		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0 , 0));
		if (this.mode == TapClientMode.DIALOG) {
			JButton button = ServerTap.getChangeServerButton();
			button.addActionListener(server);
//			titlePanel.add(button);
			optionsPanel.add(button);
		}

		if (this.mode != TapClientMode.UPLOAD) {
			if (server instanceof DynamicTapForm) {
				JButton reloadButton = getReloadButton();
				reloadButton.addActionListener(server);
//				titlePanel.add(reloadButton);
				optionsPanel.add(reloadButton);
			}
			
			server.modeChoice = new JComboBox(model);
			server.modeChoice.setRenderer(new TapClientModesRenderer(this));
//			server.modeChoice.setBackground(Aladin.BLUE);
//			server.modeChoice.setForeground(Aladin.BLUE);
			server.modeChoice.setToolTipText(TapClient.modesToolTip);
			optionsPanel.add(server.modeChoice);
			
			//enable mode in other forms if it is already created
			if (this.serverGlu != null && this.serverTap != null && this.serverTap.modeChoice != null) {
				this.serverTap.modeChoice.revalidate();
				this.serverTap.modeChoice.repaint();
			}
		}
		return optionsPanel;
	}
	
	public static JButton getReloadButton() {
		JButton reloadButton = null;
		Image image = Aladin.aladin.getImagette("reload.png");
		if (image == null) {
			reloadButton = new JButton("Reload");
		} else {
			reloadButton = new JButton(new ImageIcon(image));
		}
		reloadButton.setBorderPainted(false);
		reloadButton.setMargin(new Insets(0, 0, 0, 0));
		reloadButton.setContentAreaFilled(true);
		reloadButton.setActionCommand(RELOAD);
		reloadButton.setToolTipText(TIPRELOAD);
		return reloadButton;
	}
	
	/**
	 * Precedence is for serverGlu. So by default serverGlu this displayed
	 * In case there is not ServerGlu configured for the tap client then 
	 * generic client is displayed
	 * @param serverType - trumps priority. if specified returns the server asked for
	 * @return
	 * @throws Exception 
	 */
	
	public Server getServerToDisplay(String serverType) throws Exception {
		Server resultServer = null;
		DynamicTapForm dynamicTapForm = null;
		editing = true;
		if ((serverType == null || serverType == GLU ) && this.serverGlu != null ) {
			resultServer = this.serverGlu;
			model.setSelectedItem(GLU);
		} else {
			if (serverType == EXAMPLES) {
				if (this.serverExamples == null) {
					this.serverExamples = tapManager.getNewServerTapExamplesInstance(this);
				} 
				dynamicTapForm = this.serverExamples;
				model.setSelectedItem(EXAMPLES);
			} else if (serverType == GENERIC) {
				if (this.serverTap == null) {
					this.serverTap = tapManager.getNewServerTapInstance(this);
				} 
				dynamicTapForm = this.serverTap;
				model.setSelectedItem(GENERIC);
			} else if (serverType == OBSCORE) {
				if (this.serverObsTap == null) {
					this.serverObsTap = tapManager.getNewServerObsTapInstance(this); //tapManager.getNewServerObsTapInstance1(this);
				} 
				dynamicTapForm = this.serverObsTap;
				model.setSelectedItem(OBSCORE);
			} else {//preference is for what is already loaded
				if (this.serverExamples != null && this.serverExamples.isLoaded()) {
					dynamicTapForm = this.serverExamples;
					model.setSelectedItem(EXAMPLES);
				} else if (this.serverObsTap != null) {
					dynamicTapForm = this.serverObsTap;
					model.setSelectedItem(OBSCORE);
				} else if (this.serverTap != null && this.serverTap.isLoaded()) {
					dynamicTapForm = this.serverTap;
					model.setSelectedItem(GENERIC);
				} else {// by default we give priority to the template based tap client over generic tap client
					this.serverExamples = tapManager.getNewServerTapExamplesInstance(this);
					dynamicTapForm = this.serverExamples;
					model.setSelectedItem(EXAMPLES);
				} 
			}
			
			if (dynamicTapForm != null && dynamicTapForm.formLoadStatus == TAPFORM_STATUS_NOTLOADED) {
				if (this.tablesMetaData == null || this.tablesMetaData.isEmpty()) {
					tapManager.createAndLoadServerTap(this, dynamicTapForm);
					// when we are explicitly getting metadata for one cache, we try to update in the other as well
					tapManager.updateServerMetaDataInCache(this);
				} else {
					dynamicTapForm.showloading();
					if (this.mode == TapClientMode.TREEPANEL) {
						this.primaryColor = Aladin.COLOR_FOREGROUND;
						this.secondColor = Color.white;
					}
					tapManager.createGenericTapFormFromMetaData(dynamicTapForm);
				}
			}
			resultServer = dynamicTapForm;
			
		}
		editing = false;
		return resultServer;
	}
	
	/**
	 * Method to update the frame info panel.
	 * 	if the old frame is visible the method reloads it.
	 * @param newInfoPanel
	 */
	protected void tackleFrameInfoServerUpdate(Aladin aladin, Future<JPanel> newInfoPanel) {
		try {
			FrameInfoServer frameInfoServer = null;
			if (this.infoPanel!=null) {
				if (this.infoPanel.isDone()) {
					JPanel infoPanel = this.infoPanel.get();
					frameInfoServer = (FrameInfoServer) SwingUtilities.getRoot(infoPanel);
					if (frameInfoServer!=null) {
						frameInfoServer.setAdditionalComponent(newInfoPanel);
						if (frameInfoServer.isVisible()) {
							frameInfoServer.updateInfoPanel();
							frameInfoServer.revalidate();
							frameInfoServer.repaint();
						} else {
							frameInfoServer.setFlagUpdate(1);
						}
					}
				} else {
					this.infoPanel.cancel(true);
				}
			} else if (aladin.frameInfoServer != null && aladin.frameInfoServer.getServer().equals(this)) {
				//this else part is for a specific case where generic status report is displayed before table meta can be obtained
				frameInfoServer = new FrameInfoServer(aladin, newInfoPanel);
				if (aladin.frameInfoServer.isVisible()) {
					frameInfoServer.updateInfoPanel();
					aladin.frameInfoServer.dispose();
					aladin.frameInfoServer = frameInfoServer;
					frameInfoServer.revalidate();
					frameInfoServer.repaint();
					String visibleServer = (String) model.getSelectedItem();
					if (!visibleServer.equals(GLU)) {
						frameInfoServer.show(this.serverTap);
					}
				} else {
					frameInfoServer.setFlagUpdate(1);
			}
			}
			this.infoPanel = newInfoPanel;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			if (Aladin.levelTrace >= 3)
				e.printStackTrace();
		}

	}
	
	public void	showOnUploadFrame() {
		this.tapManager.showOnUploadFrame(this);
	}
	
	/**
	 * Method sets the database metadata to the parser
	 * and sorts tap tables into the order where there is understanding 
	 */
	public void preprocessTapClient() {
		Map<String, TapTable> backUpTablesMetaData = this.tablesMetaData;
		List<Map.Entry<String, TapTable>> sorter = new LinkedList<Map.Entry<String,TapTable>>();
		sorter.addAll(this.tablesMetaData.entrySet());
		
		Collections.sort(sorter, new Comparator<Map.Entry<String,TapTable>>() {
			@Override
			public int compare(Map.Entry<String,TapTable> o1, Map.Entry<String,TapTable> o2) {
				// TODO Auto-generated method stub
				int one = 0;
				int two = 0;
				if (o2.getValue().flaggedColumns != null) {
					two = o2.getValue().flaggedColumns.size();
				}
				if (o2.getValue().obsCoreColumns != null) {
					two = two + o2.getValue().obsCoreColumns.size();
				}
				if (o1.getValue().flaggedColumns != null) {
					one = o1.getValue().flaggedColumns.size();
				}
				if (o1.getValue().obsCoreColumns != null) {
					one = one + o1.getValue().obsCoreColumns.size();
				}
				return two-one;
			}
		});
		
		if (this.queryCheckerTables == null) {//initialise for the first time
			this.queryCheckerTables = new ArrayList<DefaultDBTable>();
		} else {
			this.queryCheckerTables.clear();
		}
		//sory as per size.
		//make obscore second choice- if it exixts
		//place tap schema at the last
		Map<String, TapTable> sorted = new LinkedHashMap<String, TapTable>();
		Map<String, TapTable> tapSchemaMeta = new LinkedHashMap<String, TapTable>();
		for (Entry<String, TapTable> metaDataEntry : sorter) {
			DefaultDBTable parserTable = new DefaultDBTable(metaDataEntry.getKey());
			Vector<TapTableColumn> columnsMeta = backUpTablesMetaData.get(metaDataEntry.getKey()).getColumns();
			updateQueryCheckTableColumns(parserTable, columnsMeta);
			this.queryCheckerTables.add(parserTable);
			if (isSchemaTable(metaDataEntry.getKey())) {
				tapSchemaMeta.put(metaDataEntry.getKey(), metaDataEntry.getValue());//tap schema stuff for the end
			} else {
				sorted.put(metaDataEntry.getKey(), metaDataEntry.getValue());
			}
		}
		sorted.putAll(tapSchemaMeta);
		this.tablesMetaData = sorted;
	}

	public boolean isSchemaTable(String tableName) {
		// TODO Auto-generated method stub
		boolean result = false;
		Pattern tapSchemaPattern = Pattern.compile(REGEX_TAPSCHEMATABLES, Pattern.CASE_INSENSITIVE);
		Matcher matcher = tapSchemaPattern.matcher(tableName);
		if (matcher.find()) {
			result = true;
		}
		return result;
	}

	/**
	 * Convenience method to set column to table for parser
	 * @param parserTable
	 * @param columnsMeta
	 */
	private static void updateQueryCheckTableColumns(DefaultDBTable parserTable, Vector<TapTableColumn> columnsMeta) {
		if (parserTable != null && columnsMeta != null) {
			for(TapTableColumn tapTableColumn : columnsMeta) {
				DefaultDBColumn columnForParser = new DefaultDBColumn(tapTableColumn.getColumn_name(), parserTable);
				if (tapTableColumn.getDatatype() != null && !tapTableColumn.getDatatype().isEmpty()) {
					int offset = tapTableColumn.getDatatype().indexOf("adql:");
					if (offset != -1 && offset + 5 < tapTableColumn.getDatatype().length()) {
						String datatype = tapTableColumn.getDatatype().substring(offset + 5);
						if (TapClient.DBDATATYPES.containsKey(datatype)) {
							DBDatatype dbDataType = TapClient.DBDATATYPES.get(datatype);
							DBType type = null;
							if (tapTableColumn.getSize() > 0) {
								type = new DBType(dbDataType, tapTableColumn.getSize());
							} else {
								type = new DBType(dbDataType);
							}
							columnForParser.setDatatype(type);
						}
					}
				}
				parserTable.addColumn(columnForParser);
			}
		}
	}
	
	/*public void updateObscoreGui(String tableName, TapTable potentialObscoreTable) {
		// TODO Auto-generated method stub
		//add parsing tables that you think are obscore
		boolean isObsCore = parseForObscore(tableName, potentialObscoreTable);
		if (isObsCore) {
			synchronized (obscoreTables) {
				obscoreTables.put(tableName, potentialObscoreTable);
			}
		} else {
			this.serverObsTap = null;
		}
	}
	
	//TODO::: tintin refactor may be move this to obstap server constructor
	public boolean parseForObscore(String tableName, TapTable tapTable) {
		String priRaColumnName = tapTable.getRaColumnName();
		String priDecColumnName = tapTable.getDecColumnName();
		int mandatoryColumnCount = 0;
		
		String selectStatement1 = null;
		String selectStatement2 = null;
		
		String dataProductTypeParamName = null;
		Map<String, String> spatialFieldValueOptions = new HashMap<String, String>();
		Map<String, String> timeFieldValueOptions = new HashMap<String, String>();
		Map<String, String> spectralFieldValueOptions = new HashMap<String, String>();
		
		//17 mandatory columns counted
		if (tapTable.getColumns() != null) {
			for (TapTableColumn columnMeta : tapTable.getColumns()) {
				String name = columnMeta.getColumn_name();
				String utype = columnMeta.getUtype();
				if (name == null && utype == null) {
					continue;
				}
				if (name == null) {
					name = EMPTYSTRING;
				}
				if (utype == null) {
					utype = EMPTYSTRING;
				}
				if (utype.equals("obscore:ObsDataset.dataProductType") || name.equalsIgnoreCase("dataproduct_type")) {
					dataProductTypeParamName = columnMeta.getColumn_name();
					mandatoryColumnCount++;
				} else if (utype.equals("obscore:DataID.observationID") || name.equalsIgnoreCase("obs_id")) {
					mandatoryColumnCount++;
					selectStatement1 = name;
					if (priRaColumnName != null && priDecColumnName != null) {
						selectStatement1 = selectStatement1+COMMA_CHAR+priRaColumnName+COMMA_CHAR+priDecColumnName;
					}
				} else if (utype.equals("obscore:DataID.title") || name.equalsIgnoreCase("obs_title")) {
				} else if (utype.equals("obscore:Curation.reference") || name.equalsIgnoreCase("bib_reference")) {
				} else if (utype.equals("obscore:Access.reference") || name.equalsIgnoreCase("access_url")) {
					mandatoryColumnCount++;
					if (selectStatement2 == null) {
						selectStatement2 = name;
					} else {
						selectStatement2 = selectStatement2+COMMA_CHAR+name;
					}
				} else if (utype.equals("obscore:Access.format") || name.equalsIgnoreCase("access_format")) {
					mandatoryColumnCount++;
					if (selectStatement2 == null) {
						selectStatement2 = name;
					} else {
						selectStatement2 = selectStatement2+COMMA_CHAR+name;
					}
				} else if (utype.equals("obscore:Access.size") || name.equalsIgnoreCase("access_estsize")) {
					mandatoryColumnCount++;
					if (selectStatement2 == null) {
						selectStatement2 = name;
					} else {
						selectStatement2 = selectStatement2+COMMA_CHAR+name;
					}
				} else if (utype.equals("Char.SpatialAxis.Coverage.Location.Coord.Position2D.Value2.C1")
						|| name.equalsIgnoreCase("s_ra")) {
					mandatoryColumnCount++;
					priRaColumnName = columnMeta.getColumn_name();
					spatialFieldValueOptions.put("ra", priRaColumnName);
				} else if (utype.equals("Char.SpatialAxis.Coverage.Location.Coord.Position2D.Value2.C2")
						|| name.equalsIgnoreCase("s_dec")) {
					mandatoryColumnCount++;
					priDecColumnName = columnMeta.getColumn_name();
					spatialFieldValueOptions.put("dec", priDecColumnName);
				} else if (utype.equals("Char.SpatialAxis.Coverage.Support.Area") || name.equals("s_region")) {
					mandatoryColumnCount++;
					spatialFieldValueOptions.put("Field size", "AREA("+columnMeta.getColumn_name()+")");
				} else if (utype.equals("obscore:Char.SpatialAxis.Resolution.refval")
						|| name.equalsIgnoreCase("s_resolution")) {
					mandatoryColumnCount++;
					spatialFieldValueOptions.put(ServerObsTap.SPATIALRESOLUTION, columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.TimeAxis.Coverage.Bounds.Limits.StartTime")
						|| name.equalsIgnoreCase("t_min")) {
					mandatoryColumnCount++;
					timeFieldValueOptions.put("t_min", columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.TimeAxis.Coverage.Bounds.Limits.StopTime")
						|| name.equalsIgnoreCase("t_max")) {
					mandatoryColumnCount++;
					timeFieldValueOptions.put("t_max", columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.TimeAxis.Coverage.Support.Extent")
						|| name.equalsIgnoreCase("t_exptime")) {
					mandatoryColumnCount++;
					timeFieldValueOptions.put(ServerObsTap.EXPOSURETIME, "t_exptime");
				} else if (utype.equals("Char.TimeAxis.Resolution.Refval.valueResolution.Refval.value")
						|| name.equalsIgnoreCase("t_resolution")) {
					mandatoryColumnCount++;
					timeFieldValueOptions.put(ServerObsTap.TIMERESOLUTION , "t_resolution");
				} else if (utype.equals("obscore:Char.SpectralAxis.Coverage.Bounds.Limits.LoLimit")
						|| name.equalsIgnoreCase("em_min")) {
					mandatoryColumnCount++;
					spectralFieldValueOptions.put("em_min", columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.SpectralAxis.Coverage.Bounds.Limits.HiLimit")
						|| name.equalsIgnoreCase("em_max")) {
					mandatoryColumnCount++;
					spectralFieldValueOptions.put("em_max", columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.SpectralAxis.Resolution.Refval.value")
						|| name.equalsIgnoreCase("em_res")) {
					spectralFieldValueOptions.put(ServerObsTap.SPECTRALRESOLUTION, columnMeta.getColumn_name());
				} else if (utype.equals("Char.SpectralAxis.Resolution.ResolPower.refVal")
						|| name.equalsIgnoreCase("em_res_power")) {
					mandatoryColumnCount++;
					spectralFieldValueOptions.put(ServerObsTap.SPECTRALRESOLUTIONPOWER, columnMeta.getColumn_name());
				} else if (utype.equals("obscore:Char.SpectralAxis.ucd") || name.equalsIgnoreCase("em_ucd")) {
				} else if (utype.equals("obscore:Char.ObservableAxis.ucd") || name.equalsIgnoreCase("o_ucd")) {
					mandatoryColumnCount++;
				} else if (utype.equals("obscore:Char.ObservableAxis.unit") || name.equalsIgnoreCase("o_unit")) {
				} else if (utype.equals("obscore:Char.PolarizationAxis.stateList")
						|| name.equalsIgnoreCase("pol_states")) {
				}
			}
		}
		
		boolean isObscore = false;
		if (mandatoryColumnCount > 6) {//==17
			isObscore = true;
			if (this.serverObsTap == null) {
				this.serverObsTap = tapManager.getNewServerObsTapInstance(this);
			}
			if (this.serverObsTap.selectAllOptions == null) {
				this.serverObsTap.selectAllOptions = new HashMap<String, List<String>>();
			}
			this.serverObsTap.selectAllOptions.put(tableName, new ArrayList<String>());
			this.serverObsTap.selectAllOptions.get(tableName).add(" * ");
			if (selectStatement1 != null) {
				this.serverObsTap.selectAllOptions.get(tableName).add(selectStatement1);
			}
			if (selectStatement2 != null) {
				this.serverObsTap.selectAllOptions.get(tableName).add(selectStatement2);
			}
			if (this.serverObsTap.dataProductTypeParamName == null) {
				this.serverObsTap.dataProductTypeParamName = new HashMap<String, String>();
			}
			this.serverObsTap.dataProductTypeParamName.put(tableName, dataProductTypeParamName);
			
			if (this.serverObsTap.spatialFieldValueOptions == null) {
				this.serverObsTap.spatialFieldValueOptions = new HashMap<String, Map<String, String>>();
			}
			this.serverObsTap.spatialFieldValueOptions.put(tableName, spatialFieldValueOptions);
			
			if (this.serverObsTap.timeFieldValueOptions == null) {
				this.serverObsTap.timeFieldValueOptions = new HashMap<String, Map<String, String>>();
			}
			this.serverObsTap.timeFieldValueOptions.put(tableName, timeFieldValueOptions);
			
			if (this.serverObsTap.spectralFieldValueOptions == null) {
				this.serverObsTap.spectralFieldValueOptions = new HashMap<String, Map<String, String>>();
			}
			this.serverObsTap.spectralFieldValueOptions.put(tableName, spectralFieldValueOptions);
			this.serverObsTap.addOtherParams(tapTable.getTable_name());
		} 
		return isObscore;
	}*/
	
	
	/**
	 * Method checks of operators are correctly used in the input
	 * 
	 * @param input
	 * @return
	 */
	public static String isInValidOperatorNumber(Server s, String input, boolean showError) {
		// TODO Auto-generated method stub
		String inputInProgress = null;
		Pattern regex = Pattern.compile(REGEX_OPNUM);// find no special chars
		Matcher matcher = regex.matcher(input);
		if (matcher.find()) {
			if (matcher.group("operator") == null) {
				inputInProgress = "=".concat(input);
			} else {
				inputInProgress = input;
			}
		} else if (showError) {
			Aladin.warning(s, input + " is incorrect! Please rectify. Valid examples are: >3, 10..12, <=-788 etc..");
			s.ball.setMode(Ball.NOK);
		}
		return inputInProgress;
	}
	
	/**
    * Method retrives the adql value for between operator from a range
    * input 1..3 or 1 3 or 1,3
    * output BETWEEN 1 and 3
    * @param input
    * @return
    */
	public static String getRangeInput(String input, String outputRegEx) {
	   String result = EMPTYSTRING;
		Pattern regex = Pattern.compile(REGEX_RANGENUMBERINPUT);
		Matcher matcher = regex.matcher(input);
		if (matcher.find()){
			if (outputRegEx == null || outputRegEx.isEmpty()) {
				outputRegEx = ADQLVALUE_FORBETWEEN;
			}
			result = matcher.replaceAll(outputRegEx);
		}
		return result;
   }
	
	/**
	 * method validates(invalid input --> null returned) if valid operators are provided for a text column constraint
	 * @param input
	 * @return
	 */
	public static String getStringInput(String input, boolean processAsString) {
		String inputInProgress = null;
		Pattern regex = Pattern.compile(REGEX_OPALPHANUM, Pattern.CASE_INSENSITIVE);// find no special chars
		Matcher matcher = regex.matcher(input);
		if (matcher.find()) {
			String value = matcher.group("value");
			String operator =matcher.group("operator");
			
			if ( operator == null || operator.isEmpty()) {
				operator = "= ";
			}
			if (value !=null && !value.isEmpty()) {
				inputInProgress = operator.toUpperCase()+" "+Util.formatterPourRequete(processAsString, matcher.group("value"));//matcher.replaceAll(PROCESSED_REGEX_OPALPHANUM);
			}
		} 
		return inputInProgress;
	}
	
	/**
	 * Reloads the generic tap client
	 * @param dynamicTapForm 
	 * @throws Exception 
	 */
	public void reload(DynamicTapForm dynamicTapForm) throws Exception {
		if (this.serverTap != null) {
			this.serverTap.removeAll();
			this.serverTap.formLoadStatus = TAPFORM_STATUS_NOTLOADED;
		} 
		if (this.serverExamples != null) {
			this.serverExamples.removeAll();
			this.serverExamples.formLoadStatus = TAPFORM_STATUS_NOTLOADED;
		}
		
		if (this.serverObsTap != null) {
			this.serverObsTap.removeAll();
			this.serverObsTap.formLoadStatus = TAPFORM_STATUS_NOTLOADED;
		}
		tapManager.createAndLoadServerTap(this, dynamicTapForm);
		if (this.mode == TapClientMode.TREEPANEL) {
			tapManager.showTapPanelFromTree(tapLabel, dynamicTapForm);
		} else {
			tapManager.showTapServerOnServerSelector(dynamicTapForm);
		}
		dynamicTapForm.revalidate();
		dynamicTapForm.repaint();
	}
	
	public boolean isUploadAllowed() {
		boolean result = false;
		if (this.capabilities != null) {
			VOSICapabilitiesReader meta;
			try {
				meta = this.capabilities.get();
				result = meta.isUploadAllowed();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return result;
	}
	
	public void updateUploadedTablesToParser(DynamicTapForm server) {
		// TODO Auto-generated method stub
		if ((this.mode != TapClientMode.UPLOAD)) {
			List<DefaultDBTable> queryCheckerTables = new ArrayList<DefaultDBTable>();
			queryCheckerTables.addAll(this.queryCheckerTables);
			if (tapManager.uploadFrame != null) {
				List<String> uploadedTables = new ArrayList<String>();
				uploadedTables.addAll(tapManager.uploadFrame.uploadClient.tablesMetaData.keySet());
				if (!uploadedTables.isEmpty()) {
					for (String uploadedTable : uploadedTables) {
						server.updateQueryChecker(true, uploadedTable, tapManager.uploadFrame.uploadClient.tablesMetaData, queryCheckerTables);
					}
				}
			}
			QueryChecker checker = new DBChecker(queryCheckerTables);
			server.adqlParser.setQueryChecker(checker);
		}
	}
	
	public String getVisibleLabel() {
		// TODO Auto-generated method stub
		String results = this.tapLabel;
		if (this.tapLabel.equalsIgnoreCase(this.tapBaseUrl)) {
			try {
				results = Util.getDomainNameFromUrl(this.tapBaseUrl);
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 
		return results;
	}
	
	public void updateTableColumnSchemas(List<String> tableNamesToUpdate) throws Exception {
		// TODO Auto-generated method stub
		this.tapManager.updateTableColumnSchemas(this, tableNamesToUpdate);
	}
	
	public TapTable getServerExampleSelectedPrimaryTable() {
		return this.tablesMetaData.get(serverExamples.selectedTableName);
	}
	
	public TapTable getServerExampleSelectedSecondaryTable() {
		return this.tablesMetaData.get(serverExamples.secondaryTable);
	}
	
	public Vector<TapTableColumn> getServerTapSelectedTableColumns() {
		return this.tablesMetaData.get(serverTap.selectedTableName).getColumns();
	}
	
	public void setData(Map<String, TapTable> tablesMetaData) {
		// TODO Auto-generated method stub
		this.tablesMetaData = tablesMetaData;
	}
	
	static {
		TAPGLUGENTOGGLEBUTTONTOOLTIP = Aladin.chaine.getString("TAPGLUGENTOGGLEBUTTONTOOLTIP");
		RELOAD = Aladin.chaine.getString("FSRELOAD");
	    TIPRELOAD = Aladin.chaine.getString("TIPRELOAD");
	    modesToolTip = Aladin.chaine.getString("TAPMODESTOOLTIP");
	    modeIconToolTips[0] = Aladin.chaine.getString("GLUTAPMODESTOOLTIP");
	    modeIconToolTips[1] = Aladin.chaine.getString("EXAMPLETAPMODESTOOLTIP");
	    modeIconToolTips[2] = Aladin.chaine.getString("GENERICTAPMODESTOOLTIP");
	    modeIconToolTips[3] = Aladin.chaine.getString("OBSCORETAPMODESTOOLTIP");
	    GENERICERROR = Aladin.chaine.getString("GENERICERROR");
	    for (DBDatatype dbDatatype : DBDatatype.values()) {
			DBDATATYPES.put(dbDatatype.name(), dbDatatype);
		}
	    TARGETERROR = Aladin.chaine.getString("TARGETERROR");
	    NOGLURECFOUND = Aladin.chaine.getString("NOGLURECFOUND");
	}

}