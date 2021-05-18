package scheduler_GPG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import enums.Enums.VisionType;
import goalplantree.ActionNode;
import goalplantree.TreeNode;
import util.Log;

public class MCTS_Scheduler_GPG extends Scheduler_GPG {

	public VisionType vision_type;
	
	public MCTS_Node_GPG rootNode;
	
	public int alpha, beta;
	public double c, rollout_stochasticity, assumed_politeness_of_other_agents;
	private Scheduler_GPG rollout_schedulers[];
	public boolean[] gpt_visible;
	
	private boolean legacy;
	
    // random
    static Random rm = new Random();
    
    // a very small value used for breaking the tie and dividing by 0
    static final double epsilon = 1e-6;
	
    // statistics
    public int nRollouts;
    
    public MCTS_Scheduler_GPG(VisionType vision_type, int alpha, int beta, double c,
    	double rollout_stochasticity, double assumed_politeness_of_other_agent, boolean legacy)
    {
    	this.vision_type = vision_type;
    	this.alpha = alpha;
    	this.beta = beta;
    	this.c = c;
    	this.rollout_stochasticity = rollout_stochasticity;
    	this.assumed_politeness_of_other_agents = assumed_politeness_of_other_agent;
    	this.legacy = legacy;
    }
    
	@Override
	public void reset()
	{
		// Nothing to reset for this scheduler
	}
    
    @Override
    public void loadMatchDetails(Match_GPG match, int agent_num, boolean mirror_match)
    {
    	super.loadMatchDetails(match, agent_num, mirror_match);
    	
		this.gpt_visible = new boolean[match.numGoalPlanTrees];
		
		for (int intentionNum = 0; intentionNum < match.numGoalPlanTrees; intentionNum++)
		{
			int agentToAssignIntention = mirror_match? ((intentionNum + 1) % match.numAgents) : (intentionNum % match.numAgents);
			
			if (agentToAssignIntention == agent_num)
			{
				gpt_visible[intentionNum] = true; // Can always see own GPTs
			}
			else
			{
				switch(vision_type)
				{
					case FULL:
						gpt_visible[intentionNum] = true;
						break;
						
					case PARTIALLY_AWARE:
						
						if ((intentionNum / match.numAgents) % 2 == 0)
						{
							gpt_visible[intentionNum] = true;
						}
						else
						{
							gpt_visible[intentionNum] = false;
						}

						break;
						
					case UNAWARE:
					default:
						gpt_visible[intentionNum] = false;
				}
			}
		}
		
    	this.rollout_schedulers = new Scheduler_GPG[match.numAgents];
    	for (int i = 0; i < match.numAgents; i++)
    	{
            this.rollout_schedulers[i] = new Random_Scheduler_GPG(legacy, true);
    	}
    }
    
    public Decision_GPG getDecision(State_GPG state)
    {
    	if (state.isGameOver())
    	{
    		return new Decision_GPG(null, true);
    	}
    	
    	rootNode = new MCTS_Node_GPG(state, match);
    	nRollouts = 0;
    	
    	run(alpha, beta);
    	
    	ArrayList<TreeNode> actionChoices = new ArrayList<TreeNode>();
    	
        MCTS_Node_GPG currentNode = rootNode;
        
        boolean first_action_in_chain = true;
        
        while (true)
	    {
            TreeNode actionChoice = null;
        	int selected_idx = -1;
	        int visits = -1;
	        double average = Double.NEGATIVE_INFINITY;

	        Log.info("\nDepth " + actionChoices.size() + " actions available:");
	        
	        for (int i = 0; i < currentNode.children.size(); i++)
	        {
	        	TreeNode tmp_action_choice = currentNode.children.get(i).getActionChoice();
	        	
	        	if (tmp_action_choice == null)
	        	{
	            	Log.info("PASS"
	    			+ ": Ave. val = " + (currentNode.children.get(i).totValue[agent_num] / currentNode.children.get(i).nVisits)
	    			+ ", visits = " + currentNode.children.get(i).nVisits);
	        	}
	        	else
	        	{
	            	Log.info("Action " + tmp_action_choice.getType()
	    			+ ": Ave. val = " + (currentNode.children.get(i).totValue[agent_num] / currentNode.children.get(i).nVisits)
	    			+ ", visits = " + currentNode.children.get(i).nVisits);
	        	}
	
	            if ((currentNode.children.get(i).totValue[agent_num] / Math.max(currentNode.children.get(i).nVisits, 1E-6) > average)
	            	&& ((currentNode.children.get(i).getActionChoice() != null) || first_action_in_chain || (currentNode.children.size() == 1))) // Make sure we don't, for example, select a plan node and *then* select PASS.
	            {
	            	selected_idx = i;
	            	actionChoice = currentNode.children.get(i).getActionChoice();
	                visits = currentNode.children.get(i).nVisits;
	                average = currentNode.children.get(i).totValue[agent_num] / Math.max(currentNode.children.get(i).nVisits, 1E-6);
	            }
	        }
	        
	    	actionChoices.add(actionChoice);
	    	
	    	if (actionChoice == null)
	    	{
	    		Log.info("Action choice: PASS"
	    		+ " (Averaged " + average + " from " + visits + " visits)");
	    	}
	    	else
	    	{
	    		Log.info("Action choice: " + actionChoice.getType()
	        		+ " (Averaged " + average + " from " + visits + " visits)");
	    	}

	    	currentNode = currentNode.children.get(selected_idx);
	    	
	    	if (actionChoice == null || actionChoice instanceof ActionNode)
	    	{
	    		break;
	    	}
	    		
	    	// In some situations, traversing the tree by following the greedy branch doesn't actually
	    	// lead to an action node. In this case, expand below until we find an action node.
	    	if (currentNode.children == null)
	    	{
            	boolean[] intentionAvailable = new boolean[match.numGoalPlanTrees];
            	for (int int_num = 0; int_num < match.numGoalPlanTrees; int_num++)
            	{
            		intentionAvailable[int_num] = available_intentions[currentNode.state.playerTurn][int_num] && gpt_visible[int_num];
            	}
            	
	    		currentNode.expand(intentionAvailable, legacy, match.agent_names[agent_num]);
	    		
	    		double best_mean_value = Double.NEGATIVE_INFINITY;
	    		TreeNode best_action_choice = null;
	    		
	    		for (MCTS_Node_GPG c : currentNode.children)
	    		{
	    			TreeNode node_choice = c.getActionChoice();
	    			
	    			if (node_choice instanceof ActionNode)
	    			{
	    				double mean_value = Double.NEGATIVE_INFINITY + 1.0;
	    				
	    				if (currentNode.nVisitsAllNodes.containsKey(node_choice))
	    				{
	    					mean_value = currentNode.totValueAllNodes.get(node_choice)[currentNode.state.playerTurn] / currentNode.nVisitsAllNodes.get(node_choice);
	    				}
	    				
    					if (mean_value > best_mean_value)
    					{
    						best_mean_value = mean_value;
    						best_action_choice = node_choice;
    					}
	    			}
	    		}
	    		
	    		if (best_mean_value > Double.NEGATIVE_INFINITY)
	    		{
		    		Log.info("\nAction choice: " + best_action_choice.getType()
	        		+ " (Rollout average of " + best_mean_value + ")");
		    		
    				actionChoices.add(best_action_choice);
	    			break;
	    		}
	    		else
	    		{
	    			Log.info("ERROR: MCTS failed to select an action!", true);
	    		}
	    	}
	    	
	    	first_action_in_chain = false;
	    }
    	
        return new Decision_GPG(actionChoices, (rootNode.children.size() == 1));
    }
    
    /**
     * @return a node with maximum UCT value
     */
    private MCTS_Node_GPG select(MCTS_Node_GPG currentNode, boolean forceAction)
    {
        // Initialisation
    	MCTS_Node_GPG selected = null;
        double bestUCT = Double.NEGATIVE_INFINITY;
        
        // Calculate the UCT value for each of its selected nodes
        for(int i = 0; i < currentNode.children.size(); i++)
        {
            // UCT calculation
            double uctValue = currentNode.children.get(i).totValue[currentNode.state.playerTurn] / (currentNode.children.get(i).nVisits + epsilon)
            		+ c * Math.sqrt(Math.log(nRollouts + 1) / (currentNode.children.get(i).nVisits + epsilon))
            		+ epsilon * rm.nextDouble(); // For tie-breaking
            
            // Compare with the current maximum value
            if (uctValue > bestUCT)
            {
                selected = currentNode.children.get(i);
                bestUCT = uctValue;
            }
        }
        
        // Return the nodes with maximum UCT value, null if current node is a leaf node (contains no child nodes)
        return selected;
    }
    
    /**
     * The main MCTS process
     * @param alpha number of iterations
     * @param beta number of simulation per iteration
     */
    private void run(int alpha, int beta)
    {	 
        long startTime = System.currentTimeMillis();
        
        // Record the list of nodes that has been visited
        List<MCTS_Node_GPG> visited = new LinkedList<>();
        
        // Run alpha iterations
        for(int i = 0; i < alpha; i++)
        {
        	//Log.info("MCTS iter: " + (i + 1) + " / " + alpha, true);
        	
            visited.clear();
            
            // Set the current node to this node
            MCTS_Node_GPG currentNode = this.rootNode;
            
            // Add this node to the list of visited node
            visited.add(currentNode);
            
            //if (currentNode.getActionChoice() != null)
            //{
            //	Log.info("MCTS selected: " + currentNode.getActionChoice().getType(), true);
            //}
            //else
            //{
            //	Log.info("MCTS selected PASS", true);
            //}
            
            // Find the leaf node which has the largest UCT value
            //Log.info("Beginning selection...");
            while ((currentNode != null) && !currentNode.isLeaf())
            {
            	//MCTS_Node_GPG tmpNode = currentNode;
                currentNode = select(currentNode, false);
                
                if (currentNode != null)
                {
                    visited.add(currentNode);
                    
                    if (currentNode.getActionChoice() != null)
                    {
                    	//Log.info("MCTS selected: " + currentNode.getActionChoice().getType() + " for player " + tmpNode.state.playerTurn, true);
                    }
                    else
                    {
                    	//Log.info("MCTS selected PASS" + " for player " + tmpNode.state.playerTurn, true);
                    }
                }
            }
            //Log.info("Ended selection.\n");
            
            if (!currentNode.state.isGameOver())
            {
            	boolean[] intentionAvailable = new boolean[match.numGoalPlanTrees];
            	for (int int_num = 0; int_num < match.numGoalPlanTrees; int_num++)
            	{
            		intentionAvailable[int_num] = available_intentions[currentNode.state.playerTurn][int_num] && gpt_visible[int_num];
            	}
            	
	            currentNode.expand(intentionAvailable, legacy, match.agent_names[agent_num]);
	
	            // Select a node for simulation
	            currentNode = select(currentNode, true);
	            visited.add(currentNode);
	            
	            if (currentNode.getActionChoice() != null)
	            {
	            	//Log.info("MCTS selected: " + currentNode.getActionChoice().getType(), true);
	            	//Log.info("Selected action STILL valid? " + currentNode.state.isCandidate(currentNode.getActionChoice()), true);
	            }
	            else
	            {
	            	//Log.info("MCTS selected PASS", true);
	            }
            }
            
            // Simulation
            for (int j = 0; j < beta; j++)
            {
            	State_GPG endOfGame;
            	HashSet<TreeNode> nodes_visited = new HashSet<TreeNode>();
            	
            	if (currentNode.state.isGameOver())
            	{
            		endOfGame = currentNode.state;
            	}
            	else
            	{
	            	//Log.info("MCTS sub iter: " + (j + 1) + " / " + beta, true);
	            	
	            	// TODO: This assumes that the rollout schedulers are not stateful. It might be safer to clone them.
            		// NOTE: Need to use *this* scheduler's name for the rollout schedulers so that they see the same linearisation. Should probably make the code less convoluted!
            		
	                //Match_GPG m = new Match_GPG("MCTS_rollout", match.numGoalPlanTrees, match.allianceType, currentNode.state.clone(),
		            //    	rollout_schedulers, new String[] {match.agent_names[agent_num], match.agent_names[agent_num]}, match.assumed_politeness);
	                
            		String[] sim_agent_names = new String[match.numAgents];
                	for (int k = 0; k < match.numAgents; k++)
                	{
                		sim_agent_names[k] = match.agent_names[agent_num];
                	}
            		
	                Match_GPG m = new Match_GPG("MCTS_rollout", match.numGoalPlanTrees, match.allianceType, currentNode.state.clone(),
	                	rollout_schedulers, sim_agent_names, match.assumed_politeness);
	                
	                Match_Info m_info = m.run(false, false, mirror_match, gpt_visible);
	                
	                endOfGame = m_info.final_state;
	                nodes_visited = m_info.nodes_visited;
            	}
                
                nRollouts++;
                
                // Calculate the score for the other agent.
				// Only count intentions that *this* scheduler can see.
    	    	int[] other_agent_score = new int[match.numAgents];
    	    	
    			if (match.schedulers.length > 1)
    			{
			    	boolean[] intention_counts_towards_score = new boolean[endOfGame.intentions.size()];
	    	        for (int intNum = 0; intNum < endOfGame.intentions.size(); intNum++)
			    	{
			    		intention_counts_towards_score[intNum] = gpt_visible[intNum];
			    	}

	    	        for (int intNum = 0; intNum < endOfGame.intentions.size(); intNum++)
	    	        {
	    	        	if (intention_counts_towards_score[intNum])
	    	        	{
	        	            if (endOfGame.isIntentionComplete(intNum))
	    	                {
	    	                	// If the intention belongs to *this* agent
	    	                	if (available_intentions[agent_num][intNum])
	    	                	{
	    	                		for (int k = 0; k < match.numAgents; k++)
	    	                		{
	    	                			if (k != agent_num)
	    	                			{
			        	                	other_agent_score[k] += assumed_politeness_of_other_agents;
	    	                			}
	    	                		}
	    	                	}
	    	                	else // Intention belongs to some other agent
	    	                	{
	    	                		for (int k = 0; k < match.numAgents; k++)
	    	                		{
	    	                			if (available_intentions[k][intNum])
	    	                			{
	    	                				other_agent_score[k] += 1.0;
	    	                			}
	    	                		}
	    	                	}
	    	                }
	    	        	}
	    	        }
    			}
                
                // Back-propagation
                // TODO: Fix this logic later so that it caters for more than two agents. It's super hacky right now.
                double value_for_this_agent = endOfGame.getStateScore(this);
                
                for(MCTS_Node_GPG node : visited)
                {
                    node.nVisits++;

    				for (int k = 0; k < match.numAgents; k++)
    				{
    					if (k == agent_num) // Update value for this scheduler
    					{
    	                	node.totValue[k] += value_for_this_agent;
    	        			node.totSqValue[k] += value_for_this_agent * value_for_this_agent;
    					}
    					else // Update value for other agent(s)
    					{
    	                	node.totValue[k] += other_agent_score[k];
    	        			node.totSqValue[k] += other_agent_score[k] * other_agent_score[k];
    					}
    				}
        			
                    for (TreeNode t : nodes_visited)
                    {
                        // TODO: Fix this logic later so that it caters for more than two agents. It's super hacky right now.
                    	int new_visits = 1;
                    	double[] newTotValue = new double[match.schedulers.length];
                    	
                    	if (node.nVisitsAllNodes.containsKey(t))
                    	{
                    		new_visits = node.nVisitsAllNodes.get(t) + 1;
                    		newTotValue = node.totValueAllNodes.get(t);
                    	}
                    	
                    	node.nVisitsAllNodes.put(t, new_visits);
                    	
                    	newTotValue[agent_num] += value_for_this_agent;
                    	
                		for (int k = 0; k < match.numAgents; k++)
                		{
                			if (k != agent_num)
                			{
                				newTotValue[k] += other_agent_score[k];
                			}
                		}
            			
            			node.totValueAllNodes.put(t, newTotValue);
                    }
                }
            }
        }

        Log.info("MCTS calculation time = " + (System.currentTimeMillis() - startTime) + "ms");
    }
}
