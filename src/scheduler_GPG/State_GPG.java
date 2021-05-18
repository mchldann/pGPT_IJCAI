package scheduler_GPG;

import beliefbase.BeliefBase;
import beliefbase.Condition;
import goalplantree.ActionNode;
import goalplantree.GoalNode;
import goalplantree.PlanNode;
import goalplantree.TreeNode;
import util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class State_GPG {

	public final static boolean ALLOW_ACTIONS_BELOW_COMPLETED_GOALS = false;
	public final static boolean REQUIRE_ALL_STEPS_COMPLETE = true;
    
	public String forest_name;
	
    // current belief base
    public BeliefBase beliefs;

    // the list of intentions
    public ArrayList<TreeNode> intentions;
    
    public static HashMap<String, Linearisation> linearisations = new HashMap<String, Linearisation>();
    
    //private ArrayList<String> availableNodes;
    private HashSet<String> nodesVisited;
    private HashSet<String> goalsCompleted;
    
    private HashSet<TreeNode> candidateActions; // Nodes that have their temporal dependencies met
    private HashSet<String> blockedPlans;
    
    private TreeNode lastActionApplied;
    
    // a mapping from the name of the node to the node itself
    public HashMap<String, TreeNode> nodeLib;
    
    public HashMap<String, String> preqMap;
    public HashMap<String, String> parentMap;

    public int playerTurn;
    public int consecutive_passes;
    public boolean game_over;
    
    // Made private since this constructor is only meant to be called by clone()
    private State_GPG(String forest_name, BeliefBase beliefs, ArrayList<TreeNode> intentions,
    	HashMap<String, TreeNode> nodeLib, HashMap<String, String> preqMap, HashMap<String, String> parentMap, int playerTurn, int consecutive_passes, boolean game_over,
    	HashSet<String> nodesVisited, HashSet<String> goalsCompleted, HashSet<TreeNode> candidateActions, HashSet<String> blockedPlans, TreeNode lastActionApplied)
    {
    	this.forest_name = forest_name;
        this.beliefs = beliefs;
        this.intentions = new ArrayList<TreeNode>(intentions);
        
        this.nodeLib = nodeLib;
        this.preqMap = preqMap;
        this.parentMap = parentMap;
        
        this.playerTurn = playerTurn;
        this.consecutive_passes = consecutive_passes;
        this.game_over = game_over;
        
        this.nodesVisited = new HashSet<String>(nodesVisited);
        this.goalsCompleted = new HashSet<String>(goalsCompleted);
        
        this.candidateActions = new HashSet<TreeNode>(candidateActions);
        
        this.blockedPlans = new HashSet<String>(blockedPlans);
        this.lastActionApplied = lastActionApplied;
    }
    
    // For creating new states at the start of a match
    public State_GPG(String forest_name, BeliefBase beliefs, ArrayList<TreeNode> intentions, HashMap<String, TreeNode> nodeLib, HashMap<String, String> preqMap, HashMap<String, String> parentMap, int playerTurn)
    {
    	this(forest_name, beliefs, intentions, nodeLib, preqMap, parentMap, playerTurn, 0, false, new HashSet<String>(), new HashSet<String>(), new HashSet<TreeNode>(), new HashSet<String>(), null);
    
        for (TreeNode intention : intentions)
        {
        	candidateActions.add(intention);
        }
        
        jumpOverGoals();
    }
    
    public void reset_linearisations()
    {
    	linearisations = new HashMap<String, Linearisation>();
    }
    
    public void applyActionList(ArrayList<TreeNode> action_list)
    {
    	for (TreeNode a : action_list)
    	{
    		if (a == null)
    		{
    			applyPass();
    		}
    		else
    		{
    			applyAction(a);
    		}
    	}
    }
    
    public void applyPass()
    {
    	lastActionApplied = null;
    }
    
    private TreeNode getFirstIncompleteStep(PlanNode plan)
    {
		TreeNode[] p_body = plan.getPlanbody();
		for (TreeNode p_item : p_body)
		{
			if (p_item instanceof ActionNode && !nodesVisited.contains(p_item.getType()))
			{
				// Plan is not complete because an action hasn't been completed.
				return p_item;
			}
			else if (p_item instanceof GoalNode && !goalsCompleted.contains(p_item.getType()))
			{
				// Plan is not complete because a goal hasn't been completed.
				return p_item;
			}
		}
		
		return null;
    }
    
    private boolean isPlanComplete(PlanNode plan)
    {
    	if (REQUIRE_ALL_STEPS_COMPLETE)
    	{
    		return getFirstIncompleteStep(plan) == null;
    	}
    	else
    	{
    		TreeNode[] p_body = plan.getPlanbody();
    		TreeNode last_step = p_body[p_body.length - 1];
    		
    		if (last_step instanceof ActionNode && !nodesVisited.contains(last_step.getType()))
			{
				return false;
			}
			else if (last_step instanceof GoalNode && !goalsCompleted.contains(last_step.getType()))
			{
				return false;
			}
    		
    		// If we've made it here, the last step is complete.
    		return true;
    	}
    }
    
    private void updateGoalsCompleted(TreeNode t)
    {
        if (t instanceof PlanNode)
        {
        	// We've just started a plan, so it's not possible that we've just completed a goal.
        	return;
        }
        
        if (t instanceof GoalNode && !goalsCompleted.contains(t.getType()))
        {
        	// The step isn't actually complete yet so the plan can't be either.
        	return;
        }
        
        PlanNode plan = (PlanNode)t.getParent();
		if (plan == null)
		{
			// We just started a top-level goal, don't need to do anything.
			return;
		}
		
		if (!isPlanComplete(plan))
		{
			// Plan isn't complete, so we haven't completed a goal.
			return;
		}
			
		// If we've made it to this point then the plan is complete.
		GoalNode goal_completed = (GoalNode)plan.getParent();
        
		// Per Yuan's email (Re: Another xml question, 11/12/2020), ignore the post conditions of plans.
		//Condition[] post = ((PlanNode)plan).getPostc();
        //this.beliefs.apply(post);
		
		goalsCompleted.add(goal_completed.getType());
		//Log.info("Completed goal " + goal_completed.getType(), true);
		
		this.addDependentsToCandidates(goal_completed.getDependents());
		
		// Now need to check if we've also completed some higher-level goal.
		updateGoalsCompleted(goal_completed);
    }
    
    public void applyAction(TreeNode action)
    {
        if (!candidateActions.contains(action))
        {
        	Log.info("ERROR: Attempted to execute an action (" + action.getType() + ") whose temporal dependencies have not been met!");
        	throw new Error("ERROR: Attempted to execute an action (" + action.getType() + ") whose temporal dependencies have not been met!");
        }
        
        if (!arePreconditionsMet(action))
        {
        	Log.info("ERROR: Attempted to execute an action (" + action.getType() + ") whose preconditions are not met!");
        	throw new Error("ERROR: Attempted to execute an action (" + action.getType() + ") whose preconditions are not met!");
        }
        
        if (!(action instanceof GoalNode))
        {
            lastActionApplied = action;
        }
        
        nodesVisited.add(action.getType());
        
        candidateActions.remove(action);

        // After a plan has been selected, prevent siblings from also being selected
        if (action instanceof PlanNode)
        {
        	GoalNode gn = (GoalNode)action.getParent();
        	PlanNode[] plans = gn.getPlans();
        	
        	for (PlanNode p : plans)
        	{
        		if (p != action)
        		{
        			blockedPlans.add(p.getType());
        		}
        	}
        }
        
        // Update goals completed.
        // NOTE: It's crucial for this to come before the call to nextIstep!
    	updateGoalsCompleted(action);

        // Apply postconditions
    	if (action instanceof ActionNode)
    	{
    		Condition[] post = ((ActionNode)action).getPostc();
            this.beliefs.apply(post);
    	}
        
    	// Get the full list of dependents for the node.
    	// This is made a bit tricky by goal nodes, since they don't automatically have plans added as dependents.
    	// Also, presumably the dependents of a goal should only be added after the goal is complete (or if it's a top-level goal).
        // TODO: Update the code if it turns out I'm wrong about this -- currently querying with Yuan.
    	ArrayList<TreeNode> dependents = new ArrayList<TreeNode>();
        
    	// TODO: Not too sure about the logic here... Shouldn't really be possible for goalsCompleted.contains(action.getType()).
        //if (!REQUIRE_ALL_STEPS_COMPLETE || !(action instanceof GoalNode) || (action.getParent() == null) || goalsCompleted.contains(action.getType()))
        
    	if (!(action instanceof GoalNode)) // When a GoalNode is passed to applyAction, it hasn't actually been completed yet.
    	{
        	dependents = action.getDependents();
        }
        
        if (action instanceof GoalNode)
        {
        	PlanNode[] ps = ((GoalNode)action).getPlans();
        	for (PlanNode p : ps)
        	{
        		dependents.add(p);
        	}
        }
        
        addDependentsToCandidates(dependents);
        
        // Further refine the list of candidates.
        ArrayList<TreeNode> candidatesToRemove = new ArrayList<TreeNode>();
        
        for (TreeNode cand : candidateActions)
        {
            // Don't allow siblings of attempted plans to run.
            if (blockedPlans.contains(cand.getType()))
            {
            	candidatesToRemove.add(cand);
            }
            
            // Ensure that each candidate action still pertains to an incomplete goal.
            if (!ALLOW_ACTIONS_BELOW_COMPLETED_GOALS)
            {
    	        TreeNode parent = cand.getParent();
    	        while (parent != null)
    	        {
    	        	if (parent instanceof GoalNode)
    	        	{
    	        		if (goalsCompleted.contains(parent.getType()))
    	        		{
    	        			candidatesToRemove.add(cand);
    	        			break;
    	        		}
    	        	}
    	        	parent = parent.getParent();
    	        }
            }
        }
        
        candidateActions.removeAll(candidatesToRemove);
    	
        jumpOverGoals();
    }
    
    private void addDependentsToCandidates(ArrayList<TreeNode> dependents)
    {
		// For each dependent, check if all prerequisites are now met.
        for (TreeNode d : dependents)
        {
        	if (!candidateActions.contains(d))
        	{
                String preqString = preqMap.get(d.getType());
                boolean preqsMet = true;
                
    	        if (preqString != null)
    	        {
    	        	String[] preqArr = preqString.split(";");
    		        for (String s : preqArr)
    		        {
    		        	TreeNode preqNode = nodeLib.get(s);
    		        	
    		        	// The interpretation of the prerequisites in the .xml is a little bit tricky...
    		        	// If 'd' is a plan node and 'preqNode' is a goal node, we just need to check if the goal node has been "visited".
    		        	// However, if 'd' is an action or goal node, and 'preqNode' is a goal node, we need to ensure that 'preqNode' has actually been completed.
    		        	
    		        	if (preqNode instanceof GoalNode && !(d instanceof PlanNode))
    		        	{
    		        		if (!goalsCompleted.contains(s))
    		        		{
    		        			preqsMet = false;
    		        			break; // Prerequisites aren't all met
    		        		}
    		        	}
    		        	else
    		        	{
        	    	        if (!nodesVisited.contains(s))
        	    	        {
        	    	        	preqsMet = false;
        	    	            break; // Prerequisites aren't all met
        	    	        }
    		        	}
    		        }
    	        }
    	        
    	        if (preqsMet)
    	        {
	    	        candidateActions.add(d);
    	        }
        	}
        }
    }
    
    // "Jump over" goal nodes, as per Brian's email
    public void jumpOverGoals()
    {
        boolean goal_jumped = true;
        
        while (goal_jumped)
        {
        	goal_jumped = false;
        	
	        for (TreeNode c : candidateActions)
	        {
	        	if (c instanceof GoalNode)
	        	{
	        		applyAction(c);
	        		goal_jumped = true;
	        		break;
	        	}
	        }
        }
    }
    
    private boolean isChildOf(TreeNode t, TreeNode other)
    {
    	TreeNode current_node = t;
    	
    	while (current_node != null)
    	{
    		if (current_node.getType().equals(other.getType()))
    		{
    			return true;
    		}
    		
    		current_node = current_node.getParent();
    	}
    	
    	return false;
    }
    
    public ArrayList<TreeNode> getAvailableActions(boolean[] intentionAvailable)
    {
    	ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    
    	for (TreeNode cand : candidateActions)
    	{
    		if (arePreconditionsMet(cand) && intentionAvailable[cand.getIntentionIdx(intentions)]
    			&& (lastActionApplied == null || lastActionApplied instanceof ActionNode || isChildOf(cand, lastActionApplied)))
    		{
    			result.add(cand);
    		}
    	}
    	
    	// Add pass action if applicable
    	if (lastActionApplied == null || lastActionApplied instanceof ActionNode || result.size() == 0)
    	{
    		result.add(null);
    	}
    	
    	return result;
    }
    
    public ArrayList<TreeNode> getExpansionActions(boolean[] intentionAvailable, boolean legacy, String player_name)
    {
    	if (game_over)
    	{
    		ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    		result.add(null);
    		return result;
    	}
    	
    	ArrayList<TreeNode> available_actions = getAvailableActions(intentionAvailable);
    	
    	if (!legacy)
    	{
    		return available_actions;
    	}
    	
    	int num_intentions = intentions.size();
    	
    	if (!linearisations.containsKey(player_name))
    	{
    		linearisations.put(player_name, new Linearisation(player_name));
    	}
    	Linearisation l = linearisations.get(player_name);
    	
    	ArrayList<ArrayList<TreeNode>> highest_precedence_nodes = new ArrayList<ArrayList<TreeNode>>();
    	for (int i = 0; i < num_intentions; i++)
    	{
    		highest_precedence_nodes.add(new ArrayList<TreeNode>());
    	}
    	
    	for (TreeNode n : candidateActions)
    	{
    		int intention_num = n.getIntentionIdx(intentions);
    		
    		if (highest_precedence_nodes.get(intention_num).size() == 0)
    		{
    			highest_precedence_nodes.get(intention_num).add(n);
    		}
    		else
    		{
    			TreeNode node_to_compare_with = highest_precedence_nodes.get(intention_num).get(0);
    			int precedence = l.getNodePrecedence(n, node_to_compare_with);
    			
    			if (precedence == 0) // Equal precedence
    			{
    				highest_precedence_nodes.get(intention_num).add(n);
    			}
    			else if (precedence == 1) // Higher precedence
    			{
    				highest_precedence_nodes.get(intention_num).clear();
    				highest_precedence_nodes.get(intention_num).add(n);
    			}
    		}
    	}
    	
    	ArrayList<TreeNode> linearised_candidates = new ArrayList<TreeNode>();
    	for (int i = 0; i < num_intentions; i++)
    	{
    		linearised_candidates.addAll(highest_precedence_nodes.get(i));
    	}
    
    	ArrayList<TreeNode> result = new ArrayList<TreeNode>();
        
    	for (TreeNode cand : linearised_candidates)
    	{
    		if (cand == null)
    		{
    			continue;
    		}
    		else if (arePreconditionsMet(cand) && intentionAvailable[cand.getIntentionIdx(intentions)]
    			&& (lastActionApplied == null || lastActionApplied instanceof ActionNode || isChildOf(cand, lastActionApplied)))
    		{
    			result.add(cand);
    		}
    	}
    	
    	// Add pass action if applicable
    	if (lastActionApplied == null || lastActionApplied instanceof ActionNode || result.size() == 0)
    	{
    		result.add(null);
    	}
    	
    	return result;
    }
   
    public boolean isCandidate(TreeNode treeNode)
    {
    	return candidateActions.contains(treeNode);
    }
    
    public boolean isPlanBlocked(PlanNode planNode)
    {
    	return blockedPlans.contains(planNode.getType());
    }
    
    public boolean arePreconditionsMet(TreeNode treeNode)
    {
    	if (treeNode instanceof PlanNode)
    	{
            return this.beliefs.evaluate(((PlanNode)treeNode).getPrec());
    	}
    	else if (treeNode instanceof ActionNode)
    	{
            return this.beliefs.evaluate(((ActionNode)treeNode).getPrec());
    	}
    	else
    	{
    		return true;
    	}
    }
    
    public BeliefBase getBeliefBase()
    {
        return this.beliefs;
    }
    
    public int getTotalNumberOfGPTs()
    {
    	return this.intentions.size();
    }
    
    public int getNumIntentionsCompleted(boolean[] intention_available)
    {
    	int intentions_completed = 0;
    	
        for (int i = 0; i < intentions.size(); i++)
        {
        	if (intention_available[i] && isIntentionComplete(i))
        	{
                intentions_completed++;
        	}
        }

        return intentions_completed;
    }
    
    public double getStateScore(Scheduler_GPG sched)
    {
    	double[] intention_values = sched.intention_values[sched.agent_num];

    	boolean[] intention_counts_towards_score = new boolean[intention_values.length];
    	
    	if (sched instanceof MCTS_Scheduler_GPG)
    	{
    		for (int i = 0; i < intention_counts_towards_score.length; i++)
    		{
    			intention_counts_towards_score[i] = ((MCTS_Scheduler_GPG)sched).gpt_visible[i];
    		}
    	}
    	else
    	{
    		intention_counts_towards_score = sched.available_intentions[sched.agent_num];
    	}
    	
    	int score = 0;
        for (int i = 0; i < intentions.size(); i++)
        {
        	if (intention_counts_towards_score[i])
        	{
        		if (isIntentionComplete(i))
        		{
        			score += intention_values[i];
        		}
        	}
        }
        
        // TODO: Get rid of this later for efficiency. Serving purely as a consistency check.
    	int alt_score = 0;
        for (int i = 0; i < intentions.size(); i++)
        {
        	if (intention_counts_towards_score[i])
        	{
        		if (goalsCompleted.contains(intentions.get(i).getType()))
        		{
        			alt_score += intention_values[i];
        		}
        	}
        }
        
        if (score != alt_score)
        {
            System.out.println("ERROR: Score mismatch!");
            System.out.println("score = " + score + ", alt_score = " + alt_score);
            System.exit(0);
        }
    	
        return (double)score;
    }
    
    public boolean isIntentionComplete(int intention_num)
    {
    	return goalsCompleted.contains(intentions.get(intention_num).getType());
    }
    
    public boolean isGameOver()
    {
    	// TODO: Fix me! Currently only used for efficiency in MCTS.
    	return false;
    	
    	/*
    	for (TreeNode t : intentions_legacy)
    	{
    		if (t != null)
    		{
    			return false;
    		}
    	}
    	
    	return true;
    	*/
    }

    /**
     * @return a copy of the current state
     */
    @Override
    public State_GPG clone()
    {
        return new State_GPG(forest_name, beliefs.clone(), new ArrayList<TreeNode>(intentions),
        	nodeLib, preqMap, parentMap, playerTurn, consecutive_passes, game_over, new HashSet<String>(nodesVisited), new HashSet<String>(goalsCompleted),
        	new HashSet<TreeNode>(candidateActions), new HashSet<String>(blockedPlans), lastActionApplied);
    }
}