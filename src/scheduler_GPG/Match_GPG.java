package scheduler_GPG;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

import enums.Enums;
import enums.Enums.AllianceType;
import goalplantree.ActionNode;
import goalplantree.GoalNode;
import goalplantree.TreeNode;
import util.Log;

public class Match_GPG
{
    public String[] agent_names;
    public int numGoalPlanTrees;
    public AllianceType allianceType;
    public int numAgents;
    public double assumed_politeness;

	public String m_name;
	public State_GPG initialState;
	public State_GPG currentState;
	public Scheduler_GPG schedulers[];

    private Decision_GPG decision;
	
    private StringBuilder str;
    
    public Match_GPG(String m_name, int numGoalPlanTrees, AllianceType allianceType, State_GPG initialState,
    		Scheduler_GPG[] schedulers, String[] agent_names, double assumed_politeness)
    {
    	this.m_name = m_name;
		this.numGoalPlanTrees = numGoalPlanTrees;
		this.allianceType = allianceType;
    	this.initialState = initialState.clone();
    	this.schedulers = schedulers;
		this.agent_names = agent_names;
		this.numAgents = schedulers.length;
		this.assumed_politeness = assumed_politeness;
		this.str = new StringBuilder();
    }
    
    private boolean getNextDecision(boolean verbose)
    {
    	boolean playerMustPass = true;
    	int iter = 0;
    	
    	while (playerMustPass && (iter < numAgents))
    	{
    		Log.info("\n" + agent_names[currentState.playerTurn] +  "'s turn...", verbose);
    		
    		// This is an expensive call, hence wrapping in "if (verbose)"
    		if (verbose)
    		{
    			Log.info(currentState.beliefs.onPrintBB());
            	//Log.info(agent_names[0] + "'s score = " + s.getStateScore(schedulers[0]), verbose);
    		}
    		
    		this.decision = schedulers[currentState.playerTurn].getDecision(currentState);
    		
    		playerMustPass = this.decision.forcedPass;
    		
            if (playerMustPass)
            {
        		Log.info("No available action, passing...", verbose);
        		
        		currentState.consecutive_passes++;
        		currentState.applyPass();
                currentState.playerTurn = (currentState.playerTurn + 1) % numAgents;

        		iter++;
            }
    	}
    	
    	boolean atLeastOnePlayerCanMove = (iter < numAgents);
    	
    	if (!atLeastOnePlayerCanMove)
    	{
    		Log.info("\nAll agents are out of actions.", verbose);
    	}
    	
    	return atLeastOnePlayerCanMove;
    }
    
    public void run_two_sided_series(boolean verbose, boolean write_results)
    {
    	run(verbose, write_results, true, false, null);
    	
    	// Alternate first agent to act for mirror match
    	State_GPG temp_state = initialState.clone();
    	initialState.playerTurn = (initialState.playerTurn + 1) % numAgents;
    	
    	run(verbose, write_results, false, true, null);
    	
    	initialState = temp_state;
    }
    
    private void printActionChosen(boolean verbose)
    {
    	String log_str = "\nAction chain = ";
    	for (int i = 0; i < this.decision.actionChoice.size() - 1; i++)
    	{
    		log_str = log_str + this.decision.actionChoice.get(i).getType() + " --> ";
    	}
    	TreeNode t = this.decision.actionChoice.get(this.decision.actionChoice.size() - 1);
    	
    	if (t == null)
    	{
    		log_str = log_str + "PASS";
    	}
    	else
    	{
    		log_str = log_str + t.getType();
    	}
		
        Log.info(log_str, verbose);
    }
    
    public Match_Info run(boolean verbose, boolean write_results, boolean mirror_match)
    {
    	return run(verbose, write_results, false, mirror_match, null);
    }
    
    public Match_Info run(boolean verbose, boolean write_results, boolean mirror_match, boolean[] visibility_mask)
    {
    	return run(verbose, write_results, false, mirror_match, visibility_mask);
    }
    
    private Match_Info run(boolean verbose, boolean write_results, boolean cache_results, boolean mirror_match, boolean[] visibility_mask)
    {
		// Reset to the initial state
    	currentState = initialState.clone();
    	
    	HashSet<TreeNode> nodes_visited = new HashSet<TreeNode>();
    	
    	for (int i = 0; i < numAgents; i++)
    	{
    		schedulers[i].reset();
    		schedulers[i].loadMatchDetails(this, i, mirror_match);
    	}
    	
    	if (visibility_mask != null)
    	{
        	for (int i = 0; i < numAgents; i++)
        	{
            	for (int j = 0; j < numAgents; j++)
            	{
            		for (int k = 0; k < numGoalPlanTrees; k++)
            		{
            			schedulers[i].available_intentions[j][k] = schedulers[i].available_intentions[j][k] && visibility_mask[k];
            		}
            	}
        	}
    	}

    	Log.info("MATCH COMMENCED", verbose);
        long startTime = System.currentTimeMillis();
    	
        // Take first action
        boolean atLeastOnePlayerCanMove = getNextDecision(verbose);
        if (atLeastOnePlayerCanMove)
        {
        	if (this.decision.actionChoice == null || this.decision.actionChoice.get(0) == null)
            {
            	Log.info("\nAction chain = PASS", verbose);
            }
            else
            {
            	printActionChosen(verbose);
            }
        }
        else
        {
        	currentState.game_over = true;
        }
  
        // While there are available executions
        while (!currentState.game_over)
        {
            // Execute the selected intention
        	if (this.decision.actionChoice.get(0) != null) // Need this in case the pass action was chosen
        	{
        		currentState.consecutive_passes = 0;
            	
        		for (TreeNode t : this.decision.actionChoice)
        		{
        			nodes_visited.add(t);
        		}
        		
        		currentState.applyActionList(this.decision.actionChoice);
        		
        		if (this.decision.actionChoice.get(this.decision.actionChoice.size() - 1) instanceof ActionNode)
        		{
                    currentState.playerTurn = (currentState.playerTurn + 1) % numAgents;
        		}
        	}
        	else
        	{
        		currentState.consecutive_passes++;
        		currentState.applyPass();
                currentState.playerTurn = (currentState.playerTurn + 1) % numAgents;
        	}

            atLeastOnePlayerCanMove = getNextDecision(verbose);
            if (atLeastOnePlayerCanMove)
            {
                if (this.decision.actionChoice == null || this.decision.actionChoice.get(0) == null)
                {
                	Log.info("\nAction chain = PASS", verbose);
                }
                else
                {
                	printActionChosen(verbose);
                }
            }
            else
            {
            	currentState.game_over = true;
            }
            
        	if (currentState.consecutive_passes >= Enums.MAX_CONSECUTIVE_PASSES)
        	{
            	Log.info("\nGame over due to repetition!", verbose);
            	currentState.game_over = true;
        		break;
        	}
        }

        long total_match_time = System.currentTimeMillis() - startTime;
        
        // This is an expensive call, hence wrapping in "if (verbose)"
        if (verbose)
        {
        	Log.info(currentState.beliefs.onPrintBB());
        }
        
        for (int i = 0; i < numAgents; i++)
        {
        	schedulers[i].match = null; // Free match memory in case the scheduler is still referenced in the main method
            Log.info(agent_names[i] + "'s score = " + currentState.getStateScore(schedulers[i]), verbose);
        }
        Log.info("", verbose);
        
        if (write_results)
        {
        	writeResults(currentState, total_match_time, cache_results);
        }
        
        return new Match_Info(currentState, nodes_visited);
    }
    
    public void writeResults(State_GPG s, long total_match_time, boolean cache_results)
    {
		try
		{
			String csv_file = Log.getLogDir() + "/match_results.csv";
			File f = new File(csv_file);
			boolean firstWrite = !f.exists();
			
			FileWriter fw = new FileWriter(csv_file, true);
	        BufferedWriter bw = new BufferedWriter(fw);
	        PrintWriter out = new PrintWriter(bw);
	        
	        // Output the header row if this is the first record we're writing
	        if (firstWrite)
	        {
	        	//out.println("MatchName,ForestName,AssumedPoliteness,MatchTimeMillis,P1Name,P1Score,P1IntentionsComplete,P2Name,P2Score,P2IntentionsComplete");
	        	out.print("MatchName,ForestName,AssumedPoliteness,MatchTimeMillis");
		        for (int i = 0; i < numAgents; i++)
		        {
		        	out.print(",P" + (i + 1) + "Name,P" + (i + 1) + "Score,P" + (i + 1) + "IntentionsComplete");
		        }
		        out.println();
	        }
	        
            str.append(m_name);
            str.append("," + initialState.forest_name);
            str.append("," + assumed_politeness);
            str.append("," + total_match_time);
            
	        for (int i = 0; i < numAgents; i++)
	        {
	        	str.append("," + agent_names[i].replace("_clone", ""));
	        	str.append("," + s.getStateScore(schedulers[i]));
	        	str.append("," + s.getNumIntentionsCompleted(schedulers[i].available_intentions[i]));
	        }
	        
	        if (cache_results)
	        {
	        	str.append(System.lineSeparator());
	        }
	        else
	        {
	        	out.println(str.toString());
		        str.setLength(0);
	        }
	        
    	    out.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * @return the next step of an action in the given intention
     */
    public TreeNode nextIstep(TreeNode node)
    {
        // if it is not the last step in a plan
        if(node.getNext() != null)
        {
            return node.getNext();
        }
        // if it is
        else
        {
            // if it is the top-level goal
            if(node.getParent() == null)
            {
                return null;
            }
            else
            {
                // get the goal it is going to achieve
                GoalNode gn = (GoalNode) node.getParent().getParent();
                return nextIstep(gn);
            }
        }
    }
}
