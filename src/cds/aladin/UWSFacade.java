/**
 * 
 */
package cds.aladin;

import static cds.aladin.Constants.ABORTJOB;
import static cds.aladin.Constants.DELETEJOB;
import static cds.aladin.Constants.DELETEONEXIT;
import static cds.aladin.Constants.EMPTYSTRING;
import static cds.aladin.Constants.GETPREVIOUSSESSIONJOB;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import adql.query.ADQLQuery;
import cds.tools.MultiPartPostOutputStream;
import cds.tools.Util;
import cds.xml.UWSReader;

/**
 * @author chaitra
 *
 */
public class UWSFacade implements ActionListener{
	
	private static UWSFacade instance = null;
	public Aladin aladin;
	public JPanel asyncPanel;
	private JPanel inSessionAsyncJobsPanel;
	public JPanel jobDetails;
	private ButtonGroup radioGroup;
	private JRadioButton prevJobRadio;
	private List<UWSJob> sessionUWSJobs;
	private JTextField previousSessionJob;
	public JCheckBox deleteOnExit;
	public JButton loadbutton;
	
	public static String JOBNOTFOUNDMESSAGE;
	public static String JOBERRORTOOLTIP;
	public static String ERROR_INCORRECTPROTOCOL = "IOException. Job url not http protocol!";
	
	static {
		JOBNOTFOUNDMESSAGE = Aladin.chaine.getString("JOBNOTFOUNDMESSAGE");
		JOBERRORTOOLTIP = Aladin.chaine.getString("JOBERRORTOOLTIP");
	}
	
	public UWSFacade() {
		// TODO Auto-generated constructor stub
	}
	
	public UWSFacade(Aladin aladin) {
		// TODO Auto-generated constructor stub
		this();
		this.aladin = aladin;
	}
	
	public static synchronized UWSFacade getInstance(Aladin aladin) {
		if (instance == null) {
			instance = new UWSFacade(aladin);
		}
		return instance;
	}
	
	/**
	 * Method where an asyn job is created and handled 
	 * @param postParams 
	 */
	public void handleJob(String serverLabel, String url, ADQLQuery query, Map<String, Object> postParams) {
		UWSJob job = null;
		try {
			job = createJob(serverLabel, url, query, postParams);
			addNewJobToGui(job);
			refreshGui();
			job.run();
			job.pollForCompletion(true, this);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (job != null && UWSJob.JOBNOTFOUND.equals(job.getCurrentPhase())) {//specific case of job not found
				if (checkIfJobInCache(job)) {
					System.err.println("Job is not found, user did not ask for delete: \n"+job.getLocation().toString());
//					removeJobUpdateGui(job); Not removing deleted jobs(deleted elsewhere) from gui. maybe user needs to view ? 
					Aladin.warning(aladin.dialog, e.getMessage());
				}
			} else {
				Aladin.warning(aladin.dialog, e.getMessage());
			}
			if (job != null) {
				job.showAsErroneous();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			if (job != null) {
				job.showAsErroneous();
			}
			Aladin.warning(aladin.dialog, e.getMessage());
		}
		
	}
	
	/**
	 * Method creates an sync job
	 * @param newJobCreationUrl
	 * @param query
	 * @param postParams 
	 * @return
	 * @throws Exception
	 */
	public UWSJob createJob(String serverLabel, String serverBaseUrl, ADQLQuery query, Map<String, Object> postParams) throws Exception {
		UWSJob job = null;
		try {
			URL tapServerRequestUrl = new URL(serverBaseUrl+"/async");
//			URL tapServerRequestUrl = new URL("http://cdsportal.u-strasbg.fr/uwstuto/basic/timers");
			MultiPartPostOutputStream.setTmpDir(Aladin.CACHEDIR);
			String boundary = MultiPartPostOutputStream.createBoundary();
			URLConnection urlConn = MultiPartPostOutputStream.createConnection(tapServerRequestUrl);
			urlConn.setRequestProperty("Accept", "*/*");
			urlConn.setRequestProperty("Content-Type", MultiPartPostOutputStream.getContentType(boundary));
			// set some other request headers...
			urlConn.setRequestProperty("Connection", "Keep-Alive");
			urlConn.setRequestProperty("Cache-Control", "no-cache");
			MultiPartPostOutputStream out = new MultiPartPostOutputStream(urlConn.getOutputStream(), boundary);

			//standard request parameters
			out.writeField("request", "doQuery");
			out.writeField("lang", "adql");
			out.writeField("version", "1.0");
			out.writeField("format", "votable");
			
			int limit = query.getSelect().getLimit();
			if (limit > 0) {
				out.writeField("maxrec", String.valueOf(limit));
			}
			out.writeField("query", query.toADQL());
			
			out.writeField("phase", "run"); // remove this if we start comparing quotes
//			out.writeField("time", "10");
//			out.writeField("name", "ti");
			
			if (postParams != null) {//this part only for upload as of now
				for (Entry<String, Object> postParam : postParams.entrySet()) {
					if (postParam.getValue() instanceof String) {
						out.writeField(postParam.getKey(), String.valueOf(postParam.getValue()));
					} else if (postParam.getValue() instanceof File) {
						out.writeFile(postParam.getKey(), null, (File) postParam.getValue(), false);
					}
				}
			}
			
			out.close();
			if (!(urlConn instanceof HttpURLConnection)) {
				throw new Exception("Error url is not http!");
			}
			
			HttpURLConnection httpClient = (HttpURLConnection) urlConn;
			httpClient.setInstanceFollowRedirects(false);
			
			if (httpClient.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) {// is accepted
				String location = httpClient.getHeaderField("Location");
				job = new UWSJob(this, serverLabel, new URL(location));
				job.setQuery(query.toADQL());
				job.setDeleteOnExit(true);
				populateJob(job.getLocation().openStream(), job);
//				getsetPhase(job);
				job.setInitialGui();
			} else {
				throw new Exception("Error in calling tap server: "+tapServerRequestUrl+"\n"+httpClient.getResponseMessage());
			}
			httpClient.disconnect();
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
		return job;
	}
	
	/**
	 * Adds newly created job into front-end and model
	 * @param job
	 */
	public synchronized void addNewJobToGui(UWSJob job) {
		if (sessionUWSJobs == null) {
			sessionUWSJobs = new ArrayList();
			inSessionAsyncJobsPanel.removeAll();
		}
		sessionUWSJobs.add(job);
		inSessionAsyncJobsPanel.add(job.gui);
		radioGroup.add(job.gui);
//		job.gui.setActionCommand(job.getLocation().toString());
//		job.gui.addActionListener(this);
	}
	
	/**
	 * Method deletes model instances and updates gui for a deleted job
	 * @param job
	 */
	public void removeJobUpdateGui(UWSJob job) {
		if (sessionUWSJobs!=null && sessionUWSJobs.contains(job)) {
			sessionUWSJobs.remove(job);
			if (job.gui.isSelected()) {
				if (sessionUWSJobs.size()>0) {
					sessionUWSJobs.get(0).gui.setEnabled(true);
					sessionUWSJobs.get(0).gui.doClick();
				} else {
					clearJobsummary();
					deleteOnExit.setVisible(false);
				}
			}
			inSessionAsyncJobsPanel.remove(job.gui);
			inSessionAsyncJobsPanel.revalidate();
			inSessionAsyncJobsPanel.repaint();
		}
	}
	
	/**
	 * Checks if the job is stored in cache
	 * @param job
	 * @return
	 */
	public boolean checkIfJobInCache(UWSJob job) {
		boolean result = false;
		if (sessionUWSJobs != null && sessionUWSJobs.contains(job)) {
			result = true;
		} else {
			result = false;
		}
		return result;
	}
	
	public synchronized void refreshGui() {
		asyncPanel.revalidate();
		asyncPanel.repaint();
	}
	
	public void clearJobsummary() {
		jobDetails.removeAll();
		jobDetails.revalidate();
		jobDetails.repaint();
	}
	
	/**
	 * Basic buffered reader for http get
	 * @param url
	 * @return response
	 */
	public static StringBuffer getResponse(URL url) {
		BufferedReader buffReader = null;
		StringBuffer result = null;
		try {
			buffReader = new BufferedReader(new InputStreamReader(Util.openStream(url)));
			String response;
			result = new StringBuffer();
			while ((response = buffReader.readLine()) != null) {
				result.append(response);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			result = null;
		} finally {
			if (buffReader!=null) {
				try {
					buffReader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return result;
	}
	
	public void checkPhase(UWSJob job) {
		//404 for non-exixtant job id
		//500 Internal ser err
		//200 normally inspite of error- need to check the error part
	}
	
	/**
	 * Method updates job based on the new uws status
	 * @param inputStream
	 * @param job
	 */
	public static void populateJob(InputStream inputStream, UWSJob job) {
//		SavotPullParser savotPullParser = new SavotPullParser(httpClient.getInputStream(), SavotPullEngine.FULL, null);
		UWSReader uwsReader = new UWSReader();
		synchronized (job) {
			uwsReader.load(inputStream, job);
		}
//		try (Scanner scanner = new Scanner(httpClient.getInputStream())) {
	}
	
	/**
	 * Sets the phase of uws job
	 * @param uwsJob
	 * @throws IOException
	 */
	public static void getsetPhase(UWSJob uwsJob) throws IOException {
		URL phaseUrl = new URL(uwsJob.getLocation().toString() + "/phase");
		StringBuffer result = getResponse(phaseUrl);
		if (result != null) {
			uwsJob.setCurrentPhase(result.toString().toUpperCase());
		}
	}
	
	/**
	 * Method deletes UWS job.
	 * @param job
	 * @throws IOException 
	 */
	public void deleteJob(UWSJob job, boolean updateModelAndGui) throws IOException {
		HttpURLConnection httpConn = null;
		try {
			URLConnection conn = job.getLocation().openConnection();
			if (conn instanceof HttpURLConnection) {
				httpConn = (HttpURLConnection) conn;
				httpConn.setInstanceFollowRedirects(false);
				httpConn.setRequestMethod("DELETE");
				httpConn.connect();
				if (httpConn.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) {
					if (updateModelAndGui) {
						removeJobUpdateGui(job);
					}
				} else {
					throw new IOException("Error when deleting job!");
				}
			}
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw e;
		} finally {
			if (httpConn!=null) {
				httpConn.disconnect();
			}
		}
	}
	
	/**
	 * sets the Uws job panel
	 * @return
	 */
	public JPanel instantiateGui() {
		if (asyncPanel == null) {
			asyncPanel = new JPanel(new GridBagLayout());
			asyncPanel.setBackground(Aladin.LBLUE);
			asyncPanel.setFont(Aladin.PLAIN);
			if (inSessionAsyncJobsPanel == null) {
				inSessionAsyncJobsPanel = new JPanel();
				if (sessionUWSJobs == null) {
					JLabel infoLabel = new JLabel("No jobs from the active session.");
					infoLabel.setFont(Aladin.LITALIC);
					inSessionAsyncJobsPanel.add(infoLabel);
				}
				inSessionAsyncJobsPanel.setBackground(Aladin.LBLUE);
				inSessionAsyncJobsPanel.setLayout(new BoxLayout(inSessionAsyncJobsPanel, BoxLayout.Y_AXIS));
			}
			radioGroup = new ButtonGroup();
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = 0;
			c.gridwidth = 1;
			c.weightx = 1;
			
			JScrollPane jobsScroll = new JScrollPane(inSessionAsyncJobsPanel);
			jobsScroll.setBackground(Aladin.BLUE);
			jobsScroll.getVerticalScrollBar().setUnitIncrement(5);
			jobsScroll.setMinimumSize(new Dimension(200, 100));
			c.weighty = 0.48;
//			c.anchor = GridBagConstraints.NONE;
			c.fill = GridBagConstraints.BOTH;
			c.insets = new Insets(4, 7, 0, 4);
			jobsScroll.setBorder(BorderFactory.createTitledBorder("Asynchronous jobs of current session:"));
			asyncPanel.add(jobsScroll,c);
			
			JPanel searchPanel = new JPanel();
			searchPanel.setBackground(Aladin.BLUE);
			searchPanel.setName("searchPanel");
			prevJobRadio = new JRadioButton("Job URL");
			searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
			radioGroup.add(prevJobRadio);
			searchPanel.add(prevJobRadio);
			prevJobRadio.addActionListener(this);
			previousSessionJob = new JTextField(25);
			searchPanel.add(previousSessionJob);
			JButton button = new JButton("GO");
			button.setActionCommand(GETPREVIOUSSESSIONJOB);
			button.addActionListener(this);
			searchPanel.add(button);
			c.gridy++;
			c.weighty = 0.01;
			c.insets = new Insets(7, 7, 0, 4);
			c.fill = GridBagConstraints.HORIZONTAL;
//			c.anchor = GridBagConstraints.BASELINE;
			searchPanel.setBorder(BorderFactory.createTitledBorder("Or choose an already submitted job:"));
			asyncPanel.add(searchPanel,c);
			
			JPanel actionPanel = new JPanel();
			actionPanel.setBackground(Aladin.LBLUE);
			button = new JButton("ABORT");
			button.setActionCommand(ABORTJOB);
			button.addActionListener(this);
			actionPanel.add(button);
			button = new JButton("DELETE");
			button.setActionCommand(DELETEJOB);
			button.addActionListener(this);
			actionPanel.add(button);
			deleteOnExit = new JCheckBox("Delete on closing Aladin");
			deleteOnExit.setActionCommand(DELETEONEXIT);
			deleteOnExit.addActionListener(this);
			deleteOnExit.setSelected(true);
			deleteOnExit.setVisible(false);
			actionPanel.add(deleteOnExit);
			c.gridy++;
			asyncPanel.add(actionPanel,c);
			
			this.jobDetails = new JPanel();
			jobsScroll = new JScrollPane(this.jobDetails); 
			c.insets = new Insets(4, 7, 7, 4);
			jobsScroll.setBackground(Aladin.BLUE);
			this.jobDetails.setVisible(false);
			this.jobDetails.setBorder(BorderFactory.createTitledBorder("Job details:"));
			c.gridy++;
			c.weighty = 0.50;
			c.fill = GridBagConstraints.BOTH;
			asyncPanel.add(jobsScroll,c);
		}
		return asyncPanel;
	}
	
	//TODO:: impl
	public UWSJob getQuote(URL url) {
		return null;
	}
	
	public void authenticate(){
		
	}
	
	public void getResults() {
		//cursor impl
	}
	
	/**
	 * Method gets the job with specified url
	 */
	public UWSJob getJobFromCache(String url) {
		UWSJob result = null;
		if (sessionUWSJobs!=null) {
			for (UWSJob uwsJob : sessionUWSJobs) {
				if (uwsJob.getLocation().toString().equalsIgnoreCase(url)) {
					result = uwsJob;
					break;
				}
			}
		}
		return result;
	}
	
	/**
	 * Method gets the selected job on frame
	 * @return
	 */
	public UWSJob getSelectedInSessionJob() {
		UWSJob selectedJob = null;
		for (UWSJob uwsJob : sessionUWSJobs) {
			if (uwsJob.gui.isSelected()) {
				selectedJob = uwsJob;
			}
		}
		return selectedJob;
	}
	
	/**
	 * Method usually called on exit to delete all asyn jobs set to delete by user
	 * Clean up method: won't bother with checking if job is still executing
	 */
	public void deleteAllSetToDeleteJobs() {
		// TODO Auto-generated method stub
		if (this.sessionUWSJobs != null) {
			for (UWSJob uwsJob : this.sessionUWSJobs) {
				if (uwsJob.isDeleteOnExit()) {
					try {
						deleteJob(uwsJob, false);
					} catch (IOException e) {
						// We do nothing on quit
					}
				}
			}
		}
	}
	
	/**
	 * loads the selected result(from the drop down by the user) of the job
	 * @param uwsJob
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws Exception
	 */
	public void loadResults(UWSJob uwsJob, String result) throws MalformedURLException, IOException, Exception {
		if (UWSJob.COMPLETED.equals(uwsJob.getCurrentPhase())) {
			String resultsUrl = uwsJob.getResultsPath(result);
			aladin.calque.newPlan(resultsUrl, uwsJob.getServerLabel(), null);
		}
	}
	
	public UWSJob processJobSelection(boolean loadJobSummary) throws Exception {
		UWSJob selectedJob = null;
		try {
			if (prevJobRadio.isSelected()) {
				if (previousSessionJob.getText().isEmpty()) {
					throw new Exception("Please enter the job url!");
				} else {
					URL jobUrl = new URL(previousSessionJob.getText());
					selectedJob = getJobFromCache(jobUrl.toString());
					if (selectedJob == null) {
						selectedJob = new UWSJob(this, EMPTYSTRING, jobUrl);
						populateJob(selectedJob.getLocation().openStream(), selectedJob);
					}
					if (loadJobSummary) {
						selectedJob.setJobDetailsPanel();
						if (sessionUWSJobs == null) {
							asyncPanel.revalidate();
							asyncPanel.repaint();
						}
					}
				}
			} else {
				selectedJob = getSelectedInSessionJob();
			}
			if (selectedJob == null) {
				throw new Exception("Cannot process results for selection! Please select again.");
			}
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			if (Aladin.levelTrace >= 3) e1.printStackTrace();
			throw e1;
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			if (Aladin.levelTrace >= 3) e1.printStackTrace();
			throw new IOException("No job found for the given url \n"+e1.getMessage());
		}
		return selectedJob;
	}
	
	
	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		Object o = e.getSource();

	      // Affichage du selecteur de fichiers
		if (o instanceof JRadioButton) {
			//if it is a valid job which was previously parsed- load it.
			//if use asks to load then make it a job- by parsing the job
			if (prevJobRadio.isSelected() && !previousSessionJob.getText().isEmpty()) {
				try {
					URL jobUrl = new URL(previousSessionJob.getText());
					UWSJob selectedJob = getJobFromCache(jobUrl.toString());
					if (selectedJob!=null) {
						selectedJob.setJobDetailsPanel();
						deleteOnExit.setVisible(false);
						if (loadbutton!=null) {
							loadbutton.setEnabled(true);
						}
						if (sessionUWSJobs == null) {
							asyncPanel.revalidate();
							asyncPanel.repaint();
						}
					}
				} catch (Exception e1) {
					//all suppressed if user just clicks on previous job button
				}
			}
		}	
		else if(o instanceof JButton) {
	    	  String action = ((JButton)o).getActionCommand();
	    	  if (action.equals(GETPREVIOUSSESSIONJOB)) {
	    		//this go button is for getting prev job. behavior : won't check if corresponding radio is selected or not, but directly selects it
				prevJobRadio.setSelected(true);
				try {
					UWSJob selectedJob = processJobSelection(true);
				} catch (Exception e1) {
					Aladin.warning(asyncPanel, e1.getMessage());
				}
			} else if (action.equals(DELETEJOB)) {
				try {
					UWSJob selectedJob = processJobSelection(false);
					deleteJob(selectedJob, true);
					if (prevJobRadio.isSelected()) {
						Aladin.info(asyncPanel, selectedJob.getJobId()+" -job sucessfully deleted");
					}
				} catch (ProtocolException e1) {
					Aladin.warning(asyncPanel, e1.getMessage());
				} catch (IOException e1) {
					Aladin.warning(asyncPanel, e1.getMessage());
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					Aladin.warning(asyncPanel, e1.getMessage());
				}
			}
			 else if (action.equals(ABORTJOB)) {
				UWSJob selectedJob = null;
				try {
					selectedJob = processJobSelection(false);
					if (selectedJob.canAbort()) {
						selectedJob.abortJob();
						if (prevJobRadio.isSelected()) {
							selectedJob.updateGui(null);
							Aladin.info(asyncPanel, selectedJob.getJobId()+" -job sucessfully aborted");
						}
					} else {
						Aladin.warning(asyncPanel, "Cannot abort job when phase is: "+selectedJob.getCurrentPhase());
						return;
					}
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					if (selectedJob != null) {
						Aladin.warning(asyncPanel, "Please try again. Error when aborting job: "+selectedJob.getJobId());
					} else {
						Aladin.warning(asyncPanel, e1.getMessage());
					}
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					Aladin.warning(asyncPanel, e1.getMessage());
				}
			}	    	  
		} else if (o instanceof JCheckBox) {//DELETEONEXIT action command not checked for now
			try {
				UWSJob selectedJob = processJobSelection(false);
				selectedJob.setDeleteOnExit(((JCheckBox)o).isSelected());
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				Aladin.warning(asyncPanel, e1.getMessage());
			}
		}
	}
	
	

	
}