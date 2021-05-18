package scheduler_GPG;

import goalplantree.ActionNode;
import goalplantree.TreeNode;

import java.util.ArrayList;
import java.util.HashMap;

public class MCTS_Node_GPG {

    // state of this node
    public State_GPG state;

    // selection information
    TreeNode actionChoice;

    // child nodes
    public ArrayList<MCTS_Node_GPG> children;
    
    // statistics
    public int nVisits;
    public Match_GPG match;
	public double[] totValue;
	public double[] totSqValue;
	public HashMap<TreeNode, double[]> totValueAllNodes;
	public HashMap<TreeNode, Integer> nVisitsAllNodes;

    public MCTS_Node_GPG(State_GPG state, Match_GPG match)
    {
        this.state = state;
        this.match = match;
        
        // statistics initialisation
        init();
    }

    /**
     * initialisation
     */
    private void init()
    {
        nVisits = 0;
		this.totValue = new double[match.schedulers.length];
		this.totSqValue = new double[match.schedulers.length];
    	this.totValueAllNodes = new HashMap<TreeNode, double[]>();
    	this.nVisitsAllNodes = new HashMap<TreeNode, Integer>();
    }

    /**
     * @return true if it is the leaf node; false, otherwise
     */
    public boolean isLeaf()
    {
        return children == null;
    }
    
    /**
     * expand the current node
     */
    public void expand(boolean[] intentionAvailable, boolean legacy, String player_name)
    {
    	children = new ArrayList<>();
    	ArrayList<TreeNode> expansionActions = state.getExpansionActions(intentionAvailable, legacy, player_name);
    	
    	for (TreeNode t : expansionActions)
    	{
    		State_GPG new_state = state.clone();
    		
    		if (t != null)
    		{
    			new_state.applyAction(t);
    		}
    		
    		if (t == null || t instanceof ActionNode)
    		{
    			new_state.playerTurn = (state.playerTurn + 1) % match.numAgents;
    		}
    		
            MCTS_Node_GPG child_node = new MCTS_Node_GPG(new_state, match);
        	child_node.actionChoice = t;
        	this.children.add(child_node);
    	}	
    }
    
    public TreeNode getActionChoice()
    {
        return actionChoice;
    }
}
