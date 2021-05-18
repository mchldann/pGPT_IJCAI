package scheduler_GPG;

import java.util.ArrayList;
import java.util.Random;

import goalplantree.TreeNode;

public class Random_Scheduler_GPG extends Scheduler_GPG {

    private static Random rm = new Random();
    
    private boolean linearise;
    private boolean allow_pass;
    
    public Random_Scheduler_GPG(boolean linearise, boolean allow_pass)
    {
    	this.linearise = linearise;
    	this.allow_pass = allow_pass;
    }
    
	@Override
	public void reset()
	{
		// Nothing to reset for this scheduler
	}
    
    public Decision_GPG getDecision(State_GPG state)
    {
    	ArrayList<TreeNode> expansionActions = state.getExpansionActions(available_intentions[state.playerTurn], linearise, match.agent_names[agent_num]);
    	    	
    	boolean playerMustPass = ((expansionActions.size() == 1) && (expansionActions.get(0) == null));

    	// Remove pass if not allowed
    	if (!this.allow_pass && (expansionActions.size() > 1) && (expansionActions.get(expansionActions.size() - 1) == null))
    	{
    		expansionActions.remove(expansionActions.size() - 1);
    	}
    	
    	ArrayList<TreeNode> actionChoices = new ArrayList<TreeNode>();
    	int i = rm.nextInt(expansionActions.size());    	
    	actionChoices.add(expansionActions.get(i));
	
    	return new Decision_GPG(actionChoices, playerMustPass);
    }
}
